#!/usr/bin/env bash
# my-tv/dis.sh <package> [comment]: disable one package for user 0 and append it to my-tv/disabled.txt.
set -u
cd "$(dirname "$0")"
TV="${TV:-$(adb devices | awk '$2=="device" && index($1,"192.168.1.68:")==1 {print $1; exit}')}"
pkg="$1"; note="${2:-}"
out=$(adb -s "$TV" shell pm disable-user --user 0 "$pkg" 2>&1)
echo "$out"
if adb -s "$TV" shell pm list packages -d | grep -qx "package:$pkg"; then
  grep -q "^$pkg\b" disabled.txt || printf '%-38s # %s\n' "$pkg" "$note" >> disabled.txt
else
  echo "NOT DISABLED: $pkg" >&2; exit 1
fi
