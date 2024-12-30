(ns leiningen.cavalo
  (:require [leiningen.cavalo.server :as server])
  (:import (java.io File)))

(defn handler []
  (fn [req]
    (let [uri (:uri req)
          file (File. (format "test/resources%s" uri))]
      (if (.exists file)
        {:status 200
         :body   (slurp file)}
        {:status 404
         :body   "not found"}))))

(defn cavalo [project & args]
  (let [handler-function-symbol (:ring-handler (:cavalo project))]
    (if handler-function-symbol
      (let [handler-function (resolve handler-function-symbol)]
        (server/run-server (handler-function) (:watch-dirs (:cavalo project))))
      (println "handler function is not defined"))))