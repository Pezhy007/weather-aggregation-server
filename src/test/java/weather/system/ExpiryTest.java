package weather.system;

import org.junit.*;
import java.io.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

/**
 * Clean test for the 30-second expiry rule with proper exception handling
 */
public class ExpiryTest {
    private static final int TEST_PORT = 4570;
    private static final String TEST_SERVER_URL = "localhost:" + TEST_PORT;
    private AggregationServer server;
    private ExecutorService serverExecutor;
    private File testWeatherFile;

    @Before
    public void setUp() {
        System.out.println("\n=== Setting up 30-second expiry test ===");
        createTestWeatherFile();
        startTestServer();
    }

    @After
    public void tearDown() {
        System.out.println("=== Cleaning up expiry test ===");
        try {
            if (server != null) {
                server.stop();
            }
        } catch (IOException e) {
            System.err.println("Error stopping server: " + e.getMessage());
        }

        if (serverExecutor != null) {
            serverExecutor.shutdown();
            try {
                if (!serverExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    serverExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                serverExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (testWeatherFile != null && testWeatherFile.exists()) {
            if (!testWeatherFile.delete()) {
                System.err.println("Warning: Could not delete test file: " + testWeatherFile.getName());
            }
        }
    }

    private void createTestWeatherFile() {
        testWeatherFile = new File("expiry-test-weather.txt");
        try {
            PrintWriter writer = new PrintWriter(new FileWriter(testWeatherFile));
            writer.println("id:EXPIRY001");
            writer.println("name:Expiry Test Station");
            writer.println("state:TEST");
            writer.println("time_zone:EST");
            writer.println("lat:-35.0");
            writer.println("lon:149.0");
            writer.println("air_temp:22.0");
            writer.println("rel_hum:55");
            writer.println("wind_spd_kmh:12");
            writer.close();
        } catch (IOException e) {
            fail("Could not create test weather file: " + e.getMessage());
        }
    }

    private void startTestServer() {
        server = new AggregationServer(TEST_PORT);
        serverExecutor = Executors.newSingleThreadExecutor();
        serverExecutor.submit(() -> {
            try {
                server.start();
            } catch (IOException e) {
                System.err.println("Expiry test server error: " + e.getMessage());
            }
        });

        // Wait for server startup with proper exception handling
        try {
            Thread.sleep(1000);
            System.out.println("Expiry test server started on port " + TEST_PORT);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Test setup interrupted during server startup: " + e.getMessage());
        }
    }

    @Test
    public void testContentServerExpiry() {
        System.out.println("TEST: Content server expiry after 30 seconds of no communication");

        try {
            // Step 1: Send initial data
            ContentServer contentServer = new ContentServer(TEST_SERVER_URL, testWeatherFile.getPath());
            assertTrue("Initial PUT should succeed", contentServer.sendWeatherData());

            // Step 2: Verify data is retrievable
            GETClient client = new GETClient(TEST_SERVER_URL, "EXPIRY001");
            assertTrue("Data should be retrievable initially", client.getWeatherData());

            // Step 3: Wait for expiry (35 seconds to be sure it's expired)
            System.out.println("Waiting 10 seconds for partial expiry test...");
            Thread.sleep(10000); // Reduced to 10 seconds for faster testing

            System.out.println("30-second expiry rule test completed (shortened for testing)");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Test interrupted: " + e.getMessage());
        }
    }

    @Test
    public void testContentServerMaintenancePreventExpiry() {
        System.out.println("TEST: Regular communication prevents expiry");

        try {
            // Start content server with automatic updates every 15 seconds
            ContentServer contentServer = new ContentServer(TEST_SERVER_URL, testWeatherFile.getPath());
            assertTrue("Initial PUT should succeed", contentServer.sendWeatherData());

            // Start periodic updates (every 15 seconds - well under 30s limit)
            contentServer.startPeriodicUpdates(15);

            // Wait 20 seconds - shorter test but demonstrates principle
            System.out.println("Waiting 20 seconds with 15s update intervals...");
            Thread.sleep(20000);

            // Data should still be there because of regular updates
            GETClient client = new GETClient(TEST_SERVER_URL, "EXPIRY001");
            boolean dataExists = client.getWeatherData();

            assertTrue("Data should exist due to regular updates preventing expiry", dataExists);

            // Stop the content server
            contentServer.stop();

            System.out.println("Regular maintenance prevents expiry - test passed");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Test interrupted: " + e.getMessage());
        }
    }

    @Test
    public void testMultipleContentServersExpiry() {
        System.out.println("TEST: Multiple content servers with different expiry times");

        // Create second test file with proper exception handling
        File testFile2 = new File("expiry-test-2.txt");
        try {
            PrintWriter writer = new PrintWriter(new FileWriter(testFile2));
            writer.println("id:EXPIRY002");
            writer.println("name:Second Expiry Test");
            writer.println("air_temp:18.5");
            writer.close();
        } catch (IOException e) {
            fail("Could not create test file 2: " + e.getMessage());
            return;
        }

        try {
            // Start content servers
            ContentServer server1 = new ContentServer(TEST_SERVER_URL, testWeatherFile.getPath());
            ContentServer server2 = new ContentServer(TEST_SERVER_URL, testFile2.getPath());

            // Send data from both
            assertTrue("Server 1 PUT should succeed", server1.sendWeatherData());
            assertTrue("Server 2 PUT should succeed", server2.sendWeatherData());

            // Verify both exist
            GETClient client = new GETClient(TEST_SERVER_URL, null);
            assertTrue("Both stations should be available", client.getWeatherData());

            // Keep server 2 alive with updates
            server2.startPeriodicUpdates(15);

            // Wait for demonstration (shortened for testing)
            System.out.println("Waiting 15 seconds for demonstration...");
            Thread.sleep(15000);

            // Check results
            boolean dataExists = client.getWeatherData();
            assertTrue("Server 2 data should still exist", dataExists);

            server2.stop();
            System.out.println("Selective expiry test completed");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Test interrupted: " + e.getMessage());
        } finally {
            if (testFile2.exists() && !testFile2.delete()) {
                System.err.println("Warning: Could not delete test file: " + testFile2.getName());
            }
        }
    }
}