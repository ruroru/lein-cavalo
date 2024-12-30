(ns live-reload.core-test
  (:require [clojure.test :refer :all]
            [hato.websocket :as ws]
            [live-reload.core :refer :all]
            [ring.adapter.jetty9 :as jetty]))

(def port 8082)
(def address (format "ws://localhost:%s/ws" port))

