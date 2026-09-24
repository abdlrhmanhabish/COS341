package za.ac.up.cos341.lexer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class Lexer {

    // NUM : no leading zeros except the single digit 0; no trailing zeros after the
    // decimal point
    // NAME : starts with '#'
    // STRING : quote-delimited
    private static final Pattern NUM = Pattern.compile(
            "0|[1-9][0-9]*|0\\.[0-9]*[1-9]|[1-9][0-9]*\\.[0-9]*[1-9]");
    private static final Pattern NAME = Pattern.compile("#[a-zA-Z0-9_]*");
    private static final Pattern STRING = Pattern.compile("\"[^\"]*\"");

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
        return tokenize(Files.readString(path, StandardCharsets.US_ASCII));
    }

    public List<Token> tokenize(String source) {
        List<Token> tokens = new ArrayList<>();
        StringBuilder chunk = new StringBuilder();
        int line = 1;
        int chunkLine = 1;

        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == ' ' || c == '\r' || c == '\n') {
                if (chunk.length() > 0) {
                    tokens.add(classify(chunk.toString(), chunkLine));
                    chunk.setLength(0);
                }
                if (c == '\n') {
                    line++;
                }
            } else {
                if (chunk.length() == 0) {
                    chunkLine = line;
                }
                chunk.append(c);
            }
        }
        if (chunk.length() > 0) {
            tokens.add(classify(chunk.toString(), chunkLine));
        }
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
        if (NUM.matcher(chunk).matches()) {
            return new Token(TokenType.NUM, chunk, line);
        }
        if (NAME.matcher(chunk).matches()) {
            return new Token(TokenType.NAME, chunk, line);
        }
        if (STRING.matcher(chunk).matches()) {
            return new Token(TokenType.STRING, chunk, line);
        }
        throw new LexicalException(chunk, line);
    }
}
