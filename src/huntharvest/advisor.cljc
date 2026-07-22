(ns huntharvest.advisor
  "HuntHarvestAdvisor -- the contained LLM/decision node. This actor's
  intelligence layer proposes wildlife-harvest operations coordination
  actions (harvest-record logging, harvest-operation scheduling,
  conservation-concern flags, pelt/meat/carcass shipment coordination)
  based on harvest-record state and licensed-operator input. The advisor
  is SEALED into the `:advise` step of `huntharvest.operation/build`'s
  compiled StateGraph; every proposal is routed through the independent
  `huntharvest.governor` before anything commits.

  PRIOR BUG (fixed here, worse than the usual sibling-actor gap): this
  namespace previously contained ONLY a docstring and a single comment
  admitting \"For this blueprint, it's a skeleton\" -- literally ZERO
  functions, no `defprotocol`, no `defrecord`, nothing `:require`d by
  `huntharvest.operation` at all. There was no Advisor node in the actual
  flow, not even a dead/unreferenced one. Now the Advisor is a real
  protocol, has a real (mock, for now) implementation, and is genuinely
  wired as the graph's `:advise` node in `huntharvest.operation/build`.

  The advisor makes proposals but has NO direct authority. Every proposal
  is always censored by `huntharvest.governor/check`:
    1. Closed op-allowlist (`:log-harvest-record` /
       `:schedule-harvest-operation` / `:flag-conservation-concern` /
       `:coordinate-shipment`, all `:effect :propose` only)
    2. Harvest-record registration hard-gate (independently verified in
       the Store, not trusted from the advisor)
    3. Hunter/trapper-license, trap-inspection, trap-check-interval,
       trap-setback, quota, and season hard-gates (`huntharvest.registry`)
    4. Unresolved-conservation-flag hard hold
    5. A PERMANENT, un-overridable block on any proposal whose `:value`
       covertly requests direct firearm/trap-deployment control
       (`:operate-firearm-or-trap-deployment?`) or a wildlife-license-
       issuing-authority decision (`:finalize-license-issuance-decision?`)
       -- this mock NEVER sets either key, by construction
    6. Human licensed-operator sign-off (`:log-harvest-record` -- the one
       real actuation event this actor performs -- and
       `:flag-conservation-concern` ALWAYS escalate, same as a
       `:coordinate-shipment` above the value threshold)

  Current implementation is a mock advisor for testing. Production should
  use langchain/Claude or similar LLM backend (same seam point as
  `pastaops.advisor`, cloud-itonami-isic-1074 / `forestrysupport.advisor`,
  cloud-itonami-isic-0240). Domain rationale text stays generic/
  structural -- this advisor never invents species-specific or
  jurisdiction-specific regulatory claims it cannot independently cite;
  citation basis is always a record-scoped spec reference, matching what
  `huntharvest.governor/spec-basis-violations` actually checks (a
  non-empty `:cites`, never an invented jurisdiction requirement).")

;; Protocol for swappable advisor implementations
(defprotocol Advisor
  (-advise [advisor store request]
    "Given store and request, return a proposal map with :op, :effect,
    :value, :cites, :summary, :confidence. `:cites` are jurisdiction/
    spec citation maps (e.g. {:spec \"harvest-license-record\"}) -- see
    `huntharvest.governor`'s spec-basis check. `:value` never sets
    `:operate-firearm-or-trap-deployment?` or
    `:finalize-license-issuance-decision?` true -- those are permanent,
    un-overridable governor blocks regardless of advisor confidence."))

;; Mock advisor for testing
(defrecord MockAdvisor []
  Advisor
  (-advise [_advisor _store request]
    (let [{:keys [op subject jurisdiction]} request]
      (case op
        :log-harvest-record
        {:op :log-harvest-record
         :effect :propose
         :value {:jurisdiction jurisdiction}
         :cites [{:spec (str subject "-harvest-license-record")}]
         :summary "Harvest record logging proposed from licensed operator field submission"
         :confidence 0.9}

        :schedule-harvest-operation
        {:op :schedule-harvest-operation
         :effect :propose
         :value {:harvest-method (:harvest-method request)
                 :requested-date (:requested-date request)}
         :cites [{:spec "licensed-operator-scheduling-request"}]
         :summary "Harvest-operation (hunting/trapping) scheduling proposed per licensed operator request"
         :confidence 0.85}

        :flag-conservation-concern
        {:op :flag-conservation-concern
         :effect :propose
         :value {:jurisdiction jurisdiction
                 :concern (:concern request "unspecified concern")}
         :cites [{:spec "field-observation-report"}]
         :summary "Conservation concern (quota exceedance/endangered species/humane-treatment) flagged for human review"
         :confidence 0.85}

        :coordinate-shipment
        {:op :coordinate-shipment
         :effect :propose
         :value {:jurisdiction jurisdiction
                 :shipment-value-usd (:shipment-value-usd request 0)}
         :cites [{:spec (str subject "-shipment-manifest")}]
         :summary "Pelt/meat/carcass shipment coordination proposed"
         :confidence 0.9}

        ;; fallback -- unrecognized op. The Governor's closed allowlist
        ;; independently rejects this regardless of what the advisor says.
        {:op op
         :effect :propose
         :value {}
         :cites []
         :summary "Operation not recognized"
         :confidence 0.0}))))

(defn mock-advisor []
  (MockAdvisor.))

(defn trace
  "Audit trail entry for an advisor proposal. Recorded whenever a proposal
  is generated, regardless of outcome."
  [request proposal]
  {:t :advisor-proposal
   :op (:op request)
   :subject (:subject request)
   :proposal-summary (:summary proposal)
   :confidence (:confidence proposal)})
