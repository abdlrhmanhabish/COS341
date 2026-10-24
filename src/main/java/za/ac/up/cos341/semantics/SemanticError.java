package za.ac.up.cos341.semantics;

import za.ac.up.cos341.lexer.Token;

// One break of a naming rule or of a type rule: line and column point at the token that breaks the rule
public final class SemanticError {

    private final String message;
    private final String hint;
    private final int line;
    private final int column;
    private final int length;

    SemanticError(Token token, String message, String hint) {
        this.message = message;
        this.hint = hint;
        this.line = token.line();
        this.column = token.column();
        this.length = token.value().length();
    }

    public String getMessage() {
        return message;
    }

    // a short explanation of the rule and a way to fix the program
    public String getHint() {
        return hint;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    // number of characters of offending token
    public int getLength() {
        return length;
    }

    @Override
    public String toString() {
        return "line " + line + ", column " + column + ": " + message;
    }
}