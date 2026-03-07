# CLAUDE.md

This file provides guidance to agents when working with code in this repository.

## Build Commands

```bash
./gradlew build                    # Build all modules
./gradlew :core:build              # Build specific module
./gradlew test                     # Run all tests
./gradlew :core:test               # Run tests for specific module
./gradlew :core:test --tests "org.coordinatekit.crf.core.InputSequenceTest"  # Run specific test class
./gradlew spotlessCheck            # Check code formatting
./gradlew spotlessApply            # Apply code formatting
./gradlew jacocoTestReport         # Generate code coverage reports
```

## Code Style

- Uses Google Java Style Guide enforced via Spotless (Eclipse formatter config in `.infra/eclipse_java_coordinatekit.xml`)
- JSpecify annotations for null safety (`@NullMarked` on classes, `@Nullable` for nullable parameters/returns)
- All source files require Apache 2.0 license header (template in `.infra/license_header.txt`)
- Requires Java 21+
- Always run `./gradlew spotlessApply` before committing

### Naming Conventions

Avoid abbreviations in identifiers. Use complete words (`configuration` not `config`, `directory` not `dir`, `message`
not `msg`, `request` not `req`, `response` not `res`, `parameters` not `params`, `arguments` not `args`, `information`
not `info`, `temporary` not `temp`, `initialize` not `init`). Domain abbreviations like `URL`, `HTTP`, `ID` are
acceptable.

### Alphabetical Ordering

Class members ordered alphabetically (case-insensitive) within their category: constants, fields, constructors (by
parameter count), methods. Applies to instance/static fields, instance/static methods, constants, and enum values.

### Records vs Classes

Configuration objects must be **classes** with builder pattern, not records. Records are appropriate for DTOs, result
objects, domain model entities, and test parameters.

### Code Analysis

After editing files, check for code analysis issues (unused imports, type mismatches, missing annotations, etc.) and fix
them. Do not add `@SuppressWarnings` annotations — fix the root cause. Existing suppressions may be intentional.

## Testing Conventions

### Test Organization

- Test methods should be sorted alphabetically by method name within each test class

### Parameterized Tests

Use JUnit 5 parameterized tests with records for test cases that vary only by input/output data:

```java
record ExtractAtParameters(
        String name,           // Descriptive test case name
        int someInput,
        List<String> tokens,
        Set<String> expectedResult
) {}

static Stream<ExtractAtParameters> extractAt() {
    return Stream.of(
            new ExtractAtParameters("descriptive_case_name", 1, List.of("a"), Set.of("A")),
            // ... more test cases
    );
}

@MethodSource
@ParameterizedTest
void extractAt(ExtractAtParameters parameters) {
    // ARRANGE //
    // ... setup using parameters

    // ACT //
    var actual = ...;

    // ASSERT //
    assertEquals(parameters.expectedResult(), actual);
}
```

**Conventions:**
- Record name: `{TestMethodName}Parameters` (e.g., `ExtractAtParameters`)
- First field is always `String name` with a descriptive snake_case identifier
- Static method name matches the test method name (enables `@MethodSource` without arguments)
- Use `// ARRANGE //`, `// ACT //`, `// ASSERT //` comments in the test method

**When to use parameterized tests:**
- Multiple test cases with the same assertion logic but different inputs/outputs
- Testing boundary conditions, edge cases, or configuration variations

**When to use regular `@Test`:**
- Tests requiring different setup or assertion logic
- Tests with different generic types than the main parameterized tests

### Exception Tests

Use parameterized tests for exception behavior with `Executable` actions:

```java
record BuilderExceptionParameters(
        String name,
        Executable action,
        Class<? extends Exception> expectedClass,
        String expectedMessage
) {}

static Stream<BuilderExceptionParameters> builder__exception() {
    return Stream.of(
            new BuilderExceptionParameters(
                    "negativeValue",
                    () -> builder().someMethod(-1),
                    IllegalArgumentException.class,
                    "value must be non-negative"
            ),
            // ... more test cases
    );
}

@MethodSource
@ParameterizedTest
void builder__exception(BuilderExceptionParameters parameters) {
    // ACT //
    Exception exception = assertThrows(parameters.expectedClass(), parameters.action());

    // ASSERT //
    assertEquals(parameters.expectedMessage(), exception.getMessage());
}
```

**Conventions:**
- Method name uses double underscore to separate context from behavior: `{context}__{behavior}` (e.g., `builder__exception`)
- Record includes `Executable action` for the code that should throw
- Record includes `Class<? extends Exception> expectedClass` and `String expectedMessage`

## Architecture

This is a Conditional Random Fields (CRF) library for sequence labeling tasks, built on MALLET.

### Module Structure

- **core**: Abstractions, interfaces, and preprocessing pipeline (no MALLET dependency)
- **mallet**: MALLET-based CRF trainer implementation

### Key Abstractions

- `TagProvider<T>`: Encodes/decodes tags between typed values and strings; defines the label space
- `FeatureExtractor<F>`: Extracts features from tokens at each position in a sequence
- `CrfTrainer`: Trains CRF models from paths and serializes output
- `CrfTagger<F, T>`: Tags input sequences using a trained model
- `TrainingDataSequencer<T>`: Reads training data from files into `TrainingSequence` streams

### Training Data Format

Training data uses XML with a CRF-specific namespace (`https://coordinatekit.org/crf/schema`):
- `<crf:Sequence>` elements wrap training examples
- Child elements represent tagged tokens (element name = tag, text content = token)
- `XmlTrainingData` handles reading/writing and can generate XSD schemas from `TagProvider`

### Training Pipeline

1. `TagProvider` defines the label set
2. `TrainingDataSequencer` reads XML into `TrainingSequence` streams
3. `FeatureExtractor` converts tokens to feature sets
4. `MalletCrfTrainer` orchestrates training with configurable parameters (iterations, regularization, threading, train/test split)
5. Trained model serialized to file, loadable by `MalletCrfTagger`
