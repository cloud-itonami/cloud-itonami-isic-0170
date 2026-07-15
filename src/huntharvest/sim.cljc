(ns huntharvest.sim
  "Simulation driver for testing the wildlife-harvest operations actor
  end-to-end.

  For CLI: clojure -M:dev:run

  Example flow:
    1. Start with empty store
    2. Register a harvest record in :intake phase
    3. Propose a harvest record -> :record transition with safety
       parameters (hunter license / trap inspection / trap-check
       interval / trap setback / quota / season window)
    4. Governor validates parameters against facts
    5. If valid, audit fact is committed
    6. CLI prints audit trail")

(defn -main [& _args]
  (println "HuntHarvest simulation: not yet implemented.")
  (println "TODO: integrate langgraph-clj StateGraph when available."))
