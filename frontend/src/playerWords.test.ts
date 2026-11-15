import { describe, expect, it } from 'vitest';
import { CARRIES_ON, playerWords } from './playerWords';

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
