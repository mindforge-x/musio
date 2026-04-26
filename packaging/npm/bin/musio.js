#!/usr/bin/env node
const { spawnSync } = require("node:child_process");
const { existsSync } = require("node:fs");
const { join, resolve } = require("node:path");

const root = resolve(__dirname, "..", "..", "..");
const cliJar = join(root, "cli-java", "target", "musio-cli.jar");

if (!existsSync(cliJar)) {
  console.error("Musio CLI jar was not found. Run `mvn -pl cli-java -am package` first.");
  process.exit(1);
}

const result = spawnSync("java", ["-jar", cliJar, ...process.argv.slice(2)], {
  stdio: "inherit"
});

process.exit(result.status ?? 1);
