package za.ac.up.cos341.semantics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import za.ac.up.cos341.lexer.Token;

// The static symbol table of an SPL program. 
public final class SymbolTable {

    private static final String NEWLINE = "\n";

    private final List<Symbol> symbols = new ArrayList<>();
    private final Map<Integer, Symbol> names = new TreeMap<>();
    private final Map<Integer, Integer> levels = new HashMap<>();
    private final Map<Integer, Type> types = new HashMap<>();
    private NumberType numbers;

    void add(Symbol symbol) {
        symbols.add(symbol);
        names.put(symbol.getDeclaration(), symbol);
    }

    void bind(int nameLeaf, Symbol symbol) {
        names.put(nameLeaf, symbol);
    }

    void setLevel(int node, int level) {
        levels.put(node, level);
    }

    void setType(int node, Type type) {
        types.put(node, type);
    }

    void setNumbers(NumberType numbers) {
        this.numbers = numbers;
    }

    // symbols in the order of their declarations
    public List<Symbol> getSymbols() {
        return Collections.unmodifiableList(symbols);
    }

    // the symbol a NAME leaf stands for or null for every other node
    public Symbol symbolOf(int nameLeaf) {
        return names.get(nameLeaf);
    }

    // the UNIDs of the NAME leaves where a symbol is used
    public List<Integer> usesOf(Symbol symbol) {
        List<Integer> uses = new ArrayList<>();
        for (Map.Entry<Integer, Symbol> entry : names.entrySet()) {
            if (entry.getValue() == symbol && entry.getKey() != symbol.getDeclaration()) {
                uses.add(entry.getKey());
            }
        }
        return uses;
    }

    //scope level of a node or -1 for an ID that is not in the tree
    public int levelOf(int node) {
        Integer level = levels.get(node);
        return level == null ? -1 : level;
    }

    // the type that phase 2b gave the node
    public Type typeOf(int node) {
        Type type = types.get(node);
        return type == null ? Type.UNKNOWN : type;
    }

    // integers or floating point numbers. It stays null until the type analysis has run
    public NumberType getNumbers() {
        return numbers;
    }

    // the table as plain text.main writes it to symbol-table.txt
    public String toText(LeafTokens leaves) {
        StringBuilder out = new StringBuilder();
        out.append("Symbol table").append(NEWLINE).append(NEWLINE);
        if (numbers != null) {
            out.append("All numbers of the program are ").append(numbers).append('.').append(NEWLINE).append(NEWLINE);
        }

        if (symbols.isEmpty()) {
            return out.append("The program declares no names.").append(NEWLINE).toString();
        }
        Map<Symbol, List<String>> uses = new IdentityHashMap<>();
        for (Map.Entry<Integer, Symbol> entry : names.entrySet()) {
            Symbol symbol = entry.getValue();
            if (entry.getKey() != symbol.getDeclaration()) {
                uses.computeIfAbsent(symbol, key -> new ArrayList<>()).add(position(leaves, entry.getKey()));
            }
        }
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {"Internal name", "SPL name", "Kind", "Type", "Level", "Scope", "Declared at", "Used at"});
        for (Symbol symbol : symbols) {
            List<String> used = uses.get(symbol);
            rows.add(new String[] {symbol.getInternalName(), symbol.getName(), kindOf(symbol),
                    symbol.getType().toString(), Integer.toString(symbol.getLevel()),
                    symbol.getOwner() == null ? "main" : symbol.getOwner().getInternalName(),
                    position(leaves, symbol.getDeclaration()), used == null ? "-" : String.join(", ", used)});
        }
        int[] widths = new int[rows.get(0).length];
        for (String[] row : rows) {
            for (int i = 0; i < row.length; i++) {
                widths[i] = Math.max(widths[i], row[i].length());
            }
        }
        for (int r = 0; r < rows.size(); r++) {
            appendRow(out, rows.get(r), widths);
            if (r == 0) {
                String[] rule = new String[widths.length];
                for (int i = 0; i < widths.length; i++) {
                    rule[i] = repeat('-', widths[i]);
                }
                appendRow(out, rule, widths);
            }
        }
        out.append(NEWLINE).append("Positions are written as line:column. The number in brackets is the UNID")
                .append(NEWLINE).append("of the NAME leaf in tree.xml.").append(NEWLINE);
        return out.toString();
    }

    private static String kindOf(Symbol symbol) {
        switch (symbol.getKind()) {
            case FUNCTION:
                return symbol.returnsValue() ? "num function" : "void function";
            case PARAMETER:
                return "parameter";
            default:
                return "variable";
        }
    }

    private static String position(LeafTokens leaves, int leaf) {
        Token token = leaves.tokenOf(leaf);
        return token == null ? "(" + leaf + ")" : token.line() + ":" + token.column() + " (" + leaf + ")";
    }

    private static void appendRow(StringBuilder out, String[] cells, int[] widths) {
        StringBuilder line = new StringBuilder();
        
        for (int i = 0; i < cells.length; i++) {
            line.append(cells[i]);
            if (i < cells.length - 1) {
                line.append(repeat(' ', widths[i] - cells[i].length() + 2));
            }
        }
        out.append(line).append(NEWLINE);
    }

    private static String repeat(char c, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            result.append(c);
        }
        return result.toString();
    }
}