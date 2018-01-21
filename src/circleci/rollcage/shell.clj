(ns circleci.rollcage.shell
  (:require [clojure.string :refer (blank?)]
            [circleci.rollcage.core :as core]
            [circleci.rollcage.system :as system]))

(defn or-default [value ^clojure.lang.IFn default-fn]
  (cond
    (instance? clojure.lang.Keyword value) (name value)
    (blank? value) (default-fn)
    :else value))

(defn create-client
  "Create a client that can can be passed used to send notifications to Rollbar.
  The following options can be set: 

  :os
  The name of the operating system running on the host. Defaults to the value
  of the `os.name` system property.

  :hostname
  The hostname of the host.

  :file-root
  The path on disk where the filenames in stack traces are relative to. Defaults
  the current working directory, as reported by the `user.dir` system property.

  :environment
  The environment that the app is running is, for example `staging` or `dev`.
  Defaults to `production`.

  :code-version
  A string, up to 40 characters, describing the version of the application code
  Rollbar understands these formats:
  - semantic version (i.e. '2.1.12')
  - integer (i.e. '45')
  - git SHA (i.e. '3da541559918a808c2402bba5012f6c60b27661c')
  There is no default value.

  See https://rollbar.com/docs/api/items_post/

  More information on System Properties:
  https://docs.oracle.com/javase/tutorial/essential/environment/sysprop.html"
  [{:keys [access-token os hostname environment code-version file-root]}]
  (let [access-token (or-default access-token #(System/getenv "ROLLBAR_ACCESS_TOKEN"))
        environment (or-default environment (constantly "production")) ;; TODO - does Rollbar API have a default?
        os (or-default os system/os)
        hostname (or-default hostname system/hostname)
        file-root (or-default file-root system/working-directory)]
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
  (core/notify level *client* options))

(def critical (partial notify "critical"))
(def error    (partial notify "error"))
(def warning  (partial notify "warning"))
(def info     (partial notify "info"))
(def debug    (partial notify "debug"))

(defn- report-uncaught-exception
  [exception thread]
  (critical *client* exception
            {:params {:thread-name (.getName thread)}}))

(defn install-uncaught-exception-handler []
 (Thread/setDefaultUncaughtExceptionHandler
 (reify Thread$UncaughtExceptionHandler
   (uncaughtException [_ thread exception]
     (report-uncaught-exception exception thread)))))
