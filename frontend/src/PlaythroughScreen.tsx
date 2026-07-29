import { useState } from 'react';
import forestArt from './assets/playthrough-forest-v1.png';

const body = [
  ['Health', 'Healthy'], ['Condition', 'Unsteady'], ['Hunger', 'Satisfied'], ['Thirst', 'Hydrated'],
  ['Energy', 'Rested'], ['Temperature', 'Comfortable'], ['Wetness', 'Damp'], ['Hygiene', 'Normal'],
];

export function PlaythroughScreen() {
  const [action, setAction] = useState('');
  const [notice, setNotice] = useState<string | null>(null);
  function submit(event: React.FormEvent) {
    event.preventDefault();
    if (!action.trim()) return;
    setNotice('Your intention has been heard. The simulation will resolve it in the next playable slice.');
    setAction('');
  }
  return <main className="playthrough" style={{ backgroundImage: `url(${forestArt})` }}>
    <div className="playthrough-vignette" />
    <header className="world-header">
      <div><p className="eyebrow">Uncharted forest</p><strong>Early morning</strong></div>
      <div className="world-signs" aria-label="Environmental conditions"><span title="Daylight">◐ Dawn</span><span title="Weather">◌ Light rain</span><span title="Season">⌁ Early spring</span></div>
      <button className="quiet-menu" aria-label="Open menus">☰</button>
    </header>
    <aside className="body-hud" aria-label="Body awareness">
      <p className="eyebrow">Body</p>
      {body.map(([label, value]) => <div key={label}><span>{label}</span><strong>{value}</strong></div>)}
    </aside>
    <section className="perception" aria-label="Current perception">
      <p className="eyebrow">Awakening</p>
      <p>Cold air fills your lungs. Rainwater darkens the leaves around you. A narrow stream moves somewhere to your right, beneath the hush of unfamiliar trees.</p>
      <p className="perception-note">You have no map. You have no supplies. You are here.</p>
    </section>
    <form className="action-composer" onSubmit={submit}>
      <label htmlFor="action">What do you do?</label>
      <div><span aria-hidden="true">›</span><input id="action" value={action} onChange={event => setAction(event.target.value)} placeholder="Describe an action…" autoComplete="off" /><button aria-label="Submit action" type="submit">↵</button></div>
      {notice && <p role="status">{notice}</p>}
    </form>
  </main>;
}
