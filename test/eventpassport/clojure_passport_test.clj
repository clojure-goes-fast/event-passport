(ns eventpassport.clojure-passport-test
  (:require [clojure.test :refer :all]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as tc.prop]
            [eventpassport.core :as sut])
  (:import eventpassport.Event))

(deftest basic-passport-operations
  (let [p (sut/make-passport :first)]
    (sut/stamp p :second)
    (Thread/sleep 1000)
    (sut/stamp p :third)
    (sut/stamp p :fourth)

    (is (< (sut/time-between p :first :second) 1e6))
    (is (< (sut/time-between p :third :fourth) 1e6))
    (is (> (sut/time-between p :second :third) 5e8))
    (is (> (sut/time-between p :first :fourth) 5e8))))

(defspec concurrent-test {:num-tests 50}
  (testing "passport is safe to use concurrently"
    (tc.prop/for-all
     [number-of-ops (gen/choose 10 10000)]
     (let [threads 32
           states-by-thread (range threads)
           passport (sut/make-passport :init)]
       (dorun (pmap (fn [state]
                      (dotimes [_ number-of-ops]
                        (sut/stamp passport state)))
                    states-by-thread))

       (let [state-freqs (frequencies (map #(.state ^Event %) (.getEvents passport)))]
         (is (= state-freqs (reduce (fn [m state] (assoc m state number-of-ops))
                                    {:init 1}
                                    states-by-thread))))))))
