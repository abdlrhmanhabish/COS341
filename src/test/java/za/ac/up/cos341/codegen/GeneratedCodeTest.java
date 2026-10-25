package za.ac.up.cos341.codegen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import za.ac.up.cos341.lexer.Lexer;
import za.ac.up.cos341.lexer.Token;
import za.ac.up.cos341.parser.Parser;
import za.ac.up.cos341.semantics.LeafTokens;
import za.ac.up.cos341.semantics.NameAnalyser;
import za.ac.up.cos341.semantics.SymbolTable;
import za.ac.up.cos341.semantics.TypeAnalyser;
import za.ac.up.cos341.semantics.ValidProgram;
import za.ac.up.cos341.tree.SyntaxTree;

// end-to-end test of the whole compiler
class GeneratedCodeTest {

    private static final int PROGRAMS = Integer.getInteger("spl.programs", 1000);
    @TempDir
    Path folder;

    private static final class Compiled {
        final SyntaxTree tree;
        final SymbolTable table;
        final String code;

        Compiled(String source) {
            List<Token> tokens = new Lexer().tokenize(source);
            tree = new Parser(tokens).parse();
            LeafTokens leaves = new LeafTokens(tree, tokens);
            table = new NameAnalyser(tree, leaves).analyse();
            new TypeAnalyser(tree, leaves, table).analyse();
            CodeGenerator generator = new CodeGenerator(tree, table);
            generator.setCallTranslator(new Inliner());
            code = generator.program();
        }
    }

    @Test
    void compiledProgramsPrintWhatTheInterpreterPrints() throws IOException {
        assumeTrue(Compilers.available("g++"), "g++ is not installed");
        List<String> sources = new ArrayList<>();
        List<String> expected = new ArrayList<>();
        StringBuilder file = new StringBuilder();
        for (long seed = 0; seed < PROGRAMS; seed++) {
            String source = ValidProgram.generate(seed).getText();
            Compiled compiled = new Compiled(source);
            String output;
            try {
                output = new Interpreter(compiled.tree, compiled.table).run();
            } catch (Interpreter.Stop e) {
                continue;
            }
            int number = sources.size();
            file.append(compiled.code.replace("int main ( ) {", "int program" + number + " ( ) {")).append('\n');
            sources.add(source);
            expected.add(output);
        }
        file.append("#include <cstdlib>\n\nint main ( int argc , char ** argv ) {\n");
        file.append("    int number = std::atoi ( argv [ 1 ] ) ;\n");
        for (int number = 0; number < sources.size(); number++) {
            file.append("    if ( number == ").append(number).append(" ) return program").append(number).append(" ( ) ;\n");
        }
        file.append("    return 1 ;\n}\n");
        Path cpp = folder.resolve("programs.cpp");
        Path programs = folder.resolve("programs");
        Files.write(cpp, file.toString().getBytes(StandardCharsets.UTF_8));
        // -fwrapv makes int overflow wrap around like in Java. Without it the C++ result of an overflow is undefined
        Compilers.Result build = Compilers.compile("g++", cpp, programs, "-fwrapv", "-w");
        assertEquals(0, build.exit, build.output.substring(0, Math.min(4000, build.output.length())));
        for (int number = 0; number < sources.size(); number++) {
            Compilers.Result run = Compilers.run(folder, programs.toString(), Integer.toString(number));
            assertEquals(0, run.exit, sources.get(number));
            assertEquals(expected.get(number), run.output, sources.get(number));
        }
        assertTrue(sources.size() > PROGRAMS / 2, "only " + sources.size() + " programs could be compared");
    }

    // the interpreter is checked against the outputs in tests/codegen which were worked out by hand
    @Test
    void interpreterGivesTheExpectedOutputOfTheExamples() throws IOException {
        int checked = 0;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(Paths.get("tests", "codegen"), "*.expected")) {
            for (Path file : files) {
                String name = file.getFileName().toString().replace(".expected", "");
                String source = new String(Files.readAllBytes(file.resolveSibling(name + ".txt")), StandardCharsets.UTF_8);
                Compiled compiled = new Compiled(source);
                String expected = new String(Files.readAllBytes(file), StandardCharsets.UTF_8).replace("\r\n", "\n");
                assertEquals(expected, new Interpreter(compiled.tree, compiled.table).run(), name);
                checked++;
            }
        }
        assertEquals(7, checked);
    }
}