package za.ac.up.cos341.semantics;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import za.ac.up.cos341.lexer.Token;
import za.ac.up.cos341.tree.Node;
import za.ac.up.cos341.tree.NodeKind;
import za.ac.up.cos341.tree.SyntaxTree;

public final class NameAnalyser {

    // The main program or one function .the parameters and the local variables of a function share one scope
    private static final class Scope {
        final Scope parent;
        final Symbol owner;
        final int level;
        final Map<String, Symbol> variables = new HashMap<>();
        // the functions of the immediate F_DECL which are the only ones the algorithm of this scope calls
        final Map<String, Symbol> functions = new HashMap<>();
        // names that were already reported as missing in this scope
        final Set<String> reported = new HashSet<>();
        Scope(Scope parent, Symbol owner) {
            this.parent = parent;
            this.owner = owner;
            this.level = parent == null ? 0 : parent.level + 1;
        }
    }

    private final SyntaxTree tree;
    private final LeafTokens leaves;
    private final SymbolTable table = new SymbolTable();
    private final List<SemanticError> errors = new ArrayList<>();
    // the scope that belongs to each P node
    private final Map<Integer, Scope> scopes = new HashMap<>();
    private int variableCount;
    private int functionCount;

    public NameAnalyser(SyntaxTree tree, LeafTokens leaves) {
        this.tree = tree;
        this.leaves = leaves;
    }

    /**
     * Checks the naming rules and builds the symbol table.
     *
     * @throws SemanticException with every naming error that was found
     */
    public SymbolTable analyse() {
        Node program = tree.child(tree.getRoot(), 0);
        declare(program, new Scope(null, null));
        resolve(program);
        markLevels();

        if (!errors.isEmpty()) {
            throw new SemanticException(SemanticException.Kind.NAMING, errors);
        }
        return table;
    }

    // P -> V_DECL : F_DECL : ALGO   the first pass over the declarations of P and of all functions inside it
    private void declare(Node program, Scope scope) {
        scopes.put(program.getId(), scope);
        for (Node name : Trees.items(tree, tree.child(program, 0))) {
            declareVariable(name, scope, Symbol.Kind.VARIABLE);
        }
        for (Node definition : Trees.items(tree, tree.child(program, 2))) {
            declareFunction(definition, scope);
        }
    }

    // F_TYPE -> void NAME ( V_DECL ) { P return } | num NAME ( V_DECL ) { P return ( TERM ) }
    private void declareFunction(Node definition, Scope outer) {
        Node nameLeaf = tree.child(definition, 1);
        String name = nameLeaf.getContents();
        boolean returnsValue = tree.child(definition, 0).getContents().equals("num");
        Symbol function = Symbol.function(name, internalName("f", ++functionCount, name), outer.level + 1,
                nameLeaf.getId(), outer.owner, returnsValue, definition.getId());
        table.add(function);
        Symbol earlier = outer.functions.get(name);
        if (earlier == null) {
            outer.functions.put(name, function);
        } 
        else {
            error(nameLeaf, "The function " + name + " is declared twice in the function list of "
                    + describe(outer.owner) + ".",
                    "The first " + name + " is declared on " + where(earlier) + ". Two functions in one list "
                            + "need different names even when their parameters differ.");
        }
        Scope inner = new Scope(outer, function);
        for (Node parameter : Trees.items(tree, tree.child(definition, 3))) {
            function.addParameter(declareVariable(parameter, inner, Symbol.Kind.PARAMETER));
        }
        declare(tree.child(definition, 6), inner);
    }

    private Symbol declareVariable(Node nameLeaf, Scope scope, Symbol.Kind kind) {
        String name = nameLeaf.getContents();
        Symbol symbol = Symbol.variable(kind, name, internalName("v", ++variableCount, name), scope.level,
                nameLeaf.getId(), scope.owner);
        table.add(symbol);
        Symbol earlier = scope.variables.get(name);
        if (earlier == null) {
            scope.variables.put(name, symbol);
        } else if (kind == Symbol.Kind.PARAMETER) {
            error(nameLeaf, "The function " + scope.owner.getName() + " has two parameters called " + name + ".",
                    "The parameters of a function need different names.");
        } else if (earlier.getKind() == Symbol.Kind.PARAMETER) {
            error(nameLeaf, "The local variable " + name + " masks the parameter " + name + " of the function "
                    + scope.owner.getName() + ".",
                    "A local variable may not have the name of a parameter of its function. The parameter is "
                            + "declared on " + where(earlier) + ". Rename the local variable.");
        } else {
            error(nameLeaf, "The variable " + name + " is declared twice in " + describe(scope.owner) + ".",
                    "The first " + name + " is declared on " + where(earlier) + ". The names in one variable "
                            + "list must all be different.");
        }
        return symbol;
    }

    // the second pass over the uses of names in the algorithms and in the terms that functions return
    private void resolve(Node program) {
        for (Node definition : Trees.items(tree, tree.child(program, 2))) {
            Node body = tree.child(definition, 6);
            resolve(body);
            if (definition.getChildren().size() > 9) {
                uses(tree.child(definition, 9), scopes.get(body.getId()));
            }
        }
        Scope scope = scopes.get(program.getId());
        for (Node instruction : Trees.items(tree, tree.child(program, 4))) {
            uses(instruction, scope);
        }
    }

    // walks through an instruction or a term and links every name to its declaration
    private void uses(Node node, Scope scope) {
        switch (node.getContents()) {
            case "ALGO":
                for (Node instruction : Trees.items(tree, node)) {
                    uses(instruction, scope);
                }
                return;
            case "ASSIGN":
                useVariable(tree.child(node, 0), scope);
                uses(tree.child(node, 2), scope);
                return;
            case "CALL":
                useFunction(tree.child(node, 0), scope);
                for (Node argument : Trees.items(tree, tree.child(node, 2))) {
                    uses(argument, scope);
                }
                return;
            case "TERM":
                if (Trees.isName(tree.child(node, 0))) {
                    useVariable(tree.child(node, 0), scope);
                    return;
                }
                break;
            default:
                break;
        }
        for (Node child : tree.children(node)) {
            if (child.getKind() != NodeKind.LEAF) {
                uses(child, scope);
            }
        }
    }

    // the nearest declaration win (Only the scopes around the use are searched and never a scope beside it)
    private void useVariable(Node nameLeaf, Scope scope) {
        String name = nameLeaf.getContents();
        for (Scope around = scope; around != null; around = around.parent) {
            Symbol symbol = around.variables.get(name);
            if (symbol != null) {
                table.bind(nameLeaf.getId(), symbol);
                return;
            }
        }
        if (scope.reported.add("variable " + name)) {
            error(nameLeaf, "The variable " + name + " is not declared.", missingVariable(name, scope));
        }
    }

    // a call must name a function from the F_DECL of the same P
    private void useFunction(Node nameLeaf, Scope scope) {
        String name = nameLeaf.getContents();
        Symbol symbol = scope.functions.get(name);
        if (symbol != null) {
            table.bind(nameLeaf.getId(), symbol);
            return;
        }

        if (scope.reported.add("function " + name)) {
            missingFunction(nameLeaf, scope);
        }
    }

    private String missingVariable(String name, Scope scope) {
        for (Scope around = scope; around != null; around = around.parent) {
            if (around.functions.containsKey(name)) {
                return name + " is the name of a function. Functions and variables are kept apart and a function "
                        + "is called with brackets as in  " + name + " ( ) .";
            }
        }
        for (Symbol symbol : table.getSymbols()) {
            if (!symbol.isFunction() && symbol.getName().equals(name) && symbol.getOwner() != null) {
                return name + " is declared in the function " + symbol.getOwner().getName() + " on "
                        + where(symbol) + ". The variables of a function are only known inside that function and "
                        + "inside the functions declared within it.";
            }
        }
        return "Declare " + name + " in the variable list before the first ':' of " + describe(scope.owner) + ".";
    }

    private void missingFunction(Node nameLeaf, Scope scope) {
        String name = nameLeaf.getContents();
        String here = "The function " + name + " cannot be called here.";
        for (Symbol around = scope.owner; around != null; around = around.getOwner()) {
            if (around.getName().equals(name)) {
                error(nameLeaf, here, around == scope.owner
                        ? "The call is inside " + name + " itself. SPL does not allow recursion."
                        : "The call is inside " + scope.owner.getName() + " which is declared inside " + name
                                + ". Calling " + name + " from there would be a recursion. SPL does not allow it.");
                return;
            }
        }

        for (Scope around = scope.parent; around != null; around = around.parent) {
            Symbol outer = around.functions.get(name);
            if (outer != null) {
                error(nameLeaf, here, name + " is declared in the function list of " + describe(around.owner)
                        + " on " + where(outer) + ". An algorithm may only call the functions of its own "
                        + "function list. Declare a copy of " + name + " in the list of " + describe(scope.owner)
                        + ".");
                return;
            }
        }
        for (Symbol symbol : table.getSymbols()) {
            if (symbol.isFunction() && symbol.getName().equals(name) && symbol.getOwner() != null) {
                error(nameLeaf, here, name + " is declared inside the function " + symbol.getOwner().getName()
                        + " on " + where(symbol) + ". Only the algorithm of " + symbol.getOwner().getName()
                        + " may call it.");
                return;
            }
        }
        String declare = "Declare " + name + " in the function list between the two ':' of " + describe(scope.owner)
                + ".";
        for (Scope around = scope; around != null; around = around.parent) {
            if (around.variables.containsKey(name)) {
                declare = name + " is a variable and a variable cannot be called. " + declare;
                break;
            }
        }
        error(nameLeaf, "The function " + name + " is not declared.", declare);
    }

    // stores the scope level of every node: 0 in the main program and one more inside every function definitionn
    private void markLevels() {
        Deque<Node> nodes = new ArrayDeque<>();
        Deque<Integer> levels = new ArrayDeque<>();
        nodes.push(tree.getRoot());
        levels.push(0);
        while (!nodes.isEmpty()) {
            Node node = nodes.pop();
            int level = levels.pop();
            if (node.getKind() == NodeKind.INNER && node.getContents().equals("F_TYPE")) {
                level++;
            }
            table.setLevel(node.getId(), level);
            for (Node child : tree.children(node)) {
                nodes.push(child);
                levels.push(level);
            }
        }
    }

    //v3_x for the third variable #x and f2 for the second function called # (a name could be just "#")
    private static String internalName(String prefix, int number, String name) {
        return prefix + number + (name.length() > 1 ? "_" + name.substring(1) : "");
    }

    private static String describe(Symbol owner) {
        return owner == null ? "the main program" : "the function " + owner.getName();
    }

    // line and column of a declaration. Many test programs are written on a single line
    private String where(Symbol symbol) {
        Token token = leaves.tokenOf(symbol.getDeclaration());
        return "line " + token.line() + ", column " + token.column();
    }

    private void error(Node leaf, String message, String hint) {
        errors.add(new SemanticError(leaves.tokenOf(leaf.getId()), message, hint));
    }
}