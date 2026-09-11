(ns huntharvest.facts
  "Reference facts for licensed hunting/trapping-service operations:
  harvest-method safety windows (trap-check interval, minimum trap
  setback distance), jurisdiction evidence-checklist requirements. This
  namespace contains pure lookup functions for wildlife-harvest safety/
  compliance checks -- the Governor calls these to independently validate
  proposals; the advisor's confidence is never sufficient on its own.

  A hunting/trapping-service contractor (ISIC Rev.5 0170) performs
  COMMERCIAL/LICENSED wildlife harvesting -- for meat, pelts, or
  population-control purposes -- and related service activities (e.g.
  game-ranching support services) on behalf of a licensing/quota
  authority or client, under an independently-verified harvest license
  and species quota allocation. This actor NEVER deploys a firearm or
  trap itself and NEVER issues or finalizes a wildlife harvest license --
  both are hard, permanent governor blocks (see `huntharvest.governor`).

  Harvest methods split into two safety shapes:
    - Trap-based methods (leg-hold trapping, live/cage trapping) carry a
      genuine trap-check interval (maximum hours a set trap may go
      unchecked -- a humane-treatment requirement) and a minimum trap
      setback distance (from trails/roads/dwellings, a public-safety
      requirement). `trap-check-interval-hours`/`min-trap-setback-m` are
      nil for direct-take (non-trap) methods -- the Governor's
      corresponding checks are skipped entirely for those methods rather
      than fabricating a target.
    - Direct-take hunting methods (rifle, shotgun) have no trap to check
      or set back, so both fields are nil.

  Harvest-license currency, trap-equipment inspection currency, species
  quota, and open-season windows all apply to EVERY harvest method
  (unlike the two trap-specific fields above) since every jurisdiction in
  this actor's scope requires a license/tag and a quota allocation for
  both hunting and trapping."
  (:require [clojure.set :as set]))

(def harvest-methods
  "Valid licensed harvest-method categories and their safety windows.
  `trap-check-interval-hours`/`min-trap-setback-m` are nil for
  direct-take (non-trap) methods -- the Governor's corresponding checks
  are skipped entirely for those methods rather than fabricating a
  target."
  {:trap/leg-hold-fur-bearer
   {:id :trap/leg-hold-fur-bearer
    :name "毛皮獣用レッグホールドトラップ設置猟"
    :trap-based? true
    :trap-check-interval-hours 24
    :min-trap-setback-m 30.0}

   :trap/live-cage-predator-control
   {:id :trap/live-cage-predator-control
    :name "有害鳥獣対策用ライブトラップ(捕獲檻)設置"
    :trap-based? true
    :trap-check-interval-hours 24
    :min-trap-setback-m 15.0}

   :hunt/big-game-rifle
   {:id :hunt/big-game-rifle
    :name "大型獣ライフル猟"
    :trap-based? false
    :trap-check-interval-hours nil
    :min-trap-setback-m nil}

   :hunt/small-game-shotgun
   {:id :hunt/small-game-shotgun
    :name "小型獣散弾銃猟"
    :trap-based? false
    :trap-check-interval-hours nil
    :min-trap-setback-m nil}})

(defn harvest-method-by-id [id]
  (get harvest-methods id))

(def jurisdictions
  "Wildlife-harvest jurisdictions and their evidence-checklist
  requirements."
  {:jp/maff-wildlife
   {:id :jp/maff-wildlife
    :name "日本 (鳥獣の保護及び管理並びに狩猟の適正化に関する法律・環境省/農林水産省)"
    :required-evidence
    [:harvest-license-record
     :quota-allocation-record
     :harvest-tag-record
     :species-identification-log
     :location-log
     :report-submission-record]}

   :us/usfws
   {:id :us/usfws
    :name "United States (Migratory Bird Treaty Act / State Wildlife Agency Harvest Reporting)"
    :required-evidence
    [:harvest-license-record
     :quota-allocation-record
     :harvest-tag-record
     :species-identification-log
     :location-log
     :report-submission-record]}

   :eu/habitats-directive
   {:id :eu/habitats-directive
    :name "European Union (Habitats Directive 92/43/EEC / Birds Directive 2009/147/EC)"
    :required-evidence
    [:harvest-license-record
     :quota-allocation-record
     :harvest-tag-record
     :species-identification-log
     :location-log
     :report-submission-record]}})

(defn jurisdiction-by-id [id]
  (get jurisdictions id))

(defn required-evidence-satisfied?
  "Verify that every item in the jurisdiction's `:required-evidence` list
  is present in `evidence`. `jurisdiction` may be a resolved jurisdiction
  map (as returned by `jurisdiction-by-id`) or a raw jurisdiction id --
  both call conventions are in use (tests pass a resolved map; the
  Governor passes the raw id straight off harvest-record metadata)."
  [jurisdiction evidence]
  (let [j (if (map? jurisdiction) jurisdiction (jurisdiction-by-id jurisdiction))]
    (if-not j
      false
      (set/subset? (set (:required-evidence j)) (set evidence)))))

(defn hunter-license-current?
  "Positive-sense convenience predicate: is the hunter/trapper harvest
  license valid (not yet expired) as of `now-epoch-ms`? Applies to EVERY
  harvest method -- every jurisdiction in this actor's scope requires a
  license/tag for both hunting and trapping."
  [expiry-epoch-ms now-epoch-ms]
  (boolean
   (and (some? expiry-epoch-ms)
        (>= expiry-epoch-ms now-epoch-ms))))

(defn trap-inspection-current?
  "Positive-sense convenience predicate: was the trap/cage equipment
  inspected and tag-registered within the safety interval (180 days) of
  `now-epoch-ms`? Returns false when the harvest method has no trap
  equipment at all (direct-take method -- nothing to inspect)."
  [last-inspection-epoch-ms now-epoch-ms harvest-method]
  (boolean
   (and (some? harvest-method)
        (true? (:trap-based? harvest-method))
        (some? last-inspection-epoch-ms)
        (<= (- now-epoch-ms last-inspection-epoch-ms)
            (* 180 24 60 60 1000)))))

(defn trap-check-interval-satisfied?
  "Positive-sense convenience predicate: does `hours-since-last-check`
  stay at or below the harvest method's mandated trap-check interval
  (humane-treatment requirement)? Returns false when the harvest method
  has no trap-check-interval spec at all (direct-take method -- nothing
  to satisfy)."
  [hours-since-last-check harvest-method]
  (boolean
   (and (some? harvest-method)
        (some? (:trap-check-interval-hours harvest-method))
        (some? hours-since-last-check)
        (<= hours-since-last-check (:trap-check-interval-hours harvest-method)))))

(defn trap-setback-in-range?
  "Positive-sense convenience predicate: does `actual-m` meet or exceed
  the harvest method's minimum trap setback distance (from trails/roads/
  dwellings)? Returns false when the harvest method has no trap-setback
  minimum at all."
  [actual-m harvest-method]
  (boolean
   (and (some? harvest-method)
        (some? (:min-trap-setback-m harvest-method))
        (some? actual-m)
        (>= actual-m (:min-trap-setback-m harvest-method)))))

(defn quota-satisfied?
  "Positive-sense convenience predicate: is `harvest-count-this-season`
  still strictly below the independently-verified `quota-limit`? Applies
  to EVERY harvest method -- species quota allocation is universal in
  this actor's scope, never method-specific."
  [harvest-count-this-season quota-limit]
  (boolean
   (and (some? harvest-count-this-season)
        (some? quota-limit)
        (< harvest-count-this-season quota-limit))))

(defn in-open-season?
  "Positive-sense convenience predicate: does `harvest-epoch-ms` fall
  within [`season-open-epoch-ms`, `season-close-epoch-ms`] inclusive?
  Harvesting outside the open season is illegal in every jurisdiction in
  this actor's scope, regardless of harvest method."
  [harvest-epoch-ms season-open-epoch-ms season-close-epoch-ms]
  (boolean
   (and (some? harvest-epoch-ms)
        (some? season-open-epoch-ms)
        (some? season-close-epoch-ms)
        (>= harvest-epoch-ms season-open-epoch-ms)
        (<= harvest-epoch-ms season-close-epoch-ms))))
