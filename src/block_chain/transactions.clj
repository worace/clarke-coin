(ns block-chain.transactions
  (:require [pandect.algo.sha256 :refer [sha256]]
            [cheshire.core :as json]))

;; serialize transactions
;; sign transaction inputs
;; how to hash a transaction

;; sample txn format:
#_{:inputs [{:source-txn "original txn hash"
             :source-output-index 0
             :signature "pizza"}]
   :outputs [{:amount 5
              :receiving-address "some addr"}]}

(defn serialize [txn]
  (json/generate-string txn))

(defn output-vec [{:keys [amount receiving-address]}]
  [amount receiving-address])

(defn serialize-outputs
  [txn]
  (->> (:outputs txn)
       (map output-vec)
       (json/generate-string)))

(defn txn-hash [txn]
  (sha256 (serialize txn)))

