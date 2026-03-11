package weather.system;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for LamportClock implementation
 * Tests the correctness of Lamport logical clock algorithm
 */
public class LamportClockTest {
    private LamportClock clock1;
    private LamportClock clock2;

    @Before
    public void setUp() {
        clock1 = new LamportClock();
        clock2 = new LamportClock();
    }

    @Test
    public void testInitialClockValue() {
        System.out.println("TEST: Initial clock value should be 0");
        assertEquals("Clock should start at 0", 0, clock1.getTime());
        System.out.println("✓ Initial clock value is correct");
    }

    @Test
    public void testTickIncrement() {
        System.out.println("TEST: Clock tick should increment by 1");
        int initialTime = clock1.getTime();
        int tickResult = clock1.tick();

        assertEquals("Tick should increment clock by 1", initialTime + 1, tickResult);
        assertEquals("Clock time should match tick result", tickResult, clock1.getTime());
        System.out.println("✓ Clock tick increments correctly");
    }

    @Test
    public void testMultipleTicks() {
        System.out.println("TEST: Multiple ticks should increment sequentially");
        assertEquals("First tick should be 1", 1, clock1.tick());
        assertEquals("Second tick should be 2", 2, clock1.tick());
        assertEquals("Third tick should be 3", 3, clock1.tick());
        System.out.println("✓ Multiple ticks work correctly");
    }

    @Test
    public void testUpdateWithHigherClock() {
        System.out.println("TEST: Update with higher received clock");
        clock1.tick(); // clock1 = 1
        int result = clock1.update(5); // Should become max(1, 5) + 1 = 6

        assertEquals("Clock should update to max(local, received) + 1", 6, result);
        assertEquals("Clock time should match update result", result, clock1.getTime());
        System.out.println("✓ Update with higher clock works correctly");
    }

    @Test
    public void testUpdateWithLowerClock() {
        System.out.println("TEST: Update with lower received clock");
        clock1.tick(); clock1.tick(); clock1.tick(); // clock1 = 3
        int result = clock1.update(1); // Should become max(3, 1) + 1 = 4

        assertEquals("Clock should update to max(local, received) + 1", 4, result);
        System.out.println("✓ Update with lower clock works correctly");
    }

    @Test
    public void testConcurrentTicks() throws InterruptedException {
        System.out.println("TEST: Concurrent tick operations (thread safety)");
        final int NUM_THREADS = 10;
        final int TICKS_PER_THREAD = 100;
        Thread[] threads = new Thread[NUM_THREADS];

        for (int i = 0; i < NUM_THREADS; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < TICKS_PER_THREAD; j++) {
                    clock1.tick();
                }
            });
        }

        for (Thread thread : threads) {
            thread.start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        assertEquals("Final clock should equal total ticks",
                NUM_THREADS * TICKS_PER_THREAD, clock1.getTime());
        System.out.println("✓ Concurrent operations maintain correctness");
    }

    @Test
    public void testLamportClockAlgorithmScenario() {
        System.out.println("TEST: Complete Lamport clock communication scenario");

        // Process 1 does some local work
        clock1.tick(); // clock1 = 1
        clock1.tick(); // clock1 = 2

        // Process 1 sends message to Process 2
        int messageClock = clock1.tick(); // clock1 = 3
        System.out.println("Process 1 sends message with clock: " + messageClock);

        // Process 2 receives message and updates its clock
        clock2.tick(); // clock2 = 1 (some local work)
        int updatedClock = clock2.update(messageClock); // clock2 = max(1, 3) + 1 = 4
        System.out.println("Process 2 receives message, updates clock to: " + updatedClock);

        // Verify the clocks follow Lamport's rules
        assertTrue("Sender's clock should be less than receiver's updated clock",
                messageClock < updatedClock);
        System.out.println("✓ Lamport clock algorithm scenario works correctly");
    }
}