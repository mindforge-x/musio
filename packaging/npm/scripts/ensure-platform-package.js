#!/usr/bin/env node
const { spawnSync } = require("node:child_process");
const { existsSync } = require("node:fs");
const { createRequire } = require("node:module");
const { dirname, join, resolve } = require("node:path");

const requireFromHere = createRequire(__filename);
const packageRoot = resolve(__dirname, "..");

const PLATFORM_PACKAGE_BY_TARGET = {
  "linux-x64": "@mindforge-x/musio-linux-x64",
  "linux-arm64": "@mindforge-x/musio-linux-arm64",
  "darwin-x64": "@mindforge-x/musio-darwin-x64",
  "darwin-arm64": "@mindforge-x/musio-darwin-arm64",
  "win32-x64": "@mindforge-x/musio-win32-x64",
  "win32-arm64": "@mindforge-x/musio-win32-arm64"
};

const target = `${process.platform}-${process.arch}`;
const platformPackage = PLATFORM_PACKAGE_BY_TARGET[target];

if (!platformPackage) {
  warn(`Unsupported Musio platform: ${process.platform} (${process.arch})`);
  process.exit(0);
}

if (resolvePlatformPackageRoot(platformPackage) || hasReleaseDirectory(packageRoot, "vendor") || hasReleaseDirectory(packageRoot, "dist")) {
  process.exit(0);
}

if (process.env.MUSIO_RELEASE_ROOT && hasReleaseRoot(process.env.MUSIO_RELEASE_ROOT)) {
  process.exit(0);
}

if (process.env.MUSIO_SKIP_PLATFORM_INSTALL === "1") {
  warn(
    `Missing Musio platform runtime package: ${platformPackage}.`,
    "MUSIO_SKIP_PLATFORM_INSTALL=1 was set, so automatic runtime installation was skipped."
  );
  process.exit(0);
}

const version = platformPackageVersion(platformPackage);
const spec = `${platformPackage}@${version}`;
console.log(`Installing Musio platform runtime: ${spec}`);

const result = spawnSync(npmCommand(), [
  "install",
  "--no-save",
  "--package-lock=false",
  "--ignore-scripts",
  "--include=optional",
  "--no-audit",
  "--fund=false",
  spec
], {
  cwd: packageRoot,
  stdio: "inherit",
  shell: process.platform === "win32"
});

if (result.status !== 0) {
  warn(
    `Failed to install Musio platform runtime package: ${spec}`,
    "Install Musio again with optional dependencies enabled, or check npm registry/network access."
  );
  process.exit(0);
}

if (!resolvePlatformPackageRoot(platformPackage)) {
  warn(
    `Musio platform runtime package is still missing after install: ${platformPackage}.`,
    "The musio command will retry before launch. If it still fails, install the matching platform package explicitly."
  );
}

function resolvePlatformPackageRoot(packageName) {
  try {
    return dirname(requireFromHere.resolve(`${packageName}/package.json`));
  } catch {
    return null;
  }
}

function platformPackageVersion(packageName) {
  const pkg = require(join(packageRoot, "package.json"));
  return pkg.optionalDependencies?.[packageName] ?? pkg.version;
}

function hasReleaseRoot(root) {
  return hasReleaseDirectory(root, "vendor") || hasReleaseDirectory(root, "dist");
}

function hasReleaseDirectory(root, child) {
  const releaseDirectory = join(root, child);
  return existsSync(join(releaseDirectory, "lib", "musio-cli.jar"))
    && existsSync(join(releaseDirectory, "app", "backend-spring.jar"));
}

function npmCommand() {
  return process.platform === "win32" ? "npm.cmd" : "npm";
}

function warn(...lines) {
  for (const line of lines) {
    if (line) {
      console.warn(line);
    }
  }
}
