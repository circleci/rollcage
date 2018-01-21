(ns circleci.rollcage.core
  (:refer-clojure :exclude [send])
  (:require [clojure.string :as string]
            [clojure.tools.logging :as log]
            [cheshire.core :as json]
            [clj-http.client :refer (post)]
            [clj-stacktrace.core :refer (parse-trace-elem)]
            [clj-stacktrace.repl :refer (method-str)])
  (:import [java.util UUID]))

(def ^:private endpoint "https://api.rollbar.com/api/1/item/")

(comment def ^:private Client {:access-token (s/maybe String)
                       :data {:environment (s/maybe String)
                              :platform String
                              :language String
                              :framework String
                              :notifier {:name String}
                              :server {:host String
                                       :root String
                                       :code_version (s/maybe String)}}})

(defn- deep-merge
  "Like merge, but merges maps recursively."
  [& maps]
  (apply merge-with deep-merge maps))

(comment def ^:private Item (deep-merge Client
                                {:data {:body {:trace_chain s/Any}
                                        :level String
                                        :timestamp s/Int
                                        :uuid UUID
                                        :custom s/Any ;; TODO verify custom
                                        :request {:url (s/maybe String)}}}))

(defn- rollbar-frame
  "Convert a clj-stacktrace stack frame element to the format that the Rollbar
  REST API expects."
  [{:keys [file line] :as frame}]
  {:filename file
   :lineno line
   :method (method-str frame)})

(defn- drop-common-head
  "Return a vector containing a copy of ys with any common head with xs removed.
  (drop-common-head [1 2 3 foo bar baz] [1 2 3 cat hat mat])
  => [cat hat mat]"
  [xs ys]
  (if (or (empty? xs)
          (empty? ys)
          (not= (first xs)
                (first ys)))
    ys
    (recur (rest xs)
           (rest ys))))

(defn- drop-common-substacks
  "Remove the common substacks from trace so that each callstack in a chained
  exceptions does not have the same 20 line prelude"
  [trace]
  (loop [head (first trace)
         tail (rest trace)
         result [head]]
    (if (not-empty tail)
      (let [cleaned (drop-common-head (:frames head)
                                      (:frames (first tail)))]
        (recur (first tail)
               (rest tail)
               (conj result (assoc (first tail) :frames cleaned))))
      result)))

(defn- rollbar-trace
  "Create a 'trace' element for the Rollbar API from an exception."
  [^Throwable exception]
  {:frames (reverse (map (comp rollbar-frame parse-trace-elem)
                         (.getStackTrace exception)))
   :exception {:class (-> exception class str)
               :message  (.getMessage exception)}})

(defn- build-trace
  "Given an Exception, create a sequence of callstacks with one for each
  Exception in the cause-chain."
  [^Throwable exception]
  (drop-common-substacks
   (loop [exception exception
          result []]
     (if (nil? exception)
       result
       (recur (.getCause exception)
              (conj result (rollbar-trace exception)))))))

(defn- ^int timestamp []
  (int (/ (System/currentTimeMillis) 1000)))

(defn- ^UUID uuid []
  (UUID/randomUUID))

(defn ^:private make-rollbar
  "Build a map that matches the Rollbar API"
  [client
   level
   exception
   {:keys [custom request user]}]
  (-> client
      (assoc-in [:data :body :trace_chain] (build-trace exception))
      (assoc-in [:data :level]             level)
      (assoc-in [:data :timestamp]         (timestamp))
      (assoc-in [:data :uuid]              (uuid))
      (assoc-in [:data :custom]            custom)
      (assoc-in [:data :user]              user)
      (assoc-in [:data :request]           request)
      (update-in [:data :custom] merge (ex-data exception))))

(defn- snake-case [kw]
  (string/replace (name kw) "-" "_"))

(def ^:private rollbar-to-logging
  "A look-up table to map from Rollbar severity levels to tools.logging levels"
  {"critical" :fatal
   "error"    :error
   "warning"  :warn
   "info"     :info})

(defn- send-item-http
  "Send a Rollbar item using the HTTP REST API.
  Return the result JSON parsed as a Map"
  [item]
  ;; TODO - parse the JSON using clj-http.
  (let [result (post endpoint
                     {:body (json/generate-string item {:key-fn snake-case})
                      :content-type :json})]
    ;; Ensure that the result in valid JSON
    (json/parse-string (:body result) true)
    (log/log (rollbar-to-logging (:level item)) "Item <<uuid>> sent successfully")))

(defn- send [{:keys [access-token] :as item}]
  (when-not (string/blank? access-token)
    (send-item-http item)))

(defn notify
  [^String level client ^Throwable exception options]
  (let [item (make-rollbar client level exception options)]
    (try
      (send endpoint item)
      nil
      (catch Exception e
        (log/error e "Failed to send exception to Rollbar")
        nil))))
