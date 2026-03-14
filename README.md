# CRF - Conditional Random Fields Wrapper Library

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java Version](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org/)

A developer-friendly Java library for Conditional Random Fields (CRF) sequence labeling, built
on [MALLET](https://mimno.github.io/Mallet).

## What are Conditional Random Fields?

Conditional random fields are a type of machine learning used to apply labels to tokens within a sequence when
surrounding tokens influence the label. Common use cases include:

- **Named Entity Recognition (NER)** - Identifying people, places, and organizations in text
- **Part-of-Speech Tagging** - Labeling words as nouns, verbs, adjectives, etc.
- **Address Parsing** - Breaking addresses into components (street number, street name, city, state, ZIP)
- **Information Extraction** - Extracting structured data from unstructured text

In sequence labeling, context matters. For example, when parsing an address like "109 UNIVERSITY ST MARTIN TN", a
five-digit number following a state abbreviation is likely a ZIP code, and the word before the state is likely the city.
CRFs learn these patterns from training data and use them to label new sequences.

## Why This Library?

While excellent CRF implementations
exist ([MALLET](https://mimno.github.io/Mallet), [Stanford CoreNLP](https://stanfordnlp.github.io/CoreNLP), [CRFsuite](https://www.chokkan.org/software/crfsuite)),
their APIs are designed for flexibility, which often means writing significant boilerplate code. This library provides:

- **Isolated complexity** - Repetitive training and tagging code is encapsulated; you implement a few focused interfaces
- **Separated concerns** - Clean division between the CRF implementation details and your application logic
- **Generalized abstractions** - Core concepts are abstracted, making it possible to swap CRF implementations without
  rewriting application code

## Design Goals

- **Quick start** - Minimal implementation needed to start benefiting from CRF
- **Extensible** - Override any component without reimplementing entire classes; sensible defaults that don't require
  extension
- **Fluent API** - Configure everything in code without endless instance variables
- **Spring ready** - Constructor-based injection makes defining `@Bean`s straightforward
- **Modular** - Common components in `core`, CRF library-specific code in separate modules
- **Minimal dependencies** - No unnecessary transitive dependencies
- **Well-tested** - Comprehensive test coverage so bugs come from your code or the CRF library, not this wrapper

## Features

- **Simple API** - Clean interfaces for training and tagging
- **Flexible Preprocessing** - Extensible tokenization and feature extraction pipeline
- **XML Training Data** - Built-in support for XML-formatted annotated training data
- **Multi-threaded Training** - Parallel training support via MALLET
- **Configurable** - Fine-grained control over training parameters, regularization, and model output

## Project Structure

```
crf/
├── core/       # Core abstractions, interfaces, and preprocessing pipeline
└── mallet/     # MALLET-based CRF trainer implementation
```

## Requirements

- Java 21 or higher
- Gradle 8.x (wrapper included)

## Installation

### Gradle

Add the following to your `build.gradle`:

```groovy
repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.coordinatekit.crf:core:0.1.0'
    implementation 'org.coordinatekit.crf:mallet:0.1.0'
}
```

### Maven

```xml
<dependency>
    <groupId>org.coordinatekit.crf</groupId>
    <artifactId>core</artifactId>
    <version>0.1.0</version>
</dependency>

<dependency>
    <groupId>org.coordinatekit.crf</groupId>
    <artifactId>mallet</artifactId>
    <version>0.1.0</version>
</dependency>
```

## Quick Start

### 1. Define Your Features

```java
public class MyFeatureExtractor implements FeatureExtractor<String> {
    @Override
    public List<String> extractFeatures(List<PositionedToken> tokens, int position) {
        List<String> features = new ArrayList<>();
        String token = tokens.get(position).token();

        features.add("TOKEN=" + token);
        features.add("LOWER=" + token.toLowerCase());

        if (Character.isUpperCase(token.charAt(0))) {
            features.add("CAPITALIZED");
        }

        return features;
    }
}
```

### 2. Prepare Training Data

```java
// Create a tag provider for your label set
TagProvider<String> crfTagProvider = new StringTagProvider(
    List.of("PERSON", "LOCATION", "ORGANIZATION", "O")
);

// Load training data from XML
XmlTrainingDataSequencer<String> sequencer = new XmlTrainingDataSequencer<>(
    crfTagProvider,
    new WhitespaceTokenizer()
);

List<TrainingSequence<String>> trainingData = sequencer.readTrainingData(inputStream);
```

### 3. Train a Model

```java
FeatureExtractor<String> featureExtractor = new MyFeatureExtractor();

MalletCrfTrainerConfiguration config = MalletCrfTrainerConfiguration.builder()
    .maxIterations(500)
    .gaussianPriorVariance(1.0)
    .build();

MalletCrfTrainer<String, String> trainer = new MalletCrfTrainer<>(
    featureExtractor,
    crfTagProvider,
    config
);

CrfTagger<String, String> tagger = trainer.train(trainingData);
```

### 4. Tag New Sequences

```java
InputSequence input = new InputSequence(List.of("John", "works", "at", "Acme", "Corp"));
TaggedSequence<String, String> result = tagger.tag(input);

for (TaggedPositionedToken<String, String> token : result.tokens()) {
    System.out.println(token.token() + " -> " + token.tag());
}
```

## Building from Source

```bash
# Clone the repository
git clone https://github.com/coordinatekit/crf.git
cd crf

# Build the project
./gradlew build

# Run tests
./gradlew test

# Check code formatting
./gradlew spotlessCheck

# Apply code formatting
./gradlew spotlessApply
```

## Contributing

Contributions are welcome! Please read our [Contributing Guidelines](CONTRIBUTING.md)
and [Code of Conduct](https://github.com/coordinatekit/.github/blob/main/CODE_OF_CONDUCT.md) before submitting a pull
request.

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

This library builds upon [MALLET](https://mimno.github.io/Mallet/) (MAchine Learning for LanguagE Toolkit) from UMass
Amherst.

