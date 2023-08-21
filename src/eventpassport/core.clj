(ns eventpassport.core
  "Passports make it easier to track multi-stage events throughout their lifetime.

  API:

  (make-passport :foo) - create a new passport with initial state :foo.
  (stamp passport :bar) - put a stamp into the passport with the state :bar.
  (time-between passport :foo :bar) -
         return time difference in nanoseconds between :foo and :bar.
  (print-passport passport) - print all stamps in the passport to stdout."
  (:import eventpassport.Passport))

(defn make-passport
  "Create a new passport with the given `init-state` (which can be nil)."
  ^Passport [init-state]
  (Passport. init-state))

#_(make-passport :kyiv)

(defn stamp
  "Put a stamp in the passport, marking its new state at the current time."
  ^Passport [^Passport passport, state]
  (.stamp passport state))

#_(stamp (make-passport :kyiv) :warsaw)

(defn time-between
  "Return a time in nanoseconds between two states in the passport. Return -1 if
  either state is not found, or if the second state is earlier than the first,
  or if passport is nil."
  ^long [^Passport passport, state-from state-to]
  (.timeBetween passport state-from state-to))

(defn print-passport
  "Print the formatted passort to *out* for debugging."
  [^Passport passport]
  (println (str passport)))


(comment
  (let [p (make-passport nil)]
    (dotimes [i 10]
      (Thread/sleep 10)
      (stamp p i))
    (print-passport p))

  (let [p (make-passport :kyiv)]
    (Thread/sleep 500)
    (stamp p :warsaw)
    (Thread/sleep 200)
    (stamp p :berlin)
    (print-passport p))

  (mm/measure (make-passport nil) :debug true))
