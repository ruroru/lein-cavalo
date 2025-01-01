(ns lein.cavalo.sidecar
  (:require
    [clojure.string :as string]
    [leiningen.cavalo.server :as server]))


(defn- require? [symbol]
  (try
    (require symbol)
    true
    (catch Exception e
      false)))


(defn- illegal-argument [& error-parts]
  (throw (IllegalArgumentException. (string/join " " error-parts))))

(defn- resolve-handler [handler]
  (when handler
    (if (fn? handler)
      handler
      (let [h (symbol handler)]
        (if-let [ns (namespace h)]
          (if (require? (symbol ns))
            (if-let [handler-var (resolve h)]
              handler-var
              (illegal-argument "unable to resolve handler var" h))
            (illegal-argument "unable to require the namespace of the handler" h))
          (illegal-argument "unable to resolve the namespace of the handler" h))))))


(defn start-server
  "I don't do a whole lot."
  [project]
  (let [dirs-to-watch (:dirs-to-watch (:cavalo project))
        handler-function-symbol (:ring-handler (:cavalo project))]
    (println "starting server")

    (server/run-server (resolve-handler handler-function-symbol) dirs-to-watch)))


(defn stop-server
  "I don't do a whole lot."
  []
  (println "stopping server"))
