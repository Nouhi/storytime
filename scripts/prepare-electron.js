const { execSync } = require("child_process");
const fs = require("fs");
const path = require("path");

const ROOT = path.resolve(__dirname, "..");
const STANDALONE = path.join(ROOT, ".next", "standalone");
const STATIC = path.join(ROOT, ".next", "static");
const PUBLIC = path.join(ROOT, "public");
const ELECTRON_APP = path.join(ROOT, "electron-app");

function copyDir(src, dest) {
  fs.mkdirSync(dest, { recursive: true });
  for (const entry of fs.readdirSync(src, { withFileTypes: true })) {
    const srcPath = path.join(src, entry.name);
    const destPath = path.join(dest, entry.name);
    if (entry.isDirectory()) {
      copyDir(srcPath, destPath);
    } else {
      fs.copyFileSync(srcPath, destPath);
    }
  }
}

/**
 * Flatten pnpm's .pnpm/ virtual store into a standard flat node_modules layout.
 * pnpm uses .pnpm/<pkg>@<ver>/node_modules/<pkg> structure with symlinks.
 * Electron-builder and Node.js module resolution inside Electron need a flat layout.
 */
function flattenPnpmNodeModules(nodeModulesDir) {
  const pnpmDir = path.join(nodeModulesDir, ".pnpm");
  if (!fs.existsSync(pnpmDir)) return;

  console.log("  Flattening pnpm node_modules...");
  let count = 0;

  // Walk .pnpm/*/node_modules/* and hoist all packages to the top level
  for (const entry of fs.readdirSync(pnpmDir)) {
    const innerNodeModules = path.join(pnpmDir, entry, "node_modules");
    if (!fs.existsSync(innerNodeModules)) continue;

    for (const pkg of fs.readdirSync(innerNodeModules)) {
      // Handle scoped packages (@scope/name)
      if (pkg.startsWith("@")) {
        const scopeDir = path.join(innerNodeModules, pkg);
        for (const scopedPkg of fs.readdirSync(scopeDir)) {
          const srcPkg = path.join(scopeDir, scopedPkg);
          const destPkg = path.join(nodeModulesDir, pkg, scopedPkg);
          if (!fs.existsSync(destPkg)) {
            fs.cpSync(srcPkg, destPkg, { recursive: true, dereference: true });
            count++;
          }
        }
      } else {
        const srcPkg = path.join(innerNodeModules, pkg);
        const destPkg = path.join(nodeModulesDir, pkg);
        if (!fs.existsSync(destPkg)) {
          fs.cpSync(srcPkg, destPkg, { recursive: true, dereference: true });
          count++;
        }
      }
    }
  }

  // Remove .pnpm directory after flattening
  fs.rmSync(pnpmDir, { recursive: true });
  console.log(`  Hoisted ${count} packages from .pnpm/`);
}

// Clean up previous build artifacts
console.log("0/7 Cleaning previous build artifacts...");
for (const dir of [ELECTRON_APP, path.join(ROOT, "standalone")]) {
  if (fs.existsSync(dir)) {
    fs.rmSync(dir, { recursive: true });
  }
}

console.log("1/7 Copying static assets into standalone...");
copyDir(STATIC, path.join(STANDALONE, ".next", "static"));
if (fs.existsSync(PUBLIC)) {
  copyDir(PUBLIC, path.join(STANDALONE, "public"));
}

console.log("2/7 Assembling electron-app directory...");
fs.mkdirSync(ELECTRON_APP, { recursive: true });

// Copy standalone into electron-app, dereferencing pnpm symlinks
fs.cpSync(STANDALONE, path.join(ELECTRON_APP, "standalone"), {
  recursive: true,
  dereference: true,
});

// Copy compiled Electron main process
copyDir(path.join(ROOT, "dist-electron"), path.join(ELECTRON_APP, "dist-electron"));

// Write minimal package.json
const rootPkg = require(path.join(ROOT, "package.json"));
fs.writeFileSync(
  path.join(ELECTRON_APP, "package.json"),
  JSON.stringify({
    name: rootPkg.name,
    version: rootPkg.version,
    main: "dist-electron/main.js",
    private: true,
  }, null, 2)
);

console.log("3/7 Cleaning unnecessary files from standalone...");
const standaloneDir = path.join(ELECTRON_APP, "standalone");
const dirsToRemove = [
  "generated", "uploads", "standalone", "src", "electron",
  "scripts", ".next/cache", "release", "dist-electron",
  "resources", "build", "electron-app"
];
for (const dir of dirsToRemove) {
  const fullPath = path.join(standaloneDir, dir);
  if (fs.existsSync(fullPath)) {
    fs.rmSync(fullPath, { recursive: true });
    console.log(`  Removed: ${dir}/`);
  }
}
const filesToRemove = [
  "pnpm-lock.yaml", "drizzle.config.ts", "eslint.config.mjs",
  "tsconfig.json", "README.md", ".env", ".env.example", ".env.local",
  ".gitignore", "next-env.d.ts", "pnpm-workspace.yaml",
  "tsconfig.tsbuildinfo", "postcss.config.mjs",
  "storytime.db", "storytime.db-shm", "storytime.db-wal"
];
for (const file of filesToRemove) {
  const fullPath = path.join(standaloneDir, file);
  if (fs.existsSync(fullPath)) {
    fs.unlinkSync(fullPath);
  }
}

console.log("4/7 Flattening pnpm node_modules...");
flattenPnpmNodeModules(path.join(standaloneDir, "node_modules"));

// Turbopack generates hashed names for serverExternalPackages
// (e.g. "better-sqlite3-bbe410e732a55b62" instead of "better-sqlite3").
// In development these resolve by walking up to the root node_modules,
// but in the packaged app there is no parent. Create symlinks so
// require("pkg-<hash>") finds the real package.
console.log("5/8 Creating Turbopack external module aliases...");
const externalsDir = path.join(standaloneDir, ".next", "server", "chunks");
const standaloneNodeModules = path.join(standaloneDir, "node_modules");
const externalsPattern = /require\("([^"]+)-[0-9a-f]{8,}"\)/g;
let aliasCount = 0;
for (const file of fs.readdirSync(externalsDir)) {
  if (!file.endsWith(".js")) continue;
  const content = fs.readFileSync(path.join(externalsDir, file), "utf8");
  let match;
  while ((match = externalsPattern.exec(content)) !== null) {
    const fullHashedName = match[0].slice('require("'.length, -'")'.length);
    const realName = match[1];
    const symlinkPath = path.join(standaloneNodeModules, fullHashedName);
    const realPath = path.join(standaloneNodeModules, realName);
    if (fs.existsSync(realPath) && !fs.existsSync(symlinkPath)) {
      fs.symlinkSync(realName, symlinkPath);
      console.log(`  ${fullHashedName} -> ${realName}`);
      aliasCount++;
    }
  }
}
console.log(`  Created ${aliasCount} aliases`);

// Rebuild better-sqlite3 for Electron's Node ABI using prebuild-install.
// The standalone copy is missing source files, so we rebuild in the root
// node_modules (which has full source) and copy the binary over.
console.log("6/8 Rebuilding native modules for Electron...");
try {
  const electronVersion = require(path.join(ROOT, "node_modules/electron/package.json")).version;
  const betterSqlitePkg = require(path.join(standaloneNodeModules, "better-sqlite3", "package.json"));
  console.log(`  Electron version: ${electronVersion}, better-sqlite3: ${betterSqlitePkg.version}`);

  // Use prebuild-install to download pre-built Electron binary into root node_modules
  const betterSqliteRoot = path.join(ROOT, "node_modules", ".pnpm", `better-sqlite3@${betterSqlitePkg.version}`, "node_modules", "better-sqlite3");
  execSync(
    `npx --yes prebuild-install --runtime electron --target ${electronVersion} --arch arm64 --force`,
    { stdio: "inherit", cwd: betterSqliteRoot }
  );

  // Copy the Electron-compatible binary into the standalone directory
  const src = path.join(betterSqliteRoot, "build", "Release", "better_sqlite3.node");
  const dest = path.join(standaloneNodeModules, "better-sqlite3", "build", "Release", "better_sqlite3.node");
  fs.copyFileSync(src, dest);
  console.log("  Copied Electron-compatible better_sqlite3.node into standalone");

  // Restore system Node build for root node_modules (so next build still works)
  execSync(`pnpm rebuild better-sqlite3`, { stdio: "inherit", cwd: ROOT });
  console.log("  Restored system Node build in root node_modules");
} catch (err) {
  console.error("Warning: native module rebuild failed:", err.message);
}

console.log("7/8 Verifying structure...");
const nodeModulesPath = path.join(standaloneDir, "node_modules");
if (fs.existsSync(nodeModulesPath)) {
  const mods = fs.readdirSync(nodeModulesPath).filter(f => !f.startsWith("."));
  console.log(`  node_modules: ${mods.length} packages`);
  for (const mod of ["better-sqlite3", "sharp", "next", "styled-jsx", "react"]) {
    const modPath = path.join(nodeModulesPath, mod);
    console.log(`  ${fs.existsSync(modPath) ? "✓" : "✗"} ${mod}`);
  }
} else {
  console.error("  ERROR: node_modules not found!");
}

console.log("8/8 Done!");
const size = execSync(`du -sh "${ELECTRON_APP}"`).toString().trim().split("\t")[0];
console.log(`Electron app directory size: ${size}`);
