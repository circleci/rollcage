(ns circleci.rollcage.core
  (:require
    [clojure.string :as string]
    [cheshire.core :as json]
    [schema.core :as s]
    [clj-http.client :refer (post)]
    [clj-stacktrace.core :refer (parse-trace-elem)]
    [clj-stacktrace.repl :refer (method-str)])
  (:import
    [java.net InetAddress UnknownHostException]
    [java.util UUID]))

(def endpoint "https://api.rollbar.com/api/1/item/")

(def Client {:access-token String
             :result-fn clojure.lang.IFn
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

(def Item (deep-merge (dissoc Client :result-fn)
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

(defn drop-common-head
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

(defn drop-common-substacks
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

(defn build-trace
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

(s/defn make-rollbar :- Item
  "Build a map that matches the Rollbar API"
  [client :- Client
   level  :- String
   exception :- Throwable
   url :- (s/maybe String)
   params :- (s/maybe s/Any)]
  ;; TODO: Pass request parameters through to here
  ;; TODO: add person here
  (-> client
      (dissoc :result-fn)
      (assoc-in [:data :body :trace_chain] (build-trace exception))
      (assoc-in [:data :level]             level)
      (assoc-in [:data :timestamp]         (timestamp))
      (assoc-in [:data :uuid]              (uuid))
      (assoc-in [:data :custom]            params)
      (assoc-in [:data :request :url]      url)))

(defn snake-case [kw]
  (string/replace (name kw) "-" "_"))

(defn- send-item*
  [endpoint item]
  (try
    (let [result (post endpoint
                       {:body (json/generate-string item {:key-fn snake-case})
                        :content-type :json})]
      (json/parse-string (:body result) true))
    (catch Exception e
      ;; Return an error that matches the shape of the Rollbar API
      ;; with an added :exception key
      {:err 1
       :exception e
       :message (.getMessage e)})))

(defn send-item
  "Send a Rollbar item using the HTTP REST API.
  Return the result JSON parsed as a Map"
  [endpoint item result-fn]
  (let [result (send-item* endpoint item)]
    (result-fn result)
    result))

(s/defn ^:private client* :- Client
  [access-token :- String
   {:keys [os hostname environment code-version file-root result-fn]
    :or {environment "production"}}]
  (let [os        (or os (guess-os))
        hostname  (or hostname (guess-hostname))
        file-root (or file-root (guess-file-root))
        result-fn (or result-fn (constantly nil))]
    {:access-token access-token
     :result-fn result-fn
     :data {:environment (name environment)
            :platform    (name os)
            :language    "Clojure"
            :framework   "Ring"
            :notifier    {:name "Rollcage"}
            :server      {:host hostname
                          :root file-root
                          :code_version code-version}}}))

(defn client
  ([access-token]
   (client access-token {}))
  ([access-token options]
   (client* access-token options)))

(defn notify
  ([level client exception]
   (notify level client exception {}))
  ([level client exception {:keys [url params]}]
   (send-item endpoint
              (make-rollbar client level exception url params)
              (:result-fn client))))

(defn report-uncaught-exception
  [level client exception thread]
  (notify level client exception
          {:params {:thread (.getName thread)}}))

(defn setup-uncaught-exception-handler
  "Setup handler to report all uncaught exceptions
   to rollbar."
  ([client]
   (setup-uncaught-exception-handler client "error"))
  ([client level]
   (Thread/setDefaultUncaughtExceptionHandler
     (reify Thread$UncaughtExceptionHandler
       (uncaughtException [_ thread ex]
         (report-uncaught-exception level client ex thread))))))

(def critical (partial notify "critical"))
(def error    (partial notify "error"))
(def warning  (partial notify "warning"))
(def info     (partial notify "info"))
(def debug    (partial notify "debug"))
