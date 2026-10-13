#!/usr/bin/env bash
# Prints public API signatures for the classes listed in the given file (one entry per line).
#   net.minecraft.world.level.block.Block              -> javap -protected of that class
#   net.minecraft.world.level.block.Block | use|tick   -> only members whose line matches the regex
#   grep:<regex>                                       -> every class name on the classpath matching <regex>
#   # comment / blank lines                            -> ignored
# Requires `./gradlew createMinecraftArtifacts` to have run first.
# Set PROBE_LIST_ALL=1 to also print every class name on the classpath.
set -uo pipefail

LIST="${1:?usage: api-probe.sh <class-list>}"
CACHE="${GRADLE_USER_HOME:-$HOME/.gradle}/caches"

GAME_JAR=$(find "${CACHE}/neoformruntime/artifacts" -maxdepth 1 -name 'minecraft_*_client.jar' 2>/dev/null | head -n 1)
NEO_JAR=$(find "${CACHE}/modules-2" -name 'neoforge-*-universal.jar' 2>/dev/null | head -n 1)

echo "== game jar: ${GAME_JAR:-<missing>}"
echo "== neoforge jar: ${NEO_JAR:-<missing>}"
if [[ -z "${GAME_JAR}" ]]; then
  find build "${CACHE}" -name '*.jar' 2>/dev/null | grep -iE 'neoforge|minecraft|moddev' | head -n 50
  exit 1
fi

CP="${GAME_JAR}${NEO_JAR:+:${NEO_JAR}}"
ALL_CLASSES=$(mktemp)
{ unzip -Z1 "${GAME_JAR}"; [[ -n "${NEO_JAR}" ]] && unzip -Z1 "${NEO_JAR}"; } \
  | grep '\.class$' | sed -e 's/\.class$//' -e 's#/#.#g' | sort -u > "${ALL_CLASSES}"
echo "== $(wc -l < "${ALL_CLASSES}") classes on the probe classpath"
if [[ "${PROBE_LIST_ALL:-0}" == "1" ]]; then
  echo "===== ALL CLASSES"
  grep -v '\$[0-9]' "${ALL_CLASSES}"
fi

while IFS= read -r line || [[ -n "${line}" ]]; do
  line="${line%%#*}"
  [[ -z "${line//[[:space:]]/}" ]] && continue
  if [[ "${line}" == grep:* ]]; then
    pattern="$(echo "${line#grep:}" | xargs)"
    echo "===== GREP ${pattern}"
    grep -E "${pattern}" "${ALL_CLASSES}" | grep -v '\$[0-9]' || echo "(no match)"
  else
    cls="$(echo "${line%%|*}" | xargs)"
    filter=""
    [[ "${line}" == *"|"* ]] && filter="$(echo "${line#*|}" | xargs)"
    echo "===== CLASS ${cls}${filter:+ (filter: ${filter})}"
    if [[ -n "${filter}" ]]; then
      javap -protected -cp "${CP}" "${cls}" 2>&1 | grep -E -- "class |interface |record |enum |${filter}" || true
    else
      javap -protected -cp "${CP}" "${cls}" 2>&1 || true
    fi
  fi
done < "${LIST}"
