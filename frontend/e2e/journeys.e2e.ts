import { expect, test, type Page, type Response } from '@playwright/test';

/**
 * #242 — journeys through the real stack: a browser, the production build, the Spring backend, and PostgreSQL.
 *
 * The framing tests (framing.e2e.ts) run the build in prototype mode and prove the screen is laid out right. These
 * prove the screen is *connected* right: that a player who walks somewhere is shown the scene the server decided for
 * where they now stand, and that a reload does not lose them — the same Chronicle, the same ground, the same scene,
 * and the story so far still on the page.
 *
 * Run only where a backend is up (the `journeys` CI job sets DRAUGR_E2E_API). Anywhere else they skip, so the
 * prototype-mode framing run never waits on a server that is not there.
 */

const api = process.env.DRAUGR_E2E_API;
test.skip(!api, 'needs a running backend: set DRAUGR_E2E_API');

type Backdrop = { key: string; candidates: string[]; fingerprint: string | null };

/** In through the front door: the shore, the crossing, then the world. */
async function enterTheWorld(page: Page) {
  await page.goto('./');
  await page.locator('.menu-button.primary').click();
  await page.getByRole('button', { name: 'OK' }).click();
  await expect(page.locator('#action')).toBeVisible({ timeout: 60_000 });
}

const isBackdrop = (response: Response) => response.url().includes('/api/visual-context/v1/backdrop') && response.request().method() === 'GET';
const isAction = (response: Response) => response.url().endsWith('/api/actions') && response.request().method() === 'POST';

test.describe.configure({ mode: 'serial' });

test('walking somewhere shows the scene the server chose, and a reload keeps everything', async ({ page }) => {
  test.setTimeout(240_000);

  // Awakening: the screen asks the server what this place is, and shows it.
  const first = page.waitForResponse(isBackdrop, { timeout: 120_000 });
  await enterTheWorld(page);
  const arrived = await (await first).json() as Backdrop;
  expect(arrived.candidates.length, 'the server always offers a chain').toBeGreaterThan(0);
  expect(arrived.candidates[arrived.candidates.length - 1]).toBe(arrived.key);

  // Walk until a step succeeds: whichever way the ground allows. Each try is typed where a player types it.
  let walked: { intent: string; outcome: string; perception: string } | null = null;
  let afterWalk: Backdrop | null = null;
  for (const way of ['walk east', 'walk south', 'walk west', 'walk north']) {
    const action = page.waitForResponse(isAction, { timeout: 60_000 });
    const scene = page.waitForResponse(isBackdrop, { timeout: 60_000 });
    await page.locator('#action').fill(way);
    await page.locator('#action').press('Enter');
    const result = await (await action).json();
    const refreshed = await (await scene).json() as Backdrop;
    if (result.intent === 'MOVE' && result.outcome === 'SUCCEEDED') { walked = result; afterWalk = refreshed; break; }
  }
  expect(walked, 'some neighbouring ground can be walked onto').not.toBeNull();

  // What the screen asked for after the walk is exactly what the server says of where the Chronicle now stands.
  const served = await (await page.request.get(`${api}/api/visual-context/v1/backdrop`)).json() as Backdrop;
  expect(afterWalk!.key).toBe(served.key);
  expect(afterWalk!.fingerprint).toBe(served.fingerprint);

  // The walk is in the story.
  const told = walked!.perception.split(/[.!?]/)[0].trim();
  await expect(page.getByText(told, { exact: false }).first()).toBeVisible();

  // Reload: the same Chronicle is waiting, on the same ground, and the story so far is still there.
  const back = page.waitForResponse(isBackdrop, { timeout: 120_000 });
  await page.reload();
  await page.locator('.menu-button.primary').click();
  await page.getByRole('button', { name: 'OK' }).click();
  await expect(page.locator('#action')).toBeVisible({ timeout: 60_000 });
  const resumed = await (await back).json() as Backdrop;
  expect(resumed.key, 'the same place after a reload').toBe(served.key);
  expect(resumed.fingerprint, 'unchanged world, unchanged fingerprint').toBe(served.fingerprint);
  await expect(page.getByText(told, { exact: false }).first()).toBeVisible();
});
