package za.ac.up.cos341.semantics;

// The type attributes of the phase 2b rules, every node of the tree starts as UNKNOWN
public enum Type {
    UNKNOWN("unknown"),
    OK("ok"),
    NUMERIC("numeric"),
    BOOLEAN("boolean"),
    PROCEDURE("procedure");

    private final String word;

    Type(String word) {
        this.word = word;
    }
    @Override
    public String toString() {
        return word;
    }
}