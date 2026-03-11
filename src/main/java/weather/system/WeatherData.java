package weather.system;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Weather data model representing a weather station's information
 * Used for JSON serialization/deserialization
 */
public class WeatherData {
    private String id;
    private String name;
    private String state;
    private String time_zone;
    private double lat;
    private double lon;
    private String local_date_time;
    private String local_date_time_full;
    private double air_temp;
    private double apparent_t;
    private String cloud;
    private double dewpt;
    private double press;
    private int rel_hum;
    private String wind_dir;
    private int wind_spd_kmh;
    private int wind_spd_kt;

    // Timestamp for expiry management (not part of JSON)
    private transient long lastUpdated;

    public WeatherData() {
        this.lastUpdated = System.currentTimeMillis();
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getTimeZone() { return time_zone; }
    public void setTimeZone(String time_zone) { this.time_zone = time_zone; }

    public double getLat() { return lat; }
    public void setLat(double lat) { this.lat = lat; }

    public double getLon() { return lon; }
    public void setLon(double lon) { this.lon = lon; }

    public String getLocalDateTime() { return local_date_time; }
    public void setLocalDateTime(String local_date_time) { this.local_date_time = local_date_time; }

    public String getLocalDateTimeFull() { return local_date_time_full; }
    public void setLocalDateTimeFull(String local_date_time_full) { this.local_date_time_full = local_date_time_full; }

    public double getAirTemp() { return air_temp; }
    public void setAirTemp(double air_temp) { this.air_temp = air_temp; }

    public double getApparentT() { return apparent_t; }
    public void setApparentT(double apparent_t) { this.apparent_t = apparent_t; }

    public String getCloud() { return cloud; }
    public void setCloud(String cloud) { this.cloud = cloud; }

    public double getDewpt() { return dewpt; }
    public void setDewpt(double dewpt) { this.dewpt = dewpt; }

    public double getPress() { return press; }
    public void setPress(double press) { this.press = press; }

    public int getRelHum() { return rel_hum; }
    public void setRelHum(int rel_hum) { this.rel_hum = rel_hum; }

    public String getWindDir() { return wind_dir; }
    public void setWindDir(String wind_dir) { this.wind_dir = wind_dir; }

    public int getWindSpdKmh() { return wind_spd_kmh; }
    public void setWindSpdKmh(int wind_spd_kmh) { this.wind_spd_kmh = wind_spd_kmh; }

    public int getWindSpdKt() { return wind_spd_kt; }
    public void setWindSpdKt(int wind_spd_kt) { this.wind_spd_kt = wind_spd_kt; }

    public long getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(long lastUpdated) { this.lastUpdated = lastUpdated; }

    /**
     * Convert to JSON string
     * @return JSON representation of weather data
     */
    public String toJson() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(this);
    }

    /**
     * Create WeatherData from JSON string
     * @param json JSON string
     * @return WeatherData object
     */
    public static WeatherData fromJson(String json) {
        Gson gson = new Gson();
        WeatherData data = gson.fromJson(json, WeatherData.class);
        data.setLastUpdated(System.currentTimeMillis());
        return data;
    }

    /**
     * Check if this weather data is expired (older than 30 seconds)
     * @return true if expired, false otherwise
     */
    public boolean isExpired() {
        return (System.currentTimeMillis() - lastUpdated) > 30000; // 30 seconds
    }
}