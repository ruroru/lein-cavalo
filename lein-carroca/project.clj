(defproject org.clojars.jj/lein-carroca "1.0.0-SNAPSHOT"
  :description "sidecar for cavalo plugin"
  :url "https://github.com/ruroru/lein-cavalo"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url  "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [org.clojure/tools.logging "1.3.0"]
                 [info.sunng/ring-jetty9-adapter "0.36.1"]
                 [ring/ring-core "1.13.0"]]
  :repl-options {:init-ns cavalo-sidecar.core})
