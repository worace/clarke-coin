(ns block-chain.chain
  (:require [clojure.java.io :as io]
            [clojure.math.numeric-tower :as math]
            [block-chain.utils :refer :all]
            [block-chain.target :as target]))

(def coinbase-reward 25)

(defn transactions [blocks] (mapcat :transactions blocks))
(defn inputs [blocks] (mapcat :inputs (transactions blocks)))
(defn outputs [blocks] (mapcat :outputs (transactions blocks)))

(defn txn-by-hash
  [hash blocks]
  (first (filter #(= hash (get % :hash))
                 (transactions blocks))))

(defn source-output [blocks input]
  (if-let [t (txn-by-hash (:source-hash input)
                          blocks)]
    (get (:outputs t) (:source-index input))))

(defn inputs-to-sources [inputs chain]
  (into {}
        (map (fn [i] [i (source-output chain i)])
             inputs)))

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

(defn balance [address blocks]
  (->> (unspent-outputs address blocks)
       (map :amount)
       (reduce +)))

(defn unspent-output-coords [key blocks]
  (mapcat (fn [txn]
            (mapcat (fn [output index]
                      (if (assigned-to-key? key output)
                        [{:source-hash (:hash txn) :source-index index}]
                        nil))
                    (:outputs txn)
                    (range (count (:outputs txn)))))
          (transactions blocks)))

(defn txn-fees
  "Finds available txn-fees from a pool of txns by finding the diff
   between cumulative inputs and cumulative outputs"
  [txns chain]
  (let [sources (map (partial source-output chain)
                     (mapcat :inputs txns))
        outputs (mapcat :outputs txns)]
    (- (reduce + (map :amount sources))
       (reduce + (map :amount outputs)))))
