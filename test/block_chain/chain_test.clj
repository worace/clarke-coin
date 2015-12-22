(ns block-chain.chain-test
  (:require [clojure.test :refer :all]
            [block-chain.chain :refer :all]
            [block-chain.utils :refer :all]
            [clojure.tools.namespace.repl :refer [refresh]]))

(def chain (read-stored-chain "./test/sample_chain.json"))
(def utxo (get-in chain [0 :transactions 0 :outputs 0]))

(def sample-block
  {:header {:parent-hash "0"
            :transactions-hash "tx_hash"
            :target "F000000000000000000000000000000000000000000000000000000000000000"
            :timestamp 1450057326
            :nonce 0
            }
   :transactions []
   :hash "some-hash"})

(deftest test-reads-stored-chain
  (is (vector? (read-stored-chain))))

(deftest adds-to-chain
  (let [c (count @block-chain)]
    (add-block! {})
    (is (= (inc c) (count @block-chain)))))

(deftest test-reads-blocks-from-json-file
  (is (vector? chain))
  (is (= 5 (count chain)))
  (is (= "acc2a45c839b7f7f25349442c68de523894f32897dea1f62fd4a2c1921d785a8"
         (get-in chain [0 :header :transactions-hash]))))

(deftest get-block-by-hash
  (let [b (first chain)
        found (block-by-hash
                (get-in b [:header :hash])
                chain)]
    (is (= b found))))

(deftest test-output-assigned-to-key
  (is (assigned-to-key? utxo (:address utxo)))
  (is (not (assigned-to-key? utxo "pizza"))))

(deftest find-txn-by-hash
  (is (txn-by-hash "e6f4ed3ff30f3936d99385d33f6410c22781359e3cfe69ccabcad109ee9ab40f"
                   chain)))

;; 5th block contains transaction spending the coinbase
;; output of txn "e6f4ed3ff30f3936d99385d33f6410c22781359e3cfe69ccabcad109ee9ab40f"
(def spent-coords
  {:source-hash "e6f4ed3ff30f3936d99385d33f6410c22781359e3cfe69ccabcad109ee9ab40f"
   :source-index 0})

(deftest tells-if-txo-is-unspent
  (let [txn-hash (get-in chain [0 :transactions :hash])]
    (is (unspent? txn-hash 0 chain))
    (is (consumes-output? (:source-hash spent-coords)
                          0
                          (get-in chain [4 :transactions 1 :inputs 0])))
    (is (not (unspent? (:source-hash spent-coords)
                       0
                       chain)))))

