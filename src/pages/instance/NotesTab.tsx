import { useState } from "react";
import type { TabProps } from "./InstanceDetail";
import { updateInstance } from "../../lib/tauri";

// Free-text per-instance notes (spec §4.2 — Prism has this too, handy for "why
// did I install this mod" reminders). Persists through update_instance like the
// rest of the instance model.

export function NotesTab({ instance, setInstance }: TabProps) {
  const [notes, setNotes] = useState(instance.notes);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const dirty = notes !== instance.notes;

  async function save() {
    setSaving(true);
    try {
      const updated = await updateInstance({ ...instance, notes });
      setInstance(updated);
      setSaved(true);
      setTimeout(() => setSaved(false), 1500);
    } finally {
      setSaving(false);
    }
  }

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 12, maxWidth: 720 }}>
      <textarea
        value={notes}
        onChange={(e) => setNotes(e.target.value)}
        placeholder="Notes about this instance — mod choices, server details, reminders…"
        style={{
          width: "100%",
          minHeight: 280,
          padding: 14,
          borderRadius: "var(--radius-md)",
          border: "1px solid var(--glass-border)",
          background: "var(--glass-bg-elevated)",
          color: "var(--text-primary)",
          fontSize: 14,
          lineHeight: 1.6,
          resize: "vertical",
          fontFamily: "var(--font-ui)",
        }}
      />
      <div style={{ display: "flex", gap: 10, alignItems: "center" }}>
        <button className="accent" onClick={save} disabled={!dirty || saving} style={{ opacity: dirty && !saving ? 1 : 0.5 }}>
          {saving ? "Saving…" : "Save notes"}
        </button>
        {saved && <span style={{ fontSize: 12, color: "var(--success)" }}>Saved</span>}
      </div>
    </div>
  );
}
