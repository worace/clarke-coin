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

(defn sign-inputs
  "Transaction inputs must be signed by including an RSA/SHA256 signature
   of all outputs in the transaction. The signature must match the Public Key
   to which the source output for each input was assigned."
  [[inputs outputs]]
  (let [sig (wallet/sign (serialize outputs))]
    [(map #(conj % sig) inputs)
     outputs])
  )
