# Step 8: Automated tests

The suite is **80 tests** (71 test methods, one repeated 10 times). `./mvnw clean verify` takes about **32 s** and starts 2 PostgreSQL containers.

Tags used below:
- **[Certain]**: verified in this project (a test, a log line, or bytecode in the resolved jar) or in the docs.
- **[Likely]**: a strong inference that wasn't directly verified.
- **[Guessing]**: filling a gap.

---

## 0. Shape of the suite

```
src/test/java/com/sayeed/lender_training_portal/
├── course/
│   ├── CourseTest                      unit (domain)
│   ├── CourseRepositoryTest            @DataJpaTest + Postgres
│   └── CourseControllerWebTest         @WebMvcTest + real SecurityConfig
├── enrollment/
│   ├── EnrollmentTest                  unit (domain)
│   ├── EnrollmentServiceTest           unit with Mockito
│   ├── EnrollmentRepositoryTest        @DataJpaTest + Postgres
│   └── EnrollmentControllerWebTest     @WebMvcTest + real SecurityConfig
├── user/AppUserRepositoryTest          @DataJpaTest + Postgres
├── integration/                        @SpringBootTest + MockMvc + Postgres
│   ├── AuthIntegrationTest
│   ├── CourseFlowIntegrationTest
│   ├── EnrollmentFlowIntegrationTest
│   └── EnrollmentConcurrencyIntegrationTest
├── LenderTrainingPortalApplicationTests   (replaces Initializr's contextLoads)
└── support/
    ├── TestcontainersConfiguration     the one Postgres container definition
    ├── FixedClockConfiguration         @Primary Clock for integration tests
    ├── TestData                        fixture builder (repositories + PasswordEncoder)
    ├── IntegrationTestBase             shared annotations + TRUNCATE before each test
    └── TestJwts                        jwt() post-processor with our ROLE_ mapping
src/test/resources/application.yml      test-only JWT secret
```

| Layer | Classes | Tests | Spring context | DB | Typical time |
|---|---|---|---|---|---|
| Domain unit | 2 | 9 | none | none | ms |
| Service unit (Mockito) | 1 | 5 | none | none | ms |
| Repository slice | 3 | 10 | JPA + Flyway only | Postgres (container #1) | ~7 s first class (container + context), then ms |
| Web slice | 2 | 20 | MVC + Security only | none | ~4 s first class, then ms |
| Full integration | 5 | 36 | whole app | Postgres (container #2) | ~10 s first class, then 1–3 s per class |

**Why two containers, not one [Certain]:** a container defined as a `@Bean` belongs to an application context. `@DataJpaTest` and `@SpringBootTest` build different contexts, so each gets its own container. Within a group, every class has identical configuration, so the context cache reuses the context and its container. The log shows exactly 2 `Container postgres:17 started` lines per run. Sharing one container across *different* contexts is possible (a `static` container plus `@DynamicPropertySource`, or Testcontainers' reuse mode), but it trades away the simple bean lifecycle. Two containers cost about 2 s.

---

## 1. Infrastructure decisions

### Testcontainers + `@ServiceConnection`

```java
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {
    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:17"));
    }
}
```

- **[Certain]** Boot 4.1.2's BOM manages Testcontainers **2.0.5**. In 2.x the artifacts are `org.testcontainers:testcontainers-postgresql` and `testcontainers-junit-jupiter`; the 1.x artifacts were `postgresql` and `junit-jupiter`. The class moved to `org.testcontainers.postgresql.PostgreSQLContainer` and is no longer generic. The old `org.testcontainers.containers.PostgreSQLContainer` still exists in the jar, but the new package is the one to use.
- **[Certain]** `@ServiceConnection` makes Boot derive the DataSource from the running container (URL, user and password), so no `spring.datasource.*` properties are needed. Flyway then runs the real `V1__init.sql`; `CourseRepositoryTest.flywayMigrationV1_wasAppliedToRealPostgres` checks `flyway_schema_history`.
- The image is pinned to `postgres:17`. `compose.yaml` uses `postgres:latest`, which can silently move to a new major version under you **[Guessing: whether you care about pinning dev]**.

### H2

- Both the `com.h2database:h2` test dependency and the main `spring-boot-h2console` dependency (which pulled `h2` in at compile scope) are removed. `./mvnw dependency:tree -Dincludes=com.h2database:h2` now finds nothing **[Certain]**.
- The repository tests still say `@AutoConfigureTestDatabase(replace = NONE)`. It's explicit and harmless: if anyone ever adds an embedded database back, Boot still can't swap Postgres out. The Flyway test also asserts that `select version()` starts with `PostgreSQL`.

### Docker Compose during tests

**[Certain]** `spring.docker.compose.skip.in-tests` defaults to `true`; I read the constructor bytecode of `DockerComposeProperties$Skip` in `spring-boot-docker-compose-4.1.2.jar`. So `compose.yaml` is not started by tests, and I did not set the property.

### Test properties

`src/test/resources/application.yml` only sets `app.jwt.secret`. **[Certain]** Boot loads both `application.yaml` (main) and `application.yml` (test) because the file names differ, and the `.yml` value wins for keys in both. `LenderTrainingPortalApplicationTests` asserts the test secret is active *and* that `app.jwt.issuer` still comes from the main file. If you named the test file `application.yaml`, it would **shadow** the main file completely (same classpath resource name), and you'd lose `ddl-auto: validate` and `open-in-view: false` in tests.

### Fixed clock

```java
@Bean @Primary
Clock fixedTestClock() {
    return Clock.fixed(Instant.now().truncatedTo(ChronoUnit.SECONDS), ZoneOffset.UTC);
}
```

**[Certain]** I could not freeze the clock at a constant like `2025-01-01`. `TokenService` stamps `iat`/`exp` with our `Clock`, but Spring Security's `JwtTimestampValidator` checks `exp` against the **system** clock (it has its own clock, which defaults to UTC system time). A clock frozen in the past would make every issued token already expired. So the clock is frozen at context start, which keeps a 1 h token valid for the whole run. It's still fixed, so `enrolledAt` and `completedAt` can be asserted exactly. Truncating to whole seconds keeps the value identical after a round trip through Postgres `timestamptz` (microseconds) and JSON.

---

## 2. Domain unit tests: `CourseTest`, `EnrollmentTest`

```java
@Test
void markCompleted_twice_keepsFirstCompletedAt() {
    Enrollment enrollment = newEnrollment();
    enrollment.markCompleted(FIRST_COMPLETION);

    enrollment.markCompleted(SECOND_COMPLETION);

    assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.COMPLETED);
    assertThat(enrollment.getCompletedAt()).isEqualTo(FIRST_COMPLETION);
}
```

**Why a unit test:** the rules live *inside* the entity (`publish()`, `addModule()`, `markCompleted()`, `isOwnedBy()`), so plain `new` + method calls exercise them completely. They run in about 20 ms in total.

**What it exercises:** `Course`, `CourseModule` (via `addModule`), `Enrollment`, and `BusinessRuleException`. IDs are DB-generated with no setter, so `isOwnedBy` needs `ReflectionTestUtils.setField(trainer, "id", 7L)`. That's a normal compromise for JPA entities.

**Cases:** sort order 1..n follows insertion order; a new course is DRAFT; publish succeeds; publish with no modules → `BusinessRuleException` and the course stays DRAFT; publish twice → exception; `isOwnedBy` true/false, plus a check with id 1000. Ids above 127 aren't cached `Long` objects, which would catch a `==` comparison by mistake **[Certain about the Long cache; the code uses `equals`, so it's correct]**. For `markCompleted`, the second call doesn't overwrite `completedAt`.

**What breaks without it:** someone "simplifies" `markCompleted` to always set `completedAt`, and completion dates silently move every time a lender re-clicks a module. The integration tests would catch that too, but slower and further from the cause.

---

## 3. Repository slice tests: `CourseRepositoryTest`, `EnrollmentRepositoryTest`, `AppUserRepositoryTest`

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class CourseRepositoryTest { ... }

@Test
void findDetailById_loadsTrainerAndModulesInOneStatement() {
    ...
    em.flush();
    em.clear();                         // empty the 1st-level cache
    statistics.setStatisticsEnabled(true);
    statistics.clear();

    Course course = courses.findDetailById(id).orElseThrow();
    String trainerName = course.getTrainer().getFull_name();
    List<String> moduleTitles = course.getModules().stream().map(CourseModule::getTitle).toList();

    assertThat(Hibernate.isInitialized(course.getTrainer())).isTrue();
    assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
}
```

**Why a slice:** we're testing *queries* (JPQL, the constructor-expression projection, join fetch, the custom `countQuery`, and DB constraints). Those need a real database and Hibernate, but not controllers, security or services. `@DataJpaTest` starts only JPA, Flyway, the DataSource and the repositories. Each test runs in a transaction that is rolled back afterwards **[Certain]**, so no cleanup code is needed here.

**What it exercises and how:** repository interface → Spring Data proxy → Hibernate 7 → JDBC → Postgres 17 with the Flyway schema. `TestEntityManager` inserts fixtures. `em.flush(); em.clear();` matters: without `clear()`, Hibernate would serve the entities from the persistence context and the query would never be tested.

**Statement counting:** Hibernate `Statistics` are switched on *at runtime* on the shared `SessionFactory`, only inside the two counting tests, and switched off in `@AfterEach`. That gives "statistics for this test only" without a `properties = ...` override, which would have created a third Spring context and a third container. **[Certain]** It works: the N+1 test counts exactly 6 statements, so statistics are clearly recording.

```java
for (Course c : courses.findAll()) {
    c.getTrainer().getFull_name();   // LAZY trainer proxy
    c.getModules().size();           // LAZY collection
}
// 1 (courses) + 2 (two distinct trainers; the second course of Tina reuses her proxy) + 3 (module lists) = 6
assertThat(statistics.getPrepareStatementCount()).isEqualTo(6);
```

That is the Step 4 N+1 written as a regression test. The pair of tests (1 vs 6) is the point: the first proves the fix, and the second proves the measurement can detect the problem.

**Other cases:**
- `findSummariesByStatus`: only PUBLISHED rows, correct `moduleCount`, `Sort.by("title")` applied, page 0/1 of size 2, `totalElements = 3` from the custom count query with the draft excluded.
- `findProgressByUserId`: completed/total counts via the two subqueries, newest first, another lender's rows excluded.
- `findByIdAndUserId` and `existsByUserIdAndCourseId`.
- Duplicate `(user_id, course_id)` → `DataIntegrityViolationException` mentioning `uq_enrollment_user_course`. **[Certain]** With `IDENTITY` ids, Hibernate executes the INSERT inside `save()` because it needs the generated id. `saveAndFlush` makes the "it has hit the DB" moment explicit.
- `findByEmail` found / not found; the `AppUser` constructor stores emails lowercase; a raw `JdbcTemplate` insert with an uppercase email is rejected by `ck_app_user_email_lower` (see §11).

**What breaks without it:** the brief says "N+1 fixed in Step 4". Nothing would stop someone from removing `join fetch` or adding a LAZY field that the DTO touches. Unit tests can't see SQL, and integration tests still *pass* with N+1; they're just slower.

---

## 4. Service unit test with Mockito: `EnrollmentServiceTest`

```java
@ExtendWith(MockitoExtension.class)
class EnrollmentServiceTest {
    @Mock EnrollmentRepository enrollments;
    @Mock ModuleCompletionRepository completions;
    ...
    service = new EnrollmentService(enrollments, completions, courses, modules, users,
                                    Clock.fixed(NOW, ZoneOffset.UTC));

@Test
void completeModule_alreadyCompletedModule_doesNotSaveAgain_andKeepsCompletedAt() {
    enrollment.markCompleted(firstCompletion);
    given(completions.existsByEnrollmentIdAndModuleId(ENROLLMENT_ID, 2L)).willReturn(true);
    ...
    then(completions).should(never()).save(any());
    assertThat(progress.completedAt()).isEqualTo(firstCompletion);
}
```

**Why a Mockito unit test:** it isolates `completeModule`'s *decision logic*: save or not, completed or not, and which exception. It runs in milliseconds and needs no Docker. With `MockitoExtension`'s strict stubs, an unused `given(...)` fails the test, which keeps the stubbing honest.

**The trade-off:** this test happily passes even if the repository queries are wrong, `@Transactional` is missing, or the unique constraint doesn't exist. The mocks return whatever we tell them. That's why there is just this one class: the same behaviour is also covered end-to-end in `EnrollmentFlowIntegrationTest`. Mock-heavy tests also couple to *how* the code works (which repository methods it calls), so refactors break them even when behaviour is unchanged.

**Cases:** enrollment not found for this user → `NotFoundException("Enrollment 100 not found")`, and `users`, `modules` and `completions` are never touched (no extra lookups just to build the message); completing the last module → COMPLETED with `completedAt = NOW`, and the saved `ModuleCompletion` captured with `ArgumentCaptor`. Already-completed module → `save` never called, `completedAt` unchanged. Not the last module → stays ACTIVE. Module from another course → `NotFoundException`, with `completions` untouched.

**One thing it surfaced:** the "enrollment not found" branch used to build its message with `users.getReferenceById(userId).getFull_name()`. With an unstubbed mock that was an NPE, and in production it was an extra `SELECT` that could turn a 404 into a 500. It's fixed now, and the not-found test asserts `then(users).shouldHaveNoInteractions()`, so the lookup can't silently come back.

---

## 5. Web slice tests: `CourseControllerWebTest`, `EnrollmentControllerWebTest`

```java
@WebMvcTest(CourseController.class)
@Import(SecurityConfig.class)
class CourseControllerWebTest {
    @Autowired MockMvc mvc;
    @MockitoBean CourseService courseService;

@Test
void publish_accessDeniedFromService_returns403ProblemDetail_notCatchAll500() throws Exception {
    given(courseService.publish(eq(5L), any()))
            .willThrow(new AccessDeniedException("Only the course's trainer or an admin can publish course"));

    mvc.perform(post("/api/courses/5/publish").with(as(TRAINER_ID, Role.TRAINER)))
            .andExpect(status().isForbidden())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.title").value("Forbidden"))
            .andExpect(jsonPath("$.status").value(403));
}
```

**Why a slice:** the HTTP contract (URL security rules, status codes, JSON shape, validation and exception → ProblemDetail mapping) is the controller's job. Mocking the service makes every error path a one-liner (`willThrow(...)`) instead of building database state for each.

**What gets loaded [Certain]:** the named controller, `@RestControllerAdvice` classes (so `ApiExceptionHandler` comes in automatically), Jackson, validation, and Boot's security auto-configuration. `SecurityConfig` is a plain `@Configuration`, which `@WebMvcTest` does *not* scan, so it's imported explicitly. That's why it had to become `public`: the test lives in the `course` package. It brings the real filter chain, `JwtDecoder`/`JwtEncoder` and `JwtProperties`.

**`@MockitoBean` [Certain]:** Spring Framework's replacement for Boot's removed `@MockBean` (package `org.springframework.test.context.bean.override.mockito`). It puts a Mockito mock into the context in place of the real bean. Each distinct set of `@MockitoBean`s is a different context cache key, so overusing them across classes multiplies contexts.

**`jwt()` and our roles [Certain]:** spring-security-test's `jwt()` skips the decoder and places a `JwtAuthenticationToken` straight into the security context. Its default authority is `SCOPE_read`, which would fail every `hasRole(...)` rule. `support/TestJwts` passes the same mapping as `SecurityConfig` (`roles` claim, `ROLE_` prefix):

```java
return jwt()
        .jwt(token -> token.subject(String.valueOf(userId)).claim("roles", List.of(role.name())) ...)
        .authorities(ROLES_CONVERTER);
```

That converter is a **copy**, because `SecurityConfig.rolesConverter()` is private. The integration tests use real tokens through the real converter, so drift would be caught there. If you want a single source of truth, make that method `public static` and call it from `TestJwts`.

**Cases:**
- **No token** → 401, empty body, `WWW-Authenticate: Bearer ...`. **[Certain]** Spring Security 7 adds an RFC 9728 parameter: `Bearer resource_metadata="http://localhost/.well-known/oauth-protected-resource"`. The first version of my test expected the bare `Bearer` and failed, so it now checks the prefix.
- **Lender on `POST /api/courses`** → 403, empty body, `WWW-Authenticate: Bearer error="insufficient_scope"...`, service never called. **Filter errors are not ProblemDetail [Certain]:** they happen in the servlet filter chain *before* `DispatcherServlet`, so `@RestControllerAdvice` never sees them.
- **Validation** → 400 `application/problem+json`, `errors[*].field` contains exactly `title` and `modules[0].durationMinutes` (nested path from `List<@Valid ModuleRequest>`).
- **Malformed JSON** → 400, not 500. `ResponseEntityExceptionHandler` declares a more specific `@ExceptionHandler` for `HttpMessageNotReadableException`, and within one advice the closest exception type wins over `Exception.class`.
- **NotFound** 404 (also checks `instance` = request path), **BusinessRule** 409, **OptimisticLockingFailure** 409 "Concurrent update", **DataIntegrityViolation** 409 with a generic detail (the SQL message isn't leaked), **AccessDenied from the service** 403 ProblemDetail, **anything else** 500 "An unexpected error occurred".
- **Size clamp:** `?size=5000&page=-3` → captured `PageRequest` has size 100, page 0, sort `title`; `size=0` → 1.
- **`POST /api/courses`** → 201, `Location: http://localhost/api/courses/99`, and the service received `CurrentUser(42, TRAINER)` from the token's `sub` and `roles`.
- **Contract pin:** asserts `$.modules[0].title`, the field the Angular front end reads. It previously pinned the typo `tile`; it was updated in the same change as the rename (any rename without updating it would fail this assertion). That's what a contract test is for: an accidental rename of a JSON field fails the build.

**What breaks without it:** someone deletes the `AccessDeniedException` handler as "redundant". The catch-all then turns ownership failures into 500s, and nothing else in the suite pins the exact ProblemDetail fields for every exception type.

---

## 6. Full integration tests: `integration/*`, `LenderTrainingPortalApplicationTests`

```java
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, FixedClockConfiguration.class, TestData.class})
public abstract class IntegrationTestBase {
    @BeforeEach
    void cleanDatabase() { data.reset(); }   // TRUNCATE ... RESTART IDENTITY CASCADE
```

```java
@Test
void completingAllModules_marksEnrollmentCompleted_andRepeatPutIsIdempotent() throws Exception {
    Course course = data.publishedCourse(data.trainer("tina@example.com"), "Fair lending", 3);
    String token = login(data.lender("lena@example.com"));      // real POST /api/auth/login
    long enrollmentId = idOf(enroll(token, course.getId()).andExpect(status().isCreated()));
    ...
    complete(token, enrollmentId, last.getId())                 // same PUT again
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.completedAt").value(now));
```

**Why integration:** these are the only tests where *everything* is real together: the BCrypt login, `TokenService` signing, `NimbusJwtDecoder` + issuer/expiry validators, our `JwtAuthenticationConverter`, URL rules, `@AuthenticationPrincipal`, services with real `@Transactional` boundaries, Hibernate, Flyway's schema and Postgres constraints. They catch wiring bugs that the slices, built from mocks, structurally can't.

**Fixtures:** `TestData` saves users through `AppUserRepository` with the real `PasswordEncoder`, so they can log in, and builds courses with `Course.addModule`/`publish`. No seed CSVs. Every test creates exactly what it needs.

### Why no `@Transactional` on these tests

A test-managed transaction would:
1. **hide `LazyInitializationException`.** The persistence context stays open across the whole MockMvc call, so a controller or serializer touching a LAZY field would "work" in the test and blow up in production, where `open-in-view` is off **[Certain about the mechanism; Likely that MockMvc runs on the same thread, which is what makes it share the transaction]**;
2. **hide commit-time behaviour.** Nothing is committed, so deferred constraint checks, `@Version` increments at flush, and "what does another transaction see" never happen;
3. **make the concurrency test impossible.** Uncommitted rows are invisible to the second thread.

`draftCourse_isHiddenFromLender_404_butVisibleToOwner_200` is the canary: the owner's response contains the LAZY trainer name and modules, which only works because `CourseDetail.from()` runs inside the service's transaction. Instead of rollback, `TRUNCATE ... RESTART IDENTITY CASCADE` runs before each test. Truncate is cheaper than per-table `DELETE`, and tests stay independent. I verified that with a random class and method order run.

### What's covered

| Class | Flow |
|---|---|
| `AuthIntegrationTest` | login OK (`Bearer`, `expiresIn: 3600`); wrong password → 401 **ProblemDetail** (it's a `ResponseStatusException` thrown in a controller, unlike the filter 401s); unknown email → same 401 message (no user enumeration); upper-case login email works; `GET /api/me` → right id/role/email/`expiresAt`; a **control** token minted with `JwtEncoder` is accepted; **tampered** token (payload rewritten `LENDER` → `ADMIN`, original signature) → 401 `invalid_token`; **expired** (2 h ago, beyond the 60 s skew) → 401; **wrong issuer** → 401 |
| `CourseFlowIntegrationTest` | create 201 → publish 200 (`version` 0 → 1) → publish 409; `Location` header is fetchable; publish with 0 modules → 409; other trainer → 403 ProblemDetail; admin can publish anyone's course; draft 404 for a lender, 200 for the owner; catalog shows only published courses, sorted, paged, with module counts; no token → 401 |
| `EnrollmentFlowIntegrationTest` | enroll 201 (with `enrolledAt` = fixed clock) → 409; complete all 3 modules → COMPLETED, repeat PUT keeps `completedAt` and count, `GET /me` agrees; another lender's enrollment id → 404; module of another course → 404; draft or missing course → 404; trainer → 403 |
| `LenderTrainingPortalApplicationTests` | context starts on Postgres; the test secret is active and the issuer still comes from main config; **no `CommandLineRunner` beans** (the `n1-demo`/`service-demo` runners are off) |

On the tampered-token test: I deliberately did *not* "flip the last character of the signature". An HS256 signature is 32 bytes = 43 base64url characters, and the last character carries 2 unused bits, so some flips decode to the **same** signature and the test would pass or fail randomly **[Certain about the arithmetic; Likely that Nimbus tolerates the non-canonical encoding]**. Rewriting the payload is deterministic and also the realistic attack.

**What breaks without it:** e.g. changing `setAuthorityPrefix("ROLE_")` or the claim name in `SecurityConfig`. Every web slice test still passes (they use their own copy of the mapping), and production rejects every request.

---

## 7. Concurrency: `EnrollmentConcurrencyIntegrationTest`

```java
@RepeatedTest(10)
void simultaneousDuplicateEnrollments_exactlyOneSucceeds() throws Exception {
    ...
    Callable<Integer> attempt = () -> {
        ready.countDown();
        go.await();                       // both threads released together
        return mvc.perform(post("/api/enrollments")...).andReturn().getResponse().getStatus();
    };
    ...
    assertThat(statuses).containsExactlyInAnyOrder(201, 409);
    assertThat(jdbc.queryForObject("select count(*) from enrollment", Integer.class)).isEqualTo(1);
}
```

**Why:** `enroll()` is "check then insert" (`existsBy...` then `save`). Two concurrent requests can both pass the check. The **database unique constraint** is the real guard; the `exists` check only gives a nicer message in the common case.

**Result [Certain]:** in a dedicated run, all **10/10** repetitions went down the constraint path (the log has 2 `duplicate key ... uq_enrollment_user_course` lines per repetition). So the race genuinely happens, and Postgres blocks the second INSERT on the unique index until the first commits, then rejects it. `DataIntegrityViolationException` → `ApiExceptionHandler` → 409. The test asserts only the deterministic outcome (one 201, one 409, one row), not *which* path produced the 409. It passed in all 3 full runs (30 repetitions), so it is **not** `@Disabled`. There are no sleeps: two latches and timeouts on `Future.get`.

---

## 8. Production changes

| File | Change | Reason |
|---|---|---|
| `config/SecurityConfig.java` | `class` → `public class` | `@WebMvcTest` doesn't scan `@Configuration` classes, so the web tests must `@Import(SecurityConfig.class)` from another package |
| `pom.xml` | removed `com.h2database:h2` (test) and `spring-boot-h2console` (main) | nothing uses H2 at runtime or in tests |
| `course/CourseDetail.java` | `ModuleView.tile` → `title` | typo leaked into the JSON contract |
| `course/CourseSummary.java` | `Description` → `description` | JSON key was capitalised; the parameter order still matches the `select new ...CourseSummary(...)` JPQL |
| `user/AppUser.java` | removed `unique = true` from `passwordHash`, `fullName`, `role` | only `email` is unique in `V1__init.sql`; the annotations documented the schema wrongly |
| `enrollment/EnrollmentService.java` | not-found message back to `"Enrollment " + id + " not found"` | the name lookup cost a query per 404 and could turn a 404 into a 500 |
| `course/CourseService.java`, `CourseRepository.java` | removed the stray Boot `DataWebProperties.Pageable` import; `listPublished` and `findSummariesByStatus` now take `org.springframework.data.domain.Pageable` instead of `PageRequest` | program to the interface; the controller still passes a `PageRequest` |
| `application.yaml` | `spring.threads.virtual.enable: false` → `enabled: true` | the misspelled key was silently ignored |
| `pom.xml` | added `spring-boot-testcontainers`, `testcontainers-postgresql`, `testcontainers-junit-jupiter` (test, no versions) | Testcontainers via the Boot 4.1.2 BOM |

`V1__init.sql` is untouched.

---

## 9. Interview talking points

**Test pyramid and trade-offs.** Many fast, narrow tests at the bottom (domain/unit), fewer slices in the middle, a handful of end-to-end flows on top. Here: 14 unit, 30 slice, 36 integration. The top is heavier than a textbook pyramid, but each integration test is cheap once the context is cached (about 0.2 s each). The real cost is the first context start plus Docker. Lower tests are faster and pinpoint failures; higher tests catch wiring and configuration bugs that lower tests can't see by design.

**`@DataJpaTest` vs `@WebMvcTest` vs `@SpringBootTest`.**
- `@DataJpaTest`: JPA + DataSource + Flyway + repositories, transactional with rollback. Use it for queries, mappings and constraints.
- `@WebMvcTest`: MVC infrastructure + chosen controllers + advice + security auto-config, no services or DB. Use it for the HTTP contract, validation, error mapping and URL security. Note: custom `@Configuration` classes are not included; import them.
- `@SpringBootTest`: the whole app. Add `@AutoConfigureMockMvc` for in-process HTTP, or `webEnvironment = RANDOM_PORT` for a real server.
- **[Certain]** In Boot 4 these annotations moved to per-technology modules: `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest`, `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`/`AutoConfigureMockMvc`, `org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase`, `org.springframework.boot.jpa.test.autoconfigure.TestEntityManager`, which come from the `spring-boot-starter-*-test` starters already in your POM.

**`@MockitoBean`.** It replaces `@MockBean` (deprecated in Boot 3.4, removed in Boot 4). It lives in Spring Framework, not Boot. It's for replacing a bean *in a Spring context*. In a plain unit test, use `@Mock` + constructor injection, as `EnrollmentServiceTest` does: no context at all.

**Testcontainers vs H2.** H2 "in Postgres mode" is a different database. It differs in identity/sequence behaviour, `timestamptz` handling, error messages and constraint names, locking under concurrency, and SQL features. Flyway migrations written for Postgres may not even run on it. The concurrency test here depends on Postgres's real unique-index locking. Cost: needs Docker, plus about 2 s per container start.

**Why no `@Transactional` on integration tests.** See §6: it hides lazy-loading bugs, skips commits and isolation, and blocks multi-threaded tests. Clean with `TRUNCATE` before each test instead. Before rather than after, so a crashed test can't poison the next one.

**spring-security-test `jwt()`.** It skips decoding and puts an already-authenticated `JwtAuthenticationToken` in place. It's great for slices, but it **doesn't** test your `JwtDecoder`, validators or converter. Default authority is `SCOPE_read`; use `.authorities(...)` for role-based rules. Real tokens in integration tests cover the rest.

**Proving N+1 fixes with statement counts.** Log-reading doesn't scale; assert `Statistics.getPrepareStatementCount()` after `em.clear()`. Show the bad version too (6 statements here), so the measurement itself is proven. Alternatives: datasource-proxy or Hypersistence Utils' `SQLStatementCountValidator` **[Likely: both are common choices; neither is used here]**.

**Testing time with `Clock`.** Inject `java.time.Clock` instead of calling `Instant.now()`. Unit tests use `Clock.fixed(...)`; Spring tests override with a `@Primary` bean. A gotcha found here: framework code (JWT expiry validation) uses its own clock, so a fake clock must stay consistent with real time, or you configure the framework's clock too (`JwtTimestampValidator.setClock`).

**`MockMvcTester` [Certain it exists since Spring Framework 6.2].** It's the AssertJ-flavoured alternative to `perform(...).andExpect(...)`, e.g. `assertThat(mvc.get().uri("/api/courses")).hasStatusOk().bodyJson().extractingPath("$.size").isEqualTo(100)`. These tests use classic `MockMvc` because that's what you'll most often see in existing codebases and interviews.

---

## 10. Bugs found while writing the tests

### Fixed (Step 8 follow-up)

1. **Virtual threads were off.** `spring.threads.virtual.enable: false` used the wrong key (`enabled`) *and* the value `false`. Spring silently ignores unknown keys **[Certain]**. It's now `enabled: true`, and `LenderTrainingPortalApplicationTests` asserts that the property resolves to `true`, so a misspelled key would leave it `null` and fail the test. The test checks configuration only: MockMvc doesn't go through Tomcat, so no test shows a request actually running on a virtual thread.
2. **JSON `tile`** → `title` (`CourseDetail.ModuleView`). Pinned by the web test.
3. **JSON `Description`** → `description` (`CourseSummary`). Now asserted in `CourseRepositoryTest` (record accessor) and `CourseFlowIntegrationTest` (`$.content[0].description`).
4. **Misleading `@Column(unique = true)`** on `passwordHash`, `fullName` and `role`. Removed; only `email` is unique, matching the schema.
5. **`completeModule` not-found message** no longer looks up the user's name. Covered by the new Mockito test.
6. **Stray `DataWebProperties.Pageable` import** removed. It compiled because the import was *unused*: the method parameter was `PageRequest`, so nothing referred to the name `Pageable` and there was no clash **[Certain]**. Both the service and the repository now take Spring Data's `Pageable`.
7. **H2** removed from the build entirely.

8. **Email case.** Fixed in the follow-up; see §11.

### Still open

- **Inconsistent 409 message under concurrency.** The racing loser gets "The request conflicts with existing data", but a sequential duplicate gets "You are already enrolled in course N". The status is correct; only the message differs **[Certain]**.
- **`/api/me` email claim.** It reads the email via `jwt.getClaimAsStringList("email").getFirst()` although `email` is a plain string claim. It works because the claim converter wraps a single value in a list **[Certain]**. `getClaimAsString("email")` says what it means.

---

## 11. Follow-up: email normalisation

### What changed

```java
// AppUser constructor
this.email = Objects.requireNonNull(email).toLowerCase(Locale.ROOT);
```

```sql
-- V2__email_lowercase.sql
alter table app_user add constraint ck_app_user_email_lower check (email = lower(email));
```

`AuthService` still lower-cases the *typed* login email, because users type mixed case.

| Test | Proves |
|---|---|
| `AppUserRepositoryTest.constructor_normalisesEmail_soItIsStoredLowercase` | the raw column value (read with `JdbcTemplate`, not through the entity) is lowercase |
| `AppUserRepositoryTest.rawInsertWithUppercaseEmail_isRejectedByCheckConstraint` | SQL that bypasses JPA gets a `DataIntegrityViolationException` naming `ck_app_user_email_lower` |
| `AuthIntegrationTest.mixedCaseUser_isStoredLowercase_andCanLogInTypingAnyCase` | user created as `Mixed.Case@Example.com` logs in typing `MIXED.case@example.com` |
| `CourseRepositoryTest.flywayMigrations_wereAppliedToRealPostgres` | `flyway_schema_history` contains exactly versions 1 and 2 |

### Why normalise at write time *and* enforce in the database

- **Write time (constructor)** is the friendly path. Every user created through the domain model is correct automatically, and lookups can stay a plain `email = ?` that uses the existing unique index **[Certain]**.
- **The check constraint** is the guarantee. The constructor only protects code that goes through it. A future setter, a native `INSERT`, a hand-written SQL fix in production, another service sharing the table or a bulk import would all bypass it. The database rejects every one of those **[Certain: the raw-insert test shows it]**.
- Together they also make the existing `UNIQUE (email)` mean "unique ignoring case". Without them, `Bob@x.com` and `bob@x.com` could both exist as separate accounts **[Certain]**.
- Alternatives you might be asked about: a `unique index on lower(email)` (enforces case-insensitive uniqueness, but stored values stay mixed and queries must say `lower(email) = ?` to use the index), or Postgres's `citext` type (case-insensitive comparisons, but it needs an extension) **[Likely: both are standard Postgres approaches; not tried here]**.

### Why V1 is never edited

Flyway records a **checksum** of every applied migration in `flyway_schema_history`. On startup it validates the files against those checksums. If `V1__init.sql` changed after being applied anywhere, startup fails with a checksum mismatch, and other databases (a teammate's, staging, production) would never receive the change anyway, because V1 already counts as applied there **[Certain: documented Flyway validate-on-migrate behaviour]**. Applied migrations are history; changes go in a new version.

### What happens to an existing database with mixed-case emails

`ALTER TABLE ... ADD CONSTRAINT ... CHECK` validates every existing row. With one mixed-case row, V2 fails:

```
ERROR:  check constraint "ck_app_user_email_lower" of relation "app_user" is violated by some row
```

**[Certain]** I reproduced this on a throwaway `postgres:17` container: V1, then one `Old.User@Example.com` row, then V2. It failed, and after `update app_user set email = lower(email)` V2 succeeded. I ran the SQL with `psql`, not through Flyway. Through Flyway, the application would fail to start. On Postgres the failed migration runs inside a transaction, so it's rolled back and not recorded as applied, and you can retry after fixing the data **[Likely]**.

The usual approach is a **data-fix migration that runs before the constraint**, e.g. `V2__lowercase_emails.sql` doing `update app_user set email = lower(email) where email <> lower(email);`, followed by `V3__email_lowercase_check.sql` with the constraint. Watch for **collisions**: if `Bob@x.com` and `bob@x.com` both exist, the `UPDATE` itself fails on the unique constraint. Someone has to decide which account survives (merge or rename) before the fix can run **[Certain about the mechanism]**.

In this project it doesn't matter: V2 has only ever run against empty test databases, and every email in `seed/app_user.csv` is already lowercase. I did **not** run V2 against your existing compose database **[Likely it applies cleanly, given the seed data]**.

