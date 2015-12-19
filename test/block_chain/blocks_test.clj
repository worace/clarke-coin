(ns block-chain.miner-test
  (:require  [clojure.test :refer :all]
             [block-chain.blocks :refer :all]))

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
