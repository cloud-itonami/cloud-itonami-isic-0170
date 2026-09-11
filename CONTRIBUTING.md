# Contributing to cloud-itonami-isic-0170

Thank you for your interest in contributing to the Hunting, Trapping And
Related Service Activities Coordination actor.

## Scope

This repository is a specialization of the cloud-itonami architecture for ISIC
0170 (hunting, trapping and related service activities). Contributions should:

1. Extend or correct the **Governor rules** (wildlife-harvest safety/regulatory constraints)
2. Add **harvest methods** or **jurisdictional requirements** to the facts registry
3. Improve **test coverage** for wildlife-harvest-specific scenarios
4. Clarify **documentation** and ADRs

## Prohibited Changes

Do **not**:

- Add direct firearm/trap-deployment control (discharging a firearm or deploying a trap remains exclusive to the licensed hunter/trapper in the field)
- Add authority to issue or finalize a wildlife harvest license
- Modify the Governor to allow LLM confidence to override safety/regulatory hard holds
- Add JVM-only code (all source must be `.cljc` / portable)
- Change the AGPL-3.0-or-later license

## Process

1. Open an issue describing your proposed change
2. Link to the relevant ADR in the `kotoba-lang/industry` registry repository (or the `com-junkawasaki/root` superproject's `90-docs/adr/`)
3. Submit a pull request against `main`
4. Ensure all tests pass: `kbb -M:test`
5. Run linter: `kbb -M:lint`

## Code Style

- Use `.cljc` for all source (no `.clj` or `.cljs` only)
- Follow Clojure conventions (kebab-case, docstrings on public fns)
- Governor rules must be pure, side-effect-free predicates
- Test all new facts and registry entries

## Questions?

File an issue or reach out to the maintainers.
