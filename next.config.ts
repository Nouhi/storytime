import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  serverExternalPackages: ["better-sqlite3", "sharp"],
  devIndicators: false,
};

export default nextConfig;
