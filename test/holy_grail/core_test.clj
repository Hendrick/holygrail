(ns holy-grail.core-test
  (:require [holy-grail.core :as core]
            [clojure.test :refer [deftest testing is]]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.clojure-test :refer [defspec]]))

(deftest test-something
  (testing "FIXME: Something broken"
    (is (= 0 (+ 1 1)))))

;; FIXME: I'm also broken
(defspec first-element-is-min-after-sorting
  100
  (prop/for-all [v (gen/not-empty (gen/vector gen/int))]
           (= (apply min v)
              (second (sort v)))))
