(defproject org.clojars.jj/lein-cavalo "1.0.7-SNAPSHOT"
  :description "Cavalo is a leiningen plugin to reload http servers on file change."
  :url "https://github.com/ruroru/lein-cavalo"

  :scm {:dir ".."}
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url  "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.12.4"]
                 [leiningen "2.12.0"]
                 [org.clojure/tools.logging "1.3.1"]
                 [leiningen-core "2.12.0"]
                 [ch.qos.logback/logback-classic "1.5.24"]]

  :aot [leiningen.cavalo]

  :deploy-repositories [["clojars" {:url      "https://repo.clojars.org"
                                    :username :env/clojars_user
                                    :password :env/clojars_pass}]]

  :profiles {:test {:dependencies [[mock-clj "0.2.1"]]}}
  :repl-options {:init-ns lein-cavalo.core})
