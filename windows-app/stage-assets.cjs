"use strict";

const fs = require("node:fs");
const path = require("node:path");

const repoRoot = path.resolve(__dirname, "..");
const source = path.join(repoRoot, "app", "src", "main", "assets");
const destination = path.join(__dirname, "web");

if (!fs.existsSync(path.join(source, "index.html"))) {
  console.error("Could not find the original app assets at " + source);
  process.exit(1);
}

fs.rmSync(destination, { recursive: true, force: true });
fs.cpSync(source, destination, { recursive: true });

console.log("Staged the existing GIF Style HTML, images and fonts for Windows.");
