(ns circleci.rollcage.throwables)

(defn- cause-seq
  [^Throwable t]
  (take-while some?
              (iterate (fn [^Throwable e]
                         (and e
                              (.getCause e))) t)))

(defn merged-ex-data
  [^Throwable ex]
  (reduce merge {} (map ex-data (cause-seq ex))))
