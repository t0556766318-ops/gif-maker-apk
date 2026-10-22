"use strict";

const { app, BrowserWindow, dialog, ipcMain, session } = require("electron");
const fs = require("node:fs");
const path = require("node:path");

let mainWindow = null;

function safeFileName(input) {
  let name = path.basename(String(input || "gif-style.gif"));
  name = name.replace(/[<>:"/\\|?*\x00-\x1F]/g, "-");
  if (!/\.gif$/i.test(name)) name += ".gif";
  return name;
}

function showError(message) {
  if (mainWindow && !mainWindow.isDestroyed()) {
    dialog.showErrorBox("GIF Style", String(message || "אירעה שגיאה"));
  }
}

ipcMain.on("save-gif", async (_event, payload) => {
  try {
    const name = safeFileName(payload && payload.name);
    const b64 = String((payload && payload.b64) || "").replace(/^data:image\/gif;base64,/, "");
    const data = Buffer.from(b64, "base64");
    if (!data.length) throw new Error("לא התקבל קובץ GIF תקין.");

    const defaultPath = path.join(app.getPath("pictures"), "GIF STYLE", name);
    const choice = await dialog.showSaveDialog(mainWindow, {
      title: "שמירת GIF",
      defaultPath,
      buttonLabel: "שמור",
      filters: [{ name: "GIF", extensions: ["gif"] }]
    });
    if (choice.canceled || !choice.filePath) return;

    await fs.promises.mkdir(path.dirname(choice.filePath), { recursive: true });
    await fs.promises.writeFile(choice.filePath, data);
    dialog.showMessageBox(mainWindow, {
      type: "info",
      title: "GIF Style",
      message: "הקובץ נשמר בהצלחה",
      detail: choice.filePath,
      buttons: ["אישור"],
      noLink: true
    });
  } catch (error) {
    showError("השמירה נכשלה: " + (error && error.message ? error.message : error));
  }
});

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1240,
    height: 880,
    minWidth: 900,
    minHeight: 650,
    title: "GIF Style",
    autoHideMenuBar: true,
    backgroundColor: "#f2f4ff",
    webPreferences: {
      preload: path.join(__dirname, "preload.cjs"),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      webSecurity: true
    }
  });

  mainWindow.webContents.setWindowOpenHandler(() => ({ action: "deny" }));
  mainWindow.webContents.on("will-navigate", (event, url) => {
    if (!url.startsWith("file://")) event.preventDefault();
  });

  mainWindow.loadFile(path.join(__dirname, "web", "index.html"));
  mainWindow.on("closed", () => {
    mainWindow = null;
  });
}

app.whenReady().then(() => {
  // Explicitly block all network requests from the app at runtime.
  session.defaultSession.webRequest.onBeforeRequest(
    { urls: ["http://*/*", "https://*/*", "ws://*/*", "wss://*/*"] },
    (_details, callback) => callback({ cancel: true })
  );

  createWindow();
  app.on("activate", () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on("window-all-closed", () => {
  if (process.platform !== "darwin") app.quit();
});
