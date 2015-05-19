(ns holy-grail.core
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]))

(def app-state (atom {}))

(defn form
  "Om component for new paste-link"
  [data owner]
  (reify
    om/IDisplayName
    (display-name [this]
      "paste-link")
    om/IRender
    (render [_]
      (dom/form #js {:className "input-form" :method "POST" :action "/action"}
                (dom/label #js {:htmlFor "answer"} "Question:")
                (dom/input #js {:type "text" :name "answer" :id "answer"})
                (dom/input #js {:type "submit" :value "Submit"})))))

(defn app [data owner]
  (reify
    om/IDisplayName
    (display-name [this]
      "app")
    om/IRender
    (render [this]
      (om/build form data))))

(om/root app app-state
         {:target (.getElementById js/document "container")})
