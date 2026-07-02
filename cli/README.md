# CLI

The `crf` command-line tool: a [picocli](https://picocli.info) front end hosting
the interactive `annotate` and `retokenize` flows. It discovers your tag provider,
tokenizer, and feature extractors through `ServiceLoader` and assembles the tool
around them, so there is no `main` to write. The distribution bundles the `mallet`
model loader, so `--model` reads MALLET models out of the box; you supply the
task-specific pieces.

## Getting the tool

`cli` builds the `crf` distribution rather than a published library. Run
`./gradlew :cli:installDist` and you get a self-contained `crf` under
`cli/build/install/crf`, with `core`, `annotator`, and the `mallet` model loader
(and its dependencies) already on the classpath. `./gradlew :cli:distZip` packages
the same tree as a zip.

The distribution ships everything except the task-specific components, which are
yours to supply: at minimum a tag provider, and usually a feature extractor.
Register them as services in a jar and drop it in the distribution's `ext/`
directory, which is on the `crf` classpath (see [Registering your
components](#registering-your-components)).

The underlying `annotator` module stays a plain published library, so you can
embed the `annotate` and `retokenize` flows directly if the `crf` command doesn't
fit. This module is only the command-line wiring.

## Registering your components

`crf` resolves five slots through `ServiceLoader`, each as
`explicit > a single registered service > a built-in default`:

| Slot                   | Service interface                                         | Default                          | Needed when                |
| ---------------------- | --------------------------------------------------------- | -------------------------------- | -------------------------- |
| Tag provider           | `org.coordinatekit.crf.core.TagProvider`                  | none (required)                  | always                     |
| Tokenizer              | `org.coordinatekit.crf.core.preprocessing.Tokenizer`      | `WhitespaceTokenizer`            | always                     |
| Full feature extractor | `org.coordinatekit.crf.core.feature.FullFeatureExtractor` | none (tags without features)     | recommended with `--model` |
| Key feature extractor  | `org.coordinatekit.crf.core.feature.KeyFeatureExtractor`  | falls back to the full extractor | optional                   |
| Model loader           | `org.coordinatekit.crf.core.tag.CrfTaggerLoader`          | `mallet` (bundled)               | overriding the bundled one |

The full feature extractor is the one your model was trained with: it drives the
tagger and the verbose "all features" view. The key feature extractor backs the
simpler "key features" view and falls back to the full extractor when you don't
register one.

Only the tag provider is required, and it stays yours to supply even with `mallet`
bundled: a tag provider defines the task's label space, so it is task-specific in a
way the model loader is not. Register one by adding a `META-INF/services` file that
names your implementation:

```
# src/main/resources/META-INF/services/org.coordinatekit.crf.core.TagProvider
com.example.MyTagProvider
```

For model-assisted suggestions, register a full feature extractor too — the one
your model was trained with. The `mallet` model loader is already bundled, so
`--model` reads MALLET models without any extra setup:

```
# src/main/resources/META-INF/services/org.coordinatekit.crf.core.feature.FullFeatureExtractor
com.example.MyFullFeatureExtractor
```

Optionally register a key feature extractor the same way to give the "key
features" view a simpler, easier-to-read feature set; without one it falls back
to the full extractor:

```
# src/main/resources/META-INF/services/org.coordinatekit.crf.core.feature.KeyFeatureExtractor
com.example.MyKeyFeatureExtractor
```

The named class must implement the marker interface it is registered under:
`FullFeatureExtractor<F>` for a full extractor, `KeyFeatureExtractor<F>` for a key
one. Implementing only the base `FeatureExtractor<F>` is not enough. `ServiceLoader`
matches on the exact interface and rejects a class that is not assignable to it with
a `ServiceConfigurationError` at load time.

With no tag provider on the classpath, the command fails fast with guidance
rather than producing garbage. If any slot has more than one registered service
it also fails, naming the conflict; leave exactly one or supply it explicitly. A
model loaded without a matching `FullFeatureExtractor` still runs, but prints a
warning — a model's suggestions are only meaningful with the extractor it was
trained on.

## Choosing a model loader

The bundled `mallet` loader reads MALLET models and is the only loader on the
classpath by default, so `--model` works with no further setup. To use a different
format, register another `CrfTaggerLoader` by dropping its jar in the
distribution's `ext/` directory. Once more than one loader is present, pick one
with `--tagger-loader <name>`, where the name is the loader's `name()` (`mallet`
for the bundled one). An unknown name fails fast and lists the names that are
available. With a single loader on the classpath the flag is unnecessary.

## Subcommands

Both subcommands resolve the same services, open an interactive terminal, and
walk you through tagging one sequence at a time. They differ in what they read:
`annotate` builds new training data from raw text, `retokenize` repairs existing
training data after a tokenizer change.

### `annotate`

Walks a plain-text input file line-by-line, presents each sequence for tagging,
and appends accepted sequences to an XML training-data file. With `--model`, a
trained model pre-tags each sequence and highlights low-confidence tokens so you
correct rather than tag from scratch. Re-running against the same output file
resumes where you left off.

```bash
crf annotate --input lines.txt --output labeled.xml
crf annotate --input lines.txt --output labeled.xml --model pos.crf
```

| Flag                | Required | Default  | Notes                                                                           |
| ------------------- | -------- | -------- | ------------------------------------------------------------------------------- |
| `--input`, `-i`     | yes      | —        | Plain-text UTF-8 input file, one sequence per line.                             |
| `--output`, `-o`    | yes      | —        | XML output; created or appended. Flushed after every acceptance.                |
| `--model`, `-m`     | no       | none     | Path to a serialized model. Without it, the annotator runs without suggestions. |
| `--tagger-loader`   | no       | `mallet` | Model loader to select when more than one is on the classpath.                  |
| `--threshold`, `-t` | no       | `0.80`   | Confidence below which tokens are highlighted. Must be in `[0.0, 1.0]`.         |
| `--help`, `-h`      | —        | —        | Print the usage banner and exit.                                                |
| `--version`, `-V`   | —        | —        | Print the version and exit.                                                     |

See [`annotator/README.md`](../annotator/README.md) for the per-sequence key
bindings, the resume behavior, and the rule about keeping your tokenizer stable
across sessions.

### `retokenize`

Walks an existing XML training-data file, re-tokenizes each sequence with the
currently registered tokenizer, and re-tags only the sequences whose tokenization
changed — the rest pass through untouched. Use it after you change tokenizers so
old training data stays aligned with how new input is split. The output file must
be absent or empty: the command writes a fresh corrected file rather than editing
in place.

```bash
crf retokenize --input labeled.xml --output retokenized.xml
crf retokenize --input labeled.xml --output retokenized.xml --model pos.crf
```

| Flag                | Required | Default  | Notes                                                                        |
| ------------------- | -------- | -------- | ---------------------------------------------------------------------------- |
| `--input`, `-i`     | yes      | —        | XML training-data file to review.                                            |
| `--output`, `-o`    | yes      | —        | XML output; must be absent or empty.                                         |
| `--model`, `-m`     | no       | none     | Path to a serialized model. Without it, re-tagging runs without suggestions. |
| `--tagger-loader`   | no       | `mallet` | Model loader to select when more than one is on the classpath.               |
| `--threshold`, `-t` | no       | `0.80`   | Confidence below which tokens are highlighted. Must be in `[0.0, 1.0]`.      |
| `--help`, `-h`      | —        | —        | Print the usage banner and exit.                                             |
| `--version`, `-V`   | —        | —        | Print the version and exit.                                                  |

The interactive prompt is the same one `annotate` uses; run `crf retokenize
--help` for the authoritative flag list.

## Exit codes

Both subcommands share one contract. The root `crf` command with no subcommand
prints usage and exits `2`.

| Code | Meaning                                                                                                                                                                                                                                                                    |
| ---- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `0`  | The run completed (or `--help` / `--version` was requested).                                                                                                                                                                                                               |
| `1`  | Startup or run failure: services could not be resolved, the terminal could not be opened, a supplied model could not be read, a precondition failed (such as no interactive terminal, or a `retokenize` output file that is not absent or empty), or the run itself threw. |
| `2`  | Picocli rejected the arguments: an unknown or missing subcommand, a missing required flag, an invalid threshold, or an unknown option.                                                                                                                                     |
