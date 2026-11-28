import json,sys
r=json.load(open(sys.argv[1]))
if r.get('error'): print('ERROR', r['error']); sys.exit(1)
print('compile:', ', '.join(f"{k}={v}" for k,v in r['compile'].items()))
for s in r['scenes']:
    print(f"{s['name']:42s} {s['quality'][:4]} flow {s['flowSize']:7s} exp {str(s['expected']):12s} mean {str(s['meanFlow']):16s} acc {s['flowAccuracy']:5}% | PSNR motion {s['psnrMotion']:6} blend {s['psnrBlend']:6} repeat {s['psnrRepeat']:6}")
