(ns huntharvest.store
  "Store abstraction for licensed wildlife-harvest records. Current
  implementation operates on plain data (`{:harvest-records
  {harvest-record-id record-map} :facts [...]}`); production should
  migrate this seam to Datomic/kotoba-server (the same seam point all
  cloud-itonami actors use) while keeping the same pure-function surface.

  A harvest record is the minimal unit of work: one commercial/licensed
  hunting or trapping engagement performed under an independently
  verified harvest license and species quota allocation (never issued or
  finalized by this actor -- that is the defining shape of ISIC 0170,
  hunting, trapping and related service activities: wildlife-harvest
  OPERATIONS COORDINATION, not license-issuing authority and not direct
  firearm/trap-deployment authority). Representative harvest-record keys:
    - :harvest-method keyword harvest-method id (see
      `huntharvest.facts/harvest-methods`)
    - :jurisdiction keyword jurisdiction id (see
      `huntharvest.facts/jurisdictions`)
    - :species the harvested species identifier
    - :evidence-checklist evidence items present for the harvest record
    - :hunter-license-expiry-date epoch-ms of the licensed hunter/
      trapper's harvest license expiry
    - :trap-last-inspection-date epoch-ms of last trap/cage equipment
      inspection/tag registration (nil for direct-take harvest methods)
    - :hours-since-last-trap-check hours elapsed since the trap was last
      checked (nil for direct-take harvest methods)
    - :trap-setback-actual-m actual distance maintained from the nearest
      trail/road/dwelling (nil for direct-take harvest methods)
    - :harvest-count-this-season count of animals harvested this season
      under this license/quota allocation, BEFORE this record
    - :quota-limit the independently-verified species quota allocation
    - :harvest-epoch-ms epoch-ms the harvest occurred
    - :season-open-epoch-ms / :season-close-epoch-ms the species'
      open-season window
    - :conservation-concern-raised? / :conservation-concern-resolved?
      open quota-exceedance/endangered-species/humane-treatment concern
      flag
    - :logged? true once a `:log-harvest-record` proposal commits
    - :scheduled? true once a `:schedule-harvest-operation` proposal
      commits
    - :shipped? true once a `:coordinate-shipment` proposal commits

  The ledger (`:facts`) is a separate append-only vector of audit facts,
  kept alongside `:harvest-records` in the same store value.")

(defn harvest-record
  "Retrieve a harvest record by id, or nil if it does not exist / is not
  yet registered."
  [st harvest-record-id]
  (get-in st [:harvest-records harvest-record-id]))

(defn harvest-record-registered?
  "True only if the harvest record exists in the store -- registration is
  the HARD invariant that must be independently verified before ANY of
  this actor's four proposal ops can be made against it."
  [st harvest-record-id]
  (some? (harvest-record st harvest-record-id)))

(defn harvest-record-already-logged?
  "True only if the harvest record exists and has already been marked
  logged."
  [st harvest-record-id]
  (true? (:logged? (harvest-record st harvest-record-id))))

(defn log-harvest-record
  "Register/update `record-data` under `harvest-record-id` and mark it
  logged (one-way flag). Used once a `:log-harvest-record` proposal
  commits."
  [st harvest-record-id record-data]
  (assoc-in st [:harvest-records harvest-record-id] (assoc record-data :logged? true)))

(defn mark-scheduled
  "Mark an existing harvest record as scheduled (one-way flag). Used once
  a `:schedule-harvest-operation` proposal commits."
  [st harvest-record-id]
  (assoc-in st [:harvest-records harvest-record-id :scheduled?] true))

(defn mark-shipped
  "Mark an existing harvest record as shipped (one-way flag). Used once a
  `:coordinate-shipment` proposal commits."
  [st harvest-record-id]
  (assoc-in st [:harvest-records harvest-record-id :shipped?] true))

(defn audit-trail
  "Return the append-only audit ledger (empty vector if none yet)."
  [st]
  (get st :facts []))

(defn append-fact
  "Append `fact` to the store's audit ledger."
  [st fact]
  (update st :facts (fnil conj []) fact))
