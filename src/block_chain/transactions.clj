(ns block-chain.transactions
  (:require [pandect.algo.sha256 :refer [sha256]]
            [block-chain.wallet]
            [cheshire.core :as json]))

;; serialize transactions
;; sign transaction inputs
;; how to hash a transaction


(defn serialize [txn]
  (json/generate-string txn))

(defn txn-hash [txn]
  (sha256 (serialize txn)))
