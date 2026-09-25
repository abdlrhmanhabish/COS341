| **#** | ****   | **FIRST**                                                       | **FOLLOW**                                              |
|-------|--------|-----------------------------------------------------------------|---------------------------------------------------------|
| 1     | P      | ε,  <u>USER-DEFINED-NAME</u>                                           | $, return                                               |
| 2     | V_DECL | ε,  <u>USER-DEFINED-NAME</u>                                           | :, `)`                                                    |
| 3     | F_DECL | ε, void, num                                                    | :                                                       |
| 4     | F_TYPE | void, num                                                       | :, ε, void, num                                         |
| 5     | ALGO   | ε, print, nop, comment, <u>USER-DEFINED-NAME</u>, if, do, while, until | `}`                                                       |
| 6     | OUTP   | (, <u>STRING</u>                                                       | ;                                                       |
| 7     | INSTR  | print, nop, comment, <u>USER-DEFINED-NAME</u>, if, do, while, until    | ;                                                       |
| 8     | CALL   | <u>USER-DEFINED-NAME</u>                                               | ), <u>USER-DEFINED-NAME</u>, mod, <u>NUM</u>, add, sub, mul, div, neg |
| 9     | INPUT  | ε, <u>USER-DEFINED-NAME</u>, <u>NUM</u>, mod, add, sub, mul, div, neg         | `)`                                                       |
| 10    | ASSIGN | <u>USER-DEFINED-NAME</u>                                               | ;                                                       |
| 11    | TERM   | <u>USER-DEFINED-NAME</u>, <u>NUM</u>, mod, add, sub, mul, div, neg            | `)`, <u>NUM</u>, <u>USER-DEFINED-NAME</u>, mod, add, sub, mul, div, neg |
| 12    | BRANCH | if                                                              | ;                                                       |
| 13    | BOOL   | not, and, or, eq, larger, lesser                                | `)`, then, not, and, or, eq, larger, lesser, do, ;        |
| 14    | LOOP   | while, until, do                                                | ;                                                       |
| 15    | COND   | while, until                                                    | not, and, or, eq, larger, lesser                        |