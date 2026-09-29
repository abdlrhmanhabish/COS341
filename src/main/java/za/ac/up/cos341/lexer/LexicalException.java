package za.ac.up.cos341.lexer;

public class LexicalException extends RuntimeException {

    private final int line;
    private final int column;
    private final String offendingText;
    private final String hint;

    public LexicalException(String offendingText, int line, int column, String hint) {
        super("Lexical error on line " + line + ": '" + offendingText + "'");
        this.line = line;
        this.column = column;
        this.offendingText = offendingText;
        this.hint = hint;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    public String getOffendingText() {
        return offendingText;
    }

    // a short explanation of what is wrong with the text and how to fix it
    public String getHint() {
        return hint;
    }
}
