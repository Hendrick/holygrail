(set-env!
 :source-paths   #{"src/cljs" "src/clj" "src/system" "test"}
 :resource-paths #{"resources"}
 :repositories #(conj % '["my.datomic.com" {:url "https://my.datomic.com/repo"
                                            :username "larry.staton@hendrickauto.com"
                                            :password "90a5d27d-5a08-4e56-b57a-12dd57592ee2"}])
 :dependencies '[[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.166"]

                 [boot/core              "2.3.0"      :scope "test"]
                 [adzerk/boot-cljs       "1.7.166-1"  :scope "test"]
                 [adzerk/boot-cljs-repl  "0.2.0"      :scope "test"]
                 [adzerk/boot-reload     "0.4.1"      :scope "test"]
                 [adzerk/boot-test       "1.0.4"      :scope "test"]
                 [environ "1.0.1"]
                 [boot-environ "1.0.1"]

                 ; server
                 [org.danielsz/system "0.2.0-SNAPSHOT"]
                 [org.immutant/web "2.1.0"]
                 [ring/ring-defaults "0.1.5"]
                 [compojure "1.4.0"]
                 [org.clojure/tools.nrepl "0.2.12"]
                 [com.cognitect/transit-clj "0.8.285"]
                 [com.taoensso/timbre "4.1.4"]

                 ; database
                 [com.datomic/datomic-pro "0.9.5327" :exclusions [joda-time]]
                 [io.rkn/conformity "0.3.5" :exclusions [com.datomic/datomic-free]]

                 ; client
                 [cljs-ajax "0.5.1"]
                 [reagent "0.5.1"]

                 ; testing
                 [org.clojure/test.check "0.8.2"]])

(require
 '[adzerk.boot-cljs      :refer [cljs]]
 '[adzerk.boot-reload    :refer [reload]]
 '[adzerk.boot-test      :refer [test]]
 '[reloaded.repl         :refer [init start stop go reset]]
 '[holy-grail.systems    :refer [dev-system prod-system]]
 '[environ.boot          :refer [environ]]
 '[environ.core          :refer [env]]
 '[system.boot           :refer [system run]]
 '[datomic.api           :as d]
 '[io.rkn.conformity     :as conformity])

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
                  :repl-port "3009"
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
   (environ :env {:http-port "3000"
                  :repl-port "3009"
                  :datomic-uri (str "datomic:mem://" (d/squuid))
                  :schema-path "schema.edn"
                  :seed-path "seed.edn"})
   (cljs :source-map true :optimizations :none)
   (run :main-namespace "holy-grail.core" :arguments [#'dev-system])
   (wait)))

(deftask prod-run
  "Run a prod system from the command line"
  []
  (comp
   (environ :env {:http-port "8008"
                  :repl-port "8009"
                  :datomic-uri (str "datomic:mem://" (d/squuid)) ; FIXME!
                  :schema-path "schema.edn"
                  :seed-path "seed.edn"})
   (cljs :optimizations :advanced)
   (run :main-namespace "holy-grail.core" :arguments [#'prod-system])
   (wait)))
