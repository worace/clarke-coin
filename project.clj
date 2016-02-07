(defproject block-chain "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :main block-chain.core
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.bouncycastle/bcpg-jdk15on "1.53"]
                 [org.bouncycastle/bcpkix-jdk15on "1.53"]
                 [org.clojure/math.numeric-tower "0.0.4"]
                 [cheshire "5.5.0"]
                 [net.async/async "0.1.0"]
                 [org.clojure/core.async "0.2.374"]
                 [pandect "0.5.4"]])
