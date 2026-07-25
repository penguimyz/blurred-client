import { invoke } from "@tauri-apps/api/core";
import { listen, type UnlistenFn } from "@tauri-apps/api/event";
import type {
  ConfigFile,
  DetectedJava,
  Instance,
  Loader,
  Modpack,
  ModUpdate,
  ScreenshotInfo,
  WorldInfo,
} from "../types/instance";
import type { GlobalSettings } from "../types/settings";
import type { Account, DeviceCodeInfo } from "../types/account";

// One function per #[tauri::command]. Keeps invoke() string literals and
// their argument shapes in exactly one place instead of scattered through
// components, so a Rust-side rename is a one-file fix on this side too.

export function listInstances(): Promise<Instance[]> {
  return invoke("list_instances");
}

export function createInstance(
  name: string,
  mcVersion: string,
  loader: Loader
): Promise<Instance> {
  return invoke("create_instance", { name, mcVersion, loader });
}

export function deleteInstance(instanceId: string): Promise<void> {
  return invoke("delete_instance", { instanceId });
}

export function launchInstance(instanceId: string): Promise<void> {
  return invoke("launch_instance", { instanceId });
}

/** Stop a running instance's game process. */
export function killInstance(instanceId: string): Promise<void> {
  return invoke("kill_instance", { instanceId });
}

/** Instance ids with a currently-running game process. */
export function listRunning(): Promise<string[]> {
  return invoke("list_running");
}

export function renameInstance(instanceId: string, name: string): Promise<Instance> {
  return invoke("rename_instance", { instanceId, name });
}

export function duplicateInstance(instanceId: string): Promise<Instance> {
  return invoke("duplicate_instance", { instanceId });
}

export function openInstanceFolder(instanceId: string): Promise<void> {
  return invoke("open_instance_folder", { instanceId });
}

export function getInstance(instanceId: string): Promise<Instance> {
  return invoke("get_instance", { instanceId });
}

/** Persist an edited instance (notes, per-instance overrides, window size). */
export function updateInstance(instance: Instance): Promise<Instance> {
  return invoke("update_instance", { instance });
}

export function listDetectedJava(): Promise<DetectedJava[]> {
  return invoke("list_detected_java");
}

// ---- mods (per-instance file management) ----

export function setModEnabled(
  instanceId: string,
  filename: string,
  enabled: boolean
): Promise<Instance> {
  return invoke("set_mod_enabled", { instanceId, filename, enabled });
}

export function setModPinned(
  instanceId: string,
  filename: string,
  pinned: boolean
): Promise<Instance> {
  return invoke("set_mod_pinned", { instanceId, filename, pinned });
}

export function removeMod(instanceId: string, filename: string): Promise<Instance> {
  return invoke("remove_mod", { instanceId, filename });
}

export function addLocalMod(instanceId: string, sourcePath: string): Promise<Instance> {
  return invoke("add_local_mod", { instanceId, sourcePath });
}

export function syncInstanceMods(instanceId: string): Promise<Instance> {
  return invoke("sync_instance_mods", { instanceId });
}

// ---- config editor ----

export function listConfigFiles(instanceId: string): Promise<ConfigFile[]> {
  return invoke("list_config_files", { instanceId });
}

export function readConfigFile(instanceId: string, relPath: string): Promise<string> {
  return invoke("read_config_file", { instanceId, relPath });
}

export function writeConfigFile(
  instanceId: string,
  relPath: string,
  contents: string
): Promise<void> {
  return invoke("write_config_file", { instanceId, relPath, contents });
}

// ---- worlds + screenshots ----

export function listWorlds(instanceId: string): Promise<WorldInfo[]> {
  return invoke("list_worlds", { instanceId });
}

export function deleteWorld(instanceId: string, name: string): Promise<void> {
  return invoke("delete_world", { instanceId, name });
}

export function listScreenshots(instanceId: string): Promise<ScreenshotInfo[]> {
  return invoke("list_screenshots", { instanceId });
}

export function readScreenshotData(instanceId: string, name: string): Promise<string> {
  return invoke("read_screenshot_data", { instanceId, name });
}

// ---- global settings ----

export function getSettings(): Promise<GlobalSettings> {
  return invoke("get_settings");
}

export function updateSettings(settings: GlobalSettings): Promise<GlobalSettings> {
  return invoke("update_settings", { settings });
}

// ---- modpacks ----

export function listModpacks(): Promise<Modpack[]> {
  return invoke("list_modpacks");
}

export function createModpackFromInstance(
  instanceId: string,
  name: string,
  description: string
): Promise<Modpack> {
  return invoke("create_modpack_from_instance", { instanceId, name, description });
}

export function deleteModpack(modpackId: string): Promise<void> {
  return invoke("delete_modpack", { modpackId });
}

export function applyModpack(modpackId: string, instanceName: string): Promise<Instance> {
  return invoke("apply_modpack", { modpackId, instanceName });
}

/** Writes a .bpack to the exports folder, returns the path written. */
export function exportModpack(modpackId: string): Promise<string> {
  return invoke("export_modpack", { modpackId });
}

export function importModpack(sourcePath: string): Promise<Modpack> {
  return invoke("import_modpack", { sourcePath });
}

/** Import a local Modrinth .mrpack file into a new instance. */
export function importMrpack(sourcePath: string): Promise<Instance> {
  return invoke("import_mrpack", { sourcePath });
}

/** Install a Modrinth modpack by project id/slug into a new instance. */
export function installModrinthModpack(projectId: string): Promise<Instance> {
  return invoke("install_modrinth_modpack", { projectId });
}

export interface UpdateStatus {
  configured: boolean;
  currentVersion: string;
  latestVersion: string | null;
  updateAvailable: boolean;
  url: string | null;
  notes: string | null;
}

export function checkLauncherUpdate(): Promise<UpdateStatus> {
  return invoke("check_launcher_update");
}

export function revealPath(path: string): Promise<void> {
  return invoke("reveal_path", { path });
}

export interface ModrinthSearchResult {
  hits: Array<{
    project_id: string;
    slug: string;
    title: string;
    description: string;
    icon_url: string | null;
    downloads: number;
    categories: string[];
    versions: string[];
    project_type: string;
  }>;
  total_hits: number;
}

export function modrinthSearch(
  query: string,
  opts: {
    mcVersion?: string;
    loader?: string;
    projectType?: string;
    categories?: string[];
    index?: string; // relevance | downloads | follows | newest | updated
  } = {}
): Promise<ModrinthSearchResult> {
  return invoke("modrinth_search", {
    query,
    mcVersion: opts.mcVersion,
    loader: opts.loader,
    projectType: opts.projectType,
    categories: opts.categories,
    index: opts.index,
  });
}

/** Install many Modrinth projects (ids or slugs) at once — the default modpack. */
export function installMods(
  instanceId: string,
  projects: string[],
  withDependencies: boolean
): Promise<Instance> {
  return invoke("install_mods", { instanceId, projects, withDependencies });
}

/** The default "Blurred Essentials" modpack slugs. */
export function blurredEssentials(): Promise<string[]> {
  return invoke("blurred_essentials");
}

/** Download + install a Modrinth project into an instance (optionally with its
 * required dependencies). Returns the updated instance. */
export function installModrinthMod(
  instanceId: string,
  projectId: string,
  versionId: string | null,
  withDependencies: boolean
): Promise<Instance> {
  return invoke("install_modrinth_mod", { instanceId, projectId, versionId, withDependencies });
}

export function checkModUpdates(instanceId: string): Promise<ModUpdate[]> {
  return invoke("check_mod_updates", { instanceId });
}

export function updateMod(instanceId: string, filename: string): Promise<Instance> {
  return invoke("update_mod", { instanceId, filename });
}

export function updateAllMods(instanceId: string): Promise<Instance> {
  return invoke("update_all_mods", { instanceId });
}

export interface InstanceLogEvent {
  instanceId: string;
  stream: "stdout" | "stderr";
  line: string;
}

/** Subscribe to log lines for a specific instance. Returns the unlisten fn. */
export async function onInstanceLog(
  instanceId: string,
  cb: (line: string, stream: "stdout" | "stderr") => void
): Promise<UnlistenFn> {
  return listen<InstanceLogEvent>("instance-log", (event) => {
    if (event.payload.instanceId === instanceId) {
      cb(event.payload.line, event.payload.stream);
    }
  });
}

// ---- accounts (offline) ----

/** Create an offline account. Takes no username: the offline identity is always
 * your signed-in Microsoft username (anti-impersonation), and requires a
 * Microsoft account that owns the game to exist first (anti-piracy). */
export function createOfflineAccount(): Promise<Account> {
  return invoke("create_offline_account");
}

export function listAccounts(): Promise<Account[]> {
  return invoke("list_accounts");
}

export function removeAccount(accountId: string): Promise<void> {
  return invoke("remove_account", { accountId });
}

export function setActiveAccount(accountId: string): Promise<Account[]> {
  return invoke("set_active_account", { accountId });
}

/** Change a Microsoft account's skin to the PNG at `url` (variant classic/slim). */
export function setAccountSkin(accountId: string, url: string, variant: string): Promise<Account> {
  return invoke("set_account_skin", { accountId, url, variant });
}

/** Change a Microsoft account's skin from a local PNG file. */
export function setAccountSkinFile(accountId: string, filePath: string, variant: string): Promise<Account> {
  return invoke("set_account_skin_file", { accountId, filePath, variant });
}

export function resetAccountSkin(accountId: string): Promise<Account> {
  return invoke("reset_account_skin", { accountId });
}

// ---- Microsoft (online) sign-in, device-code flow ----

/** Start device-code login; returns the code + URL to show the user. */
export function beginMsaLogin(): Promise<DeviceCodeInfo> {
  return invoke("begin_msa_login");
}

/** Block until the user approves the device code (or it expires), then run the
 * full auth chain and return the new account. Long-running — up to ~15 min. */
export function completeMsaLogin(
  deviceCode: string,
  interval: number,
  expiresIn: number
): Promise<Account> {
  return invoke("complete_msa_login", { deviceCode, interval, expiresIn });
}
