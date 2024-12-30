(defproject org.clojars.jj/lein-cavalo "1.0.0-SNAPSHOT"
  :description "Leiningen plugin to reload page on file change"
  :url "https://github.com/ruroru/lein-cavalo"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url  "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [ring/ring-core "1.13.0"]
                 [org.clojars.jj/ring-http-exchange "1.0.6"]
                 [info.sunng/ring-jetty9-adapter "0.36.1"]]

  :profiles {:x       {:source-paths ["xsrc"]
                       :plugins [[org.clojars.jj/lein-cavalo "1.0.0-SNAPSHOT"]]}
             :release {:plugins [[org.clojars.jj/bump "1.0.0"]]}
             :test    {:global-vars  {*warn-on-reflection* true}
                       :resource-paths ["test/resources"]
                       :dependencies [[mock-clj "0.2.1"]
                                      [hato "1.0.0"]]}}
  :cavalo {:ring-handler leiningen.cavalo.handler/handler
           :watch-dirs ["test/resources/html"]}

  :repl-options {:init-ns live-reload.core})
