(ns block-chain.block-validations
  (:require [block-chain.blocks :as b]
            [block-chain.chain :as c]
            [block-chain.queries :as q]
            [block-chain.target :as target]
            [block-chain.transactions :as txn]
            [block-chain.transaction-validations :as txn-v]
            [block-chain.utils :refer :all]))

(defn valid-hash? [_ block]
  (= (q/bhash block)
     (b/block-hash block)))

(defn valid-parent-hash? [db block]
  (not (nil? (q/get-block db (q/phash block)))))

(defn hash-meets-target? [_ block]
  (b/meets-target? block))

(defn valid-target? [db block]
  (let [expected (hex->int (target/next-target (take 10 (q/longest-chain db))))
        received (hex->int (get-in block [:header :target]))
        threshold (/ expected 1000)]
    (in-delta? expected received threshold)))

(defn valid-txn-hash? [_ block]
  (= (get-in block [:header :transactions-hash])
     (b/transactions-hash (:transactions block))))

(defn valid-coinbase? [db block]
  (let [cb (first (:transactions block))]
    (and
     (= 1 (count (:outputs cb)))
     (= 0 (count (:inputs cb)))
     (= (+ txn/coinbase-reward
           (txn/txn-fees (rest (:transactions block))
                       db))
        (:amount (first (:outputs cb)))))))

(defn valid-timestamp?
  ;; TODO -- this is not quite right
  ;; actually just want to alidate that a block is not mined
  ;; significantly BEFORE its parent
  ;; don't really mind if there is a gap between 2 blocks
  "Require timestamp between 10 mins ago and 1 min from now.
   Timestamps are in millis."
  [db {{ts :timestamp} :header :as block}]
  (and (> ts (- (current-time-millis) 60000))
       (< ts (+ (current-time-millis) 6000))))

(defn valid-transactions? [db block]
  (empty? (mapcat #(txn-v/validate-transaction db %)
                  (rest (:transactions block)))))

(defn unique-txn-inputs? [_ block]
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

(defn validate-block [db block]
  (mapcat (fn [[validation message]]
            (if-not (validation db block)
              [message]
              []))
          block-validations))
