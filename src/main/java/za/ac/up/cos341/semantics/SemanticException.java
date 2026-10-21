package za.ac.up.cos341.semantics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// Thrown when a program breaks the naming rules of phase 2a or the type rules of phase 2b
// It carries every error that was found and order them by their position in the file
public class SemanticException extends RuntimeException {

    public enum Kind {
        NAMING("Naming error"),
        TYPE("Type error");

        private final String label;

        Kind(String label) {
            this.label = label;
        }
        // the heading of an error report
        public String getLabel() {
            return label;
        }
    }

    private final Kind kind;
    private final List<SemanticError> errors;

    SemanticException(Kind kind, List<SemanticError> errors) {
        super(errors.isEmpty() ? kind.getLabel() : errors.get(0).getMessage());
        List<SemanticError> sorted = new ArrayList<>(errors);
        sorted.sort((left, right) -> left.getLine() != right.getLine()
                ? Integer.compare(left.getLine(), right.getLine())
                : Integer.compare(left.getColumn(), right.getColumn()));
        this.kind = kind;
        this.errors = Collections.unmodifiableList(sorted);
    }

    public Kind getKind() {
        return kind;
    }

    public List<SemanticError> getErrors() {
        return errors;
    }
}