# TripTaste

TripTaste is an AI-enhanced local lifestyle service platform based on the Black Horse Dianping project.

The project keeps the original high-concurrency backend capabilities and introduces AI services and a Skill-based Agent framework to provide intelligent recommendation, content analysis, and business assistance.

The system combines traditional Java backend engineering with AI application development, focusing on scalability, reliability, and safe AI integration.

---

# 1. Project Overview

## Core Business Features

TripTaste provides common local lifestyle service capabilities:

- User authentication and session management
- Shop search and category query
- Nearby shop recommendation
- User review and social interaction
- Coupon flash sale
- Order creation and payment workflow


## AI Enhanced Features

Additional AI capabilities:

- Natural language shop recommendation
- AI-generated shop summaries
- Review risk analysis
- Skill-based Agent orchestration framework

---

# 2. System Architecture

The system adopts a separated architecture:

```
                Client
                   |
             Backend Service
                   |
        +---------------------+
        | Business Services   |
        +---------------------+
                   |
        +---------------------+
        | AI Service / Agent  |
        +---------------------+
                   |
        Redis / MySQL / MQ
```

## Design Principles

- Core business logic is handled by backend services.
- AI services provide recommendation and analysis capabilities.
- High-risk operations are validated by backend systems.
- LLM does not directly execute sensitive operations.

Example:

```
User Request

      |

LLM / Skill Agent

      |

Generate Recommendation or Draft

      |

Backend Validation

      |

Business Execution
```

---

# 3. Technology Stack

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

# 4. Core Implementation

## 4.1 Authentication and Session Management

Implemented:

- Phone verification login
- Token-based authentication
- Redis-based session storage
- Automatic token refresh mechanism

Components:

```
LoginInterceptor

RefreshTokenInterceptor
```

---

# 4.2 Shop Query and Cache Optimization

Implemented Cache Aside architecture.

Optimization strategies:

## Cache Penetration Prevention

- Cache empty results
- Avoid repeated invalid database queries


## Cache Breakdown Prevention

- Mutex lock
- Logical expiration


## Cache Avalanche Prevention

- Random TTL
- Hot data preloading

---

# 4.3 Nearby Shop Search

Implemented location-based shop discovery.

Features:

- Redis GEO storage
- Distance-based sorting
- Nearby shop recommendation


Flow:

```
User Location

      |

Redis GEO Query

      |

Sorted Shop List
```

---

# 4.4 Review and Social System

Implemented:

- Review publishing
- Like interaction
- Follow relationship
- Feed stream generation
- Popular content ranking

---

# 5. High-Concurrency Flash Sale System

Designed a coupon flash sale system with asynchronous order processing.

## Architecture

```
User Request

      |

Redis Lua Validation

      |

RabbitMQ

      |

Order Consumer

      |

MySQL Persistence
```

---

## Key Implementation

### Atomic Stock Validation

Used Redis Lua scripts to guarantee atomic operations:

- Inventory check
- One-user-one-order validation
- Duplicate request prevention


### Asynchronous Order Creation

Used RabbitMQ for traffic buffering:

Benefits:

- Reduce database pressure
- Improve system stability
- Handle traffic spikes


### Order Reliability

Implemented:

- Consumer idempotency
- Retry mechanism
- Compensation tasks
- Distributed lock with Redisson

---

# 6. AI Recommendation Service

Implemented AI-powered shop recommendation.

## Workflow

```
User Query

      |

Intent Understanding

      |

Shop Retrieval

      |

Recommendation Generation

      |

Response
```

---

## API

```
POST /ai/assistant/recommend
```

Example request:

```json
{
  "query": "recommend restaurants suitable for couples"
}
```

Response:

```json
{
  "intentSummary": "...",
  "recommendShops": [],
  "keywords": []
}
```

Optimization:

- Redis caching for AI results
- Reduce repeated LLM calls
- Improve response latency

---

# 7. AI Shop Summary

Implemented automatic shop reputation summarization.

## Pipeline

```
User Reviews

      |

Review Information Extraction

      |

Shop Profile Generation

      |

LLM Summary
```

Features:

- Review aggregation
- AI summary generation
- Result caching

---

# 8. AI Review Risk Detection

Implemented AI-based review risk analysis.

## API

```
POST /ai/review/risk-check
```

Features:

- Risk classification
- Reason generation
- Suggestion output


Reliability:

- Exception handling
- Local fallback strategy
- Prevent AI failure from affecting core services

---

# 9. Skill Agent Framework

Implemented a lightweight Skill-based Agent framework.

Location:

```
src/main/java/com/hmdp/skill
```

---

# Architecture

```
User Request

      |

Skill Router

      |

Skill Registry

      |

Skill Executor

      |

Business Capability
```

---

# Components

## Skill Router

Responsible for:

- Understanding user intent
- Selecting suitable skills


## Skill Registry

Responsible for:

- Skill registration
- Skill lifecycle management
- Skill availability control


## Skill Executor

Responsible for:

- Parameter validation
- Permission checking
- Skill execution


## User Skill Profile

Stores:

- User preferences
- Feedback information

---

# Built-in Skills

Implemented:

```
shop_recommend_skill

shop_summary_skill

review_risk_check_skill

order_draft_skill
```

`order_draft_skill` only generates order drafts and does not directly execute payment operations.

---

# 10. API Design

## Business APIs

```
POST /user/login

GET /shop/{id}

GET /shop-type/list

POST /blog

POST /voucher-order/seckill
```

---

## AI APIs

```
POST /ai/assistant/recommend

GET /ai/shop/{shopId}/summary

POST /ai/review/risk-check
```

---

## Skill APIs

```
GET /skill/registry

POST /skill/execute

POST /skill/agent/chat

POST /skill/feedback
```

---

# 11. Engineering Design Highlights

## High-Concurrency System

Implemented:

- Redis atomic operations
- MQ asynchronous processing
- Distributed locking
- Idempotent order processing


## Cache Optimization

Solved:

- Cache penetration
- Cache breakdown
- Cache avalanche


## Service Reliability

Implemented:

- Exception handling
- Retry mechanism
- Fallback strategy
- Compensation tasks


## AI Safety Boundary

LLM is responsible for:

- Intent understanding
- Recommendation generation


Backend services are responsible for:

- Data validation
- Permission checking
- Transaction execution

---

# 12. Project Improvements

## Current Improvements

- Added AI service fault tolerance
- Optimized AI result caching
- Improved Skill execution safety


## Future Improvements

- Persistent Skill Registry
- Multi-Skill orchestration
- Agent evaluation system
- AI service monitoring
- Recommendation effectiveness analysis

---

# 13. Running the Project

## Requirements

- MySQL
- Redis
- RabbitMQ


## Start Backend

```bash
cd dianping-nginx-1.18.0

mvn clean compile

mvn spring-boot:run
```

Access:

```
http://127.0.0.1:8080
```

---

# Summary

TripTaste combines Java backend engineering with AI Agent development.

The project demonstrates:

- High-concurrency backend design
- Redis and MQ based system optimization
- Distributed transaction handling
- AI service integration
- Skill-based Agent architecture
- Reliable and safe AI application design
