(ns circleci.rollcage.core
  (:require
    [clojure.string :as string]
    [clojure.tools.logging :as logging]
    [cheshire.core :as json]
    [schema.core :as s]
    [clj-http.client :refer (post)]
    [clj-stacktrace.core :refer (parse-trace-elem)]
    [clj-stacktrace.repl :refer (method-str)])
  (:import
    [java.net InetAddress UnknownHostException]
    [java.util UUID]))

(def ^:private endpoint "https://api.rollbar.com/api/1/item/")

(def ^:private Client {:access-token (s/maybe String)
                       :result-fn clojure.lang.IFn
                       :send-fn clojure.lang.IFn
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

(def ^:private Item (deep-merge (dissoc Client :result-fn :send-fn)
                      {:data {:body {:trace_chain s/Any}
                              :level String
                              :timestamp s/Int
                              :uuid UUID
                              :custom s/Any ;; TODO verify custom
                              :request {:url (s/maybe String)}}}))


(defn- guess-os []
  (System/getProperty "os.name"))

(defn- guess-hostname []
  (first (filter (complement string/blank?)
                 [(System/getenv "HOSTNAME") ;; Unix
                  (System/getenv "COMPUTERNAME") ;; Windows
                  (try (.getHostName ^InetAddress (InetAddress/getLocalHost))
                       (catch UnknownHostException _ nil))])))

(defn- guess-file-root []
  (System/getProperty "user.dir"))

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

(defn- build-trace
  "Given an Exception, create a sequence of callstacks with one for each
  Exception in the cause-chain."
  [^Throwable exception]
  (drop-common-substacks
    (loop [exception exception
           result []]
      (if (nil? exception) result
        (let [elem {:frames (reverse (map (comp rollbar-frame parse-trace-elem)
                                          (.getStackTrace exception)))
                    :exception {:class (-> exception class str)
                                :message  (.getMessage exception)}}]
          (recur (.getCause exception)
                 (conj result elem)))))))

(defn- ^int timestamp []
  (int (/ (System/currentTimeMillis) 1000)))

(defn- ^UUID uuid []
  (UUID/randomUUID))

(s/defn ^:private make-rollbar :- Item
  "Build a map that matches the Rollbar API"
  [client :- Client
   level  :- String
   exception :- Throwable
   url :- (s/maybe String)
   custom :- (s/maybe {s/Any s/Any})]
  ;; TODO: Pass request parameters through to here
  ;; TODO: add person here
  (-> client
      (dissoc :result-fn :send-fn)
      (assoc-in [:data :body :trace_chain] (build-trace exception))
      (assoc-in [:data :level]             level)
      (assoc-in [:data :timestamp]         (timestamp))
      (assoc-in [:data :uuid]              (uuid))
      (assoc-in [:data :custom]            custom)
      (assoc-in [:data :request :url]      url)))

(defn- snake-case [kw]
  (string/replace (name kw) "-" "_"))

(def ^:private rollbar-to-logging
  "A look-up table to map from Rollbar severity levels to tools.logging levels"
  {"critical" :fatal
   "error"    :error
   "warning"  :warn
   "info"     :info})

(defn- send-item-null
  [^String endpoint ^Throwable exception item]
  (logging/log (rollbar-to-logging (get-in item [:data :level]))
               exception
               "No Rollbar token configured. Not reporting exception.")
  {:err 0
   :result {:uuid (-> item
                      (get-in [:data :uuid])
                      (str))}})

(defn- send-item-http
  "Send a Rollbar item using the HTTP REST API.
  Return the result JSON parsed as a Map"
  [^String endpoint ^Throwable exception item]
  (logging/log (rollbar-to-logging (get-in item [:data :level]))
               exception
               "Sending exception to Rollbar")
  (let [result (post endpoint
                     {:body (json/generate-string item {:key-fn snake-case})
                      :content-type :json})]
    (json/parse-string (:body result) true)))

(s/defn ^:private client* :- Client
  [access-token :- (s/maybe String)
   {:keys [os hostname environment code-version file-root result-fn]
    :or {environment "production"}}]
  (let [os        (or os (guess-os))
        hostname  (or hostname (guess-hostname))
        file-root (or file-root (guess-file-root))
        result-fn (or result-fn (constantly nil))]
    {:access-token access-token
     :result-fn result-fn
     :send-fn (if (string/blank? access-token)
                send-item-null
                send-item-http)
     :data {:environment (name environment)
            :platform    (name os)
            :language    "Clojure"
            :framework   "Ring"
            :notifier    {:name "Rollcage"}
            :server      {:host hostname
                          :root file-root
                          :code_version code-version}}}))

(defn client
  "Create a client that can can be passed used to send notifications to Rollbar.
  The following options can be set: 

  :os
  The name of the operating system running on the host. Defaults to the value
  of the `os.name` system property.

  :hostname
  The hostname of the host.

  :file-root
  The path on disk where the filenames in stack traces are relative to. Defaults
  the current working directory, as reported by the `user.dir` system property.

  :environment
  The environment that the app is running is, for example `staging` or `dev`.
  Defaults to `production`.

  :code-version
  A string, up to 40 characters, describing the version of the application code
  Rollbar understands these formats:
  - semantic version (i.e. '2.1.12')
  - integer (i.e. '45')
  - git SHA (i.e. '3da541559918a808c2402bba5012f6c60b27661c')
  There is no default value.

  :result-fn
  An function that will be called after each exception is sent to Rollbar.
  The function will be passed 2 parameters:
  - The Throwable that was being reported
  - A map with the result of sending the exception to Rollbar. This map will
    have the following keys:
      :err     - an integer, 1 if there was an error sending the exception to
                 Rollbar, 0 otherwise.
      :message - A human-readable message describing the error.

  See https://rollbar.com/docs/api/items_post/

  More information on System Properties:
  https://docs.oracle.com/javase/tutorial/essential/environment/sysprop.html"
  ([access-token]
   (client access-token {}))
  ([access-token options]
   (client* access-token options)))

(defn notify
  ([^String level client ^Throwable exception]
   (notify level client exception {}))
  ([^String level {:keys [result-fn  send-fn] :as client} ^Throwable exception {:keys [url params]}]
   (let [log-level (rollbar-to-logging level)
         params (merge params (ex-data exception))
         item (make-rollbar client level exception url params)
         result (try
                  (send-fn endpoint exception item)
                  (catch Exception e
                    ;; Return an error that matches the shape of the Rollbar API
                    ;; with an added :exception key
                    {:err 1
                     :exception e
                     :message (.getMessage e)}))]
     (result-fn exception result)
     result)))

(defn- report-uncaught-exception
  [level client exception thread]
  (notify level client exception
          {:params {:thread-name (.getName thread)}}))

(defn setup-uncaught-exception-handler
  "Setup handler to report all uncaught exceptions
  to rollbar."
  [client]
  (Thread/setDefaultUncaughtExceptionHandler
    (reify Thread$UncaughtExceptionHandler
      (uncaughtException [_ thread ex]
        (report-uncaught-exception "critical" client ex thread)))))

(def critical (partial notify "critical"))
(def error    (partial notify "error"))
(def warning  (partial notify "warning"))
(def info     (partial notify "info"))
(def debug    (partial notify "debug"))
