// JVM auto-detection and process launching.
//
// Two detection backends: Windows walks Program Files + the JavaSoft registry
// keys, everything else (Linux/macOS) walks the well-known install roots plus
// $PATH. They differ enough in where JVMs live -- and in the executable name
// (`javaw.exe` vs `java`) -- that a shared implementation would be mostly cfg
// branches anyway.

use serde::Serialize;
use std::path::{Path, PathBuf};
use std::process::Stdio;

use tauri::{AppHandle, Emitter};
use tokio::io::{AsyncBufReadExt, BufReader};
use tokio::process::Command;

#[derive(Debug, Clone, Serialize)]
pub struct DetectedJava {
    pub path: String,
    pub version: String, // best-effort, parsed from `java -version` stderr
    pub major_version: Option<u32>,
}

#[cfg(target_os = "windows")]
pub fn detect_installed_javas() -> Vec<DetectedJava> {
    let mut found = Vec::new();

    // 1. JAVA_HOME
    if let Ok(home) = std::env::var("JAVA_HOME") {
        let exe = PathBuf::from(&home).join("bin").join("javaw.exe");
        if exe.exists() {
            found.push(probe_java(&exe));
        }
    }

    // 2. Common install roots. Eclipse Adoptium/Temurin and Oracle both
    // install under Program Files with a versioned subfolder.
    let roots = [
        r"C:\Program Files\Java",
        r"C:\Program Files\Eclipse Adoptium",
        r"C:\Program Files (x86)\Eclipse Adoptium",
        r"C:\Program Files\Microsoft",
        r"C:\Program Files\Zulu",
    ];
    for root in roots {
        let root_path = PathBuf::from(root);
        let Ok(entries) = std::fs::read_dir(&root_path) else { continue };
        for entry in entries.flatten() {
            let candidate = entry.path().join("bin").join("javaw.exe");
            if candidate.exists() {
                found.push(probe_java(&candidate));
            }
        }
    }

    // 3. Registry (JavaSoft key), covers installs that don't land in the
    // common Program Files paths, e.g. some Oracle installers.
    if let Ok(hklm) = winreg_probe() {
        found.extend(hklm);
    }

    found.sort_by(|a, b| a.path.cmp(&b.path));
    found.dedup_by(|a, b| a.path == b.path);
    found
}

#[cfg(target_os = "windows")]
fn winreg_probe() -> anyhow::Result<Vec<DetectedJava>> {
    use winreg::enums::*;
    use winreg::RegKey;

    let mut results = Vec::new();
    let hklm = RegKey::predef(HKEY_LOCAL_MACHINE);

    // Modern JDKs register under this key; older ones under
    // SOFTWARE\JavaSoft\Java Runtime Environment. Check both.
    for base in [
        r"SOFTWARE\JavaSoft\JDK",
        r"SOFTWARE\JavaSoft\Java Runtime Environment",
    ] {
        let Ok(key) = hklm.open_subkey(base) else { continue };
        for version_name in key.enum_keys().flatten() {
            let Ok(version_key) = key.open_subkey(&version_name) else { continue };
            let install_path: Result<String, _> = version_key.get_value("JavaHome");
            if let Ok(path) = install_path {
                let exe = PathBuf::from(&path).join("bin").join("javaw.exe");
                if exe.exists() {
                    results.push(probe_java(&exe));
                }
            }
        }
    }
    Ok(results)
}

#[cfg(not(target_os = "windows"))]
pub fn detect_installed_javas() -> Vec<DetectedJava> {
    use std::collections::HashSet;

    let mut found = Vec::new();
    let mut seen: HashSet<PathBuf> = HashSet::new();

    // JAVA_HOME first, same as the Windows path.
    if let Ok(home) = std::env::var("JAVA_HOME") {
        consider_java(&PathBuf::from(&home).join("bin").join("java"), &mut found, &mut seen);
    }

    // Anything already on $PATH. This is what catches the setups that don't
    // use a predictable install root at all -- Nix, Homebrew, asdf/mise shims,
    // SDKMAN's `current` symlink, or a hand-rolled tarball the user PATH'd.
    if let Ok(path) = std::env::var("PATH") {
        for dir in std::env::split_paths(&path) {
            consider_java(&dir.join("java"), &mut found, &mut seen);
        }
    }

    // Directories whose *children* are individual JVM installs. macOS keeps
    // JVMs in versioned bundles under JavaVirtualMachines; Linux distros use
    // /usr/lib/jvm (Debian/Ubuntu/Fedora/Arch) or /usr/lib64/jvm (openSUSE).
    let mut roots: Vec<PathBuf> = if cfg!(target_os = "macos") {
        ["/Library/Java/JavaVirtualMachines", "/System/Library/Java/JavaVirtualMachines"]
            .iter()
            .map(PathBuf::from)
            .collect()
    } else {
        ["/usr/lib/jvm", "/usr/lib64/jvm", "/usr/java", "/opt/java", "/opt"]
            .iter()
            .map(PathBuf::from)
            .collect()
    };
    // Per-user JDK managers. Common enough on Linux dev machines that skipping
    // them means "no Java found" on a box that has five of them installed.
    if let Some(home) = std::env::var_os("HOME").map(PathBuf::from) {
        roots.push(home.join(".sdkman/candidates/java")); // SDKMAN
        roots.push(home.join(".jdks")); // JetBrains Toolbox / IntelliJ
        roots.push(home.join(".gradle/jdks")); // Gradle toolchain auto-provisioning
    }

    for root in &roots {
        let Ok(entries) = std::fs::read_dir(root) else { continue };
        for entry in entries.flatten() {
            // macOS nests the runtime under Contents/Home; Linux is usually
            // flat, except older JDK 8 packages that keep it under jre/.
            let dir = entry.path();
            let candidates = [
                dir.join("Contents/Home/bin/java"),
                dir.join("bin/java"),
                dir.join("jre/bin/java"),
            ];
            for exe in candidates {
                if exe.is_file() {
                    consider_java(&exe, &mut found, &mut seen);
                    break;
                }
            }
        }
    }

    found.sort_by(|a, b| a.path.cmp(&b.path));
    found
}

/// Probe `exe` and record it, unless it's a JVM we've already got. Dedup is by
/// *resolved* path: on Linux a single JDK is typically reachable through
/// several names at once (`/usr/lib/jvm/default-java`,
/// `/usr/lib/jvm/java-21-openjdk-amd64`, and `/usr/bin/java` are all one
/// install), and listing it three times in the Java picker is just noise.
/// The unresolved path is what gets shown, since it's the recognizable one.
#[cfg(not(target_os = "windows"))]
fn consider_java(exe: &Path, found: &mut Vec<DetectedJava>, seen: &mut std::collections::HashSet<PathBuf>) {
    if !exe.is_file() {
        return;
    }
    let key = std::fs::canonicalize(exe).unwrap_or_else(|_| exe.to_path_buf());
    if !seen.insert(key) {
        return;
    }
    found.push(probe_java(exe));
}

fn probe_java(exe: &Path) -> DetectedJava {
    let output = std::process::Command::new(exe)
        .arg("-version")
        .output();

    let (version, major_version) = match output {
        Ok(out) => {
            // java -version prints to stderr, e.g.:
            // openjdk version "17.0.9" 2023-10-17
            let raw = String::from_utf8_lossy(&out.stderr).to_string();
            let version = raw.lines().next().unwrap_or("unknown").to_string();
            let major = parse_major_version(&version);
            (version, major)
        }
        Err(_) => ("unknown".to_string(), None),
    };

    DetectedJava {
        path: exe.to_string_lossy().to_string(),
        version,
        major_version,
    }
}

fn parse_major_version(version_line: &str) -> Option<u32> {
    // Handles both "1.8.0_392" (old scheme) and "17.0.9" (9+ scheme).
    let quoted = version_line.split('"').nth(1)?;
    let first_segment = quoted.split('.').next()?;
    let major: u32 = first_segment.parse().ok()?;
    if major == 1 {
        // old scheme: "1.8.0_392" -> major is the second segment
        let second = quoted.split('.').nth(1)?;
        second.parse().ok()
    } else {
        Some(major)
    }
}

/// Spawns the Minecraft process and streams stdout/stderr back to the
/// frontend as `instance-log` events, tagged with the instance id so the
/// UI can route lines to the right Logs tab. This is the same pipe the
/// Logs viewer (Phase 7) and playtime tracking (Phase 7) both hang off of --
/// don't duplicate this spawn logic elsewhere.
pub async fn launch_and_stream(
    app: AppHandle,
    instance_id: String,
    java_path: String,
    jvm_args: Vec<String>,
    main_class: String,
    game_args: Vec<String>,
    working_dir: PathBuf,
    env_vars: Vec<(String, String)>,
    kill_rx: tokio::sync::oneshot::Receiver<()>,
) -> anyhow::Result<i32> {
    let mut cmd = Command::new(&java_path);
    cmd.args(&jvm_args)
        .arg(&main_class)
        .args(&game_args)
        .current_dir(&working_dir)
        .envs(env_vars)
        .stdout(Stdio::piped())
        .stderr(Stdio::piped());

    let mut child = cmd.spawn()?;

    let stdout = child.stdout.take().expect("stdout was piped");
    let stderr = child.stderr.take().expect("stderr was piped");

    let app_out = app.clone();
    let id_out = instance_id.clone();
    tokio::spawn(async move {
        let mut lines = BufReader::new(stdout).lines();
        while let Ok(Some(line)) = lines.next_line().await {
            let _ = app_out.emit(
                "instance-log",
                serde_json::json!({ "instanceId": id_out, "stream": "stdout", "line": line }),
            );
        }
    });

    let app_err = app.clone();
    let id_err = instance_id.clone();
    tokio::spawn(async move {
        let mut lines = BufReader::new(stderr).lines();
        while let Ok(Some(line)) = lines.next_line().await {
            let _ = app_err.emit(
                "instance-log",
                serde_json::json!({ "instanceId": id_err, "stream": "stderr", "line": line }),
            );
        }
    });

    // Wait for the game to exit on its own, OR for a quit request to arrive on
    // `kill_rx` (the Quit button) — whichever comes first. On quit we ask the
    // process to terminate and still wait for it so we return a real exit code
    // and don't leave a zombie.
    let status = tokio::select! {
        res = child.wait() => res?,
        _ = kill_rx => {
            let _ = child.start_kill();
            child.wait().await?
        }
    };
    Ok(status.code().unwrap_or(-1))
}

/// Builds the classpath string with the platform's separator: `;` on Windows,
/// `:` everywhere else. Always go through this rather than joining by hand --
/// a `;`-joined classpath on Linux is read as one absurdly long path and the
/// game dies on a NoClassDefFoundError that points nowhere useful.
pub fn build_classpath(jar_paths: &[PathBuf]) -> String {
    let sep = if cfg!(target_os = "windows") { ";" } else { ":" };
    jar_paths
        .iter()
        .map(|p| p.to_string_lossy().to_string())
        .collect::<Vec<_>>()
        .join(sep)
}
