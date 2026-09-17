import { describe, expect, it, vi, afterEach } from 'vitest';
import { decodeArt, latestOnly, sceneToFadeFrom } from './latestScene';

/**
 * #239 — the crossfade, and the promise not to move for someone who asked for stillness.
 *
 * This rule lived inside the screen's effect, where it could only be checked by opening a browser and watching.
 * That is how a reduced-motion promise quietly stops being kept: nothing fails, the animation simply comes back.
 */
describe('sceneToFadeFrom', () => {
  it('fades the old scene out under the new one', () => {
    expect(sceneToFadeFrom('marsh.png', 'crag.png', false)).toBe('marsh.png');
  });

  it('changes at once for someone who asked for reduced motion', () => {
    expect(sceneToFadeFrom('marsh.png', 'crag.png', true)).toBeNull();
  });

  it('does not fade the first scene in from nothing', () => {
    expect(sceneToFadeFrom(null, 'first.png', false)).toBeNull();
  });

  it('does nothing at all when the scene has not changed', () => {
    expect(sceneToFadeFrom('crag.png', 'crag.png', false)).toBeNull();
  });
});

/**
 * #237/#242 — the newest scene wins, and nothing else does.
 *
 * After every action the screen asks for the location and the environment again, fire-and-forget. Two actions in
 * quick succession send two requests each and nothing says the answers come back in order, so a slow reply to the
 * first can land after the reply to the second and put the Chronicle back where they were a moment ago. That is a
 * race: it passes by luck in a browser and is invisible in a screenshot, which is exactly the kind of thing a unit
 * test is for and a journey test is not.
 */
describe('latestOnly', () => {
  it('lets the newest reply through', () => {
    const tickets = latestOnly();
    const only = tickets.next();
    expect(tickets.current(only)).toBe(true);
  });

  it('refuses a reply that was overtaken while it was in flight', () => {
    const tickets = latestOnly();
    const first = tickets.next();
    const second = tickets.next();

    // The slow first reply lands after the second was already asked for.
    expect(tickets.current(first)).toBe(false);
    expect(tickets.current(second)).toBe(true);
  });

  it('refuses every reply once the screen is gone', () => {
    const tickets = latestOnly();
    const inFlight = tickets.next();
    tickets.retire();
    expect(tickets.current(inFlight)).toBe(false);
    expect(tickets.current(tickets.next())).toBe(false);
  });
});

/**
 * The scene must be DECODED before it is shown, or the crossfade starts on a blank frame — and a backdrop that
 * cannot be decoded must reject, so the caller keeps the last valid scene rather than painting nothing.
 */
describe('decodeArt', () => {
  const realImage = globalThis.Image;
  afterEach(() => { globalThis.Image = realImage; });

  it('resolves when the image decodes', async () => {
    const decode = vi.fn().mockResolvedValue(undefined);
    globalThis.Image = class { src = ''; decode = decode; } as unknown as typeof Image;

    await expect(decodeArt('bark.png')).resolves.toBeUndefined();
    expect(decode).toHaveBeenCalledOnce();
  });

  it('rejects when the image cannot be decoded, so a broken file is never painted', async () => {
    globalThis.Image = class {
      src = '';
      decode = () => Promise.reject(new Error('corrupt'));
    } as unknown as typeof Image;

    await expect(decodeArt('torn.png')).rejects.toThrow('corrupt');
  });

  it('falls back to load/error where decode() is not available', async () => {
    let fail: (() => void) | null = null;
    globalThis.Image = class {
      src = '';
      onload: (() => void) | null = null;
      onerror: (() => void) | null = null;
      constructor() { setTimeout(() => { fail = this.onerror; fail?.(); }, 0); }
    } as unknown as typeof Image;

    await expect(decodeArt('old-browser.png')).rejects.toThrow('backdrop could not be decoded');
  });
});
