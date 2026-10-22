#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use base64::{engine::general_purpose::STANDARD, Engine as _};
use std::{fs, path::Path};
use tauri_plugin_dialog::DialogExt;

#[tauri::command]
fn save_gif(app: tauri::AppHandle, b64: String, name: String) -> Result<bool, String> {
    let raw_name = Path::new(&name)
        .file_name()
        .and_then(|value| value.to_str())
        .unwrap_or("gif-style.gif");
    let safe_name: String = raw_name
        .chars()
        .filter(|c| !matches!(c, '<' | '>' | ':' | '"' | '/' | '\\' | '|' | '?' | '*' ))
        .collect();
    let safe_name = if safe_name.to_ascii_lowercase().ends_with(".gif") {
        safe_name
    } else {
        format!("{safe_name}.gif")
    };

    let selected = app
        .dialog()
        .file()
        .set_file_name(&safe_name)
        .add_filter("GIF image", &["gif"])
        .blocking_save_file();

    let Some(file_path) = selected else {
        return Ok(false);
    };
    let path = file_path.into_path().map_err(|e| e.to_string())?;
    let bytes = STANDARD
        .decode(b64.trim())
        .map_err(|e| format!("Base64 decoding failed: {e}"))?;
    if bytes.is_empty() {
        return Err("לא התקבל קובץ GIF תקין.".to_string());
    }
    fs::write(&path, bytes).map_err(|e| format!("שמירת הקובץ נכשלה: {e}"))?;
    Ok(true)
}

fn main() {
    tauri::Builder::default()
        .plugin(tauri_plugin_dialog::init())
        .invoke_handler(tauri::generate_handler![save_gif])
        .run(tauri::generate_context!())
        .expect("error while running GIF Style Lite");
}
