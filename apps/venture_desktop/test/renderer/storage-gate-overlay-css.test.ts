/**
 * @vitest-environment jsdom
 *
 * Regression coverage for the Gate 1 stuck-overlay defect: storage-gate.js's hide()/show()
 * (classList.add/remove('hidden')) is pure DOM plumbing that no JS-only fixture test can catch a
 * CSS cascade bug in — the real defect was `.storage-gate-overlay { display: flex; }` being
 * declared AFTER `.hidden { display: none; }` in main.css, so at equal (single-class) specificity
 * the later rule won regardless of what classList said. These tests load the ACTUAL shipped
 * index.html + main.css from src/renderer (not a hand-written markup fixture) and assert on
 * jsdom's real computed style, so a future reordering/renaming that reintroduces the conflict
 * fails here even if every classList-based assertion elsewhere still passes.
 */
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const rendererRoot = path.resolve(__dirname, '../../src/renderer');
const indexHtml = readFileSync(path.join(rendererRoot, 'index.html'), 'utf8');
const mainCss = readFileSync(path.join(rendererRoot, 'styles', 'main.css'), 'utf8');

/** Loads the real index.html body into the test document and inlines the real main.css so
 * jsdom's CSS engine computes the actual cascade — a <link> would need network/file resolution
 * jsdom doesn't perform here, so the stylesheet content is injected directly instead. */
function loadRealStorageGateMarkup() {
  const bodyMatch = indexHtml.match(/<body>([\s\S]*)<\/body>/);
  if (!bodyMatch) {
    throw new Error('index.html: could not locate <body> for test setup');
  }
  document.head.innerHTML = `<style>${mainCss}</style>`;
  document.body.innerHTML = bodyMatch[1];
}

describe('storage-gate overlay — CSS cascade (Gate 1 regression)', () => {
  it('overlay with class "storage-gate-overlay hidden" computes display:none', () => {
    loadRealStorageGateMarkup();
    const overlay = document.getElementById('storage-gate-overlay')!;
    expect(overlay.className).toBe('storage-gate-overlay hidden');
    expect(getComputedStyle(overlay).display).toBe('none');
  });

  it('overlay without the hidden class computes display:flex', () => {
    loadRealStorageGateMarkup();
    const overlay = document.getElementById('storage-gate-overlay')!;
    overlay.classList.remove('hidden');
    expect(getComputedStyle(overlay).display).toBe('flex');
  });

  it('setup/unavailable/timeout child sections still toggle normally under the real stylesheet', () => {
    loadRealStorageGateMarkup();
    const setup = document.getElementById('storage-gate-setup')!;
    const unavailable = document.getElementById('storage-gate-unavailable')!;
    const timeout = document.getElementById('storage-gate-timeout')!;

    // All three start hidden in the shipped markup.
    for (const section of [setup, unavailable, timeout]) {
      expect(getComputedStyle(section).display).toBe('none');
    }

    setup.classList.remove('hidden');
    expect(getComputedStyle(setup).display).not.toBe('none');
    expect(getComputedStyle(unavailable).display).toBe('none');
    expect(getComputedStyle(timeout).display).toBe('none');

    setup.classList.add('hidden');
    unavailable.classList.remove('hidden');
    expect(getComputedStyle(setup).display).toBe('none');
    expect(getComputedStyle(unavailable).display).not.toBe('none');
    expect(getComputedStyle(timeout).display).toBe('none');
  });

  it('re-adding hidden after removal returns the overlay to display:none (hide() after show())', () => {
    loadRealStorageGateMarkup();
    const overlay = document.getElementById('storage-gate-overlay')!;
    overlay.classList.remove('hidden');
    expect(getComputedStyle(overlay).display).toBe('flex');
    overlay.classList.add('hidden');
    expect(getComputedStyle(overlay).display).toBe('none');
  });
});
