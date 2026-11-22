/**
 * What the player's own line reads as for one row of the ledger.
 *
 * THE LEDGER KEEPS ONE ROW PER RESOLVED STEP; THE PLAYER WROTE ONE SENTENCE. Both are true and a reader
 * needs both, so the sentence stands at the head of its group — that is what was composed and submitted —
 * and the steps beneath it appear in the order they actually ran, each marked as carrying on.
 *
 * Before #892 the head of a group showed only its FIRST STEP, so a procedure that read as one sentence while
 * it was being played came back after a relaunch as its pieces, and nothing on screen was the thing the
 * player had typed. The live timeline, the Chronicle Archive (#888) and the PDF export are three readers of
 * the same ledger, so the rule lives here once; the backend's copy for the PDF is
 * `ChronicleNarrationExporter.wordsFor`, and the two must say the same thing.
 */

/**
 * The word that marks a step as carrying on from the line above it.
 *
 * It was an ellipsis, and an ellipsis in front of a sentence is how every reader on earth writes TRUNCATION:
 * the player read "… prepare a tinder nest" as the archive having cut their words short, which is the one
 * thing it must never appear to do to someone reading back their own life. A word cannot be mistaken for a
 * missing piece of text.
 */
export const CARRIES_ON = 'then ';

/** A ledger row as the two readers hold it: this step's words, and the submission they were cut from. */
export type SaidWords = { action?: string | null; submission?: string | null };

/**
 * @param row       the entry being rendered
 * @param continues true when the row above it came from the same submission
 * @returns the line to show, or null when this row never recorded any of the player's words at all
 */
export function playerWords(row: SaidWords, continues: boolean): string | null {
  const step = (row.action ?? '').trim();
  if (continues) return step ? CARRIES_ON + step : null;
  const whole = (row.submission ?? '').trim();
  // A row written before V464 has no submission to recover, and there its step text stands — which is
  // exactly what those rows have always shown.
  return whole || step || null;
}

/**
 * THE STEPS A CHRONICLE NEVER GOT TO (#873).
 *
 * <p>When a plan stops partway the undone steps are set aside, and until now they appeared nowhere: the
 * timeline showed four steps of a six-step procedure and said nothing about the other two, so a player
 * reading back could not tell a plan that finished from one that was cut short. That matters most at a
 * death, where the last thing attempted and the thing that killed them are usually different.
 *
 * <p>The line has to say three things and no more: that these were NOT attempted, why the plan stopped,
 * and whether they were taken up later. Anything longer competes with the prose it sits beneath.
 *
 * <p>The backend twin is `ChronicleNarrationExporter.setAsideFor`, and the two must say the same thing.
 */
export type SetAside = {
  stepsSetAside?: string[] | null;
  setAsideBecause?: string | null;
  takenUpLater?: boolean;
};

/** The heading above the untried steps, or null when this row set nothing aside. */
export function setAsideLine(row: SetAside): string | null {
  const steps = (row.stepsSetAside ?? []).filter((s) => s && s.trim());
  if (!steps.length) return null;
  const count = steps.length === 1 ? 'One step' : `${steps.length} steps`;
  // STEP_LIMIT and FAILED_STEP are the only two reasons the writer records, and they are different
  // stories: one is a plan that broke, the other a plan that was simply longer than one sitting.
  const why =
    row.setAsideBecause === 'STEP_LIMIT'
      ? 'the plan ran longer than one stretch of work'
      : 'the work before it did not succeed';
  const after = row.takenUpLater ? ' You took them up later.' : '';
  return `${count} left untried — ${why}.${after}`;
}

/** The untried steps themselves, in the order they were written. Empty when there are none. */
export function setAsideSteps(row: SetAside): string[] {
  return (row.stepsSetAside ?? []).filter((s) => s && s.trim()).map((s) => s.trim());
}
