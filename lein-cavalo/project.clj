  (defproject org.clojars.jj/lein-cavalo "1.0.0-SNAPSHOT"
  :description "lein cavalo plugin "
  :scm {:dir ".."}
  :url "https://github.com/ruroru/lein-cavalo"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [leiningen "2.11.2"]
                 [mock-clj "0.2.1"]
                 [org.clojure/tools.logging "1.3.0"]
                 [leiningen-core "2.11.2"]]
  :repl-options {:init-ns lein-cavalo.core})
