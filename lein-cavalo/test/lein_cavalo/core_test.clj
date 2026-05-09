(ns lein-cavalo.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [leiningen.cavalo :as cavalo]
            [leiningen.core.eval :as lein-eval]
            [mock-clj.core :as mock]))

(deftest version-is-valid
  (testing "version returns a non-empty string matching semver pattern"
    (let [v (#'cavalo/version)]
      (is (string? v))
      (is (seq v))
      (is (re-matches #"\d+\.\d+\.\d+.*" v)))))

(deftest insert-dependency-to-project-test
  (testing "adds lein-carroca dependency with correct version"
    (let [project {:dependencies [['org.clojure/clojure "1.12.4"]]}
          result (#'cavalo/insert-dependency-to-project project)
          deps (:dependencies result)]
      (is (= 2 (count deps)))
      (is (= 'org.clojars.jj/lein-carroca (first (second deps))))
      (is (= (#'cavalo/version) (second (second deps))))))

  (testing "preserves existing dependencies"
    (let [existing-dep ['some/lib "1.0.0"]
          project {:dependencies [existing-dep]}
          result (#'cavalo/insert-dependency-to-project project)]
      (is (some #(= existing-dep %) (:dependencies result)))))

  (testing "works with empty dependencies"
    (let [project {:dependencies []}
          result (#'cavalo/insert-dependency-to-project project)
          deps (:dependencies result)]
      (is (= 1 (count deps)))
      (is (= 'org.clojars.jj/lein-carroca (first (first deps)))))))

(deftest cavalo-task-test
  (testing "injects dependency and calls eval-in-project"
    (mock/with-mock [lein-eval/eval-in-project nil]
      (cavalo/cavalo {:dependencies []} "ignored-arg")
      (is (mock/called? lein-eval/eval-in-project))
      (is (= 1 (mock/call-count lein-eval/eval-in-project)))
      (let [[project-arg] (mock/last-call lein-eval/eval-in-project)
            deps (:dependencies project-arg)]
        (is (= 1 (count deps)))
        (is (= 'org.clojars.jj/lein-carroca (first (first deps)))))))

  (testing "passes extra args without error"
    (mock/with-mock [lein-eval/eval-in-project nil]
      (cavalo/cavalo {:dependencies []} "arg1" "arg2")
      (is (mock/called? lein-eval/eval-in-project)))))
