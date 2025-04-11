(ns leiningen.cavalo
  (:require
    [leiningen.core.eval :as lein-eval]))

(defmacro ^:private get-project-version1 []
  (let [project-data (slurp "project.clj")
        [_ _ version] (read-string project-data)]
    version))

(def ^:private version (fn []
               (get-project-version1)))

(defn ^:private start-server [project]
  (lein-eval/eval-in-project project
                             `(leiningen.carraco/start-server '~project)
                             '(require 'leiningen.carraco)))

(defn ^:private insert-dependency-to-project [project]
  (update-in project [:dependencies]
             conj ['org.clojars.jj/lein-carroca (version)]))

(defn cavalo
  [project & _]
  (-> (insert-dependency-to-project project)
      start-server))
