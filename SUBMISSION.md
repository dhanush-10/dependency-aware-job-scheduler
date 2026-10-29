# Assignment 1 — submission checklist

## Running the work

- Language/runtime and version: Java 21 (verified with OpenJDK 21.0.10). No runtime dependencies.
- Dependency installation command, if needed: none for the program. Tests need JUnit 5, which Maven
  downloads automatically (`mvn -B test`); alternatively use `scripts/test-without-maven.sh` with a
  JUnit Platform Console jar.
- Exact scheduler command with `--input` and `--workers`:
  `mkdir -p out/classes && javac -d out/classes src/main/java/com/complyance/scheduler/*.java && java -cp out/classes com.complyance.scheduler.Main --input inputs/basic.csv --workers 2`
- Exact test command: `mvn -B test` (not executed in my sandbox, see below), or
  `JUNIT_CONSOLE_JAR=/usr/share/java/junit-platform-console-standalone.jar scripts/test-without-maven.sh`
- Actual test results: 88 tests found, 88 successful, 0 failed, 0 skipped, run time about 1.06 s,
  using `scripts/test-without-maven.sh` (JUnit Platform Console 1.9.1, Jupiter 5.10.1, OpenJDK 21.0.10,
  Ubuntu 24.04). All seven published samples match `expected/*.json` through the real CLI.
  `mvn test` and the Maven-built jar were **not** run because the sandbox had no access to Maven Central.
- Data structures and approximate time/space complexity: priority queue of ready tasks (priority,
  then ASCII ID), tree set of idle worker IDs, priority queue of running tasks by end time, hash maps
  for remaining-dependency counts and dependents. Scheduling is O(n log n + e) time for n tasks and e
  dependency edges, independent of task durations (virtual time jumps between completions). Validation
  with iterative DFS cycle detection is O(n + e log e). Memory is O(n + e).

## Scope and assistance

- Known limitations or unfinished work: only the first error is reported; hand-written CSV reader for
  the specified format; Maven build unverified (see above).
- Documentation or libraries used: the supplied specification; JDK standard library; JUnit 5 for tests
  only. No CSV or JSON library.
- AI assistance used (or "none"), what it helped with and how you verified it: Claude (Anthropic) helped
  read the specification, design the solution, write the code, tests and documentation, and suggest
  edge cases. Verification: compiled with `javac -Xlint:all` (no warnings), ran 88 automated tests
  (all passing), ran the published samples through the CLI, compared the scheduler with an
  independent tick-by-tick reference simulator on 1,500 seeded random graphs, and confirmed with five
  deliberate code mutations that the tests fail when the policy is broken.

## Before submitting

- [x] The repository includes implementation, executable tests and reproducible instructions.
- [x] Standard output is one JSON document; errors exit nonzero without a partial schedule.
- [x] The program runs locally without AI/LLM services, API keys or paid accounts.
- [x] No credentials, customer data, dependency folders or build output are included.
- [ ] The repository is public and final work is on the default branch.
- [ ] I will leave submitted work unchanged; no corrections or resubmissions are accepted.
