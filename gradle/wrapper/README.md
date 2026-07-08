# Gradle wrapper

`gradle-wrapper.jar` is a binary and is intentionally **not** committed by the scaffold
generator. Materialize it once (committed thereafter) with a local Gradle 8.10.2:

```bash
gradle wrapper --gradle-version 8.10.2 --distribution-type bin
git add gradle/wrapper/gradle-wrapper.jar
```

CI self-heals: the workflow runs `gradle wrapper` when the jar is absent, so pipelines
pass before the jar is first committed (see `.github/workflows/ci.yml`). Once committed,
`gradle/wrapper-validation` guards its checksum against the official distribution.
