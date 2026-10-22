#!/usr/bin/env bash
# Switch the launcher's whole look, then rebuild, install and push the matching theme.
#
#   my-tv/skin.sh hacker     HackerRank: navy #0E141E, green #00EA64, JetBrains Mono, CRT lines, blinking cursor
#                           (theme my-tv/themes/hacker)
#   my-tv/skin.sh original   Outfit and the frosted-glass colours (theme my-tv/themes/asael)
#
# What it swaps: launcher/app/src/main/res/values/skin.xml (from my-tv/skins/<skin>/skin.xml) and the variable font
# behind the four UI weights in res/font/outfit_*.xml (the files keep their names). TV=192.168.1.68:<port> as usual.
set -euo pipefail
cd "$(dirname "$0")/.."
SKIN="${1:?usage: my-tv/skin.sh hacker|original}"
RES=launcher/app/src/main/res
case "$SKIN" in
  hacker)   FONT=jetbrains_mono; THEME=my-tv/themes/hacker ;;
  original) FONT=outfit;         THEME=my-tv/themes/asael ;;
  *) echo "unknown skin: $SKIN (hacker or original)" >&2; exit 2 ;;
esac
cp "my-tv/skins/$SKIN/skin.xml" "$RES/values/skin.xml"
for f in "$RES"/font/outfit_extralight.xml "$RES"/font/outfit_light.xml "$RES"/font/outfit_regular.xml "$RES"/font/outfit_medium.xml; do
  sed -i -E "s#android:font=\"@font/[a-z_]+\"#android:font=\"@font/$FONT\"#" "$f"
done
my-tv/build-and-install.sh
my-tv/theme.sh "$THEME"
echo "skin: $SKIN"
