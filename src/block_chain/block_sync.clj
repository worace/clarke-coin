(ns block-chain.block-sync
  (:require [block-chain.peer-client :as pc]
            [block-chain.db :as db]
            [block-chain.queries :refer [bhash]]
            [block-chain.block-validations :as bv]
            [block-chain.utils :refer :all]))

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
        (println "pulling block:" (first this-batch))
        (println "block data:" next-block)
        (if (empty? (bv/validate-block next-block chain))
          (recur (rest this-batch) (conj chain next-block))
          chain)))))

(defn sync-if-needed! [chain-ref peer]
  (try
    (let [h (pc/block-height peer)]
      (if (> h (count @chain-ref))
        (do (println "Found longer block chain in peer" peer "-" h)
            (swap! chain-ref synced-chain peer))))
    (catch Exception e (println "Error syncing with peer: " peer ": " (.getMessage e)))))
