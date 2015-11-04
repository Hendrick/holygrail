(set-env!
 :source-paths   #{"src/cljs" "src/clj" "src/system"}
 :resource-paths #{"resources"}
 :dependencies '[[adzerk/boot-cljs      "1.7.48-6" :scope "test"]
                 [adzerk/boot-reload    "0.4.1"      :scope "test"]
                 [environ "1.0.1"]
                 [boot-environ "1.0.1"]
                 ; server
                 [org.danielsz/system "0.2.0-SNAPSHOT"]
                 [org.immutant/web "2.1.0"]
                 [ring/ring-defaults "0.1.5"]
                 [compojure "1.4.0"]
                 [org.clojure/tools.nrepl "0.2.11"]
                 ; client
                 [org.clojure/clojurescript "1.7.145"]
                 [reagent "0.5.1"]])

(require
 '[adzerk.boot-cljs      :refer [cljs]]
 '[adzerk.boot-reload    :refer [reload]]
 '[reloaded.repl :refer [init start stop go reset]]
 '[holy-grail.systems :refer [dev-system prod-system]]
 '[environ.boot :refer [environ]]
 '[system.boot :refer [system run]])

(deftask dev
  "Run a restartable system in the REPL"
  []
  (comp
   (environ :env {:http-port "3000"})
   (watch :verbose true)
   (system :sys #'dev-system :auto-start true :hot-reload true :files ["handler.clj"])
   (reload)
   (cljs :source-map true :optimizations :none)
   (repl :server true)))

(deftask dev-run
  "Run a dev system from the command line"
  []
  (comp
   (environ :env {:http-port "3000"})
   (cljs :source-map true :optimizations :none)
   (run :main-namespace "holy-grail.core" :arguments [#'dev-system])
   (wait)))

(deftask prod-run
  "Run a prod system from the command line"
  []
  (comp
   (environ :env {:http-port 8008
                  :repl-port 8009})
   (cljs :optimizations :advanced)
   (run :main-namespace "holy-grail.core" :arguments [#'prod-system])
   (wait)))
