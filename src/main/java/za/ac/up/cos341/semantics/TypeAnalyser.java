package za.ac.up.cos341.semantics;

import java.util.ArrayList;
import java.util.List;

import za.ac.up.cos341.lexer.Token;
import za.ac.up.cos341.tree.Node;
import za.ac.up.cos341.tree.NodeKind;
import za.ac.up.cos341.tree.SyntaxTree;

public final class TypeAnalyser {

    // the error message that the specification prescribes for programs with both mod and div
    public static final String FLOAT_INTEGER_CONFLICT = "A float-integer-conflict might perhaps be possible";

    private final SyntaxTree tree;
    private final LeafTokens leaves;
    private final SymbolTable table;
    private final List<SemanticError> errors = new ArrayList<>();

    public TypeAnalyser(SyntaxTree tree, LeafTokens leaves, SymbolTable table) {
        this.tree = tree;
        this.leaves = leaves;
        this.table = table;
    }

    /**
     * Checks the type rules and stores the type of every node in the symbol table.
     *
     * @throws SemanticException with every type error that was found
     */
    public void analyse() {
        checkNumbers();
        Node root = tree.getRoot();
        set(root, program(tree.child(root, 0)) == Type.OK ? Type.OK : Type.UNKNOWN);
        if (!errors.isEmpty()) {
            throw new SemanticException(SemanticException.Kind.TYPE, errors);
        }
    }

    // The crude rule for mod and div. mod works only with integers and div only with floating point numbers
    //A tree that contains both is rejected without looking any further. With mod no number may have a decimal point
    private void checkNumbers() {
        Node firstMod = null;
        Node firstDiv = null;
        Node conflict = null;
        List<Node> decimals = new ArrayList<>();
        for (Node node : Trees.preOrder(tree)) {
            if (node.getKind() != NodeKind.LEAF) {
                continue;
            }
            String text = node.getContents();
            if (text.equals("mod") || text.equals("div")) {
                boolean mod = text.equals("mod");
                if (conflict == null && (mod ? firstDiv : firstMod) != null) {
                    conflict = node;
                }
                if (mod && firstMod == null) {
                    firstMod = node;
                } else if (!mod && firstDiv == null) {
                    firstDiv = node;
                }
            } else if (Trees.isNumber(node) && text.indexOf('.') >= 0) {
                decimals.add(node);
            }
        }
        if (conflict != null) {
            error(conflict, FLOAT_INTEGER_CONFLICT, "The program uses mod on " + where(firstMod) + " and div on "
                    + where(firstDiv) + ". mod works only with integers and div only with floating point numbers. "
                    + "One program cannot contain both.");
            throw new SemanticException(SemanticException.Kind.TYPE, errors);
        }
        if (firstMod != null && !decimals.isEmpty()) {
            for (Node number : decimals) {
                error(number, "The number " + number.getContents() + " has a decimal point but the program uses mod.",
                        "mod works only with integers and is used on " + where(firstMod) + ". In a program with "
                                + "mod every number must be a whole number.");
            }
            throw new SemanticException(SemanticException.Kind.TYPE, errors);
        }
        table.setNumbers(firstDiv != null || !decimals.isEmpty() ? NumberType.FLOAT : NumberType.INTEGER);
    }

    // P -> V_DECL : F_DECL : ALGO
    private Type program(Node program) {
        boolean ok = variables(tree.child(program, 0)) == Type.OK;
        ok &= functions(tree.child(program, 2)) == Type.OK;
        ok &= algorithm(tree.child(program, 4)) == Type.OK;
        return set(program, ok ? Type.OK : Type.UNKNOWN);
    }

    // V_DECL -> NAME V_DECL | ε   every declared variable and every parameter is numeric
    private Type variables(Node list) {
        List<Type> names = new ArrayList<>();
        for (Node name : Trees.items(tree, list)) {
            declare(name, Type.NUMERIC);
            names.add(Type.NUMERIC);
        }
        return list(Trees.links(tree, list), names, Type.NUMERIC);
    }

    // F_DECL -> F_TYPE F_DECL | ε
    private Type functions(Node list) {
        List<Type> definitions = new ArrayList<>();
        for (Node definition : Trees.items(tree, list)) {
            definitions.add(function(definition));
        }
        return list(Trees.links(tree, list), definitions, Type.OK);
    }

    // F_TYPE -> void NAME ( V_DECL ) { P return }        NAME is a procedure
    // F_TYPE -> num NAME ( V_DECL ) { P return ( TERM ) }  NAME is numeric
    // The type of NAME comes from the keyword in front of it . calls of a function with a faulty body are still checked against that type
    private Type function(Node definition) {
        Symbol function = table.symbolOf(tree.child(definition, 1).getId());
        declare(tree.child(definition, 1), function.returnsValue() ? Type.NUMERIC : Type.PROCEDURE);
        boolean ok = variables(tree.child(definition, 3)) == Type.OK;
        ok &= program(tree.child(definition, 6)) == Type.OK;
        if (function.returnsValue()) {
            ok &= term(tree.child(definition, 9)) == Type.NUMERIC;
        }
        return set(definition, ok ? Type.OK : Type.UNKNOWN);
    }

    // ALGO -> INSTR ; ALGO | ε
    private Type algorithm(Node list) {
        List<Type> instructions = new ArrayList<>();
        for (Node instruction : Trees.items(tree, list)) {
            instructions.add(instruction(instruction));
        }
        return list(Trees.links(tree, list), instructions, Type.OK);
    }

    // INSTR -> print OUTP | nop | comment STRING | ASSIGN | BRANCH | LOOP | CALL
    private Type instruction(Node instruction) {
        Node first = tree.child(instruction, 0);
        Type type;
        switch (first.getContents()) {
            case "print":
                type = output(tree.child(instruction, 1)) == Type.OK ? Type.OK : Type.UNKNOWN;
                break;
            case "nop":
            case "comment":
                type = Type.OK;
                break;
            case "ASSIGN":
                type = assignment(first);
                break;
            case "BRANCH":
                type = branch(first);
                break;
            case "LOOP":
                type = loop(first);
                break;
            default:
                type = procedureCall(first);
        }
        return set(instruction, type);
    }

    // INSTR -> CALL is only ok when the called function is a procedure
    private Type procedureCall(Node call) {
        Type type = call(call);
        if (type == Type.NUMERIC) {
            String name = tree.child(call, 0).getContents();
            error(tree.child(call, 0), name + " is a num function. Its call gives back a value and cannot stand alone "
                    + "as an instruction.", "Use the value of the call, for example in  #x = " + name + " ( ... )  or in  "
                    + "print ( " + name + " ( ... ) ) . Declare " + name + " with void if it should not return anything.");
            return Type.UNKNOWN;
        }
        return type == Type.PROCEDURE ? Type.OK : Type.UNKNOWN;
    }

    // OUTP -> ( TERM ) | STRING
    private Type output(Node output) {
        if (output.getChildren().size() == 1) {
            return set(output, Type.OK);
        }
        return set(output, term(tree.child(output, 1)) == Type.NUMERIC ? Type.OK : Type.UNKNOWN);
    }

    // CALL -> NAME ( INPUT )   the call has the type of the function. Every parameter need exactly one argument
    private Type call(Node call) {
        Node name = tree.child(call, 0);
        Symbol function = table.symbolOf(name.getId());
        Node input = tree.child(call, 2);
        List<Type> arguments = new ArrayList<>();
        for (Node argument : Trees.items(tree, input)) {
            arguments.add(term(argument));
        }

        Type inputType = list(Trees.links(tree, input), arguments, Type.NUMERIC);
        Type type = use(name);
        int expected = function.getParameters().size();
        if (arguments.size() != expected) {
            error(name, function.getName() + " has " + count(expected, "parameter") + " but this call passes "
                    + count(arguments.size(), "argument") + ".", function.getName() + " is declared on "
                    + where(leaves.tokenOf(function.getDeclaration())) + " as  " + signature(function)
                    + " . Every parameter needs exactly one argument.");
            return set(call, Type.UNKNOWN);
        }
        return set(call, inputType == Type.OK ? type : Type.UNKNOWN);
    }

    // ASSIGN -> NAME = TERM
    private Type assignment(Node assignment) {
        boolean ok = use(tree.child(assignment, 0)) == Type.NUMERIC;
        ok &= term(tree.child(assignment, 2)) == Type.NUMERIC;

        return set(assignment, ok ? Type.OK : Type.UNKNOWN);
    }

    // TERM -> NAME | NUM | CALL | mod ( TERM TERM ) | add ( TERM TERM ) | sub ( TERM TERM ) | mul ( TERM TERM ) | div ( TERM TERM ) | neg ( TERM )
    private Type term(Node term) {
        Node first = tree.child(term, 0);
        boolean numeric;
        if (first.getKind() != NodeKind.LEAF) {
            numeric = valueCall(first);
        } else if (Trees.isName(first)) {
            numeric = use(first) == Type.NUMERIC;
        } else if (Trees.isNumber(first)) {
            set(first, Type.NUMERIC);
            numeric = true;
        } else if (first.getContents().equals("neg")) {
            numeric = term(tree.child(term, 2)) == Type.NUMERIC;
        } else {
            numeric = term(tree.child(term, 2)) == Type.NUMERIC;
            numeric &= term(tree.child(term, 3)) == Type.NUMERIC;
        }
        return set(term, numeric ? Type.NUMERIC : Type.UNKNOWN);
    }

    // TERM -> CALL is only numeric when the called function returns a value
    private boolean valueCall(Node call) {
        Type type = call(call);
        if (type == Type.PROCEDURE) {
            String name = tree.child(call, 0).getContents();
            error(tree.child(call, 0), name + " is a void function and gives back no value. Its call cannot be used "
                    + "as a term.", "Only num functions can be called inside a term. Call " + name + " as an "
                    + "instruction of its own or declare it with num and let it return a value.");
            return false;
        }
        return type == Type.NUMERIC;
    }

    // BRANCH -> if BOOL then { ALGO } else { ALGO }
    private Type branch(Node branch) {
        boolean ok = condition(tree.child(branch, 1)) == Type.BOOLEAN;
        ok &= algorithm(tree.child(branch, 4)) == Type.OK;
        ok &= algorithm(tree.child(branch, 8)) == Type.OK;
        return set(branch, ok ? Type.OK : Type.UNKNOWN);
    }

    // BOOL -> not ( BOOL ) | and ( BOOL BOOL ) | or ( BOOL BOOL ) | eq ( TERM TERM ) | larger ( TERM TERM ) | lesser ( TERM TERM )
    private Type condition(Node condition) {
        String operator = tree.child(condition, 0).getContents();
        boolean ok;
        if (operator.equals("not")) {
            ok = condition(tree.child(condition, 2)) == Type.BOOLEAN;
        } else if (operator.equals("and") || operator.equals("or")) {
            ok = condition(tree.child(condition, 2)) == Type.BOOLEAN;
            ok &= condition(tree.child(condition, 3)) == Type.BOOLEAN;
        } else {
            ok = term(tree.child(condition, 2)) == Type.NUMERIC;
            ok &= term(tree.child(condition, 3)) == Type.NUMERIC;
        }
        return set(condition, ok ? Type.BOOLEAN : Type.UNKNOWN);
    }

    // LOOP -> COND BOOL do { ALGO } | do { ALGO } COND BOOL     with COND -> while | until
    private Type loop(Node loop) {
        boolean bodyFirst = tree.child(loop, 0).getKind() == NodeKind.LEAF;
        set(tree.child(loop, bodyFirst ? 4 : 0), Type.OK);
        boolean ok;
        if (bodyFirst) {
            ok = algorithm(tree.child(loop, 2)) == Type.OK;
            ok &= condition(tree.child(loop, 5)) == Type.BOOLEAN;
        } else {
            ok = condition(tree.child(loop, 1)) == Type.BOOLEAN;
            ok &= algorithm(tree.child(loop, 4)) == Type.OK;
        }

        return set(loop, ok ? Type.OK : Type.UNKNOWN);
    }

    // this gives every link of a right-recursive list its type. A link is ok when its item has the wanted type and the rest of the list behind it is ok
    private Type list(List<Node> links, List<Type> items, Type wanted) {
        Type rest = set(links.get(links.size() - 1), Type.OK);
        for (int i = links.size() - 2; i >= 0; i--) {
            rest = set(links.get(i), items.get(i) == wanted && rest == Type.OK ? Type.OK : Type.UNKNOWN);
        }
        return rest;
    }

    // a NAME leaf that declares a variable or a parameter or a function
    private void declare(Node name, Type type) {
        table.symbolOf(name.getId()).setType(type);
        set(name, type);
    }

    //a NAME leaf that uses a declared name takes its type from the symbol table
    private Type use(Node name) {
        Symbol symbol = table.symbolOf(name.getId());
        return set(name, symbol == null ? Type.UNKNOWN : symbol.getType());
    }

    private Type set(Node node, Type type) {
        table.setType(node.getId(), type);
        return type;
    }

    private static String signature(Symbol function) {
        StringBuilder text = new StringBuilder(function.getName()).append(" (");
        for (Symbol parameter : function.getParameters()) {
            text.append(' ').append(parameter.getName());
        }
        return text.append(" )").toString();
    }

    private static String count(int number, String word) {
        return number + " " + word + (number == 1 ? "" : "s");
    }

    private String where(Node leaf) {
        return where(leaves.tokenOf(leaf.getId()));
    }

    // the column matters because many test programs are written on a single line
    private static String where(Token token) {
        return "line " + token.line() + ", column " + token.column();
    }

    private void error(Node leaf, String message, String hint) {
        errors.add(new SemanticError(leaves.tokenOf(leaf.getId()), message, hint));
    }
}