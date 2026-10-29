package com.complyance.scheduler;

import java.io.PrintStream;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

/** Command-line entry point: {@code --input PATH --workers W}. */
public final class Main {
    static final int MIN_WORKERS = 1;
    static final int MAX_WORKERS = 32;
    private static final int EXIT_OK = 0;
    private static final int EXIT_ERROR = 1;
    private static final int EXIT_USAGE = 2;
    private static final Pattern UNSIGNED_DECIMAL = Pattern.compile("[0-9]{1,9}");

    private Main() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    /** Runs the CLI and returns the process exit code. Exactly one JSON document goes to {@code out}. */
    static int run(String[] args, PrintStream out, PrintStream err) {
        try {
            Arguments arguments = parseArguments(args);
            List<Task> tasks = TaskLoader.loadFile(arguments.input());
            ScheduleResult result = JobScheduler.schedule(tasks, arguments.workers());
            out.print(JsonWriter.success(result));
            return EXIT_OK;
        } catch (SchedulerException e) {
            out.print(JsonWriter.error(e));
            err.println(e.code() + ": " + e.getMessage());
            return e.code() == ErrorCode.INVALID_ARGUMENT ? EXIT_USAGE : EXIT_ERROR;
        }
    }

    private record Arguments(Path input, int workers) {
    }

    private static Arguments parseArguments(String[] args) throws SchedulerException {
        String input = null;
        String workers = null;
        for (int i = 0; i < args.length; i++) {
            String option = args[i];
            if (!option.equals("--input") && !option.equals("--workers")) {
                throw usage("Unknown argument '" + option + "'");
            }
            if (i + 1 >= args.length) {
                throw usage("Option " + option + " requires a value");
            }
            String value = args[++i];
            if (option.equals("--input")) {
                if (input != null) {
                    throw usage("Option --input was given more than once");
                }
                input = value;
            } else {
                if (workers != null) {
                    throw usage("Option --workers was given more than once");
                }
                workers = value;
            }
        }
        if (input == null) {
            throw usage("Missing required option --input PATH");
        }
        if (workers == null) {
            throw usage("Missing required option --workers W");
        }
        int workerCount = parseWorkerCount(workers);
        try {
            return new Arguments(Path.of(input), workerCount);
        } catch (InvalidPathException e) {
            throw new SchedulerException(ErrorCode.READ_ERROR, "Invalid input path: " + e.getReason());
        }
    }

    private static int parseWorkerCount(String text) throws SchedulerException {
        String message = "--workers must be an integer from " + MIN_WORKERS + " to " + MAX_WORKERS;
        if (!UNSIGNED_DECIMAL.matcher(text).matches()) {
            throw usage(message);
        }
        int workers = Integer.parseInt(text);
        if (workers < MIN_WORKERS || workers > MAX_WORKERS) {
            throw usage(message);
        }
        return workers;
    }

    private static SchedulerException usage(String message) {
        return new SchedulerException(ErrorCode.INVALID_ARGUMENT,
                message + ". Usage: --input PATH --workers W");
    }
}
