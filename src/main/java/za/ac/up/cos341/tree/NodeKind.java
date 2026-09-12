package za.ac.up.cos341.tree;

// The three kinds of node that the tree.xml output distinguish
public enum NodeKind {
    // The single start-symbol node of the tree. Has children, no parent
    ROOT,
    // A non-terminal below the root. Has both a parent and children
    INNER,
    // A terminal token eaten by the parser. Has a parent, no children
    LEAF
}