(ns circleci.rollcage.ring-middleware
  (:require [circleci.rollcage.shell :as rollcage]))

(def ^:dynamic *request-scope* (atom {}))

(defn add-to-custom!
  "Add custom data m into the data that will be sent to rollbar."
  [custom]
  (swap! *request-scope* update :custom merge custom))

(defn add-to-request!
  "Add request data m into the data that will be sent to rollbar."
  [request]
  (swap! *request-scope* update :request merge request))

(defn add-to-user!
  "Add user data m into the data that will be sent to rollbar."
  [user]
  (swap! *request-scope* update :user merge user))

(defn wrap-rollbar [handler]
  (fn [request]
    (binding [*request-scope* (atom {:request {:url (:uri request)}})]
      (handler request)
      (catch Exception exception
        (rollcage/error exception (deref *request-scope*))
        (throw exception)))))
