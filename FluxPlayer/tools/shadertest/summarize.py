import json, sys
r = json.load(open(sys.argv[1]))
if r.get('error'):
    print('ERROR', r['error']); sys.exit(1)
print('compile:', ', '.join(f"{k}={v}" for k, v in r['compile'].items()))
for s in r['scenes']:
    extra = ''
    if s.get('psnrDual') is not None: extra += f" dual {s['psnrDual']:6}"
    if s.get('psnrSlowmoAvg') is not None: extra += f" | Zeitlupe-Ø(6 Zeitpunkte) {s['psnrSlowmoAvg']:6}"
    print(f"{s['name']:38s} {s['quality'][:5]:5s} flow {s['flowSize']:7s} acc {s['flowAccuracy']:5}% | PSNR motion {s['psnrMotion']:6} blend {s['psnrBlend']:6} repeat {s['psnrRepeat']:6}{extra}")
for u in r.get('upscale', []):
    print('Upscale 2x', u['name'] + ':', ', '.join(f"{k} {v}" for k, v in u.items() if k != 'name'))
for k, v in (r.get('bench') or {}).items():
    print(f"Laufzeit {k}: {v} ms")
