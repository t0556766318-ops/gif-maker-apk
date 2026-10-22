# GIF Style Lite for Windows

This is the compact Windows edition. It reuses the existing GIF Style UI, fonts and images, but uses the WebView2 runtime supplied by Windows rather than bundling a full browser engine.

## Offline behavior

- The application UI, fonts, images and video frame extraction are local.
- GIF creation and saving run on the computer without an online service.
- The installer is configured not to download or install WebView2. The computer must already have the WebView2 runtime installed (it is included with Windows 11 and is present on many up-to-date Windows 10 PCs).
- If you need an app that does not depend on WebView2 being installed, use the self-contained Electron build in `windows-app`; it is larger because it bundles its own browser runtime.

## Build

On Windows with Node.js 24, Rust stable MSVC, and the Visual Studio C++ build tools installed:

```powershell
npm install
npm run build:win
```

The installer is produced under `src-tauri/target/release/bundle/nsis`.
