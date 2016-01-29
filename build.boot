(def datomic-ver "0.9.5344")

;; Check that the needed variables are configured
(def required-config '[datomic-user datomic-pass datomic-license])

(let [msgs (->> required-config
                (map #(if-not (resolve %) (format "`%s'" %)))
                (filter identity)
                (clojure.string/join " and "))]
  (if-not (empty? msgs)
    (do
      (println "Please define" msgs "in profile.boot first!")
      (System/exit 1))))

;; Requires needed to set the boot environment below
(require '[boot.from.backtick :refer [template]]
         '[clojure.java.io :as io])

(set-env!
 :source-paths   #{"src/cljs" "src/clj" "src/system" "test"}
 :resource-paths #{"resources"}
 :repositories #(concat % [["my.datomic.com" {:url "https://my.datomic.com/repo"
                                              :username datomic-user
                                              :password datomic-pass}]
                           ["local" (-> "repository" io/file io/as-url str)]])
 :dependencies (template
                [[org.clojure/clojure        "1.8.0"]
                 [org.clojure/clojurescript  "1.7.228"]

                 ; boot tasks
                 [boot/core                         "2.5.5"           :scope "test"]
                 [adzerk/boot-cljs                  "1.7.228-1"       :scope "test"]
                 [adzerk/boot-cljs-repl             "0.3.0"           :scope "test"]
                 [adzerk/boot-reload                "0.4.4"           :scope "test"]
                 [adzerk/boot-test                  "1.1.0"           :scope "test"]
                 [pleasetrythisathome/boot-datomic  "0.1.0-SNAPSHOT"  :scope "test"]
                 [boot-environ                      "1.0.1"           :scope "test"]

                 ; task dependencies
                 [clj-http-lite "0.3.0" :scope "test"]

                 ; server
                 [org.danielsz/system        "0.2.0"]
                 [org.immutant/web           "2.1.2"]
                 [ring/ring-defaults         "0.1.5"]
                 [compojure                  "1.4.0"]
                 [environ                    "1.0.1"]
                 [org.clojure/tools.nrepl    "0.2.12"]
                 [com.cognitect/transit-clj  "0.8.285"  :exclusions [com.fasterxml.jackson.core/jackson-core]]
                 [com.taoensso/timbre        "4.2.1"]

                 ; database
                 [com.datomic/datomic-pro  ~datomic-ver  :exclusions [joda-time org.slf4j/slf4j-nop]]
                 [io.rkn/conformity        "0.4.0"       :exclusions [com.datomic/datomic-free]]

                 ; transactor dependencies
                 [com.amazonaws/aws-java-sdk-core        "1.10.49"]
                 [com.amazonaws/aws-java-sdk-cloudwatch  "1.10.49"]
                 [com.amazonaws/aws-java-sdk-s3          "1.10.49"]

                 ; client
                 [cljs-ajax  "0.5.3"]
                 [reagent    "0.5.1"]

                 ; testing
                 [org.clojure/test.check  "0.9.0"]]))

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
 '[clj-http.lite.client  :as http]
 '[io.rkn.conformity     :as conformity])

(task-options!
 environ {:env {:http-port "3000"
                :repl-port "3009"
                :datomic-uri "datomic:dev://localhost:4334/athena"
                :schema-path "schema.edn"
                :seed-path "seed.edn"}}
 datomic {:license-key datomic-license}
 cljs {:source-map true :optimizations :none})

;; See http://hoplon.discoursehosting.net/t/question-about-data-readers-with-datomic-and-boot/99/7
(boot.core/load-data-readers!)

(defn- datomic-download
  "Returns an input stream for the Datomic ZIP file download. Currently,
  following successful authentication Datomic redirects the request to a
  generated S3 URL. However, S3 balks at the Basic Auth headers from the
  original request that are automatically included in the re-request by
  clj-http-lite. This function, then, wraps the convoluted dance required to
  make a manual redirect work."
  [user pass]
  (let [zip-url (format
                 "https://my.datomic.com/repo/com/datomic/datomic-pro/%1$s/datomic-pro-%1$s.zip"
                 datomic-ver)
        request (http/get zip-url {:as :stream
                                   :basic-auth [user pass]
                                   :follow-redirects false})]
    (if (= 302 (:status request))
      (let [redirect (http/get (get-in request [:headers "location"]) {:as :stream})]
        (-> request :body .close)
        (:body redirect))
      (:body request))))

(deftask install-datomic
  "Download and install the Datomic transactor JAR (if necessary)"
  []
  (try
    (set-env! :dependencies #(conj % (template [com.datomic/datomic-transactor-pro ~datomic-ver])))
    (require '[datomic.transactor])
    (println "Datomic transactor already installed!")
    (catch Throwable e
      (println "Datomic transactor not found. Installing now...")
      (let [repo-dir (-> "repository/com/datomic/datomic-transactor-pro/"
                         (str datomic-ver)
                         io/file)
            jar-name (format "datomic-transactor-pro-%s.jar" datomic-ver)
            jar-path (str repo-dir "/" jar-name)
            jar-digest (java.security.MessageDigest/getInstance "SHA-1")
            zip-dir (tmp-dir!)
            zip-file (-> (.getPath zip-dir)
                         (str "datomic.zip")
                         (io/file))]
        (when-not (.exists repo-dir)
          (.mkdirs repo-dir))
        (with-open [download (datomic-download datomic-user datomic-pass)
                    zip-file-out (io/output-stream zip-file)]
          (io/copy download zip-file-out))
        (with-open [jar-file (-> jar-path
                                 io/file
                                 io/output-stream)
                    zip-file-in (java.util.zip.ZipFile. zip-file)
                    zipped-jar (java.security.DigestInputStream.
                                (->> (format "datomic-pro-%s/%s" datomic-ver jar-name)
                                     (.getEntry zip-file-in)
                                     (.getInputStream zip-file-in))
                                jar-digest)]
          (io/copy zipped-jar jar-file)
          (spit (str jar-path ".sha1")
                (->> (.digest jar-digest)
                     (java.math.BigInteger. 1)
                     (format "%x\n")))))
      (set-env! :dependencies #(conj % (template [com.datomic/datomic-transactor-pro ~datomic-ver])))))
  identity)

(deftask ensure-schema
  "Ensure that Datomic transacted our schema"
  []
  (let [tmp (tmp-dir!)]
    (with-pre-wrap fileset
      (empty-dir! tmp)
      (println "Ensuring schema...")
      (let [ds (read-string (slurp (tmp-file (first (by-name [(env :schema-path)] (user-files fileset))))))
            dc (d/connect (env :datomic-uri))]
        (conformity/ensure-conforms dc ds))
      (commit! fileset))))

(deftask seed-database
  "Seed the database with some data"
  []
  (let [tmp (tmp-dir!)]
    (with-pre-wrap fileset
      (empty-dir! tmp)
      (println "Seeding database...")
      (let [sd (read-string (slurp (tmp-file (first (by-name [(env :seed-path)] (user-files fileset))))))
            dc (d/connect (env :datomic-uri))]
        (d/transact-async dc sd))
      (commit! fileset))))

(deftask dev
  "Run a restartable system in the REPL"
  []
  (comp
   (environ)
   (install-datomic)
   (datomic)
   (watch :verbose true)
   (system :sys #'dev-system :auto-start true :hot-reload true :files ["handler.clj"])
   (reload)
   (cljs)
   (repl :server true)
   (ensure-schema)
   (seed-database)
   (target :no-clean true)))

(deftask dev-run
  "Run a dev system from the command line"
  []
  (comp
   (environ)
   (install-datomic)
   (datomic)
   (cljs)
   (run :main-namespace "holy-grail.core" :arguments [#'dev-system])
   (target)
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
   (target)
   (wait)))
