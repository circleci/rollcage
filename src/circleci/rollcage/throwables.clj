(ns circleci.rollcage.throwables
  {:no-doc true})

(def default-exception-key "__exception")

(defn- cause-seq
  [^Throwable t]
  (take-while some?
              (iterate (fn [^Throwable e]
                         (and e
                              (.getCause e))) t)))

(defn merge-ex-data
  [^Throwable ex]
  (reduce merge {} (map ex-data (cause-seq ex))))

(defn enriched-ex-data
  [exception-name-field ^Throwable ex]
  (assoc (ex-data ex)
         exception-name-field
         (ex-message ex)))

(defn index-ex-data
  [exception-name-field ^Throwable ex]
  (let [exes     (reverse (cause-seq ex))
        quantity (count exes)]
    (if (= 1 quantity)
      (ex-data (first exes))
      (let [indexes      (range 0 quantity)
            rich-ex-data (map (partial enriched-ex-data exception-name-field)
                              exes)]
        (zipmap indexes rich-ex-data)))))

(defn select-exception-key
  [indexed-ex-data]
  (let [ekey (if-not (boolean? indexed-ex-data)
              (str indexed-ex-data)
              default-exception-key)]
    (if-not (seq ekey)
      default-exception-key
      ekey)))

(defn merged-ex-data
  [{:keys [indexed-ex-data] :as client}
   ^Throwable ex]
  (if-not indexed-ex-data
    (merge-ex-data ex)
    (index-ex-data (select-exception-key indexed-ex-data)
                   ex)))
