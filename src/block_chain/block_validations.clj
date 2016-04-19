(ns block-chain.block-validations
  (:require [block-chain.blocks :as b]
            [block-chain.chain :as c]
            [block-chain.target :as t]
            [block-chain.utils :refer :all]))

(defn valid-hash?
  ([block] (valid-hash? block []))
  ([block chain]
   (= (get-in block [:header :hash])
                    (b/block-hash block))))

(defn valid-parent-hash? [block chain]
  (= (get-in block [:header :parent-hash])
     (c/latest-block-hash chain)))

(def hash-meets-target? b/meets-target?)

(defn valid-target? [block chain]
  (let [expected (hex->int (c/next-target chain))
        received (hex->int (get-in block [:header :target]))
        threshold (/ expected 1000)]
    (in-delta? expected received threshold)))

(defn valid-txn-hash? [block]
  (= (get-in block [:header :transactions-hash])
     (b/transactions-hash (:transactions block))))

(defn valid-coinbase? [block chain]
  (let [cb (first (:transactions block))]
    (println "COINBASE: " cb)
    (and
     (= 1 (count (:outputs cb)))
     (= 0 (count (:inputs cb)))
     (= (+ c/coinbase-reward
           (c/txn-fees (rest (:transactions block)) chain))
        (:amount (first (:outputs cb))))
     )))
