import { useEffect, useState } from 'react';
import forestArt from './assets/onboarding-forest-v1.png';

type Panel = 'none' | 'archive' | 'settings' | 'crossing' | 'exit';

type ChronicleSummary = { id: string; sequenceNumber: number; lifeState: string; arrivedAt: string; diedAt: string | null; deathCause: string | null };
type JourneyEntry = { at: string; intent: string; outcome: string; narration: string };
type ChronicleJourney = { summary: ChronicleSummary; entries: JourneyEntry[]; finalBody: string | null; discoveries: number; placesNamed: number };

/** How long a life lasted, in the world's own reckoning. */
function lifespan(from: string, to: string | null) {
  const end = to ? new Date(to).getTime() : Date.now();
  const mins = Math.max(0, Math.round((end - new Date(from).getTime()) / 60000));
  const d = Math.floor(mins / 1440), h = Math.floor((mins % 1440) / 60);
  return d > 0 ? `${d}d ${h}h` : h > 0 ? `${h}h ${mins % 60}m` : `${mins}m`;
}

export function OnboardingScreen({ hasLivingChronicle, onAwaken, entryError, apiUrl }: { hasLivingChronicle: boolean; onAwaken: () => void; entryError?: string | null; apiUrl?: string }) {
  const [panel, setPanel] = useState<Panel>('none');
  const [roster, setRoster] = useState<ChronicleSummary[] | null>(null);
  const [journey, setJourney] = useState<ChronicleJourney | null>(null);
  const [archiveError, setArchiveError] = useState<string | null>(null);

  // The roster is loaded when the archive opens, not on mount — the main menu
  // should not depend on the world being reachable in order to render.
  useEffect(() => {
    if (panel !== 'archive' || !apiUrl || roster) return;
    setArchiveError(null);
    fetch(`${apiUrl}/api/chronicles/archive`)
      .then(r => (r.ok ? r.text() : Promise.reject(new Error('unreachable'))))
      .then(raw => setRoster(raw ? JSON.parse(raw) : []))
      .catch(() => setArchiveError('The archive could not be read. The world may not be running.'));
  }, [panel, apiUrl, roster]);

  function openJourney(id: string) {
    if (!apiUrl) return;
    setJourney(null); setArchiveError(null);
    fetch(`${apiUrl}/api/chronicles/${id}/journey`)
      .then(r => (r.ok ? r.text() : Promise.reject(new Error('unreachable'))))
      .then(raw => { if (raw) setJourney(JSON.parse(raw)); })
      .catch(() => setArchiveError('That life could not be read back.'));
  }

  // The server renders the whole life's narration to a PDF — every action, not
  // just what is on screen — and streams it back for download.
  async function exportJourneyPdf(id: string, sequenceNumber: number) {
    if (!apiUrl) return;
    try {
      const response = await fetch(`${apiUrl}/api/chronicles/${id}/narration.pdf`);
      if (!response.ok) throw new Error();
      const blob = await response.blob();
      const objectUrl = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = objectUrl;
      link.download = `chronicle-${sequenceNumber}.pdf`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      setTimeout(() => URL.revokeObjectURL(objectUrl), 1000);
    } catch {
      setArchiveError('That life could not be exported.');
    }
  }
  const primaryLabel = hasLivingChronicle ? 'Soul Link' : 'Awaken';
  const canExitApplication = window.location.protocol === 'file:' || ['localhost', '127.0.0.1'].includes(window.location.hostname);

  return <main className="onboarding" style={{ backgroundImage: `url(${forestArt})` }}>
    <div className="onboarding-shade" />
    <section className="onboarding-menu" aria-label="Project Draugr main menu">
      <p className="onboarding-kicker">A persistent survival chronicle</p>
      <h1>Project<br /><em>Draugr</em></h1>
      <p className="onboarding-tagline"><strong>Every life leaves a mark.</strong><br /><em>The world remembers. The Overseer never forgets.</em></p>
      <nav className="onboarding-actions" aria-label="Main actions">
        <button className="menu-button primary" onClick={() => setPanel('crossing')}><span aria-hidden="true">○</span>{primaryLabel}</button>
        <button className="menu-button" onClick={() => setPanel('archive')}><span aria-hidden="true">○</span>Chronicle Archive</button>
        <button className="menu-button" onClick={() => setPanel('settings')}><span aria-hidden="true">○</span>Settings</button>
        {canExitApplication && <button className="menu-button" onClick={() => setPanel('exit')}><span aria-hidden="true">○</span>Exit</button>}
      </nav>
    </section>

    {panel !== 'none' && <div className="onboarding-dialog-backdrop" role="presentation" onClick={() => setPanel('none')}>
      <section className={`onboarding-dialog ${panel === 'crossing' ? 'crossing-dialog' : ''}`} role="dialog" aria-modal="true" aria-label={panel} onClick={event => event.stopPropagation()}>
        {panel === 'crossing' && <>
          <p className="dialog-kicker">First awakening</p>
          <p className="crossing-copy">The familiar world fractures at its edges.</p>
          <p className="crossing-copy">A sudden pressure grips behind your eyes. Your balance fails. Your stomach turns as though the ground has dropped away beneath you.</p>
          <p className="crossing-copy">Then the pain is gone.</p>
          <p className="crossing-copy">Cold air fills your lungs. Damp earth presses beneath your palms. Above you: an unfamiliar sky.</p>
          <p className="crossing-copy final">Your soul has found the body waiting for it.</p>
          <button className="dialog-close" onClick={onAwaken}>OK</button>
          {entryError && <p role="status">{entryError}</p>}
        </>}
        {panel === 'archive' && <>
          <p className="dialog-kicker">Chronicle Archive</p>
          {archiveError && <p role="status">{archiveError}</p>}

          {/* A single life, read back in full. */}
          {journey ? <div className="archive-journey">
            <h2>Chronicle {journey.summary.sequenceNumber}</h2>
            <p className="archive-meta">
              {journey.summary.lifeState === 'DEAD'
                ? <>Lived {lifespan(journey.summary.arrivedAt, journey.summary.diedAt)} · {journey.summary.deathCause}</>
                : <>Living · {lifespan(journey.summary.arrivedAt, null)} so far</>}
              {' · '}{journey.entries.length} action{journey.entries.length === 1 ? '' : 's'}
              {journey.discoveries > 0 && <> · {journey.discoveries} discover{journey.discoveries === 1 ? 'y' : 'ies'}</>}
              {journey.placesNamed > 0 && <> · {journey.placesNamed} place{journey.placesNamed === 1 ? '' : 's'} named</>}
            </p>
            {journey.entries.length === 0
              ? <p>This chronicle resolved no actions before the end. The world holds nothing of them but their arrival.</p>
              : <ol className="archive-entries">
                  {journey.entries.map((e, i) => <li key={i}>
                    <span className="archive-entry-time">{new Date(e.at).toISOString().slice(0, 16).replace('T', ' ')}</span>
                    <span className="archive-entry-text">{e.narration}</span>
                  </li>)}
                </ol>}
          </div>

          /* The roster of every life the world has held. */
          : <div className="archive-roster">
            {roster === null && !archiveError && <p>Reading the archive…</p>}
            {roster !== null && roster.length === 0 && <>
              <h2>No lives recorded yet.</h2>
              <p>When a Chronicle awakens, their record begins here: arrival, discoveries, hardships, and the final moment.</p>
            </>}
            {roster !== null && roster.length > 0 && <>
              <h2>{roster.length} {roster.length === 1 ? 'life' : 'lives'} recorded</h2>
              <ul className="archive-list">
                {roster.map(c => <li key={c.id}>
                  <button className="archive-item" onClick={() => openJourney(c.id)}>
                    <span className="archive-item-name">Chronicle {c.sequenceNumber}</span>
                    <span className="archive-item-fate">
                      {c.lifeState === 'DEAD'
                        ? <>{c.deathCause} · lived {lifespan(c.arrivedAt, c.diedAt)}</>
                        : <>Still living · {lifespan(c.arrivedAt, null)}</>}
                    </span>
                  </button>
                </li>)}
              </ul>
            </>}
          </div>}

          <div className="archive-footer">
            {journey && <button className="dialog-close" onClick={() => setJourney(null)}>Back to archive</button>}
            {journey && <button className="dialog-close" onClick={() => exportJourneyPdf(journey.summary.id, journey.summary.sequenceNumber)}>Export PDF</button>}
            <button className="dialog-close" onClick={() => { setPanel('none'); setJourney(null); }}>Return</button>
          </div>
        </>}
        {panel === 'settings' && <>
          <p className="dialog-kicker">Settings</p>
          <h2>Display</h2>
          <label>Window mode<select defaultValue="borderless"><option value="borderless">Borderless fullscreen</option><option value="fullscreen">Fullscreen</option><option value="windowed">Windowed</option></select></label>
          <label>Resolution<select defaultValue="1920x1080"><option>1280 × 720</option><option>1366 × 768</option><option>1600 × 900</option><option>1920 × 1080</option><option>2560 × 1440</option><option>3840 × 2160</option></select></label>
          <label>Interface scale<select defaultValue="Default"><option>Small</option><option>Default</option><option>Large</option></select></label>
          <button className="dialog-close" onClick={() => setPanel('none')}>Done</button>
        </>}
        {panel === 'exit' && <>
          <p className="dialog-kicker">Exit Project Draugr</p><h2>Leave the world behind?</h2><p>Your persistent world remains unchanged.</p>
          <button className="dialog-close" onClick={() => window.close()}>Exit</button><button className="dialog-close" onClick={() => setPanel('none')}>Return</button>
        </>}
      </section>
    </div>}
  </main>;
}
