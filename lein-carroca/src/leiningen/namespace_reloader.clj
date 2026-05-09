(ns leiningen.namespace-reloader
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as logger]))

(defn- clj-file? [^String file-path]
  (or (str/ends-with? file-path ".clj")
      (str/ends-with? file-path ".cljc")))

(defn- read-ns-symbol [^String file-path]
  (try
    (with-open [rdr (java.io.PushbackReader. (io/reader file-path))]
      (let [form (read rdr)]
        (when (and (list? form) (= 'ns (first form)))
          (second form))))
    (catch Exception _ nil)))

(defn reload-changed-files [changed-files]
  (doseq [file changed-files
          :when (clj-file? file)]
    (when-let [ns-sym (read-ns-symbol file)]
      (try
        (logger/info "Reloading namespace:" ns-sym)
        (load-file file)
        (catch Exception e
          (logger/warn "Failed to reload namespace" ns-sym (.getMessage e)))))))
