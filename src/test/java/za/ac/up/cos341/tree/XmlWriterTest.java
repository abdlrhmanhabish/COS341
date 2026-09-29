package za.ac.up.cos341.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class XmlWriterTest {
    @Test
    void rootOnlyTreeStillCarriesBothEmptySections() {
        SyntaxTree tree = new SyntaxTree();
        tree.createRoot("SPL_PROG");

        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<SYNTREE>\n"
                + "  <ROOT>\n"
                + "    <UNID>0</UNID>\n"
                + "    <SYMB>SPL_PROG</SYMB>\n"
                + "    <CHILDREN>\n"
                + "    </CHILDREN>\n"
                + "  </ROOT>\n"
                + "  <INNERNODES>\n"
                + "  </INNERNODES>\n"
                + "  <LEAFNODES>\n"
                + "  </LEAFNODES>\n"
                + "</SYNTREE>\n", XmlWriter.toXmlString(tree));
    }

    @Test
    void rootWithTwoLeavesListsChildrenAndParents() {
        SyntaxTree tree = new SyntaxTree();
        Node root = tree.createRoot("SPL_PROG");
        tree.createLeaf("nop", root);
        tree.createLeaf("$", root);

        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<SYNTREE>\n"
                + "  <ROOT>\n"
                + "    <UNID>0</UNID>\n"
                + "    <SYMB>SPL_PROG</SYMB>\n"
                + "    <CHILDREN>\n"
                + "      <ID>1</ID>\n"
                + "      <ID>2</ID>\n"
                + "    </CHILDREN>\n"
                + "  </ROOT>\n"
                + "  <INNERNODES>\n"
                + "  </INNERNODES>\n"
                + "  <LEAFNODES>\n"
                + "    <LEAF>\n"
                + "      <PARENT>0</PARENT>\n"
                + "      <UNID>1</UNID>\n"
                + "      <TERMINAL>nop</TERMINAL>\n"
                + "    </LEAF>\n"
                + "    <LEAF>\n"
                + "      <PARENT>0</PARENT>\n"
                + "      <UNID>2</UNID>\n"
                + "      <TERMINAL>$</TERMINAL>\n"
                + "    </LEAF>\n"
                + "  </LEAFNODES>\n"
                + "</SYNTREE>\n", XmlWriter.toXmlString(tree));
    }

    @Test
    void threeLevelNestingWritesRootInnerNodesAndLeafSeparately() {
        SyntaxTree tree = new SyntaxTree();
        Node root = tree.createRoot("SPL_PROG");
        Node parent = tree.createInner("P", root);
        Node algo = tree.createInner("ALGO", parent);
        tree.createLeaf("nop", algo);

        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<SYNTREE>\n"
                + "  <ROOT>\n"
                + "    <UNID>0</UNID>\n"
                + "    <SYMB>SPL_PROG</SYMB>\n"
                + "    <CHILDREN>\n"
                + "      <ID>1</ID>\n"
                + "    </CHILDREN>\n"
                + "  </ROOT>\n"
                + "  <INNERNODES>\n"
                + "    <IN>\n"
                + "      <PARENT>0</PARENT>\n"
                + "      <UNID>1</UNID>\n"
                + "      <SYMB>P</SYMB>\n"
                + "      <CHILDREN>\n"
                + "        <ID>2</ID>\n"
                + "      </CHILDREN>\n"
                + "    </IN>\n"
                + "    <IN>\n"
                + "      <PARENT>1</PARENT>\n"
                + "      <UNID>2</UNID>\n"
                + "      <SYMB>ALGO</SYMB>\n"
                + "      <CHILDREN>\n"
                + "        <ID>3</ID>\n"
                + "      </CHILDREN>\n"
                + "    </IN>\n"
                + "  </INNERNODES>\n"
                + "  <LEAFNODES>\n"
                + "    <LEAF>\n"
                + "      <PARENT>2</PARENT>\n"
                + "      <UNID>3</UNID>\n"
                + "      <TERMINAL>nop</TERMINAL>\n"
                + "    </LEAF>\n"
                + "  </LEAFNODES>\n"
                + "</SYNTREE>\n", XmlWriter.toXmlString(tree));
    }

    @Test
    void innerNodesAndLeavesAreWrittenInAscendingIdOrder() {
        SyntaxTree tree = new SyntaxTree();
        Node root = tree.createRoot("SPL_PROG");
        Node parent = tree.createInner("P", root);
        tree.createLeaf("$", root);
        Node algo = tree.createInner("ALGO", parent);
        tree.createLeaf("nop", algo);
        String xml = XmlWriter.toXmlString(tree);

        assertTrue(xml.indexOf("<UNID>1</UNID>") < xml.indexOf("<UNID>3</UNID>"), "inner node 1 must be written before inner node 3");
        assertTrue(xml.indexOf("<TERMINAL>$</TERMINAL>") < xml.indexOf("<TERMINAL>nop</TERMINAL>"), "leaf 2 must be written before leaf 4");
    }

    @Test
    void punctuationAndReservedCharactersSurviveAsEscapes() {
        SyntaxTree tree = new SyntaxTree();
        Node root = tree.createRoot("SPL_PROG");
        tree.createLeaf("\"hello, world!\"", root);
        tree.createLeaf("\"a & b < c\"", root);
        String xml = XmlWriter.toXmlString(tree);

        assertTrue(xml.contains("<TERMINAL>&quot;hello, world!&quot;</TERMINAL>"));
        assertTrue(xml.contains("<TERMINAL>&quot;a &amp; b &lt; c&quot;</TERMINAL>"));
        assertFalse(xml.contains("a & b"), "a bare ampersand would break the XML");
    }

    @Test
    void everyReservedCharacterHasAnEscape() {
        assertEquals("&amp;&lt;&gt;&quot;&apos;", XmlWriter.escape("&<>\"'"));
        assertEquals("#counter", XmlWriter.escape("#counter"));
    }

    @Test
    void aTreeWithoutARootCannotBeWritten(@TempDir Path directory) {
        SyntaxTree empty = new SyntaxTree();
        Path target = directory.resolve("tree.xml");

        assertThrows(IllegalStateException.class, () -> XmlWriter.toXmlString(empty));
        assertThrows(IllegalStateException.class, () -> XmlWriter.write(empty, target));
        assertFalse(Files.exists(target), "no file may be left behind after the failure");
    }

    @Test
    void writeStoresTheSameTextAsToXmlString(@TempDir Path directory) throws IOException {
        SyntaxTree tree = new SyntaxTree();
        Node root = tree.createRoot("SPL_PROG");
        tree.createLeaf("\"str, with punctuation: ok!\"", root);
        Path target = directory.resolve("out").resolve("tree.xml");
        XmlWriter.write(tree, target);
        String written = new String(Files.readAllBytes(target), StandardCharsets.UTF_8);

        assertEquals(XmlWriter.toXmlString(tree), written);
        assertTrue(written.endsWith("</SYNTREE>\n"), "the file ends with a newline");
    }
}