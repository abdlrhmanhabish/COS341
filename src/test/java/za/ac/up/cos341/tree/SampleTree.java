package za.ac.up.cos341.tree;

import java.nio.file.Path;
import java.io.IOException;

/**
 * Builds a small syntax tree by hand shaped like the SPL program:
 *
 * <pre>
 * #x :
 * :
 * #x = 3 ; print "hello, world!" ;
 * </pre>
 *
 */
public final class SampleTree {
    static final Path SAMPLE_FILE = Path.of("tests", "sample-tree.xml");
    private SampleTree() {}

    static SyntaxTree build() {
        SyntaxTree tree = new SyntaxTree();
        Node splProg = tree.createRoot("SPL_PROG");
        Node p = tree.createInner("P", splProg);

        Node varDecl = tree.createInner("V_DECL", p);
        tree.createLeaf("#x", varDecl);
        tree.createInner("V_DECL", varDecl);

        tree.createLeaf(":", p);
        tree.createInner("F_DECL", p);
        tree.createLeaf(":", p);

        Node algo = tree.createInner("ALGO", p);
        Node assignInstr = tree.createInner("INSTR", algo);
        Node assign = tree.createInner("ASSIGN", assignInstr);
        tree.createLeaf("#x", assign);
        tree.createLeaf("=", assign);
        Node assignedTerm = tree.createInner("TERM", assign);
        tree.createLeaf("3", assignedTerm);
        tree.createLeaf(";", algo);

        Node restOfAlgo = tree.createInner("ALGO", algo);
        Node printInstr = tree.createInner("INSTR", restOfAlgo);
        tree.createLeaf("print", printInstr);
        Node outp = tree.createInner("OUTP", printInstr);
        tree.createLeaf("\"hello, world!\"", outp);
        tree.createLeaf(";", restOfAlgo);
        tree.createInner("ALGO", restOfAlgo);
        tree.createLeaf("$", splProg);
        return tree;
    }

    public static void main(String[] args) throws IOException {
        Path target = args.length > 0 ? Path.of(args[0]) : SAMPLE_FILE;
        XmlWriter.write(build(), target);
        System.out.println("Wrote " + target.toAbsolutePath());
    }
}