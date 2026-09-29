SPL_PROG → P$ <!-- comment: This is the special "Rule 0" for the End-of-File ($) -->   
P → V_DECL <mark><strong>:</strong></mark> F_DECL <mark><strong>:</strong></mark> ALGO  
V_DECL → ε <!-- comment: nullable -->  
V_DECL → <u><strong>USER-DEFINED-NAME</strong></u> V_DECL  
F_DECL → ε <!-- comment: nullable -->  
F_DECL → F_TYPE F_DECL  
F_TYPE → <mark><strong>void</strong></mark> <u><strong>USER-DEFINED-NAME</strong></u> <mark><strong>(</strong></mark> V_DECL <mark><strong>)</strong></mark> <mark><strong>{</strong></mark> P <mark><strong>return</strong></mark> <mark><strong>}</strong></mark>  
F_TYPE → <mark><strong>num</strong></mark> <u><strong>USER-DEFINED-NAME</strong></u> <mark><strong>(</strong></mark> V_DECL <mark><strong>)</strong></mark> <mark><strong>{</strong></mark> P <mark><strong>return</strong></mark> <mark><strong>(</strong></mark> TERM <mark><strong>)</strong></mark> <mark><strong>}</strong></mark>  
ALGO → ε <!-- comment: nullable -->  
ALGO → INSTR <mark><strong>;</strong></mark> ALGO  
OUTP → <mark><strong>(</strong></mark> TERM <mark><strong>)</strong></mark>  
OUTP → <u><strong>STRING</strong></u>  
INSTR → <mark><strong>print</strong></mark> OUTP  
INSTR → <mark><strong>nop</strong></mark> <!-- comment: no-operation : could be used for empty else-cases (after if-then-) -->  
INSTR → <mark><strong>comment</strong></mark> <u><strong>STRING</strong></u>  
INSTR → <u><strong>USER-DEFINED-NAME</strong></u> INSTR_TAIL  
INSTR → BRANCH  
INSTR → LOOP  
INSTR_TAIL → <mark><strong>=</strong></mark> TERM  
INSTR_TAIL → <mark><strong>(</strong></mark> INPUT <mark><strong>)</strong></mark>  
INPUT → ε <!-- comment: nullable -->  
INPUT → TERM INPUT  
TERM → <u><strong>USER-DEFINED-NAME</strong></u> TERM_TAIL  
TERM → <u><strong>NUM</strong></u>  
TERM → <mark><strong>mod</strong></mark> <mark><strong>(</strong></mark> TERM TERM <mark><strong>)</strong></mark>  
TERM → <mark><strong>add</strong></mark> <mark><strong>(</strong></mark> TERM TERM <mark><strong>)</strong></mark>  
TERM → <mark><strong>sub</strong></mark> <mark><strong>(</strong></mark> TERM TERM <mark><strong>)</strong></mark>  
TERM → <mark><strong>mul</strong></mark> <mark><strong>(</strong></mark> TERM TERM <mark><strong>)</strong></mark>  
TERM → <mark><strong>div</strong></mark> <mark><strong>(</strong></mark> TERM TERM <mark><strong>)</strong></mark>  
TERM → <mark><strong>neg</strong></mark> <mark><strong>(</strong></mark> TERM <mark><strong>)</strong></mark>  
TERM_TAIL → <mark><strong>(</strong></mark> INPUT <mark><strong>)</strong></mark>  
TERM_TAIL → ε <!-- comment: nullable -->  
BRANCH → <mark><strong>if</strong></mark> BOOL <mark><strong>then</strong></mark> <mark><strong>{</strong></mark> ALGO <mark><strong>}</strong></mark> <mark><strong>else</strong></mark> <mark><strong>{</strong></mark> ALGO <mark><strong>}</strong></mark>  
BOOL → <mark><strong>not</strong></mark> <mark><strong>(</strong></mark> BOOL <mark><strong>)</strong></mark>  
BOOL → <mark><strong>and</strong></mark> <mark><strong>(</strong></mark> BOOL BOOL <mark><strong>)</strong></mark>  
BOOL → <mark><strong>or</strong></mark> <mark><strong>(</strong></mark> BOOL BOOL <mark><strong>)</strong></mark>  
BOOL → <mark><strong>eq</strong></mark> <mark><strong>(</strong></mark> TERM TERM <mark><strong>)</strong></mark>  
BOOL → <mark><strong>larger</strong></mark> <mark><strong>(</strong></mark> TERM TERM <mark><strong>)</strong></mark>  
BOOL → <mark><strong>lesser</strong></mark> <mark><strong>(</strong></mark> TERM TERM <mark><strong>)</strong></mark>  
LOOP → COND BOOL <mark><strong>do</strong></mark> <mark><strong>{</strong></mark> ALGO <mark><strong>}</strong></mark>  
LOOP → <mark><strong>do</strong></mark> <mark><strong>{</strong></mark> ALGO <mark><strong>}</strong></mark> COND BOOL  
COND → <mark><strong>while</strong></mark>  
COND → <mark><strong>until</strong></mark>  

**How this grammar differs from the specification**

* `INSTR → ASSIGN` and `INSTR → CALL` both begin with <u>USER-DEFINED-NAME</u>. Left-factoring turns them into `INSTR → USER-DEFINED-NAME INSTR_TAIL`.
* `TERM → USER-DEFINED-NAME` and `TERM → CALL` both begin with <u>USER-DEFINED-NAME</u> as well. They become `TERM → USER-DEFINED-NAME TERM_TAIL`.
* No production uses `CALL` or `ASSIGN` any more. Both are therefore gone from this grammar.
* All other productions are the same as in the specification.

The FIRST and FOLLOW sets are in first-follow-table.md. The parse table in ll1-table.md has at most one production per cell, which shows that this grammar is LL(1). The conflicts of the original grammar are in ll1-table-conflicts.md.

**Syntax tree**

The parser takes its decisions with this grammar but builds tree.xml with the non-terminals of the specification. When `INSTR_TAIL` starts with `=` the node becomes `ASSIGN` and when it starts with `(` the node becomes `CALL`. In the same way `TERM → USER-DEFINED-NAME TERM_TAIL` becomes `TERM → CALL` for `TERM_TAIL → ( INPUT )` and `TERM → USER-DEFINED-NAME` for the empty `TERM_TAIL`.
