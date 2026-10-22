#!/usr/bin/env bash
# Put a wallpaper behind the launcher, or take it away, over Wireless debugging with the stock adb binary.
# The launcher reads config.json and wallpaper.jpg from its files folder on the TV and overlays them on config.xml
# (themes/README.md lists every key). Keep the picture dark, 1920x1080 and under about 300 KB.
#
# Usage:  my-tv/wallpaper.sh <picture.jpg> [dim 0-90]     e.g. my-tv/wallpaper.sh ~/Imágenes/fondo.jpg 40
#         my-tv/wallpaper.sh --none                       back to the plain black background
#         TV=192.168.1.68:<port> my-tv/wallpaper.sh ...    use a specific adb serial
set -euo pipefail
TV_IP=192.168.1.68
PKG=com.gathin.tvhome
DIR=/sdcard/Android/data/$PKG/files
ARG="${1:?usage: wallpaper.sh <picture.jpg> [dim 0-90] | --none}"
DIM="${2:-0}"

if [ -z "${TV:-}" ]; then
  TV=$(adb devices | awk -v ip="$TV_IP" '$2=="device" && index($1, ip":")==1 {print $1; exit}')
fi
if [ -z "${TV:-}" ] || ! adb -s "$TV" get-state >/dev/null 2>&1; then
  echo "TV not connected: Developer options > Wireless debugging shows the port; adb connect $TV_IP:<port>" >&2
  exit 1
fi

adb -s "$TV" shell "mkdir -p $DIR"
if [ "$ARG" = --none ]; then
  adb -s "$TV" shell "rm -rf $DIR/wallpaper.jpg $DIR/config.json $DIR/wallpapers"
else
  [ -f "$ARG" ] || { echo "no such file: $ARG" >&2; exit 1; }
  TMP=$(mktemp)
  printf '{ "wallpaper": true, "wallpaper_dim": %d }\n' "$DIM" > "$TMP"
  adb -s "$TV" push "$ARG" "$DIR/wallpaper.jpg" >/dev/null
  adb -s "$TV" shell "rm -rf $DIR/wallpapers"
  adb -s "$TV" push "$TMP" "$DIR/config.json" >/dev/null
  rm -f "$TMP"
fi
# Restart Home so it reads the files again.
adb -s "$TV" shell "am force-stop $PKG; am start -n $PKG/.MainActivity >/dev/null"
echo "done"
