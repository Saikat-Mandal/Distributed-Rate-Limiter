# Distributed Rate Limiter & Edge Controller

A highly scalable, cloud-agnostic request-throttling microservice designed to protect backend systems from traffic spikes, DDoS attacks, and resource abuse. This project implements an API gateway-level bouncer using **Spring Boot/Cloud**, **Redis**, and atomic **Lua scripting** to enforce rate-limiting rules seamlessly across a distributed system.

## 🚀 Key Features
* **Distributed Architecture:** Completely stateless Java application layer sharing a centralized, high-performance memory cache.
* **Token Bucket Strategy:** Provides flexible handling for burst traffic while enforcing strict sustained limits.
* **Atomicity & Consistency:** Leverages server-side Lua scripts inside Redis to eradicate concurrency race conditions without slow distributed locks.
* **High Performance:** In-memory caching guarantees sub-millisecond evaluation windows (< 2ms latency overhead).
* **Cluster Support:** Designed to scale horizontally across multi-node Redis cluster shards using consistent hashing slot allocations.

---

## 🛠️ System Architecture

The system segregates the stateless application layer from the stateful storage tier to handle traffic scaling smoothly:

1. **Interception:** A client request lands on the API/Edge layer.
2. **Evaluation:** The Java application pulls zero local state. It executes an atomic Lua script over a fast TCP socket directly to the standalone Redis instance.
3. **Lazy-Refill Calculation:** The script calculates token regeneration reactively based on the elapsed time delta since the client's last active request:
   `Tokens Generated = (Current Time - Last Updated Time) * Refill Rate`
4. **Enforcement:** If a token is available, the script decrements the count and returns `1` (Success). If empty, it leaves the state intact and returns `0` (Block), allowing Java to immediately terminate the request with an `HTTP 429 Too Many Requests` response.

---

## 💻 Tech Stack
* **Framework:** Spring Boot 3.x, Spring Web
* **Data Layer:** Spring Data Redis (Lettuce Driver)
* **Database/Cache:** Redis 7.x (Standalone & Clustered topologies)
* **Containerization:** Docker
* **Scripting Engine:** Lua

---

## 📦 Getting Started & Installation

### Prerequisites
* Java 17 or higher
* Maven 3.6+
* Docker Desktop installed and running

### 1. Spin up the Redis Cluster Environment
To replicate a production cluster environment on your local machine, run the following Docker command to establish a multi-node master/replica topology:
```bash
docker run -p 7000:7000 -p 7001:7001 -p 7002:7002 \
  -p 7003:7003 -p 7004:7004 -p 7005:7005 \
  --name redis-cluster-bouncer -d grokzen/redis-cluster:latest 
  ```

### 2. Configure the Application
* Verify that your src/main/resources/application.properties points to your clustered nodes correctly:
* server.port=8080

```bash
server.port=8080

# Redis Cluster Shards
spring.data.redis.cluster.nodes=localhost:7000,localhost:7001,localhost:7002
```

###  3. Run the Application
* Compile and start up your Spring Boot server:
```bash
mvn clean install
mvn spring-boot:run
```

### 🧪 Testing the Bouncer
* You can verify the rate-limiting capabilities using cURL or any REST client. The endpoint is pre-configured to allow a maximum capacity of 5 tokens refilling at a rate of 1 token per second.
* Execute this script rapidly in your terminal to simulate high traffic burst for a specific user:
```bash
for i in {1..7}; do curl "http://localhost:8080/api/v1/secure-data?userId=alex"; echo ""; done 
```
### Expected Output
```bash
Access Granted! Here is your highly secure data.
Access Granted! Here is your highly secure data.
Access Granted! Here is your highly secure data.
Access Granted! Here is your highly secure data.
Access Granted! Here is your highly secure data.
HTTP Error 429: Too Many Requests. Please slow down!
HTTP Error 429: Too Many Requests. Please slow down!
```

* Wait 3 seconds to let the lazy-refill regenerate tokens, then execute the command again to watch access become granted once more.


### Core Production Implementations Explained
* If the mathematical evaluation window happened inside the Java Virtual Machine, a multi-server setup would succumb to severe race conditions under high concurrency. If two separate instances read a token balance of 1 at the exact same microsecond, both would process the request and violate the limit.
  Because Redis is natively single-threaded and executes Lua scripts atomically, the entire transaction (Read -> Refill Math -> Deduct -> Write) acts as an unbreakable instruction sequence. This guarantees absolute data integrity across multiple client instances with zero distributed lock overhead.

### Hash Slot Sharding & Scalability
* In a standard Redis Cluster setup, keys are divided into 16,384 physical Hash Slots using a CRC16 % 16384 algorithm. The microservice structures user keys cleanly (e.g., rate:alex).
  For advanced multi-key transactional checks (such as keeping a user's separate login throttling metrics and payment throttling metrics synchronized on the exact same physical database server), the architecture implements Hash Tags by wrapping identifiers in curly braces (e.g., rate:{alex}:login). This forces Redis to evaluate only the encapsulated text, ensuring the keys hash to the same physical node slot for local, hyper-fast execution blocks.


![image.png](../../Desktop/image.png)