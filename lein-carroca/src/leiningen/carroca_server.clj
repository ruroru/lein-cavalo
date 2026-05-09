(ns leiningen.carroca-server
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]
    [clojure.tools.logging :as logger]
    [ring.adapter.jetty9 :as jetty]
    [ring.websocket :as ringws]))

(def ^:private sockets (atom #{}))

(defn- reload-script [port]
  (str "<script>" (str/replace (slurp (io/resource "js/reload.js")) "%s" (str port)) "</script>"))

(defn- websocket-handler [req]
  (let [uri (:uri req)
        provided-subprotocols (:websocket-subprotocols req)]

    {:ring.websocket/listener {:on-open    (fn [socket]
                                             (do
                                               (swap! sockets (fn [old-socket-set]
                                                                (conj old-socket-set socket)))
                                               (tap> [:ws :connect])))
                               :on-message (fn [socket message]
                                             (tap> [:ws :msg message])
                                             (ringws/send socket (str "echo: " message)))
                               :on-close   (fn [socket status-code reason]
                                             (do
                                               (swap! sockets disj socket)
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


(defn- is-text-html? [headers]
  (let [lower-case-headers (into {} (map (fn [[k v]] [(clojure.string/lower-case (name k)) v]) headers))]
    (= "text/html" (get lower-case-headers "content-type"))))


(def ^:private css-extensions #{".css"})
(def ^:private image-extensions #{".png" ".jpg" ".jpeg" ".gif" ".svg" ".webp" ".ico" ".bmp"})
(def ^:private js-extensions #{".js" ".mjs"})

(defn- file-extension [^String path]
  (let [dot-idx (.lastIndexOf path ".")]
    (when (pos? dot-idx)
      (str/lower-case (subs path dot-idx)))))

(defn- all-match-extensions? [changed-files extensions]
  (and (seq changed-files)
       (every? #(contains? extensions (file-extension %)) changed-files)))

(defn- detect-reload-type [changed-files]
  (cond
    (empty? changed-files) "reload"
    (all-match-extensions? changed-files css-extensions) "css-reload"
    (all-match-extensions? changed-files image-extensions) "img-reload"
    (all-match-extensions? changed-files js-extensions) "js-reload"
    :else "reload"))

(defn notify-clients
  ([] (notify-clients nil))
  ([changed-files]
   (let [message (detect-reload-type changed-files)]
     (doseq [socket @sockets]
       (tap> [:ws :msg message])
       (ringws/send socket message)))))

(defn- get-server-config [server-config]
  (merge {:port               8080
          :join?              false
          :notification-delay 200}
         server-config))

(defn- attach-reload-script [body port]
  (str/replace body "</html>"
               (format "%s\n</html>" (reload-script port))))

(defn run-server [server-config
                  handler]
  (let [config (get-server-config server-config)]
    (logger/info "Starting server on port " (:port config))
    (jetty/run-jetty (fn [req]
                       (if (ringws/upgrade-request? req)
                         (websocket-handler req)
                         (let [response (handler req)]
                           (if (is-text-html? (:headers response))
                             {:status  (:status response 404)
                              :headers (:headers response {})
                              :body    (attach-reload-script (:body response) (:port config))}
                             response))))
                     config)))