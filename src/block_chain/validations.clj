(ns block-chain.validations
  (:require [schema.core :as s]
            [block-chain.chain :as c]
            [block-chain.transactions :as t]
            [block-chain.key-serialization :as ks]
            [block-chain.wallet :as w]
            [block-chain.schemas :refer :all]))

(defn new-transaction? [txn _ txn-pool]
  (contains? txn-pool txn))

(defn sufficient-inputs? [txn chain txn-pool]
  (let [sources (map (partial c/source-output chain)
                     (:inputs txn))
        outputs (:outputs txn)]
    (>= (reduce + (map :amount sources))
        (reduce + (map :amount outputs)))))

(defn match-inputs-to-sources [txn chain]
  (into {}
        (map (fn [i] [i (c/source-output chain i)]) (:inputs txn))))

(defn verify-input-signature [input source txn]
  (let [source-key (ks/der-string->pub-key (:address source))]
    (w/verify (:signature input)
              (t/txn-signable txn)
              source-key)))

(defn signatures-valid? [txn chain _]
  (let [inputs-sources (match-inputs-to-sources txn chain)]
    (every? (fn [[input source]]
              (verify-input-signature input source txn))
            inputs-sources)))

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
