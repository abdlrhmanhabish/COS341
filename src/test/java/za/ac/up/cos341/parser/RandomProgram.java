package za.ac.up.cos341.parser;

import java.util.Random;

// It writes random SPL programs by expanding the grammar from the start symbol and the depth limits keep the programs finite
final class RandomProgram {

    private static final String[] NAMES = {"#a", "#b", "#x1", "#counter", "#", "#9"};
    private static final String[] NUMBERS = {"0", "7", "-3", "12.5", "-0.25", "100", "0.001"};
    private static final String[] STRINGS = {"\"hello\"", "\"\"", "\"a,b.c:d-e?f!\"", "\"x1\""};
    private static final String[] BLANKS = {" ", "  ", "\n", "\r\n", "\t", " \n "};
    private static final String[] OPERATORS = {"add", "sub", "mul", "div", "mod"};
    private static final String[] COMPARISONS = {"eq", "larger", "lesser"};

    private final Random random;
    private final StringBuilder text = new StringBuilder();

    private RandomProgram(long seed) {
        random = new Random(seed);
    }

    static String generate(long seed) {
        RandomProgram generator = new RandomProgram(seed);
        generator.program(0);
        return generator.text.toString();
    }

    private void emit(String token) {
        text.append(token).append(pick(BLANKS));
    }

    private String pick(String[] options) {
        return options[random.nextInt(options.length)];
    }

    // P -> V_DECL : F_DECL : ALGO
    private void program(int depth) {
        for (int i = random.nextInt(3); i > 0; i--) {
            emit(pick(NAMES));
        }
        emit(":");
        if (depth < 2) {
            for (int i = random.nextInt(3); i > 0; i--) {
                function(depth + 1);
            }
        }
        emit(":");
        algorithm(depth);
    }

    // F_TYPE -> void NAME ( V_DECL ) { P return } | num NAME ( V_DECL ) { P return ( TERM ) }
    private void function(int depth) {
        boolean returnsValue = random.nextBoolean();
        emit(returnsValue ? "num" : "void");
        emit(pick(NAMES));
        emit("(");
        for (int i = random.nextInt(3); i > 0; i--) {
            emit(pick(NAMES));
        }
        emit(")");
        emit("{");
        program(depth);
        emit("return");
        if (returnsValue) {
            emit("(");
            term(depth + 1);
            emit(")");
        }
        emit("}");
    }

    // ALGO -> INSTR ; ALGO | ε
    private void algorithm(int depth) {
        for (int i = random.nextInt(4); i > 0; i--) {
            instruction(depth);
            emit(";");
        }
    }

    private void instruction(int depth) {
        switch (random.nextInt(depth < 3 ? 9 : 6)) {
            case 0:
                emit("print");
                if (random.nextBoolean()) {
                    emit(pick(STRINGS));
                } else {
                    emit("(");
                    term(depth + 1);
                    emit(")");
                }
                break;
            case 1:
                emit("nop");
                break;
            case 2:
                emit("comment");
                emit(pick(STRINGS));
                break;
            case 3:
                emit(pick(NAMES));
                emit("=");
                term(depth + 1);
                break;
            case 4:
            case 5:
                call(depth);
                break;
            case 6:
                emit("if");
                condition(depth + 1);
                emit("then");
                block(depth);
                emit("else");
                block(depth);
                break;
            case 7:
                emit(random.nextBoolean() ? "while" : "until");
                condition(depth + 1);
                emit("do");
                block(depth);
                break;
            default:
                emit("do");
                block(depth);
                emit(random.nextBoolean() ? "while" : "until");
                condition(depth + 1);
        }
    }

    private void block(int depth) {
        emit("{");
        algorithm(depth + 1);
        emit("}");
    }

    private void call(int depth) {
        emit(pick(NAMES));
        emit("(");
        for (int i = random.nextInt(depth < 4 ? 4 : 1); i > 0; i--) {
            term(depth + 1);
        }
        emit(")");
    }

    private void term(int depth) {
        switch (random.nextInt(depth < 5 ? 10 : 2)) {
            case 0:
                emit(pick(NAMES));
                break;
            case 1:
                emit(pick(NUMBERS));
                break;
            case 2:
                call(depth);
                break;
            case 3:
                emit("neg");
                emit("(");
                term(depth + 1);
                emit(")");
                break;
            default:
                emit(pick(OPERATORS));
                emit("(");
                term(depth + 1);
                term(depth + 1);
                emit(")");
        }
    }

    private void condition(int depth) {
        switch (random.nextInt(depth < 5 ? 6 : 3)) {
            case 0:
            case 1:
            case 2:
                emit(pick(COMPARISONS));
                emit("(");
                term(depth + 1);
                term(depth + 1);
                emit(")");
                break;
            case 3:
                emit("not");
                emit("(");
                condition(depth + 1);
                emit(")");
                break;
            default:
                emit(random.nextBoolean() ? "and" : "or");
                emit("(");
                condition(depth + 1);
                condition(depth + 1);
                emit(")");
        }
    }
}