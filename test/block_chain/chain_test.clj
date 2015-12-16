(ns block-chain.chain-test
  (:require [clojure.test :refer :all]
            [block-chain.chain :refer :all]
            [clojure.tools.namespace.repl :refer [refresh]]))

(deftest test-reads-stored-chain
  (is (= [] (read-stored-chain))))
