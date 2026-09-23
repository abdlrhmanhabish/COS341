package za.ac.up.cos341.tree;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

// This is the syntax tree that the parser builds while it works through an SPL program
// The tree owns its own ID counter. so every node of one tree carries an ID that occurs nowhere else in that tree. 
// The counter is an instance field and not static: two trees built in the same JVM do not interfere with each other, and both start counting at 0
public final class SyntaxTree {
    private Node root;
    private int nextId = 0;
    private final Map<Integer, Node> nodes = new LinkedHashMap<>();

    /**
     * Creates the root node holding the start symbol.
     * 
     * @param contents the start symbol. Normally {@code SPL_PROG}
     * @return the new root node
     * @throws IllegalStateException    if this tree already has a root
     * @throws IllegalArgumentException if {@code contents} is null
     */
    public Node createRoot(String contents) {
        if (root != null) {
            throw new IllegalStateException("This tree already has a root node (ID " + root.getId()
                    + "). A tree has to have exactly one root.");
        }
        Node node = newNode(NodeKind.ROOT, contents, null);
        root = node;
        return node;
    }

    /**
     * Creates an inner node for a non-terminal and links it under {@code parent}.
     *
     * @param contents the non-terminal from the SPL grammar
     * @param parent   the node above the new node
     * @return the new inner node
     * @throws IllegalArgumentException if the parent is null/leaf/not found in the
     *                                  tree or if {@code contents} is null
     */
    public Node createInner(String contents, Node parent) {
        checkParent(parent);
        Node node = newNode(NodeKind.INNER, contents, parent.getId());
        parent.addChild(node.getId());
        return node;
    }

    /**
     * Creates a leaf node for a terminal token and links it under {@code parent}.
     *
     * @param token  the token the parser has eaten. Example: {@code print} or
     *               {@code #counter}
     * @param parent the node above the new leaf
     * @return the new leaf node
     * @throws IllegalArgumentException if the parent is null/leaf/not found in the
     *                                  tree or if {@code contents} is null
     */
    public Node createLeaf(String token, Node parent) {
        checkParent(parent);
        Node node = newNode(NodeKind.LEAF, token, parent.getId());
        parent.addChild(node.getId());
        return node;
    }

    /**
     * Looks up a node by its unique ID.
     *
     * @return the node or {@code null} if this tree holds no node with that ID
     */
    public Node getNode(int id) {
        return nodes.get(id);
    }

    // Gives the root node or {@code null} while the tree is still empty.
    public Node getRoot() {
        return root;
    }

    // Gives all nodes of the tree in ascending ID order. The list is read-only.
    public List<Node> getNodes() {
        List<Node> all = new ArrayList<>(nodes.values());
        all.sort((left, right) -> Integer.compare(left.getId(), right.getId()));
        return Collections.unmodifiableList(all);
    }

    // How many nodes the tree currently holds.
    public int size() {
        return nodes.size();
    }

    // Creates a new node with the specified kind, contetns and parent.
    private Node newNode(NodeKind kind, String contents, Integer parentId) {
        if (contents == null) {
            throw new IllegalArgumentException("Node contents may not be null.");
        }
        Node node = new Node(nextId++, kind, contents, parentId);
        nodes.put(node.getId(), node);
        return node;
    }

    // Makes sure every child node has a valid parent node
    private void checkParent(Node parent) {
        if (parent == null) {
            throw new IllegalArgumentException("A child node needs a parent. pass the node it hangs under.");
        }
        if (nodes.get(parent.getId()) != parent) {
            throw new IllegalArgumentException("Parent node " + parent.getId() + " does not belong to this tree.");
        }
        if (parent.getKind() == NodeKind.LEAF) {
            throw new IllegalArgumentException("Node " + parent.getId() + " (" + parent.getContents()+ ") is a leaf. A terminal token cannot have children.");
        }
    }
}