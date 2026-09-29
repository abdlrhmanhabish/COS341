package za.ac.up.cos341.lexer;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class Lexer {

    // The regular expressions of the syntax specification. The minus sign of NUM is the ASCII '-'
    // NUM : no leading zeros except the single digit 0 and no trailing zeros after the decimal point
    // NAME : '#' followed by lowercase letters and digits
    // STRING : lowercase letters, digits and , . : - ? ! between double quotes
    private static final Pattern NUM = Pattern.compile(
            "0|-?0\\.[0-9]*[1-9]|-?[1-9][0-9]*(\\.[0-9]*[1-9])?");
    private static final Pattern NAME = Pattern.compile("#[a-z0-9]*");
    private static final Pattern STRING = Pattern.compile("\"[a-z0-9,.:?!-]*\"");

    private static final Map<String, TokenType> KEYWORDS = new HashMap<>();
    private static final Map<String, TokenType> SYMBOLS = new HashMap<>();
    // these are operators people know from other languages and the SPL way of writing them
    private static final Map<String, String> FOREIGN_OPERATORS = new HashMap<>();

    static {
        KEYWORDS.put("void", TokenType.VOID);
        KEYWORDS.put("num", TokenType.NUM_KW);
        KEYWORDS.put("return", TokenType.RETURN);
        KEYWORDS.put("print", TokenType.PRINT);
        KEYWORDS.put("nop", TokenType.NOP);
        KEYWORDS.put("comment", TokenType.COMMENT);
        KEYWORDS.put("if", TokenType.IF);
        KEYWORDS.put("then", TokenType.THEN);
        KEYWORDS.put("else", TokenType.ELSE);
        KEYWORDS.put("while", TokenType.WHILE);
        KEYWORDS.put("until", TokenType.UNTIL);
        KEYWORDS.put("do", TokenType.DO);
        KEYWORDS.put("not", TokenType.NOT);
        KEYWORDS.put("and", TokenType.AND);
        KEYWORDS.put("or", TokenType.OR);
        KEYWORDS.put("eq", TokenType.EQ);
        KEYWORDS.put("larger", TokenType.LARGER);
        KEYWORDS.put("lesser", TokenType.LESSER);
        KEYWORDS.put("add", TokenType.ADD);
        KEYWORDS.put("sub", TokenType.SUB);
        KEYWORDS.put("mul", TokenType.MUL);
        KEYWORDS.put("div", TokenType.DIV);
        KEYWORDS.put("mod", TokenType.MOD);
        KEYWORDS.put("neg", TokenType.NEG);

        SYMBOLS.put(":", TokenType.COLON);
        SYMBOLS.put(";", TokenType.SEMI);
        SYMBOLS.put("(", TokenType.LPAREN);
        SYMBOLS.put(")", TokenType.RPAREN);
        SYMBOLS.put("{", TokenType.LBRACE);
        SYMBOLS.put("}", TokenType.RBRACE);
        SYMBOLS.put("=", TokenType.ASSIGN);

        FOREIGN_OPERATORS.put("+", "SPL writes an addition as  add ( #a #b ) .");
        FOREIGN_OPERATORS.put("-", "A minus sign belongs directly in front of a number as in -5. "
                + "Subtraction is written as  sub ( #a #b )  and negation as  neg ( #a ) .");
        FOREIGN_OPERATORS.put("*", "SPL writes a multiplication as  mul ( #a #b ) .");
        FOREIGN_OPERATORS.put("/", "SPL writes a division as  div ( #a #b ) .");
        FOREIGN_OPERATORS.put("%", "SPL writes the remainder as  mod ( #a #b ) .");
        FOREIGN_OPERATORS.put("<", "Use  lesser ( #a #b )  to test whether #a is smaller than #b.");
        FOREIGN_OPERATORS.put(">", "Use  larger ( #a #b )  to test whether #a is bigger than #b.");
        FOREIGN_OPERATORS.put("==", "Equality is tested with  eq ( #a #b ) . A single '=' only appears in assignments.");
        FOREIGN_OPERATORS.put("<=", "SPL has no '<='. The condition  not ( larger ( #a #b ) )  means the same.");
        FOREIGN_OPERATORS.put(">=", "SPL has no '>='. The condition  not ( lesser ( #a #b ) )  means the same.");
        FOREIGN_OPERATORS.put("!=", "SPL has no '!='. The condition  not ( eq ( #a #b ) )  means the same.");
        FOREIGN_OPERATORS.put("!", "A condition is negated with  not ( BOOL ) .");
        FOREIGN_OPERATORS.put("&&", "Two conditions are combined with  and ( BOOL BOOL ) .");
        FOREIGN_OPERATORS.put("||", "Two conditions are combined with  or ( BOOL BOOL ) .");
        FOREIGN_OPERATORS.put("$", "The $ only marks the end of the input in the grammar. It must not appear in the file.");
    }

    public List<Token> tokenize(Path path) throws IOException {
        return tokenize(readSource(path));
    }

    // read the file byte by byte so that no input can fail to decode
    public static String readSource(Path path) throws IOException {
        String text = new String(Files.readAllBytes(path), StandardCharsets.ISO_8859_1);
        return text.startsWith("\u00EF\u00BB\u00BF") ? text.substring(3) : text;
    }

    public List<Token> tokenize(String source) {
        List<Token> tokens = new ArrayList<>();
        int line = 1;
        int column = 1;
        int start = -1;
        int startLine = 0;
        int startColumn = 0;
        // one extra round with a blank at the end. we want the last token to be closed as well
        for (int i = 0; i <= source.length(); i++) {
            char c = i < source.length() ? source.charAt(i) : ' ';
            if (isBlank(c)) {
                if (start >= 0) {
                    tokens.add(classify(source.substring(start, i), startLine, startColumn));
                    start = -1;
                }
            } else if (start < 0) {
                start = i;
                startLine = line;
                startColumn = column;
            }
            boolean lineEnds = c == '\n' || (c == '\r' && (i + 1 >= source.length() || source.charAt(i + 1) != '\n'));
            if (lineEnds) {
                line++;
                column = 1;
            } else {
                column++;
            }
        }
        return tokens;
    }

    // ASCII 32 and 13 are the blank spaces. Line feeds and tabs count as well because files written on Linux or indented with tabs contain them
    private static boolean isBlank(char c) {
        return c == ' ' || c == '\r' || c == '\n' || c == '\t';
    }

    private Token classify(String chunk, int line, int column) {
        TokenType kw = KEYWORDS.get(chunk);
        if (kw != null) {
            return new Token(kw, chunk, line, column);
        }
        TokenType sym = SYMBOLS.get(chunk);
        if (sym != null) {
            return new Token(sym, chunk, line, column);
        }
        if (NUM.matcher(chunk).matches()) {
            return new Token(TokenType.NUM, chunk, line, column);
        }
        if (NAME.matcher(chunk).matches()) {
            return new Token(TokenType.NAME, chunk, line, column);
        }
        if (STRING.matcher(chunk).matches()) {
            return new Token(TokenType.STRING, chunk, line, column);
        }
        throw new LexicalException(chunk, line, column, hintFor(chunk));
    }

    private static String hintFor(String chunk) {
        for (int i = 0; i < chunk.length(); i++) {
            char c = chunk.charAt(i);
            if (c < 32 || c > 126) {
                return "The file contains a character with code " + (int) c + " which is not plain ASCII. "
                        + "Retype this part of the line. Curly quotes copied from a word processor are a common cause.";
            }
        }
        if (chunk.charAt(0) == '"') {
            return stringHint(chunk);
        }
        String operator = FOREIGN_OPERATORS.get(chunk);
        if (operator != null) {
            return operator;
        }
        for (int i = 0; i < chunk.length(); i++) {
            if (":;(){}=\"".indexOf(chunk.charAt(i)) >= 0) {
                return "Every token must be followed by a space or a line break. Here the '"
                        + chunk.charAt(i) + "' is written together with other characters.";
            }
        }
        char first = chunk.charAt(0);
        if (first == '#') {
            return nameHint(chunk);
        }
        if (first == '-' || first == '.' || isDigit(first)) {
            return numberHint(chunk);
        }
        String lower = chunk.toLowerCase(Locale.ROOT);
        if (KEYWORDS.containsKey(lower)) {
            return "Keywords are written in lowercase. Write " + lower + " instead of " + chunk + ".";
        }
        if (chunk.matches("[A-Za-z][A-Za-z0-9]*")) {
            return "Names of variables and functions start with '#'. Write #" + lower + " if this is meant to be a name.";
        }
        for (int i = 0; i < chunk.length(); i++) {
            char c = chunk.charAt(i);
            if (!isLetterOrDigit(c) && c != '#') {
                return "The character '" + c + "' is not used in SPL.";
            }
        }
        return "This is not an SPL token. Check its spelling and the spaces around it.";
    }

    private static String stringHint(String chunk) {
        int close = chunk.indexOf('"', 1);
        if (close < 0) {
            return "A string ends with '\"' on the same line and may not contain spaces.";
        }
        if (close < chunk.length() - 1) {
            return "Every token must be followed by a space or a line break. Put a space after the closing '\"'.";
        }
        for (int i = 1; i < close; i++) {
            char c = chunk.charAt(i);
            if (!isLowerOrDigit(c) && ",.:-?!".indexOf(c) < 0) {
                return "The character '" + c + "' is not allowed in a string. "
                        + "Strings may only contain lowercase letters and digits and the marks , . : - ? !";
            }
        }
        return "This is not a valid SPL string.";
    }

    private static String nameHint(String chunk) {
        String lower = chunk.toLowerCase(Locale.ROOT);
        if (NAME.matcher(lower).matches()) {
            return "Names use lowercase letters and digits only. Write " + lower + " instead of " + chunk + ".";
        }
        for (int i = 1; i < chunk.length(); i++) {
            char c = chunk.charAt(i);
            if (!isLetterOrDigit(c)) {
                return "After '#' a name may only contain lowercase letters and digits. The character '"
                        + c + "' is not allowed.";
            }
        }
        return "After '#' a name may only contain lowercase letters and digits.";
    }

    private static String numberHint(String chunk) {
        String digits = chunk.startsWith("-") ? chunk.substring(1) : chunk;
        if (digits.isEmpty() || digits.equals(".") || !digits.matches("[0-9]*(\\.[0-9]*)?")) {
            return "A number is made of digits with at most one decimal point and an optional '-' in front as in -12.5";
        }
        if (digits.startsWith(".") || digits.endsWith(".")) {
            return "A decimal point needs at least one digit on each side as in 0.5 or 2.25";
        }
        String written = new BigDecimal(chunk).stripTrailingZeros().toPlainString();
        return "SPL numbers have no leading zeros and no trailing zeros after the decimal point. "
                + "Write " + written + " instead of " + chunk + ".";
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isLowerOrDigit(char c) {
        return (c >= 'a' && c <= 'z') || isDigit(c);
    }

    private static boolean isLetterOrDigit(char c) {
        return isLowerOrDigit(c) || (c >= 'A' && c <= 'Z');
    }
}
