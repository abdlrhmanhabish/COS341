package za.ac.up.cos341.lexer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

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
        assertEquals(Arrays.asList(TokenType.COLON, TokenType.SEMI, TokenType.LPAREN, TokenType.RPAREN,
                        TokenType.LBRACE, TokenType.RBRACE, TokenType.ASSIGN),
                tokens.stream().map(Token::type).collect(Collectors.toList()));
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
        assertDoesNotThrow(() -> lexer.tokenize(Paths.get("tests", "lexer", file)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid_01_leading_zero.txt", "invalid_02_trailing_zero.txt",
            "invalid_03_bad_symbol.txt", "invalid_04_name_without_hash.txt"})
    void invalidFilesFail(String file) throws IOException {
        Path p = Paths.get("tests", "lexer", file);
        assertThrows(LexicalException.class, () -> lexer.tokenize(p));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-5", "-0.5", "-12.75", "-100", "-0.001"})
    void negativeNumbersAreNumbers(String n) {
        assertEquals(TokenType.NUM, lexer.tokenize(n).get(0).type());
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0", "-", "--5", "-05", "-1.0", "+5", "1.2.3", "0.0"})
    void badSignedOrMalformedNumbersFail(String n) {
        assertThrows(LexicalException.class, () -> lexer.tokenize(n));
    }

    @ParameterizedTest
    @ValueSource(strings = {"#", "#abc123", "#9"})
    void namesUseLowercaseLettersAndDigits(String name) {
        assertEquals(TokenType.NAME, lexer.tokenize(name).get(0).type());
    }

    @ParameterizedTest
    @ValueSource(strings = {"#ABC", "#a_b", "#x!", "#Count"})
    void namesWithOtherCharactersFail(String name) {
        assertThrows(LexicalException.class, () -> lexer.tokenize(name));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"\"", "\"a,b.c:d-e?f!\"", "\"x1\""})
    void stringsWithAllowedCharacters(String text) {
        assertEquals(TokenType.STRING, lexer.tokenize(text).get(0).type());
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"Hello\"", "\"a;b\"", "\"a", "\"a\"b\"", "\"tab\\there\""})
    void stringsWithOtherCharactersFail(String text) {
        assertThrows(LexicalException.class, () -> lexer.tokenize(text));
    }

    @Test
    void tabsSeparateTokens() {
        assertEquals(Arrays.asList(TokenType.PRINT, TokenType.STRING, TokenType.SEMI),
                lexer.tokenize("print\t\"hi\"\t;").stream().map(Token::type).collect(Collectors.toList()));
    }

    @Test
    void carriageReturnAloneEndsALine() {
        List<Token> tokens = lexer.tokenize("nop\r;\r\rnop");
        assertEquals(Arrays.asList(1, 2, 4), tokens.stream().map(Token::line).collect(Collectors.toList()));
    }

    @Test
    void columnsAreTracked() {
        List<Token> tokens = lexer.tokenize("  #x = 5 ;\n\tnop");
        assertEquals(Arrays.asList(3, 6, 8, 10, 2), tokens.stream().map(Token::column).collect(Collectors.toList()));
    }

    @Test
    void byteOrderMarkIsIgnored(@TempDir Path folder) throws IOException {
        Path file = folder.resolve("SPL.txt");
        byte[] text = ": : nop ;".getBytes(StandardCharsets.US_ASCII);
        byte[] withMark = new byte[text.length + 3];
        withMark[0] = (byte) 0xEF;
        withMark[1] = (byte) 0xBB;
        withMark[2] = (byte) 0xBF;
        System.arraycopy(text, 0, withMark, 3, text.length);
        Files.write(file, withMark);
        assertEquals(4, lexer.tokenize(file).size());
    }

    @Test
    void nonAsciiCharactersFailWithAHint() {
        LexicalException ex = assertThrows(LexicalException.class, () -> lexer.tokenize("print \u201Chi\u201D ;"));
        assertTrue(ex.getHint().contains("not plain ASCII"), ex.getHint());
    }

    @Test
    void errorsCarryAHintAndAColumn() {
        assertHint("#x = 007 ;", "Write 7 instead of 007");
        assertHint("#x = 1.50 ;", "Write 1.5 instead of 1.50");
        assertHint("#x = -0 ;", "Write 0 instead of -0");
        assertHint("#x = 1 + 2 ;", "add ( #a #b )");
        assertHint("print(", "written together with other characters");
        assertHint("Print", "Write print instead of Print");
        assertHint("x", "Write #x");
        assertHint("#AB", "Write #ab instead of #AB");
        assertHint("#a_b", "'_' is not allowed");
        assertHint("\"Hi\"", "'H' is not allowed in a string");
        assertHint("\"hello world\"", "may not contain spaces");
        assertHint("$", "must not appear in the file");
        LexicalException ex = assertThrows(LexicalException.class, () -> lexer.tokenize("nop ;\n  #x = @ ;"));
        assertEquals(2, ex.getLine());
        assertEquals(8, ex.getColumn());
    }

    private void assertHint(String source, String expectedPart) {
        LexicalException ex = assertThrows(LexicalException.class, () -> lexer.tokenize(source));
        assertTrue(ex.getHint().contains(expectedPart), source + " gave the hint: " + ex.getHint());
    }
}
