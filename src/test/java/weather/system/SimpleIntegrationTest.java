package weather.system;

import org.junit.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

/**
 * Fixed integration tests with unique ports per test to avoid conflicts
 */
public class SimpleIntegrationTest {
    private static final AtomicInteger portCounter = new AtomicInteger(4570);
    private int testPort;
    private String testServerUrl;
    private AggregationServer server;
    private ExecutorService serverExecutor;
    private File testWeatherFile;

    @Before
    public void setUp() throws IOException {
        // Use unique port for each test
        testPort = portCounter.getAndIncrement();
        testServerUrl = "localhost:" + testPort;

        System.out.println("\n=== Setting up test on port " + testPort + " ===");
        cleanupDataFiles();
        createTestWeatherFile();
        startTestServer();
    }

    @After
    public void tearDown() throws IOException {
        cleanupTestResources();
    }

    private void cleanupDataFiles() {
        String[] dataFiles = {"weather_data.json", "weather_data.bak",
                "simple-test-weather.txt"};
        for (String filename : dataFiles) {
            File file = new File(filename);
            if (file.exists()) {
                file.delete();
            }
        }
    }

    private void createTestWeatherFile() throws IOException {
        testWeatherFile = new File("simple-test-weather.txt");
        try (PrintWriter writer = new PrintWriter(new FileWriter(testWeatherFile))) {
            writer.println("id:SIMPLE001");
            writer.println("name:Simple Test Station");
            writer.println("state:VIC");
            writer.println("time_zone:EST");
            writer.println("lat:-37.8");
            writer.println("lon:144.9");
            writer.println("air_temp:20.5");
            writer.println("rel_hum:65");
            writer.println("wind_spd_kmh:10");
        }
    }

    private void startTestServer() {
        server = new AggregationServer(testPort);
        serverExecutor = Executors.newSingleThreadExecutor();
        serverExecutor.submit(() -> {
            try {
                server.start();
            } catch (IOException e) {
                System.err.println("Test server error on port " + testPort + ": " + e.getMessage());
            }
        });

        // Wait for server to start
        waitForServerReady();
    }

    private void waitForServerReady() {
        for (int i = 0; i < 20; i++) {
            try (Socket testSocket = new Socket()) {
                testSocket.connect(new InetSocketAddress("localhost", testPort), 500);
                System.out.println("Test server ready on port " + testPort);

                // Wait for full initialization
                try {
                    Thread.sleep(300);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                return;
            } catch (IOException e) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        fail("Test server failed to start on port " + testPort);
    }

    private void cleanupTestResources() {
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
                if (!serverExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                    serverExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                serverExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (testWeatherFile != null && testWeatherFile.exists()) {
            testWeatherFile.delete();
        }

        cleanupDataFiles();
    }

    @Test
    public void testServerStartsAndResponds() {
        System.out.println("TEST: Server starts and responds to connections");
        assertTrue("Server should be running", isServerRunning());
        System.out.println("✓ Server is running and accepting connections");
    }

    @Test
    public void testContentServerPutOperation() {
        System.out.println("TEST: ContentServer can PUT data to server");

        ContentServer contentServer = new ContentServer(testServerUrl, testWeatherFile.getPath());
        boolean result = contentServer.sendWeatherData();

        assertTrue("ContentServer PUT should succeed", result);
        System.out.println("✓ ContentServer PUT operation successful");
    }

    @Test
    public void testGetClientRetrievesData() {
        System.out.println("TEST: GETClient can retrieve data from server");

        ContentServer contentServer = new ContentServer(testServerUrl, testWeatherFile.getPath());
        assertTrue("Data should be added first", contentServer.sendWeatherData());

        pauseBriefly(500);

        GETClient client = new GETClient(testServerUrl, null);
        boolean result = client.getWeatherData();

        assertTrue("GETClient should retrieve data successfully", result);
        System.out.println("✓ GETClient retrieval successful");
    }

    @Test
    public void testMultipleClients() {
        System.out.println("TEST: Multiple GET clients can access server");

        ContentServer contentServer = new ContentServer(testServerUrl, testWeatherFile.getPath());
        assertTrue("Content server PUT should succeed", contentServer.sendWeatherData());

        pauseBriefly(500);

        // Test just 2 clients to reduce complexity
        boolean[] results = new boolean[2];
        CountDownLatch latch = new CountDownLatch(2);

        for (int i = 0; i < 2; i++) {
            final int clientIndex = i;
            new Thread(() -> {
                try {
                    // Stagger starts slightly
                    Thread.sleep(clientIndex * 200);

                    GETClient client = new GETClient(testServerUrl, null);
                    results[clientIndex] = client.getWeatherData();

                    System.out.println("Client " + clientIndex + " completed: " + results[clientIndex]);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        try {
            boolean completed = latch.await(8, TimeUnit.SECONDS);
            assertTrue("All clients should complete within timeout", completed);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Test interrupted");
        }

        for (int i = 0; i < 2; i++) {
            assertTrue("Client " + i + " should succeed", results[i]);
        }
        System.out.println("✓ Multiple concurrent clients successful");
    }

    @Test
    public void testErrorHandling() {
        System.out.println("TEST: System handles errors gracefully");

        GETClient failClient = new GETClient("localhost:9999", null);
        boolean shouldFail = failClient.getWeatherData();

        assertFalse("Client should fail when server unavailable", shouldFail);
        System.out.println("✓ Error handling works correctly");
    }

    @Test
    public void testLamportClockFunctionality() {
        System.out.println("TEST: Lamport clocks are working in components");

        LamportClock clock = new LamportClock();
        assertEquals("Initial clock should be 0", 0, clock.getTime());
        assertEquals("First tick should be 1", 1, clock.tick());
        assertEquals("Update should work correctly", 6, clock.update(5));

        System.out.println("✓ Lamport clock functionality verified");
    }

    @Test
    public void testWeatherDataProcessing() {
        System.out.println("TEST: Weather data processing and JSON conversion");

        WeatherData weather = new WeatherData();
        weather.setId("TEST123");
        weather.setAirTemp(25.0);
        weather.setRelHum(70);

        String json = weather.toJson();
        assertNotNull("JSON should not be null", json);
        assertTrue("JSON should contain ID", json.contains("TEST123"));
        assertTrue("JSON should contain temperature", json.contains("25.0"));

        WeatherData parsed = WeatherData.fromJson(json);
        assertEquals("ID should survive round trip", "TEST123", parsed.getId());
        assertEquals("Temperature should survive round trip", 25.0, parsed.getAirTemp(), 0.01);

        System.out.println("✓ Weather data processing works correctly");
    }

    private boolean isServerRunning() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", testPort), 1000);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void pauseBriefly(int milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}