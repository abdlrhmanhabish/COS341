package za.ac.up.cos341.lexer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LexerTest {

    private final Lexer lexer = new Lexer();

    @Test
    void keywordIsNotAName() {
        List<Token> tokens = lexer.tokenize("while");
        assertEquals(1, tokens.size());
        assertEquals(TokenType.WHILE, tokens.get(0).type());
        // TODO: extend once tokenize() is implemented
    }

    @Test
    void symbolsAreClassified() {
        List<Token> tokens = lexer.tokenize(": ; ( ) { } =");
        assertEquals(7, tokens.size());
        // TODO: assert each type in order
    }

    @Test
    void lineNumbersAreTracked() {
        List<Token> tokens = lexer.tokenize("print\nnop");
        assertEquals(1, tokens.get(0).line());
        assertEquals(2, tokens.get(1).line());
    }

    @Test
    void invalidTokenReportsLineNumber() {
        // a chunk that matches no keyword, symbol, or category regex must fail (not crash silently)
        LexicalException ex = assertThrows(LexicalException.class, () -> lexer.tokenize("@@@"));
        assertEquals(1, ex.getLine());
    }
}
