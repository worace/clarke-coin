(ns block-chain.chain-test
  (:require [clojure.test :refer :all]
            [block-chain.chain :refer :all]
            [block-chain.utils :refer :all]
            [clojure.tools.namespace.repl :refer [refresh]]))

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
  (let [c (read-stored-chain "./test/sample_chain.json")]
    (is (vector? c))
    (is (= 1 (count c)))
    (is (= "tx_hash" (get-in c [0 :header :transactions-hash])))))

(deftest get-block-by-hash
  (let [c (read-stored-chain "./test/sample_chain.json")
        b (block-by-hash "some-hash" c)]
    (is (= "tx_hash" (get-in b [:header :transactions-hash])))))
