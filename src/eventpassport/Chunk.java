package eventpassport;

import java.util.concurrent.atomic.*;

/** Chunks make up a thread-safe intrusive linked list that appends to the tail.
 * A chunk has a buffer of predefined size, and when it runs out of that buffer,
 * the call to {@code getNext()} generates and appends a new chunk twice the
 * size to the tail.
 * @param <T> Type of the event states held by the chunk.
 **/
class Chunk<T> {

    /** Array of states ("keys") for the stamped events. **/
    final AtomicReferenceArray<T> eventStates;

    /** Array of timestamps for the stamped events. **/
    final AtomicLongArray eventTimestamps;

    /** Reference to the next chunk in the list. If it points to null, then it
     * means the current chunk is last in the list.
     **/
    final AtomicReference<Chunk<T>> next = new AtomicReference<>(null);

    /** Construct a new Chunk that can hold {@code size} number of stamps. **/
    Chunk(int size) {
        this.eventStates = new AtomicReferenceArray<>(size);
        this.eventTimestamps = new AtomicLongArray(size);
    }

    /** Return the maximum size of the chunk.
     * @return Number of events the chunk can hold.
     **/
    int size() {
        return eventStates.length();
    }

    /** Return the chunk that follows the current one in the list. If the
     * current chunk is the last one, construct a new chunk twice the size, set
     * it to be the next chunk of the current one, and return it.
     * @return Chunk after the current one in the list.
     **/
    Chunk<T> getNext() {
        Chunk<T> nextChunk = next.get();
        if (nextChunk != null)
            return nextChunk;

        Chunk<T> newChunk = new Chunk<>(size() * 2);
        // Try to CAS the newly created chunk in and return it. If CAS fails, it
        // means somebody else has set the next chunk, fetch it again.
        return next.compareAndSet(null, newChunk) ? newChunk : next.get();
    }

    /** Return the event state by the given index. The index should be relative
     * to the chunk, not absolute to the whole passport.
     * @param idx Index of the event in the chunk.
     * @return Event state.
     **/
    T getState(int idx) {
        return eventStates.get(idx);
    }

    /** Return the event timestamp state by the given index. The index should be
     * relative to the chunk, not absolute to the whole passport.
     * @param idx Index of the event in the chunk.
     * @return Event timestamp.
     **/
    long getTimestamp(int idx) {
        return eventTimestamps.get(idx);
    }

    /** Record an event specified by its state and timestamp at the given index.
     * The index should be relative to the chunk, not absolute to the whole
     * passport.
     * @param idx Index in the chunk to save the event to.
     * @param state Event state.
     * @param timestamp Event timestamp.
     **/
    void put(int idx, T state, long timestamp) {
        eventStates.set(idx, state);
        eventTimestamps.set(idx, timestamp);
    }
}
