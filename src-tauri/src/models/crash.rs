use serde::{Deserialize, Serialize};

/// A saved record of a game session that ended badly.
///
/// Persisted as one JSON file per crash under `<data dir>/crashes/`, so a crash
/// survives closing the launcher — the frontend's log buffer is memory-only and
/// used to be the *only* copy, which meant the one thing a user most needs to
/// look at was also the one thing they lost by restarting.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CrashReport {
    pub id: String,
    pub instance_id: String,
    pub instance_name: String,
    pub mc_version: String,
    pub loader: String,
    pub exit_code: i32,
    pub occurred_at: String,
    /// Absolute path to the full session log.
    pub log_path: String,
    /// Best-effort one-line explanation. See `diagnose`.
    pub summary: String,
    /// Trailing lines of output, oldest first.
    pub tail: Vec<String>,
}

/// Turn the tail of a crashed session's log into a one-line explanation.
///
/// This is pattern matching, not real analysis, and it's ordered most-specific
/// first so a mod's missing dependency doesn't get reported as the generic
/// "mod initialization" case that also matches. Anything unrecognised falls
/// through to a statement of the exit code, which is honest — a wrong guess
/// here would send someone chasing the wrong problem, so the fallback says
/// nothing rather than something invented.
pub fn diagnose(exit_code: i32, tail: &[String]) -> String {
    let haystack = tail.join("\n");
    let has = |needle: &str| haystack.contains(needle);

    if has("java.lang.OutOfMemoryError") {
        return "Ran out of memory. Raise the max memory for this instance in its settings.".into();
    }
    if has("Incompatible mods found") || has("ModResolutionException") || has("Mod resolution encountered an incompatible mod set") {
        return "A mod is incompatible or missing a dependency. The lines below name it.".into();
    }
    if has("requires version") && has("of fabric") {
        return "A mod needs a different Fabric loader or API version than this instance has.".into();
    }
    if has("java.lang.UnsupportedClassVersionError") {
        return "A mod was built for a newer Java than this instance is using. Point it at a newer JDK.".into();
    }
    if has("Pixel format not accelerated") || has("Failed to create window") || has("GLFW error") {
        return "The graphics driver refused to create a window. Update your GPU driver.".into();
    }
    if has("Mixin apply failed") || has("MixinApplyError") || has("InvalidMixinException") {
        return "A mod's mixin failed to apply — usually two mods patching the same class.".into();
    }
    if has("java.lang.NoSuchMethodError") || has("java.lang.NoClassDefFoundError") {
        return "A mod called into code that isn't there — usually a version mismatch between mods.".into();
    }
    if has("Exception in thread \"main\"") {
        return "The game threw an unhandled exception during startup.".into();
    }
    if exit_code == 1 {
        return "The game exited with code 1 without a recognised error.".into();
    }
    format!("The game exited with code {exit_code}.")
}

#[cfg(test)]
mod tests {
    use super::*;

    fn tail(lines: &[&str]) -> Vec<String> {
        lines.iter().map(|s| s.to_string()).collect()
    }

    #[test]
    fn spots_an_out_of_memory_crash() {
        let t = tail(&["[main] ERROR", "java.lang.OutOfMemoryError: Java heap space"]);
        assert!(diagnose(1, &t).contains("Ran out of memory"));
    }

    #[test]
    fn prefers_the_dependency_case_over_the_generic_exception_case() {
        // Both patterns are present; the specific one has to win.
        let t = tail(&[
            "Exception in thread \"main\" java.lang.RuntimeException",
            "Incompatible mods found",
        ]);
        assert!(diagnose(1, &t).contains("incompatible or missing a dependency"));
    }

    #[test]
    fn falls_back_to_the_exit_code_rather_than_guessing() {
        let t = tail(&["shutting down", "goodbye"]);
        assert_eq!(diagnose(137, &t), "The game exited with code 137.");
    }

    #[test]
    fn reports_a_bare_exit_1_without_inventing_a_cause() {
        assert!(diagnose(1, &tail(&["nothing useful"])).contains("without a recognised error"));
    }
}
