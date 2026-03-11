# Weather Aggregation Server

A distributed weather data aggregation system built in Java using a RESTful API over raw sockets. Implements Lamport clocks for distributed synchronisation, crash recovery, and concurrent client/server consistency management. Developed as part of a third-year Distributed Systems course at the University of Adelaide.

## Overview

The system consists of three components communicating over HTTP:

```
Content Server (PUT) ──▶ Aggregation Server ──▶ GET Client
                              │
                         Persistent Store
                         (crash recovery)
```

- **Content Servers** upload weather data via HTTP PUT
- **Aggregation Server** stores, validates, and serves weather data
- **GET Clients** request and display aggregated weather feeds

## Features

- RESTful HTTP API (GET/PUT) implemented over raw Java sockets
- Lamport clocks across all entities for distributed event ordering
- Concurrent PUT serialisation ordered by Lamport timestamp
- 30-second content expiry — data from inactive content servers is automatically purged
- Crash recovery — persistent storage survives server restarts, including mid-write crashes
- Full HTTP status code handling (200, 201, 204, 400, 500)
- JSON parsing and validation of weather data feeds
- Fault-tolerant clients with retry on server unavailability
- Automated test suite covering single and multi-client scenarios, failure modes, and Lamport clock logic

## System Components

| File | Role |
|---|---|
| `AggregationServer.java` | Main server — handles PUT/GET, manages data expiry |
| `GETClient.java` | Read client — fetches and displays weather feed |
| `ContentServer.java` | Upload client — reads local file and PUTs to server |

## Build & Run

**Compile:**
```bash
javac *.java
```

**Start aggregation server (default port 4567):**
```bash
java AggregationServer
# or with custom port:
java AggregationServer 8080
```

**Run content server:**
```bash
java ContentServer http://localhost:4567 weather_data.txt
```

**Run GET client:**
```bash
java GETClient http://localhost:4567
# or with station ID:
java GETClient http://localhost:4567 IDS60901
```

## Technical Details

- **Language:** Java
- **Communication:** Raw BSD sockets (no HttpServer or web frameworks)
- **Synchronisation:** Lamport clocks embedded in all request headers
- **Consistency:** Concurrent PUTs serialised by Lamport timestamp
- **Persistence:** File-based storage with atomic write handling for crash safety
- **Expiry:** Background thread purges content from servers silent for 30+ seconds
- **JSON:** Gson library for parsing; custom validation logic

## Lamport Clock Implementation

Each entity (aggregation server, content server, GET client) maintains a local Lamport clock. The clock value is included in every request and response header. On receive, each entity updates its clock to `max(local, received) + 1`, ensuring a consistent global event ordering across the distributed system.
