(ns block-chain.db-test
  (:require [block-chain.db :refer :all]
            [clojure.test :refer :all]))

(def chain (read-stored-chain "./test/sample_chain.json"))

(deftest test-reads-blocks-from-json-file
  (is (vector? chain))
  (is (= 5 (count chain)))
  (is (= "acc2a45c839b7f7f25349442c68de523894f32897dea1f62fd4a2c1921d785a8"
         (get-in chain [0 :header :transactions-hash]))))

(deftest test-reads-stored-chain
  (is (vector? (read-stored-chain chain-path))))
