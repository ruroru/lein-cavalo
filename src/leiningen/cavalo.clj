(ns leiningen.cavalo
  (:require [leiningen.cavalo.server :as server]))

(defn cavalo [project & args]
  (let [handler-function-symbol (:ring-handler (:cavalo project))]
    (if handler-function-symbol
      (let [handler-function (resolve handler-function-symbol)]
        (server/run-server (handler-function) (:watch-dirs (:cavalo project))))
      (println "handler function is not defined"))))