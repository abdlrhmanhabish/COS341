package za.ac.up.cos341.lexer;

public record Token(TokenType type, String value, int line) {

    @Override
    public String toString() {
        return "{" + type + ", \"" + value + "\", line " + line + "}";
    }
}
