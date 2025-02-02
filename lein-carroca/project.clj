(defproject org.clojars.jj/lein-carroca "1.0.0-SNAPSHOT"
  :description "Carroça is an http server that is attached to a leiningen project."
  :url "https://github.com/ruroru/lein-cavalo"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url  "https://www.eclipse.org/legal/epl-2.0/"}
  :resource-paths ["test/resources"]

  :dependencies [[org.clojure/clojure "1.12.0"]
                 [org.clojure/tools.logging "1.3.0"]
                 [info.sunng/ring-jetty9-adapter "0.36.1"]
                 [ch.qos.logback/logback-classic "1.5.16"]
                 [ring/ring-core "1.13.0"]]

  :plugins [[lein-ancient "1.0.0-RC4-SNAPSHOT"]]

  :profiles {:test {:dependencies [[hato "1.0.0"]
                                   [commons-io/commons-io "2.18.0"]]}}

  :repl-options {:init-ns cavalo-sidecar.core})
