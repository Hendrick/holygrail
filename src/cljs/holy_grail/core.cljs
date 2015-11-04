(ns holy-grail.core
  (:require [reagent.core :as reagent]))

(defn hello-world-view []
  [:h1 "Hello World"])

(defn ^:export main []
  (reagent/render-component [hello-world-view] (.getElementById js/document "container")))
