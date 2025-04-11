(defproject org.clojars.jj/lein-carroca "1.0.1-SNAPSHOT"
  :description "Carroça is a sidecar for lein cavalo plugin."
  :url "https://github.com/ruroru/lein-cavalo"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url  "https://www.eclipse.org/legal/epl-2.0/"}

  :dependencies [[org.clojure/clojure "1.12.0"]
                 [org.clojure/tools.logging "1.3.0"]
                 [info.sunng/ring-jetty9-adapter "0.37.3"]
                 [ch.qos.logback/logback-classic "1.5.18"]
                 [ring/ring-core "1.13.0"]]

  :deploy-repositories [["clojars" {:url      "https://repo.clojars.org"
                                    :username :env/clojars_user
                                    :password :env/clojars_pass}]]
  :plugins [[lein-ancient "1.0.0-RC4-SNAPSHOT"]]
  :resource-paths ["resources"]
  :profiles {:test {:resource-paths ["test/resources"]
                    :dependencies   [[hato "1.0.0"]
                                     [commons-io/commons-io "2.18.0"]]}}

  )
