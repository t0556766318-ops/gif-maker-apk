"use strict";

const { contextBridge, ipcRenderer, webUtils } = require("electron");

// Keep the existing web UI API surface small. No Node.js APIs are exposed to the page.
// openGallery is intentionally not provided: the UI then uses its normal local file picker.
contextBridge.exposeInMainWorld("AndroidBridge", {
  saveGif: (b64, name) => ipcRenderer.send("save-gif", { b64, name }),
  shareGif: (b64, name) => ipcRenderer.send("save-gif", { b64, name }),
  videoOpen: () => ipcRenderer.sendSync("video-open-current"),
  videoFrame: (milliseconds) => ipcRenderer.sendSync("video-frame", String(milliseconds)),
  videoClose: () => ipcRenderer.send("video-close")
});

// Electron's file input intentionally presents a native Windows picker.
// We only pass the chosen local video path to the main process for offline frame extraction.
window.addEventListener("change", (event) => {
  const input = event.target;
  if (!input || input.id !== "vf" || !input.files || !input.files[0]) return;

  try {
    const localPath = webUtils.getPathForFile(input.files[0]);
    if (localPath) ipcRenderer.send("set-video-path", localPath);
  } catch (_error) {
    // The UI will show its normal video-processing error if the path is unavailable.
  }
}, true);
