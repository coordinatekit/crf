# Contributing to CRF

Thank you for your interest in contributing to the CRF library! This document provides guidelines and instructions for
contributing.

## Code of Conduct

This project adheres to a [Code of Conduct](https://github.com/coordinatekit/.github/blob/main/CODE_OF_CONDUCT.md). By
participating, you are expected to uphold this code. Please report unacceptable behavior to conduct@coordinatekit.org.

## How to Contribute

### Reporting Bugs

Before creating a bug report, please check existing issues to avoid duplicates. When creating a bug report, include:

- A clear, descriptive title
- Steps to reproduce the issue
- Expected behavior vs actual behavior
- Your environment (Java version, OS, library version)
- Relevant logs or error messages
- A minimal code example that reproduces the issue

### Suggesting Enhancements

Enhancement suggestions are welcome! Please include:

- A clear, descriptive title
- A detailed description of the proposed enhancement
- The motivation and use case
- Examples of how the enhancement would be used

### Pull Requests

We use a fork and pull request workflow:

1. **Fork the repository** - Create your own fork of the project on GitHub

2. **Clone your fork**
   ```bash
   git clone https://github.com/YOUR_USERNAME/crf.git
   cd crf
   ```

3. **Create a feature branch**
   ```bash
   git checkout -b feature/your-feature-name
   ```
   Use descriptive branch names:
    - `feature/` for new features
    - `fix/` for bug fixes
    - `docs/` for documentation changes
    - `refactor/` for code refactoring

4. **Make your changes** - Write your code following our coding standards

5. **Write tests** - Add tests for any new functionality

6. **Run the test suite**
   ```bash
   ./gradlew test
   ```

7. **Check code formatting**
   ```bash
   ./gradlew spotlessCheck
   ```
   If formatting issues are found, fix them with:
   ```bash
   ./gradlew spotlessApply
   ```

8. **Commit your changes**
   ```bash
   git add .
   git commit -m "Brief description of changes"
   ```
   Write clear, concise commit messages that explain what and why.

9. **Push to your fork**
   ```bash
   git push origin feature/your-feature-name
   ```

10. **Open a Pull Request** - Go to the original repository and create a pull request from your fork

## Development Setup

### Prerequisites

- Java 17 or higher
- Git

### Building the Project

```bash
# Build all modules
./gradlew build

# Build a specific module
./gradlew :core:build
./gradlew :mallet:build
./gradlew :lingpipe:build
```

### Running Tests

```bash
# Run all tests
./gradlew test

# Run tests for a specific module
./gradlew :core:test
./gradlew :mallet:test
./gradlew :lingpipe:test

# Run a specific test class
./gradlew :core:test --tests "org.coordinatekit.crf.core.InputSequenceTest"
```

### Code Coverage

```bash
./gradlew jacocoTestReport
```

Reports are generated in `<module>/build/reports/jacoco/`.

## Coding Standards

### Code Style

This project uses the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html), enforced via
Spotless. Run `./gradlew spotlessApply` to automatically format your code.

### Null Safety

We use [JSpecify](https://jspecify.dev/) annotations for null safety:

- All parameters are assumed non-null by default
- Use `@Nullable` to explicitly mark nullable parameters or return values
- Avoid returning null when an empty collection or `Optional` is appropriate

### Testing

- Write unit tests for all new functionality
- Maintain or improve code coverage
- Use descriptive test method names that explain what is being tested
- Follow the Arrange-Act-Assert pattern

### Documentation

- Add Javadoc comments for all public classes and methods
- Keep documentation up to date with code changes
- Include usage examples in documentation where helpful

## Pull Request Guidelines

### Before Submitting

- [ ] Code compiles without errors (`./gradlew build`)
- [ ] All tests pass (`./gradlew test`)
- [ ] Code is properly formatted (`./gradlew spotlessCheck`)
- [ ] New code has appropriate test coverage
- [ ] Documentation is updated if needed

### PR Description

Your pull request description should include:

- A summary of the changes
- The motivation for the changes
- Any breaking changes
- Related issue numbers (e.g., "Fixes #123")

### Review Process

1. A maintainer will review your PR
2. Address any requested changes
3. Once approved, a maintainer will merge your PR

## Questions?

If you have questions about contributing, feel free to:

- Open an issue with the "question" label
- Reach out to the maintainers

Thank you for contributing!
