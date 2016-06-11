(ns block-chain.transaction-validations
  (:require [schema.core :as s]
            [clojure.set :refer [intersection]]
            [block-chain.transactions :as t]
            [block-chain.queries :as q]
            [block-chain.key-serialization :as ks]
            [block-chain.wallet :as w]
            [block-chain.utils :refer :all]
            [block-chain.schemas :refer :all]))

(defn new-transaction? [db txn]
  (not (contains? (q/transaction-pool db) txn)))

(defn sufficient-inputs? [db txn]
  (let [sources (compact (map (partial q/source-output db)
                              (:inputs txn)))
        outputs (:outputs txn)]
    (>= (reduce + (map :amount sources))
        (reduce + (map :amount outputs)))))

(defn verify-input-signature [input source txn]
  (if (and input source txn)
    (let [source-key (ks/der-string->pub-key (:address source))]
      (w/verify (:signature input)
                (t/txn-signable txn)
                source-key))
    false))

(defn signatures-valid? [db txn]
  (let [inputs-sources (t/inputs-to-sources (:inputs txn) db)]
    (every? (fn [[input source]]
              (verify-input-signature input source txn))
            inputs-sources)))

(defn txn-structure-valid? [db txn]
  (try
    (s/validate Transaction txn)
    (catch Exception e
        false)))

(defn inputs-properly-sourced? [db txn]
  (let [inputs-sources (t/inputs-to-sources (:inputs txn)
                                            db)]
    (and (every? identity (keys inputs-sources))
         (every? identity (vals inputs-sources))
         (= (count (vals inputs-sources))
            (count (into #{} (vals inputs-sources)))))))

(defn sources-unspent? [db txn]
  (let [mapped (t/inputs-to-sources (:inputs txn) db)
        sources (vals mapped)]
    (every? (partial t/unspent? (q/longest-chain db)) sources)))

(defn valid-hash? [db txn]
  (= (:hash txn) (t/txn-hash txn)))

(defn inputs-unspent-in-txn-pool? [db txn]
  (empty? (intersection (q/coord-only-inputs (q/transaction-pool db))
                        (q/coord-only-inputs [txn]))))

(defn valid-address? [db txn]
  (every? (fn [output]
            (try
              (instance?
               java.security.PublicKey
               (ks/der-string->pub-key (:address output)))
              (catch Exception e false)))
          (:outputs txn)))

(def txn-validations
  {txn-structure-valid? "Transaction structure invalid."
   inputs-properly-sourced? "One or more transaction inputs is not properly sourced, OR multiple inputs attempt to source the same output."
   sources-unspent? "Outputs referenced by one or more txn inputs has already been spent."
   sufficient-inputs? "Transaction lacks sufficient inputs to cover its outputs."
   signatures-valid? "One or more transactions signatures is invalid."
   valid-hash? "Transaction's hash does not match its contents."
   valid-address? "One or more of transaction's outputs are not assigned to a valid address."
   })

(defn validate-transaction [db txn]
  (mapcat (fn [[validation message]]
            (if-not (validation db txn)
              [message]
              []))
          txn-validations))

(defn valid-transaction? [db txn]
  (empty? (validate-transaction db txn)))
