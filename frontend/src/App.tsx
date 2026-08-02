import { useEffect, useState } from 'react';
import { OnboardingScreen } from './OnboardingScreen';
import { OverseerMap } from './OverseerMap';
import { PlaythroughScreen } from './PlaythroughScreen';

export function App() {
  const [playing, setPlaying] = useState(false);
  const [hasLivingChronicle, setHasLivingChronicle] = useState(false);
  const [entryError, setEntryError] = useState<string | null>(null);
  const apiUrl = import.meta.env.VITE_DRAUGR_API_URL ?? (import.meta.env.DEV ? 'http://localhost:8080' : undefined);

  async function findLivingChronicle() {
    if (!apiUrl) return null;
    const response = await fetch(`${apiUrl}/api/chronicles/active`);
    if (!response.ok) return null;
    // When no chronicle is living the endpoint answers 200 with an empty body, so
    // response.json() would throw and be caught as "the world could not be reached"
    // — telling a player whose chronicle has just died that the server is down, and
    // blocking them from ever awakening a new one. Permanent death is the core of
    // this game, so that path is the common one, not the edge case.
    const raw = await response.text();
    if (!raw) return null;
    try { return JSON.parse(raw); } catch { return null; }
  }

  useEffect(() => {
    if (!apiUrl) return;
    findLivingChronicle().then(active => setHasLivingChronicle(Boolean(active))).catch(() => setHasLivingChronicle(false));
  }, [apiUrl]);

  async function enterWorld() {
    setEntryError(null);
    try {
      const active = await findLivingChronicle();
      if (active) {
        setHasLivingChronicle(true);
        setPlaying(true);
        return;
      }
      if (apiUrl) {
      const response = await fetch(`${apiUrl}/api/chronicles`, { method: 'POST' });
      if (!response.ok) throw new Error('Unable to awaken a Chronicle.');
      setHasLivingChronicle(true);
      }
      setPlaying(true);
    } catch { setEntryError('The world could not be reached. Start Project Draugr, then try again.'); }
  }
  if (new URLSearchParams(window.location.search).get('mode') === 'overseer') return <OverseerMap />;
  // Returning to the shore — after a death or by choice — re-checks whether a chronicle
  // still lives, so the onboarding button reads "Awaken" for a fresh start rather than
  // "Soul Link" into a chronicle that is already gone.
  function returnToMainMenu() {
    setPlaying(false);
    findLivingChronicle().then(active => setHasLivingChronicle(Boolean(active))).catch(() => setHasLivingChronicle(false));
  }
  return playing ? <PlaythroughScreen apiUrl={apiUrl} onReturnToMainMenu={returnToMainMenu} /> : <OnboardingScreen hasLivingChronicle={hasLivingChronicle} onAwaken={() => void enterWorld()} entryError={entryError} apiUrl={apiUrl} />;
}
