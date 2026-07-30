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
type ActionResult = { actionId: string; intent: string; outcome: string; durationMinutes: number; resolvedAt: string; perception: string; body: BodySnapshot };
type LocationSnapshot = { biome: string; presentationKey: string };
type EnvironmentSnapshot = { simulatedAt: string; weatherKind: string; ambientTemperatureC: number | null; windSpeedKph: number | null };
type ItemState = { carried: { id: string; displayName: string; itemKey: string; containerId: string | null }[]; equipped: { id: string; displayName: string; bodyPosition: string; layer: string }[]; load: { massGrams: number; bulkMl: number; heaviestObjectGrams: number; sustainedMassCapacityGrams: number; directBulkCapacityMl: number; maximumSingleLiftGrams: number }; containers: { id: string; displayName: string; maxMassGrams: number; maxVolumeMl: number; usedMassGrams: number; usedVolumeMl: number }[] };
type Panel = 'none' | 'chronicle' | 'equipment' | 'load' | 'storage' | 'crafting' | 'construction' | 'knowledge' | 'map' | 'literature';
type ReaderDocument = 'field-journal' | 'folded-letter';
type LiteratureDoc = { id: string; kind: string; title: string; revisionId: string | null; revisionNumber: number };
type LiteratureRevision = { id: string; kind: string; title: string; revisionId: string; revisionNumber: number; content: string; createdAt: string };
type NarrationEntry = { id: string; text: string; occurredAt?: string };
type NarrationPage = { entries: { id: string; occurredAt: string; narration: string }[]; hasMore: boolean };
type DiscoveryContext = { discoveries: string[]; constructions: { id: string; displayName: string; projectKind: string; state: string; progressPercent: number }[] };

const olderNarrationPages = [[
  { id: 'arrival-2', text: 'The familiar world recedes. Sound thins, distance loses meaning, and weakness moves through you before the rain-cold air of another place takes hold.' },
  { id: 'arrival-1', text: 'Pain loosens its grip. Beneath an unfamiliar sky, the forest remains where it was.' },
]];

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

function seasonAt(instant: string) {
  const month = new Date(instant).getUTCMonth();
  if (month <= 1 || month === 11) return 'Winter';
  if (month <= 4) return 'Spring';
  if (month <= 7) return 'Summer';
  return 'Autumn';
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
    group('Legs & Knees', [
      ['Left leg · protection', attached('LEG_LEFT')],
      ['Left knee · protection', attached('KNEE_LEFT')],
      ['Right leg · protection', attached('LEG_RIGHT')],
      ['Right knee · protection', attached('KNEE_RIGHT')]
    ]),
    group('Feet', [
      ['Left foot · inner layer', 'Empty'],
      ['Left foot · outer layer', prototype ? 'Bare foot' : attached('FOOT_LEFT')],
      ['Right foot · inner layer', 'Empty'],
      ['Right foot · outer layer', prototype ? 'Bare foot' : attached('FOOT_RIGHT')]
    ])
  ]}</div>;
}

export function PlaythroughScreen({ apiUrl, onReturnToMainMenu }: { apiUrl?: string; onReturnToMainMenu: () => void }) {
  const prototypeMode = !apiUrl;
  const [action, setAction] = useState('');
  const [body, setBody] = useState(previewBody);
  const [narrations, setNarrations] = useState<NarrationEntry[]>([{ id: 'arrival-3', text: 'Cold air fills your lungs. Rainwater darkens the leaves around you. A narrow stream moves somewhere to your right, beneath the hush of unfamiliar trees.' }]);
  const [olderNarrationPage, setOlderNarrationPage] = useState(0);
  const [hasOlderNarrations, setHasOlderNarrations] = useState(prototypeMode);
  const [loadingOlderNarrations, setLoadingOlderNarrations] = useState(false);
  const [resolving, setResolving] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [location, setLocation] = useState(backdropByBiome.TEMPERATE_FOREST);
  const [environment, setEnvironment] = useState({ time: 'Early morning', weather: 'Light rain', season: 'Early spring' });
  const [panel, setPanel] = useState<Panel>('none');
  const [menuOpen, setMenuOpen] = useState(false);
  const [bodyOpen, setBodyOpen] = useState(false);
  const [mapOverlay, setMapOverlay] = useState(false);
  const [readerDocument, setReaderDocument] = useState<ReaderDocument | null>(null);
  const [documents, setDocuments] = useState<LiteratureDoc[]>([]);
  const [openDoc, setOpenDoc] = useState<LiteratureRevision | null>(null);
  const [items, setItems] = useState<ItemState | null>(null);
  const [discoveries, setDiscoveries] = useState<DiscoveryContext | null>(null);
  const actionField = useRef<HTMLTextAreaElement>(null);
  const narrationTimeline = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!apiUrl) return;
    fetch(`${apiUrl}/api/chronicles/active/body`).then(response => response.ok ? response.json() : null).then((snapshot: BodySnapshot | null) => {
      if (snapshot) setBody(toBodyRows(snapshot));
    }).catch(() => undefined);
  }, [apiUrl]);

  useEffect(() => {
    if (!apiUrl) return;
    fetch(`${apiUrl}/api/chronicles/active/environment`).then(response => response.ok ? response.json() : null).then((snapshot: EnvironmentSnapshot | null) => {
      if (!snapshot) return;
      const hour = new Date(snapshot.simulatedAt).getUTCHours();
      setEnvironment({ time: hour < 6 ? 'Before dawn' : hour < 12 ? 'Morning' : hour < 18 ? 'Afternoon' : 'Night', weather: snapshot.weatherKind.toLowerCase().replace(/^./, letter => letter.toUpperCase()), season: `${seasonAt(snapshot.simulatedAt)} · ${Math.round(snapshot.ambientTemperatureC ?? 18)}°C · ${snapshot.windSpeedKph ?? 0} kph wind` });
    }).catch(() => undefined);
  }, [apiUrl]);

  useEffect(() => {
    if (!apiUrl) return;
    fetch(`${apiUrl}/api/chronicles/active/discoveries`).then(response => response.ok ? response.json() : null).then((context: DiscoveryContext | null) => setDiscoveries(context)).catch(() => undefined);
  }, [apiUrl]);

  useEffect(() => {
    if (!apiUrl) return;
    fetch(`${apiUrl}/api/actions/history?limit=20`).then(response => response.ok ? response.json() : null).then((page: NarrationPage | null) => {
      if (!page) return;
      // A newly awakened Chronicle has no action history yet. Keep the
      // arrival perception visible instead of replacing it with an empty list.
      if (page.entries.length > 0) {
        setNarrations(page.entries.reverse().map(entry => ({ id: entry.id, occurredAt: entry.occurredAt, text: entry.narration })));
      }
      setHasOlderNarrations(page.hasMore);
    }).catch(() => undefined);
  }, [apiUrl]);

  useEffect(() => {
    if (!apiUrl) return;
    fetch(`${apiUrl}/api/items/state`).then(response => response.ok ? response.json() : null).then((snapshot: ItemState | null) => setItems(snapshot)).catch(() => setItems(null));
  }, [apiUrl]);

  useEffect(() => {
    if (!apiUrl) return;
    fetch(`${apiUrl}/api/literature`).then(response => response.ok ? response.json() : null).then((docs: LiteratureDoc[] | null) => setDocuments(docs ?? [])).catch(() => undefined);
  }, [apiUrl]);

  async function openDocument(id: string) {
    if (!apiUrl) return;
    try {
      const response = await fetch(`${apiUrl}/api/literature/${id}`);
      const revision = response.ok ? await response.json() as LiteratureRevision : null;
      if (revision) setOpenDoc(revision);
    } catch { /* a document briefly out of reach simply does not open */ }
  }

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
    // A per-submission key so a duplicated delivery of this exact request resolves
    // once on the backend rather than advancing the world twice.
    const idempotencyKey = crypto.randomUUID();
    try {
      const response = await fetch(`${apiUrl}/api/actions`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ text, idempotencyKey }) });
      const result = await response.json().catch(() => null) as ActionResult | { message?: string } | null;
      if (!response.ok || !result || !('perception' in result)) throw new Error(result && 'message' in result && result.message ? result.message : 'The simulation could not resolve that action.');
      setNarrations(entries => [...entries, { id: result.actionId, occurredAt: result.resolvedAt, text: result.perception }]);
      setBody(toBodyRows(result.body));
      fetch(`${apiUrl}/api/chronicles/active/location`).then(response => response.ok ? response.json() : null).then((snapshot: LocationSnapshot | null) => {
        if (snapshot) setLocation(backdropByBiome[snapshot.presentationKey] ?? backdropByBiome[snapshot.biome] ?? backdropByBiome.TEMPERATE_FOREST);
      }).catch(() => undefined);
      fetch(`${apiUrl}/api/chronicles/active/discoveries`).then(response => response.ok ? response.json() : null).then((context: DiscoveryContext | null) => setDiscoveries(context)).catch(() => undefined);
      fetch(`${apiUrl}/api/items/state`).then(response => response.ok ? response.json() : null).then((snapshot: ItemState | null) => setItems(snapshot)).catch(() => setItems(null));
      fetch(`${apiUrl}/api/literature`).then(response => response.ok ? response.json() : null).then((docs: LiteratureDoc[] | null) => setDocuments(docs ?? [])).catch(() => undefined);
      fetch(`${apiUrl}/api/chronicles/active/environment`).then(response => response.ok ? response.json() : null).then((snapshot: EnvironmentSnapshot | null) => { if (snapshot) { const hour=new Date(snapshot.simulatedAt).getUTCHours(); setEnvironment({ time: hour < 6 ? 'Before dawn' : hour < 12 ? 'Morning' : hour < 18 ? 'Afternoon' : 'Night', weather: snapshot.weatherKind.toLowerCase().replace(/^./, letter => letter.toUpperCase()), season: `${seasonAt(snapshot.simulatedAt)} · ${Math.round(snapshot.ambientTemperatureC ?? 18)}°C · ${snapshot.windSpeedKph ?? 0} kph wind` }); } }).catch(() => undefined);
      setAction('');
    } catch (error) {
      setActionError(error instanceof Error ? error.message : 'The simulation could not resolve that action.');
    } finally {
      setResolving(false);
      actionField.current?.focus();
    }
  }

  async function loadOlderNarrations() {
    if (loadingOlderNarrations || !hasOlderNarrations) return;
    const timeline = narrationTimeline.current;
    const previousHeight = timeline?.scrollHeight ?? 0;
    if (prototypeMode) {
      if (olderNarrationPage >= olderNarrationPages.length) { setHasOlderNarrations(false); return; }
      setNarrations(entries => [...olderNarrationPages[olderNarrationPage], ...entries]);
      const next = olderNarrationPage + 1;
      setOlderNarrationPage(next);
      setHasOlderNarrations(next < olderNarrationPages.length);
      requestAnimationFrame(() => { if (timeline) timeline.scrollTop = timeline.scrollHeight - previousHeight; });
      return;
    }
    const oldest = narrations[0];
    if (!apiUrl || !oldest?.occurredAt) return;
    setLoadingOlderNarrations(true);
    try {
      const params = new URLSearchParams({ before: oldest.occurredAt, beforeId: oldest.id, limit: '20' });
      const response = await fetch(`${apiUrl}/api/actions/history?${params}`);
      const page = response.ok ? await response.json() as NarrationPage : null;
      if (!page) return;
      setNarrations(entries => [...page.entries.reverse().map(entry => ({ id: entry.id, occurredAt: entry.occurredAt, text: entry.narration })), ...entries]);
      setHasOlderNarrations(page.hasMore);
      requestAnimationFrame(() => { if (timeline) timeline.scrollTop = timeline.scrollHeight - previousHeight; });
    } finally { setLoadingOlderNarrations(false); }
  }

  function exportChronicle() {
    if (prototypeMode && olderNarrationPage < olderNarrationPages.length) {
      setNarrations(entries => [...olderNarrationPages.slice(olderNarrationPage).flat(), ...entries]);
      setOlderNarrationPage(olderNarrationPages.length);
      requestAnimationFrame(() => window.print());
      return;
    }
    window.print();
  }

  const menuItems = (prototypeMode ? [['chronicle','Chronicle'],['equipment','Equipment'],['load','Load'],['storage','Storage'],['crafting','Crafting'],['construction','Construction'],['knowledge','Knowledge'],['map','Chronicle Map'],['literature','Literature']] : [['chronicle','Chronicle'],['equipment','Equipment'],['load','Load'],...(items?.containers.length ? [['storage','Storage']] : []),...(discoveries?.discoveries.includes('WOVEN_BASKET') ? [['crafting','Crafting']] : []),...(discoveries?.constructions.length ? [['construction','Construction']] : []),...(discoveries?.discoveries.length ? [['knowledge','Knowledge']] : [])]) as [Panel,string][];

  return <main className="playthrough" style={{ backgroundImage: `url(${location.art})` }}>
    <div className="playthrough-vignette" />
    <header className="world-header">
      <div><p className="eyebrow">{location.label}</p><strong>{environment.time}</strong></div>
      <div className="world-signs" aria-label="Environmental conditions"><span title="Daylight">{environment.time}</span><span title="Weather">{environment.weather}</span><span title="Environment">{environment.season}</span></div>
      <div className="header-controls"><button className="quiet-body" aria-label="Open body awareness" aria-expanded={bodyOpen} onClick={() => { setBodyOpen(!bodyOpen); setMenuOpen(false); setPanel('none'); }}>🧍</button><button className="quiet-menu" aria-label="Open menus" aria-expanded={menuOpen} onClick={() => { setMenuOpen(!menuOpen); setBodyOpen(false); setPanel('none'); }}>☰</button></div>
    </header>
    <aside className={`body-hud${bodyOpen ? ' is-open' : ''}`} aria-label="Body awareness">
      <p className="eyebrow">Body</p>
      {body.map(([label, value]) => <div key={label}><span>{label}</span><strong>{value}</strong></div>)}
    </aside>
    {menuOpen && <aside className="expanded-menu" aria-label="Chronicle menu">
      {panel === 'none' ? <nav>{menuItems.map(([id,label]) => <button key={id} onClick={() => setPanel(id)}>{label}</button>)}<button className="export-chronicle" onClick={exportChronicle}>Export Chronicle</button><button className="return-main" onClick={onReturnToMainMenu}>Return to Main Menu</button></nav> : <>
      <header><button aria-label="Back to menu" onClick={() => setPanel('none')}>←</button></header>
      <section className="menu-detail">
        {panel==='equipment' && <><h2>Equipment</h2><EquipmentHierarchy prototype={prototypeMode} equipped={items?.equipped} /></>}
        {panel==='load' && <><h2>Load</h2>{prototypeMode ? <><div className="record"><strong>Carried load</strong><span>6.3 kg / 25 kg sustained carry</span><span>4.8 L / 18 L direct bulk</span><span>Heaviest object · 1.2 kg / 40 kg lift</span></div><div className="record"><strong>Woven basket</strong><span>0.9 kg empty + 4.2 kg contents</span><span>Contains · plant fiber bundles, field stones</span></div></> : items?.load && <><div className="record"><strong>Carried load</strong><span>{(items.load.massGrams / 1000).toFixed(1)} kg / {(items.load.sustainedMassCapacityGrams / 1000).toFixed(1)} kg sustained carry</span><span>{(items.load.bulkMl / 1000).toFixed(1)} L / {(items.load.directBulkCapacityMl / 1000).toFixed(1)} L direct bulk</span><span>Heaviest object · {(items.load.heaviestObjectGrams / 1000).toFixed(1)} kg / {(items.load.maximumSingleLiftGrams / 1000).toFixed(1)} kg lift</span></div>{items.carried.map(item => <div className="record" key={item.id}><strong>{item.displayName}</strong><span>{item.containerId ? 'Stored inside a carried container' : 'Carried directly'}</span></div>)}</>}</>}
        {panel==='storage' && <><h2>Storage</h2>{prototypeMode ? <div className="record"><strong>Woven basket</strong><span>5.4 kg / 12 kg internal mass</span><span>4.2 L / 18 L internal volume</span><span>Contents · plant fiber bundles, field stones, clay lump</span></div> : items?.containers.map(container => <div className="record" key={container.id}><strong>{container.displayName}</strong><span>{(container.usedMassGrams / 1000).toFixed(1)} kg / {(container.maxMassGrams / 1000).toFixed(1)} kg internal mass</span><span>{(container.usedVolumeMl / 1000).toFixed(1)} L / {(container.maxVolumeMl / 1000).toFixed(1)} L internal volume</span></div>)}</>}
        {panel==='chronicle' && <><h2>Chronicle</h2><p>Arrival · first day</p><p>Current place · uncharted forest</p><p>Every life leaves a mark.</p></>}
        {panel==='crafting' && <><h2>Crafting</h2>{prototypeMode ? <><div className="record"><strong>Woven basket</strong><span>Known through practice</span><span>Current materials · plant fiber bundles</span></div><div className="record"><strong>Primitive fire</strong><span>Established method</span></div></> : discoveries?.discoveries.filter(item => item==='WOVEN_BASKET').map(item => <div className="record" key={item}><strong>Woven basket</strong><span>Known through practice</span></div>)}<p className="perception-note">Reference only. Declare all attempts in the Action Composer.</p></>}
        {panel==='construction' && <><h2>Construction</h2>{prototypeMode ? <div className="record"><strong>Stone fire pit</strong><span>Understood</span><span>Current ground · forest clearing</span></div> : discoveries?.constructions.map(project => <div className="record" key={project.id}><strong>{project.displayName}</strong><span>{project.state.toLowerCase()} · {project.progressPercent}% complete</span></div>)}<p className="perception-note">Reference only. Declare all attempts in the Action Composer.</p></>}
        {panel==='knowledge' && <><h2>Knowledge</h2>{prototypeMode ? <><div className="record"><strong>Plant fiber</strong><span>Workable material</span></div><div className="record"><strong>Fire tending</strong><span>Observed</span></div><div className="record"><strong>Forest water</strong><span>Unverified</span></div></> : discoveries?.discoveries.map(item => <div className="record" key={item}><strong>{item === 'WOVEN_BASKET' ? 'Woven basket' : item === 'STONE_FIRE_PIT' ? 'Stone fire pit' : item}</strong><span>Established through practice</span></div>)}</>}
        {panel==='map' && <><h2>Chronicle Map</h2>{prototypeMode ? <button className="map-list-entry" onClick={() => setMapOverlay(true)}>Hand-drawn forest sketch <span>carried in woven basket</span></button> : <>{documents.filter(doc => doc.kind === 'MAP').map(doc => <button className="map-list-entry" key={doc.id} onClick={() => openDocument(doc.id)}><strong>{doc.title}</strong><span>Revision {doc.revisionNumber}</span></button>)}{documents.filter(doc => doc.kind === 'MAP').length === 0 && <p className="perception-note">No maps are within reach.</p>}</>}<p className="perception-note">Only physical maps within reach are shown.</p></>}
        {panel==='literature' && <><h2>Literature</h2>{prototypeMode ? <><button className="map-list-entry" onClick={() => setReaderDocument('field-journal')}><strong>Weather-worn field journal</strong><span>Carried in woven basket · Revision 3</span></button><button className="map-list-entry" onClick={() => setReaderDocument('folded-letter')}><strong>Folded letter</strong><span>Carried in woven basket · Revision 1</span></button></> : <>{documents.filter(doc => doc.kind !== 'MAP').map(doc => <button className="map-list-entry" key={doc.id} onClick={() => openDocument(doc.id)}><strong>{doc.title}</strong><span>Revision {doc.revisionNumber}</span></button>)}{documents.filter(doc => doc.kind !== 'MAP').length === 0 && <p className="perception-note">No readable records are within reach.</p>}</>}<p className="perception-note">Only readable objects within reach are shown. Maps remain in Chronicle Map.</p></>}
      </section>
      </>}</aside>}
    {mapOverlay && <div className="map-overlay" role="dialog" aria-modal="true" aria-label="Hand-drawn forest sketch"><button aria-label="Close map" onClick={() => setMapOverlay(false)}>×</button><section><p className="eyebrow">Hand-drawn forest sketch</p><h2>Uncharted Forest</h2><div className="map-sketch"><span>Stream</span><span>Fallen cedar</span><span>Clay bank</span><i /></div><p>Weather-worn charcoal and bark marks. The far edges remain blank.</p></section></div>}
    {readerDocument && <div className="reader-overlay" role="dialog" aria-modal="true" aria-label="Read literature"><button aria-label="Close reader" onClick={() => setReaderDocument(null)}>×</button><article>{readerDocument === 'field-journal' ? <><p className="eyebrow">Weather-worn field journal</p><p className="reader-revision">Revision 3 · copied by hand</p><h2>Near the cedar</h2><div className="reader-page"><p>The stream turns shallow beside the fallen cedar. Clay clings beneath the roots after rain.</p><p>Something broad moved in the fern beds before dawn. It left the stems bent low, then the forest settled again.</p><p className="reader-mark">The next pages are blank.</p></div></> : <><p className="eyebrow">Folded letter</p><p className="reader-revision">Revision 1 · ink faded by damp</p><h2>Untitled</h2><div className="reader-page"><p>If this reaches you, keep it dry. The trail does not stay where it was drawn.</p><p className="reader-mark">No signature remains.</p></div></>}<p className="reader-note">Inspection only. Any writing, copying, or revision must be declared through the Action Composer.</p></article></div>}
    {openDoc && <div className="reader-overlay" role="dialog" aria-modal="true" aria-label={openDoc.kind === 'MAP' ? 'Read map' : 'Read record'}><button aria-label="Close" onClick={() => setOpenDoc(null)}>×</button><article><p className="eyebrow">{openDoc.kind === 'MAP' ? 'Hand-drawn map' : 'Written record'}</p><p className="reader-revision">Revision {openDoc.revisionNumber}</p><h2>{openDoc.title}</h2>{openDoc.kind === 'MAP' ? <pre className="doc-body">{openDoc.content}</pre> : <div className="reader-page reader-prose">{openDoc.content}</div>}<p className="reader-note">Inspection only. Any writing, copying, or revision must be declared through the Action Composer.</p></article></div>}
    <div className="playthrough-bottom">
      <section className="perception" aria-label="Narrative history">
        <p className="eyebrow">Awakening</p>
        <div className="narration-timeline" ref={narrationTimeline} onScroll={event => { if (event.currentTarget.scrollTop < 12) loadOlderNarrations(); }}>
          {hasOlderNarrations && <p className="narration-loading">{loadingOlderNarrations ? 'Earlier memories surface…' : 'Scroll upward for earlier moments.'}</p>}
          {narrations.map(entry => <p key={entry.id}>{entry.text}</p>)}
        </div>
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
