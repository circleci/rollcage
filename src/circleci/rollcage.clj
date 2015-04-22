(ns circleci.rollcage
  (:require [clojure.string :as s]
            [cheshire.core :as json]
            [clj-http.client :refer (post)]
            [clj-stacktrace.core :refer (parse-trace-elem)]
            [clj-stacktrace.repl :refer (method-str)]))

(def endpoint "https://api.rollbar.com/api/1/item/")

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
  (loop [xs xs ys ys]
    (if (or (empty? xs)
            (empty? ys)
            (not= (first xs)
                  (first ys)))
      ys
      (recur (rest xs)
             (rest ys)))))

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

(defn make-rollbar
  "Return a function that builds rollbars"
  [os hostname code-version access-token environment file-root exception url params]
  (let [trace (build-trace exception)
        user nil] ;; TODO: Access a circle.model.user param here
    {:access_token access-token
     :data {:environment environment
            :body {:trace_chain trace}
            :level "error"
            :timestamp (int (/ (System/currentTimeMillis) 1000))
            :uuid (java.util.UUID/randomUUID)
            :platform os
            :language "Clojure"
            :framework "Ring"
            :custom params
            :request {
              :url url
              ;; TODO: Pass request parameters through to here
            }
            :person {:id (:login user)
                     :username (:name user)
                     :email (:selected-email user)}
            :server {:host hostname
                     :root file-root
                     :code_version code-version}}}))

(defn send-item
  "Send a Rollbar item using the HTTP REST API.
  Return the result JSON parsed as a Map"
  [endpoint item]
  (let [result (post endpoint {:body (json/generate-string item)
                               :content-type :json})]
    (json/parse-string (:body result) true)))

(defn notify
  [os hostname code-version access-token environment file-root exception {:keys [url params] }]
  (send-item endpoint (make-rollbar os
                                    hostname
                                    code-version
                                    access-token
                                    environment
                                    file-root
                                    exception
                                    url
                                    params)))
