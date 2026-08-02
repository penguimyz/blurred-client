import { useEffect, useState } from "react";
import { Sidebar } from "./components/Sidebar";
import { Backdrop } from "./components/Backdrop";
import { TitleBar } from "./components/TitleBar";
import { FishSchool } from "./components/FishSchool";
import { SeaLife } from "./components/SeaLife";
import { Bubbles } from "./components/Bubbles";
import { Servers } from "./pages/Servers";
import { Home } from "./pages/Home";
import { Browse } from "./pages/Browse";
import { Modpacks } from "./pages/Modpacks";
import { Accounts } from "./pages/Accounts";
import { Settings } from "./pages/Settings";
import { GlobalLogs } from "./pages/GlobalLogs";
import { Chat } from "./pages/Chat";
import { Cosmetics } from "./pages/Cosmetics";
import { InstanceDetail } from "./pages/instance/InstanceDetail";
import { LoginGate } from "./components/LoginGate";
import { useAccountStore } from "./store/accountStore";
import { useSettingsStore } from "./store/settingsStore";
import { useChatStore } from "./store/chatStore";
import type { NavKey } from "./lib/nav";

export default function App() {
  // Typed against the nav definition, so a tab that isn't a real destination
  // fails to compile rather than silently rendering nothing.
  const [active, setActive] = useState<NavKey>("home");
  // When set, the per-instance detail view takes over the content area,
  // independent of which global tab is "active" underneath it.
  const [openInstance, setOpenInstance] = useState<string | null>(null);
  const { accounts, loaded, refresh } = useAccountStore();
  const refreshSettings = useSettingsStore((s) => s.refresh);
  const settings = useSettingsStore((s) => s.settings);
  const { wire, connect, connected, connecting } = useChatStore();

  useEffect(() => {
    refresh();
    // Load + apply theme/accent as early as possible (does nothing visible if
    // the login gate is up, but means the home screen is themed on arrival).
    refreshSettings();
  }, [refresh, refreshSettings]);

  // Chat listeners are attached app-wide rather than on the Chat page, so DMs
  // still arrive (and the rail's unread pip still lights up) while you're
  // somewhere else in the launcher.
  useEffect(() => {
    if (loaded && accounts.length > 0) wire();
  }, [loaded, accounts.length, wire]);

  // Optional auto-connect. Guarded on `connected || connecting` so a settings
  // save (which re-runs this effect) can't stack a second connection on top of
  // a live one.
  useEffect(() => {
    if (!settings?.chatAutoConnect || connected || connecting) return;
    const account = accounts
      .slice()
      .sort((a, b) => (b.lastUsed ?? "").localeCompare(a.lastUsed ?? ""))[0];
    if (account) connect(account.username).catch(() => {});
  }, [settings?.chatAutoConnect, accounts, connected, connecting, connect]);

  function selectTab(key: NavKey) {
    setActive(key);
    setOpenInstance(null);
  }

  // Per spec 8.1: don't render app content until we know whether an account
  // exists. `loaded` (not accounts.length) gates this so a returning signed-in
  // user doesn't see a flash of the login screen while listAccounts() is still
  // in flight. The window chrome (Backdrop + TitleBar) always renders so the
  // window is movable/closable even on the loading and login screens.
  let content = null;
  if (loaded && accounts.length === 0) {
    content = <LoginGate onSuccess={refresh} />;
  } else if (loaded) {
    content = (
      // paddingTop reserves the title-bar strip so content isn't hidden under it.
      <div
        style={{
          display: "flex",
          height: "100vh",
          paddingTop: "var(--titlebar-height)",
          boxSizing: "border-box",
        }}
      >
        <Sidebar active={active} onSelect={selectTab} />
        <div style={{ flex: 1, overflow: "hidden", minWidth: 0 }}>
          {openInstance ? (
            <InstanceDetail instanceId={openInstance} onBack={() => setOpenInstance(null)} />
          ) : (
            <>
              {active === "home" && <Home onOpenInstance={setOpenInstance} />}
              {active === "browse" && <Browse onOpenInstance={setOpenInstance} />}
              {active === "modpacks" && <Modpacks onOpenInstance={setOpenInstance} />}
              {active === "chat" && <Chat />}
              {active === "cosmetics" && <Cosmetics />}
              {active === "servers" && <Servers />}
              {active === "accounts" && <Accounts />}
              {active === "settings" && <Settings />}
              {active === "logs" && <GlobalLogs onOpenInstance={setOpenInstance} />}
            </>
          )}
        </div>
      </div>
    );
  }

  return (
    <>
      <Backdrop />
      {/* Must come after Backdrop: both sit at z-index -1, so DOM order is what
          puts the creatures in front of the water gradient. */}
      {settings?.seaLifeEnabled && <SeaLife />}
      {settings?.fishEnabled && <FishSchool />}
      {settings?.seaLifeEnabled && <Bubbles />}
      <TitleBar />
      {content}
    </>
  );
}
