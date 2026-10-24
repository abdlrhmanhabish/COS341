package za.ac.up.cos341.semantics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import za.ac.up.cos341.lexer.Token;
import za.ac.up.cos341.tree.Node;

class NameAnalyserTest {

    // a scope example with a global #x that #k falls back to
    private static final String A21 = "#x : num #f ( ) { : "
            + "void #g ( ) { #x : void #j ( ) { : : #x = 1 ; return } : #j ( ) ; return } "
            + "void #h ( ) { : void #k ( ) { : : #x = 2 ; return } : #k ( ) ; return } "
            + ": #g ( ) ; #h ( ) ; return ( 0 ) } : #x = #f ( ) ;";

    private static SemanticException rejected(String source) {
        SemanticException e = assertThrows(SemanticException.class, () -> new Analysis(source).names());
        assertEquals(SemanticException.Kind.NAMING, e.getKind());
        return e;
    }

    @Test
    void smallestProgramDeclaresNothing() {
        assertTrue(new Analysis(": :").names().getSymbols().isEmpty());
    }

    @Test
    void everyDeclarationGetsItsOwnInternalName() {
        SymbolTable table = new Analysis("#x # : void #f ( #n ) { #y : : return } : nop ;").names();
        List<Symbol> symbols = table.getSymbols();
        assertEquals(Arrays.asList("v1_x", "v2", "f1_f", "v3_n", "v4_y"), symbols.stream().map(Symbol::getInternalName).collect(Collectors.toList()));
        assertEquals(Arrays.asList(Symbol.Kind.VARIABLE, Symbol.Kind.VARIABLE, Symbol.Kind.FUNCTION,
                Symbol.Kind.PARAMETER, Symbol.Kind.VARIABLE),
                symbols.stream().map(Symbol::getKind).collect(Collectors.toList()));
        assertEquals(Arrays.asList(0, 0, 1, 1, 1), symbols.stream().map(Symbol::getLevel).collect(Collectors.toList()));
        assertNull(symbols.get(2).getOwner());
        assertSame(symbols.get(2), symbols.get(3).getOwner());
        assertEquals(Arrays.asList(symbols.get(3)), symbols.get(2).getParameters());
    }

    @Test
    void usesShareTheSymbolOfTheirDeclaration() {
        Analysis analysis = new Analysis("#x : : #x = 1 ; print ( #x ) ;");
        SymbolTable table = analysis.names();
        Symbol declared = analysis.symbol(table, "#x", 0);
        assertSame(declared, analysis.symbol(table, "#x", 1));
        assertSame(declared, analysis.symbol(table, "#x", 2));
        assertEquals(2, table.usesOf(declared).size());
    }

    @Test
    void theNearestDeclarationWins() {
        Analysis analysis = new Analysis("#x : void #f ( ) { #x : : #x = 1 ; return } : #x = 2 ; #f ( ) ;");
        SymbolTable table = analysis.names();
        Symbol global = analysis.symbol(table, "#x", 0);
        Symbol local = analysis.symbol(table, "#x", 1);
        assertNotSame(global, local);
        assertNotEquals(global.getInternalName(), local.getInternalName());
        assertSame(local, analysis.symbol(table, "#x", 2));
        assertSame(global, analysis.symbol(table, "#x", 3));
    }

    @Test
    void parametersAndLocalsAreKnownInTheBodyAndInTheReturnedTerm() {
        Analysis analysis = new Analysis(": num #f ( #n ) { #t : : #t = add ( #n 1 ) ; return ( mul ( #t #n ) ) } " + ": print ( #f ( 2 ) ) ;");
        SymbolTable table = analysis.names();
        for (String name : new String[] {"#n", "#t"}) {
            Symbol declared = analysis.symbol(table, name, 0);
            assertSame(declared, analysis.symbol(table, name, 1));
            assertSame(declared, analysis.symbol(table, name, 2));
        }

        assertEquals(Symbol.Kind.PARAMETER, analysis.symbol(table, "#n", 0).getKind());
    }

    @Test
    void nestedFunctionsSeeTheVariablesAroundThem() {
        Analysis analysis = new Analysis("#g1 : void #f ( #p ) { #q : void #h ( ) { : : #g1 = add ( #p #q ) ; return } "
                + ": #h ( ) ; return } : #f ( 1 ) ;");
        SymbolTable table = analysis.names();
        for (String name : new String[] {"#g1", "#p", "#q"}) {
            assertSame(analysis.symbol(table, name, 0), analysis.symbol(table, name, 1), name);
        }
    }

    @Test
    void declarationsAreFoundInAncestorsAndNeverSideways() {
        Analysis analysis = new Analysis(A21);
        SymbolTable table = analysis.names();
        Symbol global = analysis.symbol(table, "#x", 0);
        Symbol ofG = analysis.symbol(table, "#x", 1);
        assertEquals(ofG.getInternalName(), analysis.symbol(table, "#x", 2).getInternalName(), "the #x of #j");
        assertEquals(global.getInternalName(), analysis.symbol(table, "#x", 3).getInternalName(), "the #x of #k");
        assertNotEquals(global.getInternalName(), ofG.getInternalName());
        assertSame(global, analysis.symbol(table, "#x", 4));
    }

    @Test
    void sidewaysDeclarationDoesNotCount() {
        // A#21 without the global #x: the #x of #k finds no declaration
        String source = ": num #f ( ) { : "
                + "void #g ( ) { #x : void #j ( ) { : : #x = 1 ; return } : #j ( ) ; return } "
                + "void #h ( ) { : void #k ( ) { : : #x = 2 ; return } : #k ( ) ; return } "
                + ": #g ( ) ; #h ( ) ; return ( 0 ) } : print ( #f ( ) ) ;";
        Analysis analysis = new Analysis(source);
        SemanticException e = rejected(source);
        assertEquals(1, e.getErrors().size(), e.getErrors().toString());
        SemanticError error = e.getErrors().get(0);
        Token use = analysis.tokens.get(analysis.token("#x", 2));
        assertEquals(use.column(), error.getColumn());
        assertEquals("The variable #x is not declared.", error.getMessage());
        assertTrue(error.getHint().contains("declared in the function #g"), error.getHint());
    }

    @Test
    void variablesAndFunctionsMayShareAName() {
        Analysis analysis = new Analysis("#f : num #f ( ) { : : return ( 1 ) } : #f = #f ( ) ;");
        SymbolTable table = analysis.names();
        assertSame(analysis.symbol(table, "#f", 0), analysis.symbol(table, "#f", 2));
        assertSame(analysis.symbol(table, "#f", 1), analysis.symbol(table, "#f", 3));
        assertTrue(analysis.symbol(table, "#f", 3).isFunction());
    }

    @Test
    void eachFunctionListMayHaveItsOwnCopyOfAHelper() {
        Analysis analysis = new Analysis(": void #f ( ) { : void #h ( ) { : : return } : #h ( ) ; return } "
                + "void #g ( ) { : void #h ( ) { : : return } : #h ( ) ; return } : #f ( ) ; #g ( ) ;");
        SymbolTable table = analysis.names();
        Symbol first = analysis.symbol(table, "#h", 0);
        Symbol second = analysis.symbol(table, "#h", 2);
        assertSame(first, analysis.symbol(table, "#h", 1));
        assertSame(second, analysis.symbol(table, "#h", 3));
        assertNotEquals(first.getInternalName(), second.getInternalName());
    }

    @Test
    void aSubFunctionMayCarryTheNameOfItsParent() {
        Analysis analysis = new Analysis(": void #f ( ) { : void #f ( ) { : : return } : #f ( ) ; return } : #f ( ) ;");
        SymbolTable table = analysis.names();
        assertSame(analysis.symbol(table, "#f", 1), analysis.symbol(table, "#f", 2), "the call inside #f");
        assertSame(analysis.symbol(table, "#f", 0), analysis.symbol(table, "#f", 3), "the call in the main program");
    }

    @Test
    void aNestedFunctionMayReuseAParameterNameOfItsParent() {
        Analysis analysis = new Analysis(": void #f ( #p ) { : void #g ( ) { #p : : #p = 1 ; return } : #g ( ) ; return } "
                + ": #f ( 1 ) ;");
        SymbolTable table = analysis.names();
        assertSame(analysis.symbol(table, "#p", 1), analysis.symbol(table, "#p", 2));
        assertNotSame(analysis.symbol(table, "#p", 0), analysis.symbol(table, "#p", 1));
    }
    static Stream<Arguments> faultyPrograms() {
        return Stream.of(
                arguments(": : #x = 1 ;", "#x", 0, "The variable #x is not declared.",
                        "before the first ':' of the main program"),
                arguments(": void #f ( ) { : : print ( #y ) ; return } : #f ( ) ;", "#y", 0,
                        "The variable #y is not declared.", "of the function #f"),
                arguments(": num #f ( ) { : : return ( #z ) } : print ( #f ( ) ) ;", "#z", 0,
                        "The variable #z is not declared.", "of the function #f"),
                arguments(": : #f ( ) ;", "#f", 0, "The function #f is not declared.",
                        "function list between the two ':' of the main program"),
                arguments(": void #f ( ) { : : #f ( ) ; return } : #f ( ) ;", "#f", 1,
                        "The function #f cannot be called here.", "inside #f itself"),
                arguments(": void #f ( ) { : void #g ( ) { : : #f ( ) ; return } : #g ( ) ; return } : #f ( ) ;", "#f", 1,
                        "The function #f cannot be called here.", "would be a recursion"),
                arguments(": void #f ( ) { : : #g ( ) ; return } void #g ( ) { : : return } : #f ( ) ;", "#g", 0,
                        "The function #g cannot be called here.", "function list of the main program"),
                arguments(": void #f ( ) { : void #g ( ) { : : return } : return } : #g ( ) ;", "#g", 1,
                        "The function #g cannot be called here.", "declared inside the function #f"),
                arguments("#a #a : : nop ;", "#a", 1, "The variable #a is declared twice in the main program.",
                        "line 1, column 1"),
                arguments(": void #f ( ) { #a #a : : return } : #f ( ) ;", "#a", 1,
                        "The variable #a is declared twice in the function #f.", "must all be different"),
                arguments(": void #f ( #p #p ) { : : return } : #f ( 1 2 ) ;", "#p", 1,
                        "The function #f has two parameters called #p.", "need different names"),
                arguments(": void #f ( #p ) { #p : : return } : #f ( 1 ) ;", "#p", 1,
                        "The local variable #p masks the parameter #p of the function #f.", "Rename the local variable"),
                arguments(": void #f ( ) { : : return } void #f ( ) { : : return } : #f ( ) ;", "#f", 1,
                        "The function #f is declared twice in the function list of the main program.",
                        "even when their parameters differ"),
                arguments(": void #f ( ) { : void #h ( ) { : : return } num #h ( ) { : : return ( 1 ) } : #h ( ) ; return } "
                        + ": #f ( ) ;", "#h", 1, "The function #h is declared twice in the function list of the function #f.",
                        "line 1, column 24"),
                arguments(": void #f ( ) { : : return } : #f = 1 ;", "#f", 1, "The variable #f is not declared.",
                        "is the name of a function"),
                arguments("#v : : #v ( ) ;", "#v", 1, "The function #v is not declared.",
                        "is a variable and a variable cannot be called"));
    }

    @ParameterizedTest
    @MethodSource("faultyPrograms")
    void brokenRulesAreReportedAtTheOffendingName(String source, String name, int occurrence, String message, String hint) {
        SemanticException e = rejected(source);
        assertEquals(1, e.getErrors().size(), e.getErrors().toString());
        SemanticError error = e.getErrors().get(0);
        Analysis analysis = new Analysis(source);
        Token token = analysis.tokens.get(analysis.token(name, occurrence));
        assertEquals(token.line(), error.getLine());
        assertEquals(token.column(), error.getColumn());
        assertEquals(name.length(), error.getLength());
        assertEquals(message, error.getMessage());
        assertTrue(error.getHint().contains(hint), error.getHint());
    }

    @Test
    void aMissingNameIsReportedOncePerScope() {
        SemanticException e = rejected(": void #f ( ) { : : #x = 1 ; #x = 2 ; return } : #x = 3 ; #x = 4 ; #f ( ) ;");
        assertEquals(2, e.getErrors().size(), e.getErrors().toString());
    }

    @Test
    void allErrorsAreReportedInTheOrderOfTheFile() {
        SemanticException e = rejected("#a #b #a :\nvoid #f ( #p #p ) { #q #p : : return }\n"
                + "void #f ( ) { : : return }\n: #c = 1 ;");
        List<String> places = new ArrayList<>();
        for (SemanticError error : e.getErrors()) {
            places.add(error.getLine() + ":" + error.getColumn());
        }
        assertEquals(Arrays.asList("1:7", "2:14", "2:24", "3:6", "4:3"), places);
    }

    @Test
    void levelsFollowTheNestingOfTheFunctions() {
        Analysis analysis = new Analysis("#x : void #f ( #p ) { : void #g ( ) { : : nop ; return } : #g ( ) ; return } : nop ;");
        SymbolTable table = analysis.names();
        assertEquals(0, table.levelOf(analysis.tree.getRoot().getId()));
        List<Node> definitions = analysis.all("F_TYPE");
        assertEquals(1, table.levelOf(definitions.get(0).getId()));
        assertEquals(2, table.levelOf(definitions.get(1).getId()));
        List<Node> algorithms = analysis.all("INSTR");
        assertEquals(2, table.levelOf(algorithms.get(0).getId()), "nop inside #g");
        assertEquals(1, table.levelOf(algorithms.get(1).getId()), "the call inside #f");
        assertEquals(0, table.levelOf(algorithms.get(2).getId()), "nop of the main program");
        assertEquals(1, analysis.symbol(table, "#p", 0).getLevel());
        assertEquals(2, analysis.symbol(table, "#g", 0).getLevel());
        assertEquals(-1, table.levelOf(10000));
    }

    @Test
    void internalNamesAreUnique() {
        SymbolTable table = new Analysis(A21).names();
        List<String> names = table.getSymbols().stream().map(Symbol::getInternalName).collect(Collectors.toList());
        assertEquals(names.size(), new HashSet<>(names).size(), names.toString());
    }

    @Test
    void textOfTheTableListsEveryName() {
        Analysis analysis = new Analysis("#x\n:\nnum #inc ( #n ) { : : return ( add ( #n 1 ) ) }\n:\n#x = #inc ( 5 ) ;\n");
        String text = analysis.types().toText(analysis.leaves);
        assertTrue(text.startsWith("Symbol table\n\nAll numbers of the program are integers.\n"), text);
        assertTrue(text.contains("v1_x           #x        variable      numeric  0      main    1:1 (3)      5:1 ("), text);
        assertTrue(text.contains("f1_inc         #inc      num function  numeric  1      main    3:5 ("), text);
        assertTrue(text.contains("v2_n           #n        parameter     numeric  1      f1_inc  3:12 ("), text);
    }
}