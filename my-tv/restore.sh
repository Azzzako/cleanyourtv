#!/usr/bin/env bash
# Put the TCL G08 (Smart TV Pro, Google TV 14) back the way it was on 2026-10-21 before the debloat:
#   1. re-enable every package listed in my-tv/disabled.txt (the log of what this session disabled),
#   2. give Home back to the stock Google TV launcher,
#   3. undo Private DNS (it was unset before: delete the keys),
#   4. optionally (--remove-launcher) uninstall the custom launcher.
# Nothing was uninstalled, so this brings back everything. Backup of the original state: backup/20261021-213511/.
#
# Usage:  my-tv/restore.sh [--dry-run] [--remove-launcher]
#   TV=192.168.1.68:<port> my-tv/restore.sh     use a specific adb serial (Wireless debugging port changes)
set -u
cd "$(dirname "$0")"
DRY=0; REMOVE_LAUNCHER=0
for a in "$@"; do
  case "$a" in
    --dry-run) DRY=1 ;;
    --remove-launcher) REMOVE_LAUNCHER=1 ;;
    *) echo "unknown option: $a" >&2; exit 2 ;;
  esac
done

TV_IP=192.168.1.68
STOCK_HOME=com.google.android.apps.tv.launcherx/.home.HomeActivity
NEW_LAUNCHER=com.gathin.tvhome
LIST=disabled.txt

# Pick the adb serial: $TV, else whatever 192.168.1.68 is already connected as, else the mDNS port.
if [ -z "${TV:-}" ]; then
  TV=$(adb devices | awk -v ip="$TV_IP" '$2=="device" && index($1, ip":")==1 {print $1; exit}')
fi
if [ -z "${TV:-}" ]; then
  PORT=$(adb mdns services 2>/dev/null | awk -v ip="$TV_IP" '/_adb-tls-connect/ && index($3, ip":")==1 {split($3,a,":"); print a[2]; exit}')
  [ -n "$PORT" ] && adb connect "$TV_IP:$PORT" >/dev/null && TV="$TV_IP:$PORT"
fi
if [ -z "${TV:-}" ] || ! adb -s "$TV" get-state >/dev/null 2>&1; then
  echo "TV not connected. Look up the port under Developer options > Wireless debugging and run:" >&2
  echo "  adb connect $TV_IP:<port>   then   TV=$TV_IP:<port> $0" >&2
  exit 1
fi
echo "TV: $TV"

run() {  # one adb shell call per command: chained pm commands can stop silently after the first
  if [ "$DRY" = 1 ]; then echo "would run: $*"; else echo "+ $*"; adb -s "$TV" shell "$@"; fi
}

# 1. Stock launcher first and make it Home, so the setup wizard never gets Home to itself
#    (its RecoveryActivity takes Home when it is enabled and no launcher is the default).
run pm enable --user 0 "${STOCK_HOME%%/*}"
run cmd package set-home-activity --user 0 "$STOCK_HOME"

# 2. Re-enable the rest, newest first. Lines are "package  # comment"; blank lines and comments are skipped.
grep -v '^\s*#' "$LIST" | awk 'NF {print $1}' | tac | grep -vx "${STOCK_HOME%%/*}" | while read -r pkg; do
  run pm enable --user 0 "$pkg" </dev/null
done
run cmd package set-home-activity --user 0 "$STOCK_HOME"

# 3. Private DNS was never set on this TV.
run settings delete global private_dns_specifier
run settings delete global private_dns_mode

# 4. Custom launcher: its notification access (player card), then the app itself if asked.
run cmd notification disallow_listener $NEW_LAUNCHER/$NEW_LAUNCHER.MediaListener
if [ "$REMOVE_LAUNCHER" = 1 ]; then
  run pm uninstall "$NEW_LAUNCHER"
fi

if [ "$DRY" = 0 ]; then
  echo
  echo "Still disabled now (should be empty):"
  adb -s "$TV" shell pm list packages -d
  echo "Home:"
  adb -s "$TV" shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1
fi
