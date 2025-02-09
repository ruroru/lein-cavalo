(ns leiningen.cavalo.server
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]
    [clojure.tools.logging :as logger]
    [ring.adapter.jetty9 :as jetty]
    [ring.websocket :as ringws])
  (:import (java.nio.file FileSystem FileSystems Paths StandardWatchEventKinds WatchKey WatchService)))

(def sockets (atom #{}))

(defn- get-reload-script [port]
  (str "<script>" (format (slurp (io/resource "js/reload.js")) port) "</script>"))

(defn- my-websocket-handler [req]
  (let [uri (:uri req)
        provided-subprotocols (:websocket-subprotocols req)]
    {:ring.websocket/listener {:on-open    (fn [socket]
                                             (do
                                               (println "Opening socket")
                                               (swap! sockets (fn [old-socket-set]
                                                                (conj old-socket-set socket)))
                                               (tap> [:ws :connect])))
                               :on-message (fn [socket message]
                                             (println message)
                                             (tap> [:ws :msg message])
                                             (ringws/send socket (str "echo: " message)))
                               :on-close   (fn [socket status-code reason]
                                             (do
                                               (println "closing")
                                               (swap! sockets (fn [old-socket-map]
                                                                (disj (get old-socket-map uri #{}) socket)))
                                               (tap> [:ws :close status-code reason])))
                               :on-pong    (fn [_socket _data]
                                             (tap> [:ws :pong]))
                               :on-ping    (fn [socket _data]
                                             (tap> [:ws :ping])
                                             (ringws/pong socket))
                               :on-error   (fn [_socket error]
                                             (.printStackTrace error)
                                             (tap> [:ws :error error]))}
     :ring.websocket/protocol (first provided-subprotocols)}))


(defn- watch-dirs [dirs-to-watch]
  (logger/debug "Setting up dirs to watch" dirs-to-watch)

  (let [default-fs ^FileSystem (FileSystems/getDefault)
        watch-service ^WatchService (.newWatchService default-fs)]
    (let [paths (map (fn [item]
                       (Paths/get ^String (str item) (into-array String nil)))
                     dirs-to-watch)]
      (doseq [path paths]
        (.register path watch-service (into-array
                                        (list
                                          StandardWatchEventKinds/ENTRY_DELETE
                                          StandardWatchEventKinds/ENTRY_MODIFY
                                          StandardWatchEventKinds/ENTRY_CREATE)))))
    watch-service))



(defn- notify-clients [sockets notification-delay]
  (Thread/sleep notification-delay)
  (doseq [socket sockets]
    (tap> [:ws :msg "message"])
    (ringws/send socket (str "reload"))))

(defn- is-html? [response-body]
  (try
    (str/includes? response-body "</html>")
    (catch Exception e
      false)))




(defn get-server-config [server-config]

  (merge {:port               8080
          :join?              false
          :daemon?            true
          :notification-delay 200}
         server-config))

(defn run-server [server-config
                  handler
                  dirs-to-watch]



  (let [new-server-config (get-server-config server-config)]
    (logger/info "Setting up watch dir.")

    (.start (Thread. (fn []
                       (let [watch-service ^WatchService (watch-dirs dirs-to-watch)]
                         (loop []
                           (let [key (.poll watch-service)]
                             (when key
                               (.pollEvents ^WatchKey key)
                               (notify-clients @sockets (:notification-delay new-server-config))
                               (.reset key)))
                           (recur))))))




    (logger/info "Starting server on port " (:port new-server-config))
    (jetty/run-jetty (fn [req]
                       (if (ringws/upgrade-request? req)
                         (my-websocket-handler req)
                         (let [response (handler req)]
                           (if (is-html? (:body response))
                             {:status  (:status response 404)
                              :headers (:headers response {})
                              :body    (str/replace (:body response) "</html>"
                                                    (format "%s\n</html>" (get-reload-script (:port new-server-config))))}
                             response))))
                     new-server-config))
  )