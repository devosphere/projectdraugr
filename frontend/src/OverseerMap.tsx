import { useEffect, useState } from 'react';
import atlasArt from './assets/overseer-atlas-v1.png';

// Keep the public GitHub Pages Atlas fully static: it must not request access
// to a visitor's local backend. Local development still uses the Spring API.
const apiUrl = import.meta.env.VITE_DRAUGR_API_URL ?? (import.meta.env.DEV ? 'http://localhost:8080' : undefined);
const seed = 681013497;
type Marker = { category: 'RESOURCE' | 'WILDLIFE' | 'MONSTER' | 'RUIN'; label: string; x: number; y: number };
type Preview = { markers: Marker[] };
const fallbackMarkers: Marker[] = [
  ...['Freshwater spring', 'Old-growth timber', 'Wild herb grove', 'Edible-root patch', 'Reed marsh', 'Clay beds', 'Flint field', 'Stone outcrop', 'Iron vein', 'Copper seam', 'Salt marsh', 'Mushroom hollow', 'Fiber grassland', 'Shelter grove'].map((label, index) => ({ category: 'RESOURCE' as const, label, x: (index * 5 + 2) % 28, y: (index * 7 + 3) % 20 })),
  ...['Deer range', 'Boar range', 'Elk range', 'Wolf pack ground', 'Bear den', 'Marsh-fowl nesting', 'Hare warren', 'Fox earth', 'Goat cliff range', 'Beaver lodge'].map((label, index) => ({ category: 'WILDLIFE' as const, label, x: (index * 7 + 4) % 28, y: (index * 3 + 5) % 20 })),
  ...['Bog warden lair', 'Ridge stalker lair', 'Glasswing roost', 'Mire hydra nest', 'Deepwater maw', 'Dusk prowler territory', 'Thornback wallow', 'Ash hound den', 'Fen siren pool', 'Gloom moth colony'].map((label, index) => ({ category: 'MONSTER' as const, label, x: (index * 9 + 1) % 28, y: (index * 11 + 2) % 20 })),
  ...['Ancient observatory', 'Sunken shrine', 'Collapsed causeway', 'Overgrown watchtower', 'Flooded archive', 'Buried granary', 'Broken aqueduct'].map((label, index) => ({ category: 'RUIN' as const, label, x: (index * 11 + 6) % 28, y: (index * 5 + 1) % 20 })),
];

export function OverseerMap() {
  const [ready, setReady] = useState(false);
  const [markers, setMarkers] = useState<Marker[]>([]);
  const [error, setError] = useState<string | null>(null);
  useEffect(() => {
    if (!apiUrl) {
      setMarkers(fallbackMarkers);
      setReady(true);
      return;
    }
    fetch(`${apiUrl}/api/overseer/world/preview`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ seed, widthChunks: 28, heightChunks: 20 }) })
      .then(response => response.ok ? response.json() : Promise.reject(new Error('Preview unavailable.')))
      .then((preview: Preview) => { setMarkers(preview.markers); setReady(true); }).catch(() => { setMarkers(fallbackMarkers); setReady(true); });
  }, []);

  return <main className="overseer-shell">
    <header className="overseer-header">
      <div><p className="eyebrow">Project Draugr / creator tools</p><h1>Overseer Atlas</h1></div>
      <p className="overseer-status">Canonical knowledge · never visible to a Chronicle</p>
    </header>
    <section className="atlas-layout" aria-label="Canonical world preview">
      <aside className="atlas-sidebar">
        <p className="eyebrow">Seed</p><strong>{seed}</strong>
        <p className="atlas-copy">This is a non-persistent ecological placement preview. Approve the geography before World Genesis is allowed to create canonical state.</p>
        <dl>
          <div><dt>Atlas extent</dt><dd>280 × 200 km</dd></div>
          <div><dt>Simulation unit</dt><dd>10 km atlas region</dd></div>
          <div><dt>Human civilization</dt><dd>None</dd></div>
        </dl>
        <p className="eyebrow">Marker key</p>
        <ul className="atlas-legend"><li className="resource">Resource site</li><li className="wildlife">Wildlife range</li><li className="monster">Monster lair</li><li className="ruin">Ancient ruin</li></ul>
      </aside>
      <div className="atlas-map-wrap">
        {error ? <p className="error">{error}</p> : ready ? <div className="atlas-art"><img className="atlas-map" src={atlasArt} alt="Illustrated ancient wilderness atlas, used only as a creator reference" />{markers.map(marker => <span key={`${marker.category}-${marker.label}`} className={`atlas-marker ${marker.category.toLowerCase()}`} style={{ left: `${((marker.x + .5) / 28) * 100}%`, top: `${((marker.y + .5) / 20) * 100}%` }} title={`${marker.category.toLowerCase()}: ${marker.label}`} aria-label={`${marker.category.toLowerCase()}: ${marker.label}`} />)}</div> : <p className="atlas-loading">Generating deterministic geography…</p>}
      </div>
    </section>
  </main>;
}
