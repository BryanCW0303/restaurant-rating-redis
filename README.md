# restaurant-rating-redis

A backend demo project built with **Spring Boot**, inspired by Yelp, featuring user accounts, shops, blogs, comments, social follows, and voucher ordering.  
The project uses **Redis** for caching, distributed ID generation, locks, and token/session management. Data persistence is handled by **MyBatis** mappers and MySQL.

---

## 🧩 Features

- **User Module** – Login, token-based auth, password encryption.
- **Shop & ShopType** – CRUD, caching, and category management.
- **Blog & Comments** – Posting, commenting, and infinite scroll feeds.
- **Follow System** – Follow/unfollow and personalized feed.
- **Voucher System** – Normal & flash-sale vouchers with Redis-based distributed locks.
- **Upload Module** – File upload handling.
- **Redis Infrastructure**
  - Logical expiration, cache rebuild, distributed locks
  - ID generation via `RedisIdWorker`
  - Token refresh interceptor
  - Global exception handling

---

## ⚙️ Tech Stack

- **Backend**: Java JDK 11, Spring Boot, Spring MVC
- **Persistence**: MyBatis, MySQL
- **Cache**: Redis, Redisson
- **Build Tool**: Maven

