package weather.system;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.*;
import java.net.*;

/**
 * GET client that retrieves weather data from the aggregation server
 * and displays it in a readable format. Implements Lamport clocks.
 */
public class GETClient {
    private final String serverUrl;
    private final String stationId;
    private final LamportClock clock;

    public GETClient(String serverUrl, String stationId) {
        this.serverUrl = normalizeUrl(serverUrl);
        this.stationId = stationId;
        this.clock = new LamportClock();

        System.out.println("GETClient initialized");
        System.out.println("Server URL: " + this.serverUrl);
        if (stationId != null) {
            System.out.println("Station ID filter: " + stationId);
        }
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
     * Retrieve weather data from the server
     */
    public boolean getWeatherData() {
        try {
            // Create URL and connection
            URL url = new URL(serverUrl + "/weather.json");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            // Set up GET request
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "ATOMClient/1.0");
            connection.setRequestProperty("Lamport-Clock", String.valueOf(clock.tick()));

            // Send request and get response
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

            System.out.println("GET Response: " + responseCode + " " + responseMessage +
                    " [Clock: " + clock.getTime() + "]");

            if (responseCode == 200) {
                // Read response body
                StringBuilder response = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line).append("\n");
                    }
                }

                String jsonResponse = response.toString().trim();
                if (!jsonResponse.isEmpty()) {
                    displayWeatherData(jsonResponse);
                    return true;
                } else {
                    System.out.println("No weather data available");
                    return false;
                }

            } else if (responseCode == 204) {
                System.out.println("No weather data available (server returned 204 No Content)");
                return false;
            } else {
                System.err.println("Server returned error code: " + responseCode);

                // Try to read error message
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.err.println("Error: " + line);
                    }
                } catch (Exception e) {
                    // Ignore if we can't read error stream
                }
                return false;
            }

        } catch (IOException e) {
            System.err.println("Error connecting to server: " + e.getMessage());
            System.err.println("Make sure the server is running at: " + serverUrl);
            return false;
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Display weather data in a readable format, with optional station ID filtering
     * Assignment specifies: "stripped of JSON formatting and displayed, one line at a time"
     */
    private void displayWeatherData(String jsonData) {
        try {
            JsonElement element = JsonParser.parseString(jsonData);

            if (element.isJsonArray()) {
                // Handle array of weather data
                JsonArray weatherArray = element.getAsJsonArray();
                System.out.println("\n=== Weather Data Feed ===");
                System.out.println("Total stations available: " + weatherArray.size());

                boolean foundMatch = false;
                for (int i = 0; i < weatherArray.size(); i++) {
                    JsonElement weatherElement = weatherArray.get(i);
                    if (weatherElement.isJsonObject()) {
                        WeatherData weather = new Gson().fromJson(weatherElement, WeatherData.class);

                        // Filter by station ID if specified
                        if (stationId == null || stationId.equals(weather.getId())) {
                            displaySingleWeatherStation(weather, foundMatch ? -1 : 1);
                            foundMatch = true;
                        }
                    }
                }

                if (stationId != null && !foundMatch) {
                    System.out.println("No weather data found for station ID: " + stationId);
                }

            } else if (element.isJsonObject()) {
                // Handle single weather data object
                WeatherData weather = new Gson().fromJson(element, WeatherData.class);

                // Filter by station ID if specified
                if (stationId == null || stationId.equals(weather.getId())) {
                    System.out.println("\n=== Weather Data ===");
                    displaySingleWeatherStation(weather, 1);
                } else {
                    System.out.println("No weather data found for station ID: " + stationId);
                }
            } else {
                System.err.println("Unexpected JSON format received");
            }

        } catch (Exception e) {
            System.err.println("Error parsing weather data: " + e.getMessage());
            System.out.println("Raw response: " + jsonData);
        }
    }

    /**
     * Display a single weather station's data
     * Assignment: "stripped of JSON formatting and displayed, one line at a time, with the attribute and its value"
     */
    private void displaySingleWeatherStation(WeatherData weather, int stationNumber) {
        if (stationNumber > 0) {
            System.out.println("\n--- Weather Station " + stationNumber + " ---");
        } else {
            System.out.println("");
        }

        // Display each attribute on its own line as specified
        if (weather.getId() != null)
            System.out.println("id: " + weather.getId());
        if (weather.getName() != null)
            System.out.println("name: " + weather.getName());
        if (weather.getState() != null)
            System.out.println("state: " + weather.getState());
        if (weather.getTimeZone() != null)
            System.out.println("time_zone: " + weather.getTimeZone());

        System.out.println("lat: " + weather.getLat());
        System.out.println("lon: " + weather.getLon());

        if (weather.getLocalDateTime() != null)
            System.out.println("local_date_time: " + weather.getLocalDateTime());
        if (weather.getLocalDateTimeFull() != null)
            System.out.println("local_date_time_full: " + weather.getLocalDateTimeFull());

        System.out.println("air_temp: " + weather.getAirTemp());
        System.out.println("apparent_t: " + weather.getApparentT());

        if (weather.getCloud() != null)
            System.out.println("cloud: " + weather.getCloud());

        System.out.println("dewpt: " + weather.getDewpt());
        System.out.println("press: " + weather.getPress());
        System.out.println("rel_hum: " + weather.getRelHum());

        if (weather.getWindDir() != null)
            System.out.println("wind_dir: " + weather.getWindDir());

        System.out.println("wind_spd_kmh: " + weather.getWindSpdKmh());
        System.out.println("wind_spd_kt: " + weather.getWindSpdKt());
    }

    /**
     * Retry mechanism for failed requests
     */
    public boolean getWeatherDataWithRetry(int maxRetries, int retryDelayMs) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            System.out.println("Attempt " + attempt + " of " + maxRetries);

            if (getWeatherData()) {
                return true;
            }

            if (attempt < maxRetries) {
                System.out.println("Retrying in " + retryDelayMs + "ms...");
                try {
                    Thread.sleep(retryDelayMs);
                } catch (InterruptedException e) {
                    System.err.println("Retry interrupted");
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        System.err.println("Failed to get weather data after " + maxRetries + " attempts");
        return false;
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java GETClient <server_url> [station_id]");
            System.err.println("Examples:");
            System.err.println("  java GETClient localhost:4567");
            System.err.println("  java GETClient http://localhost:4567 IDS60901");
            System.err.println("  java GETClient servername.domain.com:8080");
            System.exit(1);
        }

        String serverUrl = args[0];
        String stationId = args.length > 1 ? args[1] : null;

        GETClient client = new GETClient(serverUrl, stationId);

        System.out.println("Requesting weather data from server...");

        // Try to get weather data with retry mechanism
        boolean success = client.getWeatherDataWithRetry(3, 2000);

        if (success) {
            System.out.println("\nWeather data retrieved successfully!");
        } else {
            System.err.println("\nFailed to retrieve weather data.");
            System.err.println("Please check:");
            System.err.println("1. Server is running at: " + serverUrl);
            System.err.println("2. Network connectivity");
            System.err.println("3. Server has weather data available");
            System.exit(1);
        }
    }
}