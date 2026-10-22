#!/usr/bin/env bash
# Build the Clean Your TV launcher (launcher/, settings in launcher/app/src/main/res/values/config.xml) and install it
# as Home on the TCL G08 (Smart TV Pro, Google TV 14) over Wireless debugging with the stock adb binary.
#
# Usage:  my-tv/build-and-install.sh                      build, install, make it Home (no accessibility helper)
#         INPUT_KEY_SERVICE=1 my-tv/build-and-install.sh  also bind InputKeyService: remote app buttons work inside apps
#                                                        too, but holding volume then moves ONE step per press here
#         TV=192.168.1.68:<port> my-tv/build-and-install.sh   use a specific adb serial (the port changes)
#
# Order (same as launcher/build-and-install.sh): install, start the new launcher, and only then disable the stock one
# and set Home. The setup wizard (setupwraith) must already be disabled, after the Wi-Fi network was re-saved in
# Settings, or its RecoveryActivity takes Home.
set -euo pipefail
cd "$(dirname "$0")/.."
TV_IP=192.168.1.68
PKG=com.gathin.tvhome
STOCK=com.google.android.apps.tv.launcherx
HOME_QUERY='cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME'

if [ -z "${TV:-}" ]; then
  TV=$(adb devices | awk -v ip="$TV_IP" '$2=="device" && index($1, ip":")==1 {print $1; exit}')
fi
if [ -z "${TV:-}" ]; then
  PORT=$(adb mdns services 2>/dev/null | awk -v ip="$TV_IP" '/_adb-tls-connect/ && index($3, ip":")==1 {split($3,a,":"); print a[2]; exit}')
  [ -n "$PORT" ] && adb connect "$TV_IP:$PORT" >/dev/null && TV="$TV_IP:$PORT"
fi
if [ -z "${TV:-}" ] || ! adb -s "$TV" get-state >/dev/null 2>&1; then
  echo "TV not connected: Developer options > Wireless debugging shows the port; adb connect $TV_IP:<port>" >&2
  exit 1
fi
sh() { adb -s "$TV" shell "$@" | tr -d '\r'; }
echo "TV: $TV"

# Build.
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/sdk}"
[ -f launcher/local.properties ] || echo "sdk.dir=$ANDROID_HOME" > launcher/local.properties
(cd launcher && ./gradlew assembleRelease -q --console=plain)
APK=launcher/app/build/outputs/apk/release/app-release.apk

# Push and install with pm (adb install can hang on Android 14 waiting for an on-screen prompt).
adb -s "$TV" push "$APK" /data/local/tmp/tvhome.apk >/dev/null
[ "$(md5sum < "$APK" | cut -c1-32)" = "$(sh md5sum /data/local/tmp/tvhome.apk | cut -c1-32)" ] || { echo "md5 mismatch after push" >&2; exit 1; }
OUT=$(sh pm install -r /data/local/tmp/tvhome.apk); sh rm -f /data/local/tmp/tvhome.apk
echo "$OUT"; case "$OUT" in *Success*) ;; *) echo "pm install failed; Home left alone." >&2; exit 1 ;; esac

# Start it before touching the stock launcher.
START=$(sh am start -W -n $PKG/.MainActivity)
printf '%s\n' "$START" | grep -qF 'Status: ok' || { echo "$START"; echo "launcher did not start; Home left alone." >&2; exit 1; }
sh pm grant $PKG android.permission.ACCESS_FINE_LOCATION || true    # Wi-Fi name on the network pill
sh cmd package compile -m speed -f $PKG >/dev/null
if ! sh pm list packages -d | grep -qx "package:$STOCK"; then sh pm disable-user --user 0 $STOCK; fi
sh cmd package set-home-activity --user 0 $PKG/.MainActivity

# Notification access for the Spotify player card (NowPlaying reads its MediaSession); kept across reinstalls,
# granted again here in case it was revoked.
sh cmd notification allow_listener $PKG/$PKG.MediaListener || true

HOME_NOW=$(sh "$HOME_QUERY" | tail -n 1)
case "$HOME_NOW" in
  $PKG/*) echo "Home resolves to $HOME_NOW" ;;
  *) echo "Home resolves to '$HOME_NOW', not the new launcher: rolling back to $STOCK" >&2
     sh pm enable --user 0 $STOCK; sh cmd package set-home-activity --user 0 $STOCK/.home.HomeActivity; exit 1 ;;
esac

# InputKeyService: the remote's Netflix / Prime Video / YouTube / Apple TV buttons (app_keys in config.xml).
# TCL refuses to bind an app's accessibility service unless it holds AUTO_START. A reinstall drops the service,
# so it is written back every time (cleared first so Android binds it again).
if [ "${INPUT_KEY_SERVICE:-0}" = 1 ]; then
  SVC=$PKG/.InputKeyService
  sh cmd appops set $PKG AUTO_START allow || true
  CUR=$(sh settings get secure enabled_accessibility_services)
  case "$CUR" in
    null|'') NEW=$SVC ;;
    *InputKeyService*) NEW=$CUR ;;
    *) NEW="$CUR:$SVC" ;;
  esac
  sh settings put secure enabled_accessibility_services "''"
  sh settings put secure accessibility_enabled 0
  sleep 1
  sh settings put secure enabled_accessibility_services "$NEW"
  sh settings put secure accessibility_enabled 1
  sleep 2
  echo "accessibility: $(sh settings get secure enabled_accessibility_services)"
  sh dumpsys accessibility | grep "Bound services" | grep -q "Service\[" && echo "InputKeyService bound" || echo "WARNING: InputKeyService not bound" >&2
fi

# Prove Home goes to the new launcher, not just that it resolves there.
sh input keyevent KEYCODE_HOME; sleep 2
sh dumpsys activity activities | grep mFocusedApp || true
