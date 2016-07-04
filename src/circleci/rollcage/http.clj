(ns circleci.rollcage.http
  (:require [clj-http.client :as http]))

(defprotocol HttpClient
  (post [this url json]
    "Makes a post request to Rollbar API and returns response body"))

(defrecord CljHttpClient []
  HttpClient
  (post [this url json]
    (-> (http/post url {:body json :content-type :json}) :body)))

(defn make-default-http-client
  "Makes clj-http client"
  []
  (->CljHttpClient))
