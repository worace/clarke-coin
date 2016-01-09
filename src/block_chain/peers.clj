(ns block-chain.peers)

(defonce peers (atom #{}))

(defn get-peers [] (into [] @peers))
(defn add-peer! [info] (swap! peers conj info))
(defn reset-peers! [] (reset! peers #{}))
