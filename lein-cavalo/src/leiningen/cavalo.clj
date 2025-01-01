(ns leiningen.cavalo
  (:require
    [clojure.tools.logging :as logger]
    [leiningen.core.eval :as lein-eval]))

(defmacro get-project-version [] "1.0.0-SNAPSHOT")


(defn start-server [project]
  (when (logger/enabled? :debug)
    (logger/debug project))

  (lein-eval/eval-in-project project
                             `(lein.cavalo.sidecar/start-server '~project)
                             '(require 'lein.cavalo.sidecar)))

(defn insert-dependency-to-project [project]
  (update-in project [:dependencies]
             conj ['org.clojars.jj/lein-carroca (get-project-version)]))

(defn cavalo
  [project & _]
  (-> (insert-dependency-to-project project)
      start-server))
