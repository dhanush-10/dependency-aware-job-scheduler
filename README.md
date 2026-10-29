# Dependency-Aware Job Scheduler

A command-line program that reads tasks (duration, priority, dependencies) from a CSV file and
simulates running them on `W` workers. It prints the resulting schedule as one JSON document. It is
a pure simulation: no task is executed and no real time passes.

Written in Java 21 with no runtime dependencies. JUnit 5 is used for tests only. The program has no
AI/LLM integration, API keys or network access.

## Prerequisites

- JDK 21 or newer (`javac -version`).
- Either Maven 3.9+ **or** a JUnit Platform Console jar (see "Running the tests").

## Build and run

Plain `javac` (this is the path I verified; see "Verification"):

```sh
mkdir -p out/classes
javac -d out/classes src/main/java/com/complyance/scheduler/*.java
java -cp out/classes com.complyance.scheduler.Main --input inputs/basic.csv --workers 2
```

With Maven (the `pom.xml` is standard, but I could **not** run Maven in my sandbox; see "Verification"):

```sh
mvn -B package
java -jar target/scheduler.jar --input inputs/basic.csv --workers 2
```

Arguments: `--input PATH` (CSV file) and `--workers W` (integer 1 to 32), in either order. Standard
output is exactly one JSON document; shell redirection saves it (`... > result.json`). A one-line
diagnostic also goes to standard error when something fails.

### Input format

```csv
task_id,duration,priority,dependencies
A,3,2,
B,2,1,A
C,4,1,
D,1,2,"B,C"
```

- Header must be exactly `task_id,duration,priority,dependencies`.
- `task_id`: `[A-Za-z][A-Za-z0-9_-]{0,31}`, unique and case-sensitive.
- `duration`: integer 1 to 1,000,000. `priority`: integer 0 to 1,000, **smaller runs first**.
- `dependencies`: empty or comma-separated task IDs (quote the field). Forward references are fine.
- UTF-8 with optional BOM, LF or CRLF. Outer whitespace around fields is trimmed. Blank lines are
  ignored. At most 2,000 tasks. A header-only file is a valid empty task set.

### Output

Success (exit 0):

```json
{
  "status": "ok",
  "workers": 2,
  "makespan": 6,
  "schedule": [
    { "task_id": "C", "worker_id": 1, "start": 0, "end": 4 },
    { "task_id": "A", "worker_id": 2, "start": 0, "end": 3 },
    { "task_id": "B", "worker_id": 2, "start": 3, "end": 5 },
    { "task_id": "D", "worker_id": 1, "start": 5, "end": 6 }
  ]
}
```

Error (nonzero exit, never a schedule): `{"status":"error","errors":[{"code":...,"message":...}]}`
with one of `INVALID_ARGUMENT`, `READ_ERROR`, `INVALID_CSV`, `INVALID_TASK`, `DUPLICATE_TASK`,
`INVALID_DEPENDENCY`, `CYCLE_DETECTED`. A cycle error also has `"cycle": ["A","B","C","A"]`.
Exit code is 2 for `INVALID_ARGUMENT` and 1 for every other error.

```sh
$ java -cp out/classes com.complyance.scheduler.Main --input inputs/cycle.csv --workers 2
{ "status": "error", "errors": [ { "code": "CYCLE_DETECTED",
  "message": "Dependency cycle A -> B -> C -> A (each task depends on the next)",
  "cycle": ["A", "B", "C", "A"] } ] }          # exit code 1
```

## Running the tests

Maven:

```sh
mvn -B test
```

Without Maven, using a JUnit Platform Console Launcher jar (on Ubuntu/Debian, `apt install junit5`
provides `/usr/share/java/junit-platform-console-standalone.jar`):

```sh
JUNIT_CONSOLE_JAR=/usr/share/java/junit-platform-console-standalone.jar scripts/test-without-maven.sh
```

Tests must be run from the repository root because the CLI tests read `inputs/` and `expected/`.

## What the tests cover (88 tests)

| File | Verifies |
| --- | --- |
| `JobSchedulerTest` | Priority order, ASCII ID ties (`J10` < `J2`, `Z` < `a`), input-order independence, multiple dependencies, simultaneous completions, newly-ready tasks merged with waiting ones, lowest idle worker (including one idle since an earlier time), idle gaps, no waiting for blocked tasks, empty input, 32 workers, long-arithmetic and no-sleep checks (2,000 x 1,000,000-unit chain finishes within a 5 s timeout). |
| `ReferenceSimulationTest` | 1,500 seeded random DAGs compared with an independent tick-by-tick reference simulator that shares no code with the scheduler. |
| `TaskLoaderTest` | Header rules, BOM/CRLF/blank lines/trimming, column counts, ID/duration/priority bounds and leading zeroes, 2,000-task limit, duplicates, unknown/self/repeated/empty/malformed dependencies, cycles (with unrelated tasks, 2-cycles, long cycles, long chains, diamonds), invalid UTF-8, unreadable files. |
| `CsvParserTest` | Quoting, escaped quotes, line breaks in quotes, LF/CRLF, malformed CSV. |
| `CliTest` | The seven published samples against `expected/*.json` (structural comparison), exit codes, one-JSON-document stdout, no `schedule` on errors, 15 invalid argument combinations, worker bounds, read errors. |
| `JsonWriterTest` | JSON escaping round trip, ASCII-only output, `cycle` only on cycle errors. |

## Verification (actual results)

Environment: Ubuntu 24.04, OpenJDK 21.0.10, JUnit Platform Console 1.9.1 with Jupiter 5.10.1.

```text
$ JUNIT_CONSOLE_JAR=/usr/share/java/junit-platform-console-standalone.jar scripts/test-without-maven.sh
Test run finished after 1064 ms
[        88 tests found           ]
[         0 tests skipped         ]
[        88 tests successful      ]
[         0 tests failed          ]
```

Compilation with `javac -Xlint:all` produced no warnings. All seven published samples produce the
expected results through the real CLI (also covered by `CliTest`).

I also ran a manual mutation check: temporarily breaking the scheduler in five ways (highest worker
first, reversed priority, reversed ID tie-break, finishing only one completion per instant, a
dependency counting as satisfied after any one finishes) made 12, 7, 8, 3 and 7 tests fail respectively.

**Not verified:** `mvn test` / `mvn package` and the Maven-built jar. My sandbox could not reach
Maven Central, so the `pom.xml` has not been executed. It uses stock Maven plugins and the
`junit-jupiter` 5.10.2 aggregate artifact.

## Design

- `CsvParser` reads RFC 4180 style records. `TaskLoader` validates fields and cross-references and
  returns immutable `Task` records, so nothing is scheduled until the whole file is valid.
- `CycleDetector` is an iterative DFS (no recursion, so a 2,000-task chain cannot overflow the
  stack). Tasks and dependencies are visited in ASCII order, so the reported cycle is deterministic.
- `JobScheduler` is an event-driven simulation. Data structures: a `PriorityQueue` of ready tasks
  (priority, then ASCII ID), a `TreeSet` of idle worker IDs (lowest first), a `PriorityQueue` of
  running tasks keyed by end time, a map of remaining-dependency counts and a dependents adjacency
  map. At each instant it finishes all tasks ending then, releases dependents, then assigns the whole
  ready set to idle workers in order. Time jumps straight to the next completion.
- Complexity for `n` tasks and `e` dependency edges: O(n log n + e) scheduling, O(n + e log e)
  validation including cycle detection, O(n + e) memory. Running time does not depend on durations.
- Times are `long`: 2,000 tasks of 1,000,000 units can total 2,000,000,000, close to the `int` limit.
- Only the first error found is reported. Check order: arguments, readability and encoding, CSV
  structure, per-row task fields and duplicates, dependencies, then cycles.

### Interpretation choices

- A whitespace-only line is ignored, but `,,,` is an invalid task (`INVALID_TASK`).
- Spaces or tabs between a closing quote and the next comma are tolerated; any other text there, a
  quote inside an unquoted field, or a bare `\r` is `INVALID_CSV`.
- Dependency IDs are case-sensitive, like task IDs.
- Error messages only echo validated task IDs, the input path and sanitised header text; all JSON output is escaped to ASCII.

## Known limitations

- Reports one error per run rather than all of them.
- The CSV reader is hand-written for this narrow format, not a general-purpose library.
- Maven build is unverified (see above).

## AI assistance disclosure

An AI coding assistant (Claude, by Anthropic) was used for this assignment: reading the
specification, designing the structure, writing the Java source, tests and this documentation, and
suggesting edge cases. The code was not written entirely by hand. What was independently checked:
all tests were compiled and executed (results above), published samples were run through the real
CLI, and the mutation check above confirmed the tests detect policy violations. The submitted
program has no AI integration.
