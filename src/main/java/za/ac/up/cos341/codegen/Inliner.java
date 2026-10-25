package za.ac.up.cos341.codegen;

import java.util.ArrayList;
import java.util.List;

import za.ac.up.cos341.semantics.Symbol;
import za.ac.up.cos341.semantics.Trees;
import za.ac.up.cos341.tree.Node;
import za.ac.up.cos341.tree.SyntaxTree;

// Phase 3b: function calls by inlining. SPL has no recursion and every call can be replaced by the body of the called function
// The code-mine shows the code of every function inside C comments. When called, the body is translated again from the syntax tree which gives every copy its own labels and temporaries.
public final class Inliner implements CallTranslator {

    private static final String NL = "\n";
    //the arguments of a call are computed into p1, p2, ... before the parameters receive them
    private int arguments;

    // F_DECL -> F_TYPE F_DECL     a function inside a function is mined before the function containing it
    @Override
    public String codeMine(CodeGenerator generator, Node functions) {
        SyntaxTree tree = generator.getTree();
        StringBuilder out = new StringBuilder();
        for (Node definition : Trees.items(tree, functions)) {
            Node body = tree.child(definition, 6);
            out.append(codeMine(generator, tree.child(body, 2)));
            out.append(mine(generator, definition));
        }
        return out.toString();
    }

    // one comment per function with its parameters and local variables and its translated body
    private String mine(CodeGenerator generator, Node definition) {
        SyntaxTree tree = generator.getTree();
        Symbol function = function(generator, tree.child(definition, 1));
        Node body = tree.child(definition, 6);
        String kind = function.returnsValue() ? "num-function " : "void-procedure ";
        StringBuilder text = new StringBuilder();
        text.append("/* begin of code-mine for ").append(kind).append(function.getInternalName()).append(NL);
        text.append("parameters for inlining:").append(names(function.getParameters())).append(NL);
        List<String> locals = new ArrayList<>();


        for (Node name : Trees.items(tree, tree.child(body, 0))) {
            locals.add(generator.intern(name));
        }
        text.append("local variables:").append(join(locals)).append(NL);
        text.append("body:").append(NL);
        StringBuilder code = new StringBuilder(generator.algorithm(tree.child(body, 4))).append(" ;").append(NL);
        if (function.returnsValue()) {
            String result = returnVariable(generator, function);
            code.append(generator.declare(result)).append(NL)
                    .append(generator.term(tree.child(definition, 9), result)).append(" ;").append(NL);
            text.append(CodeGenerator.indent(unnest(code.toString())));
            text.append("the result is found in the return variable ").append(result).append(NL);
        } else {
            text.append(CodeGenerator.indent(unnest(code.toString())));
        }
        text.append("end of code-mine for ").append(function.getInternalName()).append(" */").append(NL).append(NL);
        return text.toString();
    }

    // INSTR -> CALL   the body of a void function takes the place of the call
    @Override
    public String instruction(CodeGenerator generator, Node call) {
        Symbol function = function(generator, generator.getTree().child(call, 0));
        return inline(generator, call, function) + NL + "/* end of inlining " + function.getInternalName() + " */";
    }

    // TERM -> CALL    the body of a num function then its return variable r is copied into the caller's variable c
    @Override
    public String term(CodeGenerator generator, Node call, String result) {
        SyntaxTree tree = generator.getTree();
        Symbol function = function(generator, tree.child(call, 0));
        Node definition = tree.getNode(function.getDefinition());
        String r = returnVariable(generator, function);
        
        return inline(generator, call, function) + NL
                + generator.declare(r) + NL
                + generator.term(tree.child(definition, 9), r) + " ;" + NL
                + "/* end of inlining " + function.getInternalName() + " */" + NL
                + result + " = " + r;
    }

    // CALL -> NAME ( INPUT )   every argument goes into a new pN first
    private String inline(CodeGenerator generator, Node call, Symbol function) {
        SyntaxTree tree = generator.getTree();
        StringBuilder out = new StringBuilder("/* begin of inlining " + function.getInternalName() + " */");
        List<String> values = new ArrayList<>();

        for (Node argument : Trees.items(tree, tree.child(call, 2))) {
            String p = "p" + (++arguments);
            values.add(p);
            out.append(NL).append(generator.declare(p)).append(NL)
                    .append(generator.term(argument, p)).append(" ;");
        }
        List<Symbol> parameters = function.getParameters();
        for (int i = 0; i < parameters.size(); i++) {
            out.append(NL).append(parameters.get(i).getInternalName()).append(" = ").append(values.get(i)).append(" ;");
        }
        Node body = tree.child(tree.getNode(function.getDefinition()), 6);
        return out.append(NL).append(generator.algorithm(tree.child(body, 4))).append(" ;").toString();
    }

    // r = f ++ n  with the internal name f of the function and a fresh variable n
    private static String returnVariable(CodeGenerator generator, Symbol function) {
        return function.getInternalName() + "_" + generator.newVar();
    }

    private static Symbol function(CodeGenerator generator, Node name) {
        Symbol function = generator.getTable().symbolOf(name.getId());
        if (function == null || !function.isFunction()) {
            throw new IllegalStateException("The name " + name.getContents() + " is not a function. Run phase 2 first.");
        }
        return function;
    }

    // C comments do not nest. 
    private static String unnest(String code) {
        return code.replace("/*", "(*").replace("*/", "*)");
    }

    private static String names(List<Symbol> symbols) {
        List<String> names = new ArrayList<>();
        for (Symbol symbol : symbols) {
            names.add(symbol.getInternalName());
        }
        return join(names);
    }

    private static String join(List<String> names) {
        return names.isEmpty() ? " none" : " " + String.join(" ", names);
    }
}