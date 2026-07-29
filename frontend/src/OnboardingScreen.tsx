import { useState } from 'react';
import forestArt from './assets/onboarding-forest-v1.png';

type Panel = 'none' | 'archive' | 'settings' | 'crossing' | 'exit';

export function OnboardingScreen({ hasLivingChronicle, onAwaken }: { hasLivingChronicle: boolean; onAwaken: () => void }) {
  const [panel, setPanel] = useState<Panel>('none');
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
        </>}
        {panel === 'archive' && <>
          <p className="dialog-kicker">Chronicle Archive</p>
          <h2>No lives have concluded.</h2>
          <p>When a Chronicle dies, their complete life record will remain here: arrival, discoveries, hardships, and the final moment.</p>
          <button className="dialog-close" onClick={() => setPanel('none')}>Return</button>
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
