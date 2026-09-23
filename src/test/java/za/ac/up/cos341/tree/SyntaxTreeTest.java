package za.ac.up.cos341.tree;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;

class SyntaxTreeTest {

    @Test
    void idsStartAtZeroAndCountUpPerTree() {
        SyntaxTree first = new SyntaxTree();
        Node firstRoot = first.createRoot("SPL_PROG");
        Node firstChild = first.createInner("P", firstRoot);
        SyntaxTree second = new SyntaxTree();
        Node secondRoot = second.createRoot("SPL_PROG");

        assertEquals(0, firstRoot.getId());
        assertEquals(1, firstChild.getId());
        assertEquals(0, secondRoot.getId(), "each tree numbers its own nodes from 0");
    }

    @Test
    void rootHasNoParentAndIsFoundBackByIdAndByGetRoot() {
        SyntaxTree tree = new SyntaxTree();
        Node root = tree.createRoot("SPL_PROG");

        assertNull(root.getParent());
        assertEquals(NodeKind.ROOT, root.getKind());
        assertSame(root, tree.getRoot());
        assertSame(root, tree.getNode(0));
        assertNull(tree.getNode(7), "unknown IDs give back nothing");
    }

    @Test
    void secondRootIsRejected() {
        SyntaxTree tree = new SyntaxTree();
        tree.createRoot("SPL_PROG");
        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> tree.createRoot("SPL_PROG"));

        assertTrue(thrown.getMessage().contains("already has a root"));
    }

    @Test
    void rootWithTwoLeafChildrenLinksBothWays() {
        SyntaxTree tree = new SyntaxTree();
        Node root = tree.createRoot("SPL_PROG");
        Node left = tree.createLeaf("nop", root);
        Node right = tree.createLeaf("$", root);

        assertEquals(List.of(1, 2), root.getChildren());
        assertEquals(0, left.getParent());
        assertEquals(0, right.getParent());
        assertEquals(NodeKind.LEAF, left.getKind());
        assertEquals(3, tree.size());
    }

    @Test
    void threeLevelNestingKeepsEveryLink() {
        SyntaxTree tree = new SyntaxTree();
        Node root = tree.createRoot("SPL_PROG");
        Node outer = tree.createInner("P", root);
        Node inner = tree.createInner("ALGO", outer);
        Node leaf = tree.createLeaf("nop", inner);

        assertEquals(List.of(1), root.getChildren());
        assertEquals(List.of(2), outer.getChildren());
        assertEquals(List.of(3), inner.getChildren());
        assertEquals(0, outer.getParent());
        assertEquals(1, inner.getParent());
        assertEquals(2, leaf.getParent());
        assertTrue(leaf.getChildren().isEmpty());
    }

    @Test
    void aLeafCannotBecomeAParent() {
        SyntaxTree tree = new SyntaxTree();
        Node root = tree.createRoot("SPL_PROG");
        Node leaf = tree.createLeaf("print", root);
        IllegalArgumentException fromInner = assertThrows(IllegalArgumentException.class, () -> tree.createInner("OUTP", leaf));
        IllegalArgumentException fromLeaf = assertThrows(IllegalArgumentException.class, () -> tree.createLeaf("nop", leaf));

        assertTrue(fromInner.getMessage().contains("is a leaf"));
        assertTrue(fromLeaf.getMessage().contains("is a leaf"));
        assertEquals(2, tree.size(), "a rejected node is not kept in the tree");
    }

    @Test
    void aParentFromAnotherTreeIsRejected() {
        SyntaxTree tree = new SyntaxTree();
        tree.createRoot("SPL_PROG");
        SyntaxTree other = new SyntaxTree();
        Node otherRoot = other.createRoot("SPL_PROG");

        assertThrows(IllegalArgumentException.class, () -> tree.createInner("P", otherRoot));
        assertThrows(IllegalArgumentException.class, () -> tree.createLeaf("$", null));
    }

    @Test
    void theChildrenListIsNotEditableFromOutside() {
        SyntaxTree tree = new SyntaxTree();
        Node root = tree.createRoot("SPL_PROG");

        assertThrows(UnsupportedOperationException.class, () -> root.getChildren().add(99));
    }

    @Test
    void getNodesComesBackInAscendingIdOrder() {
        SyntaxTree tree = new SyntaxTree();
        Node root = tree.createRoot("SPL_PROG");
        Node p = tree.createInner("P", root);
        tree.createLeaf("$", root);
        tree.createLeaf("nop", p);
        List<Node> nodes = tree.getNodes();
        
        for (int i = 0; i < nodes.size(); i++) {
            assertEquals(i, nodes.get(i).getId());
        }
    }
}