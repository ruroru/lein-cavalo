(ns leiningen.carraco-test
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [hato.client :as http-client]
            [hato.websocket :as ws]
            [leiningen.carraco :as carroca])
  (:import (java.io File)
           (java.nio.file Files)
           (java.nio.file.attribute FileAttribute)
           (org.apache.commons.io FileUtils)))

(defn- normalize-newlines [s]
  (-> s
      (str/replace "\r\n" "\n")
      (str/replace "\r" "\n")))

(def  ^:private handler (fn [req]
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
    (carroca/start-server project)
    (testing "non html responses"
      (let [response (http-client/get (format "http://localhost:8080/not-html"))]
        (is (= (:body response) "body"))
        (is (= (:status response) 200))
        (is (= (dissoc (:headers response) "server")))))

    (testing "html responses witha  correct webscocket script attached to html page."
      (let [response (http-client/get (format "http://localhost:8080/"))]
        (is (= (normalize-newlines (:body response)) (normalize-newlines (slurp (io/resource "test/html/default-port.html")))))
        (is (= (:status response) 201))
        (is (= (dissoc (:headers response) "server")))))
    (carroca/stop-server)))

(deftest test-custom-port
  (let [dirs-to-watch []
        project {:cavalo {:server-config {:port 3000}
                          :ring-handler  handler
                          :dirs-to-watch dirs-to-watch}}]

    (carroca/start-server project)
    (testing "non html responses"
      (let [response (http-client/get (format "http://localhost:3000/not-html"))]
        (is (= (:body response) "body"))
        (is (= (:status response) 200))
        (is (= (dissoc (:headers response) "server")))))

    (testing "html responses witha  correct webscocket script attached to html page."
      (let [response (http-client/get (format "http://localhost:3000/"))]
        (is (= (normalize-newlines (:body response))
               (normalize-newlines (slurp (io/resource "test/html/3000-port.html")))))
        (is (= (:status response) 201))
        (is (= (dissoc (:headers response) "server")))))
    (carroca/stop-server)))


(deftest websocket-default-port
  (let [watch-dir-path (format "%s/websocket-test3" (System/getProperty "java.io.tmpdir"))
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

    (carroca/start-server project)

    (let [sleep? (atom true)
          ws @(ws/websocket "ws://localhost:8080/"
                            {:on-open    (fn [_]
                                           (reset! sleep? true)
                                           (println "socket open:"))
                             :on-message (fn [_ msg _]
                                           (is (= "reload" (.toString msg)))
                                           (swap! msg-count (fn [count]
                                                              (inc count)))
                                           (reset! sleep? false)
                                           (println "Received message:" msg))
                             :on-close   (fn [_ _ _]
                                           (reset! sleep? false)
                                           (println "WebSocket closed!"))})]


      (while @sleep?
        (spit watched-file "<html>new-body</html>")
        (Thread/sleep 1000))
      (ws/close! ws)
      (is (or
            (= 1 @msg-count)
            (= 2 @msg-count))))
    (carroca/stop-server)
    (Thread/sleep 100)))



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
                                          :port               1234}
                          :ring-handler  handler
                          :dirs-to-watch dirs-to-watch}}]

    (when (.exists watch-dir)
      (FileUtils/forceDelete watch-dir))

    (Files/createDirectory (.toPath watch-dir) (into-array FileAttribute nil))
    (spit watched-file "<html>og-body</html>")

    (carroca/start-server project)

    (let [sleep? (atom true)
          ws @(ws/websocket "ws://localhost:1234/"
                            {:on-open    (fn [_]
                                           (reset! sleep? true)
                                           (println "socket open:"))
                             :on-message (fn [_ msg _]
                                           (is (= "reload" (.toString msg)))
                                           (swap! msg-count (fn [count]
                                                              (inc count)))
                                           (reset! sleep? false)
                                           (println "Received message:" msg))
                             :on-close   (fn [_ _ _]
                                           (reset! sleep? false)
                                           (println "WebSocket closed!"))})]


      (spit watched-file "<html>new-body</html>")
      (Thread/sleep 2500)
      (is (= @msg-count) 0)
      (Thread/sleep 1000)
      (is (= 1 @msg-count) 1)
      (ws/close! ws))

    (Thread/sleep 100)
    (carroca/stop-server)))

(deftest websocket-default-delay
  (let [watch-dir-path (format "%s/websocket-test5" (System/getProperty "java.io.tmpdir"))
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

    (carroca/start-server project)

    (let [sleep? (atom true)
          ws @(ws/websocket "ws://localhost:1234/"
                            {:on-open    (fn [_]
                                           (reset! sleep? true)
                                           (println "socket open:"))
                             :on-message (fn [_ msg _]
                                           (is (= "reload" (.toString msg)))
                                           (swap! msg-count (fn [count]
                                                              (inc count)))
                                           (reset! sleep? false)
                                           (println "Received message:" msg))
                             :on-close   (fn [_ _ _]
                                           (reset! sleep? false)
                                           (println "WebSocket closed!"))})]


      (spit watched-file "<html>new-body</html>")
      (Thread/sleep 100)
      (is (= @msg-count) 0)
      (Thread/sleep 220)
      (is (= 1 @msg-count) 1)
      (ws/close! ws))

    (Thread/sleep 100)
    (carroca/stop-server)))
