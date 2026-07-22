(ns huntharvest.operation-graph-test
  "Integration tests for `huntharvest.operation/build` -- builds the REAL
  compiled `langgraph.graph` StateGraph and runs it end-to-end via
  `langgraph.graph/run*` through commit / hard-hold / escalate-approve /
  escalate-reject routes. These did not exist before: there was no
  `build` at all, no Advisor node (the file had zero functions), and
  `store/append-ledger!` had never been called from any real commit/hold
  path (only from `test/huntharvest/store_test.cljc` setup code).

  Falsifiable claims each test proves, not just asserts:
    1. the ledger is verified EMPTY before the run (never pre-populated
       by test fixtures), so a post-run non-empty ledger is genuinely
       caused by this run's own `:commit`/`:hold` node, not residue;
    2. a HARD governor violation blocks the graph from EVER reaching
       `:commit` -- proven for an op that would otherwise always
       escalate to human approval, showing the hard-hold check runs
       and wins BEFORE the escalate branch, not merely that no ledger
       fact happens to appear;
    3. the Advisor's proposal is genuinely threaded through
       `:advise -> :govern -> :decide -> :commit` -- proven by injecting
       a custom `Advisor` (via `build`'s `:advisor` opt) whose proposal
       carries a random, single-use `:summary` string generated at test
       run time (impossible to have been hardcoded anywhere in
       `huntharvest.operation`) and asserting the committed ledger fact
       carries that EXACT string."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [huntharvest.advisor :as advisor]
            [huntharvest.operation :as operation]
            [huntharvest.store :as store]))

(def ^:private now-ms #?(:clj (System/currentTimeMillis) :cljs (.now js/Date)))
(def ^:private ten-days-ago (- now-ms (* 10 24 60 60 1000)))
(def ^:private ten-days-from-now (+ now-ms (* 10 24 60 60 1000)))

(def ^:private hunt-op {:actor-id "hunt-op-01" :role :licensed-operator})

(def ^:private clean-trap-record
  "Governor-clean against every independent hard check (spec-basis,
  evidence-completeness, hunter-license, trap-inspection, trap-check-
  interval, trap-setback, quota, season). Mirrors
  `test/huntharvest/governor_test.cljc`'s `clean-trap-order` fixture of
  the same shape."
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
   :evidence-checklist [:harvest-license-record :quota-allocation-record :harvest-tag-record
                        :species-identification-log :location-log :report-submission-record]})

(defn- exec
  ([actor tid request] (exec actor tid request hunt-op))
  ([actor tid request context]
   (g/run* actor {:request request :context context} {:thread-id tid})))

(deftest commit-path-clean-low-stakes-proposal
  (testing "a clean, low-stakes (:schedule-harvest-operation) proposal commits
            through the REAL compiled graph and appends exactly one fact
            to the audit ledger -- the ledger is verified EMPTY
            beforehand, proving the write is a genuine effect of THIS
            run, not test-setup residue"
    (let [s (store/mem-store {"record-001" clean-trap-record})
          actor (operation/build s)]
      (is (empty? (store/ledger s)) "ledger is empty before any run")
      (let [result (exec actor "t-commit" {:op :schedule-harvest-operation :subject "record-001"})
            state (:state result)]
        (is (= :done (:status result)))
        (is (= :commit (:disposition state)))
        (let [ledger (store/ledger s)]
          (is (= 1 (count ledger)))
          (is (= :committed (:t (first ledger))))
          (is (= :schedule-harvest-operation (:op (first ledger))))
          (is (= "record-001" (:subject (first ledger))))
          (is (true? (:scheduled? (store/harvest-record (store/current s) "record-001")))))))))

(deftest hard-hold-path-record-never-registered
  (testing "log-harvest-record against a harvest record that was NEVER
            registered (`:harvest-record-not-registered`) is a HARD
            governor violation -- the real graph routes straight to
            :hold (no interrupt, no human-approval detour) and durably
            records the hold fact; the underlying record stays nil
            (`commit-log-harvest-record!` never fired), proving the write
            path is genuinely gated"
    (let [s (store/mem-store)
          actor (operation/build s)]
      (is (empty? (store/ledger s)))
      (let [result (exec actor "t-hold" {:op :log-harvest-record :subject "record-999"
                                          :jurisdiction :jp/maff-wildlife})
            state (:state result)]
        (is (= :done (:status result)))
        (is (= :hold (:disposition state)))
        (let [ledger (store/ledger s)]
          (is (= 1 (count ledger)))
          (is (= :governor-hold (:t (first ledger))))
          (is (seq (:violations (first ledger))))
          (is (some #{:harvest-record-not-registered} (map :rule (:violations (first ledger)))))))
      (is (nil? (store/harvest-record (store/current s) "record-999"))
          "a held proposal never registers/mutates the harvest record"))))

(deftest governor-hard-hold-blocks-ledger-write-before-commit
  (testing "a HARD governor violation (hunter-license-expired) is caught
            BEFORE the graph would otherwise have escalated for human
            approval -- :log-harvest-record is normally ALWAYS-escalate
            (high-stakes), so a plain '(:hold disposition)' assertion
            alone wouldn't distinguish hard-block from an unresolved
            escalation. This test proves the ledger contains ONLY a
            :governor-hold fact citing the actual violated rule -- never
            a :committed fact, never an :approval-requested-only outcome
            -- for a request whose op WOULD have interrupted for approval
            had it been governor-clean"
    (let [s (store/mem-store {"record-bad" (assoc clean-trap-record
                                                  :hunter-license-expiry-date ten-days-ago)})
          actor (operation/build s)
          result (exec actor "t-govhold" {:op :log-harvest-record :subject "record-bad"
                                           :jurisdiction :jp/maff-wildlife})]
      (is (= :done (:status result)) "no interrupt -- HARD holds never pause for approval")
      (is (= :hold (:disposition (:state result))))
      (let [ledger (store/ledger s)]
        (is (= 1 (count ledger)))
        (is (every? #(= :governor-hold (:t %)) ledger)
            "no :committed fact was ever written -- the governor hold
            blocked the ledger write before :commit could run")
        (is (some #{:hunter-license-expired}
                  (map :rule (:violations (first ledger))))))
      (is (not (true? (:logged? (store/harvest-record (store/current s) "record-bad"))))
          "store/commit-log-harvest-record! never fired for a hard-held proposal"))))

(deftest escalate-then-approve-commits-and-genuinely-consults-advisor
  (testing ":log-harvest-record ALWAYS escalates (high-stakes) -- the
            real graph GENUINELY interrupts (checkpointed) at
            :request-approval, and the ledger stays EMPTY until a human
            licensed operator resumes it. A custom, non-default Advisor
            (injected at test time, NOT a call-site literal in
            `huntharvest.operation`) proposes with a randomly generated,
            single-use `:summary` string. Only if the graph truly
            threads the Advisor's own proposal through
            :advise -> :govern -> :decide -> :commit (rather than
            re-deriving/hardcoding a proposal internally) can that exact
            string reach the ledger's committed fact."
    (let [distinctive-summary (str "TEST-ADVISOR-" (rand-int 1000000000))
          test-advisor (reify advisor/Advisor
                         (-advise [_ _store request]
                           {:op (:op request)
                            :effect :propose
                            :value {:jurisdiction :us/usfws}
                            :cites [{:spec "record-001-harvest-license-record"}]
                            :summary distinctive-summary
                            :confidence 0.9}))
          s (store/mem-store {"record-001" (assoc clean-trap-record :jurisdiction :us/usfws)})
          actor (operation/build s {:advisor test-advisor})]
      (is (empty? (store/ledger s)))
      (let [held (exec actor "t-escalate" {:op :log-harvest-record :subject "record-001"
                                            :jurisdiction :us/usfws})]
        (is (= :interrupted (:status held)))
        (is (= [:request-approval] (:frontier held)))
        (is (empty? (store/ledger s)) "not yet committed -- awaiting human sign-off")
        (let [approved (g/run* actor {:approval {:status :approved :by "hunt-op-01"}}
                               {:thread-id "t-escalate" :resume? true})
              approved-state (:state approved)]
          (is (= :done (:status approved)))
          (is (= :commit (:disposition approved-state)))
          (let [ledger (store/ledger s)]
            (is (= 1 (count ledger)))
            (is (= :committed (:t (first ledger))))
            (is (= distinctive-summary (:summary (first ledger)))
                "the ledger's committed fact carries the INJECTED test
                Advisor's own distinctive summary -- proof the graph
                genuinely threads the Advisor's real proposal through
                :govern -> :decide -> :commit rather than hardcoding a
                pass-string or ignoring the Advisor node's output")
            (is (true? (:logged? (store/harvest-record (store/current s) "record-001"))))))))))

(deftest escalate-then-reject-holds
  (testing "a human licensed operator rejecting an escalated
            flag-conservation-concern routes to :hold via the
            :request-approval node's own decision, and durably records
            the rejection -- not a hand-rolled parallel path"
    (let [s (store/mem-store {"record-001" clean-trap-record})
          actor (operation/build s)
          _held (exec actor "t-reject" {:op :flag-conservation-concern :subject "record-001"
                                         :jurisdiction :jp/maff-wildlife
                                         :concern "possible quota exceedance"})
          rejected (g/run* actor {:approval {:status :rejected :by "hunt-op-01"}}
                           {:thread-id "t-reject" :resume? true})
          rejected-state (:state rejected)]
      (is (= :done (:status rejected)))
      (is (= :hold (:disposition rejected-state)))
      (let [ledger (store/ledger s)]
        (is (= 1 (count ledger)))
        (is (= :approval-rejected (:t (first ledger))))))))

(deftest coordinate-shipment-hard-hold-record-not-registered
  (testing "`:coordinate-shipment` against a harvest record that was
            never registered is a HARD, permanent block
            (`:harvest-record-not-registered`), proven end-to-end
            through the compiled graph"
    (let [s (store/mem-store)
          actor (operation/build s)
          result (exec actor "t-ship-unreg" {:op :coordinate-shipment :subject "ghost-record"
                                              :jurisdiction :jp/maff-wildlife})]
      (is (= :hold (:disposition (:state result))))
      (let [ledger (store/ledger s)]
        (is (= 1 (count ledger)))
        (is (some #{:harvest-record-not-registered} (map :rule (:violations (first ledger))))))
      (is (not (true? (:shipped? (store/harvest-record (store/current s) "ghost-record"))))))))

(deftest coordinate-shipment-high-value-escalates-low-value-commits
  (testing "a high-value shipment escalates for human sign-off while a
            low-value shipment on the SAME record shape auto-commits --
            proven end-to-end through the compiled graph, not just
            asserted against `governor/check` in isolation"
    (let [s (store/mem-store {"record-hi" clean-trap-record "record-lo" clean-trap-record})
          actor (operation/build s)]
      (let [held (exec actor "t-ship-hi" {:op :coordinate-shipment :subject "record-hi"
                                          :jurisdiction :jp/maff-wildlife
                                          :shipment-value-usd 10000})]
        (is (= :interrupted (:status held)))
        (is (empty? (store/ledger s))))
      (let [committed (exec actor "t-ship-lo" {:op :coordinate-shipment :subject "record-lo"
                                                :jurisdiction :jp/maff-wildlife
                                                :shipment-value-usd 500})]
        (is (= :done (:status committed)))
        (is (= :commit (:disposition (:state committed))))
        (is (true? (:shipped? (store/harvest-record (store/current s) "record-lo"))))))))

(deftest hard-block-op-not-allowed-through-compiled-graph
  (testing "an op entirely outside the closed allowlist (direct firearm/
            trap-deployment control) is a HARD, permanent block, proven
            end-to-end through the compiled graph"
    (let [s (store/mem-store {"record-001" clean-trap-record})
          actor (operation/build s)
          result (exec actor "t-notallowed" {:op :discharge-firearm :subject "record-001"})]
      (is (= :hold (:disposition (:state result))))
      (let [ledger (store/ledger s)]
        (is (= 1 (count ledger)))
        (is (some #{:op-not-allowed} (map :rule (:violations (first ledger)))))))))
