(ns circleci.rollcage.system
  "Functions that introspect the underlying system."
  (:require [clojure.string :as string]
    [clojure.java.io :as io]
    [clojure.edn :as edn])
  (:import [java.net InetAddress UnknownHostException]))

(defn version
  "Get the version of the current leiningen project."
  []
    (some-> 
      (io/resource "project.clj")
      (slurp)
      (edn/read-string)
      (nth 2)))

(defn os
  "Get the name of the current OS"
  []
  (System/getProperty "os.name"))

(defn hostname
  "Get the system hostname."
  []
  (first (filter
          (complement string/blank?)
          [(System/getenv "HOSTNAME") ;; Unix
           (System/getenv "COMPUTERNAME") ;; Windows
           (try (.getHostName ^InetAddress (InetAddress/getLocalHost))
                (catch UnknownHostException _ nil))])))

(defn working-directory
  "Get the current working directory."
  []
  (System/getProperty "user.dir"))


  (version)
