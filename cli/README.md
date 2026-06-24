# CLI

The `crf` command-line tool: a [picocli](https://picocli.info) front end hosting
the interactive `annotate` and `retokenize` flows. It ships no components of its
own — it discovers your tag provider, tokenizer, feature extractor, and model
loader through `ServiceLoader` and assembles the tool around them, so there is no
`main` to write. Register your components as services and put this module on the
classpath; the `crf` command then exposes them through its `annotate` and
`retokenize` subcommands.

## Depending on it

Depend on `cli`, plus `mallet` if you want model-assisted suggestions (it
provides the model loader that reads MALLET models):

```groovy
dependencies {
    implementation "org.coordinatekit.crf:cli:0.1.0"
    // Optional, only for model-assisted suggestions:
    implementation "org.coordinatekit.crf:mallet:0.1.0"
}
```

```xml
<dependency>
    <groupId>org.coordinatekit.crf</groupId>
    <artifactId>cli</artifactId>
    <version>0.1.0</version>
</dependency>
```

The underlying `annotator` module stays a plain library, so you can embed the
`annotate` and `retokenize` flows directly if the `crf` command doesn't fit. This
module is only the command-line wiring.

## Registering your components

`crf` resolves four slots through `ServiceLoader`, each as
`explicit > a single registered service > a built-in default`:

| Slot              | Service interface                                           | Default                      | Needed when                |
| ----------------- | ----------------------------------------------------------- | ---------------------------- | -------------------------- |
| Tag provider      | `org.coordinatekit.crf.core.TagProvider`                    | none (required)              | always                     |
| Tokenizer         | `org.coordinatekit.crf.core.preprocessing.Tokenizer`        | `WhitespaceTokenizer`        | always                     |
| Feature extractor | `org.coordinatekit.crf.core.preprocessing.FeatureExtractor` | none (tags without features) | recommended with `--model` |
| Model loader      | `org.coordinatekit.crf.core.tag.CrfTaggerLoader`            | none                         | `--model`                  |

Only the tag provider is required. Register one by adding a `META-INF/services`
file that names your implementation:

```
# src/main/resources/META-INF/services/org.coordinatekit.crf.core.TagProvider
com.example.MyTagProvider
```

For model-assisted suggestions, register a feature extractor too and keep the
`mallet` module on the classpath — it registers the `CrfTaggerLoader` that reads
MALLET models:

```
# src/main/resources/META-INF/services/org.coordinatekit.crf.core.preprocessing.FeatureExtractor
com.example.MyFeatureExtractor
```

With no tag provider on the classpath, the command fails fast with guidance
rather than producing garbage. If any slot has more than one registered service
it also fails, naming the conflict; leave exactly one or supply it explicitly.
Passing `--model` without a registered model loader is the same kind of failure:
it stops before the terminal opens and tells you to add `mallet`. A model loaded
without a matching feature extractor still runs, but prints a warning — a model's
suggestions are only meaningful with the extractor it was trained on.

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

| Flag                | Required | Default | Notes                                                                           |
| ------------------- | -------- | ------- | ------------------------------------------------------------------------------- |
| `--input`, `-i`     | yes      | —       | Plain-text UTF-8 input file, one sequence per line.                             |
| `--output`, `-o`    | yes      | —       | XML output; created or appended. Flushed after every acceptance.                |
| `--model`, `-m`     | no       | none    | Path to a serialized model. Without it, the annotator runs without suggestions. |
| `--threshold`, `-t` | no       | `0.80`  | Confidence below which tokens are highlighted. Must be in `[0.0, 1.0]`.         |
| `--help`, `-h`      | —        | —       | Print the usage banner and exit.                                                |
| `--version`, `-V`   | —        | —       | Print the version and exit.                                                     |

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

| Flag                | Required | Default | Notes                                                                        |
| ------------------- | -------- | ------- | ---------------------------------------------------------------------------- |
| `--input`, `-i`     | yes      | —       | XML training-data file to review.                                            |
| `--output`, `-o`    | yes      | —       | XML output; must be absent or empty.                                         |
| `--model`, `-m`     | no       | none    | Path to a serialized model. Without it, re-tagging runs without suggestions. |
| `--threshold`, `-t` | no       | `0.80`  | Confidence below which tokens are highlighted. Must be in `[0.0, 1.0]`.      |
| `--help`, `-h`      | —        | —       | Print the usage banner and exit.                                             |
| `--version`, `-V`   | —        | —       | Print the version and exit.                                                  |

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
