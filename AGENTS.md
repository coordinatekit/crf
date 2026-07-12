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

Order class members alphabetically (case-insensitive) within each category: constants, fields, constructors (by
parameter count), then methods. This covers static and instance members alike, plus enum constants.

Deviate only when another order is part of correctness, like enum constants a lookup walks in numeric order. Add a
comment at the declaration explaining why.

### Records vs Classes

Records must never appear on the public API surface. That rule is about records, not interfaces: it does not mean every
public value type needs one.

An interface is for polymorphism. Reach for one only when a public type has, or is about to have, more than one
implementation, and keep those implementations (record or class) package-private. A type with a single implementation is
a public final class built through a builder or static factory. Do not add an interface just to keep a record off the
public surface; a class removes the record without the extra indirection.

A public value class that participates in equality (stored in a set or map, or compared) declares its own `equals`,
`hashCode`, and `toString`, since it gives up the ones a record would generate.

Records remain the right choice for internal DTOs, package-private value types, private implementations of public
interfaces, and test parameters. Configuration objects are classes with a builder, never records.

### Models Factory

Public value types are constructed through a static factory method or builder, always reached through a static import.
Whether the type is an interface with private implementations or a public final class follows Records vs Classes above.
Factories are scoped to where the value type is used. `AnnotatorModels`, for example, lives in the annotator's `ui`
package and exposes the types used there.

### Code Analysis

After editing files, check for code analysis issues (unused imports, type mismatches, missing annotations, etc.) and fix
them. Do not add `@SuppressWarnings` annotations — fix the root cause. Existing suppressions may be intentional.

## Commit Messages

Follow [Conventional Commits](https://www.conventionalcommits.org/) format:

```
<type>[optional scope]: <description>
```

- **Types**: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`, `revert`
- **Scopes** (optional): `core`, `mallet`, `annotator`, `cli`
- Description must start with a lowercase letter
- Use imperative mood ("add feature" not "added feature")

Examples:

```
feat(core): add token normalization
fix(mallet): handle empty training sequences
docs: update installation instructions
refactor(core): simplify feature extraction pipeline
```

## Testing Conventions

### Test Organization

- Test methods should be sorted alphabetically by method name within each test class
- When testing a specific method or context, use double underscore to separate context from behavior:
  `{context}__{behavior}` (e.g., `of__combinesMultipleExtractors`, `builder__exception`)

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
- Place the parameter record and its `@MethodSource` provider immediately before the parameterized test
  method they belong to, rather than grouping all records at the top of the class
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
                    "negative_value",
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

- Record includes `Executable action` for the code that should throw
- Record includes `Class<? extends Exception> expectedClass` and `String expectedMessage`

## Architecture

This is a Conditional Random Fields (CRF) library for sequence labeling tasks, built on MALLET.

### Module Structure

- **core**: Abstractions, interfaces, and preprocessing pipeline (no MALLET dependency)
- **mallet**: MALLET-based CRF trainer implementation
- **annotator**: Parser-free `Configuration` and `Runner` types for the interactive `annotate` and `retokenize` flows
- **cli**: The picocli command-line front end. Wires the `annotate` and `retokenize` subcommands under a root `crf` command and delegates to the `annotator` runners

### Key Abstractions

- `TagProvider<T>`: Encodes/decodes tags between typed values and strings; defines the label space
- `FeatureExtractor<F>`: Extracts features from tokens at each position in a sequence
- `CrfTrainer`: Trains CRF models from paths and serializes output
- `CrfTagger<F, T>`: Tags input sequences using a trained model
- `CrfTaggerLoader`: Loads a `CrfTagger` from a serialized model file; the SPI the `mallet` module implements with `MalletCrfTaggerLoader`
- `TrainingDataSequencer<T>`: Reads training data from files into `TrainingSequence` streams

### Service Discovery

The CLI does not construct domain services directly. `org.coordinatekit.crf.core.spi` resolves each
SPI (tokenizer, feature extractor, tag provider, tagger loader) through `java.util.ServiceLoader`, so
a downstream registers `META-INF/services` files instead of writing a `main` or a factory.

- `CrfServices`: Discovers the domain SPIs and binds each slot to its built-in default
- `AmbiguousServiceException`: Thrown when more than one provider of a service type is registered and none was supplied explicitly
- `UnknownServiceException`: Thrown when a service is requested by name but no registered provider carries that name

A slot resolves by the precedence `explicit > single discovered provider > fallback`. The generic discovery kernel is package-private and reached through `CrfServices`.

`CrfTaggerLoader` additionally supports selection by name: each loader returns a stable `name()` (the
`mallet` loader returns `"mallet"`), and `CrfServices.taggerLoader(explicit, name)` picks the loader whose
name matches. The CLI exposes this as `--tagger-loader <name>`, the disambiguator when more than one loader
is on the classpath. Name selection is strict — a name that matches nothing throws `UnknownServiceException`
rather than falling back to a lone registered loader.

### Training Data Format

Training data uses XML with a CRF-specific namespace (`https://coordinatekit.org/schema/crf/training-data`):

- `<crf:Sequence>` elements wrap training examples
- Child elements represent tagged tokens (element name = tag, text content = token)
- `XmlTrainingData` handles reading/writing and can generate XSD schemas from `TagProvider`

### Training Pipeline

1. `TagProvider` defines the label set
2. `TrainingDataSequencer` reads XML into `TrainingSequence` streams
3. `FeatureExtractor` converts tokens to feature sets
4. `MalletCrfTrainer` orchestrates training with configurable parameters (iterations, regularization, threading, train/test split)
5. Trained model serialized to file, loadable by `MalletCrfTagger`
