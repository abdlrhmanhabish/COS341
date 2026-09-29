package za.ac.up.cos341.lexer;

import java.util.Objects;

// line and column are 1-based and point at the first character of the token
public final class Token {

    private final TokenType type;
    private final String value;
    private final int line;
    private final int column;

    public Token(TokenType type, String value, int line, int column) {
        this.type = type;
        this.value = value;
        this.line = line;
        this.column = column;
    }

    public TokenType type() {
        return type;
    }

    public String value() {
        return value;
    }

    public int line() {
        return line;
    }

    public int column() {
        return column;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Token)) {
            return false;
        }
        Token token = (Token) other;
        return type == token.type && value.equals(token.value) && line == token.line && column == token.column;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, value, line, column);
    }

    @Override
    public String toString() {
        return "{" + type + ", \"" + value + "\", line " + line + "}";
    }
}
