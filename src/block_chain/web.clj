(ns block-chain.web
  (:require [compojure.core :refer :all]
            [ring.adapter.jetty :as jetty]
            [compojure.route :as route]))

(defroutes app
  (GET "/" [] "<h1>Hello World</h1>")
  (route/not-found "<h1>Page not found</h1>"))

(defonce server (atom nil))

(defn start! []
  (if-let [server @server]
    (.stop server))
  (let [s (jetty/run-jetty #'app {:port 3000 :join? false})]
    (.start s)
    (reset! server s)))
