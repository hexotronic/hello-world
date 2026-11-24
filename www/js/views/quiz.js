// Training: Multiple Choice und Eintippen für Pflanzennamen, botanische Namen, Familien und Merkmale.
import { app } from '../app.js';
import { h, topbar, btn, segmented, field, toggle, toast, emptyState } from '../ui.js';
import { icon } from '../icons.js';
import { allCards, listDecks, addLog, saveCards, displayName, saveSettings } from '../db.js';
import { photoURL, lazyPhoto } from '../images.js';
import { openZoom } from '../cardview.js';
import { shuffle, norm, checkAnswer, checkSpoken, speak } from '../util.js';
import { speechSupported, micButton, SPEECH_LANGS } from '../speech.js';
import { FAMILIES, familyKey, sameFamily, acceptedNames, mainName, getNameMode } from '../plants.js';

const MODES = [
  { id: 'mc-photo', label: 'Foto → Name', desc: 'Wähle aus vier Namen', icon: 'image' },
  { id: 'type-photo', label: 'Foto → Name eintippen', desc: 'Tippfehler werden erkannt', icon: 'edit' },
  { id: 'speak-photo', label: 'Foto → Name sprechen', desc: 'Mit Spracherkennung, auch botanisch', icon: 'mic' },
  { id: 'mc-latin', label: 'Deutsch → Botanisch', desc: 'Lateinische Namen lernen', icon: 'leaf' },
  { id: 'mc-german', label: 'Botanisch → Deutsch', desc: 'Andersherum', icon: 'leaf' },
  { id: 'mc-family', label: 'Foto → Familie', desc: 'Pflanzenfamilien erkennen', icon: 'layers' },
  { id: 'mc-lifeform', label: 'Foto → Lebensform', desc: 'Baum, Staude, Geophyt …', icon: 'flower' },
  { id: 'features', label: 'Merkmale → Name', desc: 'Nur anhand der Beschreibung', icon: 'search' },
];

const prefs = { mode: 'mc-photo', deckId: '', count: 10, tricky: true, expect: 'both' };

const nameOf = c => mainName(c.plant) || c.title || '';

/** Erlaubte Antworten beim Eintippen/Sprechen: nur deutsch, nur botanisch oder beides. */
function spokenAnswers(c, expect) {
  const p = c.plant || {};
  if (expect === 'la') return [p.latinName].filter(Boolean);
  if (expect === 'de') return [p.deName, c.title].filter(Boolean);
  return acceptedNames(c);
}

function eligible(c, mode, expect = 'both') {
  if (c.type !== 'plant' || c.suspended) return false;
  const p = c.plant || {};
  switch (mode) {
    case 'mc-photo':
    case 'type-photo':
    case 'speak-photo': return c.photoIds?.length && spokenAnswers(c, expect).length;
    case 'mc-latin':
    case 'mc-german': return p.deName && p.latinName;
    case 'mc-family': return c.photoIds?.length && p.family;
    case 'features': return p.features && nameOf(c);
    case 'mc-lifeform': return c.photoIds?.length && p.lifeForm?.length;
  }
  return false;
}

function answerValue(c, mode, expect = 'both') {
  if (mode === 'type-photo' || mode === 'speak-photo') {
    if (expect === 'la') return c.plant.latinName;
    if (expect === 'de') return c.plant.deName || c.title;
  }
  // Bei mehreren Lebensformen wird pro Frage eine davon abgefragt
  if (mode === 'mc-lifeform') return c.plant.lifeForm[Math.floor(Math.random() * c.plant.lifeForm.length)];
  if (mode === 'mc-latin') return c.plant.latinName;
  if (mode === 'mc-family') return c.plant.family;
  return nameOf(c);
}

export default async function quizView(params = {}) {
  const [cards, decks] = await Promise.all([allCards(), listDecks()]);
  const st = { ...prefs };
  if (params.deckId !== undefined) st.deckId = params.deckId;
  const root = h('div', { class: 'page quiz' });
  const save = () => Object.assign(prefs, st);

  const pool = () => cards.filter(c => (!st.deckId || c.deckId === st.deckId) && eligible(c, st.mode, st.expect));

  function renderSetup() {
    const pl = pool();
    const enough = pl.length >= 2;
    const modeList = h('div', { class: 'mode-list', role: 'radiogroup' }, MODES.filter(m => m.id !== 'speak-photo' || speechSupported()).map(m => {
      const n = cards.filter(c => (!st.deckId || c.deckId === st.deckId) && eligible(c, m.id, st.expect)).length;
      return h('button', {
        type: 'button', role: 'radio', 'aria-checked': String(st.mode === m.id),
        class: 'mode' + (st.mode === m.id ? ' on' : ''), onclick: () => { st.mode = m.id; save(); renderSetup(); },
      }, icon(m.icon, 22), h('span', { class: 'mode-main' }, h('span', { class: 'mode-label' }, m.label), h('span', { class: 'mode-desc' }, m.desc)),
      h('span', { class: 'mode-n' + (n < 2 ? ' low' : '') }, n));
    }));
    root.replaceChildren(
      topbar({ title: 'Training', back: params.deckId ? () => app.back() : null }),
      h('div', { class: 'quiz-body' },
        h('p', { class: 'lead' }, 'Gezielt üben, ohne die Lernplanung zu verändern. Die Zahl zeigt, wie viele Karten für die Übung passen.'),
        h('div', { class: 'form' },
          field('Stapel', h('select', { class: 'input', id: 'quiz-deck', value: st.deckId, onchange: e => { st.deckId = e.target.value; save(); renderSetup(); } },
            h('option', { value: '' }, 'Alle Stapel'), decks.map(d => h('option', { value: d.id }, d.name)))),
          h('div', { class: 'field' }, h('span', { class: 'field-label' }, 'Übung'), modeList),
          h('div', { class: 'field' }, h('span', { class: 'field-label' }, 'Fragen'), segmented(
            [{ value: 10, label: '10' }, { value: 20, label: '20' }, { value: 0, label: 'Alle' }], st.count, v => { st.count = v; save(); })),
          ['type-photo', 'speak-photo'].includes(st.mode) ? h('div', { class: 'field' }, h('span', { class: 'field-label' }, 'Antwort'), segmented([
            { value: 'de', label: 'Deutsch' }, { value: 'la', label: 'Botanisch' }, { value: 'both', label: 'Beides' },
          ], st.expect, v => { st.expect = v; save(); renderSetup(); }, { id: 'quiz-expect' })) : null,
          st.mode === 'speak-photo' ? field('Erkennungssprache', h('select', {
            class: 'input', id: 'quiz-speechlang', value: app.settings.speechInLang || 'de-DE',
            onchange: async e => { app.settings = await saveSettings({ speechInLang: e.target.value }); },
          }, SPEECH_LANGS.map(l => h('option', { value: l.value }, l.label))), 'Für botanische Namen klappt Italienisch oft besser, weil die Aussprache dem Latein ähnelt. Ähnlich klingende Erkennungen zählen trotzdem.') : null,
          st.mode.startsWith('mc') || st.mode === 'features'
            ? toggle('Knifflig', st.tricky, v => { st.tricky = v; save(); }, { id: 'quiz-tricky', hint: 'Falsche Antworten bevorzugt aus derselben Familie – wie draußen, wo sich Korbblütler gern ähneln.' })
            : null),
        enough
          ? btn('Training starten', () => start(shuffle(pl)), { kind: 'primary', iconName: 'play', cls: 'study-btn' })
          : emptyState('target', 'Zu wenige passende Karten', 'Für diese Übung brauchst du mindestens zwei Pflanzenkarten mit den nötigen Angaben (z. B. Foto und Name, oder deutscher und botanischer Name).')));
  }

  function options(card, pl, answer) {
    if (st.mode === 'mc-lifeform') {
      // Falsche Antworten: Lebensformen, die diese Pflanze nicht hat
      const own = new Set(card.plant.lifeForm.map(norm));
      const pool = [...new Set([...(app.settings.lifeForms || []), ...pl.flatMap(c => c.plant.lifeForm)])].filter(f => !own.has(norm(f)));
      return shuffle([answer, ...shuffle(pool).slice(0, 3)]);
    }
    if (st.mode === 'mc-family') {
      const fams = [...new Set([...pl.map(c => c.plant.family), ...FAMILIES])]
        .filter(f => norm(familyKey(f)) !== norm(familyKey(answer)));
      return shuffle([answer, ...shuffle(fams).slice(0, 3)]);
    }
    let cands = shuffle(pl.filter(c => c.id !== card.id));
    if (st.tricky) cands.sort((a, b) => (sameFamily(b, card) ? 1 : 0) - (sameFamily(a, card) ? 1 : 0));
    const out = [answer];
    const seen = new Set([norm(answer)]);
    for (const c of cands) {
      const v = answerValue(c, st.mode);
      if (v && !seen.has(norm(v))) { out.push(v); seen.add(norm(v)); }
      if (out.length === 4) break;
    }
    return shuffle(out);
  }

  let run = null;
  function start(list) {
    const pl = pool();
    const qs = (st.count ? list.slice(0, st.count) : list).map(c => {
      const answer = answerValue(c, st.mode, st.expect);
      return { card: c, answer, opts: ['type-photo', 'speak-photo'].includes(st.mode) ? null : options(c, pl, answer) };
    });
    if (!run) app.pushLayer();
    run = { qs, i: 0, right: 0, wrong: [], answered: false };
    document.body.classList.add('fullscreen-view');
    renderQuestion();
  }

  async function renderQuestion() {
    const r = run;
    if (r.i >= r.qs.length) return renderSummary();
    const { card, opts, answer } = r.qs[r.i];
    const p = card.plant;
    let prompt;
    if (['mc-photo', 'type-photo', 'speak-photo', 'mc-family', 'mc-lifeform'].includes(st.mode)) {
      const pid = card.photoIds[Math.floor(Math.random() * card.photoIds.length)];
      const img = h('img', { class: 'quiz-img', alt: 'Welche Pflanze?' });
      photoURL(pid, 'full').then(u => { img.src = u || ''; });
      prompt = h('figure', { class: 'quiz-photo', onclick: () => openZoom(card.photoIds, card.photoIds.indexOf(pid)) }, img);
    } else if (st.mode === 'mc-latin') {
      prompt = h('div', { class: 'quiz-text' }, h('span', { class: 'quiz-q' }, 'Botanischer Name von'), h('strong', { class: 'quiz-big' }, p.deName));
    } else if (st.mode === 'mc-german') {
      prompt = h('div', { class: 'quiz-text' }, h('span', { class: 'quiz-q' }, 'Deutscher Name von'), h('strong', { class: 'quiz-big latin' }, p.latinName,
        h('button', { type: 'button', class: 'icon-btn speak', 'aria-label': 'Vorlesen', onclick: () => speak(p.latinName, app.settings?.speechLang) }, icon('volume', 20))));
    } else {
      prompt = h('div', { class: 'quiz-text' }, h('span', { class: 'quiz-q' }, 'Welche Pflanze ist gemeint?'), h('p', { class: 'quiz-features pre' }, p.features));
    }
    const question = st.mode === 'speak-photo' ? ({ la: 'Sag den botanischen Namen', de: 'Sag den deutschen Namen' }[st.expect] || 'Sag den Namen der Pflanze')
      : st.mode === 'mc-family' ? 'Zu welcher Familie gehört sie?'
      : st.mode === 'mc-lifeform' ? 'Welche Lebensform hat sie?'
        : (st.mode.endsWith('photo') ? 'Welche Pflanze ist das?' : null);
    const feedback = h('div', { class: 'quiz-feedback' });
    const nextBtn = btn(r.i + 1 >= r.qs.length ? 'Auswertung' : 'Weiter', () => { r.i++; r.answered = false; renderQuestion(); }, { kind: 'primary', iconName: 'chevron', attrs: { hidden: true } });

    const finishAnswer = (ok, given, closeNote) => {
      r.answered = true;
      if (ok) r.right++;
      else r.wrong.push({ card, given, answer });
      addLog({ cardId: card.id, deckId: card.deckId, rating: ok ? 3 : 1, mode: 'quiz', quiz: st.mode });
      const extra = [p.deName !== answer ? p.deName : null, p.latinName !== answer ? p.latinName : null, st.mode !== 'mc-family' ? p.family : null].filter(Boolean).join(' · ');
      feedback.replaceChildren(h('div', { class: 'fb ' + (ok ? 'ok' : 'bad') },
        icon(ok ? 'check' : 'close', 20),
        h('span', null, ok ? (closeNote ? `Fast richtig – korrekt: ${answer}` : 'Richtig!') : `Richtig wäre: ${answer}`)),
        extra ? h('div', { class: 'muted small' }, extra) : '');
      nextBtn.hidden = false;
      nextBtn.focus();
    };

    let answerArea;
    if (opts) {
      const latinOpts = st.mode === 'mc-latin' || (getNameMode() === 'la' && ['mc-photo', 'features'].includes(st.mode));
      answerArea = h('div', { class: 'opt-list' + (latinOpts ? ' latin' : '') }, opts.map((o, k) => h('button', {
        type: 'button', class: 'opt', dataset: { k: k + 1 },
        onclick: e => {
          if (r.answered) return;
          const ok = norm(o) === norm(answer);
          answerArea.querySelectorAll('.opt').forEach(b => {
            if (norm(b.textContent) === norm(answer)) b.classList.add('right');
          });
          if (!ok) e.currentTarget.classList.add('wrong');
          finishAnswer(ok, o);
        },
      }, o)));
    } else if (st.mode === 'speak-photo') {
      // Sprechen: großer Mikrofon-Knopf, mehrere Erkennungsvorschläge werden tolerant verglichen
      const heard = h('p', { class: 'heard muted' });
      const mic = micButton({
        big: true, label: 'Antippen und Namen sagen',
        getLang: () => app.settings.speechInLang || 'de-DE',
        onResult: alts => {
          if (r.answered) return;
          const res = checkSpoken(alts, spokenAnswers(card, st.expect));
          heard.textContent = `Gehört: „${res.heard}“`;
          mic.hidden = true;
          skip.hidden = true;
          finishAnswer(res.result !== 'wrong', res.heard, res.result === 'close');
        },
        onError: err => toast(err.message),
      });
      const skip = btn('Weiß ich nicht', () => { if (!r.answered) { mic.hidden = true; skip.hidden = true; finishAnswer(false, '—'); } }, { kind: 'ghost' });
      answerArea = h('div', { class: 'speak-area' }, mic, heard, skip);
    } else {
      const input = h('input', { class: 'input type-input', id: 'quiz-type', placeholder: { la: 'Botanischer Name …', de: 'Deutscher Name …' }[st.expect] || 'Deutscher oder botanischer Name …', autocomplete: 'off', autocapitalize: 'off', spellcheck: 'false', enterkeyhint: 'done' });
      const judge = res => {
        input.disabled = true;
        input.classList.add(res.result === 'wrong' ? 'bad' : 'ok');
        finishAnswer(res.result !== 'wrong', input.value, res.result === 'close');
      };
      const check = () => {
        if (r.answered) { nextBtn.click(); return; }
        judge(checkAnswer(input.value, spokenAnswers(card, st.expect)));
      };
      input.addEventListener('keydown', e => { if (e.key === 'Enter') { e.preventDefault(); check(); } });
      const mic = speechSupported() ? micButton({
        getLang: () => app.settings.speechInLang || 'de-DE',
        onResult: alts => {
          if (r.answered) return;
          const res = checkSpoken(alts, spokenAnswers(card, st.expect));
          input.value = res.heard;
          judge(res);
        },
        onError: err => toast(err.message),
      }) : null;
      answerArea = h('div', { class: 'type-area' }, input, mic, btn('Prüfen', check, { kind: 'secondary' }));
      setTimeout(() => input.focus(), 60);
    }

    root.replaceChildren(
      topbar({ title: `Frage ${r.i + 1} von ${r.qs.length}`, sub: `${r.right} richtig`, back: stop }),
      h('div', { class: 'rv-progress' }, h('span', { style: { width: `${Math.round((r.i / r.qs.length) * 100)}%` } })),
      h('div', { class: 'quiz-stage' },
        prompt,
        question ? h('p', { class: 'prompt' }, question) : null,
        answerArea,
        feedback),
      h('div', { class: 'rv-bottom' }, nextBtn));
  }

  function renderSummary() {
    const r = run;
    const total = r.qs.length;
    const pct = total ? Math.round((r.right / total) * 100) : 0;
    const wrongCards = [...new Map(r.wrong.map(w => [w.card.id, w.card])).values()];
    root.replaceChildren(
      topbar({ title: 'Auswertung', back: stop }),
      h('div', { class: 'quiz-body' },
        h('section', { class: 'score' },
          h('div', { class: 'score-ring', style: { '--p': pct } }, h('span', null, `${pct} %`)),
          h('div', null, h('div', { class: 'score-main' }, `${r.right} von ${total} richtig`),
            h('div', { class: 'muted' }, MODES.find(m => m.id === st.mode)?.label))),
        r.wrong.length ? h('section', null,
          h('h2', { class: 'section-h' }, 'Daneben'),
          h('div', { class: 'wrong-list' }, r.wrong.map(w => h('button', { type: 'button', class: 'wrong-row', onclick: () => app.go('edit', { id: w.card.id }) },
            w.card.photoIds?.length ? lazyPhoto(h('img', { class: 'cl-thumb', alt: '' }), w.card.photoIds[0]) : h('span', { class: 'cl-thumb none' }, icon('leaf', 18)),
            h('span', { class: 'cl-main' }, h('span', { class: 'cl-name' }, ['mc-family', 'mc-lifeform'].includes(st.mode) ? `${displayName(w.card)}: ${w.answer}` : w.answer),
              h('span', { class: 'cl-sub' }, `Deine Antwort: ${w.given || '—'}`)))))) : h('p', { class: 'lead' }, 'Fehlerfrei!'),
        h('div', { class: 'finished-actions' },
          wrongCards.length ? btn('Falsche wiederholen', () => start(shuffle(wrongCards)), { kind: 'primary', iconName: 'undo' }) : null,
          wrongCards.length ? btn('Falsche markieren', async () => {
            wrongCards.forEach(c => { c.marked = true; });
            await saveCards(wrongCards);
            toast(`${wrongCards.length} Karten markiert`);
          }, { iconName: 'star' }) : null,
          btn('Neue Runde', () => start(shuffle(pool())), { iconName: 'shuffle' }),
          btn('Fertig', stop, { kind: 'ghost' }))));
  }

  function stop() {
    app.popLayer();
  }
  function onPop() {
    if (!run) return false;
    run = null;
    document.body.classList.remove('fullscreen-view');
    renderSetup();
    return true;
  }

  const onKey = e => {
    if (!run || document.querySelector('.backdrop, .zoom')) return;
    if (e.target.matches?.('input')) return;
    if (!run.answered && /^[1-4]$/.test(e.key)) root.querySelector(`.opt[data-k="${e.key}"]`)?.click();
    else if (run.answered && (e.key === 'Enter' || e.key === ' ') && !e.target.matches?.('button')) { e.preventDefault(); root.querySelector('.rv-bottom .btn')?.click(); }
  };
  document.addEventListener('keydown', onKey);

  renderSetup();
  return { el: root, tab: 'quiz', onPop, destroy: () => { document.removeEventListener('keydown', onKey); document.body.classList.remove('fullscreen-view'); } };
}

export { displayName };
