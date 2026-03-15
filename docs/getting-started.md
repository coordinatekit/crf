---
title: Getting Started
---

Conditional random fields are a type of machine learning that is used to apply a tag to tokens within a sequence when the surrounding tokens influence the tag. Conceptually, CRFs process sequences of tokens and apply tags to each token. A set of features are associated with each token, and the relationships of those features to each other are used to assign scores to each possible tag and assign the best tag to each token. Typical features include the length of the token, punctuation in the token, and tokens from the preceding and following tokens.

## Use cases of CRF

There are several common use cases including:

- Named entity recognition,
- Part-of-speech tagging,
- Address parsing, and
- Product catalog entity extraction

Named entity recognition (NER) and parts-of-speech (POS) tagging treat each sentence as a sequence and each word as a token. NER attempts to identify proper nouns such as people, organizations, and locations within a sentence. Tags might include `PERSON`, `ORGANIZATION`, `LOCATION`, and `NOT_ENTITY`. Whereas, POS tagging attempts to tag the part of speech for each token (e.g., nouns, verbs) and may use tags like `N` (noun), `V` (verb), and `PN` (pronoun), amongst others. Features for each token might include the first and last characters of the token (to capture prefixes like `pre-` and suffixes like `-ed`).

Address parsing treats each address string (e.g., `123 Main St, Anytown, US 12345`) as a sequence and each word in the address string as a token. The tags for address parsing might include `RECIPIENT`, `STREET_NAME`, `CITY`, and `POSTAL_CODE`, amongst others. Features might include the names of states, directions, and tokens matching a postal code pattern.

Product catalog entity extraction is similar to NER and address parsing. It treats the product description as the sequence and each word in the description as a token. The tags may be used to identify structured elements (e.g., brand, model number, size) and might include `BRAND`, `MODEL`, `SIZE`, and `COLOR`. Features might include known brands, sizes (e.g., `S`, `M`, `L`), and typical colors.

A straightforward example and common use case is applying the parts of speech (e.g., noun, verb) to words in a sentence. In parts of speech, each word in a sentence is not independent of the other words. In fact, the neighbor of each word influences its part of speech.

## Library architecture

This library is split into a core module and implementation-specific modules. The core module contains concepts common across CRF implementations including several sequence classes and feature providers. The implementation-specific modules translate the common concepts to the implementations. At present, the only implementation is for MALLET, a Java native library originating out of UMass Amherst for classifying and tagging text including conditional random fields.

## Key concepts

There are several concepts in the core module.

### Tags and tag providers

A tag provider tells the core module how to translate a raw string tag from a CRF implementation to another type. The intention is to translate raw string types to enum instances. This is ideal when the set of labels are defined and when further processing will occur on the tokens and tags.

```mermaid
classDiagram
    TagProvider <|-- StringTagProvider
    &lt;&lt;interface&gt;&gt; TagProvider
    TagProvider : *+decode(String tag) T
    TagProvider : *+encode(T rawTag) String
    TagProvider : *+startingTag() T
    TagProvider : *+tags() SortedSet~T~
```

_Diagram 1: The tag provider has a simple interface for translating to and from the raw string tag, getting all available tags (if known), and obtaining the starting (default) tag._

A `StringTagProvider` is included to expose the raw string tags.

### Feature extraction

Tags are assigned based on the relationship of a set of features to each other. A set of features are extracted from each token. Features tend to characterize the token such as token length, character case, common prefixes and suffixes, as well as punctuation. They may also incorporate known information such as city names, person names, and whether the token represents a number. This is a critical part of training your model--the better the features, the better the predictions.

Feature extraction is managed through the `FeatureExtractor` interface and its `extractAt` method.

```mermaid
classDiagram
    direction LR
    FeatureExtractor : +extractAt(Sequence sequence, int position) Set~F~*
    &lt;&lt;interface&gt;&gt; FeatureExtractor
```

_Diagram 2: Feature extraction is managed through the `FeatureExtractor` interface._

To simplify the feature extraction process, numerous common feature extractors are provided, including extractors to obtain the length of the token, extract the beginning and end of tokens, and check for matching regular expressions.

```mermaid
classDiagram
    direction LR
    FeatureExtractor <|-- CompositeFeatureExtractor
    FeatureExtractor <|-- LengthFeatureExtractor
    FeatureExtractor <|-- PatternMatchingFeatureExtractor
    FeatureExtractor <|-- PositionFeatureExtractor
    FeatureExtractor <|-- SubstringFeatureExtractor
    FeatureExtractor <|-- TransformingFeatureExtractor
    FeatureExtractor <|-- WindowFeatureExtractor
    FeatureExtractor <|-- XPathFeatureExtractor
    &lt;&lt;interface&gt;&gt; FeatureExtractor
    FeatureExtractor : +extractAt(Sequence sequence, int position) Set~F~*
```

_Diagram 3: Common feature extractors are included._

These common feature extractors are meant to be combined via the `CompositeFeatureExtractor`. The composite feature extractor allows you to use each feature extractor as a component. The window feature extractor can further be used to add features from nearby tokens.

```java
FeatureExtractor<String> compositeFeatureExtractor = new CompositeFeatureExtractor<>(List.of(
    // Extract the first 2 characters of each token (prefix)
    SubstringFeatureExtractor.builder(s -> "PREFIX2=" + s).length(2).build(),
    // Extract the first 3 characters of each token (prefix)
    SubstringFeatureExtractor.builder(s -> "PREFIX3=" + s).length(3).build(),
    // Extract the last 2 characters of each token (suffix)
    SubstringFeatureExtractor.builder(s -> "SUFFIX2=" + s).length(2).ending(true).build(),
    // Extract the last 3 characters of each token (suffix)
    SubstringFeatureExtractor.builder(s -> "SUFFIX3=" + s).length(3).ending(true).build(),
    // Extract features based on the sequence length
    LengthFeatureExtractor.builder(10).hasLengthFeatureMapper(len -> "HAS_LENGTH=" + len).build()
));
// Prepend the tokens from the previous and next two tokens with `<PREV|NEXT>_<OFFSET>__`
FeatureExtractor<String> featureExtractor = WindowFeatureExtractor
    .builder(compositeFeatureExtractor, (feature, offset) ->
        (offset < 0 ? "PREV_" + (-offset) : "NEXT_" + offset) + "__" + feature)
    .windowBefore(2)
    .windowAfter(2)
    .build();
```

### Training sequencer

Training data is similar to input data except it also contains information on the tag associated with each token. To handle both token and tag data, the library uses a `TrainingDataSequencer` that converts an input stream (typically a file) into a stream of training sequences.

```mermaid
classDiagram
    TrainingDataSequencer <|-- XmlTrainingData
    &lt;&lt;interface&gt;&gt; TrainingDataSequencer
    TrainingDataSequencer : *+read(InputStream input) Stream~TrainingSequence~T~~
```

_Diagram 4: The `TrainingDataSequencer` is responsible for converting an input stream to a stream of training sequences._

The library includes an `XmlTrainingData` class that includes the tokens with their tags in XML.

```xml
<Collection>
	<Sequence>
		<Determiner>The</Determiner>
		<Adjective>quick</Adjective>
		<Adjective>brown</Adjective>
		<Noun>fox</Noun>
		<Verb>jumps</Verb>
		<Preposition>over</Preposition>
		<Determiner>the</Determiner>
		<Adjective>lazy</Adjective>
		<Adjective>sleeping</Adjective>
		<Noun>dog.</Noun>
	</Sequence>
</Collection>
```

### Training models

Training a CRF model is the responsibility of the implementations of the `CrfTrainer` interface. The library includes `MalletCrfTrainer` which is an implementation for MALLET, a Java native library originating out of UMass Amherst for classifying and labeling text. The `MalletCrfTrainer` requires a `FeatureExtractor`, `TagProvider`, and `TrainingDataSequencer`.

A simple implementation of a MALLET CRF trainer is shown below.

```java
var tagProvider = new StringTagProvider("U");
var trainer = new MalletCrfTrainer(
    (sequence, position) -> Set.of(sequence.get(position).token(), "LENGTH=" + sequence.get(position).token().length()),
    tagProvider,
    new XmlTrainingData(tagProvider)
);
trainer.train(Path.of("training.xml"), Path.of("model.crf"));
```

### Tagging

Applying tags to inputs is handled by the concrete implementations of `CrfTagger`. The library includes a `MalletCrfTagger` that tags an input string using a trained MALLET CRF model. The `MalletCrfTagger` needs a `FeatureExtractor` and `TagProvider` as well as a `Tokenizer` to convert input data into a sequence of tokens.

The `MalletCrfTagger` will also require a serialized model to read which is the output of the `train` method in `MalletCrfTrainer`.

A simple implementation of a tagger is shown below.

```java
var tagger = new MalletCrfTagger(
    (sequence, position) -> Set.of(sequence.get(position).token(), "LENGTH=" + sequence.get(position).token().length()),
    Path.of("model.crf"),
    new StringTagProvider("U"),
    new WhitespaceTokenizer()
);
var sequence = tagger.tag("The quick brown fox jumps over the lazy sleeping dog.");
```

## Dependencies and installation

### Maven coordinates

Add the core and MALLET modules to your `pom.xml`:

```xml
<dependency>
    <groupId>org.coordinatekit.crf</groupId>
    <artifactId>core</artifactId>
    <version>0.1-SNAPSHOT</version>
</dependency>

<dependency>
    <groupId>org.coordinatekit.crf</groupId>
    <artifactId>mallet</artifactId>
    <version>0.1-SNAPSHOT</version>
</dependency>
```

The `mallet` module transitively includes `core`, so adding only `mallet` is sufficient if you do not need the core module independently.

### Gradle setup

Add the dependencies to your `build.gradle`:

```groovy
dependencies {
    implementation "org.coordinatekit.crf:core:0.1-SNAPSHOT"
    implementation "org.coordinatekit.crf:mallet:0.1-SNAPSHOT"
}
```

Or in `build.gradle.kts`:

```kotlin
dependencies {
    implementation("org.coordinatekit.crf:core:0.1-SNAPSHOT")
    implementation("org.coordinatekit.crf:mallet:0.1-SNAPSHOT")
}
```

### Java version requirements

This library requires **Java 21** or later.

## Quick start

This section walks through a complete parts-of-speech tagging example: creating training data, defining tags, extracting features, training a model, and tagging new text.

### Creating training data XML

Training data is provided as XML where each element name is the tag and the element text is the token. Sequences are grouped under `<Sequence>` elements within a root `<Collection>` element.

Create a file called `training.xml`:

```xml
<Collection>
	<Sequence>
		<Determiner>The</Determiner>
		<Adjective>quick</Adjective>
		<Adjective>brown</Adjective>
		<Noun>fox</Noun>
		<Verb>jumps</Verb>
		<Preposition>over</Preposition>
		<Determiner>the</Determiner>
		<Adjective>lazy</Adjective>
		<Noun>dog</Noun>
	</Sequence>
	<Sequence>
		<Determiner>A</Determiner>
		<Noun>cat</Noun>
		<Verb>sat</Verb>
		<Preposition>on</Preposition>
		<Determiner>the</Determiner>
		<Noun>mat</Noun>
	</Sequence>
</Collection>
```

### Defining a tag provider

A `TagProvider` translates raw string tags to a usable type. The `StringTagProvider` passes strings through directly. Provide the set of valid tags and a starting (default) tag:

```java
var tagProvider = new StringTagProvider(
    Set.of("Adjective", "Determiner", "Noun", "Preposition", "Verb"),
    "Noun"
);
```

### Building a basic feature extractor

Feature extractors characterize each token to help the model assign tags. Combine multiple extractors with `CompositeFeatureExtractor`:

```java
var compositeFeatureExtractor = new CompositeFeatureExtractor<>(List.of(
    // Extract the first 3 characters of each token (prefix)
    SubstringFeatureExtractor.<String>builder(s -> "PREFIX=" + s)
        .length(3).build(),
    // Extract the last 3 characters of each token (suffix)
    SubstringFeatureExtractor.<String>builder(s -> "SUFFIX=" + s)
        .length(3).ending(true).build(),
    // Extract features based on the sequence length
    LengthFeatureExtractor.<String>builder(10)
        .hasLengthFeatureMapper(len -> "HAS_LENGTH=" + len).build()
));
```

To incorporate context from neighboring tokens, wrap the feature extractor in a `WindowFeatureExtractor`. This extracts features from surrounding tokens and prefixes them with their relative position:

```java
var featureExtractor = WindowFeatureExtractor
    .builder(compositeFeatureExtractor, (feature, offset) ->
        (offset < 0 ? "PREV_" + (-offset) : "NEXT_" + offset) + "__" + feature)
    .windowBefore(2)
    .windowAfter(2)
    .build();
```

### Training the model

Create a `MalletCrfTrainer` with the feature extractor, tag provider, and an `XmlTrainingData` sequencer, then train the model:

```java
var trainer = new MalletCrfTrainer<>(
    featureExtractor,
    tagProvider,
    new XmlTrainingData<>(tagProvider)
);
trainer.train(Path.of("training.xml"), Path.of("pos-model.crf"));
```

The trained model is serialized to `pos-model.crf`.

### Tagging new text

Create a `MalletCrfTagger` with the same feature extractor and tag provider, plus a tokenizer and the trained model:

```java
var tagger = new MalletCrfTagger<>(
    featureExtractor,
    Path.of("pos-model.crf"),
    tagProvider,
    new WhitespaceTokenizer()
);
var result = tagger.tag("The brown cat sat quietly");

for (int i = 0; i < result.size(); i++) {
    var token = result.get(i);
    System.out.println(token.token() + " -> " + token.tag());
}
```
