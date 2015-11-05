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
                 ; database
                 [com.datomic/datomic-pro "0.9.5327" :exclusions [joda-time]]
                 [io.rkn/conformity "0.3.5" :exclusions [com.datomic/datomic-free]]
                 ; client
                 [org.clojure/clojurescript "1.7.145"]
                 [reagent "0.5.1"]])

(require
 '[adzerk.boot-cljs      :refer [cljs]]
 '[adzerk.boot-reload    :refer [reload]]
 '[reloaded.repl :refer [init start stop go reset]]
 '[holy-grail.systems :refer [dev-system prod-system]]
 '[environ.boot :refer [environ]]
 '[environ.core :refer [env]]
 '[system.boot :refer [system run]]
 '[datomic.api :as d]
 '[io.rkn.conformity :as conformity])

;; See http://hoplon.discoursehosting.net/t/question-about-data-readers-with-datomic-and-boot/99/7
(boot.core/load-data-readers!)

(deftask ensure-schema
  "Ensure that Datomic transacted our schema"
  []
  (let [tmp (boot.core/tmp-dir!)]
    (boot.core/with-pre-wrap fileset
      (boot.core/empty-dir! tmp)
      (println "Ensuring schema...")
      (let [ds (read-string (slurp (boot.core/tmp-file (first (boot.core/by-name [(env :schema-path)] (user-files fileset))))))
            dc (d/connect (env :datomic-uri))]
        (conformity/ensure-conforms dc ds))
      (commit! fileset))))

(deftask seed-database
  "Seed the database with some data"
  []
  (let [tmp (boot.core/tmp-dir!)]
    (boot.core/with-pre-wrap fileset
      (boot.core/empty-dir! tmp)
      (println "Seeding database...")
      (let [sd (read-string (slurp (boot.core/tmp-file (first (boot.core/by-name [(env :seed-path)] (user-files fileset))))))
            dc (d/connect (env :datomic-uri))]
        (d/transact-async dc sd))
      (commit! fileset))))

(deftask dev
  "Run a restartable system in the REPL"
  []
  (comp
   (environ :env {:http-port "3000"
                  :datomic-uri (str "datomic:mem://" (d/squuid))
                  :schema-path "schema.edn"
                  :seed-path "seed.edn"})
   (watch :verbose true)
   (system :sys #'dev-system :auto-start true :hot-reload true :files ["handler.clj"])
   (reload)
   (cljs :source-map true :optimizations :none)
   (repl :server true)
   (ensure-schema)
   (seed-database)))

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
