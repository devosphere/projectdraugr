import { expect, test, type Page } from '@playwright/test';

/**
 * #239 — the responsive framing matrix, in a real browser.
 *
 * The manual check at 375 / 768 / 1280 / 2560 recorded on the ticket was a person looking, once. These are the same
 * widths plus the one a person looking never tried: 700, between the phone breakpoint and the tablet, where the
 * narration column stops shrinking and the Body panel does not yet fold away.
 *
 * What is asserted is what a screenshot shows only if someone thinks to look for it: the page never scrolls sideways,
 * the place a player types is on screen and usable, the header's controls are reachable, and the Body panel never
 * lies across the narration or the composer. None of it depends on a backdrop image being present, because in CI the
 * images are not in the repository; a scene with no picture must still frame correctly.
 */

type Box = { left: number; top: number; right: number; bottom: number };

const VIEWPORTS = [
  { name: 'phone', width: 375, height: 812 },
  { name: 'between phone and tablet', width: 700, height: 900 },
  { name: 'tablet', width: 768, height: 1024 },
  { name: 'landscape tablet', width: 1024, height: 768 },
  { name: 'laptop', width: 1280, height: 800 },
  { name: 'wide', width: 2560, height: 1440 },
];

/** Walk in through the front door, as a player does: the shore, the crossing, then the world. */
async function enterTheWorld(page: Page) {
  await page.goto('./');
  await page.locator('.menu-button.primary').click();
  await page.getByRole('button', { name: 'OK' }).click();
  await expect(page.locator('#action')).toBeVisible();
}

async function boxOf(page: Page, selector: string): Promise<Box | null> {
  return page.evaluate(sel => {
    const el = document.querySelector(sel);
    if (!el || getComputedStyle(el).display === 'none') return null;
    const r = el.getBoundingClientRect();
    return { left: r.left, top: r.top, right: r.right, bottom: r.bottom };
  }, selector);
}

const overlaps = (a: Box, b: Box) => a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom;

for (const viewport of VIEWPORTS) {
  test.describe(`${viewport.name} (${viewport.width}×${viewport.height})`, () => {
    test.use({ viewport: { width: viewport.width, height: viewport.height } });

    test('frames the playthrough without hiding or crowding anything a player needs', async ({ page }) => {
      await enterTheWorld(page);

      const sideways = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
      expect(sideways, 'the page must never scroll sideways').toBeLessThanOrEqual(0);

      const action = await boxOf(page, '#action');
      expect(action, 'the action field must be rendered').not.toBeNull();
      expect(action!.left, 'the action field starts on screen').toBeGreaterThanOrEqual(0);
      expect(action!.right, 'the action field ends on screen').toBeLessThanOrEqual(viewport.width);
      expect(action!.bottom, 'the action field is above the bottom edge').toBeLessThanOrEqual(viewport.height);
      expect(action!.right - action!.left, 'the action field is wide enough to type a sentence into').toBeGreaterThan(200);

      await page.locator('#action').fill('look around');
      await expect(page.locator('#action')).toHaveValue('look around');

      const header = await boxOf(page, '.header-controls');
      if (header) expect(header.right, 'the header controls are reachable').toBeLessThanOrEqual(viewport.width);

      // The Body panel stands to the left on wide screens and folds behind a button on a phone. Wherever it is
      // shown, it must not lie across what the player is reading or typing.
      const body = await boxOf(page, '.body-hud');
      if (body) {
        for (const selector of ['.playthrough-bottom .perception', '.playthrough-bottom .action-composer']) {
          const other = await boxOf(page, selector);
          if (other) expect(overlaps(body, other), `the Body panel must not cover ${selector}: body ${JSON.stringify(body)}, ${selector} ${JSON.stringify(other)}`).toBe(false);
        }
      }
    });
  });
}

test.describe('reduced motion', () => {
  test.use({ viewport: { width: 1280, height: 800 } });

  test('a scene change is not animated for someone who asked for less motion', async ({ page }) => {
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await enterTheWorld(page);
    // Proves the emulation took, so a failure below is about the stylesheet and never about the harness.
    expect(await page.evaluate(() => matchMedia('(prefers-reduced-motion: reduce)').matches)).toBe(true);
    // The fade is a class the screen puts on the incoming scene. Asked of the stylesheet directly, so it holds
    // whether or not this build has an image to fade to.
    const animation = await page.evaluate(() => {
      const probe = document.createElement('div');
      probe.className = 'scene-fade';
      document.body.appendChild(probe);
      const name = getComputedStyle(probe).animationName;
      probe.remove();
      return name;
    });
    expect(animation).toBe('none');
  });
});
