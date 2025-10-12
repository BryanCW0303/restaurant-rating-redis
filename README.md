# restaurant-rating-redis

A backend demo project built with **Spring Boot**, inspired by Yelp.  
This system covers user login, merchant browsing, coupon flash sales, blogs, follows, feeds, and location-based recommendations.  
It demonstrates **high-performance caching**, **distributed locking**, **asynchronous message processing**, and **geo queries** — all powered by Redis.

---

## Key Highlights

- **Spring Boot + MyBatis + Redis + Redisson**
- **JWT-like token mechanism**
- **Global unique ID generation** via Redis
- **High-concurrency voucher seckill system**
- **Feed stream implementation using Redis ZSET**
- **Geo-location based merchant query**
- **Message queues** built on Redis Stream, Pub/Sub, and List
- **User check-in tracking** with Bitmap
- **UV (unique visitor) counting** with HyperLogLog

---

## Features Overview

### 🧾 User & Authentication
- SMS-based login & registration flow
- Session replacement with Redis
- Login state auto-refresh via interceptor
- User info desensitization
- Token-based authentication (`LoginInterceptor`, `RefreshTokenInterceptor`)

### Shop & Cache System
- Shop and category data caching (`RedisConstants`)
- Logical expiration and cache rebuild strategies
- Cache penetration / breakdown / avalanche protection
- Double-write consistency between DB and cache
- `RedisData` wrapper for logical expiration
- Mutex lock and logical expiration solutions
- Encapsulated `RedisUtils` helper class

### Voucher & Flash Sale
- Global unique ID generator (`RedisIdWorker`)
- Seckill voucher implementation
- One-person-one-order rule enforcement
- Optimistic locking to prevent overselling
- Distributed locks (`ILock`, `SimpleRedisLock`, `Redisson`)
- Async order creation using Redis Streams
- MultiLock & WatchDog mechanism (Redisson)
- Seckill optimization: eligibility check + async order queue

### 📨 Redis Messaging & Asynchronous Processing
- Message queue demos using:
  - List (simple queue)
  - Pub/Sub (publish-subscribe)
  - Stream (single consumer & consumer group)
- Full async order flow with Stream consumer

### Blog & Social Interaction
- Post “explore notes” and blogs
- Like and ranking features using ZSET
- Follower & following system
- Feed stream push to inbox (fan-out)
- Scroll pagination (rolling feed)
- Common-follow detection

### Nearby Shops & GEO
- GEO data structure for storing shop coordinates
- Search nearby shops by type and distance

### User Sign-in & Statistics
- Daily sign-in tracking via Bitmap
- Continuous sign-in streak calculation
- Unique visitor counting with HyperLogLog

---

## Tech Stack

| Category | Technology |
|-----------|-------------|
| Backend | Java 11+, Spring Boot |
| Persistence | MyBatisPlus, MySQL |
| Caching / MQ | Redis, Redisson |
| Build Tool | Maven |
| Architecture | RESTful API, DTO + Entity + Mapper + Service |
| Utilities | Lua scripts, logical expiration, mutex locks |

---

## Redis Infrastructure

- Redis cache with key conventions (`RedisConstants`)
- Logical expiration / cache rebuild patterns
- Distributed lock abstractions (`ILock`, `SimpleRedisLock`) and **Redisson** config
- High-throughput, k-ordered **ID worker** (`RedisIdWorker`)
- Login/refresh token interceptors (`LoginInterceptor`, `RefreshTokenInterceptor`)
- Global exception advice (`WebExceptionAdvice`)

---

