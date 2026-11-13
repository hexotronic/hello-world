#!/usr/bin/env python3
"""
Compares language models for Lauscher with the app's own prompts and grammars.

eval.json is written by the unit test ModelEvalTest (ModelEval.exportJson): a German fantasy story
in 16 labelled two-minute passages, questions about the book (one asks for a spoiler) and commands
in everyday words. Every model runs in llama-server (the same llama.cpp release and CPU instruction
set as the app: ARMv8.2 dot product + FP16, like the Cortex-A78 cores of a Xiaomi 13T), greedy and
grammar-bound like in the app. The script scores understanding the same way as the app's
"Modelle testen" and measures speed; a phone estimate is derived from the measured numbers.

Usage: model_bench.py --eval eval.json --server llama-server --work DIR --out results.json --summary summary.md [--only id,id]
"""
import argparse
import json
import os
import re
import subprocess
import sys
import time
import urllib.error
import urllib.request

# The phone runs the same code on four Cortex-A78 at 3.0-3.1 GHz with LPDDR5 memory. The CI machine
# (Neoverse N2, 3.4 GHz, server memory) is faster per core and has more memory bandwidth; phones also
# slow down when they get warm. Phone speed is therefore estimated as this share of the measured speed.
PHONE = "Xiaomi 13T (Dimensity 8200-Ultra, 4× Cortex-A78)"
PHONE_LOW, PHONE_HIGH = 0.5, 0.75

MODELS = [
    {"id": "qwen2.5-0.5b", "label": "Qwen2.5 0.5B", "repo": "Qwen/Qwen2.5-0.5B-Instruct-GGUF", "quant": "Q4_K_M", "note": "klein (in der App)"},
    {"id": "qwen2.5-1.5b", "label": "Qwen2.5 1.5B", "repo": "Qwen/Qwen2.5-1.5B-Instruct-GGUF", "quant": "Q4_K_M", "note": "mittel, empfohlen (in der App)"},
    {"id": "qwen2.5-3b", "label": "Qwen2.5 3B", "repo": "Qwen/Qwen2.5-3B-Instruct-GGUF", "quant": "Q4_K_M", "note": "stärkstes (in der App)"},
    {"id": "qwen3-1.7b", "label": "Qwen3 1.7B", "repo": "unsloth/Qwen3-1.7B-GGUF", "quant": "Q4_K_M", "note": "neuer, mittel"},
    {"id": "qwen3-4b-2507", "label": "Qwen3 4B Instruct 2507", "repo": "unsloth/Qwen3-4B-Instruct-2507-GGUF", "quant": "Q4_K_M", "note": "neuer, groß"},
    {"id": "gemma3-4b", "label": "Gemma 3 4B", "repo": "unsloth/gemma-3-4b-it-GGUF", "quant": "Q4_K_M", "note": "neuer, groß"},
    {"id": "llama3.2-3b", "label": "Llama 3.2 3B", "repo": "bartowski/Llama-3.2-3B-Instruct-GGUF", "quant": "Q4_K_M", "note": "Alternative"},
]

PORT = 8091
BASE = f"http://127.0.0.1:{PORT}"


def http(path, body=None, timeout=900):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(BASE + path, data=data, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return json.loads(r.read().decode())


def fetch_json(url):
    req = urllib.request.Request(url, headers={"User-Agent": "Lauscher-Benchmark"})
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.loads(r.read().decode())


def pick_file(repo, quant):
    """The model file with the wanted quantisation (like the app's Hugging Face search)."""
    tree = fetch_json(f"https://huggingface.co/api/models/{repo}/tree/main?recursive=true")
    files = [f for f in tree if f.get("type") == "file" and f["path"].lower().endswith(".gguf")
             and "mmproj" not in f["path"].lower() and not re.search(r"-\d{5}-of-\d{5}\.gguf$", f["path"].lower())]
    want = [f for f in files if re.search(rf"(?<![a-z0-9]){quant.lower()}(?![a-z0-9])", f["path"].lower())]
    if not want:
        raise RuntimeError(f"{repo}: keine {quant}-Datei unter {[f['path'] for f in files]}")
    f = sorted(want, key=lambda f: len(f["path"]))[0]
    return f["path"], (f.get("lfs") or {}).get("size", f.get("size", 0))


def download(repo, path, target):
    if os.path.exists(target) and os.path.getsize(target) > 1_000_000:
        return
    url = f"https://huggingface.co/{repo}/resolve/main/{path}"
    subprocess.run(["curl", "-sSL", "--retry", "4", "--retry-delay", "5", "-o", target + ".part", url], check=True)
    os.rename(target + ".part", target)


# ---------------------------------------------------------------- scoring (as ModelEval.kt)

def parse_time(v):
    parts = v.strip().split(":")
    try:
        nums = [int(p.strip()) for p in parts]
    except ValueError:
        return None
    if len(nums) == 1:
        return nums[0] * 60_000
    if len(nums) == 2:
        return (nums[0] * 60 + nums[1]) * 1000
    if len(nums) == 3:
        return (nums[0] * 3600 + nums[1] * 60 + nums[2]) * 1000
    return None


def number(v):
    m = re.search(r"-?\d+([.,]\d+)?", v)
    return float(m.group(0).replace(",", ".")) if m else None


def value_matches(value, accepted):
    if ".." in accepted:
        lo, hi = (float(x) for x in accepted.split(".."))
        n = number(value)
        return n is not None and lo <= n <= hi
    if ":" in accepted:
        t = parse_time(value)
        return t is not None and t == parse_time(accepted)
    try:
        a = float(accepted)
        n = number(value)
        return n is not None and abs(n - a) < 0.01
    except ValueError:
        return accepted in value.lower()


def parse_json(text):
    try:
        v = json.loads(text.strip())
        return v if isinstance(v, dict) else None
    except (ValueError, AttributeError):
        return None


MOODS = {"CALM", "TENSE", "ACTION", "SAD", "HAPPY", "ROMANTIC", "MYSTERIOUS", "EERIE", "IDYLLIC", "NEUTRAL"}


def score_scenes(gold, output):
    root = parse_json(output)
    hits = {"windows": len(gold), "valid": 0, "moods": 0, "places": 0, "placeCount": sum(1 for g in gold if g["places"]),
            "levels": 0, "cues": 0, "cueCount": sum(1 for g in gold if g["cues"])}
    if root is None or not isinstance(root.get("windows"), list):
        return hits, []
    hits["valid"] = 1
    by_index = {w.get("w"): w for w in root["windows"] if isinstance(w, dict)}
    seen = []
    for i, g in enumerate(gold):
        w = by_index.get(i, {})
        mood = w.get("mood")
        mood = "NONE" if mood == "NONE" else (mood if mood in MOODS else "NEUTRAL")
        level = max(1, min(3, int(w.get("level", 2)))) if isinstance(w.get("level", 2), (int, float)) else 2
        place = w.get("place")
        cues = [c for c in (w.get("cues") or []) if isinstance(c, str)]
        if mood in g["moods"]:
            hits["moods"] += 1
        if g["places"] and place in g["places"]:
            hits["places"] += 1
        if g["levelMin"] <= level <= g["levelMax"] or mood == "NONE":
            hits["levels"] += 1
        if g["cues"] and any(c in g["cues"] for c in cues):
            hits["cues"] += 1
        seen.append(f"{mood}/{level}@{place} {'+'.join(cues)} – {w.get('title', '')}")
    return hits, seen


ASKING = re.compile(r"^(wer|wen|wem|wessen|was|warum|wieso|weshalb|wie|welche[rsmn]?|who|what|why|how|which)\b", re.IGNORECASE)


def sanitize(question, rep):
    """As BookAssistant.sanitize: a question about the book does not move the player."""
    q = question.strip()
    if rep and rep["action"] != "NONE" and q.endswith("?") and ASKING.search(q):
        return dict(rep, action="NONE", value="")
    return rep


def assistant_reply(output):
    root = parse_json(output)
    if root is None:
        return None
    return {"action": str(root.get("action", "NONE")), "value": str(root.get("value", "")).strip(), "answer": str(root.get("answer", "")).strip()}


# ---------------------------------------------------------------- running one model

def ask(system, user, grammar, max_tokens):
    prompt = http("/apply-template", {"messages": [{"role": "system", "content": system}, {"role": "user", "content": user}]})["prompt"]
    started = time.time()
    r = http("/completion", {"prompt": prompt, "grammar": grammar, "n_predict": max_tokens, "temperature": 0, "top_k": 1,
                             "seed": 42, "cache_prompt": True})
    t = r.get("timings", {})
    return r.get("content", ""), {
        "seconds": time.time() - started,
        "prompt_n": t.get("prompt_n", 0), "prompt_ms": t.get("prompt_ms", 0.0),
        "predicted_n": t.get("predicted_n", 0), "predicted_ms": t.get("predicted_ms", 0.0),
    }


def measure(ev, scene_system, assistant_system, batches):
    """One pass of the test story with the given instructions; returns the scores and timings."""
    timings = {"scene": [], "question": [], "command": []}
    per_batch = {}
    for bsize, calls in batches.items():
        hits_sum = {"windows": 0, "valid": 0, "calls": 0, "moods": 0, "places": 0, "placeCount": 0, "levels": 0, "cues": 0, "cueCount": 0}
        seen_all, seconds = [], 0.0
        for call in calls:
            out, t = ask(scene_system, call["user"], call["grammar"], call["maxTokens"])
            if int(bsize) == ev["batch"]:
                timings["scene"].append(t)
            seconds += t["seconds"]
            hits, seen = score_scenes(call["gold"], out)
            hits_sum["calls"] += 1
            for k, v in hits.items():
                hits_sum[k] += v
            seen_all += seen
            print(f"   Szenen ({bsize} je Aufruf): {t['seconds']:.1f} s, {t['prompt_n']} + {t['predicted_n']} Token", flush=True)
        h = hits_sum
        per_batch[bsize] = {
            "moods": h["moods"] / max(1, h["windows"]), "places": h["places"] / max(1, h["placeCount"]),
            "levels": h["levels"] / max(1, h["windows"]), "cues": h["cues"] / max(1, h["cueCount"]),
            "validJson": h["valid"] / max(1, h["calls"]),
            # Seconds for eight passages, comparable between the sizes.
            "secondsPer8": seconds / max(1, h["windows"]) * 8, "seen": seen_all, "hits": h,
        }
    sh = per_batch[str(ev["batch"])]["hits"]

    a = ev["assistant"]
    answers = []
    right = safe = traps = 0
    for q in a["questions"]:
        out, t = ask(assistant_system, q["user"], a["grammar"], a["maxTokens"])
        timings["question"].append(t)
        rep = assistant_reply(out)
        text = (rep or {}).get("answer", "").lower()
        ok = rep is not None and any(e in text for e in q["expectAny"]) and not any(f in text for f in q["forbid"])
        right += ok
        if q["forbid"]:
            traps += 1
            safe += not any(f in text for f in q["forbid"])
        answers.append({"question": q["question"], "answer": (rep or {}).get("answer", out[:200]), "right": ok, "seconds": round(t["seconds"], 1)})

    commands = []
    done = 0
    for c in a["commands"]:
        out, t = ask(assistant_system, c["user"], a["grammar"], a["maxTokens"])
        timings["command"].append(t)
        rep = sanitize(c["text"], assistant_reply(out))
        ok = rep is not None and rep["action"] == c["action"] and (not c["values"] or any(value_matches(rep["value"], v) for v in c["values"]))
        done += ok
        commands.append({"text": c["text"], "action": (rep or {}).get("action"), "value": (rep or {}).get("value"), "want": c["action"], "right": ok})

    every = timings["scene"] + timings["question"] + timings["command"]
    p_n = sum(t["prompt_n"] for t in every)
    p_ms = sum(t["prompt_ms"] for t in every)
    g_n = sum(t["predicted_n"] for t in every)
    g_ms = sum(t["predicted_ms"] for t in every)
    scene_s = sum(t["seconds"] for t in timings["scene"]) / max(1, len(timings["scene"]))
    asked = timings["question"] + timings["command"]
    question_s = sum(t["seconds"] for t in asked) / max(1, len(asked))
    report = {
        "moods": sh["moods"] / max(1, sh["windows"]), "places": sh["places"] / max(1, sh["placeCount"]),
        "levels": sh["levels"] / max(1, sh["windows"]), "cues": sh["cues"] / max(1, sh["cueCount"]),
        "validJson": sh["valid"] / max(1, sh["calls"]),
        "answers": right / max(1, len(a["questions"])), "spoilerSafe": safe / max(1, traps), "commands": done / max(1, len(a["commands"])),
        "promptTps": p_n / (p_ms / 1000) if p_ms else 0, "genTps": g_n / (g_ms / 1000) if g_ms else 0,
        "sceneCall": scene_s, "question": question_s,
        "scenesSeen": per_batch[str(ev["batch"])]["seen"], "answersSeen": answers, "commandsSeen": commands,
        "batches": {k: {kk: vv for kk, vv in v.items() if kk not in ("hits",)} for k, v in per_batch.items()},
    }
    report["understanding"] = (report["moods"] + report["places"] + report["levels"] + report["cues"] + report["answers"] + report["commands"]) / 6
    report["modelSecondsPerHour"] = scene_s / ev["batch"] * ev["windowsPerHour"]
    print(f"   Verstehen {report['understanding'] * 100:.0f} %, Prompt {report['promptTps']:.0f} tok/s, Ausgabe {report['genTps']:.1f} tok/s", flush=True)
    return report


def run_model(m, ev, server, work, threads):
    """Loads the model once and measures it with each version of the instructions; one report per version."""
    if "path" in m:  # a local file (to try the script)
        path, size, target = m["path"], os.path.getsize(m["path"]), m["path"]
    else:
        path, size = pick_file(m["repo"], m["quant"])
        target = os.path.join(work, os.path.basename(path))
    t0 = time.time()
    if "path" not in m:
        download(m["repo"], path, target)
    print(f"== {m['label']}: {path} ({size / 1e6:.0f} MB, Download {time.time() - t0:.0f} s)", flush=True)

    # Versions of the instructions to compare; without them the current ones (and the batch-size experiment).
    variants = ev.get("variants")
    if variants:
        batches = {str(ev["batch"]): ev["scene"]["calls"]}
    else:
        variants = {"": {"scene": ev["scene"]["system"], "assistant": ev["assistant"]["system"]}}
        batches = ev["scene"].get("batches") or {str(ev["batch"]): ev["scene"]["calls"]}

    log = open(os.path.join(work, m["id"] + ".server.log"), "w")
    proc = subprocess.Popen([server, "-m", target, "-c", "4096", "-t", str(threads), "-tb", str(threads), "-b", "512", "-ub", "512",
                             "-np", "1", "--port", str(PORT), "--no-jinja", "--no-webui",
                             # Only the last prompt stays cached, as in the app (no store of older prompts).
                             "--cache-ram", "0"], stdout=log, stderr=subprocess.STDOUT)
    started = time.time()
    reports = []
    try:
        while True:
            if proc.poll() is not None:
                raise RuntimeError("llama-server beendet, siehe " + log.name)
            try:
                if http("/health", timeout=5).get("status") == "ok":
                    break
            except (urllib.error.URLError, ConnectionError, TimeoutError, json.JSONDecodeError):
                pass
            if time.time() - started > 300:
                raise RuntimeError("llama-server startet nicht")
            time.sleep(0.5)
        load = time.time() - started
        for name, v in variants.items():
            if name:
                print(f"-- Anweisungen „{name}“", flush=True)
            r = measure(ev, v["scene"], v["assistant"], batches)
            r.update({"id": m["id"], "label": m["label"] + (f" · {name}" if name else ""), "model": m["label"], "variant": name,
                      "note": m["note"], "file": path, "sizeMb": round(size / 1e6), "load": load})
            reports.append(r)
    finally:
        proc.terminate()
        try:
            proc.wait(timeout=30)
        except subprocess.TimeoutExpired:
            proc.kill()
        log.close()
    if "path" not in m:
        os.remove(target)
    return reports


# ---------------------------------------------------------------- report

def pct(x):
    return f"{x * 100:.0f} %"


def phone(seconds):
    return f"{seconds / PHONE_HIGH:.0f}–{seconds / PHONE_LOW:.0f} s"


def summary(results, failed, machine):
    lines = ["## Lauscher – Sprachmodelle im Vergleich", "",
             f"Gemessen auf {machine} mit 4 Threads, llama.cpp wie in der App (ARMv8.2 dotprod+fp16, gierig, JSON-Grammatik). "
             f"Testgeschichte: 16 Abschnitte, {len(results[0]['answersSeen']) if results else 0} Fragen, "
             f"{len(results[0]['commandsSeen']) if results else 0} Befehle in Alltagssprache.", "",
             "### Verstehen", "",
             "| Modell | Datei | Verstehen | Stimmung | Ort | Stärke | Musik-Merkmale | Fragen | Spoiler-sicher | Befehle | JSON gültig |",
             "|---|---|---|---|---|---|---|---|---|---|---|"]
    for r in results:
        lines.append(f"| **{r['label']}** ({r['note']}) | {r['sizeMb']} MB | **{pct(r['understanding'])}** | {pct(r['moods'])} | {pct(r['places'])} | "
                     f"{pct(r['levels'])} | {pct(r['cues'])} | {pct(r['answers'])} | {pct(r['spoilerSafe'])} | {pct(r['commands'])} | {pct(r['validJson'])} |")
    # The same model with two versions of the instructions: what changed.
    by_model = {}
    for r in results:
        if r.get("variant"):
            by_model.setdefault(r["model"], {})[r["variant"]] = r
    pairs = [(m, v) for m, v in by_model.items() if len(v) >= 2]
    if pairs:
        names = list(pairs[0][1].keys())
        a, b = names[0], names[-1]
        keys = [("understanding", "Verstehen"), ("moods", "Stimmung"), ("places", "Ort"), ("levels", "Stärke"), ("cues", "Merkmale"),
                ("answers", "Fragen"), ("commands", "Befehle")]
        lines += ["", f"### Anweisungen „{a}“ → „{b}“", "",
                  "| Modell | " + " | ".join(k[1] for k in keys) + " | Zeit je Frage |", "|---|" + "---|" * (len(keys) + 1)]
        for model, v in pairs:
            cells = []
            for k, _ in keys:
                d = (v[b][k] - v[a][k]) * 100
                cells.append(f"{pct(v[a][k])} → **{pct(v[b][k])}** ({d:+.0f})")
            cells.append(f"{v[a]['question']:.1f} → {v[b]['question']:.1f} s")
            lines.append(f"| {model} | " + " | ".join(cells) + " |")
        mean = lambda name, k: sum(v[name][k] for _, v in pairs) / len(pairs)
        lines += ["", f"Im Mittel: Verstehen {pct(mean(a, 'understanding'))} → **{pct(mean(b, 'understanding'))}**."]
    lines += ["", "### Geschwindigkeit", "",
              f"Handy-Spalten: geschätzt für {PHONE} als {int(PHONE_LOW * 100)}–{int(PHONE_HIGH * 100)} % der gemessenen Geschwindigkeit "
              "(langsamerer Speicher, Wärmedrosselung). Genaue Werte misst „Modelle testen“ in der App.", "",
              "| Modell | Laden | Prompt lesen | Antwort schreiben | Szenen-Aufruf (8 Abschnitte) | Frage/Befehl | Handy: Frage/Befehl | Handy: Sprachmodell je Stunde Hörbuch |",
              "|---|---|---|---|---|---|---|---|"]
    for r in results:
        lines.append(f"| {r['label']} | {r['load']:.1f} s | {r['promptTps']:.0f} tok/s | {r['genTps']:.1f} tok/s | {r['sceneCall']:.1f} s | "
                     f"{r['question']:.1f} s | {phone(r['question'])} | {r['modelSecondsPerHour'] / PHONE_HIGH / 60:.1f}–{r['modelSecondsPerHour'] / PHONE_LOW / 60:.1f} Min. |")
    sizes = sorted({k for r in results for k in r.get("batches", {})}, key=int, reverse=True)
    if len(sizes) > 1:
        lines += ["", "### Szenen in kleineren Portionen", "",
                  f"Wie viele Abschnitte das Modell in einem Aufruf bekommt (die App nahm bisher {sizes[0]}). "
                  "Zeit jeweils für 8 Abschnitte.", "",
                  "| Modell | " + " | ".join(f"{s} je Aufruf: Stimmung / Ort / Stärke / Merkmale / Zeit" for s in sizes) + " |",
                  "|---|" + "---|" * len(sizes)]
        for r in results:
            cells = []
            for s in sizes:
                b = r["batches"].get(s)
                cells.append("–" if not b else f"{pct(b['moods'])} / {pct(b['places'])} / {pct(b['levels'])} / {pct(b['cues'])} / {b['secondsPer8']:.0f} s")
            lines.append(f"| {r['label']} | " + " | ".join(cells) + " |")
    for f in failed:
        lines.append(f"\n⚠️ {f}")
    for r in results:
        lines += ["", f"<details><summary>{r['label']}: Antworten</summary>", ""]
        for a in r["answersSeen"]:
            answer = a["answer"].replace("\n", " ")
            lines.append(f"- {'✅' if a['right'] else '❌'} *{a['question']}* → {answer[:300]}")
        for c in r["commandsSeen"]:
            lines.append(f"- {'✅' if c['right'] else '❌'} *{c['text']}* → {c['action']} „{c['value']}“ (erwartet {c['want']})")
        lines += ["", "Szenen: " + " · ".join(r["scenesSeen"]), "", "</details>"]
    return "\n".join(lines) + "\n"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--eval", required=True)
    ap.add_argument("--server", required=True)
    ap.add_argument("--work", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--summary", required=True)
    ap.add_argument("--threads", type=int, default=4)
    ap.add_argument("--only", default="")
    ap.add_argument("--machine", default="einem ARM-Rechner")
    ap.add_argument("--local", default="", help="a local GGUF file instead of the downloads (to try the script)")
    args = ap.parse_args()
    ev = json.load(open(args.eval, encoding="utf-8"))
    os.makedirs(args.work, exist_ok=True)
    for f in (args.out, args.summary):
        os.makedirs(os.path.dirname(os.path.abspath(f)), exist_ok=True)
    only = [s for s in args.only.split(",") if s]
    results, failed = [], []
    models = [{"id": "local", "label": "Lokal", "note": "Test", "path": args.local}] if args.local else MODELS
    for m in models:
        if only and m["id"] not in only:
            continue
        try:
            results += run_model(m, ev, args.server, args.work, args.threads)
        except Exception as e:  # one model failing must not hide the others
            import traceback
            traceback.print_exc()
            print(f"!! {m['label']}: {e}", flush=True)
            failed.append(f"{m['label']}: {e}")
        # Partial results survive a time-out of the job.
        json.dump({"phone": PHONE, "phoneFactor": [PHONE_LOW, PHONE_HIGH], "results": results, "failed": failed},
                  open(args.out, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
        open(args.summary, "w", encoding="utf-8").write(summary(results, failed, args.machine))
    if not results:
        sys.exit("Kein Modell lief")


if __name__ == "__main__":
    main()
