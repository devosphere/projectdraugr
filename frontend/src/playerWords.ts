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
