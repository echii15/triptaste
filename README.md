# TripTaste

TripTaste is an AI-enhanced local lifestyle service platform based on the Black Horse Dianping project.

The project maintains the original high-concurrency backend capabilities, including caching, flash sale, asynchronous order processing, and distributed consistency. It also introduces AI services and a Skill-based Agent framework for recommendation, content analysis, and intelligent business workflows.

---

## Project Highlights

- Designed high-concurrency backend services with Redis, MQ, and MySQL
- Implemented flash sale system with atomic stock control and asynchronous order processing
- Built AI services for shop recommendation, review analysis, and content summarization
- Designed a lightweight Skill Agent framework for modular AI capability management
- Added fault tolerance mechanisms to isolate AI service failures from core business flows

---

# Architecture Overview

The system consists of three layers:
Client
|
Backend Service
|
+----------------------+
| Business Logic Layer |
+----------------------+
|
+----------------------+
| AI Service / Agent |
+----------------------+
|
Redis / MySQL / MQ

Core business services handle transaction-sensitive operations, while AI services provide recommendation and analysis capabilities.

High-risk operations such as ordering and payment are always validated by backend services instead of being directly controlled by LLM.

---

# Technology Stack

## Backend

- Java 8
- Spring Boot
- MyBatis-Plus
- MySQL
- Redis
- Redisson
- RabbitMQ
- Nginx

## AI Service

- Java 17
- Spring Boot 3
- Spring AI
- LLM API Integration

---

# Core Features

## 1. Authentication and User Session

Implemented:

- Phone verification login
- Token-based authentication
- Redis-based session storage
- Automatic token expiration refresh

---

## 2. Shop Query and Cache System

Implemented Cache Aside strategy.

Optimizations:

- Empty cache to prevent cache penetration
- Mutex lock and logical expiration to prevent cache breakdown
- TTL strategy and cache warming to reduce cache avalanche

---

## 3. Nearby Shop Search

Implemented location-based search:

- Store shop coordinates using Redis GEO
- Support nearby shop queries sorted by distance

---

## 4. Review and Social Features

Implemented:

- Review publishing
- Like interaction
- Follow relationship
- Feed stream generation
- Popular content ranking

---

# 5. Flash Sale System

Designed a high-concurrency coupon ordering system.

## Request Flow
User Request
|
Redis Lua Validation
|
RabbitMQ
|
Order Consumer
|
MySQL Persistence

## Implementation

- Redis Lua script for atomic stock deduction
- One-user-one-order validation
- RabbitMQ asynchronous order creation
- Consumer idempotency
- Failure retry and compensation mechanism
- Redisson distributed lock

Ensured inventory consistency and reliable order creation under high concurrency.

---

# 6. AI Recommendation Service

Implemented AI-powered shop recommendation.

## Features

- Natural language query understanding
- Shop retrieval
- Recommendation reason generation

Example API:
POST /ai/assistant/recommend

Input:

```json
{
  "query": "nearby restaurants suitable for couples"
}
Output:

{
  "intentSummary": "...",
  "recommendShops": [],
  "keywords": []
}

Added Redis caching for AI results to reduce repeated model calls.
7. AI Shop Summary

Implemented automatic shop reputation summarization.

Pipeline:

User Reviews

    |

Review Information Extraction

    |

Shop Profile Generation

    |

LLM Summary

Features:

Review aggregation
AI-generated summaries
Result caching
8. AI Review Risk Detection

Implemented review content risk analysis.

API:

POST /ai/review/risk-check

Functions:

Risk classification
Reason generation
Suggestion output

Added fallback handling to prevent AI service failures from affecting the main system.

9. Skill Agent Framework

Implemented a lightweight Skill-based Agent framework.

Directory:

src/main/java/com/hmdp/skill
Components
Skill Router

Responsible for:

User intent understanding
Skill selection
Skill Registry

Responsible for:

Skill registration
Skill lifecycle management
Skill Executor

Responsible for:

Parameter validation
Permission checking
Skill execution
User Skill Profile

Stores:

User preferences
Feedback information
Built-in Skills

Implemented:

shop_recommend_skill

shop_summary_skill

review_risk_check_skill

order_draft_skill

order_draft_skill only generates order drafts and does not directly execute payment operations.

API Design
Business APIs
POST /user/login

GET /shop/{id}

GET /shop-type/list

POST /blog

POST /voucher-order/seckill
AI APIs
POST /ai/assistant/recommend

GET /ai/shop/{shopId}/summary

POST /ai/review/risk-check
Skill APIs
GET /skill/registry

POST /skill/execute

POST /skill/agent/chat

POST /skill/feedback
Engineering Improvements
1. High-Concurrency Design

Applied:

Redis atomic operations
MQ asynchronous processing
Distributed locking
Idempotent consumer design
2. Cache Optimization

Solved:

Cache penetration
Cache breakdown
Cache avalanche
3. Service Reliability

Implemented:

Exception handling
Fallback strategy
Retry mechanism
Compensation task
4. AI Safety Boundary

LLM is responsible for:

Intent understanding
Recommendation generation

Backend services are responsible for:

Data validation
Permission control
Transaction execution
Running the Project
Start Dependencies

Required:

MySQL
Redis
RabbitMQ
Start Backend
cd dianping-nginx-1.18.0

mvn clean compile

mvn spring-boot:run

Access:

http://127.0.0.1:8080
Future Improvements
Persistent Skill Registry
Multi-Skill orchestration
Agent evaluation system
AI service monitoring
Recommendation effectiveness analysis
Summary

TripTaste combines traditional Java backend engineering with AI application development.

The project demonstrates:

High-concurrency backend design
Distributed system practices
Cache and MQ optimization
AI service integration
Agent capability orchestration
