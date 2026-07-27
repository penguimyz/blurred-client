// Prevents an additional console window on Windows in release builds.
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

fn main() {
    // WebKitGTK 2.42+ defaults to a DMA-BUF renderer that renders nothing at
    // all under a fair number of Linux driver stacks (proprietary Nvidia most
    // notably, plus some VM/software-GL setups) -- the window opens and stays
    // blank, with nothing logged. Disabling it costs a little compositing
    // performance and is what every Tauri app on Linux ends up doing. This must
    // be set before the webview initializes, hence here rather than in setup().
    // Respect an existing value so a user can opt back in.
    #[cfg(target_os = "linux")]
    if std::env::var_os("WEBKIT_DISABLE_DMABUF_RENDERER").is_none() {
        std::env::set_var("WEBKIT_DISABLE_DMABUF_RENDERER", "1");
    }

    blurred_client_lib::run();
}
