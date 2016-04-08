(ns block-chain.http
  (:require [compojure.core :refer :all]
            [ring.adapter.jetty :as jetty]
            [block-chain.utils :as utils]
            [ring.util.response :refer [response]]
            [ring.middleware.json :refer [wrap-json-response]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [block-chain.message-handlers :as h]
            [compojure.route :as route]))

(defroutes my-routes
  (GET "/" [] "<h1>Hello World</h1>")
  (POST "/echo" req (response (h/echo (:body req) {})))
  (POST "/ping" req (response (h/ping (:body req) {})))
  (route/not-found "<h1>Page not found</h1>"))

(defn json-body [handler]
  (fn [request]
    (handler (update request :body (comp utils/read-json slurp)))))

(def app (-> my-routes
             (json-body)
             (wrap-json-response)))

(defonce server (atom nil))

(defn start!
  ([] (start! 3000))
  ([port]
   (if-let [server @server]
     (.stop server))
   (let [s (jetty/run-jetty #'app {:port port :join? false})]
     (.start s)
     (reset! server s))))

(defn stop! [] (.stop @server))
