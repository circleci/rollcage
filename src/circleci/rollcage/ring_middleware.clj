(ns circleci.rollcage.ring-middleware
  (:require [circleci.rollcage.core :as rollcage]))

(defn wrap-rollbar [handler rollcage-client]
  (if-not rollcage-client
    handler
    (fn [req]
      (try
        (handler req)
        (catch Exception e
          (rollcage/error rollcage-client e {:url (:uri req)
                                             :params (dissoc req :uri)})
          (throw e))))))
