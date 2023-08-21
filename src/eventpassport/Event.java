package eventpassport;

/** Represents a single event stamped in the passport. Note that internally
 * {@code Passport} holds states and timestamps in separate arrays, and Event
 * objects are only created on demand when respective API methods are called.
 * @param <T> Type of the event state.
 **/
public class Event<T> {

    public final T state;
    public final long timestamp;
    public final int index;

    public Event(T state, long timestamp, int index) {
        this.state = state;
        this.timestamp = timestamp;
        this.index = index;
    }
}
