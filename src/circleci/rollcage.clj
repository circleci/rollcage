(ns circleci.rollcage
  (:require [clojure.string :refer (blank?)]
            [clojure.tools.logging :as log]
            [circleci.rollcage.core :as core]
            [circleci.rollcage.system :as system])
  (:import [clojure.lang Keyword]))

(defn- or-default
  "Helper function that will return the `value` or the default value.
  If `value` is a keyword, it is converted to a string."
  [value ^clojure.lang.IFn default-fn]
  (cond
    (instance? Keyword value) (name value)
    (blank? value) (default-fn)
    :else value))

(defn create-client
  "Create a client that can can be passed used to send notifications to Rollbar.
  The following options can be set: 

  :access-token
  The Rollbar project access token. This token must have permission to post server
  items. If no value is supplied, the token is loaded from ROLLBAR_ACCESS_TOKEN.
  If no access token is specified or ROLLBAR_ACCESS_TOKEN is blank, then items
  will not be sent.

  :os
  The name of the operating system running on the host. Defaults to the value
  of the `os.name` system property.

  :hostname
  The hostname of the host. Default value is the current hostname of the system.

  :file-root
  The path on disk where the filenames in stack traces are relative to. Defaults
  the current working directory, as reported by the `user.dir` system property.

  :environment
  The environment that the app is running is, for example `staging` or `dev`.
  Defaults to the `unknown` environment.

  :code-version
  A string, up to 40 characters, describing the version of the application code
  Rollbar understands these formats:
  - semantic version (i.e. '2.1.12')
  - integer (i.e. '45')
  - git SHA (i.e. '3da541559918a808c2402bba5012f6c60b27661c')
  The default value is the version loaded from the leiningen project, if any
  or nil.

  See https://rollbar.com/docs/api/items_post/

  More information on System Properties:
  https://docs.oracle.com/javase/tutorial/essential/environment/sysprop.html"
  [{:keys [access-token os hostname environment code-version file-root]}]
  (let [access-token (or-default access-token #(System/getenv "ROLLBAR_ACCESS_TOKEN"))
        os (or-default os system/os)
        environment (or-default environment #(System/getenv "ROLLBAR_ENVIRONMENT"))
        code-version (or-default code-version system/version)
        hostname (or-default hostname system/hostname)
        file-root (or-default file-root system/working-directory)]
    (printf "Creating client with token %sz%n" access-token)
    {:access-token access-token
     :data {:environment environment
            :platform    os
            :language    "Java"
            :framework   "Ring"
            :notifier    {:name "Rollcage"}
            :server      {:host hostname
                          :root file-root
                          :code_version code-version}}}))

(def ^{:dynamic true} *client* (create-client {}))

(defn notify [^String level ^Throwable exception options]
  (core/notify level *client* exception options))

(def critical (partial notify "critical"))
(def error    (partial notify "error"))
(def warning  (partial notify "warning"))
(def info     (partial notify "info"))
(def debug    (partial notify "debug"))

(defn report-uncaught-exception
  [exception thread]
  (critical exception {:params {:thread-name (.getName thread)}}))

(defn install-uncaught-exception-handler []
  (Thread/setDefaultUncaughtExceptionHandler
   (reify Thread$UncaughtExceptionHandler
     (uncaughtException [_ thread exception]
       (report-uncaught-exception exception thread)))))
