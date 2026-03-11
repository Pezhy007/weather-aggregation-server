package weather.system;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.*;
import java.net.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Content server that reads weather data from files and sends it to the aggregation server
 * Implements Lamport clocks and periodic updates with retry logic
 */
public class ContentServer {
    private final String serverUrl;
    private final String filePath;
    private final LamportClock clock;
    private final ScheduledExecutorService scheduler;

    public ContentServer(String serverUrl, String filePath) {
        this.serverUrl = normalizeUrl(serverUrl);
        this.filePath = filePath;
        this.clock = new LamportClock();
        this.scheduler = Executors.newScheduledThreadPool(1);

        System.out.println("ContentServer initialized");
        System.out.println("Server URL: " + this.serverUrl);
        System.out.println("File path: " + this.filePath);
        System.out.println("Lamport Clock: " + clock.getTime());
    }

    /**
     * Normalize URL to include protocol if missing
     */
    private String normalizeUrl(String url) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return "http://" + url;
        }
        return url;
    }

    /**
     * Parse weather data file into WeatherData object
     */
    private WeatherData parseWeatherFile(String filePath) throws IOException {
        WeatherData weatherData = new WeatherData();

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                String[] parts = line.split(":", 2);
                if (parts.length != 2) continue;

                String key = parts[0].trim();
                String value = parts[1].trim();

                // Map file fields to WeatherData properties
                switch (key.toLowerCase()) {
                    case "id":
                        weatherData.setId(value);
                        break;
                    case "name":
                        weatherData.setName(value);
                        break;
                    case "state":
                        weatherData.setState(value);
                        break;
                    case "time_zone":
                        weatherData.setTimeZone(value);
                        break;
                    case "lat":
                        try {
                            weatherData.setLat(Double.parseDouble(value));
                        } catch (NumberFormatException e) {
                            System.err.println("Invalid latitude: " + value);
                        }
                        break;
                    case "lon":
                        try {
                            weatherData.setLon(Double.parseDouble(value));
                        } catch (NumberFormatException e) {
                            System.err.println("Invalid longitude: " + value);
                        }
                        break;
                    case "local_date_time":
                        weatherData.setLocalDateTime(value);
                        break;
                    case "local_date_time_full":
                        weatherData.setLocalDateTimeFull(value);
                        break;
                    case "air_temp":
                        try {
                            weatherData.setAirTemp(Double.parseDouble(value));
                        } catch (NumberFormatException e) {
                            System.err.println("Invalid air temperature: " + value);
                        }
                        break;
                    case "apparent_t":
                        try {
                            weatherData.setApparentT(Double.parseDouble(value));
                        } catch (NumberFormatException e) {
                            System.err.println("Invalid apparent temperature: " + value);
                        }
                        break;
                    case "cloud":
                        weatherData.setCloud(value);
                        break;
                    case "dewpt":
                        try {
                            weatherData.setDewpt(Double.parseDouble(value));
                        } catch (NumberFormatException e) {
                            System.err.println("Invalid dew point: " + value);
                        }
                        break;
                    case "press":
                        try {
                            weatherData.setPress(Double.parseDouble(value));
                        } catch (NumberFormatException e) {
                            System.err.println("Invalid pressure: " + value);
                        }
                        break;
                    case "rel_hum":
                        try {
                            weatherData.setRelHum(Integer.parseInt(value));
                        } catch (NumberFormatException e) {
                            System.err.println("Invalid relative humidity: " + value);
                        }
                        break;
                    case "wind_dir":
                        weatherData.setWindDir(value);
                        break;
                    case "wind_spd_kmh":
                        try {
                            weatherData.setWindSpdKmh(Integer.parseInt(value));
                        } catch (NumberFormatException e) {
                            System.err.println("Invalid wind speed kmh: " + value);
                        }
                        break;
                    case "wind_spd_kt":
                        try {
                            weatherData.setWindSpdKt(Integer.parseInt(value));
                        } catch (NumberFormatException e) {
                            System.err.println("Invalid wind speed kt: " + value);
                        }
                        break;
                }
            }
        }

        // Validate required fields
        if (weatherData.getId() == null || weatherData.getId().trim().isEmpty()) {
            throw new IOException("Weather data must have a valid ID");
        }

        return weatherData;
    }

    /**
     * Send weather data to aggregation server via PUT request
     */
    public boolean sendWeatherData() {
        try {
            // Parse weather file
            WeatherData weatherData = parseWeatherFile(filePath);
            String jsonData = weatherData.toJson();

            // Parse URL
            URL url = new URL(serverUrl + "/weather.json");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            // Set up PUT request
            connection.setRequestMethod("PUT");
            connection.setRequestProperty("User-Agent", "ATOMClient/1.0");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Content-Length", String.valueOf(jsonData.length()));
            connection.setRequestProperty("Lamport-Clock", String.valueOf(clock.tick()));
            connection.setDoOutput(true);

            // Send JSON data
            try (PrintWriter writer = new PrintWriter(connection.getOutputStream())) {
                writer.print(jsonData);
            }

            // Read response
            int responseCode = connection.getResponseCode();
            String responseMessage = connection.getResponseMessage();

            // Update clock from server response
            String serverClock = connection.getHeaderField("Lamport-Clock");
            if (serverClock != null) {
                try {
                    clock.update(Integer.parseInt(serverClock));
                } catch (NumberFormatException e) {
                    System.err.println("Invalid Lamport clock from server: " + serverClock);
                }
            }

            System.out.println("PUT Response: " + responseCode + " " + responseMessage +
                    " [Clock: " + clock.getTime() + "]");

            if (responseCode == 200 || responseCode == 201) {
                System.out.println("Successfully sent weather data for station: " + weatherData.getId());
                return true;
            } else {
                System.err.println("Server returned error code: " + responseCode);
                return false;
            }

        } catch (IOException e) {
            System.err.println("Error sending weather data: " + e.getMessage());
            return false;
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Start periodic updates to the server
     */
    public void startPeriodicUpdates(int intervalSeconds) {
        System.out.println("Starting periodic updates every " + intervalSeconds + " seconds");

        scheduler.scheduleWithFixedDelay(() -> {
            System.out.println("\n--- Periodic Update ---");
            boolean success = sendWeatherData();
            if (!success) {
                System.err.println("Failed to send update, will retry next interval");
            }
        }, 0, intervalSeconds, TimeUnit.SECONDS);
    }

    /**
     * Stop periodic updates
     */
    public void stop() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java ContentServer <server_url> <file_path>");
            System.err.println("Examples:");
            System.err.println("  java ContentServer localhost:4567 weather-sample.txt");
            System.err.println("  java ContentServer http://localhost:4567 weather-sample.txt");
            System.exit(1);
        }

        String serverUrl = args[0];
        String filePath = args[1];

        ContentServer contentServer = new ContentServer(serverUrl, filePath);

        // Send initial data
        System.out.println("Sending initial weather data...");
        boolean success = contentServer.sendWeatherData();

        if (success) {
            System.out.println("Initial data sent successfully!");

            // Start periodic updates every 15 seconds (well under 30s expiry limit)
            // This ensures the content server maintains its connection to avoid expiry
            contentServer.startPeriodicUpdates(15);

            System.out.println("Content server running with 15s updates (30s expiry rule)...");
            System.out.println("Press Ctrl+C to stop");

            // Shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\nShutting down content server...");
                contentServer.stop();
            }));

            // Keep main thread alive
            try {
                Thread.sleep(Long.MAX_VALUE);
            } catch (InterruptedException e) {
                System.out.println("Content server interrupted");
            }
        } else {
            System.err.println("Failed to send initial data. Check server connection and file path.");
            System.exit(1);
        }
    }
}