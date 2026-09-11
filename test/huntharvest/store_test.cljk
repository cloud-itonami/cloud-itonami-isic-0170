(ns huntharvest.store-test
  (:require [clojure.test :refer [deftest is testing]]
            [huntharvest.store :as store]))

;; ──────────────────────── Harvest-Record Retrieval ──────────────────────

(deftest harvest-record-test
  (testing "retrieve an existing harvest record"
    (let [record-data {:harvest-method :trap/leg-hold-fur-bearer :species "marten"}
          st {:harvest-records {"record-001" record-data}}
          result (store/harvest-record st "record-001")]
      (is (= result record-data))))

  (testing "nonexistent harvest record returns nil"
    (let [st {:harvest-records {}}
          result (store/harvest-record st "nonexistent")]
      (is (nil? result)))))

(deftest harvest-record-registered-test
  (testing "registered harvest record returns true"
    (let [st {:harvest-records {"record-001" {:species "marten"}}}
          result (store/harvest-record-registered? st "record-001")]
      (is (true? result))))

  (testing "unregistered harvest record returns false"
    (let [st {:harvest-records {}}
          result (store/harvest-record-registered? st "record-999")]
      (is (false? result)))))

;; ──────────────────────── Harvest-Record Status Checks ──────────────────────

(deftest harvest-record-already-logged-test
  (testing "logged harvest record is detected"
    (let [st {:harvest-records {"record-001" {:logged? true}}}
          result (store/harvest-record-already-logged? st "record-001")]
      (is (true? result))))

  (testing "unlogged harvest record returns false"
    (let [st {:harvest-records {"record-001" {:logged? false}}}
          result (store/harvest-record-already-logged? st "record-001")]
      (is (false? result))))

  (testing "nonexistent harvest record returns false"
    (let [st {:harvest-records {}}
          result (store/harvest-record-already-logged? st "record-001")]
      (is (false? result)))))

;; ──────────────────────── Harvest-Record Logging ──────────────────────

(deftest log-harvest-record-test
  (testing "logging a harvest record marks it as logged"
    (let [st {:harvest-records {}}
          record-data {:harvest-method :trap/leg-hold-fur-bearer}
          result (store/log-harvest-record st "record-001" record-data)]
      (is (true? (get-in result [:harvest-records "record-001" :logged?])))))

  (testing "logging preserves harvest-record data"
    (let [st {:harvest-records {}}
          record-data {:harvest-method :trap/leg-hold-fur-bearer :species "marten"}
          result (store/log-harvest-record st "record-001" record-data)]
      (is (= (:harvest-method (get-in result [:harvest-records "record-001"])) :trap/leg-hold-fur-bearer))
      (is (= (:species (get-in result [:harvest-records "record-001"])) "marten")))))

;; ──────────────────────── Harvest-Record Scheduling ──────────────────────

(deftest mark-scheduled-test
  (testing "marking a harvest record marks it as scheduled"
    (let [st {:harvest-records {"record-001" {:species "marten"}}}
          result (store/mark-scheduled st "record-001")]
      (is (true? (get-in result [:harvest-records "record-001" :scheduled?]))))))

;; ──────────────────────── Harvest-Record Shipment ──────────────────────

(deftest mark-shipped-test
  (testing "marking a harvest record marks it as shipped"
    (let [st {:harvest-records {"record-001" {:species "marten"}}}
          result (store/mark-shipped st "record-001")]
      (is (true? (get-in result [:harvest-records "record-001" :shipped?]))))))

;; ──────────────────────── Audit Trail ──────────────────────

(deftest audit-trail-test
  (testing "audit trail is initially empty"
    (let [st {:facts []}
          result (store/audit-trail st)]
      (is (empty? result))))

  (testing "appended facts appear in audit trail"
    (let [st {:facts []}
          fact1 {:t :test-fact :detail "test 1"}
          fact2 {:t :test-fact :detail "test 2"}
          st' (store/append-fact st fact1)
          st'' (store/append-fact st' fact2)
          result (store/audit-trail st'')]
      (is (= (count result) 2))
      (is (= (first result) fact1))
      (is (= (second result) fact2)))))

(deftest append-fact-test
  (testing "appending a fact increases ledger length"
    (let [st {:facts []}
          fact {:t :governor-hold :op :log-harvest-record}
          result (store/append-fact st fact)]
      (is (= (count (:facts result)) 1))
      (is (= (first (:facts result)) fact)))))
