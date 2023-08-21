package eventpassport;

import java.util.concurrent.atomic.*;
import java.util.*;
import java.time.Instant;

/** This is a wait-free thread-safe implementation of the Passport pattern where
 * one can stamp arbitrary events into the passport, so that later the timespans
 * between two particular events can be easily computed. See <a
 * href="http://web.archive.org/web/20190729153806/https://eng.fitbit.com/the-passport-a-tool-for-better-metrics/">Passport:
 * A Tool For Better Metrics</a> .
 *
 * This implementation performs O(log2N) insertions (stamps) and O(log2n)
 * calculations of time durations.
 * @param <T> Type of the event states that could be stamped into the passport.
 **/
public class Passport<T> {

    private static final int FIRST_CHUNK_SIZE = 8;

    private final Chunk<T> firstChunk = new Chunk<T>(FIRST_CHUNK_SIZE);
    private final AtomicInteger stampCount = new AtomicInteger(0);
    private final long issuedTimeMs;

    /** Construct a new passport with an initial state.
     * @param initState Initial state (first stamp). Can be null.
     **/
    public Passport(T initState) {
        issuedTimeMs = System.currentTimeMillis();
        firstChunk.put(0, initState, System.nanoTime());
        stampCount.getAndIncrement();
    }

    /** Record an event with the provided state into the passport. Timestamp
     * will be obtained from {@code System.nanoTime}.
     * @param  state Event state.
     * @return       This object (for fluent interface).
     **/
    public Passport stamp(T state) {
        return stamp(state, System.nanoTime());
    }

    /** Record an event with the provided state and timestamp into the passport.
     * @param  state     Event state.
     * @param  timestamp Event timestamp.
     * @return           This object (for fluent interface).
     **/
    public Passport stamp(T state, long timestamp) {
        int idx = stampCount.getAndIncrement();

        // First, we have to chase down the chunk where we should put the state
        // and timestamp for our index.
        Chunk<T> chunk = firstChunk;
        while (idx >= chunk.size()) {
            idx -= chunk.size();
            chunk = chunk.getNext();
        }

        chunk.put(idx, state, timestamp);
        return this;
    }

    /** Find and return the first event in the passport that has the state equal
     * to the provided state. Return null if the event was not found.
     * @param  state State to search for.
     * @return       Matched event or {@code null} if not found.
     **/
    public Event<T> findEventByState(T state) {
        return findEventByState(state, 0);
    }

    /** Find and return the index of the event in the passport that has the
     * state equal to the provided state. Begins searching from the provided
     * starting index. Return -1 if the event was not found.
     * @param  state     State to search for.
     * @param  startFrom Starting index to search from.
     * @return           Index of the found event.
     **/
    public Event<T> findEventByState(T state, int startFrom) {
        int total = stampCount.get();
        Chunk<T> chunk = firstChunk;
        int skip = startFrom;

        while (skip >= chunk.size()) {
            skip -= chunk.size();
            chunk = chunk.getNext();
        }

        int i = startFrom, ci = skip;
        for (; i < total; i++, ci++) {
            if (ci == chunk.size()) {
                ci = 0;
                chunk = chunk.getNext();
            }
            T currState = chunk.getState(ci);
            if (state.equals(currState)) {
                return new Event<>(currState, chunk.getTimestamp(ci), i);
            }
        }

        return null;
    }

    /** Calculate a period in nanoseconds between two given states, or -1 if
     * either of the states was not found in the passport (or they are not one
     * after the other). Note that if the same states are recorded multiple
     * times in the passport, this method will return the period between first
     * encountered states.
     * @param  stateFrom Event state for the start of the period.
     * @param  stateTo   Event state for the end of the period.
     * @return Period duration in nanoseconds or -1 if such period does not
     * exist. **/
    public long timeBetween(T stateFrom, T stateTo) {
        int remainingStampCount = stampCount.get();

        int idxInChunk = 0;
        long fromTime = -1, toTime = -1;
        Chunk<T> chunk = firstChunk;
        for (; remainingStampCount --> 0; idxInChunk++) {
            if (idxInChunk == chunk.size()) {
                idxInChunk = 0;
                chunk = chunk.getNext();
            }
            if (stateFrom.equals(chunk.getState(idxInChunk))) {
                fromTime = chunk.getTimestamp(idxInChunk);
                break;
            }
        }

        if (fromTime == -1) // stateFrom was not found
            return -1;

        idxInChunk++;
        for (; remainingStampCount --> 0; idxInChunk++) {
            if (idxInChunk == chunk.size()) {
                idxInChunk = 0;
                chunk = chunk.getNext();
            }
            if (stateTo.equals(chunk.getState(idxInChunk))) {
                toTime = chunk.getTimestamp(idxInChunk);
                break;
            }
        }

        return (toTime == -1) ? -1 : (toTime - fromTime);
    }

    private static String formatNsDelta(long delta) {
        if (delta < 1000)
            return "" + delta + "ns";
        if (delta < 1000000)
            return "" + (delta / 1000) + "us";
        return "" + (delta / 1000000) + "ms";
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        String issued = Instant.ofEpochMilli(issuedTimeMs).toString();
        Chunk<T> chunk = firstChunk;
        long firstTime = chunk.getTimestamp(0);
        Object firstState = chunk.getState(0);
        String fmt = "\n%" + issued.length() + "s - %s";
        int stampCountNum = stampCount.get() - 1;

        sb.append(issued).append(" - ").append(firstState == null ?
                                               "<created>" : firstState);
        for (int ci = 1; stampCountNum --> 0; ci++) {
            if (ci == chunk.size()) {
                ci = 0;
                chunk = chunk.getNext();
            }
            sb.append(String.format(fmt,
                                    "+" + formatNsDelta(chunk.getTimestamp(ci) - firstTime),
                                    chunk.getState(ci)));
        }

        return sb.toString();
    }

    /** Return a list of all events in the Passport. This method is inefficient
     * and should only be used for testing and debugging purposes.
     * @return List of Event objects.
     **/
    public List<Event<T>> getEvents() {
        int n = stampCount.get();
        int i = 0;
        ArrayList<Event<T>> result = new ArrayList<>(n);
        Chunk<T> chunk = firstChunk;
        while (true) {
            for (int j = 0; j < chunk.size() && i < n; i++, j++)
                result.add(new Event<>(chunk.getState(j), chunk.getTimestamp(j), i));
            if (i == n) break;
            chunk = chunk.getNext();
        }
        return result;
    }
}
