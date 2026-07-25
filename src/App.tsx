import { useEffect, useState } from "react";
import { Sidebar } from "./components/Sidebar";
import { Backdrop } from "./components/Backdrop";
import { TitleBar } from "./components/TitleBar";
import { Home } from "./pages/Home";
import { Browse } from "./pages/Browse";
import { Modpacks } from "./pages/Modpacks";
import { Accounts } from "./pages/Accounts";
import { Settings } from "./pages/Settings";
import { GlobalLogs } from "./pages/GlobalLogs";
import { InstanceDetail } from "./pages/instance/InstanceDetail";
import { LoginGate } from "./components/LoginGate";
import { useAccountStore } from "./store/accountStore";
import { useSettingsStore } from "./store/settingsStore";

export default function App() {
  const [active, setActive] = useState("home");
  // When set, the per-instance detail view takes over the content area,
  // independent of which global tab is "active" underneath it.
  const [openInstance, setOpenInstance] = useState<string | null>(null);
  const { accounts, loaded, refresh } = useAccountStore();
  const refreshSettings = useSettingsStore((s) => s.refresh);

  useEffect(() => {
    refresh();
    // Load + apply theme/accent as early as possible (does nothing visible if
    // the login gate is up, but means the home screen is themed on arrival).
    refreshSettings();
  }, [refresh, refreshSettings]);

  function selectTab(key: string) {
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
      // paddingTop reserves the 32px title-bar strip so content isn't hidden under it.
      <div style={{ display: "flex", height: "100vh", paddingTop: 32, boxSizing: "border-box" }}>
        <Sidebar active={active} onSelect={selectTab} />
        <div style={{ flex: 1, overflow: "hidden" }}>
          {openInstance ? (
            <InstanceDetail instanceId={openInstance} onBack={() => setOpenInstance(null)} />
          ) : (
            <>
              {active === "home" && <Home onOpenInstance={setOpenInstance} />}
              {active === "browse" && <Browse onOpenInstance={setOpenInstance} />}
              {active === "modpacks" && <Modpacks onOpenInstance={setOpenInstance} />}
              {active === "accounts" && <Accounts />}
              {active === "settings" && <Settings />}
              {active === "logs" && <GlobalLogs />}
            </>
          )}
        </div>
      </div>
    );
  }

  return (
    <>
      <Backdrop />
      <TitleBar />
      {content}
    </>
  );
}
