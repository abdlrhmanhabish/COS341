# Semantic analysis

The semantic analysis runs after the parser has built the syntax tree. It has two parts that follow
the specification sheets 2a and 2b.

| Part | Class | Checks | Result |
| --- | --- | --- | --- |
| 2a | `NameAnalyser` | names and scopes | symbol table with internal names and scope levels |
| 2b | `TypeAnalyser` | types | type of every node and the number type of the program |

Both classes walk the syntax tree from the parser. They also get the token list because the error
messages need line and column. `LeafTokens` pairs each leaf with its token. The parser hangs the leaves
into the tree in the order in which it eats the tokens. The k-th leaf in pre-order therefore belongs
to the k-th token.

## Scope levels

The main program is level 0. An `F_TYPE` node and everything
below it is one level deeper than the `P` whose function list contains it.

```
#x                                   level 0   global variable
:
void #f ( #p ) {                     level 1   #f, its parameter #p and its body
  #q
  :
  void #g ( ) { : : nop ; return }   level 2   #g and its body
  :
  #g ( ) ;
  return
}
:
#f ( #x ) ;                          level 0
```

The parameters and the local variables of a function belong to the same scope. Inside one scope every
name in the variable lists must be different; a local variable cannot mask a parameter.

## Name analysis (2a)

### First pass: declarations

The analyser visits the declarations in pre-order and gives each one a new internal name.

| Declared entity | Internal name | Example |
| --- | --- | --- |
| variable or parameter | `v` + counter + `_` + name without `#` | `#x` becomes `v1_x` |
| function | `f` + counter + `_` + name without `#` | `#sum` becomes `f2_sum` |
| the name `#` alone | counter only | `#` becomes `v3` |

The counter makes every internal name unique. The rest of the name only helps a human reader of the
symbol table and of the generated code.

This pass also finds names that are declared twice:

- two equal names in the variable list of the main program or of a function body
- two parameters with the same name
- a local variable with the name of a parameter of the same function
- two functions with the same name in one function list

### Second pass: uses

Every NAME leaf in an algorithm or in the term that a num function returns is linked to a declaration.

A **variable** is searched in the scope of the use first. After that the analyser moves outwards through
the scopes around it until it reaches the main program. The first declaration it meets wins. A local
variable thereby hides a global one with the same name. Scopes beside the current one are never searched. In
the example of announcement A#21 the `#x` used in `#k` does not find the `#x` declared in `#g`, because
`#g` is not an ancestor of `#k`.

A **function** must be listed in the function list of the same `P` as the algorithm that calls it.
Functions further out are not visible. With this rule recursion cannot happen. A function can only call
functions declared inside itself, and that nesting is finite.

Variables and functions are kept apart. A variable is always declared in a `V_DECL` and a function in an
`F_DECL`. The grammar tells a call from a variable by the bracket behind the name. A program may therefore
declare a variable `#f` and a function `#f` at the same time. They get different internal names.

### Error messages

A name without a declaration is reported once per scope, at its first use. The hint explains the
situation found in the rest of the program:

| Situation | Hint |
| --- | --- |
| variable declared only in a function beside or below | names that function |
| a variable name that belongs to a function | explains that functions are called with brackets |
| call of the function itself or of a function around it | recursion is not allowed |
| function from a list further out | only the own function list counts, declare a copy |
| function declared deeper inside another function | only that function may call it |

## Type analysis (2b)

The types are `unknown`, `ok`, `numeric`, `boolean` and `procedure`. Every node starts as `unknown`.
The analyser applies the rule of each production from the bottom up and stores the type of every node in
the symbol table.

| Node | Type of a correct node |
| --- | --- |
| `SPL_PROG`, `P`, the lists, `F_TYPE`, `INSTR`, `ASSIGN`, `BRANCH`, `LOOP`, `COND`, `OUTP` | `ok` |
| `TERM` | `numeric` |
| `BOOL` | `boolean` |
| `CALL` | `numeric` for a num function and `procedure` for a void function |
| NAME of a variable or parameter | `numeric` |

### The crude rule for mod and div

The check for `mod` and `div` comes before everything else. If the tree contains both, it is rejected
immediately with the message the sheet prescribes: `A float-integer-conflict might perhaps be possible`.
In a tree with `mod` no number may contain a decimal point.

The same scan fixes the number type of the program. A program with `div` or with a decimal point works
with floating point numbers. All other programs work with integers. The code generator will use this
for the type of its variables.

### Decisions beyond the sheet

- The type of a function name comes from the keyword in front of it (`void` or `num`). 
- Every parameter needs exactly one argument. The sheet does not list this rule.
- An error is reported only where a rule breaks while the children have known types. A void call inside
  a long term gives one message at the call and none for the nodes above it.

## Symbol table

`SymbolTable` uses the UNIDs of the syntax tree as foreign keys:

| Lookup | Meaning |
| --- | --- |
| `symbolOf(unid)` | the symbol a NAME leaf stands for |
| `levelOf(unid)` | the scope level of any node |
| `typeOf(unid)` | the type of any node after part 2b |
| `getNumbers()` | integers or floating point numbers |

A `Symbol` holds the original name, the internal name, the kind, the level, the UNID of its declaration
and the function it belongs to. A function symbol also lists its parameters and the UNID of its `F_TYPE`
node. All leaves that stand for the same entity share one `Symbol` object.

For a correct program `Main` writes the table to `symbol-table.txt`. This is the table of a small
program:

```
#x
:
num #inc ( #n ) { : : return ( add ( #n 1 ) ) }
:
#x = #inc ( 5 ) ;
```

```
Symbol table

All numbers of the program are integers.

Internal name  SPL name  Kind          Type     Level  Scope   Declared at  Used at
-------------  --------  ------------  -------  -----  ------  -----------  ---------
v1_x           #x        variable      numeric  0      main    1:1 (3)      5:1 (39)
f1_inc         #inc      num function  numeric  1      main    3:5 (9)      5:6 (43)
v2_n           #n        parameter     numeric  1      f1_inc  3:12 (12)    3:38 (28)
```

Positions are written as line:column. The number in brackets is the UNID of the NAME leaf in tree.xml.
