# GPU-Funktionstest der Frame-Generierungs-Shader

Führt die exakt gleichen GLSL-ES-3.00-Shader wie die App per WebGL2 (ANGLE/SwiftShader) in
Headless-Chromium aus und misst an synthetischen Szenen (Kameraschwenks, Subpixel-Bewegung,
bewegtes Objekt vor statischem Hintergrund), wie gut das berechnete Zwischenbild die echte
Zwischenposition trifft (PSNR) – im Vergleich zu Überblenden und Bildwiederholung.

```bash
cd FluxPlayer
./gradlew testDebugUnitTest --tests '*ShaderDumpTest*'   # schreibt app/build/shaders/*.glsl
node tools/shadertest/run.js app/build/shaders > /tmp/result.json   # benötigt playwright
python3 tools/shadertest/summarize.py /tmp/result.json
```
