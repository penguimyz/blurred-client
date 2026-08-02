// The water column the whole app floats in (spec §2.2, ocean theme).
//
// Four stacked layers, all pure CSS (see .backdrop* in theme.css) so they cost
// nothing on the JS side and hold still under prefers-reduced-motion:
//   blobs - slow drifting currents of colored light
//   rays  - god rays angling down from an unseen surface
//   motes - marine snow, fine particulate drifting up past the viewport
// One instance, mounted once behind the whole app.
export function Backdrop() {
  return (
    <div className="backdrop" aria-hidden>
      {/* Currents first: they're a colour wash *under* the banded water. */}
      <div className="backdrop-blob b1" />
      <div className="backdrop-blob b2" />
      <div className="backdrop-blob b3" />
      {/* Then the stepped water column and its dithered seams. */}
      <div className="backdrop-bands" />
      <div className="backdrop-dither" />
      <div className="backdrop-rays" />
      <div className="backdrop-motes" />
    </div>
  );
}
