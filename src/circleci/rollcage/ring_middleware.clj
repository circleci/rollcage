(ns circleci.rollcage.ring-middleware
  (:require [circleci.rollcage.core :as rollcage]))

(def ^:const request-and-wrap-parameter-keys
  "selector for keys provided by ring's request map and by ring's wrap-parameter middleware"
  [:http-method :query-string :query-params :form-params :params])

(defn wrap-rollbar [handler rollcage-client]
  (if-not rollcage-client
    handler
    (fn [req]
      (try
        (handler req)
        (catch Exception e
          (rollcage/error rollcage-client e {:url (:uri req)
                                             :params (select-keys req request-and-wrap-parameter-keys)})
          (throw e))))))
