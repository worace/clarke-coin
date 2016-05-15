(ns block-chain.transaction-validations
  (:require [schema.core :as s]
            [block-chain.chain :as c]
            [block-chain.transactions :as t]
            [block-chain.queries :as q]
            [block-chain.key-serialization :as ks]
            [block-chain.wallet :as w]
            [block-chain.utils :refer :all]
            [block-chain.schemas :refer :all]))

(defn new-transaction? [db txn]
  (not (contains? (q/transaction-pool db) txn)))

(defn sufficient-inputs? [db txn]
  (let [sources (compact (map (partial c/source-output (q/longest-chain db))
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
  (let [inputs-sources (c/inputs-to-sources (:inputs txn) (q/longest-chain db))]
    (every? (fn [[input source]]
              (verify-input-signature input source txn))
            inputs-sources)))

(defn txn-structure-valid? [db txn]
  (try
    (s/validate Transaction txn)
    (catch Exception e
        false)))

(defn inputs-properly-sourced? [db txn]
  (let [inputs-sources (c/inputs-to-sources (:inputs txn)
                                            (q/longest-chain db))]
    (and (every? identity (keys inputs-sources))
         (every? identity (vals inputs-sources))
         (= (count (vals inputs-sources))
            (count (into #{} (vals inputs-sources)))))))

(defn inputs-unspent? [db txn]
  (let [sources (vals (c/inputs-to-sources (:inputs txn)
                                           (q/longest-chain db)))]
    (every? (partial c/unspent? (q/longest-chain db)) sources)))

(defn valid-hash? [db txn]
  (= (:hash txn) (t/txn-hash txn)))

(def txn-validations
  {txn-structure-valid? "Transaction structure invalid."
   inputs-properly-sourced? "One or more transaction inputs is not properly sourced, OR multiple inputs attempt to source the same output."
   inputs-unspent? "Outputs referenced by one or more txn inputs has already been spent."
   sufficient-inputs? "Transaction lacks sufficient inputs to cover its outputs."
   signatures-valid? "One or more transactions signatures is invalid."
   valid-hash? "Transaction's hash does not match its contents."
   })

(defn validate-transaction [db txn]
  (mapcat (fn [[validation message]]
            (if-not (validation db txn)
              [message]
              []))
          txn-validations))

(defn valid-transaction? [db txn]
  (empty? (validate-transaction db txn)))
