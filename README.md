# SPL Compiler

Compiler Construction Project (COS341, 2026).

## Front end (project phase 1)

The front end reads an SPL program from a plain ASCII file and runs two phases on it.

1. The **lexer** (`za.ac.up.cos341.lexer`) splits the text into tokens. It follows the regular
   expressions of the syntax specification and gives a hint for every token it rejects.
2. The **parser** (`za.ac.up.cos341.parser`) is a recursive-descent LL(1) parser. It builds the
   syntax tree or stops at the first syntax error with a message and a hint.

For a correct program the syntax tree is written to `tree.xml` in the folder of the input file.
The tree module (`za.ac.up.cos341.tree`) holds the tree and writes the XML.

### LL(1) analysis in short

The grammar of the specification is not LL(1) as given. `INSTR -> ASSIGN` and `INSTR -> CALL`
both start with a name, and so do `TERM -> NAME` and `TERM -> CALL`. Left-factoring removes these
two FIRST/FIRST conflicts. The parser reads the name and decides on the next token: `=` means an
assignment and `(` means a call. The tree still uses the non-terminals of the original grammar. `tree.xml` shows `ASSIGN` and `CALL` and never `INSTR_TAIL` or `TERM_TAIL`. The end marker `$` of
rule 0 is not part of the input file. The tree therefore has no leaf for it.

The analysis is documented in `src/docs`:

| File | Contents |
| --- | --- |
| [first-follow-table.md](src/docs/first-follow-table.md) | FIRST and FOLLOW sets of both grammars |
| [ll1-table-conflicts.md](src/docs/ll1-table-conflicts.md) | parse table of the original grammar with its two conflicts |
| [grammar-ll1.md](src/docs/grammar-ll1.md) | the left-factored grammar that the parser follows |
| [ll1-table.md](src/docs/ll1-table.md) | parse table of the left-factored grammar without conflicts |

## Semantic analysis (project phase 2)

Two more phases run on the syntax tree of a syntactically correct program. Both are in
`za.ac.up.cos341.semantics` and crawl through the tree that the parser built.

1. The **name analysis** (`NameAnalyser`, phase 2a) checks the rules for names and scopes. Its first pass
   enters every declaration into the symbol table and gives it a new internal name such as `v3_x` or
   `f2_g`. The second pass links every use of a name to its declaration.
2. The **type analysis** (`TypeAnalyser`, phase 2b) applies the type rules. Every node starts as `unknown`
   and gets its type from the types of its children. Names take their types from the symbol table.

The `SymbolTable` uses the UNIDs of the tree as foreign keys. Every NAME leaf is linked to its symbol, and
every node to its scope level and its type. For a correct program the table is written to
`symbol-table.txt` next to `tree.xml`.

### The rules in short

- The main program is scope level 0 and every function adds one level. The parameters and the local
  variables of a function share its level.
- A variable is looked up in its own scope first and then in the scopes around it. The nearest declaration
  wins. A declaration in a function beside the current one is never found.
- An algorithm may only call the functions of its own function list. Recursion is impossible this way.
- Names in one variable list must differ. A local variable may not mask a parameter of its function. One
  function list may not hold two functions with the same name.
- Variables and functions are kept apart. A variable and a function may share a name.
- A void function is a procedure and may only be called as an instruction. A num function is numeric and
  may only be called inside a term.
- Every parameter needs exactly one argument. The specification does not list this rule, but the inlining
  in phase 3 depends on it.
- A program with both `mod` and `div` is rejected at once with the message
  `A float-integer-conflict might perhaps be possible`. With `mod` no number may contain a decimal point.
  A program with `div` or a decimal point works with floating point numbers and all others with integers.

Every broken rule is reported with its line and column and a hint. The design is explained in
[src/docs/semantic-analysis.md](src/docs/semantic-analysis.md).

```
SPL.txt is syntactically correct and its syntax tree was written to C:\COS341\tree.xml

Naming error in SPL.txt on line 12, column 11:
  The variable #x is not declared.

   12 |   print ( #x ) ;
      |           ^^

Hint: #x is declared in the function #g on line 3, column 3. The variables of
      a function are only known inside that function and inside the functions
      declared within it.

Found 1 naming error. No symbol table was written.
```

## Code generation (project phase 3a)

`za.ac.up.cos341.codegen.CodeGenerator` translates the algorithms of a type-checked program into C++ as
task sheet 3a describes. For a correct program `Main` writes the C++ program next to `tree.xml`. It is named after the input
file. `SPL.txt` gives `SPL.cpp`.

- Every term gets a fresh variable `t1`, `t2`, ... declared as `int` or `float`. The number type comes from
  phase 2b. Variables use their internal names from phase 2a (`v1_x`).
- The target language has no `else` and no `&&`, `||` or `!`. Conditions become cascading `if ( x op y ) goto L`
  jumps with labels `L1`, `L2`, ... The false code of a branch comes first, as the task sheet shows.
- `while` is translated as `until not`, and `do ... until` as `do ... while not`.
- The cascading jumps test `and` and `or` from left to right and stop as soon as the result is known.
  The second condition is skipped when the first one decides. This only shows when the second condition
  calls a function which prints something.
- Every `print` ends with a line break, and every `;` with a new line.
- A `?` inside a printed string is written as `\?`. Compilers before C++17 would otherwise read `??!`
  or `??-` as trigraphs and would print other characters.
- The task sheet's `not` and `and` rules taken literally send both outcomes of a condition to the same
  place. `jumpIfTrue` and `jumpIfFalse` keep the idea (the dummy label is the code right behind the jump)
  and add the missing `goto`.


## Function calls by inlining (project phase 3b)

`za.ac.up.cos341.codegen.Inliner` is the `CallTranslator` of phase 3b. `Main` plugs it in with `setCallTranslator`. A program
with function calls now gets its C++ file as well. SPL has no recursion and needs no run-time stack.

The C++ file has two parts:

1. **The code-mine.** It is only comments and never runs. Every function has its own `/* ... */` comment. It lists the
   parameters and the local variables of the function together with its body. A `num` function also names its return
   variable `r` (`f` + `_` + a fresh `tN`). A function inside a function is mined before the function around it. Calls inside
   a body are already inlined here (the first phase of the inlining). C comments do not nest. The `/* */` of the mined code
   are therefore written as `(* *)` inside the mine.
2. **The executable code.** For every call `NAME ( INPUT )` the generator writes `begin of inlining`, then for each argument
   a new `pN` with the code of the argument, then `a1 = p1 ; ... aN = pN ;` (all arguments are computed before the first parameter
   changes), then the body of the function, and for a `num` function `r = TERM ;` followed by `c = r`.

Decisions to know about:

- The body is **translated again from the syntax tree** at every call instead of copying text out of the mine. The result is the
  text that copy, paste and rename would give, but every copy gets new labels `Lk` and temporaries `tk` from the generator. Two
  inlinings of the same function never share a label, and a string like `"l1"` is never renamed by accident.
- The parameters and the local variables of **all** functions are declared once at the top of `main`, together with the variables of
  the main program. Their internal names from phase 2a are unique, and without recursion a function is never active twice.
  A local variable of a function is not reset at a call (the main program's variables are not either).
- A `void` call is a plain instruction. A `num` call may stand wherever a term may, also inside arguments (`f ( f ( 1 ) )`),
  conditions and loop conditions. The inlined code of a call in a loop condition runs again at every test.

`CodeGeneratorTest` compiles the programs of `tests/codegen` with `g++`. The `calls_*` programs among them test the
inlining. `tests/codegen/README.txt` says what each of them covers.


## Building and running

```
mvn test        # compile and run the JUnit 5 tests
mvn package     # build target/spl-compiler-1.0-SNAPSHOT.jar
java -jar target/spl-compiler-1.0-SNAPSHOT.jar            # reads SPL.txt from the current folder
java -jar target/spl-compiler-1.0-SNAPSHOT.jar prog.txt   # reads prog.txt
```

The classes are compiled for Java 8 (`maven.compiler.release` in the pom). The jar therefore runs
on every Java from version 8 on. Building needs a JDK from version 9 on because of the `--release`
option. JDK 17 and JDK 21 are tested.

| Exit code | Meaning |
| --- | --- |
| 0 | correct program, `tree.xml`, `symbol-table.txt` and the C++ file (`SPL.cpp` for `SPL.txt`) written |
| 1 | lexical or syntax error |
| 2 | the input file cannot be used |
| 3 | naming error (phase 2a) |
| 4 | type error (phase 2b) |

An error report looks like this:

```
Syntax error in SPL.txt on line 7, column 1:
  Found '}' but expected ';' to end the instruction.

    7 | } ;
      | ^

Hint: The last instruction before a '}' needs a ';' as well. The instruction
      that needs it starts on line 6.

No tree.xml was written.
```

### Trying the code generator by hand

Needs a JDK, Maven and g++. Without g++ the tests that run the generated programs are skipped.

```
mvn package
java -jar target/spl-compiler-1.0-SNAPSHOT.jar tests/codegen/calls_nested.txt
g++ -o tests/codegen/calls_nested tests/codegen/calls_nested.cpp
./tests/codegen/calls_nested
```

The compiler writes calls_nested.cpp, tree.xml and symbol-table.txt next to the input file.
The output must equal `tests/codegen/calls_nested.expected`. `mvn test` leaves no `.cpp` behind because
`CodeGeneratorTest` compiles into a temporary folder.

To check that no label is defined twice after inlining (no output means correct):

```
sed -n '/begin of executable C code/,$p' tests/codegen/calls_nested.cpp | grep -o '^L[0-9]* :' | sort | uniq -d
```

On Windows, the shell one-liners with sed and grep need Git Bash or WSL. Compiling the generated C++ needs g++. In cmd or
PowerShell it only works when MinGW or a similar toolchain is installed. Without g++ the tests that run the generated programs
are skipped.

## Testing

Besides unit tests for every part, `GeneratedProgramTest` follows the advice of announcement A#23.
It generates 1000 random programs from the grammar and checks that each one is accepted. Every
tree must be a derivation tree of the original grammar whose leaves are exactly the input tokens.
The same programs are then damaged by removing, adding or replacing one token. The parser must
answer those with a valid tree or a `SyntaxError` and never with any other exception.

`GeneratedSemanticsTest` does the same for phase 2. `ValidProgram` writes random programs that keep
every naming and type rule and records which declaration each name stands for. Both analyses must
accept all of them, and every name must be linked to the declaration the generator meant. After that
the programs are damaged in a single place. A name may be replaced by an unknown one or a call may get
an extra argument. Another test brings `mod` and `div` together. Each damaged program must give exactly
one error at the damaged token.

The folders `tests/semantics` and `tests/tutor-p1` hold example programs for every rule and the six
test cases of project part 1. `MainTest` runs all of them and checks their exit codes.

## Syntax tree and tree.xml writer

Every node carries a unique ID number. The file is split into three sections: the root, the inner
nodes and the leaves. Inner nodes and leaves are written in ascending ID order. A worked example
can be found at [tests/sample-tree.xml](tests/sample-tree.xml). It can be opened in a web browser
to inspect the structure.

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
tree.createLeaf("nop", instr);                         // a token the parser has eaten
tree.createLeaf(";", algo);
XmlWriter.write(tree, Paths.get("tree.xml"));         // throws IOException
```

Each `create*` call assigns the next free ID then sets the new node's parent and
appends the new ID to the parent's list of children. The caller never edits a
children list itself.

### Public API

`za.ac.up.cos341.tree.SyntaxTree`

| Method | Purpose |
| --- | --- |
| `Node createRoot(String contents)` | Creates the start-symbol node. Throws `IllegalStateException` if a root already exists. |
| `Node createInner(String contents, Node parent)` | Creates a non-terminal node under `parent`. |
| `Node createLeaf(String token, Node parent)` | Creates a terminal node under `parent`. |
| `Node getNode(int id)` | Looks a node up by ID. Returns `null` if there is none. |
| `Node getRoot()` | The root. Returns `null` while the tree is empty. |
| `Node child(Node node, int index)` | A child of a node. The first child has the index 0. |
| `List<Node> children(Node node)` | The children of a node from left to right. |
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

`za.ac.up.cos341.parser.Parser`

| Method | Purpose |
| --- | --- |
| `Parser(List<Token> tokens)` | Takes the tokens produced by `Lexer.tokenize`. |
| `SyntaxTree parse()` | Parses the tokens as `SPL_PROG`. Throws `SyntaxError` with line, column and hint. |

## Layout

```
src/main/java/za/ac/up/cos341/Main.java     entry point of the compiler
src/main/java/za/ac/up/cos341/lexer/        Lexer, Token, TokenType, LexicalException
src/main/java/za/ac/up/cos341/parser/       Parser, SyntaxError
src/main/java/za/ac/up/cos341/tree/         Node, NodeKind, SyntaxTree, XmlWriter
src/main/java/za/ac/up/cos341/semantics/    NameAnalyser, TypeAnalyser, SymbolTable, Symbol
src/main/java/za/ac/up/cos341/codegen/      CodeGenerator, CallTranslator
src/test/java/za/ac/up/cos341/              JUnit 5 tests and the random program generators
src/docs/                                   grammar analysis and the design of the semantic analysis
tests/lexer/                                lexer test inputs
tests/semantics/                            example programs for the naming and type rules
tests/tutor-p1/                             the six test cases of project part 1
tests/codegen/                              programs for the code generator with their expected output
tests/sample-tree.xml                       the sample tree.xml
manual/user-manual.pdf                      user manual for the tutors
scripts/package.sh                          builds group-N.jar and the upload zip
```

To regenerate the sample after a change to the output format:

```
mvn -q test-compile
java -cp target/test-classes:target/classes za.ac.up.cos341.tree.SampleTree
```