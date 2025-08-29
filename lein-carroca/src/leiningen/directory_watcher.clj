(ns leiningen.directory-watcher
  (:require [clojure.tools.logging :as logger])
  (:import (java.nio.file FileSystem FileSystems FileVisitResult FileVisitor Files LinkOption Path Paths StandardWatchEventKinds WatchEvent WatchKey WatchService)))

(def ^:private watch-dir (atom false))
(def ^:private registered-paths (atom #{}))

(defn- get-all-subdirectories
  "Recursively gets all subdirectories of a given path"
  [^Path path]
  (when (and (Files/exists path (into-array LinkOption []))
             (Files/isDirectory path (into-array LinkOption [])))
    (let [subdirs (atom [path])]
      (Files/walkFileTree path
                          (reify FileVisitor
                            (visitFile [_ _ _]
                              FileVisitResult/CONTINUE)
                            (preVisitDirectory [_ dir _]
                              (when-not (= dir path)
                                (swap! subdirs conj dir))
                              FileVisitResult/CONTINUE)
                            (visitFileFailed [_ file exc]
                              (logger/warn "Failed to visit file" (.toString file) exc)
                              FileVisitResult/CONTINUE)
                            (postVisitDirectory [_ _ _]
                              FileVisitResult/CONTINUE)))
      @subdirs)))

(defn- register-directory
  "Registers a single directory with the watch service"
  [^Path path ^WatchService watch-service]
  (try
    (when (and (Files/exists path (into-array LinkOption []))
               (Files/isDirectory path (into-array LinkOption []))
               (not (contains? @registered-paths path)))
      (logger/debug "Registering directory for watching:" (.toString path))
      (.register path watch-service
                 (into-array [StandardWatchEventKinds/ENTRY_DELETE
                              StandardWatchEventKinds/ENTRY_MODIFY
                              StandardWatchEventKinds/ENTRY_CREATE]))
      (swap! registered-paths conj path)
      true)
    (catch Exception e
      (logger/warn "Failed to register directory" (.toString path) e)
      false)))

(defn- register-all-directories
  "Registers a directory and all its subdirectories with the watch service"
  [^Path root-path ^WatchService watch-service]
  (logger/debug "Registering all subdirectories under:" (.toString root-path))
  (doseq [path (get-all-subdirectories root-path)]
    (register-directory path watch-service)))

(defn- handle-directory-creation
  "Handles creation of new directories by registering them for watching"
  [^Path parent-path ^String created-name ^WatchService watch-service]
  (let [created-path (.resolve parent-path created-name)]
    (when (and (Files/exists created-path (into-array LinkOption []))
               (Files/isDirectory created-path (into-array LinkOption [])))
      (logger/debug "New directory created, registering:" (.toString created-path))
      (register-all-directories created-path watch-service))))

(defn- watch-dirs [dirs-to-watch]
  (logger/debug "Setting up dirs to watch" dirs-to-watch)
  (reset! registered-paths #{})

  (let [default-fs ^FileSystem (FileSystems/getDefault)
        watch-service ^WatchService (.newWatchService default-fs)]

    (let [paths (map (fn [item]
                       (Paths/get ^String (str item) (into-array String nil)))
                     dirs-to-watch)]

      (doseq [path paths]
        (register-all-directories path watch-service)))

    watch-service))

(defn watch [dirs-to-watch notify-function notification-delay]
  (reset! watch-dir true)
  (let [thread (Thread. (fn []
                          (let [watch-service ^WatchService (watch-dirs dirs-to-watch)]
                            (loop []
                              (let [key (.poll watch-service)]
                                (when key
                                  (let [events (.pollEvents ^WatchKey key)
                                        watched-path (.watchable key)]

                                    (doseq [event events]
                                      (let [kind (.kind ^WatchEvent event)
                                            context (.context ^WatchEvent event)]
                                        (when (= kind StandardWatchEventKinds/ENTRY_CREATE)
                                          (handle-directory-creation watched-path
                                                                     (.toString context)
                                                                     watch-service))))

                                    (^[long] Thread/sleep notification-delay)
                                    (notify-function)
                                    (.reset key))))

                              (when @watch-dir
                                (recur))))))]
    (.start ^Thread thread)))

(defn stop []
  (reset! watch-dir false)
  (reset! registered-paths #{}))