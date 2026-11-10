package eu.amadeos.apps.labelprinter;

import eu.amadeos.commons.process.CommandRunner;
import eu.amadeos.commons.process.FinishedProcess;
import eu.amadeos.commons.process.ProgramMissingException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/** Answers commands from a table (by program name) and records what was run and what went to standard input. */
public final class FakeRunner implements CommandRunner {
    public final List<List<String>> commands = new ArrayList<>();
    public final List<byte[]> inputs = new ArrayList<>();
    private final Map<String, Function<List<String>, Process>> answers = new ConcurrentHashMap<>();

    public FakeRunner answer(String program, Function<List<String>, Process> answer) {
        answers.put(program, answer);
        return this;
    }

    public FakeRunner prints(String program, String stdout) {
        return answer(program, c -> FinishedProcess.printing(stdout));
    }

    public FakeRunner fails(String program, int exit, String stdout) {
        return answer(program, c -> new Recording(stdout, exit));
    }

    @Override
    public synchronized Process start(List<String> command) throws IOException {
        commands.add(List.copyOf(command));
        Function<List<String>, Process> answer = answers.get(command.getFirst());
        if (answer == null) throw new ProgramMissingException(command.getFirst(), null);
        Process p = answer.apply(command);
        if (p instanceof Recording r) r.owner = this;
        return p;
    }

    /** All commands as text lines, for asserting. */
    public synchronized List<String> lines() {
        return commands.stream().map(c -> String.join(" ", c)).toList();
    }

    /** A finished process that keeps what is written to its standard input. */
    public static final class Recording extends Process {
        private final byte[] stdout;
        private final int exit;
        private final ByteArrayOutputStream stdin = new ByteArrayOutputStream() {
            @Override
            public void close() {
                if (owner != null) {
                    synchronized (owner) {
                        owner.inputs.add(toByteArray());
                    }
                }
            }
        };
        FakeRunner owner;

        public Recording(String stdout, int exit) {
            this.stdout = stdout.getBytes(StandardCharsets.UTF_8);
            this.exit = exit;
        }

        @Override
        public OutputStream getOutputStream() {
            return stdin;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(stdout);
        }

        @Override
        public InputStream getErrorStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public int waitFor() {
            return exit;
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) {
            return true;
        }

        @Override
        public int exitValue() {
            return exit;
        }

        @Override
        public void destroy() {
        }
    }
}
