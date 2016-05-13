(ns block-chain.utils
  (:require [cheshire.core :as json]
            [clojure.math.numeric-tower :as math]
            [pandect.algo.sha256 :as pandect]
            [clojure.java.io :as io]))

(def sha256 pandect/sha256)

(defn cat-keys
  "take a map and a vector of keys and create a concatenated
   string of the value for each key"
  [keys m]
  (apply str (map (partial get m) keys)))

(defn hex->int
  "Read hex string and convert it to big integer."
  [hex-string]
  (bigint (java.math.BigInteger. hex-string 16)))

(defn hex-string [num] (format "%064x"
                               (biginteger num)))

(defn current-time-seconds [] (int (/ (System/currentTimeMillis) 1000.0)))

(defn current-time-millis [] (System/currentTimeMillis))

(defn read-json [s]
  (json/parse-string s (fn [k] (keyword (.replace (name k) "_" "-")))))

(defn write-json [m]
  (json/generate-string
   m
   {:key-fn (fn [k] (.replace (name k) "-" "_"))}))

(defn avg [things]
  (/ (reduce + things) (count things)))

(def compact (partial filter (comp not nil?)))

(def abs math/abs)

(defn in-delta?
  ([x y] (in-delta? x y 0.001))
  ([x y delta] (< (abs (- x y)) delta)))
