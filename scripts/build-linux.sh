#!/usr/bin/env bash
# Build the Linux bundles (.deb, .rpm, .AppImage) on a Linux machine or WSL.
#
# This is the same build CI runs (.github/workflows/build-linux.yml), just
# driveable by hand. Run it from the repo root:
#
#   ./scripts/build-linux.sh          # install deps if needed, then build
#   ./scripts/build-linux.sh --deps   # install system deps only
#   ./scripts/build-linux.sh --skip-deps
#
# Note on portability: bundles link against the glibc of whatever distro you
# build on. Build on the oldest distro you intend to support, or users on older
# releases get a GLIBC_x.y version error at startup.

set -euo pipefail

cd "$(dirname "$0")/.."

DO_DEPS=1
DO_BUILD=1
case "${1:-}" in
  --deps) DO_BUILD=0 ;;
  --skip-deps) DO_DEPS=0 ;;
  "") ;;
  *) echo "usage: $0 [--deps | --skip-deps]" >&2; exit 2 ;;
esac

install_deps() {
  # libdbus is needed because tao depends on the `dbus` crate; the rest is
  # Tauri v2's documented Linux prerequisite set. webkit2gtk *4.1* (not 4.0) is
  # the one Tauri v2 wants -- 4.0 is the v1 dependency and won't satisfy it.
  if command -v apt-get >/dev/null; then
    sudo apt-get update
    sudo apt-get install -y \
      libwebkit2gtk-4.1-dev libgtk-3-dev libayatana-appindicator3-dev \
      librsvg2-dev libdbus-1-dev libssl-dev patchelf build-essential file wget
  elif command -v dnf >/dev/null; then
    sudo dnf install -y \
      webkit2gtk4.1-devel gtk3-devel libappindicator-gtk3-devel \
      librsvg2-devel dbus-devel openssl-devel patchelf file wget \
      rpm-build "@development-tools"
  elif command -v pacman >/dev/null; then
    sudo pacman -S --needed --noconfirm \
      webkit2gtk-4.1 gtk3 libayatana-appindicator librsvg dbus openssl \
      patchelf base-devel file wget
  else
    echo "!! Unrecognized package manager. Install Tauri's Linux prerequisites"
    echo "!! manually: https://tauri.app/start/prerequisites/"
    exit 1
  fi
}

require() {
  command -v "$1" >/dev/null || {
    echo "!! $1 not found -- install it first ($2)" >&2
    exit 1
  }
}

if [ "$DO_DEPS" = 1 ]; then
  echo "==> Installing system dependencies"
  install_deps
fi

if [ "$DO_BUILD" = 0 ]; then
  exit 0
fi

require node "https://nodejs.org — v18 or newer"
require cargo "https://rustup.rs"

echo "==> Installing frontend dependencies"
if [ -f package-lock.json ]; then npm ci; else npm install; fi

echo "==> Building"
npm run tauri build

echo
echo "==> Done. Bundles:"
find src-tauri/target/release/bundle \
  \( -name '*.deb' -o -name '*.rpm' -o -name '*.AppImage' \) -print
