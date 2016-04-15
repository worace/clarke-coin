(ns block-chain.validations
  (:require [schema.core :as s]
            [block-chain.chain :as c]
            [block-chain.transactions :as t]
            [block-chain.key-serialization :as ks]
            [block-chain.wallet :as w]
            [block-chain.utils :refer :all]
            [clojure.pprint :refer [pprint]]
            [block-chain.schemas :refer :all]))

(defn new-transaction? [txn _ txn-pool]
  (not (contains? txn-pool txn)))

(defn sufficient-inputs? [txn chain txn-pool]
  (let [sources (compact (map (partial c/source-output chain)
                              (:inputs txn)))
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

(defn inputs-properly-sourced? [txn chain _]
  (let [inputs-sources (match-inputs-to-sources txn chain)]
    (and (every? identity (keys inputs-sources))
         (every? identity (vals inputs-sources))
         (= (count (vals inputs-sources))
            (count (into #{} (vals inputs-sources)))))))

(defn inputs-unspent? [txn chain _]
  (let [sources (vals (match-inputs-to-sources txn chain))]
    (every? (partial c/unspent? chain) sources)))

(def txn-validations
  {new-transaction? "Transaction rejected because it already exists in this node's pending txn pool."
   txn-structure-valid? "Transaction structure invalid."
   ;; inputs-properly-sourced? "One or more transaction inputs is not properly sourced, OR multiple inputs attempt to source the same output."
   ;; inputs-unspent? "Outputs referenced by one or more txn inputs has already been spent."
   sufficient-inputs? "Transaction lacks sufficient inputs to cover its outputs"
   ;; signatures-valid? "One or more transactions signatures is invalid."
   })

(defn validate-transaction [txn chain txn-pool]
  (mapcat (fn [[validation message]]
            (if-not (validation txn chain txn-pool)
              [message]
              []))
          txn-validations))
