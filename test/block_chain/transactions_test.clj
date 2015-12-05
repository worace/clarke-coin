(ns block-chain.transactions-test
  (:require [clojure.test :refer :all]
            [block-chain.transactions :refer :all]
            [cheshire.core :as json]
            [clojure.tools.namespace.repl :refer [refresh]]))

(def sample-transaction
  {:inputs [{:source-txn "1234"
             :source-output-index 1
             :signature "pizza"}]
   :outputs [{:amount 5
              :receiving-address "5678"}]})

(deftest test-serializes-transaction
  (is (= (json/generate-string [[["1234" 1 "pizza"]]
                                [[5 "5678"]]])
         (serialize sample-transaction))))

(deftest test-hashes-transaction
  #_(is (= "7fc7ff0e187867a8820ae3e6561c9dd84bcf97e9c6b9c54a64a232546693d894"
         (txn-hash sample-transaction))))

(deftest test-serializes-outputs
  (let [txn {:outputs [{:amount 5 :receiving-address "addr"}]}]
    (is (= (json/generate-string [[5 "addr"]])
           (serialize-outputs txn)))))

(run-tests)
