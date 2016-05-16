(ns block-chain.chain
  (:require [clojure.java.io :as io]
            [clojure.math.numeric-tower :as math]
            [block-chain.queries :as q]
            [block-chain.utils :refer :all]
            [block-chain.target :as target]))

(def coinbase-reward 25)

(defn transactions [blocks] (mapcat :transactions blocks))
(defn inputs [blocks] (mapcat :inputs (transactions blocks)))
(defn outputs [blocks] (mapcat :outputs (transactions blocks)))

(defn inputs-to-sources [inputs db]
  (zipmap inputs
          (map (partial q/source-output db) inputs)))

(defn consumes-output?
  [source-hash source-index input]
  (and (= source-hash (:source-hash input))
       (= source-index (:source-index input))))

(defn unspent?
  "takes a txn hash and output index identifying a
   Transaction Output in the block chain. Searches the
   chain to find if this output has been spent."
  [blocks output]
  (let [inputs (inputs blocks)
        {:keys [transaction-id index]} (:coords output)
        spends-output? (partial consumes-output? transaction-id index)]
    (not-any? spends-output? inputs)))

(defn assigned-to-key? [key output]
  (= key (:address output)))

(defn unspent-outputs [key blocks]
  (->> (outputs blocks)
       (filter (partial assigned-to-key? key))
       (filter (partial unspent? blocks))))

(defn unspent-outputs-db [key db]
  (->> (q/utxos db)
       (filter (partial assigned-to-key? key))))

(defn balance-db [address db]
  (->> (unspent-outputs-db address db)
       (map :amount)
       (reduce +)))

(defn txn-fees
  "Finds available txn-fees from a pool of txns by finding the diff
   between cumulative inputs and cumulative outputs"
  [txns db]
  (let [sources (map (partial q/source-output db)
                     (mapcat :inputs txns))
        outputs (mapcat :outputs txns)]
    (- (reduce + (map :amount sources))
       (reduce + (map :amount outputs)))))
