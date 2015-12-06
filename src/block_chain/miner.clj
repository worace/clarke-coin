(ns block-chain.miner
  (:require [clojure.math.numeric-tower :as math]
            [pandect.algo.sha256 :refer [sha256]]
            [block-chain.transactions :as txn]))

;; Block Contents

;; 1. **Previous Block Hash** - Hash of the header of the previous block (this links this block
;; to the previous one)
;; 2. **Transactions Hash** - Hash of all the transactions contained in this block
;; 3. **Block Timestamp** - Time the block was created, in seconds since Unix epoch
;; 4. **Difficulty Target** - The hashing difficulty against which this block was mined (more
;; on how this target gets set later)
;; 5. **Nonce** - A special value used to "complete" the block by causing it to generate a hash
;; value lower than the required difficulty target. This value will start at 0 and be incremented
;; by miners until they find an appropriate hash value
;; 6. **Block Hash** - A SHA256 hash of the other contents in this block's header

(defn current-time-seconds [] (int (/ (System/currentTimeMillis) 1000.0)))

(defn hex-string [num] (format "%064x"
                               (biginteger num)))

(defn select-values [map ks]
  (reduce (fn [values key] (conj values (get map key)))
          []
          ks))

(def sample-block
  {:header {:parent-hash "0"
            :transactions-hash "some-hash"
            :timestamp 1449422864
            ;; 2 ^ 245
            :target "0020000000000000000000000000000000000000000000000000000000000000"
            :nonce 0
            :block-hash "some-hash"}
   :transactions [{:inputs [{:source-txn "original txn hash"
                             :source-output-index 0
                             :signature "pizza"}]
                   :outputs [{:amount 5
                              :receiving-address "(PUBLIC KEY)"}]}]})

(defn transactions-hash [{:keys [transactions]}]
  (sha256 (apply str (map txn/txn-hash transactions))))

(defn block-hash [{:keys [header]}]
  (sha256 (apply str (select-values header
                                    [:parent-hash
                                     :transactions-hash
                                     :timestamp
                                     :target
                                     :nonce]))))

(defn generate-block
  [transactions])
(defn mine [block])
