(ns block-chain.block-validations
  (:require [block-chain.blocks :as b]
            [block-chain.chain :as c]
            [block-chain.target :as t]
            [block-chain.transaction-validations :as txn-v]
            [block-chain.utils :refer :all]))

(defn valid-hash? [block _]
  (= (get-in block [:header :hash])
     (b/block-hash block)))

(defn valid-parent-hash? [block chain]
  (= (get-in block [:header :parent-hash])
     (c/latest-block-hash chain)))

(defn hash-meets-target? [block _]
  (b/meets-target? block))

(defn valid-target? [block chain]
  (let [expected (hex->int (c/next-target chain))
        received (hex->int (get-in block [:header :target]))
        threshold (/ expected 1000)]
    (in-delta? expected received threshold)))

(defn valid-txn-hash? [block _]
  (= (get-in block [:header :transactions-hash])
     (b/transactions-hash (:transactions block))))

(defn valid-coinbase? [block chain]
  (let [cb (first (:transactions block))]
    (and
     (= 1 (count (:outputs cb)))
     (= 0 (count (:inputs cb)))
     (= (+ c/coinbase-reward
           (c/txn-fees (rest (:transactions block)) chain))
        (:amount (first (:outputs cb))))
     )))

(defn valid-timestamp?
  ;; TODO -- this is not quite right
  ;; actually just want to alidate that a block is not mined
  ;; significantly BEFORE its parent
  ;; don't really mind if there is a gap between 2 blocks
  "Require timestamp between 10 mins ago and 1 min from now.
   Timestamps are in millis."
  [{{ts :timestamp} :header} _]
  (and (> ts (- (current-time-millis) 60000))
       (< ts (+ (current-time-millis) 6000))))

(defn valid-transactions? [block chain]
  (empty? (mapcat #(txn-v/validate-transaction % chain #{})
                  (rest (:transactions block)))))

(defn unique-txn-inputs? [block chain]
  (let [inputs (mapcat :inputs (:transactions block))]
    (= (count inputs)
       (count (into #{} inputs)))))

(def block-validations
  {valid-hash? "Block's hash does not match its contents."
   valid-parent-hash? "Block's parent hash does not match most recent block on the chain."
   hash-meets-target? "Block's hash does not meet the specified target."
   valid-target? "Block's target does not match expectations based on time spread of recent blocks."
   valid-txn-hash? "Block's transaction-hash does not match the contents of its transactions."
   valid-coinbase? "Block's coinbase transaction is malformed or has incorrect amount."
   ;; valid-timestamp? "Block's timestamp is not within the expected 10-minutes-ago to 1-minute-from now window."
   valid-transactions? "One or more of the Block's non-coinbase transactions are invalid."
   unique-txn-inputs? "One or more of the Block's transactions attempt to spend the same sources."})

(defn validate-block [block chain]
  (mapcat (fn [[validation message]]
            (if-not (validation block chain)
              [message]
              []))
          block-validations))
