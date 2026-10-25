package za.ac.up.cos341.codegen;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

// this run g++ and gcc for the tests that compile the generated C++ code and start the programs
final class Compilers {

    static final class Result {
        final int exit;
        final String output;

        Result(int exit, String output) {
            this.exit = exit;
            this.output = output;
        }
    }

    private Compilers() {
    }

    static boolean available(String compiler) {
        try {
            return run(Paths.get("."), compiler, "--version").exit == 0;
        } catch (IOException e) {
            return false;
        }
    }

    // gcc compiles a .cpp file as C++ but needs -lstdc++ to link std::cout. g++ links it by itself
    static Result compile(String compiler, Path source, Path program, String... options) throws IOException {
        List<String> command = new ArrayList<>();
        command.add(compiler);
        command.addAll(Arrays.asList(options));
        command.add(source.toString());
        command.add("-o");
        command.add(program.toString());

        if (compiler.equals("gcc")) {
            command.add("-lstdc++");
        }
        return run(source.getParent(), command.toArray(new String[0]));
    }

    static Result run(Path folder, String... command) throws IOException {
        Process process = new ProcessBuilder(command).directory(folder.toFile()).redirectErrorStream(true).start();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = process.getInputStream()) {
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
        }

        try {
            if (!process.waitFor(120, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return new Result(-1, "timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return new Result(-1, "interrupted");
        }
        return new Result(process.exitValue(), new String(out.toByteArray(), StandardCharsets.UTF_8).replace("\r\n", "\n"));
    }
}