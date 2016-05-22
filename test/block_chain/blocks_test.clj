(ns block-chain.miner-test
  (:require  [clojure.test :refer :all]
             [block-chain.miner :as miner]
             [block-chain.test-helper :as th]
             [block-chain.blocks :refer :all]))
(th/restore-empty-db!)
(th/restore-initial-db!)

(deftest hashes-block-by-hashing-header-values
  (let [b {:header {:parent-hash "0"
                    :transactions-hash "1"
                    :target "2"
                    :nonce 3}}]
    (is (= (sha256 "0123") (block-hash b)))))

(deftest determines-if-block-meets-specified-target
  (let [b1 {:header {:hash "0F" :target "FF"}}
        b2 {:header {:hash "FF" :target "0F"}}]
    (is (meets-target? b1))
    (is (not (meets-target? b2)))))

(deftest test-generating-block
  (let [cb (miner/coinbase address-a)
        block (blocks/generate-block
               [cb]
               {:chain []})]
    (is (= [:header :transactions] (keys block)))
    (is (= [:parent-hash :transactions-hash
            :target :timestamp :nonce] (keys (:header block))))
    (is (nil? (:hash block)))
    (is (= (hex-string 0) (get-in block [:header :parent-hash])))))

(deftest test-hashing-block
  (let [block (blocks/hashed
               (blocks/generate-block
                [(miner/coinbase address-a)]))]
    (is (get-in block [:header :hash]))
    (is (= 0 (get-in block [:header :nonce])))
    (is (not (blocks/meets-target? block)))))

(th/restore-empty-db!)
(th/restore-initial-db!)
