(ns huntharvest.governor
  "Wildlife-Harvest Operations Governor -- the independent compliance
  layer that earns the HuntHarvestAdvisor the right to commit. The LLM
  has no notion of:
    - Whether a harvest license/quota record has been independently
      verified/registered in the store at all, for ANY of this actor's
      four proposal ops
    - Whether the licensed hunter/trapper's harvest license is current
    - Whether the trap/cage equipment's inspection/tag registration is
      current (only meaningful for trap-based harvest methods)
    - Whether a set trap has gone unchecked longer than its mandated
      trap-check interval (only meaningful for trap-based harvest
      methods)
    - Whether the actual trap setback distance to the nearest trail/
      road/dwelling met the harvest method's minimum (only meaningful
      for trap-based harvest methods)
    - Whether logging this harvest would meet or exceed the
      independently-verified species quota allocation
    - Whether the harvest occurred within the species' legally open
      season
    - Whether a proposal is covertly requesting direct firearm/trap-
      deployment control or a wildlife-license-issuing-authority
      decision
    - Whether a previously-raised conservation concern has been resolved
    - Whether a harvest record has already been logged (double-commit)

  This MUST be a separate system able to *reject* a proposal and fall
  back to HOLD.

  Unlike direct firearm/trap deployment (NEVER done by this actor) or
  issuing/finalizing a wildlife harvest license (NEVER done by this
  actor -- both are HARD, permanent governor blocks, never overridable
  by human approval), the Governor operates on harvest-record metadata:
  licensed-hunter/trapper identity, harvest-method parameters, and
  safety/compliance flags. This is wildlife-harvest OPERATIONS
  COORDINATION for commercial/licensed hunting, trapping and related
  service activities -- NOT direct firearm/trap-deployment authority and
  NOT a wildlife-license-issuing authority.

  CRITICAL: `:flag-conservation-concern` ALWAYS escalates to human
  sign-off at every phase, regardless of advisor confidence -- a quota-
  exceedance/endangered-species/humane-treatment concern is never
  auto-resolved by advisor confidence alone.

  Hard violations (always HOLD, no override):
    1. Operation outside the closed allowlist (`:op-not-allowed`) --
       includes any proposal that would amount to direct firearm/trap-
       deployment control
    2. Proposal asserting an `:effect` other than `:propose`
       (`:effect-not-propose`)
    3. Harvest record not independently verified/registered in the
       store -- applies to ALL FOUR allowed ops
       (`:harvest-record-not-registered`)
    4. No jurisdiction citation (`:no-spec-basis`)
    5. Evidence checklist incomplete (`:evidence-incomplete`)
    6. Hunter/trapper harvest license expired
       (`:hunter-license-expired`)
    7. Trap/cage equipment inspection overdue
       (`:trap-inspection-overdue` -- only when the harvest method is
       trap-based)
    8. Trap-check interval violated (`:trap-check-interval-violated` --
       only when the harvest method is trap-based)
    9. Trap setback distance violated (`:trap-setback-violated` -- only
       when the harvest method is trap-based)
   10. Species quota exceeded (`:quota-exceeded`)
   11. Harvest attempted outside the legal open season
       (`:season-closed`)
   12. Proposal covertly requests direct firearm/trap-deployment control
       or a wildlife-license-issuing-authority decision
       (`:firearm-or-trap-deployment-or-licensing-authority-blocked` --
       a HARD, PERMANENT block, never overridable by human approval,
       evaluated against every op as defense-in-depth even though those
       actions are already outside the closed allowlist)
   13. Unresolved conservation concern (`:conservation-flag-unresolved`)
   14. Harvest record already logged (`:already-logged`, double-commit
       guard)

  Soft gates (always escalate for human):
    - Low confidence
    - `:log-harvest-record` -- the one real actuation event this actor
      performs (logging an actual wildlife take into permanent official
      records)
    - `:flag-conservation-concern` -- never auto-resolved by confidence
      alone
    - `:coordinate-shipment` above the value threshold
      (`shipment-value-threshold-usd`)

  This design mirrors `cropsupport.governor` (ISIC 0161, support
  activities for crop production) in overall shape but specializes on
  wildlife-harvest safety concerns -- hunter/trapper licensing, trap
  equipment inspection, trap-check interval (humane treatment), trap
  setback (public safety), species quota (conservation), and open-season
  windows -- for commercial/licensed hunting, trapping and related
  service activities, never firearm/trap deployment or license-issuing
  authority."
  (:require [huntharvest.facts :as facts]
            [huntharvest.registry :as registry]
            [huntharvest.store :as store]))

(def confidence-floor 0.6)

(def shipment-value-threshold-usd
  "Pelt/meat/carcass shipments (`:coordinate-shipment`) at or below this
  declared value may auto-commit when the Governor is otherwise clean;
  shipments above this threshold always require human sign-off,
  regardless of advisor confidence."
  5000)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Logging a completed harvest record (`:log-harvest-record`) is the one
  real-world actuation event this actor performs -- it commits an actual
  wildlife take (and, transitively, the safety/compliance facts that
  accompanied it) into the permanent official record."
  #{:log-harvest-record})

(def always-escalate-ops
  "Operations that always require human sign-off, even when the
  Governor's hard checks are clean and confidence is high: the high-
  stakes actuation event (`high-stakes`) plus
  `:flag-conservation-concern` -- a conservation concern (quota
  exceedance, endangered species, humane-treatment) is never
  auto-resolved by advisor confidence alone, it always needs a human
  look."
  (conj high-stakes :flag-conservation-concern))

(def allowed-ops
  "Closed allowlist of proposal operations this actor may ever make. Any
  proposal for an operation outside this set -- most importantly direct
  firearm/trap-deployment control -- is a hard, permanent block: this
  actor coordinates wildlife-harvest operations, it does not deploy
  firearms or traps itself, and it does not issue wildlife harvest
  licenses."
  #{:log-harvest-record :schedule-harvest-operation
    :flag-conservation-concern :coordinate-shipment})

;; ────────────────────────── Checks ──────────────────────────

(defn- op-not-allowed-violations
  "HARD, permanent block: any proposal outside the closed operation
  allowlist (e.g. direct firearm/trap-deployment control) is refused
  unconditionally -- this actor has no authority to make such a proposal
  at all, let alone commit it."
  [{:keys [op]} _proposal]
  (when-not (contains? allowed-ops op)
    [{:rule :op-not-allowed
      :detail (str op " はこのactorの許可された提案種別 (log-harvest-record/"
                  "schedule-harvest-operation/flag-conservation-concern/coordinate-shipment) "
                  "に含まれない -- 銃器・罠の直接展開はこのactorに無い")}]))

(defn- effect-not-propose-violations
  "HARD invariant: this actor's proposals are always `:effect :propose` --
  it never claims direct write/actuation authority for itself. A proposal
  asserting any other effect is refused unconditionally."
  [_request proposal]
  (when-let [effect (:effect proposal)]
    (when (not= effect :propose)
      [{:rule :effect-not-propose
        :detail (str "この actor の提案は :propose 以外の :effect を持てない (got " effect ")")}])))

(defn- harvest-record-not-registered-violations
  "HARD invariant: a harvest-license/quota record must be independently
  verified/registered in the store BEFORE any of this actor's four
  proposal ops can be made against it -- coordinating a harvest this
  actor never checked in is out of scope. Evaluated across ALL FOUR
  allowed ops, not just one."
  [{:keys [op subject]} st]
  (when (contains? allowed-ops op)
    (when-not (store/harvest-record-registered? st subject)
      [{:rule :harvest-record-not-registered
        :detail (str subject " は独立に検証・登録されたharvest-record記録が無い -- いかなる提案も進められない")}])))

(defn- spec-basis-violations
  "A proposal with no jurisdiction citation is a HARD violation -- never
  invent a jurisdiction's wildlife-harvest safety/regulatory
  requirements."
  [{:keys [op]} proposal]
  (when (contains?
         #{:log-harvest-record :coordinate-shipment :flag-conservation-concern}
         op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :jurisdiction) (nil? (:jurisdiction value))))
        [{:rule :no-spec-basis
          :detail "公式仕様の引用が無い提案は法域要件として扱えない"}]))))

(defn- evidence-incomplete-violations
  "For `:log-harvest-record`, verify the harvest record's evidence
  checklist is complete per jurisdiction requirements."
  [{:keys [op subject]} st]
  (when (= op :log-harvest-record)
    (let [r (store/harvest-record st subject)]
      (when-not (and r
                     (facts/required-evidence-satisfied?
                      (:jurisdiction r)
                      (:evidence-checklist r)))
        [{:rule :evidence-incomplete
          :detail "法域の必要書類(harvest-license-record/quota-allocation-record/harvest-tag-record等)が充足していない状態での提案"}]))))

(defn- hunter-license-expired-violations
  "For `:log-harvest-record`, INDEPENDENTLY verify the licensed hunter/
  trapper's harvest license has not expired via
  `registry/hunter-license-expired?`. Applies to EVERY harvest method --
  every jurisdiction in this actor's scope requires a license/tag for
  both hunting and trapping."
  [{:keys [op subject]} st now-ms]
  (when (= op :log-harvest-record)
    (let [r (store/harvest-record st subject)]
      (when (and r (:hunter-license-expiry-date r)
                 (registry/hunter-license-expired? (:hunter-license-expiry-date r) now-ms))
        [{:rule :hunter-license-expired
          :detail (str subject " の狩猟者・わな猟免許(hunter license)が失効している -- 記録提案は進められない")}]))))

(defn- trap-inspection-overdue-violations
  "For `:log-harvest-record`, INDEPENDENTLY verify the trap/cage
  equipment's inspection/tag registration is current via
  `registry/trap-inspection-overdue?`. Only evaluated when the harvest
  method actually requires trap equipment (trap-based methods)."
  [{:keys [op subject]} st now-ms]
  (when (= op :log-harvest-record)
    (let [r (store/harvest-record st subject)
          hm (when r (facts/harvest-method-by-id (:harvest-method r)))]
      (when (and r hm (:trap-based? hm) (:trap-last-inspection-date r)
                 (registry/trap-inspection-overdue? (:trap-last-inspection-date r) now-ms))
        [{:rule :trap-inspection-overdue
          :detail (str subject " の罠・檻の点検・タグ登録が期限切れ -- 記録提案は進められない")}]))))

(defn- trap-check-interval-violated-violations
  "For `:log-harvest-record`, INDEPENDENTLY verify that the hours elapsed
  since the trap was last checked did not exceed the harvest method's
  mandated trap-check interval via
  `registry/trap-check-interval-violated?`. Only evaluated when the
  harvest method actually has a trap-check-interval spec (trap-based
  methods) -- direct-take methods have nothing to check here, never a
  fabricated target."
  [{:keys [op subject]} st]
  (when (= op :log-harvest-record)
    (let [r (store/harvest-record st subject)
          hm (when r (facts/harvest-method-by-id (:harvest-method r)))]
      (when (and r hm (:trap-check-interval-hours hm) (:hours-since-last-trap-check r)
                 (registry/trap-check-interval-violated?
                  (:hours-since-last-trap-check r)
                  (:trap-check-interval-hours hm)))
        [{:rule :trap-check-interval-violated
          :detail (str subject " の未点検経過時間(" (:hours-since-last-trap-check r)
                      "時間)が罠点検義務間隔基準を超過 -- 記録提案は進められない")}]))))

(defn- trap-setback-violated-violations
  "For `:log-harvest-record`, INDEPENDENTLY verify that the actual
  distance maintained from the nearest trail/road/dwelling met the
  harvest method's minimum trap setback via
  `registry/trap-setback-violated?`. Only evaluated when the harvest
  method actually has a trap-setback minimum."
  [{:keys [op subject]} st]
  (when (= op :log-harvest-record)
    (let [r (store/harvest-record st subject)
          hm (when r (facts/harvest-method-by-id (:harvest-method r)))]
      (when (and r hm (:min-trap-setback-m hm) (:trap-setback-actual-m r)
                 (registry/trap-setback-violated?
                  (:trap-setback-actual-m r)
                  (:min-trap-setback-m hm)))
        [{:rule :trap-setback-violated
          :detail (str subject " の罠設置距離(" (:trap-setback-actual-m r)
                      "m)が最小離隔基準を下回る -- 記録提案は進められない")}]))))

(defn- quota-exceeded-violations
  "For `:log-harvest-record`, INDEPENDENTLY verify that logging this
  harvest would not meet or exceed the independently-verified species
  quota allocation via `registry/quota-exceeded?`. Applies to EVERY
  harvest method -- quota allocation is universal in this actor's scope,
  never method-specific."
  [{:keys [op subject]} st]
  (when (= op :log-harvest-record)
    (let [r (store/harvest-record st subject)]
      (when (and r (:harvest-count-this-season r) (:quota-limit r)
                 (registry/quota-exceeded? (:harvest-count-this-season r) (:quota-limit r)))
        [{:rule :quota-exceeded
          :detail (str subject " は種別捕獲割当(quota)に既に到達・超過している -- 記録提案は進められない")}]))))

(defn- season-closed-violations
  "For `:log-harvest-record`, INDEPENDENTLY verify that the harvest
  occurred within the species' legally open season via
  `registry/season-closed?`. Applies to EVERY harvest method."
  [{:keys [op subject]} st]
  (when (= op :log-harvest-record)
    (let [r (store/harvest-record st subject)]
      (when (and r (:harvest-epoch-ms r) (:season-open-epoch-ms r) (:season-close-epoch-ms r)
                 (registry/season-closed?
                  (:harvest-epoch-ms r)
                  (:season-open-epoch-ms r)
                  (:season-close-epoch-ms r)))
        [{:rule :season-closed
          :detail (str subject " の捕獲日時が法定猟期の範囲外 -- 記録提案は進められない")}]))))

(defn- firearm-or-trap-deployment-or-licensing-authority-blocked-violations
  "HARD, PERMANENT block, defense-in-depth: any proposal whose `:value`
  covertly requests direct firearm/trap-deployment control
  (`:operate-firearm-or-trap-deployment?` true) or a wildlife-license-
  issuing-authority decision (`:finalize-license-issuance-decision?`
  true) is refused unconditionally, regardless of which op it is
  nominally filed under and regardless of advisor confidence. Never
  overridable by human approval -- this is a scope boundary, not a risk
  judgment."
  [_request proposal]
  (let [value (:value proposal)]
    (when (or (true? (:operate-firearm-or-trap-deployment? value))
              (true? (:finalize-license-issuance-decision? value)))
      [{:rule :firearm-or-trap-deployment-or-licensing-authority-blocked
        :detail "銃器・罠の直接展開または狩猟免許発給の最終決定はこのactorの範囲外 -- 恒久的にブロックされる"}])))

(defn- conservation-flag-unresolved-violations
  "An unresolved conservation flag is a HARD, un-overridable hold.
  Conservation concerns (quota exceedance, endangered species, humane-
  treatment) raised during the harvest must be resolved before the
  harvest record can be logged. Evaluated UNCONDITIONALLY at
  `:log-harvest-record`."
  [{:keys [op subject]} st]
  (when (= op :log-harvest-record)
    (let [r (store/harvest-record st subject)]
      (when (and (true? (:conservation-concern-raised? r))
                 (not (true? (:conservation-concern-resolved? r))))
        [{:rule :conservation-flag-unresolved
          :detail (str subject " は未解決の保全懸念フラグがある -- 記録提案は進められない")}]))))

(defn- already-logged-violations
  "For `:log-harvest-record`, refuse to log the SAME harvest record
  twice, off a dedicated `:logged?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :log-harvest-record)
    (when (store/harvest-record-already-logged? st subject)
      [{:rule :already-logged
        :detail (str subject " は既に記録済み")}])))

(defn- now-epoch-ms
  "Current time in epoch milliseconds, portable across Clojure/
  ClojureScript. Isolated to this single call site so the rest of the
  namespace (and all of `huntharvest.registry`) stays free of host-clock
  calls."
  []
  #?(:clj (System/currentTimeMillis)
     :cljs (js/Date.now)))

(defn- high-value-shipment?
  "Soft-gate helper: a `:coordinate-shipment` proposal escalates to a human
  unless its `:shipment-value-usd` can be established to be BELOW `shipment-value-threshold-usd`.

  Note the direction. This gate used to read `:shipment-value-usd` out of the
  advisor's OWN proposal and escalate only when that number exceeded
  the threshold, which made the gate's only input the very number it
  existed to doubt:

    - an advisor understating bought itself an auto-commit wherever
      `:coordinate-shipment` was `:auto`-eligible -- no human saw it;
    - `(some-> amount (> threshold))` returned nil when the field was
      ABSENT, so omitting `:shipment-value-usd` skipped the gate entirely.

  There is no filed catalog in this actor's store to recompute the
  figure from -- the advisor states it directly -- so a self-declared
  value cannot be verified. An unverifiable number is worthless as a
  DE-escalation signal: it may raise the alarm, it must never silence
  it. The gate now escalates whenever the value is absent, non-numeric,
  or above the threshold, and stands down only for one that is present,
  numeric and below it."
  [{:keys [op]} proposal]
  (when (= op :coordinate-shipment)
    (let [v (get-in proposal [:value :shipment-value-usd])]
      (or (not (number? v))
          (> v shipment-value-threshold-usd)))))

(defn check
  "Censors a HuntHarvestAdvisor proposal against the Governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}.

  Stakes (high-stakes actuation vs. always-escalate vs. high-value
  shipment) are read off the REQUEST's `:op` (and, for shipment value,
  the proposal's own declared value) -- not off the advisor's self-
  reported stake -- since the operation being proposed is what determines
  whether a human must sign off."
  [request _context proposal st]
  (let [now-ms (now-epoch-ms)
        hard (into []
                   (concat (op-not-allowed-violations request proposal)
                           (effect-not-propose-violations request proposal)
                           (harvest-record-not-registered-violations request st)
                           (spec-basis-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (hunter-license-expired-violations request st now-ms)
                           (trap-inspection-overdue-violations request st now-ms)
                           (trap-check-interval-violated-violations request st)
                           (trap-setback-violated-violations request st)
                           (quota-exceeded-violations request st)
                           (season-closed-violations request st)
                           (firearm-or-trap-deployment-or-licensing-authority-blocked-violations request proposal)
                           (conservation-flag-unresolved-violations request st)
                           (already-logged-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        actuation? (boolean (high-stakes (:op request)))
        escalate-op? (or (boolean (always-escalate-ops (:op request)))
                          (boolean (high-value-shipment? request proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not escalate-op?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? escalate-op?))
     :high-stakes? actuation?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
