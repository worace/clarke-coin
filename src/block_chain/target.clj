(ns block-chain.target
  (:require [block-chain.utils :refer :all]
            [environ.core :refer [env]]
            [clojure.math.numeric-tower :as math]))

(def hard "0000000000000000000000010000000000000000000000000000000000000000")
(def default (or (env :default-target) "0000010000000000000000000000000000000000000000000000000000000000"))
(def frequency 300000)

(defn avg-spacing
  "Finds average time spacing in seconds of a series of times"
  [times]
  (if-not (> (count times) 1)
    0
    (->> times
       (partition 2 1)
       (map reverse)
       (map #(apply - %))
       (avg)
       (abs))))

(def max-increase 1.50)
(def min-increase 0.50)
(defn capped [ratio]
  (cond
    (> ratio max-increase) max-increase
    (< ratio min-increase) min-increase
    :else (float ratio)))

(defn target-value [block]
  (hex->int (get-in block [:header :target])))

(defn adjusted-target [blocks frequency]
  "Finds the appropriate next target for the given collection of
   blocks at the desired block-generation-frequency. First finds the
   average spacing among this sequence of blocks, then adjusts the
   most recent target by the ratio between this average and the
   desired frequency.

   Also caps the amount of change to avoid wild fluctuations.

   Note that a higher target is easier and lower target is harder, so
   an average spacing longer than the desired frequency will result in
   increasing the target, and vice versa.

   Assumes blocks are ordered from most recent to least recent,
   so the first block is the latest."
  ;; TODO - Blocks probably need to be reversed here
  ;; OR need to use ABS value for the adjustment calc
  ;; Since we reversed the chain ordering the time gaps now
  ;; come in as negative
  ;; Try testing with these sample times:
  ;; (1464998857331 1464997835129 1464994875175 1464993687994 1464987372796 1464984656015 1464983933372 1464983456575 1464983276480 1464983035510 1464979726070 1464972468482 1464962387353 1464959711863 1464959653445 1464956635558 1464956341155 1464952375924 1464952209420 1464947955003 1464945310960 1464942079225 1464936260364 1464935128026 1464933320948 1464928038067 1464925262651 1464922847098 1464920856719 1464919110823)
  (let [times (map #(get-in % [:header :timestamp]) blocks)
        latest-target (target-value (first blocks))
        ratio (/ (avg-spacing times) frequency)
        adjustment (capped ratio)]
    (hex-string (bigint (* adjustment latest-target)))))

(defn next-target
  "Calculate the appropriate next target based on the time frequency
   of recent blocks."
  [chain]
  (let [blocks (drop-last (take 30 chain))]
    (if (> (count blocks) 10)
      (adjusted-target blocks frequency)
      default)))
