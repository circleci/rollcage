(ns circleci.rollcage.core
  (:require
    [circleci.rollcage.http :as http]
    [clojure.string :as string]
    [cheshire.core :as json]
    [schema.core :as s]
    [clj-stacktrace.core :refer (parse-trace-elem)]
    [clj-stacktrace.repl :refer (method-str)])
  (:import
    [java.net InetAddress UnknownHostException]
    [java.util UUID]))

(def endpoint "https://api.rollbar.com/api/1/item/")

(def Person {:id String
             :username (s/maybe String)
             :email (s/maybe String)})

(def Request {(s/optional-key :url) String
              (s/optional-key :method) String
              (s/optional-key :headers) {s/Any s/Any}
              (s/optional-key :params) {s/Any s/Any}
              (s/optional-key :GET) {s/Any s/Any}
              (s/optional-key :POST) {s/Any s/Any}
              (s/optional-key :user_ip) String
              (s/optional-key :query_string) String
              (s/optional-key :body) String})

(def Client {:access-token String
             :http-client  (s/protocol http/HttpClient)
             :data {:environment (s/maybe String)
                    :platform String
                    :language String
                    :framework String
                    :notifier {:name String}
                    :server {:host String
                             :root String
                             :code_version (s/maybe String)}}})

(def DataFromParams {(s/optional-key :custom)    {s/Any s/Any}
                     (s/optional-key :request)   Request
                     (s/optional-key :person)    Person
                     (s/optional-key :context)   String
                     (s/optional-key :framework) String})

(defn- deep-merge
  "Like merge, but merges maps recursively."
  [& maps]
  (apply merge-with deep-merge maps))

(def Item (deep-merge Client
                      {:data {:body {:trace_chain s/Any}
                              :level String
                              :timestamp s/Int
                              :uuid UUID
                              (s/optional-key :custom) s/Any ;; TODO verify custom
                              (s/optional-key :person) Person
                              (s/optional-key :context) String
                              (s/optional-key :request) Request}}))

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

(s/defn ^:private params->data :- DataFromParams
  "Extract data for the Rollbar API from params"
  [params :- (s/maybe s/Any)]
  (let [param-keys [:request :person :context :framework]
        custom     (apply dissoc params param-keys)]
    (cond-> (select-keys params param-keys)
      (not-empty custom) (assoc :custom custom))))

(s/defn make-rollbar :- Item
  "Build a map that matches the Rollbar API"
  [client :- Client
   level  :- String
   exception :- Throwable
   url :- (s/maybe String)
   params :- (s/maybe {s/Any s/Any})]
  (let [data (cond-> {:body      {:trace_chain (build-trace exception)}
                      :level     level
                      :timestamp (timestamp)
                      :uuid      (uuid)}
               true (merge (params->data params))
               url  (assoc-in [:request :url] url))]
    (update-in client [:data] merge data)))

(defn snake-case [kw]
  (string/replace (name kw) "-" "_"))

(s/defn ^:private client* :- Client
  [access-token :- String
   {:keys [os hostname environment code-version framework file-root http-client]
    :or {environment "production"
         framework   "Ring"
         os          (guess-os)
         file-root   (guess-file-root)
         hostname    (guess-hostname)
         http-client (http/make-default-http-client)}}]
  {:access-token access-token
   :http-client http-client
   :data {:environment (name environment)
          :platform    (name os)
          :language    "Clojure"
          :framework   framework
          :notifier    {:name "Rollcage"}
          :server      {:host hostname
                        :root file-root
                        :code_version code-version}}})

(defn client
  ([access-token]
   (client access-token {}))
  ([access-token options]
   (client* access-token options)))

(defn send-item
  [http-client endpoint item]
  (let [body (json/generate-string item {:key-fn snake-case})
        result (http/post http-client endpoint body)]
    (json/parse-string result true)))

(defn notify
  ([level client exception]
   (notify level client exception {}))
  ([level {:keys [http-client] :as client} exception {:keys [url params]}]
   (send-item http-client
              endpoint
              (make-rollbar client level exception url params))))


(defn report-uncaught-exception
  [level client exception thread]
  (let [custom-data {:thread (.getName thread)}]
    (send-item endpoint
               (make-rollbar client level exception nil custom-data))))

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
