SPL_PROG → P$ <!-- comment: This is the special "Rule 0" for the End-of-File ($) -->   
P → V_DECL <mark><strong>:</mark></strong> F_DECL <mark><strong>:</mark></strong> ALGO  
V_DECL → ε <!-- comment: nullable -->  
V_DECL → <u><strong>USER-DEFINED-NAME</u></strong> V_DECL  
F_DECL → ε <!-- comment: nullable -->  
F_DECL → F_TYPE F_DECL  
F_TYPE → <mark><strong>void</mark></strong> <u><strong>USER-DEFINED-NAME</u></strong> <mark><strong>(</mark></strong> V_DECL <mark><strong>)</mark></strong> <mark><strong>{</mark></strong> P <mark><strong>return</mark></strong> <mark><strong>}</mark></strong>  
F_TYPE → <mark><strong>num</mark></strong> <u><strong>USER-DEFINED-NAME</u></strong> <mark><strong>(</mark></strong> V_DECL <mark><strong>)</mark></strong> <mark><strong>{</mark></strong> P <mark><strong>return</mark></strong> <mark><strong>(</mark></strong> TERM <mark><strong>)</mark></strong> <mark><strong>}</mark></strong>  
ALGO → ε <!-- comment: nullable -->  
ALGO → INSTR <mark><strong>;</mark></strong> ALGO  
OUTP → <mark><strong>(</mark></strong> TERM <mark><strong>)</mark></strong>  
OUTP → <u><strong>STRING</u></strong>  
INSTR → <mark><strong>print</mark></strong> OUTP  
INSTR → <mark><strong>nop</mark></strong> <!-- comment: no-operation : could be used for empty else-cases (after if-then-) -->  
INSTR → <mark><strong>comment</mark></strong> <u><strong>STRING</u></strong>  
INSTR → <u><strong>USER-DEFINED-NAME</u></strong> INSTR_TAIL  
INSTR → BRANCH  
INSTR → LOOP  
INSTR_TAIL → <mark><strong>=</mark></strong> TERM  
INSTR_TAIL → <mark><strong>(</mark></strong> INPUT <mark><strong>)</mark></strong>  
CALL → <u><strong>USER-DEFINED-NAME</u></strong> <mark><strong>(</mark></strong> INPUT <mark><strong>)</mark></strong>  
INPUT → ε <!-- comment: nullable -->  
INPUT → TERM INPUT  
ASSIGN → <u><strong>USER-DEFINED-NAME</u></strong> <mark><strong>=</mark></strong> TERM  
TERM → <u><strong>USER-DEFINED-NAME</u></strong> TERM_TAIL  
TERM → <u><strong>NUM</u></strong>  
TERM_TAIL → <mark><strong>(</mark></strong> INPUT <mark><strong>)</mark></strong>  
TERM_TAIL → ε  
TERM → <mark><strong>mod</mark></strong> <mark><strong>(</mark></strong> TERM TERM <mark><strong>)</mark></strong>  
TERM → <mark><strong>add</mark></strong> <mark><strong>(</mark></strong> TERM TERM <mark><strong>)</mark></strong>  
TERM → <mark><strong>sub</mark></strong> <mark><strong>(</mark></strong> TERM TERM <mark><strong>)</mark></strong>  
TERM → <mark><strong>mul</mark></strong> <mark><strong>(</mark></strong> TERM TERM <mark><strong>)</mark></strong>  
TERM → <mark><strong>div</mark></strong> <mark><strong>(</mark></strong> TERM TERM <mark><strong>)</mark></strong>  
TERM → <mark><strong>neg</mark></strong> <mark><strong>(</mark></strong> TERM <mark><strong>)</mark></strong>  
BRANCH → <mark><strong>if</mark></strong> BOOL <mark><strong>then</mark></strong> <mark><strong>{</mark></strong> ALGO <mark><strong>}</mark></strong> <mark><strong>else</mark></strong> <mark><strong>{</mark></strong> ALGO <mark><strong>}</mark></strong>  
BOOL → <mark><strong>not</mark></strong> <mark><strong>(</mark></strong> BOOL <mark><strong>)</mark></strong>  
BOOL → <mark><strong>and</mark></strong> <mark><strong>(</mark></strong> BOOL BOOL <mark><strong>)</mark></strong>  
BOOL → <mark><strong>or</mark></strong> <mark><strong>(</mark></strong> BOOL BOOL <mark><strong>)</mark></strong>  
BOOL → <mark><strong>eq</mark></strong> <mark><strong>(</mark></strong> TERM TERM <mark><strong>)</mark></strong>  
BOOL → <mark><strong>larger</mark></strong> <mark><strong>(</mark></strong> TERM TERM <mark><strong>)</mark></strong>  
BOOL → <mark><strong>lesser</mark></strong> <mark><strong>(</mark></strong> TERM TERM <mark><strong>)</mark></strong>  
LOOP → COND BOOL <mark><strong>do</mark></strong> <mark><strong>{</mark></strong> ALGO <mark><strong>}</mark></strong>  
LOOP → <mark><strong>do</mark></strong> <mark><strong>{</mark></strong> ALGO <mark><strong>}</mark></strong> COND BOOL  
COND → <mark><strong>while</mark></strong>  
COND → <mark><strong>until</mark></strong>  
