"use strict";

const { contextBridge, ipcRenderer } = require("electron");

// Small bridge for saving output. The web UI uses a normal local file picker
// for selecting input media; all video frame extraction is performed by Chromium
// with a local video URL and canvas, without downloading or launching FFmpeg.
contextBridge.exposeInMainWorld("AndroidBridge", {
  saveGif: (b64, name) => ipcRenderer.send("save-gif", { b64, name }),
  shareGif: (b64, name) => ipcRenderer.send("save-gif", { b64, name }),
  videoOpen: () => "",
  videoClose: () => {}
});
