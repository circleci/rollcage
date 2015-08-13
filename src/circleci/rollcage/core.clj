(ns circleci.rollcage.core
  (:require
    [clojure.string :as string]
    [cheshire.core :as json]
    [schema.core :as s]
    [clj-http.client :refer (post)]
    [clj-stacktrace.core :refer (parse-trace-elem)]
    [clj-stacktrace.repl :refer (method-str)])
  (:import
    [java.net InetAddress]
    [java.util UUID]))

(def endpoint "https://api.rollbar.com/api/1/item/")

(def Client {:access-token String
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
  (if (every? map? maps)
    (apply merge-with deep-merge maps)
    (last maps)))

(def Item (deep-merge Client
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
                  (.getHostName ^InetAddress (InetAddress/getLocalHost))])))

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
      (assoc-in [:data :body :trace_chain] (build-trace exception))
      (assoc-in [:data :level]             level)
      (assoc-in [:data :timestamp]         (timestamp))
      (assoc-in [:data :uuid]              (uuid))
      (assoc-in [:data :custom]            params)
      (assoc-in [:data :request :url]      url)))

(defn snake-case [kw]
  (string/replace (name kw) "-" "_"))

(defn send-item
  "Send a Rollbar item using the HTTP REST API.
  Return the result JSON parsed as a Map"
  [endpoint item]
  (let [result (post endpoint {:body (json/generate-string item {:key-fn snake-case})
                               :content-type :json})]
    (json/parse-string (:body result) true)))

(s/defn ^:private client* :- Client
  [access-token :- String
   {:keys [os hostname environment code-version file-root]
    :or {hostname (guess-hostname)
         os (guess-os)
         file-root  (guess-file-root)
         environment "production"}}]
  {:access-token access-token
   :data {:environment environment
          :platform    os
          :language    "Clojure"
          :framework   "Ring"
          :notifier    {:name "Rollcage"}
          :server      {:host hostname
                        :root file-root
                        :code_version code-version}}})

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
              (make-rollbar client level exception url params))))

(def critical (partial notify "critical"))
(def error    (partial notify "error"))
(def warning  (partial notify "warning"))
(def info     (partial notify "info"))
(def debug    (partial notify "debug"))
