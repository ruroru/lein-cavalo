(ns leiningen.directory-watcher
  (:require [clojure.tools.logging :as logger])
  (:import (java.nio.file FileSystem FileSystems Paths StandardWatchEventKinds WatchKey WatchService)))

(def watch-dir (atom false))

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



(defn watch [dirs-to-watch notify-function new-server-config]
  (reset! watch-dir true)
  (let [thread (Thread. (fn []
                          (let [watch-service ^WatchService (watch-dirs dirs-to-watch)]
                            (loop []
                              (let [key (.poll watch-service)]
                                (when key
                                  (.pollEvents ^WatchKey key)
                                  (^[long] Thread/sleep (:notification-delay new-server-config))
                                  (notify-function)
                                  (.reset key)))
                              (when @watch-dir
                                (recur))))))]

    (.start ^Thread thread)))


(defn stop []
  (reset! watch-dir false))