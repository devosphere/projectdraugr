import { useState } from 'react';
import { OnboardingScreen } from './OnboardingScreen';
import { OverseerMap } from './OverseerMap';
import { PlaythroughScreen } from './PlaythroughScreen';

export function App() {
  const [playing, setPlaying] = useState(false);
  if (new URLSearchParams(window.location.search).get('mode') === 'overseer') return <OverseerMap />;
  return playing ? <PlaythroughScreen /> : <OnboardingScreen onAwaken={() => setPlaying(true)} />;
}
