package za.ac.up.cos341.codegen;

import za.ac.up.cos341.semantics.NumberType;
import za.ac.up.cos341.semantics.Symbol;
import za.ac.up.cos341.semantics.SymbolTable;
import za.ac.up.cos341.semantics.Trees;
import za.ac.up.cos341.tree.Node;
import za.ac.up.cos341.tree.NodeKind;
import za.ac.up.cos341.tree.SyntaxTree;

// Phase 3a: translates the algorithms of a type-checked SPL program into C++ in the spirit of chapter 6.
// Booleans become cascading goto jumps and there is no else. Function calls are left to phase 3b through CallTranslator.
public final class CodeGenerator {

    private static final String NL = "\n";

    private final SyntaxTree tree;
    private final SymbolTable table;
    private final String numberType;
    private CallTranslator calls = CallTranslator.NOT_YET;
    private int temps;
    private int labels;

    public CodeGenerator(SyntaxTree tree, SymbolTable table) {
        this.tree = tree;
        this.table = table;
        this.numberType = table.getNumbers() == NumberType.FLOAT ? "float" : "int";
    }

    // phase 3b plugs its inlining in here
    public void setCallTranslator(CallTranslator calls) {
        this.calls = calls;
    }

    // int or float, decided by the type analysis of phase 2b
    public String numberType() {
        return numberType;
    }

    // a fresh temporary variable. The internal names of phase 2a start with v or f so t never clashes with them
    public String newVar() {
        return "t" + (++temps);
    }

    public String newLabel() {
        return "L" + (++labels);
    }

    // the C++ name of a NAME leaf, taken from the symbol table of phase 2a
    public String intern(Node name) {
        Symbol symbol = table.symbolOf(name.getId());
        if (symbol == null) {
            throw new IllegalStateException("The name " + name.getContents() + " has no symbol. Run phase 2 first.");
        }
        return symbol.getInternalName();
    }

    // the main program as a runnable C++ file. Phase 3b extends this with the inlined functions
    public String program() {
        Node p = tree.child(tree.getRoot(), 0);
        StringBuilder out = new StringBuilder();
        out.append("#include <iostream>").append(NL).append(NL);
        out.append("int main ( ) {").append(NL);
        for (Node name : Trees.items(tree, tree.child(p, 0))) {
            out.append(numberType).append(' ').append(intern(name)).append(" = 0 ;").append(NL);
        }
        out.append(algorithm(tree.child(p, 4))).append(" ;").append(NL);
        out.append("return 0 ;").append(NL);
        out.append("}").append(NL);
        return out.toString();
    }

    // ALGO -> INSTR ; ALGO   gives  trans(INSTR) ; NL trans(ALGO)
    // ALGO -> ε              gives  /* end */
    public String algorithm(Node algo) {
        StringBuilder out = new StringBuilder();
        for (Node instruction : Trees.items(tree, algo)) {
            out.append(instruction(instruction)).append(" ;").append(NL);
        }
        return out.append("/* end */").toString();
    }

    // INSTR -> print OUTP | nop | comment STRING | ASSIGN | BRANCH | LOOP | CALL
    private String instruction(Node instruction) {
        Node first = tree.child(instruction, 0);
        switch (first.getContents()) {
            case "print":
                return print(tree.child(instruction, 1));
            case "nop":
                return "/* nop */";
            case "comment":
                return "/* " + tree.child(instruction, 1).getContents() + " */";
            case "ASSIGN":
                return assignment(first);
            case "BRANCH":
                return branch(first);
            case "LOOP":
                return loop(first);
            default:
                return calls.instruction(this, first);
        }
    }

    // OUTP -> STRING   gives  std::cout << STRING
    // OUTP -> ( TERM ) gives  typ vN ; code ; std::cout << vN
    // every print ends with a line break, as agreed in the group
    private String print(Node output) {
        if (output.getChildren().size() == 1) {
            // a string like "??!" would be read as trigraph by compilers before C++17
            String text = tree.child(output, 0).getContents().replace("?", "\\?");
            return "std::cout << " + text + " << std::endl";
        }
        String v = newVar();
        return declare(v) + NL + term(tree.child(output, 1), v) + " ;" + NL + "std::cout << " + v + " << std::endl";
    }

    // ASSIGN -> NAME = TERM   gives  typ vN ; code ; intern = vN
    private String assignment(Node assignment) {
        String v = newVar();
        String code = term(tree.child(assignment, 2), v);
        return declare(v) + NL + code + " ;" + NL + intern(tree.child(assignment, 0)) + " = " + v;
    }

    // TERM -> NAME | NUM | CALL | unop ( TERM ) | binop ( TERM TERM )   puts the value of the term into result
    public String term(Node term, String result) {
        Node first = tree.child(term, 0);
        if (first.getKind() != NodeKind.LEAF) {
            return calls.term(this, first, result);
        }
        if (Trees.isName(first)) {
            return result + " = " + intern(first);
        }
        if (Trees.isNumber(first)) {
            return result + " = " + first.getContents();
        }
        String operator = first.getContents();
        String a = newVar();
        if (operator.equals("neg")) {
            return declare(a) + NL + term(tree.child(term, 2), a) + " ;" + NL + result + " = - " + a;
        }
        String b = newVar();
        return declare(a) + NL + declare(b) + NL
                + term(tree.child(term, 2), a) + " ;" + NL
                + term(tree.child(term, 3), b) + " ;" + NL
                + result + " = " + a + " " + binop(operator) + " " + b;
    }

    // BRANCH -> if BOOL then { ALGO } else { ALGO }   the target language has no else and the false code comes first
    private String branch(Node branch) {
        String la = newLabel();
        String lb = newLabel();
        String lc = newLabel();
        String decision = jumpIfTrue(tree.child(branch, 1), la);
        String trueCode = algorithm(tree.child(branch, 4));
        String falseCode = algorithm(tree.child(branch, 8));
        return "/* if */" + NL
                + decision + " ;" + NL
                + lb + " : /* else */" + NL
                + falseCode + " ;" + NL
                + "goto " + lc + " ;" + NL
                + la + " : /* then */" + NL
                + trueCode + " ;" + NL
                + lc + " : /* continue */";
    }

    // LOOP -> COND BOOL do { ALGO } | do { ALGO } COND BOOL
    private String loop(Node loop) {
        boolean bodyFirst = tree.child(loop, 0).getKind() == NodeKind.LEAF;
        if (bodyFirst) {
            boolean until = isUntil(tree.child(loop, 4));
            return doLoop(tree.child(loop, 2), tree.child(loop, 5), until);
        }
        boolean until = isUntil(tree.child(loop, 0));
        return conditionFirst(tree.child(loop, 1), tree.child(loop, 4), until);
    }

    // until B do { ALGO }: leave as soon as B is true.  while B do { ALGO } = until not ( B ) do { ALGO }
    private String conditionFirst(Node condition, Node body, boolean until) {
        String ls = newLabel();
        String le = newLabel();
        String d = newLabel();
        String decision = until ? jumpIfTrue(condition, le) : jumpIfFalse(condition, le);
        String bodyCode = algorithm(body);
        return ls + " : /* " + (until ? "until" : "while") + " */" + NL
                + decision + " ;" + NL
                + d + " : /* do */" + NL
                + bodyCode + " ;" + NL
                + "goto " + ls + " ;" + NL
                + le + " :" + NL
                + "/* exit */";
    }

    // do { ALGO } while B: go back as soon as B is true.  do { ALGO } until B = do { ALGO } while not ( B )
    private String doLoop(Node body, Node condition, boolean until) {
        String ld = newLabel();
        String le = newLabel();
        String bodyCode = algorithm(body);
        String decision = until ? jumpIfFalse(condition, ld) : jumpIfTrue(condition, ld);
        return ld + " : /* do */" + NL
                + bodyCode + " ;" + NL
                + "/* " + (until ? "until" : "while") + " */" + NL
                + decision + " ;" + NL
                + le + " : /* exit */";
    }

    // Jumps to label when the condition is true and falls through when it is false.
    // The fall-through is the dummy label of the specification: the caller always places it straight behind this code.
    String jumpIfTrue(Node condition, String label) {
        String operator = tree.child(condition, 0).getContents();
        switch (operator) {
            case "not":
                return jumpIfFalse(tree.child(condition, 2), label);
            case "or":
                return jumpIfTrue(tree.child(condition, 2), label) + " ;" + NL
                        + jumpIfTrue(tree.child(condition, 3), label);
            case "and": {
                String fail = newLabel();
                return jumpIfFalse(tree.child(condition, 2), fail) + " ;" + NL
                        + jumpIfFalse(tree.child(condition, 3), fail) + " ;" + NL
                        + "goto " + label + " ;" + NL
                        + fail + " :";
            }
            default:
                return compare(condition, label);
        }
    }

    // Jumps to label when the condition is false and falls through when it is true. Only == > < are used, never ! && ||
    String jumpIfFalse(Node condition, String label) {
        String operator = tree.child(condition, 0).getContents();
        switch (operator) {
            case "not":
                return jumpIfTrue(tree.child(condition, 2), label);
            case "and":
                return jumpIfFalse(tree.child(condition, 2), label) + " ;" + NL
                        + jumpIfFalse(tree.child(condition, 3), label);
            default: {
                String pass = newLabel();
                return jumpIfTrue(condition, pass) + " ;" + NL
                        + "goto " + label + " ;" + NL
                        + pass + " :";
            }
        }
    }

    // BOOL -> eq ( TERM TERM ) | larger ( TERM TERM ) | lesser ( TERM TERM )
    private String compare(Node condition, String label) {
        String x = newVar();
        String y = newVar();
        return declare(x) + NL + declare(y) + NL
                + term(tree.child(condition, 2), x) + " ;" + NL
                + term(tree.child(condition, 3), y) + " ;" + NL
                + "if ( " + x + " " + comparison(tree.child(condition, 0).getContents()) + " " + y + " ) goto " + label;
    }

    private boolean isUntil(Node cond) {
        return tree.child(cond, 0).getContents().equals("until");
    }

    private String declare(String variable) {
        return numberType + " " + variable + " ;";
    }

    private static String binop(String operator) {
        switch (operator) {
            case "mod":
                return "%";
            case "div":
                return "/";
            case "add":
                return "+";
            case "sub":
                return "-";
            case "mul":
                return "*";
            default:
                throw new IllegalArgumentException("Unknown operator " + operator);
        }
    }

    private static String comparison(String operator) {
        switch (operator) {
            case "eq":
                return "==";
            case "larger":
                return ">";
            case "lesser":
                return "<";
            default:
                throw new IllegalArgumentException("Unknown comparison " + operator);
        }
    }

    public SyntaxTree getTree() {
        return tree;
    }

    public SymbolTable getTable() {
        return table;
    }
}
