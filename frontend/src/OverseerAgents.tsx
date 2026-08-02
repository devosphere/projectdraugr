import { useEffect, useState } from 'react';

// The Overseer's window onto the two operator-facing persistent-state agents. Read-only for the
// Auditor; propose-only for the Architect (a proposal is text to review, never an applied change).
// Every panel renders on deterministic data, so it works with the AI layer off — the AI only fills
// in the prose summary and the drafted migration once a key is configured.

type AuditSummary = { consistent: boolean; violations: string[]; summary: string | null };
type BacklogEntry = { sampleText: string; classifiedCategory: string | null; nearProcessKey: string | null; hitCount: number; gapKind: string };
type ArchitectProposal = { gapKind: string; sampleText: string; draftMigration: string };

export function OverseerAgents({ apiUrl }: { apiUrl?: string }) {
  const [audit, setAudit] = useState<AuditSummary | null>(null);
  const [auditError, setAuditError] = useState<string | null>(null);
  const [backlog, setBacklog] = useState<BacklogEntry[] | null>(null);
  const [proposal, setProposal] = useState<ArchitectProposal | null>(null);
  const [proposing, setProposing] = useState(false);
  const [architectNote, setArchitectNote] = useState<string | null>(null);

  useEffect(() => {
    if (!apiUrl) return;
    fetch(`${apiUrl}/api/audit/summary`).then(r => (r.ok ? r.json() : Promise.reject(new Error())))
      .then((s: AuditSummary) => setAudit(s))
      .catch(() => setAuditError('The audit surface is unreachable. Is the local backend running?'));
    fetch(`${apiUrl}/api/architect/backlog?limit=12`).then(r => (r.ok ? r.json() : Promise.reject(new Error())))
      .then((b: BacklogEntry[]) => setBacklog(b))
      .catch(() => setBacklog([]));
  }, [apiUrl]);

  async function proposeTop() {
    if (!apiUrl || proposing) return;
    setProposing(true); setProposal(null); setArchitectNote(null);
    try {
      const res = await fetch(`${apiUrl}/api/architect/propose-top`, { method: 'POST' });
      if (res.status === 204) {
        setArchitectNote(backlog && backlog.length
          ? 'The Architect AI is off — enable draugr.ai to draft a migration for this gap.'
          : 'No unresolved gaps to propose against yet.');
        return;
      }
      if (!res.ok) throw new Error();
      setProposal(await res.json() as ArchitectProposal);
    } catch {
      setArchitectNote('The Architect could not draft a proposal.');
    } finally {
      setProposing(false);
    }
  }

  if (!apiUrl) {
    return <section className="agents-panel">
      <h2>Persistent-state agents</h2>
      <p className="atlas-copy">Connect to a local backend to inspect the Auditor and Architect.</p>
    </section>;
  }

  return <section className="agents-grid" aria-label="Persistent-state agents">
    <article className="agent-card">
      <p className="eyebrow">Persistent State Auditor · read-only</p>
      {auditError ? <p className="error">{auditError}</p> : audit ? <>
        <div className={`audit-badge ${audit.consistent ? 'ok' : 'bad'}`}>
          {audit.consistent ? 'World consistent' : `${audit.violations.length} violation${audit.violations.length === 1 ? '' : 's'}`}
        </div>
        {audit.violations.length > 0 && <ul className="agent-list">{audit.violations.map((v, i) => <li key={i}>{v}</li>)}</ul>}
        <p className="agent-summary">{audit.summary ?? 'AI summary off — enable draugr.ai to render the report in prose.'}</p>
      </> : <p className="atlas-loading">Auditing…</p>}
    </article>

    <article className="agent-card">
      <p className="eyebrow">Persistent State Architect · authoring-time</p>
      <p className="atlas-copy">Gaps players walked into. The Architect drafts a migration for review — it never applies one.</p>
      {backlog === null ? <p className="atlas-loading">Reading backlog…</p>
        : backlog.length === 0 ? <p className="agent-summary">No unresolved routing gaps recorded.</p>
        : <ul className="agent-list backlog">{backlog.map((e, i) => <li key={i}>
            <span className={`gap-tag ${e.gapKind.toLowerCase()}`}>{e.gapKind}</span>
            <span className="gap-text">{e.sampleText}</span>
            <span className="gap-hits">×{e.hitCount}</span>
          </li>)}</ul>}
      <button className="agent-action" onClick={proposeTop} disabled={proposing}>
        {proposing ? 'Drafting…' : 'Draft a proposal for the worst gap'}
      </button>
      {architectNote && <p className="agent-note">{architectNote}</p>}
      {proposal && <div className="agent-proposal">
        <p className="eyebrow">{proposal.gapKind} · “{proposal.sampleText}”</p>
        <pre>{proposal.draftMigration}</pre>
        <p className="agent-note">Proposal only — review, then add as a real V*.sql migration through the gate.</p>
      </div>}
    </article>
  </section>;
}
