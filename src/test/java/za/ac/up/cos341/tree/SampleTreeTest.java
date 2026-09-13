package za.ac.up.cos341.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;

class SampleTreeTest {

    @Test
    void theGeneratedSampleMatchesTheSampleTree() throws IOException {
        assertTrue(Files.exists(SampleTree.SAMPLE_FILE), "tests/sample-tree.xml is missing; regenerate it with SampleTree.main");
        String generated = Files.readString(SampleTree.SAMPLE_FILE, StandardCharsets.UTF_8);
        assertEquals(XmlWriter.toXmlString(SampleTree.build()), generated, "tests/sample-tree.xml is out of date; regenerate it with SampleTree.main");
    }

    @Test
    void everyNodeOfTheSampleAppearsExactlyOnceInTheOutput() {
        SyntaxTree tree = SampleTree.build();
        String xml = XmlWriter.toXmlString(tree);

        for (Node node : tree.getNodes()) {
            String unid = "<UNID>" + node.getId() + "</UNID>";
            assertEquals(1, countOccurrences(xml, unid), "node " + node.getId() + " must be written once");
        }
        assertEquals(tree.size(), countOccurrences(xml, "<UNID>"));
    }

    private static int countOccurrences(String text, String part) {
        int count = 0;
        int from = text.indexOf(part);
        while (from >= 0) {
            count++;
            from = text.indexOf(part, from + part.length());
        }
        return count;
    }
}