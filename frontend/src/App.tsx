import { useEffect, useState } from 'react';
import { OnboardingScreen } from './OnboardingScreen';
import { OverseerMap } from './OverseerMap';
import { PlaythroughScreen } from './PlaythroughScreen';

export function App() {
  const [playing, setPlaying] = useState(false);
  const [hasLivingChronicle, setHasLivingChronicle] = useState(false);
  const apiUrl = import.meta.env.VITE_DRAUGR_API_URL ?? (import.meta.env.DEV ? 'http://localhost:8080' : undefined);
  useEffect(() => {
    if (!apiUrl) return;
    fetch(`${apiUrl}/api/chronicles/active`).then(response => response.ok ? response.json() : null).then(active => setHasLivingChronicle(Boolean(active))).catch(() => setHasLivingChronicle(false));
  }, [apiUrl]);
  async function enterWorld() {
    if (!hasLivingChronicle && apiUrl) {
      const response = await fetch(`${apiUrl}/api/chronicles`, { method: 'POST' });
      if (!response.ok) throw new Error('Unable to awaken a Chronicle.');
      setHasLivingChronicle(true);
    }
    setPlaying(true);
  }
  if (new URLSearchParams(window.location.search).get('mode') === 'overseer') return <OverseerMap />;
  return playing ? <PlaythroughScreen apiUrl={apiUrl} /> : <OnboardingScreen hasLivingChronicle={hasLivingChronicle} onAwaken={() => void enterWorld()} />;
}
