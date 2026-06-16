# Annotator

A terminal-based interactive annotator that walks a user through tagging
sequences for CRF training. Reads plain-text sequences line-by-line, presents
each one in a JLine-rendered table, accepts per-token edits, and appends
confirmed sequences to an XML training-data file that is flushed on every
acceptance — so a crash leaves a valid document with every confirmed sequence
intact.

## When to use this

- You have raw text (one sequence per line) and need to build the `<crf:Sequence>`
  training XML the rest of the library consumes.
- You have a preliminary model and want to tag-and-correct rather than tag
  from scratch.
- You want resumable work across multiple sessions — re-running against the
  same output file skips any input line whose tokenization already appears in
  the output.

If you only need to _programmatically_ generate training data, depend on
`core` directly and call `XmlTrainingData.writer(...)` or
`XmlTrainingData.appendingWriter(...)`. The annotator is the human-in-the-loop
layer on top.

## Wiring

The annotator module depends only on `core` and JLine. To use it with a
MALLET-backed tagger, depend on `mallet` too and construct the
`MalletCrfTagger` in your `main`.

```groovy
dependencies {
    implementation "org.coordinatekit.crf:annotator:0.1.0"
    // Optional, only if you want model-assisted suggestions:
    implementation "org.coordinatekit.crf:mallet:0.1.0"
}
```

## Sample `main`

Write a small entry point that wires your tag provider, tokenizer, optional
tagger, and feature extractor through `AnnotatorCli.run(...)`:

```java
public final class MyAnnotator {
    public static void main(String[] arguments) {
        int exitCode = AnnotatorCli.run(arguments, (options, terminal) -> {
            FeatureExtractor<MyFeature> featureExtractor = new MyFeatureExtractor();

            CrfTagger<MyFeature, MyTag> tagger = options.model() == null
                    ? null
                    : new MalletCrfTagger<>(
                            featureExtractor,
                            options.model(),
                            new MyTagProvider(),
                            new WhitespaceTokenizer()
                    );

            TerminalTaggingInterface<MyFeature, MyTag> ui =
                    TerminalTaggingInterface.<MyFeature, MyTag>builder()
                            .tagProvider(new MyTagProvider())
                            .terminal(terminal)
                            .threshold(options.threshold())
                            .build();

            return Annotator.<MyFeature, MyTag>builder()
                    .tagProvider(new MyTagProvider())
                    .tokenizer(new WhitespaceTokenizer())
                    .tagger(tagger)
                    .taggingInterface(ui)
                    .terminal(terminal)
                    .build();
        });
        System.exit(exitCode);
    }
}
```

## Flags

| Flag                | Required | Default | Notes                                                                                      |
| ------------------- | -------- | ------- | ------------------------------------------------------------------------------------------ |
| `--input`, `-i`     | yes      | —       | Plain-text UTF-8 input file, one sequence per line.                                        |
| `--output`, `-o`    | yes      | —       | XML output; created or appended. Flushed after every acceptance.                           |
| `--model`, `-m`     | no       | none    | Path to a serialized model. Your factory decides how to materialize a `CrfTagger` from it. |
| `--threshold`, `-t` | no       | `0.80`  | Confidence below which tokens are highlighted (bold + yellow). Must be in `[0.0, 1.0]`.    |
| `-h`, `--help`      | —        | —       | Print the usage banner and exit.                                                           |

## Per-sequence actions

The sequence screen accepts a single key plus `<enter>`:

- `A` — accept the current tagging and write the sequence.
- `<number>` — edit the tag for that token (1-based).
- `S` — skip; the line is re-presented next run.
- `U` — undo the last per-token edit.
- `X` — exit; previously accepted sequences are preserved.

The edit screen accepts:

- `<number>` — select that tag.
- `C` — cancel and return to the sequence screen.

## Resume behavior

Re-running against the same output file skips any input line whose
tokenization already appears in the output, compared by content fingerprint of
the token list. A `Resumed: skipped K of N input lines already present in output.`
message is printed at session start (or after the input is exhausted if no
sequence remained to present).

**Pick a tokenizer up front and keep it stable for the lifetime of an output
file.** If you change tokenizers between sessions, previously-tagged sequences
may no longer match incoming input and will be re-presented. A surprisingly
low skip count after switching tokenizers is the cue that something changed.

## Exit codes

| Code | Meaning                                                                                                                          |
| ---- | -------------------------------------------------------------------------------------------------------------------------------- |
| `0`  | Annotation completed (or `--help` was requested).                                                                                |
| `1`  | Interactive-terminal precondition failed, the terminal could not be opened, or `Annotator.annotate(...)` threw an `IOException`. |
| `2`  | Picocli rejected the arguments (missing required flag, invalid threshold, unknown option).                                       |
