(ns leiningen.cavalo.sidecar-test
  (:require [hato.client :as http-client]
            [hato.websocket :as ws]
            [clojure.test :refer [deftest is testing]]
            [leiningen.cavalo.sidecar :as carraco])
  (:import (java.io File)
           (java.nio.file Files)
           (java.nio.file.attribute FileAttribute)
           (org.apache.commons.io FileUtils)))

(def handler (fn [req]
               (if (= "/not-html" (:uri req))
                 {:status  200
                  :body    "body"
                  :headers {}
                  }
                 {:status  201
                  :body    "<html>body</html>"
                  :headers {"Content-Type" "text/html"}})))



(deftest test-default-port
  (let [dirs-to-watch []
        project {:cavalo {:ring-handler  handler
                          :dirs-to-watch dirs-to-watch}}]
    (carraco/start-server project)
    (testing "non html responses"
      (let [response (http-client/get (format "http://localhost:8080/not-html"))]
        (is (= (:body response) "body"))
        (is (= (:status response) 200))
        (is (= (dissoc (:headers response) "server")))))

    (testing "html responses witha  correct webscocket script attached to html page."
      (let [response (http-client/get (format "http://localhost:8080/"))]
        (is (= (:body response) "<html>body<script>new WebSocket(\"ws://localhost:8080/\").onmessage=function(o){\"reload\"===o.data&&window.location.reload()};</script\n</html>"))
        (is (= (:status response) 201))
        (is (= (dissoc (:headers response) "server")))))
    (carraco/stop-server)))

(deftest test-custom-port
  (let [dirs-to-watch []
        project {:cavalo {:server-config {:port 3000}
                          :ring-handler  handler
                          :dirs-to-watch dirs-to-watch}}]

    (carraco/start-server project)
    (testing "non html responses"
      (let [response (http-client/get (format "http://localhost:3000/not-html"))]
        (is (= (:body response) "body"))
        (is (= (:status response) 200))
        (is (= (dissoc (:headers response) "server")))))

    (testing "html responses witha  correct webscocket script attached to html page."
      (let [response (http-client/get (format "http://localhost:3000/"))]
        (is (= (:body response) "<html>body<script>new WebSocket(\"ws://localhost:3000/\").onmessage=function(o){\"reload\"===o.data&&window.location.reload()};</script\n</html>"))
        (is (= (:status response) 201))
        (is (= (dissoc (:headers response) "server")))))
    (carraco/stop-server)))


(deftest websocket-default-port
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
      (is (or
            (= 1 @msg-count)
            (= 2 @msg-count))))
    (carraco/stop-server)
    (Thread/sleep 100)))

(deftest websocket-custom-port
  (let [watch-dir-path (format "%s/websocket-test" (System/getProperty "java.io.tmpdir"))
        watch-dir (File. watch-dir-path)
        watched-file (format "%s/file.html" watch-dir-path)
        handler (fn [_]
                  {:status  201
                   :body    "<html>body</html>"
                   :headers {"Content-Type" "text/html"}})
        dirs-to-watch [watch-dir-path]
        msg-count (atom 0)
        project {:cavalo {:server-config {:port 1234}
                          :ring-handler  handler
                          :dirs-to-watch dirs-to-watch}}]

    (when (.exists watch-dir)
      (FileUtils/forceDelete watch-dir))

    (Files/createDirectory (.toPath watch-dir) (into-array FileAttribute nil))
    (spit watched-file "<html>og-body</html>")

    (carraco/start-server project)

    (let [sleep? (atom true)
          ws @(ws/websocket "ws://localhost:1234/"
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
      (is (or
            (= 1 @msg-count)
            (= 2 @msg-count))))
    (Thread/sleep 100)
    (carraco/stop-server)))


    
(deftest websocket-custom-delay
  (let [watch-dir-path (format "%s/websocket-test" (System/getProperty "java.io.tmpdir"))
        watch-dir (File. watch-dir-path)
        watched-file (format "%s/file.html" watch-dir-path)
        handler (fn [_]
                  {:status  201
                   :body    "<html>body</html>"
                   :headers {"Content-Type" "text/html"}})
        dirs-to-watch [watch-dir-path]
        msg-count (atom 0)
        project {:cavalo {:server-config {:notification-delay 3000
                                          :port 1234}
                          :ring-handler  handler
                          :dirs-to-watch dirs-to-watch}}]

    (when (.exists watch-dir)
      (FileUtils/forceDelete watch-dir))

    (Files/createDirectory (.toPath watch-dir) (into-array FileAttribute nil))
    (spit watched-file "<html>og-body</html>")

    (carraco/start-server project)

    (let [sleep? (atom true)
          ws @(ws/websocket "ws://localhost:1234/"
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


      (spit watched-file "<html>new-body</html>")
      (Thread/sleep 2500)
      (is (=  @msg-count) 0)
      (Thread/sleep 1000)
      (is (= 1 @msg-count) 1)
      (ws/close! ws))
    
    (Thread/sleep 100)
    (carraco/stop-server)))


       
(deftest websocket-default-delay
  (let [watch-dir-path (format "%s/websocket-test" (System/getProperty "java.io.tmpdir"))
        watch-dir (File. watch-dir-path)
        watched-file (format "%s/file.html" watch-dir-path)
        handler (fn [_]
                  {:status  201
                   :body    "<html>body</html>"
                   :headers {"Content-Type" "text/html"}})
        dirs-to-watch [watch-dir-path]
        msg-count (atom 0)
        project {:cavalo {:server-config {:port 1234}
                          :ring-handler  handler
                          :dirs-to-watch dirs-to-watch}}]

    (when (.exists watch-dir)
      (FileUtils/forceDelete watch-dir))

    (Files/createDirectory (.toPath watch-dir) (into-array FileAttribute nil))
    (spit watched-file "<html>og-body</html>")

    (carraco/start-server project)

    (let [sleep? (atom true)
          ws @(ws/websocket "ws://localhost:1234/"
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


      (spit watched-file "<html>new-body</html>")
      (Thread/sleep 100)
      (is (=  @msg-count) 0)
      (Thread/sleep 220)
      (is (= 1 @msg-count) 1)
      (ws/close! ws))
    
    (Thread/sleep 100)
    (carraco/stop-server)))