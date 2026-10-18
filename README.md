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

This service does not log users in. Original browser routes authenticate from the **shared Redis-backed session** created by the Authentication service:

- The browser sends the `SESSION` cookie (HttpOnly, `SameSite=Lax`, raw/non-Base64 id). Spring Session loads it from Redis (namespace `core-banking:session`, 30 min timeout) and Spring Security reads the `SPRING_SECURITY_CONTEXT` attribute, so the roles (`ROLE_ADMIN` / `ROLE_MANAGER` / `ROLE_USER`) and the `username` / `customerId` / `role` details drive `@PreAuthorize` checks.
- CSRF protection is enabled: mutating requests (`POST` / `PUT` / `DELETE`) must echo the `XSRF-TOKEN` cookie (issued by the Authentication service) in the `X-XSRF-TOKEN` header.
- Unauthenticated requests get `401 {"error":"Unauthorized"}` (no redirects). `/actuator/**` is public.
- Browser REST requests use no request cache: a 401 never saves servlet request/cookie objects in the shared Redis session or creates a session for redirect purposes.
- Calls to other services through the load-balanced `RestTemplate` forward the caller's `Cookie` and `X-XSRF-TOKEN` headers (`SessionForwardingInterceptor`).
- Kafka messages from Transaction / Loan carry the session id in the `X-Session-Id` header; `SessionKafkaMessageConverter` resolves the user from the same Redis session.

### Mobile OAuth2 / OIDC

Every original account route also has an alias formed by **`/api/mobile` + the complete original absolute path**.
For example, `POST /accounts/createAccount` becomes `POST /api/mobile/accounts/createAccount`;
`GET /accounts/getAccount?accNo=...` becomes `GET /api/mobile/accounts/getAccount?accNo=...`.
All seven handler methods are reused with their existing `@PreAuthorize` rules and business logic.

- `/api/mobile/**` uses a higher-priority, stateless, bearer-only chain. Cookies cannot authenticate it;
  it neither loads nor saves a security context in the browser session, caches requests, nor exposes login/logout/CSRF endpoints.
  CSRF is disabled **only** for this mobile chain; browser writes still require the cookie/header pair.
- RS256 signatures are verified against the internal JWK endpoint; issuer, audience, expiry and not-before timestamps
  are validated. The `banking` scope is required. `sub` is the username/email, `customerId` is a nonempty string,
  and `role` must be exactly `ROLE_ADMIN`, `ROLE_MANAGER` or `ROLE_USER` (no second `ROLE_` prefix).
  `Authentication.getName()` is `sub`; the details are a `HashMap` containing `username`, `customerId`, `role`,
  matching the shared-session helper.
- The native public PKCE client obtains five-minute access tokens from Authentication. It has no refresh-token grant;
  renew access through authorization-code + PKCE reauthorization, not a stored refresh token.
- Authenticated mobile `RestTemplate` calls forward only the validated `Authorization: Bearer ...` token,
  remove Cookie/CSRF headers and prefix downstream paths exactly once, preserving query encoding.
  Browser forwarding is unchanged. Customer validation keeps its original role rules (ADMIN/MANAGER);
  enabling mobile authentication does not grant USER additional downstream privileges.

```properties
app.oauth.issuer=${OAUTH_ISSUER:http://localhost:8080}
app.oauth.jwk-set-uri=${OAUTH_JWK_SET_URI:http://authentication-service:8084/oauth2/jwks}
app.oauth.audience=core-banking-api
```

**Async boundary:** Account REST mutations are synchronous and do not publish mobile JWTs to Kafka.
Existing Transaction/Loan-to-Account asynchronous balance messages still depend on `X-Session-Id` and
Redis-backed sessions. There is no trusted credential-free mobile Kafka authorization protocol here.
Do not put reusable access tokens into Kafka or substitute one for a session id. Mobile flows requiring
that asynchronous path must be rejected before enqueue until such a protocol exists.
The legacy balance consumer's permissive invalid-session handling is unchanged; it is not a mobile authorization mechanism.

### Phase 1 mobile money-mutation policy

Mobile money mutations require **both** `banking` and `transactions.write`. Missing either scope returns **403**.
Even with both scopes, Phase 1 returns **503 before calling business services or persisting changes**,
pending Phase 2 signed commands/outbox authorization:

- `PUT /api/mobile/accounts/updateAccountBalance` (direct balance-setting bypass).
- `POST /api/mobile/accounts/createAccount` when the requested opening balance is nonzero
  (including negative values; cannot mint or destroy money through account opening).
- `DELETE /api/mobile/accounts/deleteAccount` (can destroy an account's existing balance).

Zero-balance account creation and all reads remain available with the normal `banking` scope and existing roles.
Customer profile operations are unaffected. Browser paths, role checks and CSRF requirements are unchanged.
No reusable JWT is sent through Kafka. Safe admin read probes are `GET /api/mobile/accounts/getAllAccounts`
and, in Customer, `GET /api/mobile/customers/all`; neither requires parameters (200 with admin token, 401 without).

## API Endpoints

| Method | Endpoint | Description | Roles |
|--------|----------|-------------|-------|
| GET | `/accounts/validateAccount?accNo={uuid}` | Check if account exists | ADMIN, MANAGER, USER |
| GET | `/accounts/getAccount?accNo={uuid}` | Get account details | ADMIN, MANAGER, USER |
| GET | `/accounts/getAccountByCustomerId?customerId={uuid}` | Get accounts by customer | ADMIN, MANAGER, USER |
| GET | `/accounts/getAllAccounts` | List all accounts | ADMIN |
| POST | `/accounts/createAccount` | Create new account | ADMIN, MANAGER, USER |
| PUT | `/accounts/updateAccountBalance` | Update balance | ADMIN, MANAGER, USER |
| DELETE | `/accounts/deleteAccount?accNo={uuid}` | Delete account | ADMIN, MANAGER |

The same table applies to mobile after prepending `/api/mobile` to each endpoint.

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
docker build -f Account/Dockerfile -t account-service .
docker run -p 8081:8081 account-service
```

Run the build from the repository root: the Dockerfile copies the local root POM and Account sources,
not remote `main`. Runtime stage and exposed port are unchanged.

## Focused authentication tests

From the repository root, run `.\mvnw.cmd -pl Account -Dtest=MobileSecurityTest,MobileForwardingInterceptorTest,AccountServiceMobileTest,SessionForwardingInterceptorTest,SessionContractSerializationTest,SessionKafkaMessageConverterTest test`.
Security tests exercise the real filter chains and controller method authorization using fixed test-only RSA keys;
no issuer, Redis, Kafka, database or network infrastructure is required. HTTP tests verify bearer-only URI rewriting.