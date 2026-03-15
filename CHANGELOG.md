# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
