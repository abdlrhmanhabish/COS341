package za.ac.up.cos341.semantics;

import java.util.ArrayList;
import java.util.List;

import za.ac.up.cos341.lexer.Lexer;
import za.ac.up.cos341.lexer.Token;
import za.ac.up.cos341.parser.Parser;
import za.ac.up.cos341.tree.Node;
import za.ac.up.cos341.tree.NodeKind;
import za.ac.up.cos341.tree.SyntaxTree;

// Lex and parse a program for the tests of the semantic analysis
final class Analysis {

    final List<Token> tokens;
    final SyntaxTree tree;
    final LeafTokens leaves;

    Analysis(String source) {
        tokens = new Lexer().tokenize(source);
        tree = new Parser(tokens).parse();
        leaves = new LeafTokens(tree, tokens);
    }

    SymbolTable names() {
        return new NameAnalyser(tree, leaves).analyse();
    }

    SymbolTable types() {
        SymbolTable table = names();
        new TypeAnalyser(tree, leaves, table).analyse();

        return table;
    }

    // all leaves in pre-order: The k-th leaf was made from the k-th token
    List<Node> leaves() {
        List<Node> result = new ArrayList<>();
        for (Node node : Trees.preOrder(tree)) {
            if (node.getKind() == NodeKind.LEAF) {
                result.add(node);
            }
        }
        return result;
    }

    // the leaf that was made from the k-th token
    Node leaf(int token) {
        return leaves().get(token);
    }

    // all nodes with the given non-terminal in pre-order
    List<Node> all(String symbol) {
        List<Node> result = new ArrayList<>();

        for (Node node : Trees.preOrder(tree)) {
            if (node.getKind() != NodeKind.LEAF && node.getContents().equals(symbol)) {
                result.add(node);
            }
        }
        return result;
    }

    // the index of the n-th token with the given text counted from 0
    int token(String text, int occurrence) {
        int seen = 0;
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i).value().equals(text) && seen++ == occurrence) {
                return i;
            }
        }

        throw new IllegalArgumentException(text + " does not occur " + (occurrence + 1) + " times");
    }

    //the symbol of the nth occurrence of a name in the source
    Symbol symbol(SymbolTable table, String name, int occurrence) {
        return table.symbolOf(leaf(token(name, occurrence)).getId());
    }
}