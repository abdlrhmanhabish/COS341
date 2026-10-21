package za.ac.up.cos341.semantics;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import za.ac.up.cos341.lexer.Token;
import za.ac.up.cos341.tree.Node;
import za.ac.up.cos341.tree.NodeKind;
import za.ac.up.cos341.tree.SyntaxTree;

// Links every leaf of a syntax tree to the token it was made from. The parser hangs the leaves into the tree in the order in which it eats the tokens
//The analyzers need the tokens for the line and the column of the error messages
public final class LeafTokens {

    private final Map<Integer, Token> tokens = new HashMap<>();

    /**
     * @throws IllegalArgumentException if the tokens are not the leaves of the tree in this order
     */
    public LeafTokens(SyntaxTree tree, List<Token> tokenList) {
        Deque<Node> stack = new ArrayDeque<>();
        stack.push(tree.getRoot());
        int next = 0;
        while (!stack.isEmpty()) {
            Node node = stack.pop();
            if (node.getKind() == NodeKind.LEAF) {
                if (next >= tokenList.size() || !tokenList.get(next).value().equals(node.getContents())) {
                    throw new IllegalArgumentException("The tokens do not belong to this syntax tree.");
                }
                tokens.put(node.getId(), tokenList.get(next++));
                continue;
            }
            
            List<Integer> children = node.getChildren();
            for (int i = children.size() - 1; i >= 0; i--) {
                stack.push(tree.getNode(children.get(i)));
            }
        }
        if (next != tokenList.size()) {
            throw new IllegalArgumentException("The syntax tree has fewer leaves than there are tokens.");
        }
    }

    // the token of a leaf or null if the ID is not the ID of a leaf
    public Token tokenOf(int leaf) {
        return tokens.get(leaf);
    }
}