package za.ac.up.cos341.codegen;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import za.ac.up.cos341.semantics.NumberType;
import za.ac.up.cos341.semantics.Symbol;
import za.ac.up.cos341.semantics.SymbolTable;
import za.ac.up.cos341.semantics.Trees;
import za.ac.up.cos341.tree.Node;
import za.ac.up.cos341.tree.NodeKind;
import za.ac.up.cos341.tree.SyntaxTree;

final class Interpreter {

    // this is thrown where the C++ program would crash or give an undefined result also for runs that take too long
    static final class Stop extends RuntimeException {
        Stop(String reason) {
            super(reason, null, false, false);
        }
    }

    private static final long MAX_STEPS = 1_000_000;

    private final SyntaxTree tree;
    private final SymbolTable table;
    private final boolean floats;
    private final Map<String, Double> memory = new HashMap<>();
    private final StringBuilder output = new StringBuilder();
    private long steps;

    Interpreter(SyntaxTree tree, SymbolTable table) {
        this.tree = tree;
        this.table = table;
        this.floats = table.getNumbers() == NumberType.FLOAT;
    }

    // runs the algorithm of the main program and gives back everything printed
    String run() {
        algorithm(tree.child(tree.child(tree.getRoot(), 0), 4));
        return output.toString();
    }

    private void algorithm(Node algo) {
        for (Node instruction : Trees.items(tree, algo)) {
            instruction(instruction);
        }
    }

    private void instruction(Node instruction) {
        step();
        Node first = tree.child(instruction, 0);
        switch (first.getContents()) {
            case "print":
                Node output = tree.child(instruction, 1);
                if (output.getChildren().size() == 1) {
                    String text = tree.child(output, 0).getContents();
                    this.output.append(text, 1, text.length() - 1).append('\n');
                } else {
                    this.output.append(format(term(tree.child(output, 1)))).append('\n');
                }
                return;
            case "nop":
            case "comment":
                return;
            case "ASSIGN":
                memory.put(name(tree.child(first, 0)), term(tree.child(first, 2)));
                return;
            case "BRANCH":
                algorithm(tree.child(first, condition(tree.child(first, 1)) ? 4 : 8));
                return;
            case "LOOP":
                loop(first);
                return;
            default:
                call(first);
        }
    }

    // LOOP -> COND BOOL do { ALGO } | do { ALGO } COND BOOL    while repeats on true and until on false
    private void loop(Node loop) {
        boolean bodyFirst = tree.child(loop, 0).getKind() == NodeKind.LEAF;
        Node cond = tree.child(loop, bodyFirst ? 4 : 0);
        Node condition = tree.child(loop, bodyFirst ? 5 : 1);
        Node body = tree.child(loop, bodyFirst ? 2 : 4);
        boolean repeatOn = tree.child(cond, 0).getContents().equals("while");
        if (bodyFirst) {
            do {
                step();
                algorithm(body);
            } while (condition(condition) == repeatOn);
        } else {
            while (condition(condition) == repeatOn) {
                step();
                algorithm(body);
            }
        }
    }

    // CALL -> NAME ( INPUT )
    private double call(Node call) {
        Symbol function = table.symbolOf(tree.child(call, 0).getId());
        List<Double> arguments = new ArrayList<>();
        for (Node argument : Trees.items(tree, tree.child(call, 2))) {
            arguments.add(term(argument));
        }
        for (int i = 0; i < arguments.size(); i++) {
            memory.put(function.getParameters().get(i).getInternalName(), arguments.get(i));
        }
        Node definition = tree.getNode(function.getDefinition());
        algorithm(tree.child(tree.child(definition, 6), 4));
        return function.returnsValue() ? term(tree.child(definition, 9)) : 0;
    }

    private double term(Node term) {
        Node first = tree.child(term, 0);
        if (first.getKind() != NodeKind.LEAF) {
            return call(first);
        }
        if (Trees.isName(first)) {
            Double value = memory.get(name(first));
            return value == null ? 0 : value;
        }
        if (Trees.isNumber(first)) {
            return number(first.getContents());
        }
        String operator = first.getContents();
        double a = term(tree.child(term, 2));
        if (operator.equals("neg")) {
            return floats ? -(float) a : -(int) a;
        }
        double b = term(tree.child(term, 3));
        return floats ? floatOperation(operator, (float) a, (float) b) : intOperation(operator, (int) a, (int) b);
    }

    private double number(String text) {
        if (floats) {
            return Float.parseFloat(text);
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new Stop("the number " + text + " does not fit into an int");
        }
    }

    private static double floatOperation(String operator, float a, float b) {
        float result;
        switch (operator) {
            case "add":
                result = a + b;
                break;
            case "sub":
                result = a - b;
                break;
            case "mul":
                result = a * b;
                break;
            default:
                result = a / b;
        }
        if (Float.isNaN(result) || Float.isInfinite(result)) {
            throw new Stop("a float operation gave " + result);
        }
        return result;
    }

    // the tests compile with -fwrapv, which makes int overflow wrap around as it does in Java
    private static double intOperation(String operator, int a, int b) {
        switch (operator) {
            case "add":
                return a + b;
            case "sub":
                return a - b;
            case "mul":
                return a * b;
            default:
                if (b == 0 || (a == Integer.MIN_VALUE && b == -1)) {
                    throw new Stop("mod ( " + a + " " + b + " ) stops the C++ program");
                }
                return a % b;
        }
    }

    private boolean condition(Node condition) {
        String operator = tree.child(condition, 0).getContents();
        switch (operator) {
            case "not":
                return !condition(tree.child(condition, 2));
            case "and":
                return condition(tree.child(condition, 2)) && condition(tree.child(condition, 3));
            case "or":
                return condition(tree.child(condition, 2)) || condition(tree.child(condition, 3));
            default:
                double a = term(tree.child(condition, 2));
                double b = term(tree.child(condition, 3));
                if (floats) {
                    a = (float) a;
                    b = (float) b;
                }
                return operator.equals("eq") ? a == b : operator.equals("larger") ? a > b : a < b;
        }
    }

    // std::cout writes an int in decimal and a float like printf with %g: six significant digits and no trailing zeros
    String format(double value) {
        if (!floats) {
            return Integer.toString((int) value);
        }

        if (value == 0) {
            return 1 / value < 0 ? "-0" : "0";
        }
        BigDecimal rounded = new BigDecimal(value).round(new MathContext(6, RoundingMode.HALF_EVEN));
        int exponent = rounded.precision() - rounded.scale() - 1;
        if (exponent < -4 || exponent >= 6) {
            String digits = rounded.unscaledValue().abs().toString();
            String mantissa = withoutTrailingZeros(digits.charAt(0) + (digits.length() > 1 ? "." + digits.substring(1) : ""));
            return (value < 0 ? "-" : "") + mantissa + "e" + (exponent < 0 ? "-" : "+")
                    + (Math.abs(exponent) < 10 ? "0" : "") + Math.abs(exponent);
        }
        return withoutTrailingZeros(rounded.setScale(Math.max(0, 5 - exponent), RoundingMode.HALF_EVEN).toPlainString());
    }

    private static String withoutTrailingZeros(String number) {
        if (number.indexOf('.') < 0) {
            return number;
        }
        String trimmed = number.replaceAll("0+$", "");
        return trimmed.endsWith(".") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private String name(Node leaf) {
        return table.symbolOf(leaf.getId()).getInternalName();
    }

    private void step() {
        if (++steps > MAX_STEPS) {
            throw new Stop("the program runs for too long");
        }
    }
}