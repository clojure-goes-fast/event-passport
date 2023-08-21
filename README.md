# event-passport [![CircleCI](https://img.shields.io/circleci/build/github/clojure-goes-fast/event-passport/master.svg)](https://dl.circleci.com/status-badge/redirect/gh/clojure-goes-fast/event-passport/tree/master) ![](https://img.shields.io/badge/dependencies-none-brightgreen) [![](https://img.shields.io/clojars/dt/com.clojure-goes-fast/event-passport?color=teal)](https://clojars.org/com.clojure-goes-fast/event-passport) [![](https://img.shields.io/badge/-changelog-blue.svg)](CHANGELOG.md)

[Event passport](https://youtu.be/_1rh_s1WmRA?t=718) (request passport, etc.) is
a monitoring pattern for collecting timestamped events related to a single
processed entity (API request, unit of work, etc.) in one place. The first time
I learned about this pattern was from [Zach Tellman's blog
post](http://web.archive.org/web/20190729153806/https://eng.fitbit.com/the-passport-a-tool-for-better-metrics/).
As the unit of work goes through different stages of its lifecycle, you stamp
those stage transitions into its passport. Later, the passport can be printed to
see all the events and when they happened in relation to one another. Passport
can also be used to calculate durations between those events and reported to a
monitoring solution. This avoids polluting your business logic code with
monitoring-related code.

The concept of an event passport should be familiar to those who ever used or
heard about distributed tracing. Distributed tracing solutions track and
accumulate events that happen to a single request accross several machines and
services; event passport is limited to a single service. But using the event
passport pattern doesn't require an external system, it is a simple library with
few classes and very minimal computational overhead. Besides, the data collected
in an event passport can later be forwarded to the distributed tracing system.

This library is one of the possible implementations of this pattern in
Java/Clojure. The implementation is thread-safe (multiple threads can safely
update the same Passport object) and wait-free (no locks or busy-wait loops are
used, progress is always guaranteed). The size of the passport in memory grows
gradually with the number of events. Other, simpler implementations are possible
if there is no thread safety requirement.

## Usage

Add `com.clojure-goes-fast/event-passport` to your dependencies. This
is the latest version:

[![](https://clojars.org/com.clojure-goes-fast/event-passport/latest-version.svg)](https://clojars.org/com.clojure-goes-fast/event-passport)

### Java

The main class to work with is `eventpassport.Passport`. It is parametrized by
the type of the events you stamp into it. Events can be arbitrary objects; you
will use these events later to calculate the time durations between two events.
Here is a simple usage example:

```java
enum RequestState {
    CREATED, REQUEST_SENT, RESPONSE_RECEIVED, RESPONSE_FAILED, RESPONSE_TIMEOUT;
}

...

Passport p = new Passport(RequestState.CREATED);

// Sending the request
// ...
p.stamp(RequestState.REQUEST_SENT);


// Waiting for the response or getting one in a callback
// ...
if (resp.isTimeout()) {
    p.stamp(RequestState.RESPONSE_TIMEOUT);
} else if (resp.isFailed()) {
    p.stamp(RequestState.RESPONSE_FAILED);
} else {
    p.stamp(RequestState.RESPONSE_RECEIVED);
}


// Later, when the lifecycle of the request is over.
// sendToPrometheus is an imaginary method that reports the duration to your monitoring system.
void sendToPrometheus(String metricName, long duration) {
    if (duration != -1) {
        // Use the library of choice to report metric to the monitoring solution.
    }
}

sendToPrometheus("request_success_time", p.timeBetween(RequestState.REQUEST_SENT, RequestState.RESPONSE_RECEIVED));
sendToPrometheus("request_failed_time",  p.timeBetween(RequestState.REQUEST_SENT, RequestState.RESPONSE_FAILED));
sendToPrometheus("request_timeout_time", p.timeBetween(RequestState.REQUEST_SENT, RequestState.RESPONSE_TIMEOUT));


// You can also print the entire passport to the console:
System.out.println(p);

// 2023-08-22T11:44:14.042Z - CREATED
//                    +68us - REQUEST_SENT
//                  +3005ms - RESPONSE_TIMEOUT
```

### Clojure

Clojure's API is almost identical to the one in Java. You would most likely use
keywords instead of enums for event states:

```clj
(require '[eventpassport.core :as pp])

(defn send-to-prometheus [metric-name duration]
  (when (> duration -1)
    ...))

(let [p (pp/make-passport :created)]
  ...
  (pp/stamp p :request-sent)
  ...
  (pp/stamp p (cond (.isTimeout resp) :response-timeout
                    (.isFailed resp)  :response-failed
                    :else             :response-received))
  ...
  (send-to-prometheus "request_success_time" (pp/time-between :request-sent :response-received))
  (send-to-prometheus "request_failed_time" (pp/time-between :request-sent :response-failed))
  (send-to-prometheus "request_timeout_time" (pp/time-between :request-sent :response-timeout))
  ...
  (pp/print-passport p))
```

## Performance

The cost of stamping new events into the passport is O(log2N), where N is the
number of existing events in the passport. The cost of calculating the duration
between two events is also O(log2N). The implementation is wait-free, so the
methods never stall. In-memory size of an empty passport is 248 bytes, the size
gradually grows as new events are added, ArrayList-style.

This implementation has been successfully used in performance-sensitive systems,
so the overhead it creates should be bearable.

## License

event-passport is distributed under the Eclipse Public License.
See [LICENSE](LICENSE).

Copyright 2023 Oleksandr Yakushev
