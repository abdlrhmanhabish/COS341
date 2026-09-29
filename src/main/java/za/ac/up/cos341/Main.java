package za.ac.up.cos341;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import za.ac.up.cos341.lexer.Lexer;
import za.ac.up.cos341.lexer.LexicalException;
import za.ac.up.cos341.parser.Parser;
import za.ac.up.cos341.parser.SyntaxError;
import za.ac.up.cos341.tree.SyntaxTree;
import za.ac.up.cos341.tree.XmlWriter;

public final class Main {

    static final String DEFAULT_INPUT = "SPL.txt";
    static final String OUTPUT = "tree.xml";

    //exit codes
    static final int CORRECT = 0;
    static final int REJECTED = 1;
    static final int FILE_PROBLEM = 2;

    // number of characters of a long source line that an error report show
    private static final int EXCERPT = 64;

    private Main() {
    }

    public static void main(String[] args) {
        System.exit(run(args, Paths.get(""), System.out, System.err));
    }

    static int run(String[] args, Path workingDirectory, PrintStream out, PrintStream err) {
        if (args.length == 1 && isHelp(args[0])) {
            usage(out);
            return CORRECT;
        }
        if (args.length > 1) {
            usage(err);
            return FILE_PROBLEM;
        }
        Path input = findInput(args, workingDirectory).toAbsolutePath().normalize();
        if (!Files.isRegularFile(input)) {
            err.println("Could not find the input file " + input);
            if (args.length == 0) {
                err.println("Windows may hide file extensions. Check that the file is not called SPL.txt.txt by accident.");
            }
            err.println();
            usage(err);
            return FILE_PROBLEM;
        }
        String file = input.getFileName().toString();
        Path output = input.resolveSibling(OUTPUT);
        String source;
        try {
            source = Lexer.readSource(input);
        } catch (IOException e) {
            err.println("Could not read " + input + ": " + e.getMessage());
            return FILE_PROBLEM;
        }
        try {
            SyntaxTree tree = new Parser(new Lexer().tokenize(source)).parse();
            XmlWriter.write(tree, output);
            out.println(file + " contains a syntactically correct SPL program.");
            out.println("The syntax tree has " + tree.size() + " nodes and was written to " + output);
            return CORRECT;
        } catch (LexicalException e) {
            report(err, "Lexical error", file, e.getLine(), e.getColumn(), e.getOffendingText().length(),
                    "The text " + e.getOffendingText() + " is not a valid SPL token.", e.getHint(), source);
        } catch (SyntaxError e) {
            report(err, "Syntax error", file, e.getLine(), e.getColumn(), e.getLength(),
                    e.getMessage(), e.getHint(), source);
        } catch (StackOverflowError e) {
            err.println("Syntax error in " + file + ": the program is nested too deeply to be analysed.");
        } catch (IOException e) {
            err.println("Could not write " + output + ": " + e.getMessage());
            return FILE_PROBLEM;
        }
        removeOldOutput(output, err);
        return REJECTED;
    }

    static Path findInput(String[] args, Path workingDirectory) {
        if (args.length == 1) {
            return workingDirectory.resolve(args[0]);
        }
        Path local = workingDirectory.resolve(DEFAULT_INPUT);
        Path jar = jarFile();
        if (!Files.isRegularFile(local) && jar != null) {
            Path besideJar = jar.resolveSibling(DEFAULT_INPUT);
            if (Files.isRegularFile(besideJar)) {
                return besideJar;
            }
        }
        return local;
    }

    // the jar this program was started from
    private static Path jarFile() {
        try {
            Path location = Paths.get(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return location.getFileName().toString().endsWith(".jar") ? location.toAbsolutePath() : null;
        } catch (URISyntaxException | RuntimeException e) {
            return null;
        }
    }

    // print the error together with the line it occurred in and a marker under the offending token
    static void report(PrintStream err, String kind, String file, int line, int column, int length, String message, String hint, String source) {
        err.println(kind + " in " + file + " on line " + line + ", column " + column + ":");
        for (String part : wrap(message, 76)) {
            err.println("  " + part);
        }
        String text = lineOf(source, line);
        int at = Math.min(column - 1, text.length());
        int from = Math.max(0, Math.min(at - 40, text.length() - EXCERPT));
        int to = Math.min(text.length(), from + EXCERPT);
        String before = from > 0 ? "..." : "";
        String after = to < text.length() ? "..." : "";
        StringBuilder marker = new StringBuilder(before.replace('.', ' '));
        for (int i = from; i < at; i++) {
            marker.append(text.charAt(i) == '\t' ? '\t' : ' ');
        }
        marker.append(repeat('^', Math.max(1, Math.min(length, to - at))));
        String gutter = String.format("%5d | ", line);
        err.println();
        err.println(gutter + before + printable(text.substring(from, to)) + after);
        err.println(repeat(' ', gutter.length() - 2) + "| " + marker);
        if (hint != null && !hint.trim().isEmpty()) {
            List<String> lines = wrap(hint, 72);
            err.println();
            err.println("Hint: " + lines.get(0));
            for (int i = 1; i < lines.size(); i++) {
                err.println("      " + lines.get(i));
            }
        }
    }

    // break lines that are longer than width at a space
    static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\n")) {
            int cut = line.lastIndexOf(' ', width);
            while (line.length() > width && cut > 0) {
                lines.add(line.substring(0, cut));
                line = line.substring(cut + 1);
                cut = line.lastIndexOf(' ', width);
            }
            lines.add(line);
        }
        return lines;
    }

    private static String repeat(char c, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            result.append(c);
        }
        return result.toString();
    }

    // this is z given line of the source counted the same way as the lexer counts lines
    private static String lineOf(String source, int line) {
        String[] lines = source.split("\r\n|\r|\n", -1);
        return line >= 1 && line <= lines.length ? lines[line - 1] : "";
    }

    // keeps the console readable when the line contains characters that are not plain ASCII
    private static String printable(String text) {
        StringBuilder result = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            result.append(c == '\t' || (c >= 32 && c <= 126) ? c : '?');
        }
        return result.toString();
    }

    // an old tree.xml from an earlier run must not be mistaken for the tree of this input
    private static void removeOldOutput(Path output, PrintStream err) {
        err.println();
        try {
            if (Files.deleteIfExists(output)) {
                err.println("No tree.xml was written. The tree.xml of an earlier run was deleted.");
                return;
            }
        } catch (IOException e) {
            err.println("No tree.xml was written. The old file " + output + " could not be deleted and is out of date.");
            return;
        }
        err.println("No tree.xml was written.");
    }

    private static boolean isHelp(String argument) {
        return argument.equals("-h") || argument.equals("--help") || argument.equals("/?");
    }

    private static void usage(PrintStream stream) {
        Path jar = jarFile();
        String command = jar != null ? "java -jar " + jar.getFileName() : "java " + Main.class.getName();
        stream.println("Usage: " + command + " [FILE]");
        stream.println("Checks the SPL program in FILE and writes its syntax tree to tree.xml in the folder of FILE.");
        stream.println("Without FILE the program reads " + DEFAULT_INPUT + " from the current folder.");
    }
}