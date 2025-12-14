(defproject org.clojars.jj/lein-carroca "1.0.7-SNAPSHOT"
  :description "Carroça is a sidecar for lein cavalo plugin."
  :url "https://github.com/ruroru/lein-cavalo"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url  "https://www.eclipse.org/legal/epl-2.0/"}
  :scm {:dir ".."}

  :dependencies [[org.clojure/clojure "1.12.3"]
                 [org.clojure/tools.logging "1.3.0"]
                 [info.sunng/ring-jetty9-adapter "0.39.1"]
                 [ch.qos.logback/logback-classic "1.5.22"]
                 [ring/ring-core "1.15.3"]]

  :deploy-repositories [["clojars" {:url      "https://repo.clojars.org"
                                    :username :env/clojars_user
                                    :password :env/clojars_pass}]]
  :plugins [[lein-ancient "1.0.0-RC4-SNAPSHOT"]]
  :resource-paths ["resources"]
  :profiles {:test {:resource-paths ["test/resources"]
                    :dependencies   [[hato "1.0.0"]
                                     [commons-io/commons-io "20030203.000550"]]}}

  )
