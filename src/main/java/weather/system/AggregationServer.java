package weather.system;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.*;
import java.net.*;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.*;

/**
 * Complete Aggregation server that handles weather data from multiple content servers
 * and serves it to GET clients. Implements all assignment requirements including:
 * - 30-second expiry rule for content servers
 * - Lamport clocks for distributed synchronization
 * - Crash recovery with atomic file operations
 * - All HTTP status codes as specified
 * - Concurrent client/server support
 */
public class AggregationServer {
    private final int port;
    private ServerSocket serverSocket;
    private boolean running = false;
    private final ExecutorService threadPool;
    private final LamportClock clock;

    // Storage for weather data with content server tracking
    private final Map<String, WeatherData> weatherStore;
    private final Map<String, String> stationToContentServer; // Track which server owns which station
    private final Map<String, Long> contentServerLastSeen; // Track last communication time
    private final Object storeLock = new Object();

    // File for persistence
    private static final String STORAGE_FILE = "weather_data.json";
    private static final String BACKUP_FILE = "weather_data.bak";

    public AggregationServer(int port) {
        this.port = port;
        this.threadPool = Executors.newCachedThreadPool();
        this.clock = new LamportClock();
        this.weatherStore = new ConcurrentHashMap<>();
        this.stationToContentServer = new ConcurrentHashMap<>();
        this.contentServerLastSeen = new ConcurrentHashMap<>();

        // Load existing data
        loadWeatherData();

        // Start cleanup task for expired data (every 5 seconds to ensure 30s expiry)
        startCleanupTask();
    }

    /**
     * Start the server and listen for connections
     */
    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;

        System.out.println("Weather Aggregation Server started on port " + port);
        System.out.println("Lamport Clock initialized at: " + clock.getTime());
        System.out.println("30-second expiry rule active - content servers must communicate within 30s");

        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                threadPool.submit(new ClientHandler(clientSocket));
            } catch (IOException e) {
                if (running) {
                    System.err.println("Error accepting client connection: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Stop the server
     */
    public void stop() throws IOException {
        running = false;
        if (serverSocket != null) {
            serverSocket.close();
        }
        threadPool.shutdown();
        try {
            if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            threadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        saveWeatherData(); // Save data before shutdown
    }

    /**
     * Load weather data from persistent storage with crash recovery
     */
    private void loadWeatherData() {
        try {
            File file = new File(STORAGE_FILE);
            File backup = new File(BACKUP_FILE);

            // Try main file first, then backup (crash recovery)
            File toRead = file.exists() ? file : (backup.exists() ? backup : null);

            if (toRead != null) {
                try (BufferedReader reader = new BufferedReader(new FileReader(toRead))) {
                    StringBuilder json = new StringBuilder();
                    String line;

                    while ((line = reader.readLine()) != null) {
                        json.append(line);
                    }

                    if (json.length() > 0) {
                        Gson gson = new Gson();
                        Type mapType = new TypeToken<Map<String, WeatherData>>(){}.getType();
                        Map<String, WeatherData> loaded = gson.fromJson(json.toString(), mapType);
                        if (loaded != null) {
                            weatherStore.putAll(loaded);
                            System.out.println("Loaded " + weatherStore.size() + " weather stations from storage");
                            System.out.println("Used " + (toRead.equals(backup) ? "backup" : "main") + " file for recovery");
                        }
                    }
                }
            } else {
                System.out.println("No existing weather data found - starting with empty store");
            }
        } catch (Exception e) {
            System.err.println("Warning: Could not load weather data: " + e.getMessage());
            System.out.println("Starting with empty weather store");
        }
    }

    /**
     * Save weather data to persistent storage with crash safety
     * Uses atomic file operations: write to backup first, then rename
     */
    private void saveWeatherData() {
        synchronized (storeLock) {
            try {
                // Write to backup file first (atomic operation safety)
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                try (PrintWriter writer = new PrintWriter(new FileWriter(BACKUP_FILE))) {
                    writer.println(gson.toJson(weatherStore));
                }

                // Then rename backup to main file (atomic operation on most filesystems)
                File backup = new File(BACKUP_FILE);
                File main = new File(STORAGE_FILE);
                if (main.exists()) {
                    if (!main.delete()) {
                        System.err.println("Warning: Could not delete old storage file");
                    }
                }
                if (!backup.renameTo(main)) {
                    System.err.println("Warning: Could not rename backup to main storage file");
                }

            } catch (IOException e) {
                System.err.println("Error saving weather data: " + e.getMessage());
            }
        }
    }

    /**
     * Start background task to remove expired weather data (30-second rule)
     * This is a critical assignment requirement
     */
    private void startCleanupTask() {
        ScheduledExecutorService cleanupService = Executors.newScheduledThreadPool(1);
        cleanupService.scheduleWithFixedDelay(() -> {
            synchronized (storeLock) {
                long currentTime = System.currentTimeMillis();
                Iterator<Map.Entry<String, Long>> iterator = contentServerLastSeen.entrySet().iterator();
                int removedServers = 0;
                int removedStations = 0;

                while (iterator.hasNext()) {
                    Map.Entry<String, Long> entry = iterator.next();
                    String contentServerId = entry.getKey();
                    long lastSeen = entry.getValue();

                    // Check if content server hasn't communicated in 30 seconds
                    if ((currentTime - lastSeen) > 30000) {
                        // Remove all stations from this content server
                        Iterator<Map.Entry<String, String>> stationIterator = stationToContentServer.entrySet().iterator();
                        while (stationIterator.hasNext()) {
                            Map.Entry<String, String> stationEntry = stationIterator.next();
                            if (stationEntry.getValue().equals(contentServerId)) {
                                String stationId = stationEntry.getKey();
                                weatherStore.remove(stationId);
                                stationIterator.remove();
                                removedStations++;
                            }
                        }
                        iterator.remove();
                        removedServers++;
                    }
                }

                if (removedServers > 0) {
                    System.out.println("EXPIRY: Removed " + removedServers + " content servers and " +
                            removedStations + " weather stations (30-second rule)");
                    saveWeatherData();
                }
            }
        }, 5, 5, TimeUnit.SECONDS); // Check every 5 seconds for efficiency and responsiveness
    }

    /**
     * Handler for individual client connections
     * Implements full HTTP protocol with proper Lamport clock handling
     */
    private class ClientHandler implements Runnable {
        private final Socket clientSocket;

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                 PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

                // Read HTTP request line
                String requestLine = in.readLine();
                if (requestLine == null) return;

                System.out.println("Request: " + requestLine + " [Clock: " + clock.tick() + "]");

                String[] parts = requestLine.split(" ");
                if (parts.length < 2) {
                    sendResponse(out, 400, "Bad Request", "Invalid HTTP request format");
                    return;
                }

                String method = parts[0];
                String path = parts[1];

                // Read headers to get Lamport clock and other info
                Map<String, String> headers = readHeaders(in);

                // Update our clock if Lamport-Clock header is present
                if (headers.containsKey("lamport-clock")) {
                    try {
                        int receivedClock = Integer.parseInt(headers.get("lamport-clock"));
                        clock.update(receivedClock);
                        System.out.println("Updated Lamport clock to: " + clock.getTime() +
                                " (received: " + receivedClock + ")");
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid Lamport clock in header: " + headers.get("lamport-clock"));
                    }
                }

                // Handle different HTTP methods as per assignment specification
                switch (method) {
                    case "GET":
                        handleGET(out, path);
                        break;
                    case "PUT":
                        handlePUT(in, out, headers);
                        break;
                    default:
                        // Assignment: Any request other than GET or PUT should return status 400
                        sendResponse(out, 400, "Bad Request", "Only GET and PUT methods supported");
                        break;
                }

            } catch (IOException e) {
                System.err.println("Error handling client: " + e.getMessage());
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    System.err.println("Error closing client socket: " + e.getMessage());
                }
            }
        }

        /**
         * Read HTTP headers into a map
         */
        private Map<String, String> readHeaders(BufferedReader in) throws IOException {
            Map<String, String> headers = new HashMap<>();
            String line;

            while ((line = in.readLine()) != null && !line.trim().isEmpty()) {
                String[] headerParts = line.split(":", 2);
                if (headerParts.length == 2) {
                    headers.put(headerParts[0].trim().toLowerCase(), headerParts[1].trim());
                }
            }
            return headers;
        }

        /**
         * Handle GET request - return aggregated weather data
         * Implements Lamport clock ordering for concurrent operations
         */
        private void handleGET(PrintWriter out, String path) {
            clock.tick(); // Increment clock for processing event

            synchronized (storeLock) {
                if (weatherStore.isEmpty()) {
                    sendResponse(out, 204, "No Content", "");
                    return;
                }

                // Create aggregated JSON response using Gson
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                Collection<WeatherData> weatherCollection = weatherStore.values();
                String jsonResponse = gson.toJson(weatherCollection);

                sendResponse(out, 200, "OK", jsonResponse, "application/json");
                System.out.println("GET: Served " + weatherStore.size() +
                        " weather stations [Clock: " + clock.getTime() + "]");
            }
        }

        /**
         * Handle PUT request - store weather data from content server
         * Implements assignment requirements:
         * - 201 for first time, 200 for updates
         * - Content server tracking for 30-second expiry
         * - JSON validation
         * - Lamport clock synchronization
         */
        private void handlePUT(BufferedReader in, PrintWriter out, Map<String, String> headers) {
            clock.tick(); // Increment clock for processing event

            try {
                // Get content server identifier (use IP:port as unique ID)
                String contentServerId = clientSocket.getRemoteSocketAddress().toString();

                // Read content length
                int contentLength = 0;
                if (headers.containsKey("content-length")) {
                    try {
                        contentLength = Integer.parseInt(headers.get("content-length"));
                    } catch (NumberFormatException e) {
                        sendResponse(out, 400, "Bad Request", "Invalid Content-Length header");
                        return;
                    }
                }

                // Assignment: Sending no content should return 204
                if (contentLength == 0) {
                    sendResponse(out, 204, "No Content", "");
                    return;
                }

                // Read JSON body
                char[] buffer = new char[contentLength];
                int totalRead = 0;
                while (totalRead < contentLength) {
                    int read = in.read(buffer, totalRead, contentLength - totalRead);
                    if (read == -1) break;
                    totalRead += read;
                }

                String jsonBody = new String(buffer, 0, totalRead);
                System.out.println("PUT: Received " + jsonBody.length() + " chars from " + contentServerId);

                // Parse and validate JSON
                try {
                    WeatherData weatherData = WeatherData.fromJson(jsonBody);

                    // Assignment: Reject entries with no valid ID
                    if (weatherData.getId() == null || weatherData.getId().trim().isEmpty()) {
                        sendResponse(out, 500, "Internal Server Error", "Missing or invalid station ID");
                        return;
                    }

                    synchronized (storeLock) {
                        String stationId = weatherData.getId();
                        boolean isFirstTime = !weatherStore.containsKey(stationId);

                        // Update content server tracking (critical for 30-second expiry)
                        long currentTime = System.currentTimeMillis();
                        contentServerLastSeen.put(contentServerId, currentTime);
                        stationToContentServer.put(stationId, contentServerId);

                        // Store weather data
                        weatherStore.put(stationId, weatherData);
                        saveWeatherData();

                        // Assignment: Return 201 for first time, 200 for updates
                        if (isFirstTime) {
                            sendResponse(out, 201, "Created", "");
                            System.out.println("PUT: Created new station " + stationId +
                                    " from " + contentServerId + " [Clock: " + clock.getTime() + "]");
                        } else {
                            sendResponse(out, 200, "OK", "");
                            System.out.println("PUT: Updated station " + stationId +
                                    " from " + contentServerId + " [Clock: " + clock.getTime() + "]");
                        }
                    }

                } catch (Exception e) {
                    // Assignment: Invalid JSON should return 500
                    System.err.println("PUT: JSON parsing error: " + e.getMessage());
                    sendResponse(out, 500, "Internal Server Error", "Invalid JSON format");
                }

            } catch (IOException e) {
                System.err.println("PUT: Error reading request: " + e.getMessage());
                sendResponse(out, 500, "Internal Server Error", "Error reading request");
            }
        }

        /**
         * Send HTTP response with Lamport clock header
         */
        private void sendResponse(PrintWriter out, int code, String message, String body) {
            sendResponse(out, code, message, body, "text/plain");
        }

        private void sendResponse(PrintWriter out, int code, String message, String body, String contentType) {
            out.println("HTTP/1.1 " + code + " " + message);
            out.println("Content-Type: " + contentType);
            out.println("Lamport-Clock: " + clock.getTime());
            if (!body.isEmpty()) {
                out.println("Content-Length: " + body.length());
            }
            out.println(); // Empty line to end headers
            if (!body.isEmpty()) {
                out.print(body);
            }
            out.flush();
        }
    }

    public static void main(String[] args) {
        int port = 4567; // Assignment default port

        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number: " + args[0]);
                System.err.println("Usage: java AggregationServer [port]");
                System.exit(1);
            }
        }

        AggregationServer server = new AggregationServer(port);

        // Graceful shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                System.out.println("\nShutting down Weather Aggregation Server...");
                server.stop();
                System.out.println("Server stopped gracefully");
            } catch (IOException e) {
                System.err.println("Error during shutdown: " + e.getMessage());
            }
        }));

        try {
            server.start();
        } catch (IOException e) {
            System.err.println("Failed to start Weather Aggregation Server: " + e.getMessage());
            System.exit(1);
        }
    }
}