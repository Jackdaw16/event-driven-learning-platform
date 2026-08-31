# Event-Driven Learning Platform

Backend platform for managing online courses, enrollments, asynchronous payments
and certificate issuance.

Built with Java 21, Spring Boot 4, PostgreSQL and RabbitMQ.

The implementation focuses on transactional integrity, concurrency safety,
reliable asynchronous messaging, explicit authorization rules and a modular
architecture that can evolve without requiring premature service decomposition.

## Architecture

The application is implemented as a modular monolith with pragmatic hexagonal
boundaries.

The main modules are:

- `catalog`: categories, instructors and courses.
- `student`: student persistence and student-owned data.
- `enrollment`: enrollment lifecycle, seat reservation, cancellation, progress
  and relationship queries.
- `payment`: asynchronous payment processing.
- `certificate`: certificate issuance after enrollment completion.
- `auth`: authentication, JWT issuance and ownership authorization.
- `messaging`: RabbitMQ topology, transactional outbox, publication and
  consumer reliability.
- `shared`: cross-cutting infrastructure with no business ownership.

Business behavior remains inside the owning module. Application services
coordinate use cases and transactions, while persistence, HTTP and messaging
concerns are implemented in infrastructure adapters.

The modules currently share one PostgreSQL database and are deployed as one
Spring Boot application. RabbitMQ is used only where asynchronous communication
provides a meaningful boundary.

This deliberately avoids introducing distributed-system complexity where it is
not required while keeping business and integration boundaries explicit.

For the rationale behind the main architectural decisions, see the Architecture Decision Records:

- [ADR-0001 — Modular monolith with pragmatic hexagonal boundaries](docs/adr/ADR-0001.md)
- [ADR-0002 — Database-enforced concurrency and transactional integrity](docs/adr/ADR-0002.md)
- [ADR-0003 — Transactional outbox and at-least-once messaging](docs/adr/ADR-0003.md)

The README provides an operational overview; the ADRs document the context,
alternatives, rationale and consequences behind these decisions.

## Main workflows

### Enrollment

Creating an enrollment:

1. Validates the student and published course.
2. Atomically reserves one course seat.
3. Creates the enrollment as `PENDING_PAYMENT`.
4. Creates a `PENDING` payment from the course price snapshot.
5. Records the corresponding integration event in the transactional outbox.

The complete operation is transactional. A failure rolls back the enrollment,
payment and seat reservation together.

Enrollment requests support `Idempotency-Key` to make retries safe.

### Payment

Payment processing is asynchronous.

```text
EnrollmentCreated
        |
        v
 Payment processing
      /     \
     v       v
Confirmed  Failed
   |          |
   v          v
 ACTIVE    CANCELLED
              |
              v
        seat released
```

The local implementation uses a deterministic simulated payment outcome rather
than integrating with an external payment provider.

A confirmed payment activates a `PENDING_PAYMENT` enrollment.

A failed payment cancels a still-pending enrollment and releases its reserved
seat.

### Completion and certificate

Progress can be updated only for active enrollments.

When progress reaches `100`:

```text
ACTIVE
  |
  v
COMPLETED
  |
  v
EnrollmentCompleted
  |
  v
Certificate issued asynchronously
```

Certificate generation is protected both by message deduplication and a unique
certificate-per-enrollment database constraint.

## Messaging reliability

RabbitMQ communication uses at-least-once delivery semantics.

The implementation combines:

- explicit exchanges, queues, bindings and dead-letter queues;
- bounded retry with backoff;
- transactional outbox persistence;
- publisher confirms and mandatory routing;
- durable `processed_events` consumer deduplication;
- versioned JSON integration events;
- database-backed business idempotency.

Business state and outbound events are committed in the same PostgreSQL
transaction.

The outbox worker later publishes pending events to RabbitMQ and marks them as
published only after broker confirmation.

A crash may therefore cause an event to be delivered more than once, which is
expected under at-least-once delivery. Consumers are designed to make repeated
delivery safe instead of relying on exactly-once transport semantics.

No distributed transaction between PostgreSQL and RabbitMQ is used.

## Concurrency and data integrity

Course capacity is enforced in PostgreSQL rather than through an in-memory
check.

Seat reservation uses an atomic conditional update equivalent to:

```sql
UPDATE courses
SET occupied_seats = occupied_seats + 1
WHERE id = ?
  AND status = 'PUBLISHED'
  AND occupied_seats < maximum_seats;
```

Only a request that successfully updates the row owns the seat.

This prevents concurrent requests from exceeding course capacity, including
when several requests compete for the final available seat.

Other integrity mechanisms include:

- transactional enrollment and payment creation;
- database constraints preventing duplicate live enrollments;
- global payment idempotency keys;
- pessimistic locking for cancellation and selected workflow transitions;
- atomic seat release;
- unique certificate-per-enrollment constraints;
- transactional outbox and processed-event persistence.

Concurrency behavior is covered by PostgreSQL integration tests using
Testcontainers.

## Security

The API uses self-issued JWT Bearer tokens.

Available roles:

- `ADMIN`: global management and consultation.
- `INSTRUCTOR`: create, edit and publish owned courses and inspect enrollments
  for owned courses.
- `STUDENT`: enroll, update progress, cancel and inspect owned enrollments.

Authentication is performed through:

```http
POST /api/auth/token
```

Passwords are stored as BCrypt hashes.

JWTs contain the authenticated role and, for instructors and students, an
`actorId` used to enforce resource ownership.

Authorization distinguishes between:

- `401 Unauthorized`: missing or invalid authentication.
- `403 Forbidden`: authenticated principal without the required role or
  ownership.

A student cannot operate on another student's enrollment, and an instructor
cannot mutate another instructor's course.

Secrets such as `JWT_SECRET`, PostgreSQL passwords and RabbitMQ passwords are
provided through environment variables.

No production credentials are committed to the repository.

### Authentication provisioning

User registration and account provisioning are intentionally outside the scope
of this exercise.

The `auth_users` table stores authentication identities. `INSTRUCTOR` and
`STUDENT` users reference their corresponding business identity through
`principal_id`; `ADMIN` does not require one.

No default credentials are seeded.

## Relationship queries

The API provides paginated relationship queries for:

```http
GET /api/courses/{courseId}/students
GET /api/students/{studentId}/courses
```

These endpoints use query-specific joined projections rather than traversing
lazy entity relationships.

Pagination performs a bounded content query plus count query, keeping the number
of SQL statements constant with respect to the number of returned rows.

Integration tests explicitly verify this behavior to guard against N+1
regressions.

## Running locally

### Requirements

For the recommended setup only Docker with Docker Compose is required.

Java 21 is additionally required when running Maven commands directly from the
host.

### Configuration

Create a local environment file from the provided template.

Windows PowerShell:

```powershell
Copy-Item .env.example .env
```

Linux/macOS:

```bash
cp .env.example .env
```

Replace the placeholder values for:

- `POSTGRES_PASSWORD`
- `RABBITMQ_PASSWORD`
- `JWT_SECRET`

`JWT_SECRET` must contain at least 32 bytes.

The `.env` file is ignored by Git and must not contain committed credentials.

### Start the complete stack

```bash
docker compose up --build
```

This starts:

- PostgreSQL
- RabbitMQ
- the Spring Boot application

The application waits until PostgreSQL and RabbitMQ report healthy before
starting.

Default endpoints:

| Service | URL |
| --- | --- |
| API | `http://localhost:8080` |
| Swagger UI | `http://localhost:8080/swagger-ui/index.html` |
| OpenAPI | `http://localhost:8080/v3/api-docs` |
| Health | `http://localhost:8080/actuator/health` |
| RabbitMQ management | `http://localhost:15672` |

The application port can be overridden with `APP_PORT`.

### Stop the stack

```bash
docker compose down
```

To also remove persisted local database and RabbitMQ volumes:

```bash
docker compose down -v
```

## Observability

Spring Boot Actuator exposes:

```text
/actuator/health
/actuator/metrics
```

Health includes PostgreSQL and RabbitMQ health indicators without exposing
sensitive details.

Health is publicly accessible for operational probing.

Metrics require an authenticated `ADMIN` token.

No external monitoring or distributed tracing stack is included. The rationale
for keeping those optional operational components outside the current topology
is documented below.

## API documentation

Swagger UI is available at:

```text
http://localhost:8080/swagger-ui/index.html
```

The OpenAPI contract is available as:

```text
/v3/api-docs
/v3/api-docs.yaml
```

The OpenAPI definition includes the JWT Bearer authentication scheme.
`POST /api/auth/token` is explicitly documented as unauthenticated.

## Tests

Run the complete Maven test suite with:

Linux/macOS:

```bash
./mvnw test
```

Windows:

```powershell
.\mvnw.cmd test
```

The test suite includes:

- domain and application tests;
- HTTP and validation tests;
- PostgreSQL persistence tests;
- Flyway schema verification;
- RabbitMQ integration tests;
- transactional outbox tests;
- retry and dead-letter behavior;
- consumer idempotency;
- last-seat concurrency;
- concurrent cancellation;
- asynchronous payment workflows;
- certificate issuance and deduplication;
- JWT authentication and role authorization;
- ownership authorization;
- N+1 regression verification.

PostgreSQL and RabbitMQ integration tests use Testcontainers.

## Continuous integration

GitHub Actions provides continuous integration for the repository.

The CI workflow runs automatically for every pull request targeting `main` and
for every push to `main`.

It:

- runs on a clean GitHub-hosted Linux environment;
- provisions Temurin Java 21;
- caches Maven dependencies;
- executes `./mvnw -B verify`;
- runs the complete unit and integration test suite;
- starts ephemeral PostgreSQL and RabbitMQ instances through Testcontainers.

No separately configured PostgreSQL or RabbitMQ CI services are required because
the integration-test infrastructure is owned by the test suite itself.

Running the same Testcontainers-backed suite on an ephemeral GitHub runner also
provides an additional reproducibility check: successful execution does not rely
on developer-machine database or broker state.

CI was selected as an optional enhancement because it strengthens an existing
quality guarantee without adding dependencies or infrastructure to the
production runtime.

## Main trade-offs

### Modular monolith instead of microservices

The domain has useful module boundaries, but the current scope does not justify
independent deployments, distributed transactions or operational coordination
between multiple services.

A modular monolith keeps transactions simple while preserving boundaries that
could later become extraction points.

### Transactional outbox instead of direct event publication

Publishing directly to RabbitMQ from a database transaction can leave business
state committed without its event, or publish an event whose database work
later rolls back.

The transactional outbox removes that dual-write inconsistency at the cost of
eventual publication and possible duplicate delivery.

### At-least-once instead of exactly-once messaging

Exactly-once delivery across PostgreSQL and RabbitMQ would require stronger
coordination and additional complexity.

The solution instead accepts duplicate delivery and makes consumers idempotent.

### Database-enforced capacity

Course capacity is a shared mutable invariant.

Enforcing it through a conditional SQL update provides a clear atomic boundary
and avoids relying on application-level read/check/write sequences.

These trade-offs are documented in greater detail in the corresponding ADRs.

## Known limitations and intentionally deferred work

The following product and operational concerns remain deliberately outside the
current implementation:

- external payment-provider integration;
- user registration, password reset and account administration;
- external identity providers;
- refunds and payment reversals;
- PDF or QR certificate generation;
- Kubernetes deployment;
- independent deployment of individual business modules.

They are valid future extension points, but are not prerequisites for the
domain behavior required by the current solution.

## Optional scope and deliberate exclusions

The assignment defines several additional capabilities as optional bonus work.

Those capabilities were evaluated individually rather than introduced by
default. The guiding principle was to add infrastructure or abstraction only
when it solved a concrete problem in the current scope and its operational cost
was justified.

Continuous integration was included because it automatically validates the
existing build and Testcontainers-backed test suite without affecting the
runtime architecture.

The remaining optional capabilities were deliberately deferred for the
following reasons.

### Redis / Spring Cache

The current catalog requirements do not demonstrate a read-performance
bottleneck that requires distributed caching.

Adding Redis would introduce another runtime dependency together with cache
invalidation, consistency and operational concerns without a measured need for
them.

The existing architecture leaves caching as an infrastructure concern that can
be introduced later if access patterns or performance measurements justify it.

### Rate limiting

Rate limiting is a valid production hardening mechanism, particularly for
authentication and other externally exposed endpoints.

However, an appropriate implementation depends on deployment topology, trusted
client-address information, quota semantics and whether limits must be shared
between multiple application instances.

Introducing an arbitrary in-process limit for the exercise would provide a
potentially misleading guarantee rather than a production-ready distributed
policy.

It is therefore left as an extension to be designed together with the actual
deployment and abuse model.

### OpenTelemetry and external metrics dashboards

Actuator and Micrometer already expose the health and application metrics
required by the current solution.

OpenTelemetry would provide additional value for end-to-end tracing,
particularly across asynchronous boundaries, but exporting and consuming those
traces would also introduce additional operational topology such as collectors
and observability backends.

For a single deployable modular monolith, that additional infrastructure was not
required to validate the current operational requirements.

Distributed tracing remains a natural next step if the application is split
into independently deployed services or cross-service trace correlation becomes
an operational requirement.

### Cursor pagination

The API currently provides bounded offset pagination and the relationship
queries use dedicated projections with explicit N+1 regression coverage.

Cursor pagination becomes particularly valuable for large or highly volatile
datasets, deep pagination, or APIs requiring stable traversal while concurrent
writes occur.

None of those workload characteristics are defined by the current requirements,
so a second pagination model was not added without a demonstrated need.

### MCP integration

MCP is a meaningful additional integration surface, but a useful implementation
should expose the real application capabilities while preserving the same
authorization, validation and business invariants as the REST API.

Adding a shallow set of protocol wrappers solely to satisfy an optional item
would provide less value than keeping those guarantees centralized in the
existing application services.

The current modular application-service boundaries provide suitable integration
points for MCP tools if that interface becomes a required consumer of the
platform.

### Virtual threads

Java 21 virtual threads can improve scalability for workloads dominated by
blocking I/O, but enabling them does not remove downstream capacity constraints
such as JDBC connection pools, RabbitMQ resources or database throughput.

No workload profile or benchmark in the current scope demonstrates that request
threads are the limiting resource.

They were therefore not enabled merely as a framework switch; the decision
should be backed by workload characteristics and measurements.

### MapStruct

The current DTO mappings are small and explicit.

Introducing another annotation processor and mapping abstraction would add
indirection without materially reducing complexity at the present scale.

A generated mapping layer would become more valuable if the number and
complexity of transport/domain mappings grows.

## Delivery philosophy

The implementation intentionally prioritizes correctness of the core domain,
transactional integrity, concurrency safety, reliable messaging, security,
automated verification and reproducible execution over maximizing the number of
technologies included.

Optional components remain explicit extension points rather than being
introduced without a demonstrated requirement.
