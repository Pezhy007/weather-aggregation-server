package weather.system;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for WeatherData model
 * Tests JSON serialization/deserialization and data expiry
 */
public class WeatherDataTest {
    private WeatherData weatherData;

    @Before
    public void setUp() {
        weatherData = new WeatherData();
        weatherData.setId("IDS60901");
        weatherData.setName("Adelaide (West Terrace)");
        weatherData.setState("SA");
        weatherData.setTimeZone("CST");
        weatherData.setLat(-34.9);
        weatherData.setLon(138.6);
        weatherData.setLocalDateTime("15/04:00pm");
        weatherData.setLocalDateTimeFull("20230715160000");
        weatherData.setAirTemp(13.3);
        weatherData.setApparentT(9.5);
        weatherData.setCloud("Partly cloudy");
        weatherData.setDewpt(5.7);
        weatherData.setPress(1023.9);
        weatherData.setRelHum(60);
        weatherData.setWindDir("S");
        weatherData.setWindSpdKmh(15);
        weatherData.setWindSpdKt(8);
    }

    @Test
    public void testWeatherDataCreation() {
        System.out.println("TEST: Weather data object creation");
        assertNotNull("Weather data should not be null", weatherData);
        assertEquals("ID should be set correctly", "IDS60901", weatherData.getId());
        assertEquals("Air temperature should be set correctly", 13.3, weatherData.getAirTemp(), 0.01);
        System.out.println("✓ Weather data creation works correctly");
    }

    @Test
    public void testJsonSerialization() {
        System.out.println("TEST: JSON serialization");
        String json = weatherData.toJson();

        assertNotNull("JSON should not be null", json);
        assertTrue("JSON should contain ID", json.contains("IDS60901"));
        assertTrue("JSON should contain temperature", json.contains("13.3"));
        assertTrue("JSON should contain state", json.contains("SA"));

        System.out.println("Generated JSON:");
        System.out.println(json.substring(0, Math.min(200, json.length())) + "...");
        System.out.println("✓ JSON serialization works correctly");
    }

    @Test
    public void testJsonDeserialization() {
        System.out.println("TEST: JSON deserialization");
        String json = "{\n" +
                "  \"id\": \"TEST001\",\n" +
                "  \"name\": \"Test Station\",\n" +
                "  \"state\": \"NSW\",\n" +
                "  \"time_zone\": \"EST\",\n" +
                "  \"lat\": -33.8,\n" +
                "  \"lon\": 151.2,\n" +
                "  \"air_temp\": 25.5,\n" +
                "  \"rel_hum\": 70\n" +
                "}";

        WeatherData parsed = WeatherData.fromJson(json);

        assertNotNull("Parsed weather data should not be null", parsed);
        assertEquals("ID should be parsed correctly", "TEST001", parsed.getId());
        assertEquals("Temperature should be parsed correctly", 25.5, parsed.getAirTemp(), 0.01);
        assertEquals("Humidity should be parsed correctly", 70, parsed.getRelHum());
        System.out.println("✓ JSON deserialization works correctly");
    }

    @Test
    public void testRoundTripJsonConversion() {
        System.out.println("TEST: Round-trip JSON conversion");
        String json = weatherData.toJson();
        WeatherData parsed = WeatherData.fromJson(json);

        assertEquals("ID should survive round trip", weatherData.getId(), parsed.getId());
        assertEquals("Name should survive round trip", weatherData.getName(), parsed.getName());
        assertEquals("Temperature should survive round trip",
                weatherData.getAirTemp(), parsed.getAirTemp(), 0.01);
        assertEquals("Humidity should survive round trip",
                weatherData.getRelHum(), parsed.getRelHum());
        System.out.println("✓ Round-trip JSON conversion works correctly");
    }

    @Test
    public void testDataExpiry() throws InterruptedException {
        System.out.println("TEST: Data expiry mechanism");
        WeatherData freshData = new WeatherData();
        freshData.setId("FRESH001");

        assertFalse("Fresh data should not be expired", freshData.isExpired());

        // Create data with old timestamp (simulate expired data)
        WeatherData oldData = new WeatherData();
        oldData.setId("OLD001");
        oldData.setLastUpdated(System.currentTimeMillis() - 31000); // 31 seconds ago

        assertTrue("Old data should be expired", oldData.isExpired());
        System.out.println("✓ Data expiry mechanism works correctly");
    }

    @Test
    public void testTimestampUpdating() {
        System.out.println("TEST: Timestamp updating");
        long initialTime = weatherData.getLastUpdated();

        // Wait a small amount and update
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // Continue with test
        }

        weatherData.setLastUpdated(System.currentTimeMillis());
        long newTime = weatherData.getLastUpdated();

        assertTrue("New timestamp should be greater than initial", newTime > initialTime);
        System.out.println("✓ Timestamp updating works correctly");
    }

    @Test
    public void testInvalidJsonHandling() {
        System.out.println("TEST: Invalid JSON handling");
        try {
            WeatherData.fromJson("invalid json {");
            fail("Should throw exception for invalid JSON");
        } catch (Exception e) {
            System.out.println("✓ Invalid JSON properly throws exception: " + e.getClass().getSimpleName());
        }
    }

    @Test
    public void testMissingFieldsInJson() {
        System.out.println("TEST: JSON with missing fields");
        String minimalJson = "{\"id\": \"MIN001\"}";

        WeatherData minimal = WeatherData.fromJson(minimalJson);
        assertNotNull("Minimal weather data should not be null", minimal);
        assertEquals("ID should be set", "MIN001", minimal.getId());
        assertNull("Name should be null for missing field", minimal.getName());
        assertEquals("Default air temp should be 0", 0.0, minimal.getAirTemp(), 0.01);
        System.out.println("✓ Missing fields handled correctly");
    }
}