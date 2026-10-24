package za.ac.up.cos341.semantics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import za.ac.up.cos341.parser.RandomProgram;
import za.ac.up.cos341.tree.Node;
import za.ac.up.cos341.tree.NodeKind;

// The random programs of ValidProgram keep every rule 
// The analysis must accept all of them and must link every name to the entity meant by the generator 
// damaging one name or one call must give exactly one error in the place of damage
class GeneratedSemanticsTest {

    private static final int PROGRAMS = 500;
    private static SemanticException analyseDamaged(List<String> tokens, String original) {
        try {
            new Analysis(String.join(" ", tokens)).types();
        } catch (SemanticException e) {
            return e;
        }
        fail("the damaged program was accepted\n" + String.join(" ", tokens) + "\noriginal:\n" + original);
        return null;
    }

    //the column of the k-th token when the tokens are written at one line with single spaces
    private static int column(List<String> tokens, int token) {
        int column = 1;
        for (int i = 0; i < token; i++) {
            column += tokens.get(i).length() + 1;
        }
        return column;
    }

    @Test
    void everyNameIsLinkedToTheIntendedDeclaration() {
        for (long seed = 0; seed < PROGRAMS; seed++) {
            ValidProgram program = ValidProgram.generate(seed);
            Analysis analysis = new Analysis(program.getText());
            SymbolTable table;
            try {
                table = analysis.types();
            } catch (SemanticException e) {
                fail("seed " + seed + " was rejected: " + e.getErrors() + "\n" + program.getText());
                return;
            }
            List<Node> leaves = analysis.leaves();
            assertEquals(program.getTokens().size(), leaves.size());
            Map<Integer, Symbol> meaning = new HashMap<>();
            Map<Symbol, Integer> entities = new IdentityHashMap<>();
            for (int i = 0; i < leaves.size(); i++) {
                Symbol symbol = table.symbolOf(leaves.get(i).getId());
                int entity = program.entityOf(i);

                if (entity < 0) {
                    assertNull(symbol, "seed " + seed + " token " + i);
                    continue;
                }
                assertNotNull(symbol, "seed " + seed + " token " + i);
                Symbol earlier = meaning.putIfAbsent(entity, symbol);
                assertSame(earlier == null ? symbol : earlier, symbol, "seed " + seed + " token " + i);
                Integer other = entities.putIfAbsent(symbol, entity);
                assertEquals(other == null ? entity : other, entity, "two entities share " + symbol);
            }
            for (Node node : Trees.preOrder(analysis.tree)) {
                if (node.getKind() != NodeKind.LEAF) {
                    assertTrue(table.typeOf(node.getId()) != Type.UNKNOWN, "seed " + seed + " " + node);
                }
            }
        }
    }

    @Test
    void anUndeclaredVariableIsFoundAtItsPlace() {
        Random random = new Random(41);
        for (long seed = 0; seed < PROGRAMS; seed++) {
            ValidProgram program = ValidProgram.generate(seed);
            List<Integer> uses = program.indexesOf(ValidProgram.Role.VARIABLE_USE);
            if (uses.isEmpty()) {
                continue;
            }
            int target = uses.get(random.nextInt(uses.size()));
            List<String> tokens = new ArrayList<>(program.getTokens());
            tokens.set(target, "#unknown");
            SemanticException e = analyseDamaged(tokens, program.getText());
            assertEquals(SemanticException.Kind.NAMING, e.getKind());
            assertEquals(1, e.getErrors().size(), e.getErrors().toString());
            assertEquals(column(tokens, target), e.getErrors().get(0).getColumn());
            assertEquals("The variable #unknown is not declared.", e.getErrors().get(0).getMessage());
        }
    }

    @Test
    void aCallOfAnUnknownFunctionIsFoundAtItsPlace() {
        Random random = new Random(42);
        int checked = 0;
        for (long seed = 0; seed < PROGRAMS; seed++) {
            ValidProgram program = ValidProgram.generate(seed);
            List<Integer> calls = program.indexesOf(ValidProgram.Role.FUNCTION_USE);
            if (calls.isEmpty()) {
                continue;
            }
            int target = calls.get(random.nextInt(calls.size()));
            List<String> tokens = new ArrayList<>(program.getTokens());
            tokens.set(target, "#nowhere");
            SemanticException e = analyseDamaged(tokens, program.getText());
            assertEquals(SemanticException.Kind.NAMING, e.getKind());
            assertEquals(1, e.getErrors().size(), e.getErrors().toString());
            assertEquals(column(tokens, target), e.getErrors().get(0).getColumn());
            checked++;
        }
        assertTrue(checked > PROGRAMS / 4, "only " + checked + " programs had calls");
    }

    @Test
    void anExtraArgumentIsATypeErrorAtTheCall() {
        Random random = new Random(43);
        for (long seed = 0; seed < PROGRAMS; seed++) {
            ValidProgram program = ValidProgram.generate(seed);
            List<Integer> calls = program.indexesOf(ValidProgram.Role.FUNCTION_USE);
            if (calls.isEmpty()) {
                continue;
            }
            int target = calls.get(random.nextInt(calls.size()));
            List<String> tokens = new ArrayList<>(program.getTokens());
            tokens.add(target + 2, "1");
            SemanticException e = analyseDamaged(tokens, program.getText());
            assertEquals(SemanticException.Kind.TYPE, e.getKind());
            assertEquals(1, e.getErrors().size(), e.getErrors().toString());
            assertEquals(column(tokens, target), e.getErrors().get(0).getColumn());
        }
    }

    // The programs of the parser test are only syntactically correct and a lot of them break some rule
    //The analysis must give a symbol table or a SemanticException for each break but never crash
    @Test
    void programsThatOnlyParseNeverCrashTheAnalysis() {
        int accepted = 0;
        for (long seed = 0; seed < 1000; seed++) {
            String source = RandomProgram.generate(seed);
            try {
                new Analysis(source).types();
                accepted++;
            } catch (SemanticException e) {
                assertTrue(!e.getErrors().isEmpty(), source);
                for (SemanticError error : e.getErrors()) {
                    assertTrue(error.getLine() >= 1 && error.getColumn() >= 1, source);
                    assertTrue(!error.getHint().trim().isEmpty(), source);
                }
            }
        }
        assertTrue(accepted > 0, "no random program passed the analysis");
    }

    // turning one add or sub or mul into the operator that the program does not use yet must hit the crude rule
    @Test
    void mixingModAndDivIsFoundWhereBothHaveAppeared() {
        Random random = new Random(44);
        int checked = 0;
        for (long seed = 0; seed < PROGRAMS; seed++) {
            ValidProgram program = ValidProgram.generate(seed);
            String present = program.getNumbers() == ValidProgram.Numbers.MOD ? "mod" : "div";
            String missing = present.equals("mod") ? "div" : "mod";
            List<String> tokens = new ArrayList<>(program.getTokens());
            List<Integer> operators = new ArrayList<>();
            for (int i = 0; i < tokens.size(); i++) {
                String token = tokens.get(i);
                if (token.equals("add") || token.equals("sub") || token.equals("mul")) {
                    operators.add(i);
                }
            }
            if (program.getNumbers() == ValidProgram.Numbers.NEITHER || !tokens.contains(present)
                    || operators.isEmpty()) {
                continue;
            }
            Collections.shuffle(operators, random);
            tokens.set(operators.get(0), missing);
            int conflict = -1;
            boolean seenMod = false;
            boolean seenDiv = false;
            for (int i = 0; i < tokens.size() && conflict < 0; i++) {
                seenMod |= tokens.get(i).equals("mod");
                seenDiv |= tokens.get(i).equals("div");
                if (seenMod && seenDiv) {
                    conflict = i;
                }
            }
            
            SemanticException e = analyseDamaged(tokens, program.getText());
            assertEquals(SemanticException.Kind.TYPE, e.getKind());
            assertEquals(1, e.getErrors().size(), e.getErrors().toString());
            assertEquals(TypeAnalyser.FLOAT_INTEGER_CONFLICT, e.getErrors().get(0).getMessage());
            assertEquals(column(tokens, conflict), e.getErrors().get(0).getColumn());
            checked++;
        }
        assertTrue(checked > PROGRAMS / 10, "only " + checked + " programs were checked");
    }
}