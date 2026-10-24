package za.ac.up.cos341.semantics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

// This Writes random SPL programs that keep every naming rule and keeps every type rule 
// For each token it records whether the token declares or uses a name and which entity the name belongs to 
//Every loop counts a counter of its own from 0 and up to a small bound: the programs always terminate.
// The counters are called #k1 and #k2 after the depth of the loop. 
// Every scope declares them and nothing else assigns to them: a function that is called inside a loop can't disturb the loop
public final class ValidProgram {

    public enum Role { OTHER, DECLARATION, VARIABLE_USE, FUNCTION_USE }

    // which of mod and div the program may contain (never together)
    public enum Numbers { MOD, DIV, NEITHER }

    private static final String[] VARIABLES = {"#a", "#b", "#c", "#x", "#n", "#", "#i1"};
    private static final String[] FUNCTIONS = {"#f", "#g", "#h", "#a", "#sum"};
    private static final String[] COUNTERS = {"#k1", "#k2"};
    private static final String[] STRINGS = {"\"hello\"", "\"\"", "\"a,b.c:d-e?f!\"", "\"x1\""};
    private static final String[] WHOLE = {"0", "1", "2", "7", "-3", "12", "100"};
    private static final String[] DECIMAL = {"2.5", "-0.25", "0.001", "10.75", "-4.5"};
    private static final String[] COMPARISONS = {"eq", "larger", "lesser"};

    private static final class Function {
        final String name;
        final int entity;
        final int arity;
        final boolean returnsValue;

        Function(String name, int entity, int arity, boolean returnsValue) {
            this.name = name;
            this.entity = entity;
            this.arity = arity;
            this.returnsValue = returnsValue;
        }
    }

    private static final class Scope {
        final Scope parent;
        final Map<String, Integer> variables = new HashMap<>();
        final List<Function> functions = new ArrayList<>();

        Scope(Scope parent) {
            this.parent = parent;
        }
    }

    private final Random random;
    private final Numbers numbers;
    private final boolean decimals;
    private final StringBuilder text = new StringBuilder();
    private final List<String> tokens = new ArrayList<>();
    private final List<Role> roles = new ArrayList<>();
    private final List<Integer> entities = new ArrayList<>();
    private int nextEntity;

    private ValidProgram(long seed) {
        random = new Random(seed);
        numbers = Numbers.values()[random.nextInt(3)];
        decimals = numbers == Numbers.DIV || (numbers == Numbers.NEITHER && random.nextBoolean());
    }

    public static ValidProgram generate(long seed) {
        ValidProgram program = new ValidProgram(seed);
        program.program(new Scope(null), 0);
        return program;
    }

    public String getText() {
        return text.toString();
    }

    public List<String> getTokens() {
        return Collections.unmodifiableList(tokens);
    }

    public Role roleOf(int token) {
        return roles.get(token);
    }

    // the entity that the k-th token declares or uses() -1 for tokens which are not names)
    public int entityOf(int token) {
        return entities.get(token);
    }

    public Numbers getNumbers() {
        return numbers;
    }

    //the indexes of all tokens with the given role
    public List<Integer> indexesOf(Role role) {
        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < roles.size(); i++) {
            if (roles.get(i) == role) {
                indexes.add(i);
            }
        }
        return indexes;
    }

    private void emit(String token) {
        emit(token, Role.OTHER, -1);
    }

    private void emit(String token, Role role, int entity) {
        tokens.add(token);
        roles.add(role);
        entities.add(entity);
        text.append(token).append(random.nextInt(8) == 0 ? "\n" : " ");
    }

    private String pick(String[] options) {
        return options[random.nextInt(options.length)];
    }

    // P -> V_DECL : F_DECL : ALGO
    private void program(Scope scope, int level) {
        List<String> names = new ArrayList<>(distinct(VARIABLES, random.nextInt(4), scope.variables.keySet()));
        Collections.addAll(names, COUNTERS);
        for (String name : names) {
            declareVariable(scope, name);
        }
        emit(":");
        if (level < 2) {
            for (String name : distinct(FUNCTIONS, random.nextInt(3), Collections.<String>emptySet())) {
                function(scope, name, level + 1);
            }
        }
        emit(":");
        algorithm(scope, 0);
    }

    private void declareVariable(Scope scope, String name) {
        int entity = nextEntity++;
        scope.variables.put(name, entity);
        emit(name, Role.DECLARATION, entity);
    }

    // F_TYPE -> void NAME ( V_DECL ) { P return } | num NAME ( V_DECL ) { P return ( TERM ) }
    private void function(Scope outer, String name, int level) {
        boolean returnsValue = random.nextBoolean();
        List<String> parameters = distinct(VARIABLES, random.nextInt(4), Collections.<String>emptySet());
        Function function = new Function(name, nextEntity++, parameters.size(), returnsValue);
        emit(returnsValue ? "num" : "void");
        emit(name, Role.DECLARATION, function.entity);
        emit("(");
        Scope inner = new Scope(outer);
        for (String parameter : parameters) {
            declareVariable(inner, parameter);
        }
        emit(")");
        emit("{");
        program(inner, level);
        emit("return");
        if (returnsValue) {
            emit("(");
            term(inner, 1);
            emit(")");
        }
        emit("}");
        outer.functions.add(function);
    }

    // ALGO -> INSTR ; ALGO | ε   loops is the number of loops around the algorithm
    private void algorithm(Scope scope, int loops) {
        for (int i = random.nextInt(loops == 0 ? 5 : 3); i > 0; i--) {
            instruction(scope, loops);
            emit(";");
        }
    }

    private void instruction(Scope scope, int loops) {
        switch (random.nextInt(loops < COUNTERS.length ? 10 : 7)) {
            case 0:
                emit("print");
                emit(pick(STRINGS));
                break;
            case 1:
                emit("print");
                emit("(");
                term(scope, 0);
                emit(")");
                break;
            case 2:
                if (random.nextBoolean()) {
                    emit("nop");
                } else {
                    emit("comment");
                    emit(pick(STRINGS));
                }
                break;
            case 3:
            case 4:
                assignment(scope);
                break;
            case 5:
            case 6:
                List<Function> procedures = functions(scope, false);
                if (procedures.isEmpty()) {
                    assignment(scope);
                } else {
                    call(scope, procedures.get(random.nextInt(procedures.size())), 1);
                }
                break;
            case 7:
                emit("if");
                condition(scope, 0);
                emit("then");
                block(scope, loops);
                emit("else");
                block(scope, loops);
                break;
            default:
                loop(scope, loops);
        }
    }

    private void block(Scope scope, int loops) {
        emit("{");
        algorithm(scope, loops);
        emit("}");
    }

    // ASSIGN -> NAME = TERM   counters are left alone
    private void assignment(Scope scope) {
        List<String> names = new ArrayList<>(visible(scope).keySet());
        names.removeIf(name -> name.startsWith("#k"));
        if (names.isEmpty()) {
            emit("nop");
            return;
        }
        useVariable(scope, names.get(random.nextInt(names.size())));
        emit("=");
        term(scope, 0);
    }

    // #kD = 0 ; followed by a loop that counts #kD up by one in every round and then stops at a small bound
    private void loop(Scope scope, int loops) {
        String counter = COUNTERS[loops];
        String bound = Integer.toString(1 + random.nextInt(4));
        useVariable(scope, counter);
        emit("=");
        emit("0");
        emit(";");
        int form = random.nextInt(4);
        if (form < 2) {
            emit(form == 0 ? "while" : "until");
            guard(scope, counter, bound, form == 0);
            emit("do");
            body(scope, counter, loops);
        } else {
            emit("do");
            body(scope, counter, loops);
            emit(form == 2 ? "while" : "until");
            guard(scope, counter, bound, form == 2);
        }
    }

    private void body(Scope scope, String counter, int loops) {
        emit("{");
        algorithm(scope, loops + 1);
        useVariable(scope, counter);
        emit("=");
        emit("add");
        emit("(");
        useVariable(scope, counter);
        emit("1");
        emit(")");
        emit(";");
        emit("}");
    }

    // while and ( lesser ( #k bound ) ANY )   or   until or ( larger ( #k bound ) ANY )
    // tthe extra condition can only end the loop earlier
    private void guard(Scope scope, String counter, String bound, boolean whileForm) {
        boolean extra = random.nextInt(3) == 0;
        if (extra) {
            emit(whileForm ? "and" : "or");
            emit("(");
        }
        emit(whileForm ? "lesser" : "larger");
        emit("(");
        useVariable(scope, counter);
        emit(bound);
        emit(")");
        if (extra) {
            condition(scope, 1);
            emit(")");
        }
    }

    private void call(Scope scope, Function function, int depth) {
        emit(function.name, Role.FUNCTION_USE, function.entity);
        emit("(");
        for (int i = 0; i < function.arity; i++) {
            term(scope, depth + 1);
        }
        emit(")");
    }

    private void term(Scope scope, int depth) {
        Map<String, Integer> visible = visible(scope);
        List<Function> values = functions(scope, true);
        switch (random.nextInt(depth < 4 ? 9 : 2)) {
            case 0:
                emit(number());
                break;
            case 1:
            case 2:
                List<String> names = new ArrayList<>(visible.keySet());
                useVariable(scope, names.get(random.nextInt(names.size())));
                break;
            case 3:
                if (values.isEmpty()) {
                    emit(number());
                } else {
                    call(scope, values.get(random.nextInt(values.size())), depth);
                }
                break;
            case 4:
                emit("neg");
                emit("(");
                term(scope, depth + 1);
                emit(")");
                break;
            default:
                emit(operator());
                emit("(");
                term(scope, depth + 1);
                term(scope, depth + 1);
                emit(")");
        }
    }

    private String operator() {
        int choice = random.nextInt(4);
        if (choice < 3) {
            return new String[] {"add", "sub", "mul"}[choice];
        }
        return numbers == Numbers.MOD ? "mod" : numbers == Numbers.DIV ? "div" : "add";
    }

    private String number() {
        return decimals && random.nextBoolean() ? pick(DECIMAL) : pick(WHOLE);
    }

    private void condition(Scope scope, int depth) {
        switch (random.nextInt(depth < 3 ? 6 : 3)) {
            case 0:
            case 1:
            case 2:
                emit(pick(COMPARISONS));
                emit("(");
                term(scope, depth + 1);
                term(scope, depth + 1);
                emit(")");
                break;
            case 3:
                emit("not");
                emit("(");
                condition(scope, depth + 1);
                emit(")");
                break;
            default:
                emit(random.nextBoolean() ? "and" : "or");
                emit("(");
                condition(scope, depth + 1);
                condition(scope, depth + 1);
                emit(")");
        }
    }

    private void useVariable(Scope scope, String name) {
        emit(name, Role.VARIABLE_USE, visible(scope).get(name));
    }

    // the variables a scope can see(nearest declaration of a name wins)
    private static Map<String, Integer> visible(Scope scope) {
        Map<String, Integer> names = new TreeMap<>();
        for (Scope around = scope; around != null; around = around.parent) {
            for (Map.Entry<String, Integer> entry : around.variables.entrySet()) {
                names.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }
        return names;
    }

    // the functions of the immediate function list that return a or not return a value 
    private static List<Function> functions(Scope scope, boolean returnsValue) {
        List<Function> result = new ArrayList<>();
        for (Function function : scope.functions) {
            if (function.returnsValue == returnsValue) {
                result.add(function);
            }
        }
        return result;
    }

    private List<String> distinct(String[] pool, int count, Collection<String> taken) {
        List<String> free = new ArrayList<>();
        for (String name : pool) {
            if (!taken.contains(name)) {
                free.add(name);
            }
        }
        Collections.shuffle(free, random);
        return new ArrayList<>(free.subList(0, Math.min(count, free.size())));
    }
}