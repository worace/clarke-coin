(ns block-chain.transactions
  (:require [pandect.algo.sha256 :refer [sha256]]
            [cheshire.core :as json]))

;; sample txn format:
#_{:inputs [{:source-txn "original txn hash"
             :source-output-index 0
             :signature "pizza"}]
   :outputs [{:amount 5
              :receiving-address "some addr"}]}

(defn output-vec [{:keys [amount receiving-address]}]
  [amount receiving-address])

(defn input-vec [{:keys [source-txn source-output-index signature]}]
  [source-txn source-output-index signature])

(defn serialize [{:keys [inputs outputs]}]
  (json/generate-string [(map input-vec inputs)
                         (map output-vec outputs)]))

(defn serialize-outputs
  [txn]
  (->> (:outputs txn)
       (map output-vec)
       (json/generate-string)))

(defn txn-hash [txn]
  (sha256 (serialize txn)))

