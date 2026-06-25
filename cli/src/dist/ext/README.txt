# ext

Drop service-provider JARs here to extend `crf` at runtime.

Any `.jar` in this directory is added to the classpath by `bin/crf`
(and `bin/crf.bat`) and discovered via `java.util.ServiceLoader`. Use one
provider JAR per service interface; a dropped-in provider overrides the
built-in default. Bundled dependencies in `lib/` are not affected.

`ext/` only takes effect through the generated start scripts: the installed
dist (`installDist`) and the unpacked archive. `./gradlew run` uses the project
runtime classpath and ignores `ext/`, so don't test drop-ins via `run`.
