(ns block-chain.log
  (:require [clojure.tools.logging :as l]))

(def node-id (str "Node-" (rand-nth (range 10000))))

;; (defn info [& statements]
;;   (apply l/info "Node: " ))


(defmacro info [& statements]
  `(l/info node-id ~@statements))
