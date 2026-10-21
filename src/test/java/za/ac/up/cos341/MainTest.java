package za.ac.up.cos341;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
        assertTrue(output().startsWith("SPL.txt contains a correct SPL program.\n"), output());
        assertEquals("", errors());
        String table = new String(Files.readAllBytes(folder.resolve("symbol-table.txt")), StandardCharsets.UTF_8);
        assertTrue(table.contains("f1_inc"), table);
        assertTrue(output().endsWith("The symbol table was written to " + folder.resolve("symbol-table.txt") + "\n"), output());
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
        write(folder.resolve("symbol-table.txt"), "old");
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
        assertTrue(report.endsWith("No tree.xml was written.\nThe tree.xml of an earlier run was deleted.\n"
                + "The symbol-table.txt of an earlier run was deleted.\n"), report);
        assertFalse(Files.exists(folder.resolve("symbol-table.txt")));
    }

    @Test
    void namingErrorKeepsTheTreeButWritesNoSymbolTable() throws IOException {
        write(folder.resolve("symbol-table.txt"), "old");
        input("#x\n:\n:\n#y = 1 ;\n");
        assertEquals(3, run());
        assertTrue(Files.exists(folder.resolve("tree.xml")));
        assertFalse(Files.exists(folder.resolve("symbol-table.txt")));
        assertEquals("", output());
        assertEquals("SPL.txt is syntactically correct and its syntax tree was written to " + folder.resolve("tree.xml")
                + "\n\n"
                + "Naming error in SPL.txt on line 4, column 1:\n"
                + "  The variable #y is not declared.\n\n"
                + "    4 | #y = 1 ;\n"
                + "      | ^^\n\n"
                + "Hint: Declare #y in the variable list before the first ':' of the main\n"
                + "      program.\n\n"
                + "Found 1 naming error. No symbol table was written.\n"
                + "The symbol-table.txt of an earlier run was deleted.\n", errors());
    }

    @Test
    void typeErrorsEndWithTheirOwnExitCode() throws IOException {
        input("#x : : #x = mod ( 7 2 ) ;\n#x = div ( #x 2 ) ;\n");
        assertEquals(4, run());
        assertTrue(errors().contains("\n\nType error in SPL.txt on line 2, column 6:\n"
                + "  A float-integer-conflict might perhaps be possible\n"), errors());
        assertTrue(errors().endsWith("Found 1 type error. No symbol table was written.\n"), errors());
    }

    @Test
    void severalErrorsArePrintedInOrderAndLongListsAreCut() throws IOException {
        StringBuilder program = new StringBuilder(": :\n");
        for (int i = 1; i <= 25; i++) {
            program.append("#v").append(i).append(" = 1 ;\n");
        }
        input(program.toString());
        assertEquals(3, run());
        String report = errors();
        assertEquals(20, report.split("Naming error in SPL.txt").length - 1);
        assertTrue(report.indexOf("line 2, column 1") < report.indexOf("line 3, column 1"), report);
        assertTrue(report.endsWith("The other 5 errors are not shown.\n\n"
                + "Found 25 naming errors. No symbol table was written.\n"), report);
    }

    static Stream<Arguments> examplePrograms() throws IOException {
        List<Arguments> examples = new ArrayList<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(Paths.get("tests", "semantics"), "*_*.txt")) {
            for (Path file : files) {
                String name = file.getFileName().toString();
                int code = name.startsWith("valid") ? 0 : name.startsWith("naming") ? 3 : 4;
                examples.add(arguments(file, code));
            }
        }
        examples.add(arguments(Paths.get("tests", "tutor-p1", "T1_accept.txt"), 0));
        examples.add(arguments(Paths.get("tests", "tutor-p1", "T2_accept.txt"), 3));
        examples.add(arguments(Paths.get("tests", "tutor-p1", "T3_accept.txt"), 3));
        examples.add(arguments(Paths.get("tests", "tutor-p1", "T4_lex-err.txt"), 1));
        examples.add(arguments(Paths.get("tests", "tutor-p1", "T5_synt-err.txt"), 1));
        examples.add(arguments(Paths.get("tests", "tutor-p1", "T6_synt-err.txt"), 1));
        return examples.stream();
    }

    @ParameterizedTest
    @MethodSource("examplePrograms")
    void exampleProgramsEndWithTheExpectedExitCode(Path example, int code) throws IOException {
        Files.copy(example, folder.resolve("SPL.txt"));
        assertEquals(code, run(), errors());
        assertEquals(code == 0, Files.exists(folder.resolve("symbol-table.txt")));
        assertEquals(code != 1, Files.exists(folder.resolve("tree.xml")));
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