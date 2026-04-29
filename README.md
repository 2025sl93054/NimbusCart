# 🛒 NimbusCart - Cloud-Native E-Commerce Microservices

A production-ready e-commerce platform built with **Spring Boot**, **Spring Cloud**, **Apache Kafka**, **Docker**, and **Kubernetes**.

## 🏗️ Architecture

```
User → API Gateway (8080) → Microservices → PostgreSQL / Kafka
```

| Service | Port | Description |
|---------|------|-------------|
| Discovery Server | 8761 | Netflix Eureka - Service Registry |
| API Gateway | 8080 | Spring Cloud Gateway - Single entry point |
| User Service | 8081 | Auth (JWT), Registration, User management |
| Product Service | 8082 | CRUD products, search, stock management |
| Order Service | 8083 | Order lifecycle, Feign calls, Kafka producer |
| Payment Service | 8084 | Mock payments, Kafka consumer/producer |

## 🗺️ Workflow Diagram

```mermaid
flowchart TD
    Client(["👤 Client / Browser"])

    subgraph Infrastructure["Infrastructure Layer"]
        DS["🔍 Discovery Server\nNetflix Eureka · :8761"]
    end

    subgraph Gateway["API Gateway · :8080"]
        GW["Spring Cloud Gateway\nRoute & Load Balance"]
    end

    subgraph Services["Microservices"]
        US["👤 User Service\n:8081\nRegister · Login · JWT"]
        PS["📦 Product Service\n:8082\nCRUD · Search · Stock"]
        OS["🧾 Order Service\n:8083\nOrchestrator · Feign · Kafka"]
        PAY["💳 Payment Service\n:8084\nMock Payment · Kafka"]
    end

    subgraph Databases["PostgreSQL Databases"]
        UDB[("userdb")]
        PDB[("productdb")]
        ODB[("orderdb")]
        PAYDB[("paymentdb")]
    end

    subgraph Kafka["Apache Kafka"]
        T1["topic: order-created\n3 partitions"]
        T2["topic: payment-result\n3 partitions"]
    end

    %% Client → Gateway
    Client -->|"HTTP Request"| GW

    %% Gateway ↔ Discovery
    GW <-->|"Service Lookup"| DS
    US <-->|"Register / Heartbeat"| DS
    PS <-->|"Register / Heartbeat"| DS
    OS <-->|"Register / Heartbeat"| DS
    PAY <-->|"Register / Heartbeat"| DS

    %% Gateway → Services
    GW -->|"/api/users/**"| US
    GW -->|"/api/products/**"| PS
    GW -->|"/api/orders/**"| OS

    %% Services → Databases
    US --- UDB
    PS --- PDB
    OS --- ODB
    PAY --- PAYDB

    %% Order flow (sync Feign calls)
    OS -->|"① Feign: validate user"| US
    OS -->|"② Feign: get product\n   reduce stock"| PS

    %% Order → Kafka → Payment
    OS -->|"③ Publish OrderEvent\nstatus: PENDING"| T1
    T1 -->|"④ Consume\n(payment-group)"| PAY

    %% Payment → Kafka → Order
    PAY -->|"⑤ Publish PaymentResult\nSUCCESS / FAILED"| T2
    T2 -->|"⑥ Consume\n(order-group)"| OS

    %% Auth annotation
    GW -.->|"JWT Validation"| US

    %% Status update
    OS -->|"⑦ Update status\nCONFIRMED / PAYMENT_FAILED"| ODB

    %% Styles
    classDef svc fill:#4A90D9,stroke:#2C5F8A,color:#fff
    classDef db fill:#F5A623,stroke:#C47D0E,color:#fff
    classDef kafka fill:#6B4FBB,stroke:#4A3490,color:#fff
    classDef infra fill:#27AE60,stroke:#1E8449,color:#fff
    classDef gw fill:#E74C3C,stroke:#C0392B,color:#fff

    class US,PS,OS,PAY svc
    class UDB,PDB,ODB,PAYDB db
    class T1,T2 kafka
    class DS infra
    class GW gw
```

## 🔁 Event Flow

```
1. User places order → API Gateway → Order Service
2. Order Service validates user (Feign → User Service)
3. Order Service checks stock (Feign → Product Service)
4. Order created (PENDING) → Kafka topic "order-created"
5. Payment Service consumes → processes payment
6. Payment result → Kafka topic "payment-result"
7. Order Service consumes → updates status to CONFIRMED/FAILED
```

## 🚀 Quick Start

### Prerequisites
- Java 17+
- Docker & Docker Compose
- Maven

### Run everything with Docker Compose:
```bash
docker-compose up --build
```

### Or run locally (start in order):
```bash
# 1. Start PostgreSQL & Kafka (via Docker)
docker-compose up postgres zookeeper kafka

# 2. Start Discovery Server
cd discovery-server && mvn spring-boot:run

# 3. Start API Gateway
cd api-gateway && mvn spring-boot:run

# 4. Start services (each in a separate terminal)
cd user-service && mvn spring-boot:run
cd product-service && mvn spring-boot:run
cd order-service && mvn spring-boot:run
cd payment-service && mvn spring-boot:run
```

## 📡 API Examples

### Register
```bash
curl -X POST http://localhost:8080/api/users/register \
  -H "Content-Type: application/json" \
  -d '{"name":"John","email":"john@test.com","password":"pass123"}'
```

### Login
```bash
curl -X POST http://localhost:8080/api/users/login \
  -H "Content-Type: application/json" \
  -d '{"email":"john@test.com","password":"pass123"}'
```

### Create Product
```bash
curl -X POST http://localhost:8080/api/products \
  -H "Content-Type: application/json" \
  -d '{"name":"Laptop","description":"Gaming Laptop","price":999.99,"stockQuantity":50,"category":"Electronics"}'
```

### Place Order
```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"productId":1,"quantity":2}'
```

## 🐳 Kubernetes Deployment
```bash
kubectl apply -f k8s/deployment.yml
```

## �� Project Structure
```
NimbusCart/
├── api-gateway/          # Spring Cloud Gateway
├── discovery-server/     # Netflix Eureka
├── user-service/         # User + JWT Auth
├── product-service/      # Product catalog
├── order-service/        # Order management + Kafka
├── payment-service/      # Mock payments + Kafka
├── k8s/                  # Kubernetes manifests
├── docker-compose.yml    # One-command local setup
└── init-db.sql           # Database initialization
```

## 🛠️ Tech Stack
- **Backend:** Spring Boot 3.2, Spring Cloud 2023.0
- **Gateway:** Spring Cloud Gateway
- **Discovery:** Netflix Eureka
- **Communication:** OpenFeign (sync) + Apache Kafka (async)
- **Security:** JWT (jjwt 0.12)
- **Database:** PostgreSQL 16
- **Containers:** Docker, Kubernetes
