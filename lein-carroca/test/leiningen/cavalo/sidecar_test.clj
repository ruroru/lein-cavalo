(ns leiningen.cavalo.sidecar-test
  (:require [hato.client :as http-client]
            [hato.websocket :as ws]
            [clojure.test :refer [deftest is]]
            [leiningen.cavalo.sidecar :as carraco])
  (:import (java.io File)
           (java.nio.file Files)
           (java.nio.file.attribute FileAttribute)
           (org.apache.commons.io FileUtils)))

(deftest websocket-script-is-not-attached-to-non-html-pages
  (let [handler (fn [_]
                  {:status  200
                   :body    "body"
                   :headers {}})
        dirs-to-watch []
        project {:cavalo {:ring-handler  handler
                          :dirs-to-watch dirs-to-watch}}]

    (carraco/start-server project)
    (is (= (:body (http-client/get (format "http://localhost:8080/"))) "body"))
    (is (= (:status (http-client/get (format "http://localhost:8080/"))) 200))
    (is (= (dissoc (:headers (http-client/get (format "http://localhost:8080/"))) "server")
           {"transfer-encoding" "chunked"})
        (carraco/stop-server))))

(deftest websocket-script-is-attached-to-html-pages
  (let [handler (fn [_]
                  {:status  201
                   :body    "<html>body</html>"
                   :headers {"Content-Type" "text/html"}})
        dirs-to-watch []
        project {:cavalo {:ring-handler  handler
                          :dirs-to-watch dirs-to-watch}}]

    (carraco/start-server project)
    (is (= (:body (http-client/get (format "http://localhost:8080/")))
           "<html>body<script>\n(function() {\n  var ws = new WebSocket('ws://localhost:8080/');\n  ws.onmessage = function (msg) {\n\t  console.log(msg.data)\n      if (msg.data === 'reload') {\n          window.location.reload();\n      }\n      \n  };\n})();\n</script>\n</html>"))
    (carraco/stop-server)))


(deftest websocket-script-is-attached-to-html-pages
  (let [handler (fn [_]
                  {:status  201
                   :body    "<html>body</html>"
                   :headers {"Content-Type" "text/html"}})
        dirs-to-watch []
        project {:cavalo {:ring-handler  handler
                          :dirs-to-watch dirs-to-watch}}]

    (carraco/start-server project)
    (is (= (:body (http-client/get (format "http://localhost:8080/")))
           "<html>body<script>\n(function() {\n  var ws = new WebSocket('ws://localhost:8080/');\n  ws.onmessage = function (msg) {\n\t  console.log(msg.data)\n      if (msg.data === 'reload') {\n          window.location.reload();\n      }\n      \n  };\n})();\n</script>\n</html>"))
    (carraco/stop-server)))


(deftest websocket-test
  (let [watch-dir-path (format "%s/websocket-test" (System/getProperty "java.io.tmpdir"))
        watch-dir (File. watch-dir-path)
        watched-file (format "%s/file.html" watch-dir-path)
        handler (fn [_]
                  {:status  201
                   :body    "<html>body</html>"
                   :headers {"Content-Type" "text/html"}})
        dirs-to-watch [watch-dir-path]
        msg-count (atom 0)
        project {:cavalo {:ring-handler  handler
                          :dirs-to-watch dirs-to-watch}}]

    (when (.exists watch-dir)
      (FileUtils/forceDelete watch-dir))

    (Files/createDirectory (.toPath watch-dir) (into-array FileAttribute nil))
    (spit watched-file "<html>og-body</html>")

    (carraco/start-server project)

    (let [sleep? (atom true)
          ws @(ws/websocket "ws://localhost:8080/"
                            {:on-open    (fn [ws]
                                           (reset! sleep? true)
                                           (println "socket open:"))
                             :on-message (fn [ws msg last?]
                                           (is (= "reload" (.toString msg)))
                                           (swap! msg-count (fn [count]
                                                              (inc count)))
                                           (reset! sleep? false)
                                           (println "Received message:" msg))
                             :on-close   (fn [ws status reason]
                                           (reset! sleep? false)
                                           (println "WebSocket closed!"))})]


      (while @sleep?
        (spit watched-file "<html>new-body</html>")
        (Thread/sleep 1000))
      (ws/close! ws)
      (is (= 1 @msg-count)))
    (Thread/sleep 100)
    (carraco/stop-server)))
