LL(1) parse table of the grammar from the syntax specification.
A cell holds the production that is chosen for the non-terminal of its row when the next token is the terminal of its column.
The two marked cells hold two productions each. These FIRST/FIRST conflicts show that the grammar is not LL(1) as given.
Columns without any entry are left out: { ; = then else

|   | **$** | **:** | **<u>USER-DEFINED-NAME</u>** | **void** | **(** | **)** | **return** | **}** | **num** | **<u>STRING</u>** | **print** | **nop** | **comment** | **<u>NUM</u>** | **mod** | **add** | **sub** | **mul** | **div** | **neg** | **if** | **not** | **and** | **or** | **eq** | **larger** | **lesser** | **do** | **while** | **until** |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| **SPL_PROG** |  | SPL_PROG → P $ | SPL_PROG → P $ |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |
| **P** |  | P → V_DECL : F_DECL : ALGO | P → V_DECL : F_DECL : ALGO |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |
| **V_DECL** |  | V_DECL → ε | V_DECL → <u>USER-DEFINED-NAME</u> V_DECL |  |  | V_DECL → ε |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |
| **F_DECL** |  | F_DECL → ε |  | F_DECL → F_TYPE F_DECL |  |  |  |  | F_DECL → F_TYPE F_DECL |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |
| **F_TYPE** |  |  |  | F_TYPE → void <u>USER-DEFINED-NAME</u> ( V_DECL ) { P return } |  |  |  |  | F_TYPE → num <u>USER-DEFINED-NAME</u> ( V_DECL ) { P return ( TERM ) } |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |
| **ALGO** | ALGO → ε |  | ALGO → INSTR ; ALGO |  |  |  | ALGO → ε | ALGO → ε |  |  | ALGO → INSTR ; ALGO | ALGO → INSTR ; ALGO | ALGO → INSTR ; ALGO |  |  |  |  |  |  |  | ALGO → INSTR ; ALGO |  |  |  |  |  |  | ALGO → INSTR ; ALGO | ALGO → INSTR ; ALGO | ALGO → INSTR ; ALGO |
| **OUTP** |  |  |  |  | OUTP → ( TERM ) |  |  |  |  | OUTP → <u>STRING</u> |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |
| **INSTR** |  |  | <mark>INSTR → ASSIGN<br>INSTR → CALL</mark> |  |  |  |  |  |  |  | INSTR → print OUTP | INSTR → nop | INSTR → comment <u>STRING</u> |  |  |  |  |  |  |  | INSTR → BRANCH |  |  |  |  |  |  | INSTR → LOOP | INSTR → LOOP | INSTR → LOOP |
| **CALL** |  |  | CALL → <u>USER-DEFINED-NAME</u> ( INPUT ) |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |
| **INPUT** |  |  | INPUT → TERM INPUT |  |  | INPUT → ε |  |  |  |  |  |  |  | INPUT → TERM INPUT | INPUT → TERM INPUT | INPUT → TERM INPUT | INPUT → TERM INPUT | INPUT → TERM INPUT | INPUT → TERM INPUT | INPUT → TERM INPUT |  |  |  |  |  |  |  |  |  |  |
| **ASSIGN** |  |  | ASSIGN → <u>USER-DEFINED-NAME</u> = TERM |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |
| **TERM** |  |  | <mark>TERM → <u>USER-DEFINED-NAME</u><br>TERM → CALL</mark> |  |  |  |  |  |  |  |  |  |  | TERM → <u>NUM</u> | TERM → mod ( TERM TERM ) | TERM → add ( TERM TERM ) | TERM → sub ( TERM TERM ) | TERM → mul ( TERM TERM ) | TERM → div ( TERM TERM ) | TERM → neg ( TERM ) |  |  |  |  |  |  |  |  |  |  |
| **BRANCH** |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  | BRANCH → if BOOL then { ALGO } else { ALGO } |  |  |  |  |  |  |  |  |  |
| **BOOL** |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  | BOOL → not ( BOOL ) | BOOL → and ( BOOL BOOL ) | BOOL → or ( BOOL BOOL ) | BOOL → eq ( TERM TERM ) | BOOL → larger ( TERM TERM ) | BOOL → lesser ( TERM TERM ) |  |  |  |
| **LOOP** |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  | LOOP → do { ALGO } COND BOOL | LOOP → COND BOOL do { ALGO } | LOOP → COND BOOL do { ALGO } |
| **COND** |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  | COND → while | COND → until |
