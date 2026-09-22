package za.ac.up.cos341.lexer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class Lexer {

    // TODO: copy these three patterns VERBATIM from the Part-1 grammar spec.
    // Known constraints (do not reinvent, transcribe):
    //   NUM    -> no leading zeros except the single digit 0; no trailing zeros after the decimal point
    //   NAME   -> starts with '#'
    //   STRING -> quote-delimited
    private static final String NUM_REGEX = "";     // TODO
    private static final String NAME_REGEX = "";    // TODO
    private static final String STRING_REGEX = "";  // TODO

    private static final Map<String, TokenType> KEYWORDS = Map.ofEntries(
            Map.entry("void", TokenType.VOID),
            Map.entry("num", TokenType.NUM_KW),
            Map.entry("return", TokenType.RETURN),
            Map.entry("print", TokenType.PRINT),
            Map.entry("nop", TokenType.NOP),
            Map.entry("comment", TokenType.COMMENT),
            Map.entry("if", TokenType.IF),
            Map.entry("then", TokenType.THEN),
            Map.entry("else", TokenType.ELSE),
            Map.entry("while", TokenType.WHILE),
            Map.entry("until", TokenType.UNTIL),
            Map.entry("do", TokenType.DO),
            Map.entry("not", TokenType.NOT),
            Map.entry("and", TokenType.AND),
            Map.entry("or", TokenType.OR),
            Map.entry("eq", TokenType.EQ),
            Map.entry("larger", TokenType.LARGER),
            Map.entry("lesser", TokenType.LESSER),
            Map.entry("add", TokenType.ADD),
            Map.entry("sub", TokenType.SUB),
            Map.entry("mul", TokenType.MUL),
            Map.entry("div", TokenType.DIV),
            Map.entry("mod", TokenType.MOD),
            Map.entry("neg", TokenType.NEG));

    private static final Map<String, TokenType> SYMBOLS = Map.ofEntries(
            Map.entry(":", TokenType.COLON),
            Map.entry(";", TokenType.SEMI),
            Map.entry("(", TokenType.LPAREN),
            Map.entry(")", TokenType.RPAREN),
            Map.entry("{", TokenType.LBRACE),
            Map.entry("}", TokenType.RBRACE),
            Map.entry("=", TokenType.ASSIGN));

    public List<Token> tokenize(Path path) throws IOException {
        return tokenize(Files.readString(path));
    }

    public List<Token> tokenize(String source) {
        List<Token> tokens = new ArrayList<>();
        // TODO: scan `source`, splitting on ASCII 32 and 13 (blank-space terminated).
        //       Track the current line number as you go and stamp each token with it.
        //       For every non-empty chunk, call classify(chunk, line) and add the result.
        //       Do NOT emit a '$' token -- the parser injects that itself.
        return tokens;
    }

    private Token classify(String chunk, int line) {
        TokenType kw = KEYWORDS.get(chunk);
        if (kw != null) {
            return new Token(kw, chunk, line);
        }
        TokenType sym = SYMBOLS.get(chunk);
        if (sym != null) {
            return new Token(sym, chunk, line);
        }
        // TODO: test the three category regexes in the order your spec prioritises:
        //       if chunk matches NUM    -> new Token(TokenType.NUM, chunk, line)
        //       if chunk matches NAME   -> new Token(TokenType.NAME, chunk, line)
        //       if chunk matches STRING -> new Token(TokenType.STRING, chunk, line)
        //       else -> throw new LexicalException(chunk, line);
        throw new LexicalException(chunk, line);
    }
}
