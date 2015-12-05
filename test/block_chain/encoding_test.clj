(ns block-chain.encoding-test
  (:require [clojure.test :refer :all]
            [block-chain.base64 :refer :all]))

(deftest test-base-64
  (let [b (.getBytes "pizza")
        chars (map char "pizza")]
    (is (= chars (map char (decode64 (encode64 b)))))
    (is (= "cGl6emE=" (encode64 (.getBytes "pizza"))))
    (is (= chars (map char (decode64 "cGl6emE="))))))
