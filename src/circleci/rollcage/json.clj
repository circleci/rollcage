(ns circleci.rollcage.json
  (:require
    [clojure.string :as string]
    [cheshire.core :as json])
  (:import
    [com.fasterxml.jackson.core JsonGenerationException]))

(defn- snake-case [kw]
  (string/replace (name kw) "-" "_"))

(defn encode
  [item]
  (json/generate-string item {:key-fn snake-case}))

(defn decode
  [^String string]
  (json/parse-string string true))
