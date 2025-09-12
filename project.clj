(defproject org.clojars.jj/lein-cavalo-parent "1.0.6-SNAPSHOT"
  :description "Leiningen plugin to reload page on file change"
  :url "https://github.com/ruroru/lein-cavalo"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url  "https://www.eclipse.org/legal/epl-2.0/"}

  :dependencies [[org.clojars.jj/lein-cavalo "1.0.6-SNAPSHOT"]
                 [org.clojars.jj/lein-carroca "1.0.6-SNAPSHOT"]
                 [leiningen-core "2.11.2"]]

  :plugins [[lein-sub "0.3.0"]
            [org.clojars.jj/bump "1.0.4"]
            [org.clojars.jj/bump-md "1.1.0"]]

  :deploy-repositories [["clojars" {:url      "https://repo.clojars.org"
                                    :username :env/clojars_user
                                    :password :env/clojars_pass}]]

  :sub ["lein-cavalo"
        "lein-carroca"]

  :profiles {:test    {:global-vars    {*warn-on-reflection* true}
                       :resource-paths ["test/resources"]
                       :dependencies   [[mock-clj "0.2.1"]
                                        [hato "1.0.0"]]}}

  :repl-options {:init-ns live-reload.core})
