# AGENTS.md - LLM Contribution Guide for Nostrino

## Project Overview

Nostrino is a Nostr SDK for Kotlin, providing a multiplatform implementation of the Nostr protocol. The project implements various NIPs (Nostr Implementation Possibilities) and is used by Block/CashApp for Nostr-based features. Nostrino supports JVM, iOS, and Linux platforms.

## Technology Stack

- **Language**: Kotlin (Multiplatform)
- **Build System**: Gradle with Kotlin DSL
- **Java Version**: JVM 11
- **Test Framework**: JUnit 5 (Jupiter) + Kotest
- **Platforms**: JVM, iOS (x64, ARM64, Simulator ARM64), Linux x64
- **Key Dependencies**:
  - OkHttp (HTTP client for JVM)
  - Moshi (JSON)
  - acinq secp256k1 (Cryptographic curves)
  - Kotlinx Coroutines (Asynchronous programming)
  - OkIO (I/O operations)
  - Kotest (Testing and assertions)
  - Turbine (Flow testing)

## Build & Development Commands

### Prerequisites
This project uses [Hermit](https://cashapp.github.io/hermit/) for consistent tooling. Activate it with:
```bash
. ./bin/activate-hermit
```

### Essential Commands
```bash
# Build the entire project (includes tests)
gradle build

# Run tests only
gradle test

# Clean build
gradle clean build

# Check for API breaking changes (binary compatibility)
gradle apiCheck

# Generate new API dumps after API changes
gradle apiDump

# Check for dependency updates
gradle dependencyUpdates -Drevision=release

# Update version catalog
gradle versionCatalogUpdate

# Generate API documentation
gradle dokkaHtml
```

## Project Structure

```
nostrino/
├── lib/                    # Main SDK library (multiplatform)
│   └── src/
│       ├── commonMain/kotlin/   # Platform-agnostic code
│       └── jvmMain/kotlin/      # JVM-specific code
├── lib-test/              # Testing utilities and integration tests
│   └── src/
│       ├── main/kotlin/        # Test helpers and Arb generators
│       └── test/kotlin/        # Integration tests
├── buildSrc/              # Build configuration
└── gradle/                # Gradle wrapper and version catalog
    └── libs.versions.toml # Dependency versions
```

## Code Conventions

### General Style
- **Indentation**: 2 spaces (NOT tabs)
- **Line Length**: Maximum 120 characters
- **Line Endings**: LF (Unix-style)
- **Charset**: UTF-8
- **Final Newline**: Always insert

### Kotlin-Specific
- **Imports**: No wildcard imports (use explicit imports)
  - `ij_kotlin_name_count_to_use_star_import = 2147483647`
  - `ij_kotlin_name_count_to_use_star_import_for_members = 2147483647`
- **Naming**: Follow standard Kotlin conventions
- **Null Safety**: Leverage Kotlin's null safety features
- **Parameters**: Wrap on every item with proper formatting
  - Method parameters: new line after `(`, right paren on new line
  - Call parameters: new line after `(`, right paren on new line
- **Code Style**: Follow Kotlin official code style
- **Copyright Headers**: NOT required in new files

### Testing

#### Testing Philosophy: Sociable Unit Testing

Nostrino follows the **Sociable Unit Testing** approach for comprehensive, resilient test coverage.

**Core Principle**: Write unit tests for all classes, but let them connect through to real dependencies until hitting the system boundary.

- ✅ **Real object graphs**: Classes use their actual dependencies (services, stores, validators)
- ✅ **Fake external services**: Only external APIs and services are faked at system boundaries
- ✅ **Fast execution**: Still runs quickly despite using real components
- ✅ **Less brittle**: No need to update mocks when internal implementations change

**System Boundaries** (what gets faked):
- External relay servers (use FakeRelay for testing)
- External HTTP endpoints
- Nostr relay connections

**Benefits over traditional mock-based testing**:
- Tests behavior, not implementation details
- Resilient to refactoring
- Real integration confidence
- Simpler test setup

#### Test Framework & Conventions

- **Framework**: JUnit 5 (Jupiter) + Kotest - configured in root `build.gradle.kts`
- **Test Style**: ALWAYS use Kotest StringSpec (test names in strings)
- **Assertions**: Use Kotest matchers (`shouldBe`, `shouldBeEqual`, `shouldContainExactly`, etc.) - NOT JUnit assertions
- **Test Naming**: Test class suffix is `Test` (e.g., `RelaySetTest.kt`)
- **Test Names**: Use string literals for readable test names (StringSpec style)
- **Integration Tests**: Use Docker containers for relay testing (nostr-rs-relay)
- **Mocking**: Use fake implementations (e.g., `FakeRelay`) for external system boundaries, not internal dependencies
- **Test Data Generation**: Prefer `Arb<T>` generators over hard-coded values. Compose existing `Arb` generators for new types
  - Located in `lib-test/src/main/kotlin` (e.g., `ArbEvent.kt`, `ArbPubKey.kt`)
  - Use `Arb.next()` to generate single values
  - Use `checkAll()` for property-based testing
- **Flow Testing**: Use Turbine for testing Kotlin Flows
- **Assertion Style**: Use Kotest matchers for clean, expressive assertions

#### Test Example

```kotlin
// Simple unit test with Kotest StringSpec
class RelaySetTest : StringSpec({
  "delegates start" {
    val set = RelaySet(
      setOf(FakeRelay(), FakeRelay(), FakeRelay())
    )
    set.start()
    set.relays.map { it as FakeRelay }.map { it.started } shouldBe listOf(true, true, true)
  }

  "merge distinct events into a single flow" {
    val events = Arb.list(arbEvent, 20..20).next().distinct().take(6)
    val set = RelaySet(
      setOf(
        FakeRelay(events.take(2).toMutableList()),
        FakeRelay(events.drop(2).take(2).toMutableList()),
        FakeRelay(events.drop(4).take(2).toMutableList())
      )
    )
    set.subscribe(Filter.globalFeedNotes)

    set.allEvents.toList() shouldContainExactly events
  }
})

// Fake implementation for testing (defined in same file as tests)
class FakeRelay(val sent: MutableList<Event> = mutableListOf()) : Relay() {
  var started = false
  override fun start() {
    started = true
  }

  var stopped = false
  override fun stop() {
    stopped = true
  }

  override fun send(event: Event) {
    sent.add(event)
  }

  val subscriptions = mutableMapOf<Subscription, Set<Filter>>()
  override fun subscribe(filters: Set<Filter>, subscription: Subscription): Subscription =
    subscription.also {
      subscriptions[subscription] = filters
    }

  val unsubscriptions = mutableSetOf<Subscription>()
  override fun unsubscribe(subscription: Subscription) {
    unsubscriptions.add(subscription)
  }

  override val relayMessages: Flow<RelayMessage> =
    sent.asSequence()
      .zip(arbSubscriptionId.samples())
      .map { (event, id) -> EventMessage(id.value, event) }
      .asFlow()

  override val allEvents: Flow<Event> = sent.asFlow()
}
```

#### Testing Best Practices

1. **Use real dependencies** - Let controllers use real stores, validators, etc.
2. **Fake at boundaries** - Only fake external relay servers and HTTP endpoints
3. **Test both paths** - Write tests for success and failure cases
4. **Use property-based testing** - Leverage Kotest's `Arb` generators and `checkAll()` for comprehensive coverage
5. **Avoid redundant comments** - Let test names be descriptive. Never add AAA (Arrange/Act/Assert) comments
6. **Prefer brevity** - Keep tests concise and to the point
7. **Independent tests** - Each test creates its own test data
8. **Property-based test data** - Use Kotest's `Arb` generators where applicable

## Multiplatform Considerations

Nostrino is a Kotlin Multiplatform project. When making changes:

- **Platform-specific code**: Place in appropriate source sets (`jvmMain`, `commonMain`, etc.)
- **Common code first**: Prefer platform-agnostic implementations in `commonMain`
- **Platform implementations**: Use `expect`/`actual` pattern when platform-specific implementations are required
- **Dependencies**: Check platform compatibility of dependencies
- **Testing**: Focus integration tests in `lib-test` (JVM-only), unit tests can be in `lib`

## Binary Compatibility

The project uses the Kotlin Binary Compatibility Validator to prevent unintentional API changes.

- **API changes**: Run `gradle apiDump` after making intentional API changes
- **Before committing**: Always run `gradle apiCheck` to verify no unintentional breaking changes
- **API files**: Commit `.api` files alongside code changes
- **Breaking changes**: Avoid removals and additions in the same change to prevent downstream compatibility issues

## Module Responsibilities

### lib/
Main SDK library implementing Nostr protocol functionality. Contains multiplatform code for:
- Event creation and validation
- Cryptographic operations (signing, encryption)
- Relay communication
- Message encoding/decoding
- Filter construction

#### Adding New Message Types (NIPs)

When implementing a new NIP with message types:

1. **Package Structure**: Create a package named after the NIP (e.g., `app.cash.nostrino.nip47` for NIP-47)
2. **EventContent Implementation**: New message types should extend `EventContent` interface
3. **Kind Constants**: Define event kind as a constant in the companion object (e.g., `const val kind = 13194`)
4. **Tags**: If the NIP introduces new tags, add them to `app.cash.nostrino.model.Tag` (sealed interfaces require same package)

### lib-test/
Testing utilities and integration tests. Contains:
- Arb generators for test data (`ArbEvent`, `ArbPubKey`, etc.)
- Fake implementations (`FakeRelay`)
- Integration tests against real relay server
- Docker-based relay setup for testing

#### Testing Requirements for New Message Types

All new message types MUST include:

1. **Arb Generators**: Create `Arb<YourType>` generators in `lib-test/src/main/kotlin` matching the package structure
   - Example: `ArbNip47.kt` for NIP-47 message types
   - Compose existing Arb generators (e.g., `arbSecKey`, `arbPubKey`, `arbEvent`)

2. **Property-Based Tests**: Write tests using the Arb generators
   - Test serialization/deserialization round-trips
   - Test EventContent.sign() and validation
   - Use `checkAll()` for property-based testing where appropriate

3. **Example**:
```kotlin
// In lib-test/src/main/kotlin/app/cash/nostrino/nip47/ArbNip47.kt
val arbNip47Info = arbitrary {
  Nip47Info(
    capabilities = setOf("pay_invoice", "get_balance"),
    supportedEncryption = setOf("nip44_v2", "nip04"),
    notifications = setOf("payment_received")
  )
}

// In lib-test/src/test/kotlin/app/cash/nostrino/nip47/Nip47InfoTest.kt
class Nip47InfoTest : StringSpec({
  "round trip sign and parse" {
    val info = arbNip47Info.next()
    val signed = info.sign(arbSecKey.next())
    signed.validSignature shouldBe true
  }
})
```

## Publishing

The project is published to Maven Central:
- **Group**: `app.cash.nostrino`
- **License**: Apache License 2.0
- **Repository**: https://github.com/block/nostrino
- **Documentation**: https://cashapp.github.io/nostrino

## Contributing Guidelines

1. **Code Style**: Follow the `.editorconfig` settings (automatically enforced by IntelliJ IDEA)
2. **Testing**: All new features must include comprehensive tests
3. **Binary Compatibility**: Run `gradle apiCheck` before committing; use `gradle apiDump` for intentional API changes
4. **No Warnings**: Compiler is configured with `allWarningsAsErrors = true`
5. **Multiplatform**: Ensure changes work across all target platforms when possible
6. **Documentation**: Update module documentation and KDoc comments for public APIs
7. **Build Verification**: Always run `gradle build` before committing to ensure tests pass
8. **Dependencies**: Check existing dependencies before adding new ones
9. **Licensing**: New runtime dependencies must be licensed with Apache 2.0, MIT, BSD, ISC, or Creative Commons Attribution (test & build dependencies can be more flexible)

## Important Notes for LLMs

- **Never add wildcard imports** - use explicit imports for all Kotlin files
- **Use Kotest for assertions**, not JUnit assertions
- **Use StringSpec style** for test classes with string-based test names
- **Use Arb generators** for test data instead of hard-coded values
- **Check `.editorconfig`** before making formatting decisions
- **All tests use JUnit 5** (Jupiter) with Kotest - configured in root `build.gradle.kts`
- **Binary compatibility matters** - always run `gradle apiCheck` before committing
- **Multiplatform project** - be mindful of platform-specific vs common code placement
- **No compiler warnings allowed** - `allWarningsAsErrors = true` is set

@ai-rules/issue-tracking.md
