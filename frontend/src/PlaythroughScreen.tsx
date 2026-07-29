import { useEffect, useRef, useState } from 'react';
import forestArt from './assets/playthrough-forest-v1.png';
import streamArt from './assets/playthrough-stream-v1.png';
import quarryArt from './assets/playthrough-quarry-v1.png';
import clayDepositArt from './assets/playthrough-clay-deposit-v1.png';

const previewBody = [
  ['Health', 'Healthy'], ['Condition', 'Unsteady'], ['Hunger', 'Satisfied'], ['Thirst', 'Hydrated'],
  ['Energy', 'Rested'], ['Temperature', 'Comfortable'], ['Wetness', 'Damp'], ['Bladder', 'Comfortable'], ['Bowel', 'Comfortable'], ['Hygiene', 'Normal'],
];

type BodySnapshot = { health: string; condition: string; hunger: string; thirst: string; energy: string; temperature: string; wetness: string; bladder: string; bowel: string; hygiene: string };
type ActionResult = { actionId: string; intent: string; outcome: string; durationMinutes: number; perception: string; body: BodySnapshot };
type LocationSnapshot = { biome: string; presentationKey: string };
type ItemState = { carried: { id: string; displayName: string; itemKey: string }[]; equipped: { id: string; displayName: string; bodyPosition: string; layer: string }[] };
type Panel = 'none' | 'chronicle' | 'equipment' | 'storage' | 'crafting' | 'construction' | 'knowledge' | 'map';

const backdropByBiome: Record<string, { art: string; label: string }> = {
  TEMPERATE_FOREST: { art: forestArt, label: 'Uncharted forest' },
  WETLAND: { art: streamArt, label: 'Forest stream' },
  MOUNTAIN: { art: quarryArt, label: 'Stone basin' },
  HIGHLAND: { art: quarryArt, label: 'Highland quarry' },
  CLAY_DEPOSIT: { art: clayDepositArt, label: 'Clay deposit' },
};

function toBodyRows(snapshot: BodySnapshot) {
  return [['Health', snapshot.health], ['Condition', snapshot.condition], ['Hunger', snapshot.hunger], ['Thirst', snapshot.thirst], ['Energy', snapshot.energy], ['Temperature', snapshot.temperature], ['Wetness', snapshot.wetness], ['Bladder', snapshot.bladder], ['Bowel', snapshot.bowel], ['Hygiene', snapshot.hygiene]];
}

export function PlaythroughScreen({ apiUrl, onReturnToMainMenu }: { apiUrl?: string; onReturnToMainMenu: () => void }) {
  const [action, setAction] = useState('');
  const [body, setBody] = useState(previewBody);
  const [perception, setPerception] = useState('Cold air fills your lungs. Rainwater darkens the leaves around you. A narrow stream moves somewhere to your right, beneath the hush of unfamiliar trees.');
  const [resolving, setResolving] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [location, setLocation] = useState(backdropByBiome.TEMPERATE_FOREST);
  const [panel, setPanel] = useState<Panel>('none');
  const [items, setItems] = useState<ItemState | null>(null);
  const actionField = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    if (!apiUrl) return;
    fetch(`${apiUrl}/api/chronicles/active/body`).then(response => response.ok ? response.json() : null).then((snapshot: BodySnapshot | null) => {
      if (snapshot) setBody(toBodyRows(snapshot));
    }).catch(() => undefined);
  }, [apiUrl]);

  useEffect(() => {
    if (!apiUrl || (panel !== 'equipment' && panel !== 'storage')) return;
    fetch(`${apiUrl}/api/items/state`).then(response => response.ok ? response.json() : null).then((snapshot: ItemState | null) => setItems(snapshot)).catch(() => setItems(null));
  }, [apiUrl, panel]);

  useEffect(() => {
    const key = (event: KeyboardEvent) => { if (event.target instanceof HTMLTextAreaElement) return; const next: Record<string, Panel> = { c: 'chronicle', e: 'equipment', i: 'storage', k: 'knowledge', m: 'map' }; if (next[event.key.toLowerCase()]) setPanel(next[event.key.toLowerCase()]); };
    window.addEventListener('keydown', key); return () => window.removeEventListener('keydown', key);
  }, []);

  useEffect(() => {
    if (!apiUrl) return;
    fetch(`${apiUrl}/api/chronicles/active/location`).then(response => response.ok ? response.json() : null).then((snapshot: LocationSnapshot | null) => {
      if (snapshot) setLocation(backdropByBiome[snapshot.presentationKey] ?? backdropByBiome[snapshot.biome] ?? backdropByBiome.TEMPERATE_FOREST);
    }).catch(() => undefined);
  }, [apiUrl]);

  useEffect(() => {
    const field = actionField.current;
    if (!field) return;
    field.style.height = 'auto';
    field.style.height = `${Math.min(field.scrollHeight, 102)}px`;
  }, [action]);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    const text = action.trim();
    if (!text || resolving) return;
    if (!apiUrl) { setActionError('The local simulation service is unavailable.'); return; }
    setResolving(true);
    setActionError(null);
    try {
      const response = await fetch(`${apiUrl}/api/actions`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ text }) });
      const result = await response.json().catch(() => null) as ActionResult | { message?: string } | null;
      if (!response.ok || !result || !('perception' in result)) throw new Error(result && 'message' in result && result.message ? result.message : 'The simulation could not resolve that action.');
      setPerception(result.perception);
      setBody(toBodyRows(result.body));
      setAction('');
    } catch (error) {
      setActionError(error instanceof Error ? error.message : 'The simulation could not resolve that action.');
    } finally {
      setResolving(false);
    }
  }

  return <main className="playthrough" style={{ backgroundImage: `url(${location.art})` }}>
    <div className="playthrough-vignette" />
    <header className="world-header">
      <div><p className="eyebrow">{location.label}</p><strong>Early morning</strong></div>
      <div className="world-signs" aria-label="Environmental conditions"><span title="Daylight">Dawn</span><span title="Weather">Light rain</span><span title="Season">Early spring</span></div>
      <button className="quiet-menu" aria-label="Open menus" onClick={() => setPanel(panel === 'none' ? 'chronicle' : 'none')}>☰</button>
    </header>
    <aside className="body-hud" aria-label="Body awareness">
      <p className="eyebrow">Body</p>
      {body.map(([label, value]) => <div key={label}><span>{label}</span><strong>{value}</strong></div>)}
    </aside>
    {panel !== 'none' && <aside className="expanded-menu" aria-label="Chronicle menu">
      <header><p className="eyebrow">Chronicle systems</p><button onClick={() => setPanel('none')}>Close</button></header>
      <nav>{([['chronicle','Chronicle'],['equipment','Equipment'],['storage','Storage'],['knowledge','Knowledge'],['map','Chronicle Map']] as [Panel,string][]).map(([id,label]) => <button key={id} className={panel===id?'active':''} onClick={() => setPanel(id)}>{label}</button>)}<button className="return-main" onClick={onReturnToMainMenu}>Return to Main Menu</button></nav>
      <section className="menu-detail">
        {panel==='equipment' && <><h2>Equipment</h2>{items?.equipped.length ? items.equipped.map(item => <p key={item.id}>{item.bodyPosition.replace('_',' ')} · {item.displayName}</p>) : <p>Nothing is attached to your body.</p>}</>}
        {panel==='storage' && <><h2>Storage</h2>{items?.carried.filter(item => item.itemKey==='woven_basket').length ? items.carried.filter(item => item.itemKey==='woven_basket').map(item => <p key={item.id}>{item.displayName} is carried and may be opened when interacted with.</p>) : <p>You have no accessible storage object.</p>}</>}
        {panel==='chronicle' && <><h2>Chronicle</h2><p>This life has only just begun.</p></>}
        {(['crafting','construction','knowledge','map'] as Panel[]).includes(panel) && <><h2>{panel==='map'?'Chronicle Map':panel[0].toUpperCase()+panel.slice(1)}</h2><p>Nothing is available here yet.</p></>}
      </section>
    </aside>}
    <div className="playthrough-bottom">
      <section className="perception" aria-label="Current perception">
        <p className="eyebrow">Awakening</p>
        <p>{perception}</p>
        <p className="perception-note">You have no map. You have no supplies. You are here.</p>
      </section>
      <form className="action-composer" onSubmit={submit}>
        <label htmlFor="action">What do you do?</label>
        <div><span aria-hidden="true">&gt;</span><textarea ref={actionField} id="action" value={action} maxLength={2500} rows={1} disabled={resolving} onChange={event => setAction(event.target.value)} onKeyDown={event => { if (event.key === 'Enter' && !event.shiftKey) { event.preventDefault(); event.currentTarget.form?.requestSubmit(); } }} placeholder="Describe an action..." autoComplete="off" /><button aria-label="Submit action" type="submit" disabled={resolving}>{resolving ? '⌛' : 'Submit'}</button></div>
        <p className="action-count">{action.length}/2500 · Shift + Enter for a new line</p>
        {actionError && <p className="action-error" role="status">{actionError}</p>}
      </form>
    </div>
  </main>;
}
