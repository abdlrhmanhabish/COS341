package za.ac.up.cos341.lexer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LexerTest {

    private final Lexer lexer = new Lexer();

    @Test
    void keywordIsNotAName() {
        List<Token> tokens = lexer.tokenize("while");
        assertEquals(1, tokens.size());
        assertEquals(TokenType.WHILE, tokens.get(0).type());
    }

    @Test
    void numKeywordDiffersFromNumLiteral() {
        List<Token> tokens = lexer.tokenize("num 42");
        assertEquals(TokenType.NUM_KW, tokens.get(0).type());
        assertEquals(TokenType.NUM, tokens.get(1).type());
    }

    @Test
    void symbolsAreClassified() {
        List<Token> tokens = lexer.tokenize(": ; ( ) { } =");
        assertEquals(List.of(TokenType.COLON, TokenType.SEMI, TokenType.LPAREN, TokenType.RPAREN,
                        TokenType.LBRACE, TokenType.RBRACE, TokenType.ASSIGN),
                tokens.stream().map(Token::type).toList());
    }

    @Test
    void categoriesAreClassified() {
        List<Token> tokens = lexer.tokenize("#counter 12 \"hi\"");
        assertEquals(TokenType.NAME, tokens.get(0).type());
        assertEquals("#counter", tokens.get(0).value());
        assertEquals(TokenType.NUM, tokens.get(1).type());
        assertEquals(TokenType.STRING, tokens.get(2).type());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "7", "120", "0.5", "3.25", "10.01"})
    void validNumbers(String n) {
        assertEquals(TokenType.NUM, lexer.tokenize(n).get(0).type());
    }

    @ParameterizedTest
    @ValueSource(strings = {"00", "007", "1.50", "0.0", "1.", ".5"})
    void invalidNumbers(String n) {
        assertThrows(LexicalException.class, () -> lexer.tokenize(n));
    }

    @Test
    void lineNumbersAreTracked() {
        List<Token> tokens = lexer.tokenize("print\nnop\r\n\n;");
        assertEquals(1, tokens.get(0).line());
        assertEquals(2, tokens.get(1).line());
        assertEquals(4, tokens.get(2).line());
    }

    @Test
    void emptyInputGivesNoTokensAndNoDollar() {
        assertTrue(lexer.tokenize("  \r\n ").isEmpty());
    }

    @Test
    void invalidTokenReportsLineNumber() {
        LexicalException ex = assertThrows(LexicalException.class, () -> lexer.tokenize("nop ;\n@@@"));
        assertEquals(2, ex.getLine());
        assertEquals("@@@", ex.getOffendingText());
    }

    @ParameterizedTest
    @ValueSource(strings = {"valid_01_minimal.txt", "valid_02_decls_assign.txt", "valid_03_function.txt",
            "valid_04_control_flow.txt", "valid_05_decimals.txt"})
    void validFilesTokenize(String file) {
        assertDoesNotThrow(() -> lexer.tokenize(Path.of("tests", "lexer", file)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid_01_leading_zero.txt", "invalid_02_trailing_zero.txt",
            "invalid_03_bad_symbol.txt", "invalid_04_name_without_hash.txt"})
    void invalidFilesFail(String file) throws IOException {
        Path p = Path.of("tests", "lexer", file);
        assertThrows(LexicalException.class, () -> lexer.tokenize(p));
    }
}
