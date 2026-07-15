(ns huntharvest.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [huntharvest.governor :as governor]))

(def ^:private now-ms #?(:clj (System/currentTimeMillis) :cljs (.now js/Date)))
(def ^:private ten-days-ago (- now-ms (* 10 24 60 60 1000)))
(def ^:private two-hundred-days-ago (- now-ms (* 200 24 60 60 1000)))
(def ^:private ten-days-from-now (+ now-ms (* 10 24 60 60 1000)))

(def ^:private evidence-checklist
  [:harvest-license-record :quota-allocation-record :harvest-tag-record
   :species-identification-log :location-log :report-submission-record])

(def ^:private clean-trap-order
  "Baseline clean harvest record for a trap-based harvest method
  (leg-hold fur-bearer) -- has license/inspection/trap-check/setback/
  quota/season specs."
  {:harvest-method :trap/leg-hold-fur-bearer
   :jurisdiction :jp/maff-wildlife
   :species "marten"
   :hunter-license-expiry-date ten-days-from-now
   :trap-last-inspection-date ten-days-ago
   :hours-since-last-trap-check 10
   :trap-setback-actual-m 40.0
   :harvest-count-this-season 2
   :quota-limit 5
   :harvest-epoch-ms 1500
   :season-open-epoch-ms 1000
   :season-close-epoch-ms 2000
   :evidence-checklist evidence-checklist})

(def ^:private clean-hunt-order
  "Baseline clean harvest record for a direct-take harvest method
  (big-game rifle) -- has NO trap-specific safety-window fields at all."
  {:harvest-method :hunt/big-game-rifle
   :jurisdiction :jp/maff-wildlife
   :species "deer"
   :hunter-license-expiry-date ten-days-from-now
   :harvest-count-this-season 1
   :quota-limit 3
   :harvest-epoch-ms 1500
   :season-open-epoch-ms 1000
   :season-close-epoch-ms 2000
   :evidence-checklist evidence-checklist})

;; ──────────────────────── Registration Invariant ──────────────────────

(deftest harvest-record-not-registered-violation-test
  (testing "log-harvest-record against a never-registered harvest record is a hard block"
    (let [store {:harvest-records {}}
          req {:op :log-harvest-record :subject "record-999"}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff-wildlife} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :harvest-record-not-registered) (:violations result)))))

  (testing "schedule-harvest-operation against a never-registered harvest record is a hard block"
    (let [store {:harvest-records {}}
          req {:op :schedule-harvest-operation :subject "record-999"}
          prop {:cites [] :value {} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :harvest-record-not-registered) (:violations result)))))

  (testing "flag-conservation-concern against a never-registered harvest record is a hard block"
    (let [store {:harvest-records {}}
          req {:op :flag-conservation-concern :subject "record-999"}
          prop {:cites [{:spec "Field-Report"}] :value {:jurisdiction :jp/maff-wildlife} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :harvest-record-not-registered) (:violations result)))))

  (testing "coordinate-shipment against a never-registered harvest record is a hard block"
    (let [store {:harvest-records {}}
          req {:op :coordinate-shipment :subject "record-999"}
          prop {:cites [{:spec "Shipper-Manifest"}] :value {:jurisdiction :jp/maff-wildlife :shipment-value-usd 100} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :harvest-record-not-registered) (:violations result))))))

;; ──────────────────────── Spec Basis ──────────────────────

(deftest spec-basis-violation-test
  (testing "proposal with no jurisdiction citation is a hard violation"
    (let [store {:harvest-records {"record-001" clean-trap-order}}
          req {:op :log-harvest-record :subject "record-001"}
          prop {:cites [] :value {:jurisdiction nil}}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :no-spec-basis) (:violations result)))))

  (testing "proposal with proper citation passes spec basis check"
    (let [store {:harvest-records {"record-001" clean-trap-order}}
          req {:op :log-harvest-record :subject "record-001"}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff-wildlife} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (false? (:hard? result))))))

;; ──────────────────────── Hunter License Violations ──────────────────────

(deftest hunter-license-expired-violation-test
  (testing "expired hunter license triggers hard violation"
    (let [store {:harvest-records {"record-001" (assoc clean-trap-order
                                                         :hunter-license-expiry-date two-hundred-days-ago)}}
          req {:op :log-harvest-record :subject "record-001"}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff-wildlife} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :hunter-license-expired) (:violations result)))))

  (testing "current hunter license passes"
    (let [store {:harvest-records {"record-001" clean-trap-order}}
          req {:op :log-harvest-record :subject "record-001"}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff-wildlife} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (false? (:hard? result)))))

  (testing "direct-take harvest method still requires a current license"
    (let [store {:harvest-records {"record-002" (assoc clean-hunt-order
                                                         :hunter-license-expiry-date two-hundred-days-ago)}}
          req {:op :log-harvest-record :subject "record-002"}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff-wildlife} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :hunter-license-expired) (:violations result))))))

;; ──────────────────────── Trap Inspection Violations ──────────────────────

(deftest trap-inspection-overdue-violation-test
  (testing "overdue trap inspection triggers hard violation"
    (let [store {:harvest-records {"record-001" (assoc clean-trap-order
                                                         :trap-last-inspection-date two-hundred-days-ago)}}
          req {:op :log-harvest-record :subject "record-001"}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff-wildlife} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :trap-inspection-overdue) (:violations result)))))

  (testing "direct-take harvest method never triggers this rule"
    (let [store {:harvest-records {"record-002" clean-hunt-order}}
          req {:op :log-harvest-record :subject "record-002"}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff-wildlife} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (not (some #(= (:rule %) :trap-inspection-overdue) (:violations result)))))))

;; ──────────────────────── Trap-Check Interval Violations ──────────────────────

(deftest trap-check-interval-violated-violation-test
  (testing "hours-since-last-check above the mandated interval triggers hard violation"
    (let [store {:harvest-records {"record-001" (assoc clean-trap-order :hours-since-last-trap-check 40)}}
          req {:op :log-harvest-record :subject "record-001"}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff-wildlife} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :trap-check-interval-violated) (:violations result)))))

  (testing "direct-take harvest method never triggers this rule"
    (let [store {:harvest-records {"record-002" clean-hunt-order}}
          req {:op :log-harvest-record :subject "record-002"}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff-wildlife} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (not (some #(= (:rule %) :trap-check-interval-violated) (:violations result)))))))

;; ──────────────────────── Trap Setback Violations ──────────────────────

(deftest trap-setback-violated-violation-test
  (testing "setback narrower than minimum triggers hard violation"
    (let [store {:harvest-records {"record-001" (assoc clean-trap-order :trap-setback-actual-m 5.0)}}
          req {:op :log-harvest-record :subject "record-001"}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff-wildlife} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :trap-setback-violated) (:violations result)))))

  (testing "setback at or above minimum passes"
    (let [store {:harvest-records {"record-002" clean-trap-order}}
          req {:op :log-harvest-record :subject "record-002"}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff-wildlife} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (false? (:hard? result)))))

  (testing "direct-take harvest method never triggers this rule"
    (let [store {:harvest-records {"record-003" clean-hunt-order}}
          req {:op :log-harvest-record :subject "record-003"}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff-wildlife} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (not (some #(= (:rule %) :trap-setback-violated) (:violations result)))))))

;; ──────────────────────── Quota Violations ──────────────────────

(deftest quota-exceeded-violation-test
  (testing "harvest count at quota triggers hard violation"
    (let [store {:harvest-records {"record-001" (assoc clean-trap-order :harvest-count-this-season 5)}}
          req {:op :log-harvest-record :subject "record-001"}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff-wildlife} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :quota-exceeded) (:violations result)))))

  (testing "harvest count above quota triggers hard violation"
    (let [store {:harvest-records {"record-002" (assoc clean-hunt-order :harvest-count-this-season 4)}}
          req {:op :log-harvest-record :subject "record-002"}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff-wildlife} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :quota-exceeded) (:violations result)))))

  (testing "harvest count strictly below quota passes"
    (let [store {:harvest-records {"record-003" clean-trap-order}}
          req {:op :log-harvest-record :subject "record-003"}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff-wildlife} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (false? (:hard? result))))))

;; ──────────────────────── Open-Season Violations ──────────────────────

(deftest season-closed-violation-test
  (testing "harvest before season open triggers hard violation"
    (let [store {:harvest-records {"record-001" (assoc clean-trap-order :harvest-epoch-ms 500)}}
          req {:op :log-harvest-record :subject "record-001"}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff-wildlife} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :season-closed) (:violations result)))))

  (testing "harvest after season close triggers hard violation"
    (let [store {:harvest-records {"record-002" (assoc clean-hunt-order :harvest-epoch-ms 2500)}}
          req {:op :log-harvest-record :subject "record-002"}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff-wildlife} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :season-closed) (:violations result)))))

  (testing "harvest within season window passes"
    (let [store {:harvest-records {"record-003" clean-trap-order}}
          req {:op :log-harvest-record :subject "record-003"}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff-wildlife} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (false? (:hard? result))))))

;; ──────────────────────── Evidence Completeness ──────────────────────

(deftest evidence-incomplete-violation-test
  (testing "incomplete evidence checklist triggers hard violation"
    (let [store {:harvest-records {"record-001" (assoc clean-trap-order
                                                         :evidence-checklist [:harvest-license-record])}}
          req {:op :log-harvest-record :subject "record-001"}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff-wildlife} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :evidence-incomplete) (:violations result))))))

;; ──────────────────── Firearm/Trap-Deployment / Licensing-Authority Block ────────────────────

(deftest firearm-or-trap-deployment-or-licensing-authority-blocked-violation-test
  (testing "a proposal covertly requesting direct firearm/trap-deployment control is a hard, permanent block"
    (let [store {:harvest-records {"record-001" clean-trap-order}}
          req {:op :schedule-harvest-operation :subject "record-001"}
          prop {:cites [] :value {:operate-firearm-or-trap-deployment? true} :confidence 0.99}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :firearm-or-trap-deployment-or-licensing-authority-blocked) (:violations result)))))

  (testing "a proposal covertly requesting a wildlife-license-issuing-authority decision is a hard, permanent block"
    (let [store {:harvest-records {"record-001" clean-trap-order}}
          req {:op :log-harvest-record :subject "record-001"}
          prop {:cites [{:spec "ISO-12345"}]
                :value {:jurisdiction :jp/maff-wildlife :finalize-license-issuance-decision? true}
                :confidence 0.99}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :firearm-or-trap-deployment-or-licensing-authority-blocked) (:violations result))))))

;; ──────────────────────── Conservation-Concern Flag Violations ──────────────────────

(deftest conservation-flag-unresolved-violation-test
  (testing "an unresolved conservation flag triggers hard violation"
    (let [store {:harvest-records {"record-001" (assoc clean-trap-order
                                                         :conservation-concern-raised? true
                                                         :conservation-concern-resolved? false)}}
          req {:op :log-harvest-record :subject "record-001"}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff-wildlife} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :conservation-flag-unresolved) (:violations result)))))

  (testing "a resolved conservation flag does not trigger this rule"
    (let [store {:harvest-records {"record-002" (assoc clean-trap-order
                                                         :conservation-concern-raised? true
                                                         :conservation-concern-resolved? true)}}
          req {:op :log-harvest-record :subject "record-002"}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff-wildlife} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (not (some #(= (:rule %) :conservation-flag-unresolved) (:violations result)))))))

;; ──────────────────────── Escalation (Low Confidence) ──────────────────────

(deftest low-confidence-escalation-test
  (testing "low confidence proposal escalates even when hard checks pass"
    (let [store {:harvest-records {"record-001" clean-trap-order}}
          req {:op :schedule-harvest-operation :subject "record-001"}
          prop {:cites [] :value {} :confidence 0.5}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (false? (:ok? result)))
      (is (true? (:escalate? result)))
      (is (false? (:hard? result))))))

;; ──────────────────────── High Stakes Escalation ──────────────────────

(deftest high-stakes-escalation-test
  (testing "log-harvest-record escalates even when all checks pass"
    (let [store {:harvest-records {"record-001" clean-trap-order}}
          req {:op :log-harvest-record :subject "record-001"}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff-wildlife} :confidence 0.95}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (false? (:ok? result)))
      (is (true? (:escalate? result)))
      (is (false? (:hard? result))))))

;; ──────────────────────── Conservation Concern Always Escalates ──────────────────────

(deftest conservation-concern-always-escalates-test
  (testing "a clean flag-conservation-concern proposal is never auto-ok"
    (let [store {:harvest-records {"record-001" clean-trap-order}}
          req {:op :flag-conservation-concern :subject "record-001"}
          prop {:cites [{:spec "Field-Report"}] :value {:jurisdiction :jp/maff-wildlife} :confidence 0.99}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (false? (:ok? result)))
      (is (true? (:escalate? result)))
      (is (false? (:hard? result))))))

;; ──────────────────────── High-Value Shipment Escalation ──────────────────────

(deftest high-value-shipment-escalation-test
  (testing "a shipment above the value threshold escalates even when clean"
    (let [store {:harvest-records {"record-001" clean-trap-order}}
          req {:op :coordinate-shipment :subject "record-001"}
          prop {:cites [{:spec "Shipper-Manifest"}]
                :value {:jurisdiction :jp/maff-wildlife :shipment-value-usd 10000}
                :confidence 0.9}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (false? (:ok? result)))
      (is (true? (:escalate? result)))
      (is (false? (:hard? result)))))

  (testing "a shipment at or below the value threshold does not force escalation"
    (let [store {:harvest-records {"record-002" clean-trap-order}}
          req {:op :coordinate-shipment :subject "record-002"}
          prop {:cites [{:spec "Shipper-Manifest"}]
                :value {:jurisdiction :jp/maff-wildlife :shipment-value-usd 1000}
                :confidence 0.9}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:ok? result))))))

;; ──────────────────────── Already Logged Violation ──────────────────────

(deftest already-logged-violation-test
  (testing "harvest record already logged triggers hard violation"
    (let [store {:harvest-records {"record-001"
                                    {:harvest-method :trap/leg-hold-fur-bearer
                                     :logged? true}}}
          req {:op :log-harvest-record :subject "record-001"}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff-wildlife} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :already-logged) (:violations result))))))

;; ──────────────────────── Op-Not-Allowed Violation ──────────────────────

(deftest op-not-allowed-violation-test
  (testing "an out-of-allowlist op (e.g. direct firearm/trap-deployment control) is a hard, permanent block"
    (let [store {:harvest-records {"record-001" clean-trap-order}}
          req {:op :discharge-firearm :subject "record-001"}
          prop {:cites [{:spec "Firearm-Manual"}] :value {:jurisdiction :jp/maff-wildlife} :confidence 0.99}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :op-not-allowed) (:violations result))))))

;; ──────────────────────── Effect-Not-Propose Violation ──────────────────────

(deftest effect-not-propose-violation-test
  (testing "a proposal asserting a non-:propose effect is a hard, permanent block"
    (let [store {:harvest-records {"record-001" clean-trap-order}}
          req {:op :schedule-harvest-operation :subject "record-001"}
          prop {:effect :commit :cites [] :value {} :confidence 0.9}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :effect-not-propose) (:violations result))))))
