# Core-Banking-Account

Account microservice for the Core Banking system. Manages bank accounts, balances, and account operations.

## Features

- Create, read, update, delete bank accounts
- Balance checking and validation
- Account lookup by customer ID
- Redis caching for performance
- Kafka integration for event-driven architecture
- Shared Redis-backed session authentication (SESSION cookie + CSRF) with role-based access control

## Tech Stack

- Java 17+ with Spring Boot
- PostgreSQL database
- Redis for caching and as the shared Spring Session store
- Apache Kafka for messaging
- Eureka for service discovery

## Authentication

This service does not log users in. It authenticates every request from the **shared Redis-backed session** created by the Authentication service:

- The browser sends the `SESSION` cookie (HttpOnly, `SameSite=Lax`, raw/non-Base64 id). Spring Session loads it from Redis (namespace `core-banking:session`, 30 min timeout) and Spring Security reads the `SPRING_SECURITY_CONTEXT` attribute, so the roles (`ROLE_ADMIN` / `ROLE_MANAGER` / `ROLE_USER`) and the `username` / `customerId` / `role` details drive `@PreAuthorize` checks.
- CSRF protection is enabled: mutating requests (`POST` / `PUT` / `DELETE`) must echo the `XSRF-TOKEN` cookie (issued by the Authentication service) in the `X-XSRF-TOKEN` header.
- Unauthenticated requests get `401 {"error":"Unauthorized"}` (no redirects). `/actuator/**` is public.
- Calls to other services through the load-balanced `RestTemplate` forward the caller's `Cookie` and `X-XSRF-TOKEN` headers (`SessionForwardingInterceptor`).
- Kafka messages from Transaction / Loan carry the session id in the `X-Session-Id` header; `SessionKafkaMessageConverter` resolves the user from the same Redis session.

## API Endpoints

| Method | Endpoint | Description | Roles |
|--------|----------|-------------|-------|
| GET | `/accounts/validateAccount?accNo={uuid}` | Check if account exists | ADMIN, MANAGER, USER |
| GET | `/accounts/getAccount?accNo={uuid}` | Get account details | ADMIN, MANAGER, USER |
| GET | `/accounts/getAccountByCustomerId?customerId={uuid}` | Get accounts by customer | ADMIN, MANAGER, USER |
| GET | `/accounts/getAllAccounts` | List all accounts | ADMIN |
| POST | `/accounts/createAccount` | Create new account | ADMIN, MANAGER |
| PUT | `/accounts/updateAccount` | Update account | ADMIN, MANAGER |
| DELETE | `/accounts/deleteAccount` | Delete account | ADMIN |

## Configuration

Default port: `8081`

```properties
spring.application.name=account-service
eureka.client.service-url.defaultZone=http://discovery-service:8761/eureka
spring.datasource.url=jdbc:postgresql://postgres:5432/lmwn_db
spring.data.redis.host=redis
spring.data.redis.port=6379
spring.session.data.redis.namespace=core-banking:session
spring.session.timeout=30m
```

## Running Locally

```bash
./mvnw spring-boot:run
```

## Docker

```bash
docker build -t account-service .
docker run -p 8081:8081 account-service
```