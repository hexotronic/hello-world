// DOM-Helfer, Dialoge, Hinweise.
import { icon } from './icons.js';

/** Element bauen: h('div', {class: 'x', onclick: fn}, 'Text', child, [mehr]) */
export function h(tag, attrs, ...children) {
  const el = document.createElement(tag);
  let value;
  if (attrs) {
    for (const [k, v] of Object.entries(attrs)) {
      if (v == null || v === false) continue;
      if (k === 'value') value = v;
      else if (k === 'class') el.className = v;
      else if (k === 'style' && typeof v === 'object') Object.assign(el.style, v);
      else if (k === 'dataset') Object.assign(el.dataset, v);
      else if (k.startsWith('on') && typeof v === 'function') el.addEventListener(k.slice(2), v);
      else if (k === 'html') el.innerHTML = v;
      else if (k in el && typeof v !== 'string') el[k] = v;
      else if (v === true) el.setAttribute(k, '');
      else el.setAttribute(k, v);
    }
  }
  append(el, children);
  if (value !== undefined) el.value = value;
  return el;
}

function append(el, children) {
  for (const c of children) {
    if (c == null || c === false || c === true) continue;
    if (Array.isArray(c)) append(el, c);
    else if (c instanceof Node) el.appendChild(c);
    else el.appendChild(document.createTextNode(String(c)));
  }
}

export function iconBtn(name, label, onclick, extra = {}) {
  return h('button', { class: 'icon-btn ' + (extra.class || ''), type: 'button', 'aria-label': label, title: label, onclick, ...extra.attrs }, icon(name));
}

export function btn(label, onclick, { kind = 'secondary', iconName, attrs = {}, cls = '' } = {}) {
  return h('button', { class: `btn btn-${kind} ${cls}`, type: 'button', onclick, ...attrs }, iconName ? icon(iconName, 20) : null, h('span', null, label));
}

/** Kopfleiste einer Ansicht. */
export function topbar({ title, sub, back, actions = [], cls = '' }) {
  return h('header', { class: 'topbar ' + cls },
    back ? iconBtn('back', 'Zurück', back, { class: 'tb-back' }) : null,
    h('div', { class: 'tb-title' },
      h('h1', null, title),
      sub ? h('div', { class: 'tb-sub' }, sub) : null),
    h('div', { class: 'tb-actions' }, actions));
}

// ---------- Hinweise (Toasts) ----------
let toastWrap;
export function toast(message, { action, onAction, duration = 3200 } = {}) {
  if (!toastWrap) {
    toastWrap = h('div', { class: 'toast-wrap', role: 'status', 'aria-live': 'polite' });
    document.body.appendChild(toastWrap);
  }
  const t = h('div', { class: 'toast' }, h('span', null, message),
    action ? h('button', { class: 'toast-action', type: 'button', onclick: () => { onAction?.(); remove(); } }, action) : null);
  toastWrap.appendChild(t);
  const remove = () => { t.classList.add('out'); setTimeout(() => t.remove(), 250); };
  setTimeout(remove, duration);
  return remove;
}

// ---------- Dialoge ----------
const openDialogs = [];

export function closeTopDialog() {
  const d = openDialogs.at(-1);
  if (d) { d.close(undefined); return true; }
  return false;
}

/**
 * Dialog/Bottom-Sheet. actions: [{label, value, kind}] → Promise mit value des gedrückten Knopfs.
 * body darf eine Funktion (close) => Node sein.
 */
export function dialog({ title, body, actions = [], sheet = false, cls = '', dismissible = true }) {
  return new Promise(resolve => {
    const prevFocus = document.activeElement;
    let closed = false;
    const close = value => {
      if (closed) return;
      closed = true;
      openDialogs.splice(openDialogs.indexOf(ctrl), 1);
      backdrop.classList.add('out');
      setTimeout(() => backdrop.remove(), 200);
      document.removeEventListener('keydown', onKey, true);
      try { prevFocus?.focus?.(); } catch { /* egal */ }
      resolve(value);
    };
    const ctrl = { close };
    const onKey = e => {
      if (e.key === 'Escape' && dismissible) { e.stopPropagation(); close(undefined); }
    };
    const content = typeof body === 'function' ? body(close) : body;
    const box = h('div', { class: `dialog ${sheet ? 'sheet' : ''} ${cls}`, role: 'dialog', 'aria-modal': 'true', 'aria-label': title || 'Dialog' },
      title ? h('h2', { class: 'dialog-title' }, title) : null,
      content ? h('div', { class: 'dialog-body' }, content) : null,
      actions.length ? h('div', { class: 'dialog-actions' }, actions.map(a =>
        h('button', {
          class: `btn btn-${a.kind || 'ghost'}`, type: 'button',
          onclick: async () => {
            if (a.validate && !(await a.validate())) return;
            close(typeof a.value === 'function' ? a.value() : a.value);
          },
        }, a.label))) : null);
    const backdrop = h('div', { class: 'backdrop' + (sheet ? ' sheet-backdrop' : ''), onclick: e => { if (e.target === backdrop && dismissible) close(undefined); } }, box);
    document.body.appendChild(backdrop);
    openDialogs.push(ctrl);
    document.addEventListener('keydown', onKey, true);
    requestAnimationFrame(() => {
      const f = box.querySelector('[autofocus], input, select, textarea') || box.querySelector('button');
      f?.focus?.();
    });
  });
}

export function confirmDialog(title, text, { ok = 'Löschen', danger = true } = {}) {
  return dialog({
    title,
    body: text ? h('p', { class: 'muted' }, text) : null,
    actions: [
      { label: 'Abbrechen', value: false },
      { label: ok, value: true, kind: danger ? 'danger' : 'primary' },
    ],
  }).then(v => v === true);
}

export function promptDialog(title, { label = '', value = '', placeholder = '', ok = 'Speichern', type = 'text' } = {}) {
  const input = h('input', { class: 'input', type, value, placeholder, id: 'prompt-input', autocomplete: 'off' });
  input.addEventListener('keydown', e => {
    if (e.key === 'Enter') { e.preventDefault(); input.closest('.dialog').querySelector('.btn-primary')?.click(); }
  });
  return dialog({
    title,
    body: h('label', { class: 'field' }, label ? h('span', { class: 'field-label' }, label) : null, input),
    actions: [
      { label: 'Abbrechen', value: null },
      { label: ok, kind: 'primary', value: () => input.value },
    ],
  }).then(v => (v == null ? null : String(v)));
}

/** Auswahlmenü als Bottom-Sheet: items [{label, icon, value, danger}] */
export function menu(title, items) {
  return dialog({
    title,
    sheet: true,
    body: close => h('div', { class: 'menu-list' }, items.filter(Boolean).map(it =>
      h('button', { class: 'menu-item' + (it.danger ? ' danger' : ''), type: 'button', onclick: () => close(it.value) },
        it.icon ? icon(it.icon) : null, h('span', null, it.label), it.hint ? h('span', { class: 'menu-hint' }, it.hint) : null))),
  });
}

// ---------- Formular-Bausteine ----------
export function field(label, control, hint) {
  return h('label', { class: 'field' },
    h('span', { class: 'field-label' }, label),
    control,
    hint ? h('span', { class: 'field-hint' }, hint) : null);
}

export function segmented(options, value, onChange, { id } = {}) {
  const wrap = h('div', { class: 'segmented', role: 'radiogroup', id });
  const render = v => {
    wrap.replaceChildren(...options.map(o => h('button', {
      type: 'button', role: 'radio', 'aria-checked': String(o.value === v),
      class: o.value === v ? 'on' : '',
      onclick: () => { render(o.value); onChange(o.value); },
    }, o.icon ? icon(o.icon, 18) : null, o.label)));
  };
  render(value);
  return wrap;
}

export function toggle(label, checked, onChange, { id, hint } = {}) {
  const input = h('input', { type: 'checkbox', id, checked: !!checked, onchange: () => onChange(input.checked) });
  return h('label', { class: 'toggle-row' },
    h('span', { class: 'toggle-text' }, h('span', null, label), hint ? h('span', { class: 'field-hint' }, hint) : null),
    h('span', { class: 'switch' }, input, h('span', { class: 'switch-track' })));
}

/**
 * Mehrfachauswahl als antippbare Chips. selected (Array) wird direkt verändert.
 * Werte der Karte, die nicht (mehr) in der Liste stehen, werden trotzdem angezeigt.
 * onAdd: async () => neuer Wert (für „+ Eigene“).
 */
export function chipPicker(options, selected, { id, label, onAdd, addLabel = '+ Eigene', onChange } = {}) {
  const wrap = h('div', { class: 'chip-row picker', id, role: 'group', 'aria-label': label });
  const render = () => {
    const all = [...options, ...selected.filter(s => !options.includes(s))];
    const chips = all.map(o => {
      const on = selected.includes(o);
      return h('button', {
        type: 'button', class: 'chip' + (on ? ' on' : ''), 'aria-pressed': String(on),
        onclick: () => {
          const i = selected.indexOf(o);
          if (i >= 0) selected.splice(i, 1); else selected.push(o);
          render();
          onChange?.(selected);
        },
      }, on ? icon('check', 14) : null, o);
    });
    if (onAdd) chips.push(h('button', {
      type: 'button', class: 'chip add', onclick: async () => {
        const v = (await onAdd())?.trim();
        if (v && !selected.includes(v)) { selected.push(v); render(); onChange?.(selected); }
      },
    }, addLabel));
    wrap.replaceChildren(...chips);
  };
  render();
  return wrap;
}

export function emptyState(iconName, title, text, ...actions) {
  return h('div', { class: 'empty' },
    h('div', { class: 'empty-icon' }, icon(iconName, 34)),
    h('h3', null, title),
    text ? h('p', { class: 'muted' }, text) : null,
    actions.length ? h('div', { class: 'empty-actions' }, actions) : null);
}

/** Unsichtbares Datei-Feld öffnen und ausgewählte Dateien liefern. */
export function pickFiles({ accept = 'image/*', multiple = true, capture } = {}) {
  return new Promise(resolve => {
    const input = h('input', { type: 'file', accept, multiple, style: { position: 'fixed', left: '-9999px' } });
    if (capture) input.setAttribute('capture', capture);
    let done = false;
    input.addEventListener('change', () => { done = true; resolve([...(input.files || [])]); input.remove(); });
    // Falls der Dialog ohne Auswahl geschlossen wird
    input.addEventListener('cancel', () => { if (!done) resolve([]); input.remove(); });
    document.body.appendChild(input);
    input.click();
  });
}
