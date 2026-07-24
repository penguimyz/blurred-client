import { useEffect, useState } from "react";
import type { TabProps } from "./InstanceDetail";
import type { ScreenshotInfo } from "../../types/instance";
import { listScreenshots, readScreenshotData } from "../../lib/tauri";
import { formatBytes, formatDate } from "../../lib/format";

// In-launcher screenshot gallery (spec §4.2). Thumbnails are lazy-loaded as
// base64 data URLs one at a time (see read_screenshot_data) rather than wiring
// up the asset:// protocol scope — a screenshots folder is usually a handful of
// images, so on-demand base64 is the simpler, self-contained path.

function Thumb({ instanceId, shot, onOpen }: { instanceId: string; shot: ScreenshotInfo; onOpen: (src: string) => void }) {
  const [src, setSrc] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    readScreenshotData(instanceId, shot.name)
      .then((d) => alive && setSrc(d))
      .catch(() => {});
    return () => {
      alive = false;
    };
  }, [instanceId, shot.name]);

  return (
    <div className="glass-card" style={{ padding: 8, borderRadius: "var(--radius-md)" }}>
      <div
        onClick={() => src && onOpen(src)}
        style={{
          width: "100%",
          aspectRatio: "16 / 9",
          borderRadius: "var(--radius-sm)",
          background: "rgba(0,0,0,0.3)",
          overflow: "hidden",
          cursor: src ? "zoom-in" : "default",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
        }}
      >
        {src ? (
          <img src={src} alt={shot.name} style={{ width: "100%", height: "100%", objectFit: "cover" }} />
        ) : (
          <span style={{ fontSize: 11, color: "var(--text-tertiary)" }}>Loading…</span>
        )}
      </div>
      <div style={{ fontSize: 11, marginTop: 6, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }} title={shot.name}>
        {shot.name}
      </div>
      <div style={{ fontSize: 10, color: "var(--text-tertiary)" }}>
        {formatBytes(shot.sizeBytes)} · {formatDate(shot.modified)}
      </div>
    </div>
  );
}

export function ScreenshotsTab({ instance }: TabProps) {
  const [shots, setShots] = useState<ScreenshotInfo[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [lightbox, setLightbox] = useState<string | null>(null);

  useEffect(() => {
    listScreenshots(instance.id).then(setShots).catch((e) => setError(String(e)));
  }, [instance.id]);

  return (
    <div>
      {error && <div style={{ color: "var(--danger)", fontSize: 13, marginBottom: 12 }}>{error}</div>}
      {shots.length === 0 ? (
        <div style={{ color: "var(--text-tertiary)", fontSize: 13 }}>
          No screenshots yet. In-game screenshots (F2) show up here.
        </div>
      ) : (
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(220px, 1fr))", gap: 12 }}>
          {shots.map((s) => (
            <Thumb key={s.name} instanceId={instance.id} shot={s} onOpen={setLightbox} />
          ))}
        </div>
      )}

      {lightbox && (
        <div
          onClick={() => setLightbox(null)}
          style={{
            position: "fixed",
            inset: 0,
            background: "rgba(0,0,0,0.8)",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            zIndex: 100,
            cursor: "zoom-out",
            padding: 40,
          }}
        >
          <img src={lightbox} alt="" style={{ maxWidth: "100%", maxHeight: "100%", borderRadius: "var(--radius-md)" }} />
        </div>
      )}
    </div>
  );
}
