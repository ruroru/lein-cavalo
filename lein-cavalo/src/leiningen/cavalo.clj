(ns leiningen.cavalo
  (:require [leiningen.core.eval :as lein-eval]))


(defn cavalo
  "I don't do a whole lot."
  [project & args]
  (println (lein-eval/eval-in-project project `(lein.cavalo.sidecar/start-server '~project) '(require 'lein.cavalo.sidecar)))
  (println (lein-eval/eval-in-project project `(lein.cavalo.sidecar/stop-server '~project) '(require 'lein.cavalo.sidecar)))

  (println "Hello, World1"))
