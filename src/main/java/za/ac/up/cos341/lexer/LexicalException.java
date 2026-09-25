package za.ac.up.cos341.lexer;

public class LexicalException extends RuntimeException {

    private final int line;
    private final String offendingText;

    public LexicalException(String offendingText, int line) {
        super("Lexical error on line " + line + ": '" + offendingText + "'");
        this.line = line;
        this.offendingText = offendingText;
    }

    public int getLine() {
        return line;
    }

    public String getOffendingText() {
        return offendingText;
    }
}
