package za.ac.up.cos341.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import za.ac.up.cos341.lexer.Lexer;
import za.ac.up.cos341.lexer.Token;
import za.ac.up.cos341.tree.Node;
import za.ac.up.cos341.tree.SyntaxTree;
import za.ac.up.cos341.tree.XmlWriter;

class ParserTest {

    private static SyntaxTree parse(String source) {
        List<Token> tokens = new Lexer().tokenize(source);
        SyntaxTree tree = new Parser(tokens).parse();
        TreeCheck.assertDerivationTree(tree, tokens, source);
        return tree;
    }

    private static String render(String source) {
        return TreeCheck.render(parse(source));
    }

    private static SyntaxError rejected(String source) {
        return assertThrows(SyntaxError.class, () -> new Parser(new Lexer().tokenize(source)).parse());
    }

    @Test
    void smallestProgramHasThreeEmptyParts() {
        assertEquals("SPL_PROG(P(V_DECL() ':' F_DECL() ':' ALGO()))", render(": :"));
    }

    @Test
    void rootHoldsOnlyPBecauseTheDollarIsNotInTheInput() {
        SyntaxTree tree = parse(": : nop ;");
        assertEquals(Arrays.asList(1), tree.getRoot().getChildren());
        assertEquals("P", tree.getNode(1).getContents());
        assertTrue(tree.getNodes().stream().map(Node::getContents).noneMatch("$"::equals));
    }

    @Test
    void variableListsNestLikeTheGrammar() {
        assertEquals("SPL_PROG(P(V_DECL('#a' V_DECL('#b' V_DECL())) ':' F_DECL() ':' ALGO()))", render("#a #b : :"));
    }

    @Test
    void instructionsAreFollowedBySemicolons() {
        assertEquals("SPL_PROG(P(V_DECL() ':' F_DECL() ':' "
                        + "ALGO(INSTR('nop') ';' ALGO(INSTR('comment' '\"x\"') ';' ALGO()))))",
                render(": : nop ; comment \"x\" ;"));
    }

    @Test
    void nameFollowedByEqualsIsAnAssignment() {
        String tree = render("#x : : #x = 5 ;");
        assertTrue(tree.contains("INSTR(ASSIGN('#x' '=' TERM('5')))"), tree);
    }

    @Test
    void nameFollowedByABracketIsACall() {
        String tree = render(": : #f ( 1 #y ) ;");
        assertTrue(tree.contains("INSTR(CALL('#f' '(' INPUT(TERM('1') INPUT(TERM('#y') INPUT())) ')'))"), tree);
    }

    @Test
    void termChoosesBetweenNameAndCallOnTheNextToken() {
        String tree = render(": : #x = add ( #y #f ( ) ) ;");
        assertTrue(tree.contains("TERM('add' '(' TERM('#y') TERM(CALL('#f' '(' INPUT() ')')) ')')"), tree);
    }

    @Test
    void printTakesAStringOrATermInBrackets() {
        String tree = render(": : print \"hi\" ; print ( neg ( -2.5 ) ) ;");
        assertTrue(tree.contains("INSTR('print' OUTP('\"hi\"'))"), tree);
        assertTrue(tree.contains("INSTR('print' OUTP('(' TERM('neg' '(' TERM('-2.5') ')') ')'))"), tree);
    }

    @Test
    void functionsHaveTheirOwnProgram() {
        String tree = render(": void #show ( #a ) { : : print ( #a ) ; return } "
                + "num #twice ( #n ) { #t : : #t = mul ( #n 2 ) ; return ( #t ) } : #show ( 1 ) ;");
        assertTrue(tree.contains("F_TYPE('void' '#show' '(' V_DECL('#a' V_DECL()) ')' '{' "
                + "P(V_DECL() ':' F_DECL() ':' ALGO(INSTR('print' OUTP('(' TERM('#a') ')')) ';' ALGO())) 'return' '}')"), tree);
        assertTrue(tree.contains("'return' '(' TERM('#t') ')' '}')"), tree);
    }

    @Test
    void functionsCanBeNested() {
        String tree = render(": void #outer ( ) { : void #inner ( ) { : : return } : #inner ( ) ; return } : #outer ( ) ;");
        assertTrue(tree.contains("F_DECL(F_TYPE('void' '#inner' '(' V_DECL() ')' '{' "
                + "P(V_DECL() ':' F_DECL() ':' ALGO()) 'return' '}') F_DECL())"), tree);
    }

    @Test
    void branchHasTwoBlocks() {
        String tree = render(": : if eq ( 1 2 ) then { nop ; } else { } ;");
        assertTrue(tree.contains("INSTR(BRANCH('if' BOOL('eq' '(' TERM('1') TERM('2') ')') 'then' "
                + "'{' ALGO(INSTR('nop') ';' ALGO()) '}' 'else' '{' ALGO() '}'))"), tree);
    }

    @Test
    void loopsHaveTheConditionInFrontOrBehind() {
        String tree = render(": : while larger ( #i 0 ) do { nop ; } ; do { nop ; } until lesser ( #i 1 ) ;");
        assertTrue(tree.contains("LOOP(COND('while') BOOL('larger' '(' TERM('#i') TERM('0') ')') 'do' "
                + "'{' ALGO(INSTR('nop') ';' ALGO()) '}')"), tree);
        assertTrue(tree.contains("LOOP('do' '{' ALGO(INSTR('nop') ';' ALGO()) '}' COND('until') "
                + "BOOL('lesser' '(' TERM('#i') TERM('1') ')'))"), tree);
    }

    @Test
    void conditionsCombine() {
        String tree = render(": : if and ( not ( eq ( 1 1 ) ) or ( larger ( 2 1 ) lesser ( 1 2 ) ) ) then { } else { } ;");
        assertTrue(tree.contains("BOOL('and' '(' BOOL('not' '(' BOOL('eq' '(' TERM('1') TERM('1') ')') ')') "
                + "BOOL('or' '(' BOOL('larger' '(' TERM('2') TERM('1') ')') "
                + "BOOL('lesser' '(' TERM('1') TERM('2') ')') ')') ')')"), tree);
    }

    @Test
    void sampleProgramGivesTheCommittedSampleTree() throws IOException {
        String expected = new String(Files.readAllBytes(Paths.get("tests", "sample-tree.xml")), StandardCharsets.UTF_8);
        assertEquals(expected, XmlWriter.toXmlString(parse("#x : : #x = 3 ; print \"hello,world!\" ;")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"valid_01_minimal.txt", "valid_02_decls_assign.txt", "valid_03_function.txt",
            "valid_04_control_flow.txt", "valid_05_decimals.txt"})
    void lexerFixturesParse(String file) throws IOException {
        List<Token> tokens = new Lexer().tokenize(Paths.get("tests", "lexer", file));
        TreeCheck.assertDerivationTree(new Parser(tokens).parse(), tokens, file);
    }

    static Stream<Arguments> faultyPrograms() {
        return Stream.of(
                arguments("print \"hi\" ;", 1, "expected a variable name or ':'"),
                arguments("#x : #x = 1 ;", 1, "expected a function or ':'"),
                arguments(": :\nprint \"a\"\nnop ;", 3, "expected ';' to end the instruction"),
                arguments(": : while eq ( 1 1 ) do {\nnop\n} ;", 3, "expected ';' to end the instruction"),
                arguments(": : nop ; }", 1, "there is no '{' for it to close"),
                arguments(": : return ;", 1, "'return' outside of a function"),
                arguments(": : nop ; then", 1, "expected an instruction or the end of the file"),
                arguments(": : if eq ( 1 1 ) then { nop ; } ;", 1, "expected 'else' after the then-block"),
                arguments(": : if eq ( 1 1 ) { } else { } ;", 1, "expected 'then' after the condition"),
                arguments(": : if #x then { } else { } ;", 1, "expected a condition after 'if'"),
                arguments(": : if and ( eq ( 1 1 ) ) then { } else { } ;", 1, "expected the second condition of 'and'"),
                arguments(": : if eq ( 1 2 3 ) then { } else { } ;", 1, "expected ')' to close 'eq'"),
                arguments(": : while eq ( 1 1 ) { } ;", 1, "expected 'do' after the loop condition"),
                arguments(": : do { nop ; } ;", 1, "expected 'while' or 'until' after the do-block"),
                arguments(": : while eq ( 1 1 ) do { nop ;", 1, "The file ended before the '{' from line 1 was closed"),
                arguments(": : #x ;", 1, "expected '=' or '(' after the name #x"),
                arguments(": : #x = ( 1 ) ;", 1, "expected a term after '='"),
                arguments(": : #x = eq ( 1 2 ) ;", 1, "expected a term after '='"),
                arguments(": : #x = add ( 1 ) ;", 1, "expected the second term of 'add'"),
                arguments(": : #x = neg ( 1 2 ) ;", 1, "expected ')' to close 'neg'"),
                arguments(": : #f ( 1 ;", 1, "expected a term or ')' in the call of #f"),
                arguments(": : print #x ;", 1, "expected a string or '(' after 'print'"),
                arguments(": : comment #x ;", 1, "expected a string after 'comment'"),
                arguments(": void ( ) { : : return } :", 1, "expected a function name after 'void'"),
                arguments(": void #f ( 1 ) { : : return } : nop ;", 1, "expected a parameter name or ')'"),
                arguments(": void #f ( ) { : : return ( 1 ) } : nop ;", 1, "expected '}' to close the function #f"),
                arguments(": num #f ( ) { : : return } : nop ;", 1, "expected '(' after 'return'"),
                arguments(": void #f ( ) { : : nop ; } : nop ;", 1, "expected 'return' at the end of the function #f"),
                arguments(": void #f ( ) { : : nop ;", 1, "The file ended inside the function #f from line 1"),
                arguments(": void #f ( ) { : : if eq ( 1 1 ) then { return } else { } ; return } : nop ;", 1,
                        "'return' inside a block"));
    }

    @ParameterizedTest
    @MethodSource("faultyPrograms")
    void faultyProgramsAreRejectedWithAHint(String source, int line, String fragment) {
        SyntaxError error = rejected(source);
        assertEquals(line, error.getLine(), error.getMessage());
        assertTrue(error.getMessage().contains(fragment), error.getMessage());
        assertFalse(error.getHint().trim().isEmpty(), error.getMessage());
    }

    @Test
    void emptyFileIsRejected() {
        SyntaxError error = rejected("  \r\n  ");
        assertTrue(error.getMessage().contains("no SPL tokens"), error.getMessage());
        assertTrue(error.getHint().contains(": :"), error.getHint());
    }

    @Test
    void errorPointsAtTheOffendingToken() {
        SyntaxError error = rejected(": :\n  print \"a\" nop ;");
        assertEquals(2, error.getLine());
        assertEquals(13, error.getColumn());
        assertEquals(3, error.getLength());
        assertTrue(error.getHint().contains("starts on line 2"), error.getHint());
    }

    @Test
    void errorAtTheEndPointsBehindTheLastToken() {
        SyntaxError error = rejected(": : nop");
        assertEquals("Found the end of the file but expected ';' to end the instruction.", error.getMessage());
        assertEquals(1, error.getLine());
        assertEquals(8, error.getColumn());
        assertEquals(0, error.getLength());
    }
}