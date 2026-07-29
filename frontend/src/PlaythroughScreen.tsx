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
type Panel = 'none' | 'chronicle' | 'equipment' | 'load' | 'storage' | 'crafting' | 'construction' | 'knowledge' | 'map' | 'literature';
type ReaderDocument = 'field-journal' | 'folded-letter';

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

function EquipmentHierarchy({ prototype, equipped }: { prototype: boolean; equipped?: ItemState['equipped'] }) {
  const attached = (position: string, fallback = 'Empty') => prototype ? fallback : equipped?.filter(item => item.bodyPosition === position).map(item => item.displayName).join(', ') || 'Empty';
  const group = (title: string, entries: [string, string][]) => <section className="equipment-group" key={title}><h3>{title}</h3>{entries.map(([slot,value]) => <div className="equipment-slot" key={slot}><span>{slot}</span><strong>{value}</strong></div>)}</section>;
  return <div className="equipment-hierarchy">{[
    group('Head', [['Upper head', attached('HEAD')], ['Mid head', 'Empty'], ['Lower head', attached('FACE')]]),
    group('Neck & Shoulders', [['Neck', attached('NECK')], ['Left shoulder', attached('SHOULDER_LEFT')], ['Right shoulder', attached('SHOULDER_RIGHT')]]),
    group('Upper Body', [['Inner layer', prototype ? 'Linen shirt' : attached('TORSO')], ['Body layer', 'Empty'], ['Outer layer', 'Empty'], ['Protective layer', 'Empty'], ['Back equipment', prototype ? 'Woven basket' : attached('BACK')]]),
    group('Arms', [['Left arm · protection', attached('ARM_LEFT')], ['Right arm · protection', attached('ARM_RIGHT')]]),
    group('Hands', [['Left hand', attached('HAND_LEFT')], ['Right hand', attached('HAND_RIGHT')]]),
    group('Fingers', [['Left thumb', 'Empty'], ['Left index', 'Empty'], ['Left middle', 'Empty'], ['Left ring', 'Empty'], ['Left little', 'Empty'], ['Right thumb', 'Empty'], ['Right index', 'Empty'], ['Right middle', 'Empty'], ['Right ring', 'Empty'], ['Right little', 'Empty']]),
    group('Waist', [['Waist', prototype ? 'Fiber cord' : attached('WAIST')]]),
    group('Lower Body', [['Under layer', 'Empty'], ['Outer layer', 'Empty'], ['Protective layer', 'Empty']]),
    group('Legs & Knees', [['Leg protection', attached('LEG_LEFT')], ['Knee protection', 'Empty']]),
    group('Feet', [['Inner layer', 'Empty'], ['Outer layer', prototype ? 'Bare feet' : attached('FOOT_LEFT')]])
  ]}</div>;
}

export function PlaythroughScreen({ apiUrl, onReturnToMainMenu }: { apiUrl?: string; onReturnToMainMenu: () => void }) {
  const [action, setAction] = useState('');
  const [body, setBody] = useState(previewBody);
  const [perception, setPerception] = useState('Cold air fills your lungs. Rainwater darkens the leaves around you. A narrow stream moves somewhere to your right, beneath the hush of unfamiliar trees.');
  const [resolving, setResolving] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [location, setLocation] = useState(backdropByBiome.TEMPERATE_FOREST);
  const [panel, setPanel] = useState<Panel>('none');
  const [menuOpen, setMenuOpen] = useState(false);
  const [bodyOpen, setBodyOpen] = useState(false);
  const [mapOverlay, setMapOverlay] = useState(false);
  const [readerDocument, setReaderDocument] = useState<ReaderDocument | null>(null);
  const [items, setItems] = useState<ItemState | null>(null);
  const prototypeMode = !apiUrl;
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
      <div className="header-controls"><button className="quiet-body" aria-label="Open body awareness" aria-expanded={bodyOpen} onClick={() => { setBodyOpen(!bodyOpen); setMenuOpen(false); setPanel('none'); }}>🧍</button><button className="quiet-menu" aria-label="Open menus" aria-expanded={menuOpen} onClick={() => { setMenuOpen(!menuOpen); setBodyOpen(false); setPanel('none'); }}>☰</button></div>
    </header>
    <aside className={`body-hud${bodyOpen ? ' is-open' : ''}`} aria-label="Body awareness">
      <p className="eyebrow">Body</p>
      {body.map(([label, value]) => <div key={label}><span>{label}</span><strong>{value}</strong></div>)}
    </aside>
    {menuOpen && <aside className="expanded-menu" aria-label="Chronicle menu">
      {panel === 'none' ? <nav>{((prototypeMode ? [['chronicle','Chronicle'],['equipment','Equipment'],['load','Load'],['storage','Storage'],['crafting','Crafting'],['construction','Construction'],['knowledge','Knowledge'],['map','Chronicle Map'],['literature','Literature']] : [['chronicle','Chronicle'],['equipment','Equipment'],['load','Load'],['knowledge','Knowledge']]) as [Panel,string][]).map(([id,label]) => <button key={id} onClick={() => setPanel(id)}>{label}</button>)}<button className="return-main" onClick={onReturnToMainMenu}>Return to Main Menu</button></nav> : <>
      <header><button aria-label="Back to menu" onClick={() => setPanel('none')}>←</button></header>
      <section className="menu-detail">
        {panel==='equipment' && <><h2>Equipment</h2><EquipmentHierarchy prototype={prototypeMode} equipped={items?.equipped} /></>}
        {panel==='load' && <><h2>Load</h2>{prototypeMode ? <><div className="record"><strong>Carried load</strong><span>6.3 kg / 25 kg sustained carry</span><span>4.8 L / 18 L direct bulk</span><span>Heaviest object · 1.2 kg / 40 kg lift</span></div><div className="record"><strong>Woven basket</strong><span>0.9 kg empty + 4.2 kg contents</span><span>Contains · plant fiber bundles, field stones</span></div></> : <p>Authoritative carried load will appear here.</p>}</>}
        {panel==='storage' && <><h2>Storage</h2>{prototypeMode ? <div className="record"><strong>Woven basket</strong><span>5.4 kg / 12 kg internal mass</span><span>4.2 L / 18 L internal volume</span><span>Contents · plant fiber bundles, field stones, clay lump</span></div> : items?.carried.filter(item => item.itemKey==='woven_basket').length ? items.carried.filter(item => item.itemKey==='woven_basket').map(item => <div className="record" key={item.id}><strong>{item.displayName}</strong><span>Carried and physically accessible</span></div>) : null}</>}
        {panel==='chronicle' && <><h2>Chronicle</h2><p>Arrival · first day</p><p>Current place · uncharted forest</p><p>Every life leaves a mark.</p></>}
        {panel==='crafting' && <><h2>Crafting</h2><div className="record"><strong>Woven basket</strong><span>Known through practice</span><span>Current materials · plant fiber bundles</span></div><div className="record"><strong>Primitive fire</strong><span>Established method</span></div><p className="perception-note">Reference only. Declare all attempts in the Action Composer.</p></>}
        {panel==='construction' && <><h2>Construction</h2><div className="record"><strong>Stone fire pit</strong><span>Understood</span><span>Current ground · forest clearing</span></div><p className="perception-note">Reference only. Declare all attempts in the Action Composer.</p></>}
        {panel==='knowledge' && <><h2>Knowledge</h2><div className="record"><strong>Plant fiber</strong><span>Workable material</span></div><div className="record"><strong>Fire tending</strong><span>Observed</span></div><div className="record"><strong>Forest water</strong><span>Unverified</span></div></>}
        {panel==='map' && <><h2>Chronicle Map</h2><button className="map-list-entry" onClick={() => setMapOverlay(true)}>Hand-drawn forest sketch <span>carried in woven basket</span></button><p className="perception-note">Only physical maps within reach are shown.</p></>}
        {panel==='literature' && <><h2>Literature</h2><button className="map-list-entry" onClick={() => setReaderDocument('field-journal')}><strong>Weather-worn field journal</strong><span>Carried in woven basket · Revision 3</span></button><button className="map-list-entry" onClick={() => setReaderDocument('folded-letter')}><strong>Folded letter</strong><span>Carried in woven basket · Revision 1</span></button><p className="perception-note">Only readable objects within reach are shown. Maps remain in Chronicle Map.</p></>}
      </section>
      </>}</aside>}
    {mapOverlay && <div className="map-overlay" role="dialog" aria-modal="true" aria-label="Hand-drawn forest sketch"><button aria-label="Close map" onClick={() => setMapOverlay(false)}>×</button><section><p className="eyebrow">Hand-drawn forest sketch</p><h2>Uncharted Forest</h2><div className="map-sketch"><span>Stream</span><span>Fallen cedar</span><span>Clay bank</span><i /></div><p>Weather-worn charcoal and bark marks. The far edges remain blank.</p></section></div>}
    {readerDocument && <div className="reader-overlay" role="dialog" aria-modal="true" aria-label="Read literature"><button aria-label="Close reader" onClick={() => setReaderDocument(null)}>×</button><article>{readerDocument === 'field-journal' ? <><p className="eyebrow">Weather-worn field journal</p><p className="reader-revision">Revision 3 · copied by hand</p><h2>Near the cedar</h2><div className="reader-page"><p>The stream turns shallow beside the fallen cedar. Clay clings beneath the roots after rain.</p><p>Something broad moved in the fern beds before dawn. It left the stems bent low, then the forest settled again.</p><p className="reader-mark">The next pages are blank.</p></div></> : <><p className="eyebrow">Folded letter</p><p className="reader-revision">Revision 1 · ink faded by damp</p><h2>Untitled</h2><div className="reader-page"><p>If this reaches you, keep it dry. The trail does not stay where it was drawn.</p><p className="reader-mark">No signature remains.</p></div></>}<p className="reader-note">Inspection only. Any writing, copying, or revision must be declared through the Action Composer.</p></article></div>}
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
