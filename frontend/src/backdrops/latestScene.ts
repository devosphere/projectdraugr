/**
 * #237 — the newest scene wins.
 *
 * After every action the screen asks for the location and the environment again, fire-and-forget. Two actions in
 * quick succession send two requests each, and nothing says the answers come back in order: a slow reply to the
 * first action could land after the reply to the second and put the Chronicle back where they were a moment ago.
 *
 * {@link latestOnly} hands out a ticket per request and answers "is this still the newest?" when its reply lands,
 * so an older answer can never overwrite a newer one. Unmounting retires every ticket.
 */
export function latestOnly() {
  let newest = 0;
  let retired = false;
  return {
    /** Take a ticket for a request about to be sent. */
    next(): number { newest += 1; return newest; },
    /** Whether the reply holding this ticket is still the one to apply. */
    current(ticket: number): boolean { return !retired && ticket === newest; },
    /** The screen is gone: no reply may apply any more. */
    retire(): void { retired = true; },
  };
}

/**
 * Which scene a new one fades in over, or null when the new scene must simply appear (#239).
 *
 * The rule lived inside the screen's effect, where it could only be checked by opening a browser and watching —
 * which is how a reduced-motion promise quietly stops being kept. It is three decisions and no state, so it is a
 * function: nothing changed, no fade; someone who has asked for reduced motion gets the change at once; and there
 * is nothing to fade FROM before the first scene, so that one appears too.
 */
export function sceneToFadeFrom(shown: string | null, next: string | null, reducedMotion: boolean): string | null {
  if (shown === next) return null;
  if (reducedMotion) return null;
  return shown;
}

/**
 * Resolve once the image at {@code src} is decoded and ready to paint, so the old scene stays up until the new one
 * can replace it without a blank frame. Rejects if it cannot be decoded; the caller keeps the last valid scene.
 */
export function decodeArt(src: string): Promise<void> {
  if (typeof Image === 'undefined') return Promise.resolve();
  const image = new Image();
  image.src = src;
  if (typeof image.decode === 'function') return image.decode();
  return new Promise((resolve, reject) => {
    image.onload = () => resolve();
    image.onerror = () => reject(new Error(`backdrop could not be decoded: ${src}`));
  });
}
