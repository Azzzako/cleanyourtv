#!/usr/bin/env bash
# Push a theme folder (config.json, optionally wallpaper.jpg and a wallpapers/ collection, see themes/README.md) to the launcher over
# Wireless debugging with the stock adb binary, then restart Home. Replaces whatever theme or wallpaper was there.
#
# Usage:  my-tv/theme.sh themes/aurora        e.g. themes/aurora, themes/default, themes/six-columns
#         my-tv/theme.sh --none               back to config.xml alone (plain black, no wallpaper)
#         TV=192.168.1.68:<port> my-tv/theme.sh themes/aurora
set -euo pipefail
cd "$(dirname "$0")/.."
TV_IP=192.168.1.68
PKG=com.gathin.tvhome
DIR=/sdcard/Android/data/$PKG/files
THEME="${1:?usage: theme.sh <theme folder> | --none}"

if [ -z "${TV:-}" ]; then
  TV=$(adb devices | awk -v ip="$TV_IP" '$2=="device" && index($1, ip":")==1 {print $1; exit}')
fi
if [ -z "${TV:-}" ] || ! adb -s "$TV" get-state >/dev/null 2>&1; then
  echo "TV not connected: Developer options > Wireless debugging shows the port; adb connect $TV_IP:<port>" >&2
  exit 1
fi

adb -s "$TV" shell "mkdir -p $DIR; rm -rf $DIR/config.json $DIR/wallpaper.jpg $DIR/wallpapers"
if [ "$THEME" != --none ]; then
  [ -f "$THEME/config.json" ] || { echo "no config.json in $THEME" >&2; exit 1; }
  adb -s "$TV" push -q "$THEME/config.json" "$DIR/config.json"
  if [ -f "$THEME/wallpaper.jpg" ]; then adb -s "$TV" push -q "$THEME/wallpaper.jpg" "$DIR/wallpaper.jpg"; fi
  # A rotating collection (themes/scenes): the whole wallpapers/ folder, captions.txt included.
  # adb push cannot create a folder under Android/data, so the shell makes it and the files go one by one.
  if [ -d "$THEME/wallpapers" ]; then
    adb -s "$TV" shell "mkdir -p $DIR/wallpapers"
    for f in "$THEME"/wallpapers/*; do
      [ -f "$f" ] && adb -s "$TV" push -q "$f" "$DIR/wallpapers/$(basename "$f")"
    done
  fi
fi
adb -s "$TV" shell "am force-stop $PKG; am start -n $PKG/.MainActivity >/dev/null 2>&1"
echo "theme: $THEME"
