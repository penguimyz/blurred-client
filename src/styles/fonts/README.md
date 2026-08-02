# Bundled fonts

## Silkscreen

Used for UI chrome — headings, buttons, labels, nav, tags — in the pixel-art
theme. Body copy, chat and log output deliberately stay on the sans/mono stacks
in `theme.css`: Silkscreen is a display face and a paragraph of it is painful to
read.

- Designer: Jason Kottke
- Source: https://fonts.google.com/specimen/Silkscreen
- License: SIL Open Font License 1.1 (see `OFL.txt`)

Vendored as `woff2` rather than loaded from a CDN because the app runs offline
and a strict CSP would block the request anyway. Both weights together are
~16 KB.

To update: pull the `woff2` URLs from the Google Fonts `css2` endpoint **with a
modern browser User-Agent** — with a default curl UA it serves TTF instead,
which is roughly three times the size.
