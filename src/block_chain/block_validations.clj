(ns block-chain.block-validations
  (:require [block-chain.blocks :as b]
            [block-chain.chain :as c]
            [block-chain.queries :as q]
            [block-chain.target :as t]
            [block-chain.transaction-validations :as txn-v]
            [block-chain.utils :refer :all]))

(defn valid-hash? [block _]
  (= (q/bhash block)
     (b/block-hash block)))

(defn valid-parent-hash? [block db]
  (not (nil? (q/get-block db (q/phash block)))))

(defn hash-meets-target? [block _]
  (b/meets-target? block))

(defn valid-target? [block db]
  (let [expected (hex->int (t/next-target (take 10 (q/longest-chain db))))
        received (hex->int (get-in block [:header :target]))
        threshold (/ expected 1000)]
    (in-delta? expected received threshold)))

(defn valid-txn-hash? [block _]
  (= (get-in block [:header :transactions-hash])
     (b/transactions-hash (:transactions block))))

(defn valid-coinbase? [block db]
  (let [cb (first (:transactions block))]
    (and
     (= 1 (count (:outputs cb)))
     (= 0 (count (:inputs cb)))
     (= (+ c/coinbase-reward
           (c/txn-fees (rest (:transactions block))
                       (q/longest-chain db)))
        (:amount (first (:outputs cb)))))))

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

(defn valid-transactions? [block db]
  (empty? (mapcat #(txn-v/validate-transaction % db #{})
                  (rest (:transactions block)))))

(defn unique-txn-inputs? [block _]
  (let [inputs (mapcat :inputs (:transactions block))]
    (= (count inputs)
       (count (into #{} inputs)))))

(def block-validations
  {valid-hash? "Block's hash does not match its contents."
   valid-parent-hash? "Block's parent is not a known block."
   hash-meets-target? "Block's hash does not meet the specified target."
   valid-target? "Block's target does not match expectations based on time spread of recent blocks."
   valid-txn-hash? "Block's transaction-hash does not match the contents of its transactions."
   valid-coinbase? "Block's coinbase transaction is malformed or has incorrect amount."
   ;; valid-timestamp? "Block's timestamp is not within the expected 10-minutes-ago to 1-minute-from now window."
   valid-transactions? "One or more of the Block's non-coinbase transactions are invalid."
   unique-txn-inputs? "One or more of the Block's transactions attempt to spend the same sources."})

(defn validate-block [block db]
  (mapcat (fn [[validation message]]
            (if-not (validation block db)
              [message]
              []))
          block-validations))
