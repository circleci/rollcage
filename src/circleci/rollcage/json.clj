(ns circleci.rollcage.json
  (:require
    [clojure.string :as string]
    [clojure.walk :refer (postwalk)]
    [cheshire.core :as json]
    [cheshire.generate :as gen]
    [cheshire.factory :as factory])
  (:import
   [java.io StringWriter
            Writer]
   [com.fasterxml.jackson.core JsonFactory
                               JsonGenerator
                               JsonGenerationException]))

(defn snake-case
  [kw]
  (string/replace (name kw) "-" "_"))

(defn backstop-encoder
  "Stringify an item if it's not possible to encode it normally. This should not
   be called for most encoding jobs, only when it would otherwise throw an
   exception."
  [item]
  (let [jg (.createGenerator
            ^JsonFactory (or factory/*json-factory* factory/json-factory)
            ^Writer (StringWriter.))]
    (try
     (gen/generate jg item factory/default-date-format nil snake-case)
     item
     (catch JsonGenerationException _
       (str item)))))

(defn encode
  "Convert an arbitrary object into a JSON string."
  [item]
  (try
   (json/generate-string item {:key-fn snake-case})
   (catch JsonGenerationException _
     (json/generate-string (postwalk backstop-encoder item) {:key-fn snake-case}))))

(defn decode
  "Decode a JSON string into an object."
  [^String string]
  (json/parse-string string true))
