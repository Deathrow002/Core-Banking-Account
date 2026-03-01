# Core-Banking-Account

Account microservice for the Core Banking system. Manages bank accounts, balances, and account operations.

## Features

- Create, read, update, delete bank accounts
- Balance checking and validation
- Account lookup by customer ID
- Redis caching for performance
- Kafka integration for event-driven architecture
- JWT authentication with role-based access control

## Tech Stack

- Java 17+ with Spring Boot
- PostgreSQL database
- Redis for caching
- Apache Kafka for messaging
- Eureka for service discovery

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