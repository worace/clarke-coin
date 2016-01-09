(ns block-chain.message-handlers-test
  (:require [clojure.test :refer :all]
            [block-chain.utils :refer :all]
            [block-chain.peers :as peers]
            [block-chain.message-handlers :refer :all]))

(def sock-info
  {:remote-address "127.0.0.1"
   :local-port 8334
   :outgoing-port 51283})

(deftest test-echo
  (let [msg {:message-type "echo"
             :payload "echo this"}]
    (is (= msg (handler msg {})))))

(deftest test-ping-pong
  (let [msg {:message-type "ping"
             :payload (current-time-millis)}]
    (is (= (assoc msg :message-type "pong") (handler msg {})))))

(deftest test-getting-and-adding-peers
  (peers/reset-peers!)
  (is (= [] (:payload (handler {:message-type "get_peers"} {}))))
  (handler {:message-type "add_peer"
            :payload {:port 8335}}
           sock-info)
  (is (= [{:host "127.0.0.1" :port 8335}]
         (:payload (handler {:message-type "get_peers"} {})))))
