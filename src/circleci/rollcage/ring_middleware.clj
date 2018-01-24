(ns circleci.rollcage.ring-middleware
  (:require
   [clojure.string :as str]
   [circleci.rollcage :as rollcage]))

(def ^:dynamic *request-scope* (atom {}))

(defn add-to-custom!
  "Add custom data m into the data that will be sent to rollbar."
  [fields]
  (swap! *request-scope* update :custom merge fields))

(defn add-to-request!
  "Add request data m into the data that will be sent to rollbar."
  [fields]
  (swap! *request-scope* update :request merge fields))

(defn add-to-person!
  "Add user data m into the data that will be sent to rollbar."
  [fields]
  (swap! *request-scope* update :person merge fields))

(defn wrap-rollbar [handler]
  (fn [{:keys [uri request-method remote-addr] :as request}]
    (let [req {:url uri
               :user-ip remote-addr
               :method (some-> request-method (name) (str/upper-case))}]
      (binding [*request-scope* (atom {:request req})]
        (handler request)
        (catch Exception exception
          (rollcage/error exception (deref *request-scope*))
          (throw exception))))))
