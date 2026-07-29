import { OnboardingScreen } from './OnboardingScreen';
import { OverseerMap } from './OverseerMap';

export function App() {
  return new URLSearchParams(window.location.search).get('mode') === 'overseer'
    ? <OverseerMap />
    : <OnboardingScreen />;
}
