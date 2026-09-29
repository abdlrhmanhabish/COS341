package za.ac.up.cos341.parser;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import za.ac.up.cos341.lexer.Lexer;
import za.ac.up.cos341.lexer.Token;
import za.ac.up.cos341.tree.SyntaxTree;

class GeneratedProgramTest {

    private static final int PROGRAMS = 1000;

    private static final List<String> SPARE_TOKENS = Arrays.asList(":", ";", "(", ")", "{", "}", "=", "print", "nop",
            "comment", "if", "then", "else", "do", "while", "until", "return", "void", "num", "#a", "7", "\"s\"",
            "add", "neg", "eq", "not", "and");

    @Test
    void everyGeneratedProgramIsAccepted() {
        for (long seed = 0; seed < PROGRAMS; seed++) {
            String source = RandomProgram.generate(seed);
            List<Token> tokens = new Lexer().tokenize(source);
            try {
                SyntaxTree tree = new Parser(tokens).parse();
                TreeCheck.assertDerivationTree(tree, tokens, "seed " + seed);
            } catch (SyntaxError e) {
                fail("seed " + seed + " was rejected: " + e.getMessage() + "\n" + source);
            }
        }
    }

    // One token is removed or added or replaced. The result may still be a correct program
    @Test
    void damagedProgramsGiveATreeOrASyntaxError() {
        Random random = new Random(341);
        int rejected = 0;
        for (long seed = 0; seed < PROGRAMS; seed++) {
            List<String> words = new ArrayList<>(Arrays.asList(RandomProgram.generate(seed).trim().split("\\s+")));
            String spare = SPARE_TOKENS.get(random.nextInt(SPARE_TOKENS.size()));
            switch (random.nextInt(3)) {
                case 0:
                    words.remove(random.nextInt(words.size()));
                    break;
                case 1:
                    words.add(random.nextInt(words.size() + 1), spare);
                    break;
                default:
                    words.set(random.nextInt(words.size()), spare);
            }
            String source = String.join(" ", words);
            List<Token> tokens = new Lexer().tokenize(source);
            try {
                TreeCheck.assertDerivationTree(new Parser(tokens).parse(), tokens, source);
            } catch (SyntaxError e) {
                rejected++;
                assertTrue(e.getLine() >= 1 && e.getColumn() >= 1, source);
                assertFalse(e.getHint().trim().isEmpty(), source);
            }
        }
        assertTrue(rejected > PROGRAMS / 2, "most damaged programs should be rejected but only " + rejected + " were");
    }
}