import sys, json, numpy as np
# Reads mono float32 22050 Hz from a raw file; prints simple features.
sr = 22050
x = np.fromfile(sys.argv[1], dtype=np.float32)
n = 2048; hop = 1024
frames = np.lib.stride_tricks.sliding_window_view(x, n)[::hop]
win = np.hanning(n).astype(np.float32)
spec = np.abs(np.fft.rfft(frames * win, axis=1))
freqs = np.fft.rfftfreq(n, 1 / sr)
rms = np.sqrt((frames ** 2).mean(axis=1) + 1e-12)
db = 20 * np.log10(rms + 1e-9)
loud = db[db > db.max() - 40]
centroid = (spec * freqs).sum(1) / (spec.sum(1) + 1e-9)
flux = np.maximum(np.diff(np.log1p(spec), axis=0), 0).sum(1)
thr = np.median(flux) + 1.5 * flux.std()
onsets = ((flux[1:-1] > thr) & (flux[1:-1] >= flux[:-2]) & (flux[1:-1] >= flux[2:])).sum()
dur = len(x) / sr
# chroma over 55..2000 Hz
m = (freqs > 55) & (freqs < 2000)
pc = np.round(12 * np.log2(freqs[m] / 440.0)).astype(int) % 12
chroma = np.zeros(12)
e = (spec[:, m] ** 2).sum(0)
np.add.at(chroma, pc, e)
chroma /= chroma.sum()
major = np.array([6.35,2.23,3.48,2.33,4.38,4.09,2.52,5.19,2.39,3.66,2.29,2.88])
minor = np.array([6.33,2.68,3.52,5.38,2.60,3.53,2.54,4.75,3.98,2.69,3.34,3.17])
def best(p):
    return max(np.corrcoef(np.roll(p, k), chroma)[0, 1] for k in range(12))
print(json.dumps({
    "dur": round(dur, 1),
    "loud_db": round(float(np.median(loud)), 1),
    "dyn_db": round(float(np.percentile(loud, 95) - np.percentile(loud, 10)), 1),
    "centroid": int(np.median(centroid)),
    "onsets_per_s": round(onsets / dur, 2),
    "major": round(float(best(major)), 3),
    "minor": round(float(best(minor)), 3),
}))
