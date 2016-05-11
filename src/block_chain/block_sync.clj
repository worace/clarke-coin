(ns block-chain.block-sync
  (:require [block-chain.peer-client :as pc]
            [block-chain.db :as db]
            [block-chain.block-validations :as bv]
            [block-chain.utils :refer :all]))

(defn bhash [b] (get-in b [:header :hash]))
(defn block-hashes [chain] (map bhash chain))

(defn synced-chain [chain peer]
  (loop [this-batch (pc/blocks-since peer (bhash (last chain)))
         chain chain]
    (if (empty? this-batch)
      (let [next-batch (pc/blocks-since peer (bhash (last chain)))]
        (if (empty? next-batch)
          chain
          (recur next-batch chain)))
      (let [next-block (pc/block peer (first this-batch))]
        (if (empty? (bv/validate-block next-block chain))
          (recur (rest this-batch) (conj chain next-block))
          chain)))))

(defn sync-if-needed! [chain-ref peer]
  (try
    (if (> (pc/block-height peer) (count @chain-ref))
      (do (println "Found longer block chain in peer " peer ". Will sync")
          (swap! chain-ref synced-chain peer)))
    (catch Exception e (println "Error syncing with peer: " peer ": " (.getMessage e)))))

;; take in:
;; peer address
;; existing chain
;; return new chain with peer additions accounted for
;; 1 - find common chain
