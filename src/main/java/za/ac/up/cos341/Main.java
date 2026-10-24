package za.ac.up.cos341;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import za.ac.up.cos341.codegen.CodeGenerator;
import za.ac.up.cos341.lexer.Lexer;
import za.ac.up.cos341.lexer.LexicalException;
import za.ac.up.cos341.lexer.Token;
import za.ac.up.cos341.parser.Parser;
import za.ac.up.cos341.parser.SyntaxError;
import za.ac.up.cos341.semantics.LeafTokens;
import za.ac.up.cos341.semantics.NameAnalyser;
import za.ac.up.cos341.semantics.SemanticError;
import za.ac.up.cos341.semantics.SemanticException;
import za.ac.up.cos341.semantics.SymbolTable;
import za.ac.up.cos341.semantics.TypeAnalyser;
import za.ac.up.cos341.tree.SyntaxTree;
import za.ac.up.cos341.tree.XmlWriter;

public final class Main {

    static final String DEFAULT_INPUT = "SPL.txt";
    static final String TREE_FILE = "tree.xml";
    static final String TABLE_FILE = "symbol-table.txt";

    //exit codes
    static final int CORRECT = 0;
    static final int REJECTED = 1;
    static final int FILE_PROBLEM = 2;
    static final int NAMING_ERROR = 3;
    static final int TYPE_ERROR = 4;

    // number of characters of a long source line that an error report show
    private static final int EXCERPT = 64;
    // at most this many naming or type errors are printed
    private static final int SHOWN_ERRORS = 20;
    // the work runs in a thread with a large stack which is helpgull for deeply nested programs
    private static final long STACK_SIZE = 64L << 20;

    private Main() {
    }

    public static void main(String[] args) throws InterruptedException {
        int[] code = {REJECTED};
        Thread worker = new Thread(null, () -> code[0] = run(args, Paths.get(""), System.out, System.err), "spl", STACK_SIZE);
        worker.start();
        worker.join();
        System.exit(code[0]);
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
        Path treeFile = input.resolveSibling(TREE_FILE);
        Path tableFile = input.resolveSibling(TABLE_FILE);
        Path codeFile = input.resolveSibling(codeName(file));
        String source;
        try {
            source = Lexer.readSource(input);
        } catch (IOException e) {
            err.println("Could not read " + input + ": " + e.getMessage());
            return FILE_PROBLEM;
        }
        List<Token> tokens;
        SyntaxTree tree;
        try {
            tokens = new Lexer().tokenize(source);
            tree = new Parser(tokens).parse();
        } catch (LexicalException e) {
            report(err, "Lexical error", file, e.getLine(), e.getColumn(), e.getOffendingText().length(),
                    "The text " + e.getOffendingText() + " is not a valid SPL token.", e.getHint(), source);
            return rejected(err, REJECTED, "No tree.xml was written.", treeFile, tableFile, codeFile);
        } catch (SyntaxError e) {
            report(err, "Syntax error", file, e.getLine(), e.getColumn(), e.getLength(),
                    e.getMessage(), e.getHint(), source);
            return rejected(err, REJECTED, "No tree.xml was written.", treeFile, tableFile, codeFile);
        } catch (StackOverflowError e) {
            err.println("Syntax error in " + file + ": the program is nested too deeply to be analysed.");
            return rejected(err, REJECTED, "No tree.xml was written.", treeFile, tableFile, codeFile);
        }
        try {
            XmlWriter.write(tree, treeFile);
        } catch (IOException e) {
            err.println("Could not write " + treeFile + ": " + e.getMessage());
            return FILE_PROBLEM;
        }
        LeafTokens leaves = new LeafTokens(tree, tokens);
        SymbolTable table;
        try {
            table = new NameAnalyser(tree, leaves).analyse();
            new TypeAnalyser(tree, leaves, table).analyse();
        } 
        catch (SemanticException e) {
            // the whole report goes to one stream and consoles cannot mix up the order of the lines
            err.println(file + " is syntactically correct and its syntax tree was written to " + treeFile);
            err.println();
            return semanticErrors(err, e, file, source, tableFile, codeFile);
        } catch (StackOverflowError e) {
            err.println("The program in " + file + " is nested too deeply to be analysed.");
            return rejected(err, REJECTED, "No symbol table was written.", tableFile, codeFile);
        }
        try {
            Files.write(tableFile, table.toText(leaves).replace("\n", System.lineSeparator())
                    .getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            err.println("Could not write " + tableFile + ": " + e.getMessage());
            return FILE_PROBLEM;
        }
        out.println(file + " contains a correct SPL program.");
        out.println("The syntax tree has " + tree.size() + " nodes and was written to " + treeFile);
        out.println("The symbol table was written to " + tableFile);
        try {
            String code = new CodeGenerator(tree, table).program();
            Files.write(codeFile, code.replace("\n", System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
            out.println("The target code was written to " + codeFile);
        } catch (UnsupportedOperationException e) {
            out.println("No target code was written yet. " + e.getMessage());
            try {
                Files.deleteIfExists(codeFile);
            } catch (IOException ignored) {
                // an old C++ file that cannot be deleted does no harm here
            }
        } catch (IOException e) {
            err.println("Could not write " + codeFile + ": " + e.getMessage());
            return FILE_PROBLEM;
        }
        return CORRECT;
    }

    //this prints the naming or type errors one afterone
    private static int semanticErrors(PrintStream err, SemanticException e, String file, String source, Path... oldFiles) {
        List<SemanticError> errors = e.getErrors();
        int shown = Math.min(errors.size(), SHOWN_ERRORS);
        for (int i = 0; i < shown; i++) {
            SemanticError error = errors.get(i);
            if (i > 0) {
                err.println();
            }
            report(err, e.getKind().getLabel(), file, error.getLine(), error.getColumn(), error.getLength(),
                    error.getMessage(), error.getHint(), source);
        }
        if (shown < errors.size()) {
            err.println();
            err.println("The other " + (errors.size() - shown) + " errors are not shown.");
        }
        boolean naming = e.getKind() == SemanticException.Kind.NAMING;
        String count = errors.size() + (naming ? " naming error" : " type error") + (errors.size() == 1 ? "" : "s");
        return rejected(err, naming ? NAMING_ERROR : TYPE_ERROR, "Found " + count + ". No symbol table was written.",
                oldFiles);
    }

    // the C++ file is named after the SPL file: SPL.txt gives SPL.cpp.
    // An input which already ends with .cpp keeps whothele name in front of the new extension
    static String codeName(String input) {
        int dot = input.lastIndexOf('.');
        String name = (dot > 0 ? input.substring(0, dot) : input) + ".cpp";
        return name.equals(input) ? input + ".cpp" : name;
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

    // print what was not written. Output files of an earlier run are deleted because they could be mistaken for the results of this input
    private static int rejected(PrintStream err, int code, String summary, Path... oldFiles) {
        err.println();
        err.println(summary);
        for (Path old : oldFiles) {
            try {
                if (Files.deleteIfExists(old)) {
                    err.println("The " + old.getFileName() + " of an earlier run was deleted.");
                }
            } catch (IOException e) {
                err.println("The old file " + old + " could not be deleted and is out of date.");
            }
        }
        return code;
    }

    private static boolean isHelp(String argument) {
        return argument.equals("-h") || argument.equals("--help") || argument.equals("/?");
    }

    private static void usage(PrintStream stream) {
        Path jar = jarFile();
        String command = jar != null ? "java -jar " + jar.getFileName() : "java " + Main.class.getName();
        stream.println("Usage: " + command + " [FILE]");
        stream.println("Checks the SPL program in FILE against the syntax rules and the naming and type rules.");
        stream.println("The syntax tree goes to " + TREE_FILE + " and the symbol table to " + TABLE_FILE + " in the folder of FILE.");
        stream.println("The C++ program goes to a file named after FILE in the same folder, for example SPL.cpp.");
        stream.println("Without FILE the program reads " + DEFAULT_INPUT + " from the current folder.");
    }
}