package za.ac.up.cos341.semantics;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import za.ac.up.cos341.tree.Node;
import za.ac.up.cos341.tree.NodeKind;
import za.ac.up.cos341.tree.SyntaxTree;

// these are helpers that know the shape of SPL syntax trees.

public final class Trees {

    private Trees() {
    }

    // every link of a list including the empty one at the end
    public static List<Node> links(SyntaxTree tree, Node list) {
        List<Node> links = new ArrayList<>();
        Node link = list;
        links.add(link);
        while (!link.getChildren().isEmpty()) {
            List<Integer> children = link.getChildren();
            link = tree.getNode(children.get(children.size() - 1));
            links.add(link);
        }
        return links;
    }

    // the items of a list: INSTR nodes of an ALGO, NAME leaves of a V_DECL, F_TYPE nodes of an F_DECL and TERM nodes of an INPUT
    public static List<Node> items(SyntaxTree tree, Node list) {
        List<Node> items = new ArrayList<>();
        Node link = list;
        while (!link.getChildren().isEmpty()) {
            List<Integer> children = link.getChildren();
            items.add(tree.getNode(children.get(0)));
            link = tree.getNode(children.get(children.size() - 1));
        }
        return items;
    }

    // true for a leaf that holds a user-defined name
    public static boolean isName(Node node) {
        return node.getKind() == NodeKind.LEAF && node.getContents().startsWith("#");
    }

    // true for a leaf that holds a number
    public static boolean isNumber(Node node) {
        if (node.getKind() != NodeKind.LEAF) {
            return false;
        }

        char first = node.getContents().charAt(0);
        return first == '-' || (first >= '0' && first <= '9');
    }

    // all nodes in pre-order  and for the leaves this is the order of the tokens in the file
    public static List<Node> preOrder(SyntaxTree tree) {
        List<Node> nodes = new ArrayList<>(tree.size());
        Deque<Node> stack = new ArrayDeque<>();
        stack.push(tree.getRoot());
        while (!stack.isEmpty()) {
            Node node = stack.pop();
            nodes.add(node);
            List<Integer> children = node.getChildren();
            for (int i = children.size() - 1; i >= 0; i--) {
                stack.push(tree.getNode(children.get(i)));
            }
        }
        return nodes;
    }
}