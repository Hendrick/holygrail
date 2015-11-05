(ns holy-grail.handler
  (:import (java.io ByteArrayOutputStream))
  (:require
   [holy-grail.api :as api]
   [clojure.java.io :as io]
   [cognitect.transit :as t]
   [compojure.core :refer :all]
   [compojure.route :as route]
   [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
   [ring.util.response :as resp]))

(defn write-transit
  "Write out Transit for RESPONSE"
  [response]
  (let [baos (ByteArrayOutputStream.)
        w    (t/writer baos :json)
        _    (t/write w response)
        ret  (.toString baos)]
    (.reset baos)
    ret))

(defroutes app-routes
  (GET "/peeps" [] {:status 200
                    :headers {"Content-Type" "application/transit+json; charset=utf-8"}
                    :body (write-transit (api/peeps))})
  (GET "/" [] (-> (resp/resource-response "index.html")
                  (resp/content-type "text/html")))
  (route/not-found "Not Found"))

(def middleware (-> site-defaults
                 (assoc-in [:static :resources] "/")
                 (assoc-in [:security :anti-forgery] false)))

(def app
  (-> app-routes
      (wrap-defaults middleware)))
