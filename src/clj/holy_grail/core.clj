(ns holy-grail.core
  (:gen-class)
  (:require [holy-grail.systems :refer [prod-system]]
            [reloaded.repl :refer [system init start stop go reset]]
            [taoensso.timbre :as timbre]
            [taoensso.timbre.appenders.core :as appenders]))

(defn -main
  "Start a production system, unless a system is passed as argument (as in the dev-run task)."
  [& args]
  (let [system (or (first args) #'prod-system)]
    (timbre/merge-config! {:appenders {:immutant-dot-log
                                       (appenders/spit-appender {:fname
                                                                 "/var/log/immutant/immutant.log"})}})
    (reloaded.repl/set-init! system)
    (go)))
