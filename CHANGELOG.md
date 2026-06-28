# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [v0.2.0] - 2026-06-28

### Features

- handle untokenizable input gracefully with warnings and session continuation (#66) ([f967b252fef8e7a](https://github.com/coordinatekit/crf/commit/f967b252fef8e7a014a15f34aa18ba3de8bae6b9))
- introduce tagger loader selection by name and related exception handling (#63) ([e2970cd9f752b97](https://github.com/coordinatekit/crf/commit/e2970cd9f752b9723b1a80a2260ae6293f148713))
- introduce deserialization allowlist for improved security (#59) ([7b9a90500c60540](https://github.com/coordinatekit/crf/commit/7b9a90500c60540264a0475bcf838b80da63811a))
- add sequence likelihood scoring for interactive tag revision (#39) ([23d8ee29a840a4e](https://github.com/coordinatekit/crf/commit/23d8ee29a840a4ef25ac89baa939bb140183ec51))
- support separate key and full feature extractors in service resolution and CRF assembly (#38) ([d056ba36e3ac129](https://github.com/coordinatekit/crf/commit/d056ba36e3ac129a6a4236c8808eeb0a9ce0a180))
- add dynamic brand banner rendering to CLI usage output (#37) ([1f1d473fe0170c3](https://github.com/coordinatekit/crf/commit/1f1d473fe0170c35827ab5d50315bf310846afd4))
- add retokenize tool for re-tagging XML training data (#34) ([e610c651af9fe31](https://github.com/coordinatekit/crf/commit/e610c651af9fe313b5de25fba8aea89bdd13a38d))
- add tokenizer alignment detection framework (#30) ([270a0c76212ec5a](https://github.com/coordinatekit/crf/commit/270a0c76212ec5af0fe9cb317b3d237a8b5a0585))
- add structural schema for CRF training data and implement validation framework (#29) ([88e8323adfc0e9c](https://github.com/coordinatekit/crf/commit/88e8323adfc0e9cabd77c7ef45934f1b46d13040))
- add configurable feature display in tagging interface (#28) ([b483a471b7783d6](https://github.com/coordinatekit/crf/commit/b483a471b7783d6d2ed17a38612d6d7f59571434))
- add `AnnotatorCli` for terminal-based sequence annotation and expand test coverage (#24) ([f33cf344f25570e](https://github.com/coordinatekit/crf/commit/f33cf344f25570e2736b6735d0251a2d19045a7a))
- introduce `Annotator` and `AnnotatorTest` for interactive annotation sessions (#22) ([cc59627963b67e5](https://github.com/coordinatekit/crf/commit/cc59627963b67e58fc95bf101a4324d9d5a6e9f2))
- add `annotator` module with JLine-based tagging interface (#21) ([70c20dffc56b57e](https://github.com/coordinatekit/crf/commit/70c20dffc56b57e96d163f602e5462754ba531ab))
- add streaming and appending writers for XML training data (#20) ([1154aa3976c8721](https://github.com/coordinatekit/crf/commit/1154aa3976c87211e70c379c584262db3bb2dd55))

### Bug Fixes

- prevent file-handle leak in TrainingDataSequencer.read(Path) (#45) ([9f5d77e8bcc9d5e](https://github.com/coordinatekit/crf/commit/9f5d77e8bcc9d5ed3db453b3038223c4b89c4355))
- harden XML parsing against XXE and entity-expansion attacks (#44) ([156164c169590dd](https://github.com/coordinatekit/crf/commit/156164c169590dd41affce246294a7458afde094))
- handle unchecked exceptions in model loading and parsing (#43) ([eb8db0e7b8d230e](https://github.com/coordinatekit/crf/commit/eb8db0e7b8d230e1d50f0813c0ffc0bcb43cb100))
- ensure proper spacing between consecutive XML elements in training data serialization (#26) ([8e010a219822fad](https://github.com/coordinatekit/crf/commit/8e010a219822fad3cb1d75e4bc2c213b8395100b))

### Refactoring

- remove unused imports, switch visibility for methods, and consolidate resource path resolution (#64) ([5a3d63fe9021063](https://github.com/coordinatekit/crf/commit/5a3d63fe9021063194c3c706ca83237bb3f065b5))
- move CLI to dedicated module (#36) ([f1b1ac6ccd68170](https://github.com/coordinatekit/crf/commit/f1b1ac6ccd68170c8d92b52f83222ee3276ffb9e))
- simplify CLI structure by centralizing shared logic (#35) ([b51745b08769ddc](https://github.com/coordinatekit/crf/commit/b51745b08769ddce0a122a83514edcfdfd0a55b3))
- distinguish strict and lax wildcards for tag validation (#33) ([659b36b1ad55686](https://github.com/coordinatekit/crf/commit/659b36b1ad556861a51504ba26cc802e3b17ce81))
- make target namespace optional for schemas and documents (#32) ([1ab069071f1a85b](https://github.com/coordinatekit/crf/commit/1ab069071f1a85bcec7b87b384d746c2900d92d9))
- decompose and reorganize terminal components (#31) ([abeafea68c835b2](https://github.com/coordinatekit/crf/commit/abeafea68c835b26993f322c3ab7c99a20260f34))
- model tokenization as segments to preserve excluded characters (#27) ([b327594d382dab9](https://github.com/coordinatekit/crf/commit/b327594d382dab9d97d97fc925121f49325c76aa))
- consolidate `ui` package into `annotator` package (#23) ([8317a5390af01a0](https://github.com/coordinatekit/crf/commit/8317a5390af01a00d6318aac77ad699753fcbc4c))

### Documentation

- add guide for annotating training data and update examples (#41) ([cd651d4b1fd2b77](https://github.com/coordinatekit/crf/commit/cd651d4b1fd2b77b454fb1fc8c7e15caee7dd1ce))
- add design documentation and refactor .gitignore ([5e91ef922a65790](https://github.com/coordinatekit/crf/commit/5e91ef922a6579091762f2720fd4c584dccd920b))

### Build

- update publishing logic for Maven Central and GitHub Packages (#65) ([3ab3d2e1c8167ca](https://github.com/coordinatekit/crf/commit/3ab3d2e1c8167ca2fd6b447659f13c7c94df78ae))
- prepare for gradle 10 by capturing version catalog in root context (#62) ([3515bfee8881acd](https://github.com/coordinatekit/crf/commit/3515bfee8881acda95efca8a5b703d4073cb14d0))
- upgrade git-changelog to 3.x and migrate changelog templates (#61) ([db38a3bbf290092](https://github.com/coordinatekit/crf/commit/db38a3bbf2900928fd68dab8b637c6a7fb728132))
- bump gradle-wrapper from 8.14 to 9.6.1 (#55) ([561a72d3eee8344](https://github.com/coordinatekit/crf/commit/561a72d3eee8344f75af1e912ee65ca3971895d8))
- update logback and exclude unused jdom dependency (#60) ([7e360f769cbc481](https://github.com/coordinatekit/crf/commit/7e360f769cbc4816c0af7510690b7dd69d374578))
- bump com.github.jk1.dependency-license-report from 3.1.2 to 3.1.4 (#49) ([d28f8c5b1cbad48](https://github.com/coordinatekit/crf/commit/d28f8c5b1cbad48fe256209571c29e126c9cdd25))
- bump com.google.errorprone:error_prone_core from 2.36.0 to 2.50.0 (#56) ([0ebee718b78a42f](https://github.com/coordinatekit/crf/commit/0ebee718b78a42fc170e41d7de0b786cb21ea058))
- bump org.junit:junit-bom from 6.0.1 to 6.1.0 (#57) ([759631080811ef4](https://github.com/coordinatekit/crf/commit/759631080811ef4ee3880f0d9e6ba1c1a6d5ea66))
- bump gradle/actions from 4 to 6 (#47) ([a37bd7964d59d09](https://github.com/coordinatekit/crf/commit/a37bd7964d59d097e6415998da7b1268e47af38f))
- bump actions/setup-java from 4 to 5 (#50) ([d6ebf2e9837fe47](https://github.com/coordinatekit/crf/commit/d6ebf2e9837fe4758a32833757e8e232045459fe))
- bump softprops/action-gh-release from 2 to 3 (#52) ([dd0db53863d34d7](https://github.com/coordinatekit/crf/commit/dd0db53863d34d7cb749153cc31add10b02622ce))
- bump peter-evans/create-pull-request from 7 to 8 (#51) ([9dcf991c54000f2](https://github.com/coordinatekit/crf/commit/9dcf991c54000f2a27a2cd7481f64cd01fba43b9))
- bump amannn/action-semantic-pull-request from 5 to 6 (#53) ([6e9a08efee4e6cb](https://github.com/coordinatekit/crf/commit/6e9a08efee4e6cb8620d74262838f50e0496205c))
- bump https://github.com/compilerla/conventional-pre-commit (#54) ([3de04bfae2a5499](https://github.com/coordinatekit/crf/commit/3de04bfae2a549965207e5b270387465ec82bd93))
- configure Dependabot for automated dependency updates (#46) ([43927e352ce14ae](https://github.com/coordinatekit/crf/commit/43927e352ce14ae3f6b9b8603c8026b87791b846))
- enforce NullAway for null-safety and refactor for stricter validation (#42) ([149ac84e7c005c2](https://github.com/coordinatekit/crf/commit/149ac84e7c005c272408d7cfd4c466192c19fe1f))
- add "ext" directory support for runtime drop-in JARs (#40) ([c45c15f16de8e1e](https://github.com/coordinatekit/crf/commit/c45c15f16de8e1e0f19812e3287b25c8154906eb))
- fix Gradle deprecation and suppress Error Prone InlineMeSuggester check ([af6aa2816910a3f](https://github.com/coordinatekit/crf/commit/af6aa2816910a3fb44fa626744f8e60c3aa0eed9))
- refactor dependency management to use centralized versions catalog (#25) ([1a9001516f29565](https://github.com/coordinatekit/crf/commit/1a9001516f295654431a96b22e6e6b795b69b643))
- update project version to 0.2.0-SNAPSHOT for ongoing development ([48ada0f904b7b77](https://github.com/coordinatekit/crf/commit/48ada0f904b7b77e3d4801cdb0c183a2bfa83c79))

### CI

- add workflow for publishing Maven Central snapshots (#67) ([c4b82c6383ad36b](https://github.com/coordinatekit/crf/commit/c4b82c6383ad36b96391006751f37b752ec45136))
- expand path triggers in workflows for Gradle files and scripts (#58) ([0ed4288c06debfa](https://github.com/coordinatekit/crf/commit/0ed4288c06debfac2310b9fbf1d5ba37005fc5e7))
- add "annotator" to allowed PR title scopes in lint-pr-title workflow ([7b8f1c20af2b87a](https://github.com/coordinatekit/crf/commit/7b8f1c20af2b87a7f249e254386e74905b9ef26f))

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
