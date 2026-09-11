(ns huntharvest.registry
  "Pure validation functions for wildlife-harvest safety/regulatory
  parameters. These are called by the Governor to independently verify
  physical/regulatory constraints -- the advisor's confidence is NOT
  sufficient to override these checks.

  All functions here are pure arithmetic/boolean predicates with no
  host-clock or I/O calls, so this namespace stays trivially portable
  across Clojure/ClojureScript. Callers that need the current time (see
  `hunter-license-expired?` / `trap-inspection-overdue?`) obtain it
  themselves via a `:clj`/`:cljs` reader-conditional at the call site
  (see `huntharvest.governor`).")

(defn hunter-license-expired?
  "Independently verify that the hunter/trapper harvest license has
  expired as of `now-epoch-ms`. An expired license means the person who
  performed the harvest was not legally authorized to do so -- a genuine
  regulatory hazard distinct from any equipment, quota, or season
  concern."
  [expiry-epoch-ms now-epoch-ms]
  (< expiry-epoch-ms now-epoch-ms))

(defn trap-inspection-overdue?
  "Independently verify that the trap/cage equipment was NOT inspected
  and tag-registered within the last 180 days.
  `last-inspection-epoch-ms` and `now-epoch-ms` are both epoch
  milliseconds -- callers obtain `now` via a `:clj`/`:cljs`
  reader-conditional, keeping this namespace free of any host-clock
  call. An out-of-registration trap risks both illegal deployment and
  unaccountable, unchecked suffering."
  [last-inspection-epoch-ms now-epoch-ms]
  (> (- now-epoch-ms last-inspection-epoch-ms)
     (* 180 24 60 60 1000)))

(defn trap-check-interval-violated?
  "Independently verify that the number of hours since the trap was last
  checked EXCEEDS the harvest method's mandated trap-check interval.
  Leaving a set trap unchecked too long is a genuine humane-treatment
  hazard -- prolonged, unattended animal suffering."
  [hours-since-last-check interval-hours]
  (> hours-since-last-check interval-hours))

(defn trap-setback-violated?
  "Independently verify that the actual distance maintained from the
  nearest trail/road/dwelling is narrower than the harvest method's
  minimum trap-setback distance. A setback that is too narrow risks
  accidental capture of pets, livestock, or humans."
  [actual-m min-m]
  (< actual-m min-m))

(defn quota-exceeded?
  "Independently verify that logging this harvest would meet or exceed
  the independently-verified species quota allocation. Harvesting past
  quota is a genuine conservation hazard -- population-level harm distinct
  from any single-animal welfare concern."
  [harvest-count-this-season quota-limit]
  (>= harvest-count-this-season quota-limit))

(defn season-closed?
  "Independently verify that `harvest-epoch-ms` falls OUTSIDE
  [`season-open-epoch-ms`, `season-close-epoch-ms`]. Harvesting outside
  the open season is illegal in every jurisdiction in this actor's
  scope, regardless of harvest method."
  [harvest-epoch-ms season-open-epoch-ms season-close-epoch-ms]
  (or (< harvest-epoch-ms season-open-epoch-ms)
      (> harvest-epoch-ms season-close-epoch-ms)))
