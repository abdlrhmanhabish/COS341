package za.ac.up.cos341.tree;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

// Writes a SyntaxTree out as the tree.xml file. The root first, then every inner node, then every leaf
// each node carrys its unique ID and its links to the neighbouring nodes
public final class XmlWriter {
    private static final String NEWLINE = "\n";
    private static final String INDENT = "  ";
    private XmlWriter() {}

    /**
     * Serialises the tree and writes it to {@code outputPath} as UTF-8. It creats a file or replaces an existing one.
     *
     * @throws IllegalStateException if the tree has no root node
     * @throws IOException           if the file cannot be written
     */
    public static void write(SyntaxTree tree, Path outputPath) throws IOException {
        String xml = toXmlString(tree);
        Path parent = outputPath.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(outputPath, xml, StandardCharsets.UTF_8);
    }

    /**
     * Serialises the tree into the exact text that {@link #write} would store.
     *
     * @throws IllegalStateException if the tree has no root node
     */
    public static String toXmlString(SyntaxTree tree) {
        if (tree == null) {
            throw new IllegalArgumentException("There is no tree to write.");
        }

        Node root = tree.getRoot();
        if (root == null) {
            throw new IllegalStateException("The tree has no root node, so there is nothing to write; call createRoot first.");
        }

        StringBuilder builder = new StringBuilder();
        builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>").append(NEWLINE);
        builder.append("<SYNTREE>").append(NEWLINE);
        appendRoot(builder, root);
        appendInnerNodes(builder, tree.getNodes());
        appendLeafNodes(builder, tree.getNodes());
        builder.append("</SYNTREE>").append(NEWLINE);
        return builder.toString();
    }

    private static void appendValue(StringBuilder out, int level, String tag, String value) {
        indent(out, level).append('<').append(tag).append('>').append(value).append("</").append(tag).append('>').append(NEWLINE);
    }
    private static void appendRoot(StringBuilder out, Node root) {
        indent(out, 1).append("<ROOT>").append(NEWLINE);
        appendValue(out, 2, "UNID", Integer.toString(root.getId()));
        appendValue(out, 2, "SYMB", escape(root.getContents()));
        appendChildren(out, 2, root);
        indent(out, 1).append("</ROOT>").append(NEWLINE);
    }

    private static void appendInnerNodes(StringBuilder out, List<Node> nodes) {
        indent(out, 1).append("<INNERNODES>").append(NEWLINE);
        for (Node node : nodes) {
            if (node.getKind() != NodeKind.INNER) {
                continue;
            }
            indent(out, 2).append("<IN>").append(NEWLINE);
            appendValue(out, 3, "PARENT", Integer.toString(node.getParent()));
            appendValue(out, 3, "UNID", Integer.toString(node.getId()));
            appendValue(out, 3, "SYMB", escape(node.getContents()));
            appendChildren(out, 3, node);
            indent(out, 2).append("</IN>").append(NEWLINE);
        }
        indent(out, 1).append("</INNERNODES>").append(NEWLINE);
    }

    private static void appendLeafNodes(StringBuilder out, List<Node> nodes) {
        indent(out, 1).append("<LEAFNODES>").append(NEWLINE);
        for (Node node : nodes) {
            if (node.getKind() != NodeKind.LEAF) {
                continue;
            }
            indent(out, 2).append("<LEAF>").append(NEWLINE);
            appendValue(out, 3, "PARENT", Integer.toString(node.getParent()));
            appendValue(out, 3, "UNID", Integer.toString(node.getId()));
            appendValue(out, 3, "TERMINAL", escape(node.getContents()));
            indent(out, 2).append("</LEAF>").append(NEWLINE);
        }
        indent(out, 1).append("</LEAFNODES>").append(NEWLINE);
    }

    private static void appendChildren(StringBuilder out, int level, Node node) {
        indent(out, level).append("<CHILDREN>").append(NEWLINE);
        for (int child : node.getChildren()) {
            appendValue(out, level + 1, "ID", Integer.toString(child));
        }
        indent(out, level).append("</CHILDREN>").append(NEWLINE);
    }


    private static StringBuilder indent(StringBuilder out, int level) {
        for (int i = 0; i < level; i++) {
            out.append(INDENT);
        }
        return out;
    }

    //replaces the five characters that XML reserves. That way the SPL tokens such as strings with punctuation survive into the output file unchanged
    static String escape(String text) {
        StringBuilder escaped = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char symbol = text.charAt(i);
            switch (symbol) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&apos;");
                default -> escaped.append(symbol);
            }
        }
        return escaped.toString();
    }
}