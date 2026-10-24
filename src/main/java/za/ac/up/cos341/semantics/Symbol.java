package za.ac.up.cos341.semantics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// One computational entity of an SPL program: a variable, a parameter or a function.
// all NAME leaves that stand for the same entity share one Symbol and share one internal name with it
public final class Symbol {

    public enum Kind { VARIABLE, PARAMETER, FUNCTION }

    private final Kind kind;
    private final String name;
    private final String internalName;
    private final int level;
    private final int declaration;
    private final Symbol owner;
    private final boolean returnsValue;
    private final int definition;
    private final List<Symbol> parameters = new ArrayList<>();
    private Type type = Type.UNKNOWN;

    private Symbol(Kind kind, String name, String internalName, int level, int declaration, Symbol owner, boolean returnsValue, int definition) {
        this.kind = kind;
        this.name = name;
        this.internalName = internalName;
        this.level = level;
        this.declaration = declaration;
        this.owner = owner;
        this.returnsValue = returnsValue;
        this.definition = definition;
    }

    static Symbol variable(Kind kind, String name, String internalName, int level, int declaration, Symbol owner) {
        return new Symbol(kind, name, internalName, level, declaration, owner, false, -1);
    }

    static Symbol function(String name, String internalName, int level, int declaration, Symbol owner, boolean returnsValue, int definition) {
        return new Symbol(Kind.FUNCTION, name, internalName, level, declaration, owner, returnsValue, definition);
    }

    public Kind getKind() {
        return kind;
    }

    public boolean isFunction() {
        return kind == Kind.FUNCTION;
    }

    // the name as it is written in the SPL program (example #x)
    public String getName() {
        return name;
    }

    // the unique name given by the name analysis (example v3_x)
    public String getInternalName() {
        return internalName;
    }

    // scope level of the declaration. The main program is level 0 and every function adds one
    public int getLevel() {
        return level;
    }

    // UNID of the NAME leaf that declares the entity
    public int getDeclaration() {
        return declaration;
    }

    // the function in whose scope the entity is declared or null for the main program
    public Symbol getOwner() {
        return owner;
    }

    // true for a function declared with num and false for void functions and for variables
    public boolean returnsValue() {
        return returnsValue;
    }

    // UNID of the F_TYPE node of a function or -1 for variables
    public int getDefinition() {
        return definition;
    }

    // the parameters of a function in their order, empty for variables
    public List<Symbol> getParameters() {
        return Collections.unmodifiableList(parameters);
    }

    void addParameter(Symbol parameter) {
        parameters.add(parameter);
    }

    public Type getType() {
        return type;
    }

    void setType(Type type) {
        this.type = type;
    }

    @Override
    public String toString() {
        return internalName;
    }
}