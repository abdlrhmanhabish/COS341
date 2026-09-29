package za.ac.up.cos341.parser;

// This Thrown when the tokens don't form an SPL program. Line and column point at the token where the parser got stuck
public class SyntaxError extends RuntimeException {

    private final int line;
    private final int column;
    private final int length;
    private final String hint;

    public SyntaxError(String message, int line, int column, int length, String hint) {
        super(message);
        this.line = line;
        this.column = column;
        this.length = length;
        this.hint = hint;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    // number of characters of the offending token
    public int getLength() {
        return length;
    }

    // a short explanation of what the parser expected
    public String getHint() {
        return hint;
    }
}