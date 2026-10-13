#!/usr/bin/env bash
# Prints public API signatures for the classes listed in the given file (one per line).
#   net.minecraft.world.level.block.Block      -> javap -public of that class
#   grep:<regex>                               -> every class name in the game jar matching <regex>
#   # comment / blank lines                    -> ignored
# Requires `./gradlew createMinecraftArtifacts` to have run first.
set -uo pipefail

LIST="${1:?usage: api-probe.sh <class-list>}"

GAME_JAR=$(find build/moddev/artifacts -maxdepth 1 -name 'neoforge-*.jar' ! -name '*-sources.jar' 2>/dev/null | head -n 1)
NEO_JAR=$(find "$HOME/.gradle/caches" -name 'neoforge-*-universal.jar' 2>/dev/null | head -n 1)

echo "== game jar: ${GAME_JAR:-<missing>}"
echo "== neoforge jar: ${NEO_JAR:-<missing>}"
if [[ -z "${GAME_JAR}" ]]; then
  echo "Candidate jars:"
  find build "$HOME/.gradle/caches" -name '*.jar' 2>/dev/null | grep -iE 'neoforge|minecraft|moddev' | head -n 50
  exit 1
fi

CP="${GAME_JAR}${NEO_JAR:+:${NEO_JAR}}"
ALL_CLASSES=$(mktemp)
{ unzip -Z1 "${GAME_JAR}"; [[ -n "${NEO_JAR}" ]] && unzip -Z1 "${NEO_JAR}"; } \
  | grep '\.class$' | sed -e 's/\.class$//' -e 's#/#.#g' | sort -u > "${ALL_CLASSES}"
echo "== $(wc -l < "${ALL_CLASSES}") classes on the probe classpath"

while IFS= read -r line || [[ -n "${line}" ]]; do
  line="${line%%#*}"
  line="$(echo "${line}" | xargs)"
  [[ -z "${line}" ]] && continue
  if [[ "${line}" == grep:* ]]; then
    pattern="${line#grep:}"
    echo "===== GREP ${pattern}"
    grep -E "${pattern}" "${ALL_CLASSES}" | grep -v '\$[0-9]' || echo "(no match)"
  else
    echo "===== CLASS ${line}"
    javap -public -cp "${CP}" "${line}" 2>&1 || true
  fi
done < "${LIST}"
