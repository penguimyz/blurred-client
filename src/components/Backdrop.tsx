// The dynamic, softly-animated backdrop the glass panels float on (spec §2.2).
// Pure CSS (see .backdrop* in theme.css) so it costs nothing on the JS side and
// pauses under prefers-reduced-motion. One instance, mounted once behind the
// whole app.
export function Backdrop() {
  return (
    <div className="backdrop" aria-hidden>
      <div className="backdrop-blob b1" />
      <div className="backdrop-blob b2" />
      <div className="backdrop-blob b3" />
    </div>
  );
}
