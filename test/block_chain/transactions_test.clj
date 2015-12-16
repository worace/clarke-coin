(ns block-chain.transactions-test
  (:require [clojure.test :refer :all]
            [block-chain.transactions :refer :all]
            [cheshire.core :as json]
            [pandect.algo.sha256 :refer [sha256]]
            [clojure.tools.namespace.repl :refer [refresh]]))

(def sample-transaction
  {:inputs [{:source-hash "1234"
             :source-index 1
             :signature "pizza"}]
   :outputs [{:amount 5
              :address "5678"}]})

(deftest test-hashable-transaction-string
  (let [hashable (str "1234" "1" "pizza" "5" "5678")]
    (is (= hashable
         (txn-hashable sample-transaction)))))

(deftest test-hashes-transaction
  (let [hashable (str "1234" "1" "pizza" "5" "5678")]
    (is (= (sha256 hashable)
         (txn-hash sample-transaction)))))

(deftest test-serializes-transaction
  (let [txn {:outputs [{:amount 5 :receiving-address "addr"}]
             :inputs [{:source-hash "1234" :source-index 0 :signature "sig"}]}]
    (is (= txn
           (read-txn (serialize-txn txn))))))

(run-tests)
