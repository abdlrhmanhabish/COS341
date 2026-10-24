package za.ac.up.cos341.semantics;

// All numbers of one SPL program are either integers or floats, they are never mixed
public enum NumberType {
    INTEGER("integers"),
    FLOAT("floating point numbers");

    private final String description;

    NumberType(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return description;
    }
}