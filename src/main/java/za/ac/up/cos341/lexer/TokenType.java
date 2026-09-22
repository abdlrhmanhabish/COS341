package za.ac.up.cos341.lexer;

public enum TokenType {
    // lexical categories (regex-driven)
    NUM, NAME, STRING,

    // keywords
    VOID, NUM_KW, RETURN,
    PRINT, NOP, COMMENT,
    IF, THEN, ELSE, WHILE, UNTIL, DO,
    NOT, AND, OR, EQ, LARGER, LESSER,
    ADD, SUB, MUL, DIV, MOD, NEG,

    // symbols
    COLON, SEMI, LPAREN, RPAREN, LBRACE, RBRACE, ASSIGN
}
