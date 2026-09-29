FIRST and FOLLOW sets of the grammar from the syntax specification.
ε in a FIRST set marks a nullable non-terminal. $ is the end of the input from rule 0.

| # | Non-terminal | FIRST | FOLLOW |
|---|---|---|---|
| 0 | SPL_PROG | :, <u>USER-DEFINED-NAME</u> | (none) |
| 1 | P | :, <u>USER-DEFINED-NAME</u> | $, return |
| 2 | V_DECL | ε, <u>USER-DEFINED-NAME</u> | :, ) |
| 3 | F_DECL | ε, void, num | : |
| 4 | F_TYPE | void, num | :, void, num |
| 5 | ALGO | ε, <u>USER-DEFINED-NAME</u>, print, nop, comment, if, do, while, until | $, return, } |
| 6 | OUTP | (, <u>STRING</u> | ; |
| 7 | INSTR | <u>USER-DEFINED-NAME</u>, print, nop, comment, if, do, while, until | ; |
| 8 | CALL | <u>USER-DEFINED-NAME</u> | <u>USER-DEFINED-NAME</u>, ), ;, <u>NUM</u>, mod, add, sub, mul, div, neg |
| 9 | INPUT | ε, <u>USER-DEFINED-NAME</u>, <u>NUM</u>, mod, add, sub, mul, div, neg | ) |
| 10 | ASSIGN | <u>USER-DEFINED-NAME</u> | ; |
| 11 | TERM | <u>USER-DEFINED-NAME</u>, <u>NUM</u>, mod, add, sub, mul, div, neg | <u>USER-DEFINED-NAME</u>, ), ;, <u>NUM</u>, mod, add, sub, mul, div, neg |
| 12 | BRANCH | if | ; |
| 13 | BOOL | not, and, or, eq, larger, lesser | ), ;, then, not, and, or, eq, larger, lesser, do |
| 14 | LOOP | do, while, until | ; |
| 15 | COND | while, until | not, and, or, eq, larger, lesser |

The left-factored grammar of grammar-ll1.md keeps all of these sets.
CALL and ASSIGN no longer occur in it and two non-terminals are new.

| # | Non-terminal | FIRST | FOLLOW |
|---|---|---|---|
| 16 | INSTR_TAIL | (, = | ; |
| 17 | TERM_TAIL | ε, ( | <u>USER-DEFINED-NAME</u>, ), ;, <u>NUM</u>, mod, add, sub, mul, div, neg |