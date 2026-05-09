(ns leiningen.carraco-test
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [hato.client :as http-client]
            [hato.websocket :as ws]
            [leiningen.carraco :as carroca]
            [leiningen.carroca-server])
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
    (case (:uri req)
      "/not-html" {:status  200
                   :body    "body"
                   :headers {}}
      "/all-lower-case-content-type" {:status  201
                                      :body    "<html>body</html>"
                                      :headers {"content-type" "text/html"}}
      "/all-upper-case-content-type" {:status  201
                                      :body    "<html>body</html>"
                                      :headers {"CONTENT-TYPE" "text/html"}}
      {:status  201
       :body    "<html>body</html>"
       :headers {"Content-Type" "text/html"}}
      )))

(defn- create-project
  "Creates a project configuration with optional server config and dirs to watch"
  [& {:keys [port notification-delay dirs-to-watch]
      :or   {dirs-to-watch []}}]
  (let [server-config (cond-> {}
                              port (assoc :port port)
                              notification-delay (assoc :notification-delay notification-delay))]
    {:cavalo (cond-> {:ring-handler  handler
                      :dirs-to-watch dirs-to-watch}
                     (seq server-config) (assoc :server-config server-config))}))

(defn- test-http-responses
  [port expected-html-resource]
  (testing "non-html responses"
    (let [response (http-client/get (format "http://localhost:%d/not-html" port))]
      (is (= (:body response) "body"))
      (is (= (:status response) 200))))
  (testing "html responses with websocket script"
    (let [response (http-client/get (format "http://localhost:%d/all-upper-case-content-type" port))]
      (is (= (normalize-newlines (:body response))
             (normalize-newlines (slurp (io/resource expected-html-resource)))))
      (is (= (:status response) 201))))
  (testing "html responses with websocket script"
    (let [response (http-client/get (format "http://localhost:%d/all-lower-case-content-type" port))]
      (is (= (normalize-newlines (:body response))
             (normalize-newlines (slurp (io/resource expected-html-resource)))))
      (is (= (:status response) 201))))
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
  ([port msg-count]
   (create-websocket-connection port msg-count nil))
  ([port msg-count messages]
   (let [connected? (atom false)]
     @(ws/websocket (format "ws://localhost:%d/" port)
                    {:on-open    (fn [_] (reset! connected? true))
                     :on-message (fn [_ msg _]
                                   (let [m (.toString msg)]
                                     (when messages (swap! messages conj m))
                                     (swap! msg-count inc)))
                     :on-close   (fn [_ _ _] (reset! connected? false))}))))

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
                                        (Thread/sleep 100)  ; Allow connection to establish
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
                         (Thread/sleep 100)
                         (spit watched-file "<html>modified-subdir-content</html>")
                         (wait-for-websocket-messages msg-count 1 2000)
                         (ws/close! ws-conn)
                         (is (>= @msg-count 1) "Should receive reload message for subdirectory changes"))))
        (finally
          (when (.exists watch-dir)
            (FileUtils/forceDelete watch-dir))))))

  (testing "socket cleanup on disconnect"
    (let [project (create-project :port 4567)
          sockets-atom @(resolve 'leiningen.carroca-server/sockets)]
      (with-server project
                   (fn []
                     (is (= 0 (count @sockets-atom)) "Should start with no sockets")
                     (let [ws-conn (create-websocket-connection 4567 (atom 0))]
                       (Thread/sleep 200)
                       (is (= 1 (count @sockets-atom)) "Should have one socket after connect")
                       (ws/close! ws-conn)
                       (Thread/sleep 200)
                       (is (= 0 (count @sockets-atom)) "Should have no sockets after disconnect")))))))

(deftest namespace-reload-on-file-change
  (testing "reloads clj namespace and notifies websocket clients"
    (let [watch-dir-path (format "%s/ns-reload-test" (System/getProperty "java.io.tmpdir"))
          watch-dir (File. watch-dir-path)
          clj-file (format "%s/reloadable_test_ns.clj" watch-dir-path)
          project (create-project :port 5678 :dirs-to-watch [watch-dir-path])
          msg-count (atom 0)]

      (when (.exists watch-dir) (FileUtils/forceDelete watch-dir))
      (Files/createDirectory (.toPath watch-dir) (into-array FileAttribute nil))
      (spit clj-file "(ns reloadable-test-ns)\n(def value 1)")
      (load-file clj-file)

      (try
        (is (= 1 @(resolve 'reloadable-test-ns/value)) "Initial value should be 1")

        (with-server project
                     (fn []
                       (let [ws-conn (create-websocket-connection 5678 msg-count)]
                         (Thread/sleep 200)
                         (spit clj-file "(ns reloadable-test-ns)\n(def value 42)")
                         (wait-for-websocket-messages msg-count 1 3000)
                         (ws/close! ws-conn)
                         (is (>= @msg-count 1) "Should receive reload message after clj file change")
                         (is (= 42 @(resolve 'reloadable-test-ns/value)) "Namespace should be reloaded with new value"))))

        (finally
          (remove-ns 'reloadable-test-ns)
          (when (.exists watch-dir)
            (FileUtils/forceDelete watch-dir)))))))

(deftest css-hot-reload
  (testing "sends css-reload for CSS-only changes"
    (with-temp-dir "css-reload-test" "body { color: red; }"
                   (fn [watch-dir-path watched-file]
                     ;; rename the file to .css
                     (let [css-file (format "%s/style.css" watch-dir-path)
                           _ (spit css-file (slurp watched-file))
                           project (create-project :port 6789 :dirs-to-watch [watch-dir-path])
                           msg-count (atom 0)
                           messages (atom [])]
                       (with-server project
                                    (fn []
                                      (let [ws-conn (create-websocket-connection 6789 msg-count messages)]
                                        (Thread/sleep 200)
                                        (spit css-file "body { color: blue; }")
                                        (wait-for-websocket-messages msg-count 1 3000)
                                        (ws/close! ws-conn)
                                        (is (>= @msg-count 1) "Should receive message after CSS change")
                                        (is (some #(= "css-reload" %) @messages) "Should receive css-reload message"))))))))

  (testing "sends full reload for mixed CSS and non-CSS changes"
    (with-temp-dir "mixed-reload-test" "<html>body</html>"
                   (fn [watch-dir-path watched-file]
                     (let [css-file (format "%s/style.css" watch-dir-path)
                           _ (spit css-file "body { color: red; }")
                           project (create-project :port 6790 :dirs-to-watch [watch-dir-path])
                           msg-count (atom 0)
                           messages (atom [])]
                       (with-server project
                                    (fn []
                                      (let [ws-conn (create-websocket-connection 6790 msg-count messages)]
                                        (Thread/sleep 200)
                                        (spit watched-file "<html>updated</html>")
                                        (wait-for-websocket-messages msg-count 1 3000)
                                        (ws/close! ws-conn)
                                        (is (>= @msg-count 1) "Should receive message after HTML change")
                                        (is (some #(= "reload" %) @messages) "Should receive full reload message")))))))))

(deftest image-hot-reload
  (testing "sends img-reload for image-only changes"
    (with-temp-dir "img-reload-test" "placeholder"
                   (fn [watch-dir-path _watched-file]
                     (let [img-file (format "%s/logo.png" watch-dir-path)
                           _ (spit img-file "fake-png-data")
                           project (create-project :port 7001 :dirs-to-watch [watch-dir-path])
                           msg-count (atom 0)
                           messages (atom [])]
                       (with-server project
                                    (fn []
                                      (let [ws-conn (create-websocket-connection 7001 msg-count messages)]
                                        (Thread/sleep 200)
                                        (spit img-file "updated-fake-png-data")
                                        (wait-for-websocket-messages msg-count 1 3000)
                                        (ws/close! ws-conn)
                                        (is (>= @msg-count 1) "Should receive message after image change")
                                        (is (some #(= "img-reload" %) @messages) "Should receive img-reload message"))))))))

  (testing "sends full reload for mixed image and non-image changes"
    (with-temp-dir "mixed-img-reload-test" "<html>body</html>"
                   (fn [watch-dir-path watched-file]
                     (let [img-file (format "%s/logo.png" watch-dir-path)
                           _ (spit img-file "fake-png-data")
                           project (create-project :port 7002 :dirs-to-watch [watch-dir-path])
                           msg-count (atom 0)
                           messages (atom [])]
                       (with-server project
                                    (fn []
                                      (let [ws-conn (create-websocket-connection 7002 msg-count messages)]
                                        (Thread/sleep 200)
                                        (spit watched-file "<html>updated</html>")
                                        (wait-for-websocket-messages msg-count 1 3000)
                                        (ws/close! ws-conn)
                                        (is (>= @msg-count 1) "Should receive message after mixed change")
                                        (is (some #(= "reload" %) @messages) "Should receive full reload message")))))))))

(deftest js-hot-reload
  (testing "sends js-reload for JS-only changes"
    (with-temp-dir "js-reload-test" "placeholder"
                   (fn [watch-dir-path _watched-file]
                     (let [js-file (format "%s/app.js" watch-dir-path)
                           _ (spit js-file "console.log('hello');")
                           project (create-project :port 7003 :dirs-to-watch [watch-dir-path])
                           msg-count (atom 0)
                           messages (atom [])]
                       (with-server project
                                    (fn []
                                      (let [ws-conn (create-websocket-connection 7003 msg-count messages)]
                                        (Thread/sleep 200)
                                        (spit js-file "console.log('updated');")
                                        (wait-for-websocket-messages msg-count 1 3000)
                                        (ws/close! ws-conn)
                                        (is (>= @msg-count 1) "Should receive message after JS change")
                                        (is (some #(= "js-reload" %) @messages) "Should receive js-reload message"))))))))

  (testing "sends js-reload for .mjs files"
    (with-temp-dir "mjs-reload-test" "placeholder"
                   (fn [watch-dir-path _watched-file]
                     (let [mjs-file (format "%s/module.mjs" watch-dir-path)
                           _ (spit mjs-file "export default 1;")
                           project (create-project :port 7004 :dirs-to-watch [watch-dir-path])
                           msg-count (atom 0)
                           messages (atom [])]
                       (with-server project
                                    (fn []
                                      (let [ws-conn (create-websocket-connection 7004 msg-count messages)]
                                        (Thread/sleep 200)
                                        (spit mjs-file "export default 2;")
                                        (wait-for-websocket-messages msg-count 1 3000)
                                        (ws/close! ws-conn)
                                        (is (>= @msg-count 1) "Should receive message after .mjs change")
                                        (is (some #(= "js-reload" %) @messages) "Should receive js-reload message for .mjs"))))))))

  (testing "sends full reload for mixed JS and CSS changes"
    (with-temp-dir "mixed-js-css-test" "placeholder"
                   (fn [watch-dir-path _watched-file]
                     (let [js-file (format "%s/app.js" watch-dir-path)
                           css-file (format "%s/style.css" watch-dir-path)
                           _ (spit js-file "console.log('hello');")
                           _ (spit css-file "body { color: red; }")
                           project (create-project :port 7005 :dirs-to-watch [watch-dir-path])
                           msg-count (atom 0)
                           messages (atom [])]
                       (with-server project
                                    (fn []
                                      (let [ws-conn (create-websocket-connection 7005 msg-count messages)]
                                        (Thread/sleep 200)
                                        (spit js-file "console.log('updated');")
                                        (spit css-file "body { color: blue; }")
                                        (wait-for-websocket-messages msg-count 1 3000)
                                        (ws/close! ws-conn)
                                        (is (>= @msg-count 1) "Should receive message after mixed change")
                                        (is (some #(= "reload" %) @messages) "Should receive full reload for mixed types")))))))))

