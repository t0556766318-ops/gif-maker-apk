# GIF Style — Windows

This directory is a separate Windows desktop project built from the existing GIF Style web interface in the Android app. The Android source files are not edited by this project.

## Offline design

- The UI, images and fonts are copied from `app/src/main/assets` during staging.
- The app runs locally inside Electron and blocks HTTP, HTTPS and WebSocket requests at runtime.
- Saving GIFs uses a native Windows Save dialog.
- Video frames are extracted locally with the bundled `ffmpeg-static` binary.
- Internet is needed by the build runner to download build dependencies; the installed application is designed to run without an internet connection.

## Build on GitHub

The workflow `.github/workflows/build-windows.yml` builds a Windows x64 installer whenever the Windows project or its source assets change. When it succeeds, download the artifact named `gif-style-windows-installer` from the workflow run.

## Build locally on Windows

Install Node.js 24, open PowerShell in this directory, then run:

```powershell
npm install
npm run start
```

To generate the installer:

```powershell
npm run dist:win
```

The installer is written to `dist`.

## Notes

- The web interface remains based on the existing local `index.html`; it is not loaded from a website.
- File selection is handled by native Windows dialogs. Video frame extraction is local.
- Sharing uses the same Save dialog, so no online sharing service is required.
