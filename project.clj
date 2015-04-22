(defproject rollcage "0.1.0-SNAPSHOT"
  :description "A Clojure client for Rollbar"
  :url "http://github.com/circleci/rollcage"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [cheshire "5.4.0"]
                 [clj-http "1.1.0"]
                 [clj-stacktrace "0.2.8"]]
  :profiles {:dev {:dependencies [[bond "0.2.5"]]}})
