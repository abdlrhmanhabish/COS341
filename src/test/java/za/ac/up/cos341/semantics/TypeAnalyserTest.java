package za.ac.up.cos341.semantics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import za.ac.up.cos341.lexer.Token;
import za.ac.up.cos341.tree.Node;
import za.ac.up.cos341.tree.NodeKind;

class TypeAnalyserTest {

    private static final String EVERYTHING = "#x #y\n:\n"
            + "num #sq ( #n ) { : : return ( mul ( #n #n ) ) }\n"
            + "void #show ( #v ) { : : print ( #v ) ; return }\n"
            + ":\n"
            + "#x = #sq ( 3 ) ;\n"
            + "#show ( #x ) ;\n"
            + "if and ( larger ( #x 1 ) not ( eq ( #x 2 ) ) ) then { print \"big\" ; } else { nop ; } ;\n"
            + "#y = 0 ;\n"
            + "while lesser ( #y 3 ) do { #y = add ( #y 1 ) ; } ;\n"
            + "do { #y = sub ( #y 1 ) ; } until or ( eq ( #y 0 ) larger ( neg ( #y ) 1 ) ) ;\n"
            + "comment \"done\" ;\n";

    private static SemanticException rejected(String source) {
        SemanticException e = assertThrows(SemanticException.class, () -> new Analysis(source).types());
        assertEquals(SemanticException.Kind.TYPE, e.getKind());
        return e;
    }

    // the error must point to the nth occurrence of the given token
    private static void assertAt(String source, String token, int occurrence, SemanticError error) {
        Analysis analysis = new Analysis(source);
        Token expected = analysis.tokens.get(analysis.token(token, occurrence));
        assertEquals(expected.line() + ":" + expected.column(), error.getLine() + ":" + error.getColumn(), error.getMessage());
    }

    @Test
    void everyNodeOfACorrectProgramGetsItsType() {
        Analysis analysis = new Analysis(EVERYTHING);
        SymbolTable table = analysis.types();
        for (Node node : Trees.preOrder(analysis.tree)) {
            Type type = table.typeOf(node.getId());
            if (node.getKind() == NodeKind.LEAF) {
                if (Trees.isName(node) || Trees.isNumber(node)) {
                    assertNotEquals(Type.UNKNOWN, type, node.toString());
                }
                continue;
            }
            switch (node.getContents()) {
                case "TERM":
                    assertEquals(Type.NUMERIC, type, node.toString());
                    break;
                case "BOOL":
                    assertEquals(Type.BOOLEAN, type, node.toString());
                    break;
                case "CALL":
                    assertTrue(type == Type.NUMERIC || type == Type.PROCEDURE, node.toString());
                    break;
                default:
                    assertEquals(Type.OK, type, node.toString());
            }
        }
    }

    @Test
    void namesGetTheTypesOfTheirDeclarations() {
        Analysis analysis = new Analysis(EVERYTHING);
        SymbolTable table = analysis.types();
        assertEquals(Type.NUMERIC, analysis.symbol(table, "#x", 0).getType());
        assertEquals(Type.NUMERIC, analysis.symbol(table, "#n", 0).getType());
        assertEquals(Type.NUMERIC, analysis.symbol(table, "#sq", 0).getType());
        assertEquals(Type.PROCEDURE, analysis.symbol(table, "#show", 0).getType());
        assertEquals(Type.PROCEDURE, table.typeOf(analysis.leaf(analysis.token("#show", 1)).getId()));
    }

    static Stream<Arguments> numberTypes() {
        return Stream.of(
                arguments("#x : : #x = 1 ;", NumberType.INTEGER),
                arguments("#x : : #x = mod ( 7 2 ) ;", NumberType.INTEGER),
                arguments("#x : : #x = div ( 7 2 ) ;", NumberType.FLOAT),
                arguments("#x : : #x = -2.5 ;", NumberType.FLOAT),
                arguments(": : print \"nothing\" ;", NumberType.INTEGER));
    }

    @ParameterizedTest
    @MethodSource("numberTypes")
    void divAndDecimalPointsMakeAProgramFloat(String source, NumberType expected) {
        assertEquals(expected, new Analysis(source).types().getNumbers());
    }

    @Test
    void voidFunctionCannotBeUsedAsATerm() {
        String source = ": void #p ( ) { : : return } : print ( #p ( ) ) ;";
        SemanticException e = rejected(source);
        assertEquals(1, e.getErrors().size());
        assertAt(source, "#p", 1, e.getErrors().get(0));
        assertEquals("#p is a void function and gives back no value. Its call cannot be used as a term.",
                e.getErrors().get(0).getMessage());
    }

    @Test
    void numFunctionCannotStandAloneAsAnInstruction() {
        String source = ": num #f ( ) { : : return ( 1 ) } : #f ( ) ;";
        SemanticException e = rejected(source);
        assertEquals(1, e.getErrors().size());
        assertAt(source, "#f", 1, e.getErrors().get(0));
        assertTrue(e.getErrors().get(0).getMessage().startsWith("#f is a num function."));
    }

    @Test
    void everyParameterNeedsExactlyOneArgument() {
        String source = ": void #p ( #a #b ) { : : return } : #p ( 1 ) ; #p ( 1 2 3 ) ; #p ( 1 2 ) ;";
        SemanticException e = rejected(source);
        assertEquals(2, e.getErrors().size());
        assertEquals("#p has 2 parameters but this call passes 1 argument.", e.getErrors().get(0).getMessage());
        assertEquals("#p has 2 parameters but this call passes 3 arguments.", e.getErrors().get(1).getMessage());
        assertTrue(e.getErrors().get(0).getHint().contains("as  #p ( #a #b ) ."), e.getErrors().get(0).getHint());
        assertAt(source, "#p", 2, e.getErrors().get(1));
    }

    @Test
    void modAndDivTogetherRejectTheWholeTreeAtOnce() {
        String source = "#x : void #p ( ) { : : return } :\n#x = mod ( 7 2 ) ;\n#x = div ( 1 2 ) ;\nprint ( #p ( ) ) ;";
        SemanticException e = rejected(source);
        assertEquals(1, e.getErrors().size(), "the void call is not looked at any more");
        SemanticError error = e.getErrors().get(0);
        assertEquals(TypeAnalyser.FLOAT_INTEGER_CONFLICT, error.getMessage());
        assertEquals("A float-integer-conflict might perhaps be possible", error.getMessage());
        assertAt(source, "div", 0, error);
        assertTrue(error.getHint().contains("mod on line 2, column 6 and div on line 3, column 6"), error.getHint());
    }

    @Test
    void conflictIsReportedWhereTheSecondOperatorAppears() {
        String source = "#x : : #x = div ( 1 2 ) ; #x = mod ( 7 2 ) ;";
        assertAt(source, "mod", 0, rejected(source).getErrors().get(0));
    }

    @Test
    void modForbidsDecimalPoints() {
        String source = "#x : : #x = mod ( 7 2 ) ; #x = 2.5 ; #x = -0.5 ; #x = 3 ;";
        SemanticException e = rejected(source);
        assertEquals(2, e.getErrors().size());
        assertAt(source, "2.5", 0, e.getErrors().get(0));
        assertAt(source, "-0.5", 0, e.getErrors().get(1));
        assertEquals("The number 2.5 has a decimal point but the program uses mod.", e.getErrors().get(0).getMessage());
    }

    @Test
    void aFaultyBodyDoesNotHideErrorsAtTheCalls() {
        String source = ": num #f ( ) { : void #p ( ) { : : return } : print ( #p ( ) ) ; return ( 1 ) } : #f ( ) ;";
        SemanticException e = rejected(source);
        assertEquals(2, e.getErrors().size(), e.getErrors().toString());
        assertAt(source, "#p", 1, e.getErrors().get(0));
        assertAt(source, "#f", 1, e.getErrors().get(1));
    }

    @Test
    void anErrorIsReportedOnlyWhereTheRuleBreaks() {
        String source = "#x : num #f ( #a ) { : : return ( #a ) } void #p ( ) { : : return } :\n"
                + "print ( #f ( #p ( ) ) ) ;\n#x = add ( 1 #p ( ) ) ;";
        SemanticException e = rejected(source);
        assertEquals(2, e.getErrors().size(), e.getErrors().toString());
        assertAt(source, "#p", 1, e.getErrors().get(0));
        assertAt(source, "#p", 2, e.getErrors().get(1));
    }

    @Test
    void aNumFunctionMustReturnANumber() {
        String source = ": num #f ( ) { : void #p ( ) { : : return } : return ( #p ( ) ) } : print ( #f ( ) ) ;";
        SemanticException e = rejected(source);
        assertEquals(1, e.getErrors().size());
        assertAt(source, "#p", 1, e.getErrors().get(0));
    }

    @Test
    void typesAboveABrokenRuleStayUnknown() {
        Analysis analysis = new Analysis("#x : void #p ( ) { : : return } : #x = #p ( ) ;");
        SymbolTable table = analysis.names();
        assertThrows(SemanticException.class, () -> new TypeAnalyser(analysis.tree, analysis.leaves, table).analyse());
        assertEquals(Type.PROCEDURE, table.typeOf(analysis.all("CALL").get(0).getId()));
        assertEquals(Type.UNKNOWN, table.typeOf(analysis.all("ASSIGN").get(0).getId()));
        assertEquals(Type.UNKNOWN, table.typeOf(analysis.tree.getRoot().getId()));
        assertEquals(Type.OK, table.typeOf(analysis.all("F_DECL").get(0).getId()));
    }
}