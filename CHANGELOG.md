# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.2.0] - 2026-06-28

### Features

- handle untokenizable input gracefully with warnings and session continuation ([#66](https://github.com/coordinatekit/crf/pull/66))
- introduce tagger loader selection by name and related exception handling ([#63](https://github.com/coordinatekit/crf/pull/63))
- introduce deserialization allowlist for improved security ([#59](https://github.com/coordinatekit/crf/pull/59))
- add sequence likelihood scoring for interactive tag revision ([#39](https://github.com/coordinatekit/crf/pull/39))
- support separate key and full feature extractors in service resolution and CRF assembly ([#38](https://github.com/coordinatekit/crf/pull/38))
- add dynamic brand banner rendering to CLI usage output ([#37](https://github.com/coordinatekit/crf/pull/37))
- add retokenize tool for re-tagging XML training data ([#34](https://github.com/coordinatekit/crf/pull/34))
- add tokenizer alignment detection framework ([#30](https://github.com/coordinatekit/crf/pull/30))
- add structural schema for CRF training data and implement validation framework ([#29](https://github.com/coordinatekit/crf/pull/29))
- add configurable feature display in tagging interface ([#28](https://github.com/coordinatekit/crf/pull/28))
- add `AnnotatorCli` for terminal-based sequence annotation and expand test coverage ([#24](https://github.com/coordinatekit/crf/pull/24))
- introduce `Annotator` and `AnnotatorTest` for interactive annotation sessions ([#22](https://github.com/coordinatekit/crf/pull/22))
- add `annotator` module with JLine-based tagging interface ([#21](https://github.com/coordinatekit/crf/pull/21))
- add streaming and appending writers for XML training data ([#20](https://github.com/coordinatekit/crf/pull/20))

### Bug Fixes

- prevent file-handle leak in TrainingDataSequencer.read(Path) ([#45](https://github.com/coordinatekit/crf/pull/45))
- harden XML parsing against XXE and entity-expansion attacks ([#44](https://github.com/coordinatekit/crf/pull/44))
- handle unchecked exceptions in model loading and parsing ([#43](https://github.com/coordinatekit/crf/pull/43))
- ensure proper spacing between consecutive XML elements in training data serialization ([#26](https://github.com/coordinatekit/crf/pull/26))

### Refactoring

- remove unused imports, switch visibility for methods, and consolidate resource path resolution ([#64](https://github.com/coordinatekit/crf/pull/64))
- move CLI to dedicated module ([#36](https://github.com/coordinatekit/crf/pull/36))
- simplify CLI structure by centralizing shared logic ([#35](https://github.com/coordinatekit/crf/pull/35))
- distinguish strict and lax wildcards for tag validation ([#33](https://github.com/coordinatekit/crf/pull/33))
- make target namespace optional for schemas and documents ([#32](https://github.com/coordinatekit/crf/pull/32))
- decompose and reorganize terminal components ([#31](https://github.com/coordinatekit/crf/pull/31))
- model tokenization as segments to preserve excluded characters ([#27](https://github.com/coordinatekit/crf/pull/27))
- consolidate `ui` package into `annotator` package ([#23](https://github.com/coordinatekit/crf/pull/23))

### Documentation

- add guide for annotating training data and update examples ([#41](https://github.com/coordinatekit/crf/pull/41))
- add design documentation and refactor .gitignore ([5e91ef922a65790](https://github.com/coordinatekit/crf/commit/5e91ef922a6579091762f2720fd4c584dccd920b))

### Build

- enhance version bump script to support previous version updates and stale reference checks ([#70](https://github.com/coordinatekit/crf/pull/70))
- update publishing logic for Maven Central and GitHub Packages ([#65](https://github.com/coordinatekit/crf/pull/65))
- prepare for gradle 10 by capturing version catalog in root context ([#62](https://github.com/coordinatekit/crf/pull/62))
- upgrade git-changelog to 3.x and migrate changelog templates ([#61](https://github.com/coordinatekit/crf/pull/61))
- bump gradle-wrapper from 8.14 to 9.6.1 ([#55](https://github.com/coordinatekit/crf/pull/55))
- update logback and exclude unused jdom dependency ([#60](https://github.com/coordinatekit/crf/pull/60))
- bump com.github.jk1.dependency-license-report from 3.1.2 to 3.1.4 ([#49](https://github.com/coordinatekit/crf/pull/49))
- bump com.google.errorprone:error_prone_core from 2.36.0 to 2.50.0 ([#56](https://github.com/coordinatekit/crf/pull/56))
- bump org.junit:junit-bom from 6.0.1 to 6.1.0 ([#57](https://github.com/coordinatekit/crf/pull/57))
- bump gradle/actions from 4 to 6 ([#47](https://github.com/coordinatekit/crf/pull/47))
- bump actions/setup-java from 4 to 5 ([#50](https://github.com/coordinatekit/crf/pull/50))
- bump softprops/action-gh-release from 2 to 3 ([#52](https://github.com/coordinatekit/crf/pull/52))
- bump peter-evans/create-pull-request from 7 to 8 ([#51](https://github.com/coordinatekit/crf/pull/51))
- bump amannn/action-semantic-pull-request from 5 to 6 ([#53](https://github.com/coordinatekit/crf/pull/53))
- bump https://github.com/compilerla/conventional-pre-commit ([#54](https://github.com/coordinatekit/crf/pull/54))
- configure Dependabot for automated dependency updates ([#46](https://github.com/coordinatekit/crf/pull/46))
- enforce NullAway for null-safety and refactor for stricter validation ([#42](https://github.com/coordinatekit/crf/pull/42))
- add "ext" directory support for runtime drop-in JARs ([#40](https://github.com/coordinatekit/crf/pull/40))
- fix Gradle deprecation and suppress Error Prone InlineMeSuggester check ([af6aa2816910a3f](https://github.com/coordinatekit/crf/commit/af6aa2816910a3fb44fa626744f8e60c3aa0eed9))
- refactor dependency management to use centralized versions catalog ([#25](https://github.com/coordinatekit/crf/pull/25))
- update project version to 0.2.0-SNAPSHOT for ongoing development ([48ada0f904b7b77](https://github.com/coordinatekit/crf/commit/48ada0f904b7b77e3d4801cdb0c183a2bfa83c79))

### CI

- update actions/checkout to v7 across all workflows ([#69](https://github.com/coordinatekit/crf/pull/69))
- add workflow for publishing Maven Central snapshots ([#67](https://github.com/coordinatekit/crf/pull/67))
- expand path triggers in workflows for Gradle files and scripts ([#58](https://github.com/coordinatekit/crf/pull/58))
- add "annotator" to allowed PR title scopes in lint-pr-title workflow ([7b8f1c20af2b87a](https://github.com/coordinatekit/crf/commit/7b8f1c20af2b87a7f249e254386e74905b9ef26f))

[0.2.0]: https://github.com/coordinatekit/crf/releases/tag/v0.2.0

## [0.1.0] - 2026-03-15

Initial release of the CoordinateKit CRF library for sequence labeling tasks.

### Added

#### Core Module (`org.coordinatekit.crf:core`)

- `TagProvider` interface for encoding/decoding tags and defining the label space
- `StringTagProvider` implementation for string-based tag sets
- `Sequence`, `PositionedToken`, `InputSequence` abstractions for token sequences
- `Tokenizer` interface with `WhitespaceTokenizer` implementation
- `FeatureExtractor` interface for extracting features from token sequences
- `CompositeFeatureExtractor` for combining multiple extractors
- `WindowFeatureExtractor` for context-aware feature extraction from neighboring tokens
- `SubstringFeatureExtractor` for prefix/suffix-based features
- `PatternMatchingFeatureExtractor` for regex-based features
- `LengthFeatureExtractor` for sequence length features
- `PositionFeatureExtractor` for token position features
- `TransformingFeatureExtractor` for custom token-to-feature mappings
- `TrainingSequence` and related token types for training data representation
- `FeatureSequence` and `FeatureTrainingSequence` for feature-enriched sequences
- `TrainingDataSequencer` interface for reading training data
- `XmlTrainingData` for reading/writing CRF training data in XML format
- XSD schema generation from `TagProvider` via `TrainingSchemaGenerator`
- `CrfTrainer` interface for training CRF models
- `CrfTagger` interface for sequence labeling with trained models
- `TaggedSequence` and `TagScore` for ranked tag predictions with confidence scores
- JSpecify null safety annotations throughout

#### MALLET Module (`org.coordinatekit.crf:mallet`)

- `MalletCrfTrainer` for training CRF models using MALLET's L-BFGS optimization
- Multithreaded training via `CRFTrainerByThreadedLabelLikelihood`
- `MalletCrfTrainerConfiguration` with builder for training parameters (iterations, Gaussian variance, thread count, training fraction, weights type)
- `WeightsType` enum for feature weight storage strategies (dense, sparse, some-dense)
- Configurable train/test split with `TrainingTestSplit`
- `CompositeTestAccuracyEvaluator` for evaluating model accuracy during training
- `ConllOutputEvaluator` and `ConllOutputConfiguration` for CoNLL-format prediction output
- `ModelOutputEvaluator` and `ModelOutputConfiguration` for saving model checkpoints
- `MalletCrfTagger` for tagging sequences using trained MALLET CRF models with sum-product inference

[0.1.0]: https://github.com/coordinatekit/crf/releases/tag/v0.1.0
