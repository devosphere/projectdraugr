import { useEffect, useRef, useState } from 'react';
import forestArt from './assets/playthrough-forest-v1.png';

const previewBody = [
  ['Health', 'Healthy'], ['Condition', 'Unsteady'], ['Hunger', 'Satisfied'], ['Thirst', 'Hydrated'],
  ['Energy', 'Rested'], ['Temperature', 'Comfortable'], ['Wetness', 'Damp'], ['Bladder', 'Comfortable'], ['Bowel', 'Comfortable'], ['Hygiene', 'Normal'],
];

type BodySnapshot = { health: string; condition: string; hunger: string; thirst: string; energy: string; temperature: string; wetness: string; bladder: string; bowel: string; hygiene: string };

export function PlaythroughScreen({ apiUrl }: { apiUrl?: string }) {
  const [action, setAction] = useState('');
  const [body, setBody] = useState(previewBody);
  const actionField = useRef<HTMLTextAreaElement>(null);
  useEffect(() => {
    if (!apiUrl) return;
    fetch(`${apiUrl}/api/chronicles/active/body`).then(response => response.ok ? response.json() : null).then((snapshot: BodySnapshot | null) => {
      if (!snapshot) return;
      setBody([['Health', snapshot.health], ['Condition', snapshot.condition], ['Hunger', snapshot.hunger], ['Thirst', snapshot.thirst], ['Energy', snapshot.energy], ['Temperature', snapshot.temperature], ['Wetness', snapshot.wetness], ['Bladder', snapshot.bladder], ['Bowel', snapshot.bowel], ['Hygiene', snapshot.hygiene]]);
    }).catch(() => undefined);
  }, [apiUrl]);
  useEffect(() => {
    const field = actionField.current;
    if (!field) return;
    field.style.height = 'auto';
    field.style.height = `${Math.min(field.scrollHeight, 102)}px`;
  }, [action]);
  function submit(event: React.FormEvent) {
    event.preventDefault();
    if (!action.trim()) return;
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
    <div className="playthrough-bottom">
      <section className="perception" aria-label="Current perception">
        <p className="eyebrow">Awakening</p>
        <p>Cold air fills your lungs. Rainwater darkens the leaves around you. A narrow stream moves somewhere to your right, beneath the hush of unfamiliar trees.</p>
        <p className="perception-note">You have no map. You have no supplies. You are here.</p>
      </section>
      <form className="action-composer" onSubmit={submit}>
        <label htmlFor="action">What do you do?</label>
        <div><span aria-hidden="true">›</span><textarea ref={actionField} id="action" value={action} maxLength={2500} rows={1} onChange={event => setAction(event.target.value)} onKeyDown={event => { if (event.key === 'Enter' && !event.shiftKey) { event.preventDefault(); event.currentTarget.form?.requestSubmit(); } }} placeholder="Describe an action…" autoComplete="off" /><button aria-label="Submit action" type="submit">↵</button></div>
        <p className="action-count">{action.length}/2500 · Shift + Enter for a new line</p>
      </form>
    </div>
  </main>;
}
