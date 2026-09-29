package za.ac.up.cos341.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import za.ac.up.cos341.lexer.Token;
import za.ac.up.cos341.tree.Node;
import za.ac.up.cos341.tree.NodeKind;
import za.ac.up.cos341.tree.SyntaxTree;

// this checks that a syntax tree is a derivation tree of the SPL grammar for a given list of tokens
final class TreeCheck {

    //Rule 0 reads SPL_PROG -> P $ there, but $ is not part of the input, so the tree has no leaf for it
    private static final String[] GRAMMAR = {
            "SPL_PROG -> P",
            "P -> V_DECL : F_DECL : ALGO",
            "V_DECL ->",
            "V_DECL -> NAME V_DECL",
            "F_DECL ->",
            "F_DECL -> F_TYPE F_DECL",
            "F_TYPE -> void NAME ( V_DECL ) { P return }",
            "F_TYPE -> num NAME ( V_DECL ) { P return ( TERM ) }",
            "ALGO ->",
            "ALGO -> INSTR ; ALGO",
            "OUTP -> ( TERM )",
            "OUTP -> STRING",
            "INSTR -> print OUTP",
            "INSTR -> nop",
            "INSTR -> comment STRING",
            "INSTR -> ASSIGN",
            "INSTR -> BRANCH",
            "INSTR -> LOOP",
            "INSTR -> CALL",
            "CALL -> NAME ( INPUT )",
            "INPUT ->",
            "INPUT -> TERM INPUT",
            "ASSIGN -> NAME = TERM",
            "TERM -> NAME",
            "TERM -> NUM",
            "TERM -> CALL",
            "TERM -> mod ( TERM TERM )",
            "TERM -> add ( TERM TERM )",
            "TERM -> sub ( TERM TERM )",
            "TERM -> mul ( TERM TERM )",
            "TERM -> div ( TERM TERM )",
            "TERM -> neg ( TERM )",
            "BRANCH -> if BOOL then { ALGO } else { ALGO }",
            "BOOL -> not ( BOOL )",
            "BOOL -> and ( BOOL BOOL )",
            "BOOL -> or ( BOOL BOOL )",
            "BOOL -> eq ( TERM TERM )",
            "BOOL -> larger ( TERM TERM )",
            "BOOL -> lesser ( TERM TERM )",
            "LOOP -> COND BOOL do { ALGO }",
            "LOOP -> do { ALGO } COND BOOL",
            "COND -> while",
            "COND -> until"
    };

    private static final Map<String, Set<List<String>>> PRODUCTIONS = new HashMap<>();

    static {
        for (String rule : GRAMMAR) {
            String[] sides = rule.split("->");
            String right = sides.length > 1 ? sides[1].trim() : "";
            List<String> symbols = right.isEmpty() ? Collections.<String>emptyList() : Arrays.asList(right.split("\\s+"));
            PRODUCTIONS.computeIfAbsent(sides[0].trim(), key -> new HashSet<>()).add(symbols);
        }
    }

    private TreeCheck() {
    }

    // This Fails unless every inner node uses a production of the grammar
    static void assertDerivationTree(SyntaxTree tree, List<Token> tokens, String context) {
        Node root = tree.getRoot();
        assertEquals("SPL_PROG", root.getContents(), context);
        List<String> leaves = new ArrayList<>();
        int[] nextId = {0};
        walk(tree, root, null, leaves, nextId, context);
        assertEquals(tokens.stream().map(Token::value).collect(Collectors.toList()), leaves,
                context + ": the leaves differ from the tokens");
        assertEquals(tree.size(), nextId[0], context + ": some nodes are not reachable from the root");
    }

    private static void walk(SyntaxTree tree, Node node, Integer parent, List<String> leaves, int[] nextId, String context) {
        assertEquals(nextId[0]++, node.getId(), context + ": IDs are not in pre-order");
        assertEquals(parent, node.getParent(), context + ": wrong parent of node " + node.getId());
        if (node.getKind() == NodeKind.LEAF) {
            assertTrue(node.getChildren().isEmpty(), context);
            leaves.add(node.getContents());
            return;
        }
        List<String> symbols = new ArrayList<>();
        for (int child : node.getChildren()) {
            Node childNode = tree.getNode(child);
            symbols.add(childNode.getKind() == NodeKind.LEAF ? terminal(childNode.getContents()) : childNode.getContents());
        }
        Set<List<String>> productions = PRODUCTIONS.get(node.getContents());
        assertNotNull(productions, context + ": unknown non-terminal " + node.getContents());
        assertTrue(productions.contains(symbols),
                context + ": " + node.getContents() + " -> " + String.join(" ", symbols) + " is not in the grammar");
        for (int child : node.getChildren()) {
            walk(tree, tree.getNode(child), node.getId(), leaves, nextId, context);
        }
    }

    private static String terminal(String token) {
        if (token.startsWith("#")) {
            return "NAME";
        }
        if (token.startsWith("\"")) {
            return "STRING";
        }
        if (token.charAt(0) == '-' || Character.isDigit(token.charAt(0))) {
            return "NUM";
        }
        return token;
    }

    //  This is a compact bracket notation of a tree with quoted leaves. example: SPL_PROG(P(V_DECL() ':' F_DECL() ':' ALGO()))
    static String render(SyntaxTree tree) {
        StringBuilder out = new StringBuilder();
        render(tree, tree.getRoot(), out);
        return out.toString();
    }

    private static void render(SyntaxTree tree, Node node, StringBuilder out) {
        if (node.getKind() == NodeKind.LEAF) {
            out.append('\'').append(node.getContents()).append('\'');
            return;
        }
        out.append(node.getContents()).append('(');
        String separator = "";
        for (int child : node.getChildren()) {
            out.append(separator);
            render(tree, tree.getNode(child), out);
            separator = " ";
        }
        out.append(')');
    }
}