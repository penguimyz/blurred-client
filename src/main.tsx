import React from "react";
import ReactDOM from "react-dom/client";
import App from "./App";
import { LogWindow } from "./pages/LogWindow";
import "./styles/theme.css";

document.documentElement.setAttribute("data-theme", "dark");
// The last style used (see applyStyle), until the real settings arrive.
document.documentElement.setAttribute("data-style", localStorage.getItem("blurred.uiStyle") ?? "ocean");

/**
 * One bundle, two roots.
 *
 * The popped-out log window (see `lib/logWindow.ts`) loads this same
 * `index.html` with `?view=logs`, so the choice of what to mount is made here
 * rather than by building a second entry point. That keeps the log window on
 * exactly the components the launcher uses, with no risk of the two drifting.
 */
const params = new URLSearchParams(window.location.search);
const isLogWindow = params.get("view") === "logs";

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    {isLogWindow ? <LogWindow instanceId={params.get("instance")} /> : <App />}
  </React.StrictMode>
);
