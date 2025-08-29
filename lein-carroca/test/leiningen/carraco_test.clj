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

(def ^:private handler
  (fn [req]
    (if (= "/not-html" (:uri req))
      {:status  200
       :body    "body"
       :headers {}}
      {:status  201
       :body    "<html>body</html>"
       :headers {"Content-Type" "text/html"}})))

(defn- create-project
  "Creates a project configuration with optional server config and dirs to watch"
  [& {:keys [port notification-delay dirs-to-watch]
      :or {dirs-to-watch []}}]
  (let [server-config (cond-> {}
                              port (assoc :port port)
                              notification-delay (assoc :notification-delay notification-delay))]
    {:cavalo (cond-> {:ring-handler handler
                      :dirs-to-watch dirs-to-watch}
                     (seq server-config) (assoc :server-config server-config))}))

(defn- test-http-responses
  "Tests both non-HTML and HTML responses for a given port"
  [port expected-html-resource]
  (testing "non-html responses"
    (let [response (http-client/get (format "http://localhost:%d/not-html" port))]
      (is (= (:body response) "body"))
      (is (= (:status response) 200))))

  (testing "html responses with websocket script"
    (let [response (http-client/get (format "http://localhost:%d/" port))]
      (is (= (normalize-newlines (:body response))
             (normalize-newlines (slurp (io/resource expected-html-resource)))))
      (is (= (:status response) 201)))))

(defn- with-temp-dir
  "Creates a temporary directory and file, executes f, then cleans up"
  [dir-name file-content f]
  (let [watch-dir-path (format "%s/%s" (System/getProperty "java.io.tmpdir") dir-name)
        watch-dir (File. watch-dir-path)
        watched-file (format "%s/file.html" watch-dir-path)]

    (when (.exists watch-dir)
      (FileUtils/forceDelete watch-dir))

    (Files/createDirectory (.toPath watch-dir) (into-array FileAttribute nil))
    (spit watched-file file-content)

    (try
      (f watch-dir-path watched-file)
      (finally
        (when (.exists watch-dir)
          (FileUtils/forceDelete watch-dir))))))

(defn- with-server
  "Starts server with project config, executes f, then stops server"
  [project f]
  (try
    (carroca/start-server project)
    (f)
    (finally
      (carroca/stop-server)
      (Thread/sleep 100))))

(defn- create-websocket-connection
  "Creates a WebSocket connection with message counting"
  [port msg-count]
  (let [connected? (atom false)]
    @(ws/websocket (format "ws://localhost:%d/" port)
                   {:on-open    (fn [_] (reset! connected? true))
                    :on-message (fn [_ msg _]
                                  (is (= "reload" (.toString msg)))
                                  (swap! msg-count inc))
                    :on-close   (fn [_ _ _] (reset! connected? false))})))

(defn- wait-for-websocket-messages
  "Waits for expected number of messages with timeout"
  [msg-count expected timeout-ms]
  (let [start-time (System/currentTimeMillis)]
    (while (and (< @msg-count expected)
                (< (- (System/currentTimeMillis) start-time) timeout-ms))
      (Thread/sleep 50))))

(deftest http-server-functionality
  (testing "default port (8080)"
    (let [project (create-project)]
      (with-server project
                   #(test-http-responses 8080 "test/html/default-port.html"))))

  (testing "custom port (3000)"
    (let [project (create-project :port 3000)]
      (with-server project
                   #(test-http-responses 3000 "test/html/3000-port.html")))))

(deftest websocket-functionality
  (testing "default port with file watching"
    (with-temp-dir "websocket-default-test" "<html>original</html>"
                   (fn [watch-dir-path watched-file]
                     (let [project (create-project :dirs-to-watch [watch-dir-path])
                           msg-count (atom 0)]
                       (with-server project
                                    (fn []
                                      (let [ws-conn (create-websocket-connection 8080 msg-count)]
                                        (Thread/sleep 100) ; Allow connection to establish
                                        (spit watched-file "<html>modified</html>")
                                        (wait-for-websocket-messages msg-count 1 2000)
                                        (ws/close! ws-conn)
                                        (is (>= @msg-count 1) "Should receive at least one reload message"))))))))

  (testing "custom delay configuration"
    (with-temp-dir "websocket-delay-test" "<html>original</html>"
                   (fn [watch-dir-path watched-file]
                     (let [project (create-project :port 1234
                                                   :notification-delay 3000
                                                   :dirs-to-watch [watch-dir-path])
                           msg-count (atom 0)]
                       (with-server project
                                    (fn []
                                      (let [ws-conn (create-websocket-connection 1234 msg-count)]
                                        (spit watched-file "<html>modified</html>")
                                        (Thread/sleep 2500)
                                        (is (= @msg-count 0) "Should not receive message before delay")
                                        (Thread/sleep 1000)
                                        (is (= @msg-count 1) "Should receive message after delay")
                                        (ws/close! ws-conn))))))))

  (testing "default delay (200ms)"
    (with-temp-dir "websocket-default-delay-test" "<html>original</html>"
                   (fn [watch-dir-path watched-file]
                     (let [project (create-project :port 1234 :dirs-to-watch [watch-dir-path])
                           msg-count (atom 0)]
                       (with-server project
                                    (fn []
                                      (let [ws-conn (create-websocket-connection 1234 msg-count)]
                                        (spit watched-file "<html>modified</html>")
                                        (Thread/sleep 100)
                                        (is (= @msg-count 0) "Should not receive message immediately")
                                        (Thread/sleep 220)
                                        (is (= @msg-count 1) "Should receive message after default delay")
                                        (ws/close! ws-conn))))))))

  (testing "subdirectory file watching"
    (let [watch-dir-path (format "%s/websocket-subdir-test" (System/getProperty "java.io.tmpdir"))
          watch-dir (File. watch-dir-path)
          subdir-path (format "%s/subdir" watch-dir-path)
          subdir (File. subdir-path)
          watched-file (format "%s/file.html" subdir-path)
          project (create-project :dirs-to-watch [watch-dir-path])
          msg-count (atom 0)]

      (when (.exists watch-dir) (FileUtils/forceDelete watch-dir))
      (Files/createDirectory (.toPath watch-dir) (into-array FileAttribute nil))
      (Files/createDirectory (.toPath subdir) (into-array FileAttribute nil))
      (spit watched-file "<html>original-subdir-content</html>")

      (try
        (with-server project
                     (fn []
                       (let [ws-conn (create-websocket-connection 8080 msg-count)]
                         (Thread/sleep 100) ; Allow connection to establish
                         (spit watched-file "<html>modified-subdir-content</html>")
                         (wait-for-websocket-messages msg-count 1 2000)
                         (ws/close! ws-conn)
                         (is (>= @msg-count 1) "Should receive reload message for subdirectory changes"))))
        (finally
          (when (.exists watch-dir)
            (FileUtils/forceDelete watch-dir)))))))