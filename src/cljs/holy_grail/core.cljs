(ns holy-grail.core
  (:require [reagent.core :as reagent]
            [ajax.core :refer [GET]]))

(def peeps (reagent/atom []))

(defn handler
  "Handle RESPONSE from server"
  [response]
  (reset! peeps response))

(defn error-handler
  "Handle ERROR-RESPONSE from server"
  [error-response]
  (.error js/console error-response))

(defn load-peeps
  "Load peeps from the server"
  []
  (GET "/peeps" {:handler handler
                 :error-handler error-handler}))

(defn hello-world-view []
  [:h1 (str "Yo! " (first @peeps) "!")])

(defn ^:export main []
  (load-peeps)
  (reagent/render-component [hello-world-view] (.getElementById js/document "container")))
