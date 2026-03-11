package weather.system;

/**
 * Implementation of Lamport Logical Clock for distributed system synchronization
 * Each entity maintains its own clock and updates it according to Lamport's algorithm
 */
public class LamportClock {
    private int clock;
    private final Object lock = new Object();

    public LamportClock() {
        this.clock = 0;
    }

    /**
     * Increment clock before sending a message
     * @return current clock value after increment
     */
    public int tick() {
        synchronized (lock) {
            return ++clock;
        }
    }

    /**
     * Update clock when receiving a message
     * @param receivedClock the clock value from received message
     * @return current clock value after update
     */
    public int update(int receivedClock) {
        synchronized (lock) {
            clock = Math.max(clock, receivedClock) + 1;
            return clock;
        }
    }

    /**
     * Get current clock value without incrementing
     * @return current clock value
     */
    public int getTime() {
        synchronized (lock) {
            return clock;
        }
    }

    @Override
    public String toString() {
        return String.valueOf(getTime());
    }
}