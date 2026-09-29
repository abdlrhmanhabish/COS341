package za.ac.up.cos341;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

class MainTest {

    @TempDir
    Path folder;

    // everything the program prints is plain ASCII
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    private int run(String... args) {
        return Main.run(args, folder, new PrintStream(out, true), new PrintStream(err, true));
    }

    private String output() {
        return out.toString().replace(System.lineSeparator(), "\n");
    }

    private String errors() {
        return err.toString().replace(System.lineSeparator(), "\n");
    }

    private void write(Path file, String text) throws IOException {
        Files.write(file, text.getBytes(StandardCharsets.US_ASCII));
    }

    private void input(String text) throws IOException {
        write(folder.resolve("SPL.txt"), text);
    }

    private static String repeat(String text, int count) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < count; i++) {
            result.append(text);
        }
        return result.toString();
    }

    private String lineStartingWith(String start) {
        return Arrays.stream(errors().split("\n")).filter(line -> line.startsWith(start)).findFirst().get();
    }

    @Test
    void correctProgramGetsItsTreeWrittenBesideTheInput() throws Exception {
        input("#x\n:\nnum #inc ( #n ) { : : return ( add ( #n 1 ) ) }\n:\n#x = #inc ( 5 ) ;\nprint ( #x ) ;\n");
        assertEquals(0, run());
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(folder.resolve("tree.xml").toFile());
        assertEquals("SPL_PROG", document.getElementsByTagName("SYMB").item(0).getTextContent());
        assertLinksAreConsistent(document);
        assertTrue(output().startsWith("SPL.txt contains a syntactically correct SPL program."), output());
        assertEquals("", errors());
    }

    // every UNID is unique and every CHILDREN entry points to a node whose PARENT points back
    private static void assertLinksAreConsistent(Document document) {
        Map<String, Element> nodes = new HashMap<>();
        NodeList ids = document.getElementsByTagName("UNID");
        for (int i = 0; i < ids.getLength(); i++) {
            Element node = (Element) ids.item(i).getParentNode();
            assertNull(nodes.put(ids.item(i).getTextContent(), node), "duplicate UNID " + ids.item(i).getTextContent());
        }
        int links = 0;
        for (Map.Entry<String, Element> entry : nodes.entrySet()) {
            NodeList children = entry.getValue().getElementsByTagName("ID");
            for (int i = 0; i < children.getLength(); i++) {
                Element child = nodes.get(children.item(i).getTextContent());
                assertEquals(entry.getKey(), child.getElementsByTagName("PARENT").item(0).getTextContent());
                links++;
            }
        }
        assertEquals(nodes.size() - 1, links, "every node except the root has exactly one parent");
    }

    @Test
    void syntaxErrorWritesNoTreeAndRemovesAnOldOne() throws IOException {
        write(folder.resolve("tree.xml"), "<old/>");
        input(": :\nprint \"a\"\nnop ;\n");
        assertEquals(1, run());
        assertFalse(Files.exists(folder.resolve("tree.xml")));
        String report = errors();
        assertTrue(report.startsWith("Syntax error in SPL.txt on line 3, column 1:\n"
                + "  Found 'nop' but expected ';' to end the instruction.\n\n"
                + "    3 | nop ;\n"
                + "      | ^^^\n\n"
                + "Hint: Every instruction must end with ';'. The instruction that needs it\n"
                + "      starts on line 2.\n"), report);
        assertTrue(report.contains("The tree.xml of an earlier run was deleted."), report);
    }

    @Test
    void lexicalErrorIsReportedWithItsHint() throws IOException {
        input("#x\n:\n:\n#x = 1.50 ;\n");
        assertEquals(1, run());
        String report = errors();
        assertTrue(report.startsWith("Lexical error in SPL.txt on line 4, column 6:\n"
                + "  The text 1.50 is not a valid SPL token.\n"), report);
        assertTrue(report.contains("      |      ^^^^\n"), report);
        assertTrue(report.contains("Write 1.5 instead of 1.50"), report);
    }

    @Test
    void markerFollowsTabs() throws IOException {
        input(": :\n\t\twhile eq ( 1 1 ) do { nop ; } nop ;\n");
        assertEquals(1, run());
        // the second nop starts in column 33, behind two tabs and 30 other characters
        assertTrue(errors().contains("      | \t\t" + repeat(" ", 30) + "^^^\n"), errors());
    }

    @Test
    void longLinesAreCutAroundTheError() throws IOException {
        String filler = repeat("nop ; ", 40);
        input(": : " + filler + "print \"a\" nop ; " + filler);
        assertEquals(1, run());
        String shown = lineStartingWith("    1 | ");
        String marker = lineStartingWith("      | ");
        assertTrue(shown.startsWith("    1 | ...") && shown.endsWith("..."), shown);
        assertTrue(shown.length() <= 80, shown);
        assertEquals(shown.indexOf("\"a\" nop") + 4, marker.indexOf('^'));
    }

    @Test
    void fileGivenAsArgumentGetsItsTreeInItsOwnFolder() throws IOException {
        Path cases = Files.createDirectories(folder.resolve("cases"));
        write(cases.resolve("first.txt"), ": : nop ;");
        assertEquals(0, run("cases/first.txt"));
        assertTrue(Files.exists(cases.resolve("tree.xml")));
        assertFalse(Files.exists(folder.resolve("tree.xml")));
    }

    @Test
    void byteOrderMarkAndCarriageReturnLineBreaksAreAccepted() throws IOException {
        byte[] text = ": :\rnop ;\rprint \"x\" ;\r".getBytes(StandardCharsets.US_ASCII);
        byte[] withMark = new byte[text.length + 3];
        withMark[0] = (byte) 0xEF;
        withMark[1] = (byte) 0xBB;
        withMark[2] = (byte) 0xBF;
        System.arraycopy(text, 0, withMark, 3, text.length);
        Files.write(folder.resolve("SPL.txt"), withMark);
        assertEquals(0, run());
    }

    @Test
    void carriageReturnsCountAsLineBreaksInMessages() throws IOException {
        input(": :\rnop ;\rprint \"x\"\rnop ;");
        assertEquals(1, run());
        assertTrue(errors().startsWith("Syntax error in SPL.txt on line 4, column 1:"), errors());
    }

    @Test
    void emptyFileIsASyntaxError() throws IOException {
        input("");
        assertEquals(1, run());
        assertTrue(errors().contains("The file contains no SPL tokens."), errors());
    }

    @Test
    void missingInputIsReported() {
        assertEquals(2, run());
        assertTrue(errors().startsWith("Could not find the input file"), errors());
        assertTrue(errors().contains("SPL.txt.txt"), errors());
        assertEquals(2, run("other.txt"));
    }

    @Test
    void helpOptionPrintsTheUsage() {
        assertEquals(0, run("--help"));
        assertTrue(output().startsWith("Usage: "), output());
    }

    @Test
    void moreThanOneArgumentIsRefused() {
        assertEquals(2, run("a.txt", "b.txt"));
        assertTrue(errors().startsWith("Usage: "), errors());
    }
}