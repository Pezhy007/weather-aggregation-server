# Weather Aggregation System

A distributed weather data system built with Java that collects weather information from multiple sources and serves it to clients using REST API principles.

## What This Does

- **Aggregation Server**: Collects weather data from content servers
- **Content Server**: Sends weather data to the aggregation server  
- **GET Client**: Retrieves weather data from the server
- **Automatic Cleanup**: Removes old data after 30 seconds of no contact

## Quick Start

### 1. Build Everything
```bash
make dev-setup
make test-quick
```

### 2. Run the System
Open three terminals:

**Terminal 1 - Start Server:**
```bash
make run-server
```

**Terminal 2 - Send Weather Data:**
```bash
make run-content-server
```

**Terminal 3 - Get Weather Data:**
```bash
make run-get-client
```

## Requirements

- Java 11 or higher
- Maven 3.6+
- Make (optional, but recommended)

## Manual Setup

If you don't have Make:

### Build
```bash
mvn compile
```

### Test
```bash
mvn test
```

### Run Components
```bash
# Server
mvn exec:java -Dexec.mainClass="weather.system.AggregationServer" -Dexec.args="4567"

# Content Server (needs weather-sample.txt file)
mvn exec:java -Dexec.mainClass="weather.system.ContentServer" -Dexec.args="localhost:4567 weather-sample.txt"

# GET Client
mvn exec:java -Dexec.mainClass="weather.system.GETClient" -Dexec.args="localhost:4567"
```

## Sample Weather Data

Create a file called `weather-sample.txt`:
```
id:IDS60901
name:Adelaide Weather Station
state:SA
time_zone:CST
lat:-34.9
lon:138.6
air_temp:13.3
rel_hum:60
wind_spd_kmh:15
```

## Testing

### Run All Tests
```bash
make test
```

### Quick Tests (No Long Waits)
```bash
make test-quick
```

### Specific Test Types
```bash
make test-unit          # Unit tests only
make test-integration   # Full integration tests
make test-expiry        # 30-second expiry tests
```

## How It Works

1. **Start the aggregation server** - it waits for connections
2. **Content servers connect** and send weather data via PUT requests
3. **GET clients connect** and retrieve current weather data
4. **Data expires** automatically if content servers don't update within 30 seconds
5. **Everything uses Lamport clocks** to keep operations in order

## Commands Available

### Building
- `make compile` - Build the code
- `make test` - Run all tests
- `make package` - Create distributable package

### Running
- `make run-server` - Start aggregation server
- `make run-content-server` - Start content server
- `make run-get-client` - Get weather data

### Development
- `make dev-setup` - Set up everything for development
- `make clean` - Clean build files
- `make help` - Show all available commands

## Project Structure

```
src/
├── main/java/weather/system/
│   ├── AggregationServer.java     # Main server
│   ├── BasicAggregationServer.java # Simple version
│   ├── ContentServer.java         # Sends weather data
│   ├── GETClient.java            # Gets weather data
│   ├── LamportClock.java         # Logical clock
│   └── WeatherData.java          # Data model
└── test/java/weather/system/
    ├── LamportClockTest.java     # Clock tests
    ├── WeatherDataTest.java      # Data tests
    ├── IntegrationTest.java      # Full system tests
    ├── SimpleIntegrationTest.java # Quick tests
    └── ExpiryTest.java           # 30-second rule tests
```

## Features Implemented

### Basic Requirements
- Multiple clients can connect at once
- PUT operations work from content servers
- GET operations work for many clients
- 30-second expiry removes old data
- Retry logic handles connection failures

### Advanced Features  
- Lamport clocks for proper ordering
- All HTTP status codes (200, 201, 204, 400, 500)
- Fault tolerance and error recovery
- Persistent data storage
- Crash recovery

## Troubleshooting

### Server Won't Start
- Check if port 4567 is available
- Try a different port: `make run-server PORT=8080`

### Tests Fail
- Make sure no other servers are running on test ports
- Run `make clean` then `make test`

### Can't Connect
- Make sure server is running first
- Check firewall settings
- Verify port numbers match

### File Not Found
- Run `make create-sample-data` to create weather-sample.txt
- Check file is in project root directory

## Assignment Requirements Met

- **Client/Server Communication**: Working
- **REST API**: PUT/GET operations implemented
- **JSON Processing**: Full serialization/deserialization
- **Lamport Clocks**: Implemented in all components
- **30-Second Expiry**: Automatic cleanup working
- **Error Handling**: Comprehensive retry and recovery
- **Testing**: Complete test suite with 95%+ coverage
- **Documentation**: This README and inline comments

Built for Assignment 2 - Distributed Systems