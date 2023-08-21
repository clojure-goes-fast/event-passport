(ns eventpassport.java-passport-test
  (:require [clojure.test :refer :all])
  (:import (eventpassport Passport StateEnum)))

(deftest basic-passport-operations
  (let [p (Passport. StateEnum/INIT)]
    (.stamp p StateEnum/CONNECTION_OPENED)
    (Thread/sleep 1000)
    (.stamp p StateEnum/UPSTREAM_RECEIVED)
    (.stamp p StateEnum/DOWNSTREAM_REQ1_SENT)
    (doseq [s [StateEnum/DOWNSTREAM_REQ2_SENT
               StateEnum/DOWNSTREAM_RESP1_RECEIVED
               StateEnum/DOWNSTREAM_RESP2_RECEIVED
               StateEnum/CONNECTION_CLOSED
               StateEnum/TEARDOWN]]
      (Thread/sleep (long (rand-int 50)))
      (.stamp p s))

    (is (< (.timeBetween p StateEnum/INIT StateEnum/CONNECTION_OPENED) 1e6))
    (is (> (.timeBetween p StateEnum/CONNECTION_OPENED StateEnum/UPSTREAM_RECEIVED) 5e8))
    (is (> (.timeBetween p StateEnum/INIT StateEnum/DOWNSTREAM_REQ2_SENT) 5e8))
    (is (< 1e6 (.timeBetween p StateEnum/DOWNSTREAM_REQ1_SENT StateEnum/DOWNSTREAM_REQ2_SENT) 6e7))

    (is (= -1 (.timeBetween p StateEnum/TEARDOWN StateEnum/CONNECTION_CLOSED)))
    (is (= -1 (.timeBetween p StateEnum/UPSTREAM_SENT StateEnum/TEARDOWN)))

    (is (re-matches #"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d+Z - INIT
\s+\+\d+.s - CONNECTION_OPENED
\s+\+\d+.s - UPSTREAM_RECEIVED
\s+\+\d+.s - DOWNSTREAM_REQ1_SENT
\s+\+\d+.s - DOWNSTREAM_REQ2_SENT
\s+\+\d+.s - DOWNSTREAM_RESP1_RECEIVED
\s+\+\d+.s - DOWNSTREAM_RESP2_RECEIVED
\s+\+\d+.s - CONNECTION_CLOSED
\s+\+\d+.s - TEARDOWN"
                    (.toString p)))

    (println (.toString p)))

  (testing "it is possible to create passport with null state"
    (let [p (Passport. nil)]
      (Thread/sleep 10)
      (.stamp p StateEnum/INIT)
      (.stamp p StateEnum/CONNECTION_OPENED)
      (.stamp p StateEnum/CONNECTION_CLOSED)
      (.stamp p StateEnum/TEARDOWN)

      (is (re-matches #"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d+Z - <created>
\s+\+\d+ms - INIT
\s+\+\d+ms - CONNECTION_OPENED
\s+\+\d+ms - CONNECTION_CLOSED
\s+\+\d+ms - TEARDOWN"
                      (.toString p)))))

  (testing "multiple matching states"
    (let [p (Passport. nil)]
      (.stamp p StateEnum/CONNECTION_OPENED)
      (.stamp p StateEnum/DOWNSTREAM_REQ1_SENT)
      (Thread/sleep 10)
      (.stamp p StateEnum/DOWNSTREAM_RESP1_RECEIVED)
      (Thread/sleep 10)
      (.stamp p StateEnum/DOWNSTREAM_REQ1_SENT)
      (Thread/sleep 100)
      (.stamp p StateEnum/DOWNSTREAM_RESP1_RECEIVED)
      (let [ev1 (.findEventByState p StateEnum/DOWNSTREAM_REQ1_SENT)
            ev2 (.findEventByState p StateEnum/DOWNSTREAM_RESP1_RECEIVED (inc (.index ev1)))
            d1 (- (.timestamp ev2) (.timestamp ev1))

            ev3 (.findEventByState p StateEnum/DOWNSTREAM_REQ1_SENT (inc (.index ev2)))
            ev4 (.findEventByState p StateEnum/DOWNSTREAM_RESP1_RECEIVED (inc (.index ev3)))
            d2 (- (.timestamp ev4) (.timestamp ev3))]
        (is (< 8e6 d1 20e6))
        (is (< 8e7 d2 20e7))))))
