---
title: Annotating Training Data
---

`crf annotate` builds CRF training data by hand. It walks a plain-text file one
line at a time, shows each line in an interactive terminal, and writes the
sequences you tag to an XML training file. Where Getting Started trains and runs
models in code, this is the human-in-the-loop step that produces the labeled data
those models learn from.

The point is to let a model help you tag, not to tag in your place. It fills in a
best guess for each token so you correct a few rather than label every one from
scratch. You stay the source of the labels: a model cannot build good training
data from its own guesses. So you train often, especially early, when a small
batch of fresh labels still noticeably improves the suggestions: tag a first batch
by hand, train a model on it, then annotate the next batch with that model
proposing tags for you to correct. This guide follows that loop with the same
part-of-speech example as Getting Started.

## Before you start

`crf` is a thin front end. It ships no tag set, feature extractor, or model of its own; it
discovers those components on its classpath through Java's `ServiceLoader`. It
must already be installed and on your `PATH`. How it is packaged for your project
(a launcher script, a single jar) is a build concern the `cli` module's README
covers.

`crf` fills four slots, each from the one service registered for it, or from a
built-in default where there is one:

| Slot              | Service interface                                     | Built-in default      | You register                    |
| ----------------- | ----------------------------------------------------- | --------------------- | ------------------------------- |
| Tag provider      | `org.coordinatekit.crf.core.TagProvider`              | none                  | yes, required                   |
| Tokenizer         | `org.coordinatekit.crf.core.preprocessing.Tokenizer`  | `WhitespaceTokenizer` | only to override the default    |
| Feature extractor | `org.coordinatekit.crf.core.feature.FeatureExtractor` | none                  | for model suggestions           |
| Model loader      | `org.coordinatekit.crf.core.tag.CrfTaggerLoader`      | none                  | the `mallet` module provides it |

Only the tag provider is required. Without one, `crf` has no label set and will
not run. It defines the parts of speech and the starting tag, and the running
example uses the same provider Getting Started trains against:

```java
package com.example.pos;

import java.util.Set;
import org.coordinatekit.crf.core.StringTagProvider;

public final class PartOfSpeechTagProvider extends StringTagProvider {
    public PartOfSpeechTagProvider() {
        super(
            Set.of("Adjective", "Determiner", "Noun", "Preposition", "Unknown", "Verb"),
            "Unknown");
    }
}
```

Register it by naming the class in a `META-INF/services` file:

```
# src/main/resources/META-INF/services/org.coordinatekit.crf.core.TagProvider
com.example.pos.PartOfSpeechTagProvider
```

The tokenizer slot falls back to `WhitespaceTokenizer`, which splits on
whitespace. That suits the space-separated input used here, so you do not need to
register one. The feature extractor and model loader matter only once you bring in
a model, in [Tagging with a model](#tagging-with-a-model); leave them until then.

To check `crf` is installed and on your `PATH`, print the usage:

```bash
crf annotate --help
```

The component wiring is checked when you run the command for real, not on
`--help`. If no tag provider is registered, `crf annotate` stops before the
terminal opens and tells you to register one rather than running without a label
set. If more than one tag provider is on the classpath it also stops and names
the conflict, so exactly one defines your labels. Once a single tag provider is
registered, you are ready to annotate.

## A first pass: tagging from scratch

The first time through you have no model, so `crf` cannot suggest anything. Every
token starts on the tag provider's starting tag, `Unknown` in this example, and
you set the rest by hand. This is slower than correcting suggestions, but it is how
you produce the first batch of training data, the data your first model trains
on.

Start with a plain-text file, one sequence per line:

```text
A cat sat on the mat
The dog chased a ball
The quick brown fox jumps over the lazy dog
The baker sells fresh bread
```

Run `annotate` with that file as the input and a path for the training XML as the
output:

```bash
crf annotate --input lines.txt --output pos-training.xml
```

`crf` opens an interactive screen on the first line:

```
Sequence 1 of 4: A cat sat on the mat

##  Token  Tag      Confidence
--  -----  -------  ----------
1   A      Unknown  —
2   cat    Unknown  —
3   sat    Unknown  —
4   on     Unknown  —
5   the    Unknown  —
6   mat    Unknown  —

Enter A to accept, the number to edit the token, S to skip, U to undo, or X to exit.
```

Each row is one token. `Tag` is the tag currently assigned. Here it reads
`Unknown` on every row, because nothing has tagged the tokens yet. `Confidence`
is the model's confidence in that tag; with no model it shows `—`. The footer
lists the keys the screen accepts. Type a single key, or a token number, and
press enter.

To correct a token, type its number. Token 1 is `A`, a determiner, so type `1`:

```
Sequence 1 of 4: A cat sat on the mat
Token 1 of 6: A

##  Tag          Confidence
--  -----------  ----------
1   Adjective    —
2   Determiner   —
3   Noun         —
4   Preposition  —
5   Unknown      —
6   Verb         —

Enter the number to select the correct tag or C to cancel.
```

The edit screen lists every tag your tag provider defines. Type the number of the
right one, `2` for `Determiner`, and you return to the sequence screen with token
1 updated. `C` cancels and changes nothing.

Work through the rest the same way. `U` undoes your last tag change if you pick
the wrong one, and `S` skips the whole line, leaving it for a later run. When
every token is right, the screen reads:

```
##  Token  Tag          Confidence
--  -----  -----------  ----------
1   A      Determiner   —
2   cat    Noun         —
3   sat    Verb         —
4   on     Preposition  —
5   the    Determiner   —
6   mat    Noun         —
```

Make sure no token is left on `Unknown` before you accept: `crf` writes whatever
tag each token carries, so a stray `Unknown` becomes a mislabeled token in your
training data. Press `A` to accept. `crf` appends the sequence and moves to the
next line. Each acceptance is written to disk right away, so accepted work is never
held in memory. The closing `</crf:Collection>` tag is added when the file is
closed on exit, which is when the document becomes complete.

You do not have to label the whole file in one sitting. Press `X` to stop. `crf`
closes the file, writes that final tag, and your accepted sequences are saved:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<crf:Collection xmlns:crf="https://coordinatekit.org/crf/schema">
<crf:Sequence><Determiner>A</Determiner><crf:Excluded> </crf:Excluded><Noun>cat</Noun><crf:Excluded> </crf:Excluded><Verb>sat</Verb><crf:Excluded> </crf:Excluded><Preposition>on</Preposition><crf:Excluded> </crf:Excluded><Determiner>the</Determiner><crf:Excluded> </crf:Excluded><Noun>mat</Noun></crf:Sequence>
</crf:Collection>
```

Each element name is a tag and its text is the token. The `<crf:Excluded>` runs
hold the whitespace between tokens so the original line can be reconstructed
exactly. This is the same XML format Getting Started feeds to the trainer.

Re-running against the same output file continues from where you stopped.

## Resuming across sessions

When you re-run `annotate` against an output file that already holds sequences,
`crf` walks the input from the top and skips any line already written there. Run
the command from the first pass again:

```bash
crf annotate --input lines.txt --output pos-training.xml
```

At startup it reports how many lines it skipped, then presents the first line that
is not yet in the output:

```
Resumed: skipped 1 of 4 input lines already present in output.
```

`A cat sat on the mat` was accepted in the first pass, so it is in
`pos-training.xml` and `crf` passes over it, presenting `The dog chased a ball` as
`Sequence 2 of 4`. Tag it and accept.

What comes back is decided by the output, not by where you stopped. Only accepted
lines are written, so only those are skipped. A line you ended with `S` was never
written and is presented again on the next run, ahead of the lines you had not yet
reached. Across sessions the output grows, and no accepted line is presented twice.

The skip compares tokens, not raw text. `crf` tokenizes each input line and matches
the token list against the sequences already written, by a content fingerprint.
Keep the tokenizer fixed for the life of an output file. If you change tokenizers
between sessions, a line accepted earlier may tokenize differently, no longer match
what is on disk, and be presented again. A skip count lower than you expect is the
sign that tokenization changed.

Resuming looks only at the output file. It does not depend on a model, and a model
does not depend on it. They still combine naturally: once you have tagged enough to
train a model, you resume with it, and the model pre-tags the lines you have not
reached yet.

## Tagging with a model

Once you have tagged a batch by hand, train a model from it and let that model
take the first pass at what remains. Training is covered in Getting Started: it
reads your `pos-training.xml` and writes a serialized model, `pos-model.crf`. With
that file, annotation turns from data entry into review.

Loading a model needs the `mallet` module on the classpath, which provides the
model loader that reads `pos-model.crf`. Register the feature extractor you trained
with as well, so the model sees the same features it learned on:

```
# src/main/resources/META-INF/services/org.coordinatekit.crf.core.feature.FeatureExtractor
com.example.pos.PartOfSpeechFeatureExtractor
```

Skip the feature extractor and `crf` still runs, but its suggestions will not match
the model and are not worth trusting.

Then point `annotate` at the model with `--model`:

```bash
crf annotate --input lines.txt --output pos-training.xml --model pos-model.crf
```

Resume still applies. The lines you have already tagged are skipped, and `crf`
opens on the next one, now pre-tagged by the model:

```
Sequence 3 of 4: The quick brown fox jumps over the lazy dog

##  Token  Tag          Confidence
--  -----  -----------  ----------
1   The    Determiner   0.9900
2   quick  Adjective    0.5500
3   brown  Adjective    0.9200
4   fox    Noun         0.9700
5   jumps  Verb         0.9500
6   over   Preposition  0.8800
7   the    Determiner   0.9900
8   lazy   Adjective    0.6200
9   dog    Noun         0.9500

Enter A to accept, the number to edit the token, S to skip, U to undo, or X to exit.
```

Every token carries the model's tag and its confidence, so the `Confidence`
column is no longer `—`. Rows whose confidence falls below the threshold are shown
in bold yellow, here `quick` at `0.5500` and `lazy` at `0.6200`, to draw your eye
to the tags the model was least sure of. The threshold defaults to `0.80`; set `--threshold` to raise or lower it. It only
changes which rows are highlighted, not the tags the model assigns.

Read the whole sequence before accepting, because a high-confidence tag can still
be wrong. The highlights flag the model's shakiest guesses, so give `quick` and
`lazy` the closest look. Both are right here, so press `A` to accept. When a tag is
wrong, highlighted or not, fix it with the same number-then-edit step from the
first pass, then accept. Correcting the model's pass is still far less work than
tagging nine tokens from scratch.

The model and resume stay independent. A model can pre-tag the very first run when
you already have one, and resuming works with no model at all.

Retrain early and often. A model helps only as much as it has learned, so in the
early rounds retrain after each small batch rather than waiting for a large one.
Frequent retraining also shows where the model is weak: tags it keeps getting
wrong, or keeps flagging at low confidence, point to parts of the label set that
need more examples. Over the rounds the loop tightens, and you correct less and
accept more.

## When things go wrong

Adding a model is where most problems start. Before that, the one thing to know is
that the annotator needs an interactive terminal: launch it under CI, `nohup`, or
with input piped in, and it stops before it starts rather than running blind.

`--model` brings two requirements. Its model loader comes from the `mallet`
module, so passing `--model` without `mallet` on the classpath stops `crf` before
the terminal opens, with a message to add it. A model also expects the feature
extractor it was trained with: load it without one and `crf` still runs but prints
a warning, and its suggestions are not trustworthy.

For exit codes and the full flag reference, see the `cli` module's README.

## Next steps

- [Getting Started](getting-started) trains a model from the data you annotate and
  tags new text in code.
- The `cli` module's README gives the full flag and exit-code reference for the
  `annotate` command.
- When you change tokenizers, `crf retokenize` repairs existing training data so it
  stays aligned with how new input is split. It uses the same tagging screen as
  `annotate`.
