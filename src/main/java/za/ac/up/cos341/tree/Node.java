package za.ac.up.cos341.tree;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

// one node of the syntax tree
public final class Node {
    private final int id;
    private final NodeKind kind;
    private final String contents;
    private Integer parent;
    private final List<Integer> children = new ArrayList<>();

    Node(int id, NodeKind kind, String contents, Integer parent) {
        this.id = id;
        this.kind = kind;
        this.contents = contents;
        this.parent = parent;
    }

    // this ID is unique for each node inside  tree
    public int getId() {
        return id;
    }

    // this is a non-terminal symbol for a root or inner node and it is a terminal token for a leaf node
    public String getContents() {
        return contents;
    }

    // the kind can be root or inner node or leaf
    public NodeKind getKind() {
        return kind;
    }

    // The ID of the node above this one (null for the root)
    public Integer getParent() {
        return parent;
    }

    // The IDs of the immediate children in the order they were created
    public List<Integer> getChildren() {
        return Collections.unmodifiableList(children);
    }

    // to add a child node 
    void addChild(int childId) {
        children.add(childId);
    }

    @Override
    public String toString() {
        return kind + "(" + id + ", " + contents + ")";
    }
}