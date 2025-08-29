(ns leiningen.carraco
  (:require
    [clojure.string :as string]
    [leiningen.carroca-server :as server]
    [leiningen.directory-watcher :as dir-watcher])
  (:import (org.eclipse.jetty.server Server)))
(def  ^:private server (atom nil))

(defn- require? [symbol]
  (try
    (require symbol)
    true
    (catch Exception _
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
  "Starts server with a given handler"
  [project]
  (let [dirs-to-watch (:dirs-to-watch (:cavalo project))
        server-config (:server-config (:cavalo project))
        handler-function-symbol (:ring-handler (:cavalo project))
        notification-delay (:notification-delay server-config 200)
        ]
    (dir-watcher/watch dirs-to-watch (fn [] (server/notify-clients)) notification-delay)

    (let [local-server (server/run-server server-config (resolve-handler handler-function-symbol))]
      (reset! server local-server))))


(defn stop-server
  "Stops server"
  []
  (dir-watcher/stop)
  (.stop ^Server @server))
