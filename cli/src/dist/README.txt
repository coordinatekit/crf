# crf

The `crf` command-line tool: an interactive front end for building and
maintaining CRF training data. This archive is a self-contained distribution,
and the start scripts under `bin/` launch it.

## Requirements

Java 21 or later. The start scripts locate it through `JAVA_HOME`, falling back
to `java` on the `PATH`.

## Running

Run `bin/crf` on Linux and macOS, or `bin\crf.bat` on Windows:

```
bin/crf --help
bin/crf --version
```

With no subcommand, `crf` prints usage and exits `2`. Two subcommands do the
work.

`annotate` walks a plain-text input file line by line, tags each line through an
interactive prompt, and appends accepted sequences to an XML training-data file:

```
bin/crf annotate --input addresses.txt --output training.xml
```

`retokenize` walks an existing XML training-data file, re-tokenizes each
sequence, re-tags the ones that no longer line up, and writes a corrected file:

```
bin/crf retokenize --input training.xml --output retokenized.xml
```

Pass `--model <path>` to either command for tag suggestions from a trained
model; without it they run unaided. Run `bin/crf annotate --help` for the full
flag list.

## Registering your components

The tool ships with no domain logic of its own. Before it can tag anything you
register your implementations as `java.util.ServiceLoader` providers, at minimum
a `TagProvider`, plus the bundled `mallet` provider when you use `--model`. Drop
the provider JARs into `ext/` and `bin/crf` adds them to the classpath; see
`ext/README.txt`. With none registered, a run fails fast with guidance rather
than producing garbage.

## JVM options

Set `CRF_OPTS` to pass options to the JVM, for example
`CRF_OPTS=-Xmx2g bin/crf ...` to give a large model more heap.
```
