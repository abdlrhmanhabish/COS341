# SPL Compiler 

Compiler Construction Project (COS341, 2026).

## Syntax Tree and tree.xml Writer

This module is the **syntax tree data structure** and the **XML serialiser** for
the SPL compiler. It holds the tree that the parser builds while it reads an
SPL program. It also writes that tree out as a `tree.xml` file.


### What the output looks like

Every node carries a unique ID number. The file is split into three
sections: the root, the inner nodes, and the leaves. Inner nodes and leaves are
written in ascending ID order. A worked example can be found at
[tests/sample-tree.xml](tests/sample-tree.xml). It can be opened in a web browser to
inspect the structure.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<SYNTREE>
  <ROOT>
    <UNID>0</UNID>
    <SYMB>SPL_PROG</SYMB>
    <CHILDREN>
      <ID>1</ID>
    </CHILDREN>
  </ROOT>
  <INNERNODES>
    <IN>
      <PARENT>0</PARENT>
      <UNID>1</UNID>
      <SYMB>P</SYMB>
      <CHILDREN>
        <ID>2</ID>
      </CHILDREN>
    </IN>
  </INNERNODES>
  <LEAFNODES>
    <LEAF>
      <PARENT>1</PARENT>
      <UNID>2</UNID>
      <TERMINAL>print</TERMINAL>
    </LEAF>
  </LEAFNODES>
</SYNTREE>
```

### How the parser uses it

```java
SyntaxTree tree = new SyntaxTree();                    // owns its own ID counter which starts at 0
Node splProg = tree.createRoot("SPL_PROG");            // the start symbol
Node p = tree.createInner("P", splProg);               // non-terminal under the root
Node algo = tree.createInner("ALGO", p);
Node instr = tree.createInner("INSTR", algo);
tree.createLeaf("print", instr);                       // a token the parser has eaten
tree.createLeaf(";", algo);
tree.createLeaf("$", splProg);                         // end of file
XmlWriter.write(tree, Path.of("tree.xml"));            // throws IOException
```

Each `create*` call assigns the next free ID then sets the new node's parent and
appends the new ID to the parent's list of children. The caller never edits a
children list itself.

## Public API

`za.ac.up.cos341.tree.SyntaxTree`

| Method | Purpose |
| --- | --- |
| `Node createRoot(String contents)` | Creates the start-symbol node. Throws `IllegalStateException` if a root already exists. |
| `Node createInner(String contents, Node parent)` | Creates a non-terminal node under `parent`. |
| `Node createLeaf(String token, Node parent)` | Creates a terminal node under `parent`. |
| `Node getNode(int id)` | Looks a node up by ID. Returns `null` if there is none. |
| `Node getRoot()` | The root. Returns `null` while the tree is empty. |
| `List<Node> getNodes()` | All nodes in ascending ID order (read-only). |
| `int size()` | How many nodes the tree holds. |

`za.ac.up.cos341.tree.Node` exposes `getId()`, `getContents()`, `getKind()`,
`getParent()` and `getChildren()`.
`za.ac.up.cos341.tree.NodeKind` is `ROOT`, `INNER` or `LEAF`.

`za.ac.up.cos341.tree.XmlWriter`

| Method | Purpose |
| --- | --- |
| `static void write(SyntaxTree tree, Path outputPath)` | Writes the tree as UTF-8, creating missing directories. Throws `IOException`. |
| `static String toXmlString(SyntaxTree tree)` | The same text as a string. This is handy for tests. |

Errors the caller should expect:

* creating a child under a leaf throws `IllegalArgumentException`;
* creating a child under a node from a different tree throws `IllegalArgumentException`;
* writing a tree that has no root throws `IllegalStateException`.

The five characters that XML reserves (`&`, `<`, `>`, `"`, `'`) are escaped, so
SPL `STRING` tokens with their punctuation survive the round trip.

## Building

```
mvn test        # compile and run the JUnit 5 test suite
mvn package     # build target/spl-compiler-1.0-SNAPSHOT.jar
```

Java 17 or newer is required.

## Layout

```
src/main/java/za/ac/up/cos341/tree/   Node, NodeKind, SyntaxTree, XmlWriter
src/test/java/za/ac/up/cos341/tree/   JUnit 5 tests and the sample tree builder
tests/sample-tree.xml                 committed sample output
```

To regenerate the sample after a change to the output format:

```
mvn -q test-compile
java -cp target/test-classes:target/classes za.ac.up.cos341.tree.SampleTree
```