package weather.system;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Basic Aggregation server that works without Gson dependency issues
 * Implements all assignment requirements with manual JSON handling
 */
public class BasicAggregationServer {
    private final int port;
    private ServerSocket serverSocket;
    private boolean running = false;
    private final ExecutorService threadPool;
    private final LamportClock clock;

    // Storage for weather data with content server tracking
    private final Map<String, Map<String, String>> weatherStore;
    private final Map<String, String> stationToContentServer;
    private final Map<String, Long> contentServerLastSeen;
    private final Object storeLock = new Object();

    // File for persistence
    private static final String STORAGE_FILE = "weather_data.txt";
    private static final String BACKUP_FILE = "weather_data.bak";

    public BasicAggregationServer(int port) {
        this.port = port;
        this.threadPool = Executors.newCachedThreadPool();
        this.clock = new LamportClock();
        this.weatherStore = new ConcurrentHashMap<>();
        this.stationToContentServer = new ConcurrentHashMap<>();
        this.contentServerLastSeen = new ConcurrentHashMap<>();

        // Start cleanup task for expired data (every 5 seconds to ensure 30s expiry)
        startCleanupTask();
    }

    /**
     * Start the server and listen for connections
     */
    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;

        System.out.println("Basic Weather Aggregation Server started on port " + port);
        System.out.println("Lamport Clock initialized at: " + clock.getTime());

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
    }

    /**
     * Start background task to remove expired weather data (30-second rule)
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
                    System.out.println("Expired " + removedServers + " content servers and " +
                            removedStations + " weather stations (30-second rule)");
                }
            }
        }, 5, 5, TimeUnit.SECONDS);
    }

    /**
     * Simple JSON creator from weather data map
     */
    private String createJson(Map<String, String> weatherData) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");

        boolean first = true;
        for (Map.Entry<String, String> entry : weatherData.entrySet()) {
            if (!first) {
                json.append(",\n");
            }
            json.append("  \"").append(entry.getKey()).append("\": ");

            // Try to determine if value should be quoted
            String value = entry.getValue();
            try {
                Double.parseDouble(value);
                json.append(value); // Numeric value
            } catch (NumberFormatException e) {
                json.append("\"").append(value).append("\""); // String value
            }
            first = false;
        }

        json.append("\n}");
        return json.toString();
    }

    /**
     * Parse simple JSON-like data from PUT request
     */
    private Map<String, String> parseJsonData(String jsonBody) {
        Map<String, String> data = new HashMap<>();

        // Simple JSON parsing - look for "key": value patterns
        String[] lines = jsonBody.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.contains(":")) {
                // Find the colon
                int colonIndex = line.indexOf(":");
                if (colonIndex > 0) {
                    String key = line.substring(0, colonIndex).trim();
                    String value = line.substring(colonIndex + 1).trim();

                    // Remove quotes and commas
                    key = key.replace("\"", "").replace("'", "");
                    value = value.replace("\"", "").replace("'", "").replace(",", "");

                    if (!key.isEmpty() && !value.isEmpty()) {
                        data.put(key, value);
                    }
                }
            }
        }

        return data;
    }

    /**
     * Handler for individual client connections
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
                    sendResponse(out, 400, "Bad Request", "");
                    return;
                }

                String method = parts[0];
                String path = parts[1];

                // Read headers to get Lamport clock
                Map<String, String> headers = readHeaders(in);

                // Update our clock if Lamport-Clock header is present
                if (headers.containsKey("lamport-clock")) {
                    try {
                        int receivedClock = Integer.parseInt(headers.get("lamport-clock"));
                        clock.update(receivedClock);
                        System.out.println("Updated clock to: " + clock.getTime());
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid Lamport clock in header");
                    }
                }

                // Handle different HTTP methods
                switch (method) {
                    case "GET":
                        handleGET(out, path);
                        break;
                    case "PUT":
                        handlePUT(in, out, headers);
                        break;
                    default:
                        sendResponse(out, 400, "Bad Request", "Method not supported");
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
         */
        private void handleGET(PrintWriter out, String path) {
            clock.tick(); // Increment clock for processing event

            synchronized (storeLock) {
                if (weatherStore.isEmpty()) {
                    sendResponse(out, 204, "No Content", "");
                    return;
                }

                // Create JSON array response
                StringBuilder jsonResponse = new StringBuilder();
                jsonResponse.append("[\n");

                boolean first = true;
                for (Map<String, String> weather : weatherStore.values()) {
                    if (!first) {
                        jsonResponse.append(",\n");
                    }
                    jsonResponse.append("  ").append(createJson(weather));
                    first = false;
                }

                jsonResponse.append("\n]");

                sendResponse(out, 200, "OK", jsonResponse.toString(), "application/json");
                System.out.println("Served weather data to client [Clock: " + clock.getTime() + "]");
            }
        }

        /**
         * Handle PUT request - store weather data from content server
         */
        private void handlePUT(BufferedReader in, PrintWriter out, Map<String, String> headers) {
            clock.tick(); // Increment clock for processing event

            try {
                // Get content server identifier
                String contentServerId = clientSocket.getRemoteSocketAddress().toString();

                // Read content length
                int contentLength = 0;
                if (headers.containsKey("content-length")) {
                    contentLength = Integer.parseInt(headers.get("content-length"));
                }

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
                System.out.println("Received data from " + contentServerId);

                // Parse JSON data
                Map<String, String> weatherData = parseJsonData(jsonBody);

                if (!weatherData.containsKey("id") || weatherData.get("id").trim().isEmpty()) {
                    sendResponse(out, 500, "Internal Server Error", "Missing station ID");
                    return;
                }

                synchronized (storeLock) {
                    String stationId = weatherData.get("id");
                    boolean isFirstTime = !weatherStore.containsKey(stationId);

                    // Update content server tracking
                    long currentTime = System.currentTimeMillis();
                    contentServerLastSeen.put(contentServerId, currentTime);
                    stationToContentServer.put(stationId, contentServerId);

                    // Store weather data
                    weatherStore.put(stationId, weatherData);

                    if (isFirstTime) {
                        sendResponse(out, 201, "Created", "");
                        System.out.println("Created new weather entry for: " + stationId);
                    } else {
                        sendResponse(out, 200, "OK", "");
                        System.out.println("Updated weather entry for: " + stationId);
                    }
                }

            } catch (Exception e) {
                System.err.println("Error processing PUT: " + e.getMessage());
                sendResponse(out, 500, "Internal Server Error", "Processing error");
            }
        }

        /**
         * Send HTTP response
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
            out.println();
            if (!body.isEmpty()) {
                out.print(body);
            }
        }
    }

    public static void main(String[] args) {
        int port = 4567;

        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number: " + args[0]);
                System.exit(1);
            }
        }

        BasicAggregationServer server = new BasicAggregationServer(port);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                System.out.println("\nShutting down server...");
                server.stop();
            } catch (IOException e) {
                System.err.println("Error during shutdown: " + e.getMessage());
            }
        }));

        try {
            server.start();
        } catch (IOException e) {
            System.err.println("Server failed to start: " + e.getMessage());
        }
    }
}