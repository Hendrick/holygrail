(ns holy-grail.systems
  (:require
   [holy-grail.handler :refer [app]]
   [environ.core :refer [env]]
   [system.core :refer [defsystem]]
   (system.components
    [immutant-web :refer [new-web-server]]
    [repl-server :refer [new-repl-server]]
    [datomic :refer [new-datomic-db]])))

(defsystem dev-system
  [:web (new-web-server (Integer. (env :http-port)) app)
   :datomic (new-datomic-db (env :datomic-uri))
   :repl-server (new-repl-server (Integer. (env :repl-port)))])

(defsystem prod-system
  [:web (new-web-server (Integer. (env :http-port)) app)
   :repl-server (new-repl-server (Integer. (env :repl-port)))])
