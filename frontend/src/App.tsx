import { useCallback, useEffect, useState } from 'react';
import { OverseerMap } from './OverseerMap';

type SimulationState = { tick: number; simulatedAt: string };
// A published Atlas must never probe a visitor's local machine. The local
// backend is used automatically only by the development server.
const apiUrl = import.meta.env.VITE_DRAUGR_API_URL ?? (import.meta.env.DEV ? 'http://localhost:8080' : undefined);

export function App() {
  if (new URLSearchParams(window.location.search).get('mode') === 'overseer') return <OverseerMap />;
  const [state, setState] = useState<SimulationState | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [advancing, setAdvancing] = useState(false);

  const load = useCallback(async () => {
    if (!apiUrl) {
      setError('The local simulation is available only in the desktop application. Open the Overseer Atlas to view the public world preview.');
      return;
    }
    try {
      const response = await fetch(`${apiUrl}/api/simulation`);
      if (!response.ok) throw new Error('The simulation is unavailable.');
      setState(await response.json()); setError(null);
    } catch (reason) { setError(reason instanceof Error ? reason.message : 'Unable to load simulation state.'); }
  }, []);
  useEffect(() => { void load(); }, [load]);

  async function advance() {
    setAdvancing(true);
    try {
      const response = await fetch(`${apiUrl}/api/simulation/ticks`, { method: 'POST' });
      if (!response.ok) throw new Error('The simulation could not advance.');
      setState(await response.json()); setError(null);
    } catch (reason) { setError(reason instanceof Error ? reason.message : 'Unable to advance simulation.'); }
    finally { setAdvancing(false); }
  }

  return <main>
    <p className="eyebrow">Project Draugr / local simulation</p>
    <h1>The world remembers.</h1>
    {error ? <p role="alert" className="error">{error}</p> : <section className="state">
      <span>Authoritative tick</span><strong>{state?.tick ?? '—'}</strong>
      <span>Simulated time</span><time>{state ? new Date(state.simulatedAt).toLocaleString() : 'Loading…'}</time>
    </section>}
    <button onClick={advance} disabled={advancing}>{advancing ? 'Advancing…' : 'Advance simulation'}</button>
  </main>;
}
