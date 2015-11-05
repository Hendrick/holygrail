(ns holy-grail.api
  (:require [datomic.api :as d]
            [environ.core :refer [env]]))

(defn peeps
  "Return all person names from DB"
  []
  (let [conn (d/connect (env :datomic-uri))]
    (d/q '[:find [?name ...]
           :where [_ :person/name ?name _ _]]
         (d/db conn))))
