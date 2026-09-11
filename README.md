# cloud-itonami-isic-0170: Hunting, Trapping And Related Service Activities Coordination Actor

**ISIC Rev. 5 0170** — Hunting, trapping and related service activities

A distributed actor for autonomous, compliant coordination of commercial/licensed wildlife-harvest operations: harvest-request intake → wildlife-population/habitat-condition survey → harvest/trapping-scheduling advice → licensed hunting/trapping field operation → harvest-record logging → compliance audit. Sealed LLM advisor (`huntharvest.advisor/Advisor`); independent Governor enforcement (`huntharvest.governor`); append-only audit ledger. Composed by `huntharvest.operation/build` into a REAL compiled `langgraph-clj` `StateGraph` (`intake -> advise -> govern -> decide -+-> commit / request-approval -> commit / hold`), with `interrupt-before #{:request-approval}` + checkpoint-based resume for genuine human-in-the-loop escalation. **Not firearm/trap-deployment authority. Not a wildlife-license-issuing authority.** Discharging a firearm or deploying a trap remains exclusive to the licensed hunter/trapper in the field, and this actor never issues or finalizes a wildlife harvest license.

Fixed a prior gap worse than most sibling cloud-itonami-isic-* actors before their own fixes: `deps.edn` declared `io.github.kotoba-lang/langgraph` ONLY under the unused `:dev` alias's `:override-deps`, with an EMPTY base `:deps` map, so `langgraph.graph` was never actually resolvable on any real build/run/test path; `huntharvest.advisor` was a docstring-only namespace admitting "it's a skeleton" — literally ZERO functions, no protocol, no implementation, never `:require`d anywhere; and `huntharvest.store/append-fact` was called ONLY from test setup, never from any real commit/hold path. `huntharvest.advisor/Advisor` is now a genuine `defprotocol` + `MockAdvisor`, sealed into the graph's `:advise` node, and `huntharvest.store` now also exposes a `Store` protocol (`MemStore`, `ledger`/`append-ledger!`) alongside its original pure value helpers (unchanged, still used directly by the Governor and by the original `run-operation` pure driver, preserved for its own 9 existing tests).

## Scope

This actor coordinates **commercial/licensed wildlife-harvest operations** — hunting, trapping and related service activities performed for meat, pelts, or population-control purposes (including game-ranching support services) — under an independently-verified harvest license and species quota allocation:

- Harvest-record logging (species/quantity/location harvest data, safety/compliance parameters)
- Licensed-trapper/hunter deployment scheduling proposals
- Conservation-concern escalation (quota exceedance/endangered species/humane treatment, always escalates)
- Pelt/meat/carcass shipment coordination

**Out of scope:**
- Direct firearm/trap-deployment control (exclusive to the licensed hunter/trapper in the field)
- Issuing or finalizing a wildlife harvest license (permanent, un-overridable governor block — that is the licensing authority's role, never this actor's)
- Setting or allocating species quotas (that is the licensing/quota authority's role; this actor only coordinates against an already-verified allocation)
- Regulatory interpretation (proposals cite jurisdiction specifications; the Governor enforces only published requirements)

## Design

### Governor (Independent Compliance Layer)

The Governor is the separation-of-powers enforcement. It never trusts the advisor's confidence for anything safety- or compliance-relevant, and it always wins over the advisor.

- **Hard HOLD** (un-overridable):
  - Operation outside the closed allowlist (`:op-not-allowed`) — includes any proposal that would amount to direct firearm/trap-deployment control
  - Proposal asserting an `:effect` other than `:propose` (`:effect-not-propose`)
  - Harvest record not independently verified/registered in the store — applies to ALL FOUR allowed ops (`:harvest-record-not-registered`)
  - No jurisdiction citation (`:no-spec-basis`)
  - Evidence checklist incomplete (`:evidence-incomplete`)
  - Hunter/trapper harvest license expired (`:hunter-license-expired`)
  - Trap/cage equipment inspection/tag registration overdue (`:trap-inspection-overdue`) — only for trap-based harvest methods
  - Trap-check interval violated (`:trap-check-interval-violated`) — only for trap-based harvest methods (humane-treatment requirement)
  - Trap setback narrower than the harvest method's minimum (`:trap-setback-violated`) — only for trap-based harvest methods
  - Species quota exceeded (`:quota-exceeded`)
  - Harvest attempted outside the legal open season (`:season-closed`)
  - Proposal covertly requests direct firearm/trap-deployment control or a wildlife-license-issuing-authority decision (`:firearm-or-trap-deployment-or-licensing-authority-blocked`) — a HARD, PERMANENT block, defense-in-depth against every op
  - Unresolved conservation concern (`:conservation-flag-unresolved`)
  - Harvest record already logged (`:already-logged`, double-commit guard)
- **Escalate** (human sign-off always required):
  - `:log-harvest-record` — the one real actuation event this actor performs (logging an actual wildlife take into permanent official records), always requires human sign-off even when the Governor is otherwise clean
  - `:flag-conservation-concern` — a conservation concern (quota exceedance, endangered species, humane treatment) is never auto-resolved by advisor confidence alone
  - `:coordinate-shipment` above `governor/shipment-value-threshold-usd` (5000 USD)
  - Low advisor confidence (below `governor/confidence-floor`, 0.6)
- **Commit** (advisor proposal approved; Governor clean; not a mandatory-escalation op):
  - Routine, low-stakes proposals only — in this actor's current allowlist that is effectively `:schedule-harvest-operation` when clean, or `:coordinate-shipment` at or below the value threshold

### Operations (Proposals)

Closed allowlist — the advisor may **only** ever propose these four operation types, all `:effect :propose`:

- **`:log-harvest-record`** — Log species/quantity/location harvest data, plus safety/compliance parameters, into harvest records (always requires human sign-off)
- **`:schedule-harvest-operation`** — Propose licensed-trapper/hunter deployment scheduling (routine, low risk)
- **`:flag-conservation-concern`** — Surface a conservation concern (e.g. quota exceedance, endangered species, humane-treatment issue); always escalates
- **`:coordinate-shipment`** — Propose pelt/meat/carcass shipment coordination (escalates above the value threshold)

Any proposal for an operation outside this allowlist — most importantly anything that would amount to direct firearm/trap-deployment control — is refused unconditionally by the Governor (`:op-not-allowed`), regardless of advisor confidence. Any proposal that covertly requests direct firearm/trap-deployment control or a wildlife-license-issuing-authority decision, even nested inside an otherwise-allowed op, is likewise refused unconditionally (`:firearm-or-trap-deployment-or-licensing-authority-blocked`).

## Testing

```bash
# Run full test suite (langgraph resolved via local sibling checkout)
kbb -M:dev:test

# Check code quality
kbb -M:lint

# Run demo simulation -- drives the compiled StateGraph end-to-end
kbb -M:dev:run
```

## Standalone Use

This repo is **forkable outside the workspace**. If cloning standalone (not in the kotoba-lang monorepo), override `:local/root` paths in `deps.edn`:

```clojure
{:deps {io.github.kotoba-lang/langchain {:git/url "https://github.com/kotoba-lang/langchain" :git/tag "v0.1.0"}
        io.github.kotoba-lang/langgraph {:git/url "https://github.com/kotoba-lang/langgraph" :git/tag "v0.1.0"}}}
```

## License

AGPL-3.0-or-later. Forking/contribution welcome; see `CONTRIBUTING.md`.

## Security

Report security issues to the issue tracker or private disclosure; see `SECURITY.md`.

---

Part of **cloud-itonami**: autonomous actor fleet for regulated industries. See [github.com/cloud-itonami](https://github.com/cloud-itonami).
