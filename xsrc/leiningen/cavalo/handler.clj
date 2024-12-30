(ns leiningen.cavalo.handler
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