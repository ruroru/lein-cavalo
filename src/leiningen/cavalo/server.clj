(ns leiningen.cavalo.server
  (:require
    [clojure.string :as str]
    [ring.adapter.jetty9 :as jetty]
    [ring.adapter.jetty9.websocket :as jetty-sock]
    [ring.websocket :as ringws])
  (:import (java.io File)
           (java.nio.file FileSystem FileSystems Paths StandardWatchEventKinds WatchKey WatchService)))
(def socket-set (atom #{}))


(defn get-reload-script [uri] (format "<script>\n(function() {\n  var ws = new WebSocket('ws://localhost:8080%s');\n  ws.onmessage = function (msg) {\n\t  console.log(msg.data)\n      if (msg.data === 'reload') {\n          window.location.reload();\n      }\n      \n  };\n})();\n</script>" uri))

(defn my-websocket-handler [req]
  (let [uri (:uri req)
        provided-subprotocols (:websocket-subprotocols req)]
    {:ring.websocket/listener {:on-open    (fn [socket]
                                             (do
                                               (println "Opening socket")
                                               (swap! socket-set (fn [old-socket-set]
                                                                   (conj old-socket-set socket)))
                                               (tap> [:ws :connect])))
                               :on-message (fn [socket message]
                                             (println message)
                                             (tap> [:ws :msg message])
                                             (ringws/send socket (str "echo: " message)))
                               :on-close   (fn [socket status-code reason]
                                             (do
                                               (println "closing")
                                               (swap! socket-set (fn [old-socket-map]
                                                                   (disj (get old-socket-map uri #{}) socket)))
                                               (tap> [:ws :close status-code reason])))
                               :on-pong    (fn [socket data]
                                             (tap> [:ws :pong]))
                               :on-ping    (fn [socket data]
                                             (tap> [:ws :ping])
                                             (ringws/pong socket))
                               :on-error   (fn [socket error]
                                             (.printStackTrace error)
                                             (tap> [:ws :error error]))}
     :ring.websocket/protocol (first provided-subprotocols)}))


(defn watch-dir [dirs-to-watch]
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



(defn notify-sockets [sockets]
  (doseq [socket sockets]
    (tap> [:ws :msg "message"])
    (ringws/send socket (str "reload"))
    (Thread/sleep 10)))

(defn- is-html? [response-body]
  (str/includes? response-body "</html>"))


(defn run-server [handler dirs-to-watch]
  (jetty/run-jetty (fn [req]
                     (if (jetty-sock/ws-upgrade-request? req)
                       (my-websocket-handler req)
                       (let [response (handler req)]
                         (if (is-html? (:body response))
                           {:status  (:status response)
                            :headers (:headers response)
                            :body    (str/replace (:body response) "</html>" (format "%s\n</html>" (get-reload-script (:uri req))))}
                           response))))
                   {:port             8080
                    :ws-max-idle-time (* 30 60 1000)
                    :join?            false})


  (let [watch-service ^WatchService (watch-dir dirs-to-watch)]
    (loop []
      (let [key (.poll watch-service)]
        (when key
          (println key)
          (.pollEvents ^WatchKey key)
          (notify-sockets @socket-set)
          (.reset key)))
      (Thread/sleep 10)
      (recur))))