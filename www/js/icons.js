// Schlichte Strich-Icons (24×24), alle selbst gezeichnet.
const P = {
  plus: '<path d="M12 5v14M5 12h14"/>',
  back: '<path d="M19 12H5M11 18l-6-6 6-6"/>',
  close: '<path d="M6 6l12 12M18 6L6 18"/>',
  search: '<circle cx="11" cy="11" r="6.5"/><path d="M20 20l-4.2-4.2"/>',
  shuffle: '<path d="M16 4h4v4M4 19L20 4M20 16v4h-4M14.5 14.5L20 20M4 5l5 5"/>',
  target: '<circle cx="12" cy="12" r="8.5"/><circle cx="12" cy="12" r="4.5"/><circle cx="12" cy="12" r="0.8" fill="currentColor"/>',
  chart: '<path d="M3 20h18M6.5 16.5v-5M11.5 16.5v-10M16.5 16.5v-7"/>',
  sliders: '<path d="M4 6h9M17 6h3M4 12h3M11 12h9M4 18h11M19 18h1"/><circle cx="15" cy="6" r="2"/><circle cx="9" cy="12" r="2"/><circle cx="17" cy="18" r="2"/>',
  layers: '<path d="M12 3l9 4.5-9 4.5-9-4.5z"/><path d="M3 12l9 4.5 9-4.5M3 16.5L12 21l9-4.5"/>',
  edit: '<path d="M4 20h4L19 9l-4-4L4 16z"/><path d="M13.5 6.5l4 4"/>',
  trash: '<path d="M4 7h16M9.5 7V4.5h5V7M6.5 7l1 13h9l1-13M10 11v5.5M14 11v5.5"/>',
  camera: '<path d="M4 8h3l1.8-2.8h6.4L17 8h3v11H4z"/><circle cx="12" cy="13.2" r="3.4"/>',
  image: '<rect x="3.5" y="4.5" width="17" height="15" rx="1.5"/><path d="M3.5 16l4.5-4.5 4.5 4.5 3-3 5 5"/><circle cx="15.5" cy="9" r="1.5"/>',
  flag: '<path d="M5.5 21V4M5.5 4.5h11l-2.2 4 2.2 4h-11"/>',
  star: '<path d="M12 3.5l2.6 5.4 5.9.8-4.3 4.1 1 5.8L12 16.9l-5.2 2.7 1-5.8-4.3-4.1 5.9-.8z"/>',
  undo: '<path d="M9 14L4 9l5-5"/><path d="M4 9h10.5a5.5 5.5 0 010 11H11"/>',
  eye: '<path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z"/><circle cx="12" cy="12" r="3"/>',
  volume: '<path d="M4 9.5h3.5L12 6v12l-4.5-3.5H4z"/><path d="M15.5 9.5a3.5 3.5 0 010 5M18 7a7 7 0 010 10"/>',
  check: '<path d="M5 12.5l4.5 4.5L19 7.5"/>',
  more: '<circle cx="12" cy="5.5" r="1.4" fill="currentColor" stroke="none"/><circle cx="12" cy="12" r="1.4" fill="currentColor" stroke="none"/><circle cx="12" cy="18.5" r="1.4" fill="currentColor" stroke="none"/>',
  leaf: '<path d="M5 19.5C5 10.5 10.5 4.5 20 4.5c0 9.5-6 15-15 15z"/><path d="M5 19.5L14 10.5"/>',
  bulb: '<path d="M9.5 18h5M10.5 21h3"/><path d="M12 3a6 6 0 00-3.8 10.6c.8.7 1.3 1.6 1.3 2.6v.3h5v-.3c0-1 .5-1.9 1.3-2.6A6 6 0 0012 3z"/>',
  calendar: '<rect x="3.5" y="5" width="17" height="15.5" rx="1.5"/><path d="M3.5 10h17M8 3v4M16 3v4"/>',
  download: '<path d="M12 4v11M7.5 10.5L12 15l4.5-4.5M4.5 19.5h15"/>',
  upload: '<path d="M12 19V8M7.5 12.5L12 8l4.5 4.5M4.5 4.5h15"/>',
  play: '<path d="M7.5 4.5l12 7.5-12 7.5z"/>',
  pause: '<path d="M8 5v14M16 5v14"/>',
  grid: '<rect x="4" y="4" width="6.5" height="6.5" rx="1"/><rect x="13.5" y="4" width="6.5" height="6.5" rx="1"/><rect x="4" y="13.5" width="6.5" height="6.5" rx="1"/><rect x="13.5" y="13.5" width="6.5" height="6.5" rx="1"/>',
  tag: '<path d="M3.5 12.2V3.5h8.7l8.3 8.3-8.7 8.7z"/><circle cx="8" cy="8" r="1.5"/>',
  bolt: '<path d="M13 2.5L4.5 14h7l-1 7.5L19 10h-7z"/>',
  chevron: '<path d="M9 5.5l6.5 6.5L9 18.5"/>',
  clock: '<circle cx="12" cy="12" r="8.5"/><path d="M12 7.5V12l3 2"/>',
  ban: '<circle cx="12" cy="12" r="8.5"/><path d="M6 6l12 12"/>',
  stack: '<rect x="4" y="7" width="16" height="13" rx="1.5"/><path d="M7 4h10"/>',
  zoom: '<circle cx="11" cy="11" r="6.5"/><path d="M20 20l-4.2-4.2M11 8.5v5M8.5 11h5"/>',
  info: '<circle cx="12" cy="12" r="8.5"/><path d="M12 11v5.5M12 7.6v.2"/>',
  checkbox: '<rect x="4" y="4" width="16" height="16" rx="2"/><path d="M8 12l3 3 5-6"/>',
  square: '<rect x="4" y="4" width="16" height="16" rx="2"/>',
  move: '<path d="M4 12h13M13 7l5 5-5 5"/><path d="M4 5v14"/>',
  cloud: '<path d="M7 18.5h10.5a4 4 0 00.7-7.94A5.6 5.6 0 007.3 9.2 4.7 4.7 0 007 18.5z"/>',
  cloudUp: '<path d="M7 18.5h10.5a4 4 0 00.7-7.94A5.6 5.6 0 007.3 9.2 4.7 4.7 0 007 18.5z"/><path d="M12 16v-5M9.8 13l2.2-2.2 2.2 2.2"/>',
  cloudDown: '<path d="M7 18.5h10.5a4 4 0 00.7-7.94A5.6 5.6 0 007.3 9.2 4.7 4.7 0 007 18.5z"/><path d="M12 10.5v5M9.8 13.5l2.2 2.2 2.2-2.2"/>',
  flower: '<circle cx="12" cy="9" r="2.2"/><path d="M12 6.8c-1.6-3.3 1.6-3.3 0 0M14.2 9c3.3-1.6 3.3 1.6 0 0M12 11.2c1.6 3.3-1.6 3.3 0 0M9.8 9c-3.3 1.6-3.3-1.6 0 0"/><path d="M12 11.5V21M12 17c-2.5-.2-4-1.6-4.5-3.5 2.4 0 4 1.3 4.5 3.5z"/>',
};

export function iconSvg(name, size = 22) {
  return `<svg class="icon" width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${P[name] || ''}</svg>`;
}

export function icon(name, size = 22) {
  const t = document.createElement('template');
  t.innerHTML = iconSvg(name, size);
  return t.content.firstElementChild;
}
