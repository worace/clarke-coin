(ns block-chain.validations
  (:require [schema.core :as s]
            [block-chain.chain :as c]
            [block-chain.schemas :refer :all]))

(defn new-transaction? [txn _ txn-pool]
  (contains? txn-pool txn))

(defn sufficient-inputs? [txn chain txn-pool]
  (let [sources (map (partial c/source-output chain)
                     (:inputs txn))
        outputs (:outputs txn)]
    (>= (reduce + (map :amount sources))
        (reduce + (map :amount outputs)))))

(defn txn-structure-valid? [txn _ _]
  (try
    (s/validate Transaction txn)
    (catch Exception e
        false)))

(def txn-validations
  {new-transaction? "Transaction rejected because it already exists in this node's pending txn pool."})

(defn validate-transaction [txn chain txn-pool]
  (mapcat (fn [[validation message]]
            (if (validation txn chain txn-pool)
              [message]
              []))
          txn-validations))
