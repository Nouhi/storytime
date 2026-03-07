import { app, BrowserWindow, shell } from "electron";
import { fork, ChildProcess } from "child_process";
import path from "path";
import fs from "fs";
import net from "net";
import http from "http";

let mainWindow: BrowserWindow | null = null;
let serverProcess: ChildProcess | null = null;
let serverPort: number = 3000;

const isDev = !app.isPackaged;
const appPath = app.getAppPath();
const dataDir = isDev ? process.cwd() : app.getPath("userData");

function ensureDirectories(): void {
  const dirs = [
    dataDir,
    path.join(dataDir, "uploads", "photos"),
    path.join(dataDir, "generated"),
  ];
  for (const dir of dirs) {
    if (!fs.existsSync(dir)) {
      fs.mkdirSync(dir, { recursive: true });
    }
  }
}

function findFreePort(): Promise<number> {
  return new Promise((resolve, reject) => {
    const server = net.createServer();
    server.listen(0, () => {
      const port = (server.address() as net.AddressInfo).port;
      server.close(() => resolve(port));
    });
    server.on("error", reject);
  });
}

function waitForServer(port: number, maxWait = 30000): Promise<void> {
  const start = Date.now();
  return new Promise((resolve, reject) => {
    function check() {
      if (Date.now() - start > maxWait) {
        reject(new Error("Server failed to start within timeout"));
        return;
      }
      const req = http.get(`http://localhost:${port}`, (res) => {
        res.resume();
        resolve();
      });
      req.on("error", () => {
        setTimeout(check, 500);
      });
      req.setTimeout(1000, () => {
        req.destroy();
        setTimeout(check, 500);
      });
    }
    check();
  });
}

function setDataDirEnv(): void {
  process.env.STORYTIME_DATA_DIR = dataDir;
}

async function startNextServer(): Promise<void> {
  serverPort = await findFreePort();

  const env: NodeJS.ProcessEnv = {
    ...process.env,
    PORT: String(serverPort),
    HOSTNAME: "localhost",
    STORYTIME_DATA_DIR: dataDir,
    NODE_ENV: "production",
    // Tell Electron's fork to behave as plain Node.js
    // so Next.js require hooks (Turbopack module aliasing) work correctly
    ELECTRON_RUN_AS_NODE: "1",
  };

  if (isDev) {
    // In dev mode, assume Next.js dev server is already running externally
    serverPort = parseInt(process.env.DEV_PORT || "3000", 10);
    console.log(`[storytime] Dev mode: connecting to localhost:${serverPort}`);
    return;
  }

  // In production, spawn the standalone Next.js server
  const serverPath = path.join(appPath, "standalone", "server.js");
  console.log(`[storytime] Starting server: ${serverPath} on port ${serverPort}`);

  serverProcess = fork(serverPath, [], {
    cwd: path.join(appPath, "standalone"),
    env,
    stdio: ["pipe", "pipe", "pipe", "ipc"],
  });

  serverProcess.stdout?.on("data", (data: Buffer) => {
    console.log(`[next] ${data.toString().trim()}`);
  });
  serverProcess.stderr?.on("data", (data: Buffer) => {
    console.error(`[next] ${data.toString().trim()}`);
  });
  serverProcess.on("exit", (code) => {
    console.log(`[storytime] Next.js server exited with code ${code}`);
    if (mainWindow) {
      app.quit();
    }
  });

  await waitForServer(serverPort);
  console.log(`[storytime] Server ready on port ${serverPort}`);
}

function createWindow(): void {
  mainWindow = new BrowserWindow({
    width: 1200,
    height: 800,
    minWidth: 800,
    minHeight: 600,
    title: "Storytime",
    titleBarStyle: "hiddenInset",
    webPreferences: {
      nodeIntegration: false,
      contextIsolation: true,
    },
  });

  mainWindow.loadURL(`http://localhost:${serverPort}`);

  // Open external links in default browser
  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    if (url.startsWith("http") && !url.includes(`localhost:${serverPort}`)) {
      shell.openExternal(url);
    }
    return { action: "deny" };
  });

  mainWindow.on("closed", () => {
    mainWindow = null;
  });
}

app.whenReady().then(async () => {
  try {
    setDataDirEnv();
    ensureDirectories();
    await startNextServer();
    createWindow();
  } catch (err) {
    console.error("[storytime] Failed to start:", err);
    app.quit();
  }
});

app.on("window-all-closed", () => {
  if (serverProcess) {
    serverProcess.kill();
    serverProcess = null;
  }
  app.quit();
});

app.on("before-quit", () => {
  if (serverProcess) {
    serverProcess.kill();
    serverProcess = null;
  }
});

app.on("activate", () => {
  if (mainWindow === null) {
    createWindow();
  }
});
