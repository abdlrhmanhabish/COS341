package za.ac.up.cos341.codegen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import za.ac.up.cos341.lexer.Lexer;
import za.ac.up.cos341.lexer.Token;
import za.ac.up.cos341.parser.Parser;
import za.ac.up.cos341.semantics.LeafTokens;
import za.ac.up.cos341.semantics.NameAnalyser;
import za.ac.up.cos341.semantics.SymbolTable;
import za.ac.up.cos341.semantics.TypeAnalyser;
import za.ac.up.cos341.tree.SyntaxTree;

class CodeGeneratorTest {

    @TempDir
    Path folder;

    private static String generate(String source) {
        return generate(source, false);
    }

    // inlined = true plugs in the call translator of phase 3b
    private static String generate(String source, boolean inlined) {
        List<Token> tokens = new Lexer().tokenize(source);
        SyntaxTree tree = new Parser(tokens).parse();
        LeafTokens leaves = new LeafTokens(tree, tokens);
        SymbolTable table = new NameAnalyser(tree, leaves).analyse();
        new TypeAnalyser(tree, leaves, table).analyse();
        CodeGenerator generator = new CodeGenerator(tree, table);
        if (inlined) {
            generator.setCallTranslator(new Inliner());
        }
        return generator.program();
    }

    @Test
    void printOfAStringUsesCout() {
        String code = generate(": : print \"hi\" ;");
        assertTrue(code.contains("std::cout << \"hi\" << std::endl ;"));
    }

    @Test
    void questionMarksAreEscapedAgainstTrigraphs() {
        String code = generate(": : print \"what??!\" ;");
        assertTrue(code.contains("std::cout << \"what\\?\\?!\" << std::endl ;"), code);
    }

    @Test
    void assignmentGoesThroughANewVariable() {
        String code = generate("#x : : #x = 5 ;");
        assertTrue(code.contains("    int t1 ;\n    t1 = 5 ;\n    v1_x = t1 ;"), code);
    }

    @Test
    void mainIsIndentedAndTheLabelsStayAtTheMargin() {
        String code = generate("#a : : if eq ( #a 1 ) then { print \"yes\" ; } else { nop ; } ;");
        String main = code.substring(code.indexOf("int main ( ) {\n") + "int main ( ) {\n".length(), code.indexOf("} /* end of executable C */"));
        for (String line : main.split("\n")) {
            assertTrue(line.matches("L\\d+ :.*") || line.startsWith("    "), line);
        }
        assertTrue(main.contains("\nL1 : /* then */\n"), main);
    }

    @Test
    void theCodeMineIsFramedAsTheTaskSheetShows() {
        String code = generate("#x : void #f ( ) { : : nop ; return } : #f ( ) ;", true);
        int begin = code.indexOf("/* begin code mine */");
        int entry = code.indexOf("/* begin of code-mine for void-procedure f1_f");
        int end = code.indexOf("/* end code mine */");
        int executable = code.indexOf("/* begin of executable C code */");
        assertTrue(begin == 0 && begin < entry && entry < end && end < executable, code);
    }

    @Test
    void numberTypeComesFromPhase2b() {
        assertTrue(generate("#x : : #x = mod ( 7 2 ) ;").contains("int t1 ;"));
        assertTrue(generate("#x : : #x = div ( 7 2 ) ;").contains("float t1 ;"));
        assertTrue(generate("#x : : #x = 1.5 ;").contains("float v1_x = 0 ;"));
    }

    @Test
    void everySemicolonIsFollowedByANewLine() {
        String code = generate("#x : : #x = add ( 1 2 ) ; print ( #x ) ; nop ;");
        assertFalse(code.matches("(?s).*; [^\\n].*"), code);
    }

    @Test
    void nopAndCommentBecomeCComments() {
        String code = generate(": : nop ; comment \"note\" ;");
        assertTrue(code.contains("/* nop */"));
        assertTrue(code.contains("/* \"note\" */"));
    }

    @Test
    void booleansUseOnlyGotoNeverElseOrLogicalOperators() {
        String code = generate("#a : : if not ( and ( eq ( #a 1 ) or ( larger ( #a 2 ) lesser ( #a 3 ) ) ) ) "
                + "then { nop ; } else { nop ; } ; while not ( eq ( #a 1 ) ) do { nop ; } ;");
        assertFalse(code.contains("else {"));
        assertFalse(code.contains("&&"));
        assertFalse(code.contains("||"));
        assertFalse(code.contains("!"));
        assertTrue(code.contains("goto"));
    }

    @Test
    void branchPlacesTheFalseCodeFirst() {
        String code = generate("#a : : if eq ( #a 1 ) then { print \"yes\" ; } else { print \"no\" ; } ;");
        assertTrue(code.indexOf("\"no\"") < code.indexOf("\"yes\""), code);
        assertTrue(code.contains("/* else */") && code.contains("/* then */") && code.contains("/* continue */"));
    }

    @Test
    void callsAreLeftToPhase3b() {
        assertThrows(UnsupportedOperationException.class,
                () -> generate("#x : num #f ( ) { : : nop ; return ( 1 ) } : #x = #f ( ) ;"));
    }

    @Test
    void theCodeMineIsOneCommentPerFunctionAndNeverEndsEarly() {
        String code = generate("#x : void #f ( ) { : : nop ; return } : #f ( ) ;", true);
        String mine = code.substring(0, code.indexOf("/* begin of executable C code */"));
        assertTrue(mine.contains("code-mine for void-procedure f1_f"), code);
        // the comments of the mined code lose their markers. Every /* in the mine is then closed by exactly one */
        assertEquals(mine.split("/\\*", -1).length, mine.split("\\*/", -1).length, mine);
    }

    @Test
    void everyInliningOfAFunctionGetsItsOwnLabels() {
        String code = generate("#x : num #f ( #n ) { : : while lesser ( #n 3 ) do { #n = add ( #n 1 ) ; } ; return ( #n ) } : "
                + "#x = #f ( 0 ) ; #x = #f ( #x ) ;", true);
        String main = code.substring(code.indexOf("/* begin of executable C code */"));
        Set<String> seen = new HashSet<>();
        Matcher definitions = Pattern.compile("(?m)^(L\\d+) :").matcher(main);
        int count = 0;
        while (definitions.find()) {
            assertTrue(seen.add(definitions.group(1)), "label defined twice: " + definitions.group(1) + "\n" + main);
            count++;
        }
        assertTrue(count >= 6, main);
    }

    // all programs in tests/codegen with their expected output
    private static final String[] EXAMPLES = {"arithmetic_and_print", "loops", "branches_and_booleans", "floats", "calls_labels", "calls_nested", "calls_void_float"};

    @ParameterizedTest
    @ValueSource(strings = {"arithmetic_and_print", "loops", "branches_and_booleans", "floats",
            "calls_labels", "calls_nested", "calls_void_float"})
    void generatedCodeRunsWithTheExpectedOutput(String name) throws Exception {
        assumeTrue(Compilers.available("g++"), "g++ is not installed");
        assertEquals(expected(name), compileAndRun("g++", name));
    }

    @ParameterizedTest
    @ValueSource(strings = {"arithmetic_and_print", "loops", "branches_and_booleans", "floats",
            "calls_labels", "calls_nested", "calls_void_float"})
    void gccWithTheCppLibraryRunsTheSamePrograms(String name) throws Exception {
        assumeTrue(Compilers.available("gcc"), "gcc is not installed");
        assertEquals(expected(name), compileAndRun("gcc", name));
    }

    // the code keeps to standard C++ and even the old C++98 and the strict C++11 will accept it without errors
    @Test
    void theCodeIsStandardCpp() throws Exception {
        assumeTrue(Compilers.available("g++"), "g++ is not installed");
        for (String name : EXAMPLES) {
            Path cpp = folder.resolve(name + ".cpp");
            Files.write(cpp, generate(example(name + ".txt"), true).getBytes(StandardCharsets.UTF_8));
            for (String standard : new String[] {"-std=c++98", "-std=c++11", "-std=c++17"}) {
                Compilers.Result result = Compilers.compile("g++", cpp, folder.resolve(name), standard, "-pedantic-errors");
                assertEquals(0, result.exit, name + " " + standard + "\n" + result.output);
            }
        }
    }

    private String compileAndRun(String compiler, String name) throws IOException {
        Path cpp = folder.resolve(name + ".cpp");
        Path program = folder.resolve(name);
        Files.write(cpp, generate(example(name + ".txt"), true).getBytes(StandardCharsets.UTF_8));
        Compilers.Result build = Compilers.compile(compiler, cpp, program);
        assertEquals(0, build.exit, compiler + " rejected the code\n" + build.output);
        Compilers.Result run = Compilers.run(folder, program.toString());
        assertEquals(0, run.exit, run.output);
        return run.output;
    }

    private static String expected(String name) throws IOException {
        return example(name + ".expected").replace("\r\n", "\n");
    }

    private static String example(String file) throws IOException {
        return new String(Files.readAllBytes(Paths.get("tests", "codegen", file)), StandardCharsets.UTF_8);
    }
}
