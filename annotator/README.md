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

## Running it

The annotator runs through the `crf annotate` command in the `cli` module. There
is no `main` to write: `crf` discovers your components through `ServiceLoader` and
assembles the annotator for you. See [`cli/README.md`](../cli/README.md) for how
to depend on the module, register your components as services, and the full flag
and exit-code reference.

The annotator module itself (`Annotator`, `AnnotatorRunner`,
`AnnotatorConfiguration`) stays a plain library, so you can still embed the flow
directly if the `crf` command doesn't fit.

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
