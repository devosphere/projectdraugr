import { describe, expect, it } from 'vitest';
import { CARRIES_ON, playerWords, setAsideLine, setAsideSteps } from './playerWords';

/**
 * The live timeline, the Chronicle Archive and the PDF export are three readers of one ledger, and the ledger
 * keeps a row per resolved STEP while the player wrote one SENTENCE (#888, #892). These are the only four
 * cases there are, and getting the first one wrong is what made a relaunched Chronicle disagree with itself
 * about the player's own words.
 */
describe('the player\'s own line for a ledger row', () => {
  const whole = 'gather plant fiber, then prepare a tinder nest';

  it('shows the sentence at the head of its group, not merely the first step', () => {
    expect(playerWords({ action: 'gather plant fiber', submission: whole }, false)).toBe(whole);
  });

  it('marks a step beneath it as carrying on, in a word rather than an ellipsis', () => {
    // An ellipsis in front of a sentence reads as TRUNCATION, which is the one thing an archive of
    // someone's own life must never appear to do to it.
    expect(playerWords({ action: 'prepare a tinder nest', submission: whole }, true))
      .toBe(CARRIES_ON + 'prepare a tinder nest');
    expect(CARRIES_ON).not.toContain('…');
  });

  it('falls back to the step text for a row written before the submission was recorded', () => {
    expect(playerWords({ action: 'look around', submission: null }, false)).toBe('look around');
    expect(playerWords({ action: 'look around' }, false)).toBe('look around');
    expect(playerWords({ action: 'look around', submission: '   ' }, false)).toBe('look around');
  });

  it('says nothing at all for a row that recorded none of the player\'s words', () => {
    // Older rows predate the ledger carrying any command. They must render their narration alone rather
    // than an empty quote, which would read as the player having submitted a blank line.
    expect(playerWords({ action: null, submission: null }, false)).toBeNull();
    expect(playerWords({}, true)).toBeNull();
  });
});

/**
 * #873 — THE STEPS A CHRONICLE NEVER GOT TO.
 *
 * A six-step procedure that managed four showed four lines and said nothing about the other two, so a
 * player reading back could not tell a plan that finished from one cut short. These hold the line that
 * fixes it, and hold it to being short: it sits under the prose, not in front of it.
 */
describe('the steps a plan never got to', () => {
  it('says how many were left, and why the plan stopped', () => {
    expect(setAsideLine({ stepsSetAside: ['light the fire', 'boil the water'], setAsideBecause: 'FAILED_STEP' }))
      .toBe('2 steps left untried — the work before it did not succeed.');
  });

  it('tells a plan that broke apart from a plan that was simply too long', () => {
    // These are different stories and a player deserves to know which one happened to them.
    expect(setAsideLine({ stepsSetAside: ['rest'], setAsideBecause: 'STEP_LIMIT' }))
      .toBe('One step left untried — the plan ran longer than one stretch of work.');
    expect(setAsideLine({ stepsSetAside: ['rest'], setAsideBecause: 'FAILED_STEP' }))
      .not.toContain('longer than one stretch');
  });

  it('adds that they were taken up later, when they were', () => {
    expect(setAsideLine({ stepsSetAside: ['boil the water'], setAsideBecause: 'FAILED_STEP', takenUpLater: true }))
      .toContain('You took them up later.');
    expect(setAsideLine({ stepsSetAside: ['boil the water'], setAsideBecause: 'FAILED_STEP', takenUpLater: false }))
      .not.toContain('later');
  });

  it('says nothing at all for a row that set nothing aside, which is almost all of them', () => {
    expect(setAsideLine({})).toBeNull();
    expect(setAsideLine({ stepsSetAside: [] })).toBeNull();
    expect(setAsideLine({ stepsSetAside: null })).toBeNull();
    // A remainder whose steps are all blank is no remainder: it must not render an empty list.
    expect(setAsideLine({ stepsSetAside: ['', '   '] })).toBeNull();
    expect(setAsideSteps({ stepsSetAside: ['', '   '] })).toEqual([]);
  });

  it('keeps the untried steps in the order they were written, trimmed', () => {
    expect(setAsideSteps({ stepsSetAside: ['  light the fire ', 'boil the water'] }))
      .toEqual(['light the fire', 'boil the water']);
  });
});
