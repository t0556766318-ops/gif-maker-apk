# GIF Style — Windows

A separate Windows desktop project based on the existing GIF Style web interface. It does not edit Android source code.

## Offline behavior

- The UI, logo and fonts are bundled from `app/src/main/assets`.
- The application blocks HTTP, HTTPS and WebSocket requests at runtime.
- GIF saving uses a native Windows Save dialog.
- Video frame extraction uses the built-in local Chromium video decoder and canvas; FFmpeg is not bundled.
- The installed app is designed to work without an internet connection. Internet access is required only during the build to download Electron and build dependencies.

## Build on GitHub

The workflow `.github/workflows/build-windows.yml` builds a Windows x64 installer whenever the Windows project or original web assets change. Download the artifact named `gif-style-windows-installer` from the successful run.

## Build locally on Windows

Install Node.js 24, open PowerShell in this directory, then run:

```powershell
npm install
npm run start
```

To create the installer:

```powershell
npm run dist:win
```

The installer is written to `dist`.
