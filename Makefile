# Simple Weather System Makefile

# Build everything
build:
	mvn compile

# Run tests
test:
	mvn test -Dtest=LamportClockTest,WeatherDataTest,SimpleIntegrationTest

# Create sample data file
sample:
	echo "id:IDS60901" > weather-sample.txt
	echo "name:Adelaide Weather Station" >> weather-sample.txt
	echo "state:SA" >> weather-sample.txt
	echo "time_zone:CST" >> weather-sample.txt
	echo "lat:-34.9" >> weather-sample.txt
	echo "lon:138.6" >> weather-sample.txt
	echo "air_temp:13.3" >> weather-sample.txt
	echo "rel_hum:60" >> weather-sample.txt
	echo "wind_spd_kmh:15" >> weather-sample.txt

# Run server
server: build sample
	mvn exec:java -Dexec.mainClass=weather.system.AggregationServer -Dexec.args=4567

# Run content server
content: sample
	mvn exec:java -Dexec.mainClass=weather.system.ContentServer -Dexec.args="localhost:4567 weather-sample.txt"

# Run GET client
client:
	mvn exec:java -Dexec.mainClass=weather.system.GETClient -Dexec.args="localhost:4567"

# Clean up
clean:
	mvn clean
	rm -f weather-sample.txt weather_data.*

# Package for submission
package: test
	mvn package

.PHONY: build test sample server content client clean package