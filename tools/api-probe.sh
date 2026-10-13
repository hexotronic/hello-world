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
  echo "===== ALL CLASSES (one package per line)"
  grep -v '\$[0-9]' "${ALL_CLASSES}" | awk -F. '{cls=$NF; pkg=$0; sub("\\." cls "$", "", pkg); a[pkg]=a[pkg] " " cls} END {for (p in a) print p ":" a[p]}' | sort
fi

# PROBE_COMPACT=1 joins each class dump into a single line (members separated by " ;; ") so that CI log
# readers limited by line count still receive everything.
compact() {
  if [[ "${PROBE_COMPACT:-0}" == "1" ]]; then
    grep -v '^Compiled from' | sed -e 's/^  *//' | awk 'BEGIN { ORS = " ;; " } { print } END { printf "\n" }'
  else
    cat
  fi
}

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
      javap -protected -cp "${CP}" "${cls}" 2>&1 | grep -E -- "class |interface |record |enum |${filter}" | compact || true
    else
      javap -protected -cp "${CP}" "${cls}" 2>&1 | compact || true
    fi
  fi
done < "${LIST}"
