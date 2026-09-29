package za.ac.up.cos341.parser;

import static za.ac.up.cos341.lexer.TokenType.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import za.ac.up.cos341.lexer.Token;
import za.ac.up.cos341.lexer.TokenType;
import za.ac.up.cos341.tree.Node;
import za.ac.up.cos341.tree.SyntaxTree;

public final class Parser {

    // these are the things that may follow an ALGO
    private enum Ending { END_OF_FILE, RETURN, CLOSING_BRACE }

    private static final Set<TokenType> FIRST_INSTR = EnumSet.of(PRINT, NOP, COMMENT, NAME, IF, WHILE, UNTIL, DO);
    private static final Set<TokenType> FIRST_TERM = EnumSet.of(NAME, NUM, MOD, ADD, SUB, MUL, DIV, NEG);
    private static final Set<TokenType> FIRST_BOOL = EnumSet.of(NOT, AND, OR, EQ, LARGER, LESSER);

    private static final String PROGRAM_LAYOUT =
            "A program is written as  VARIABLES : FUNCTIONS : INSTRUCTIONS  and both ':' are required.\n"
            + "Each part may be empty. The shortest program is therefore  : :";

    private static final String BODY_LAYOUT =
            "A function body has the same three parts as a program followed by return.\n"
            + "It is written as  { VARIABLES : FUNCTIONS : INSTRUCTIONS return }  with both ':' present.";

    private static final String FUNCTION_FORMS =
            "A function is declared in one of these two forms\n"
            + "  void #name ( #params ) { BODY return }\n"
            + "  num #name ( #params ) { BODY return ( TERM ) }\n"
            + "BODY has the same three parts as a program:  VARIABLES : FUNCTIONS : INSTRUCTIONS";

    private static final String INSTRUCTION_FORMS =
            "An instruction has one of these forms and is always followed by ';'\n"
            + "  print \"text\"              print ( TERM )\n"
            + "  nop                       comment \"text\"\n"
            + "  #x = TERM                 #f ( ARGUMENTS )\n"
            + "  if BOOL then { ... } else { ... }\n"
            + "  while BOOL do { ... }     until BOOL do { ... }\n"
            + "  do { ... } while BOOL     do { ... } until BOOL";

    private static final String TERM_FORMS =
            "A term has one of these forms\n"
            + "  #x                    a variable\n"
            + "  7  or  -2.5           a number\n"
            + "  #f ( ARGUMENTS )      a function call\n"
            + "  add ( TERM TERM )     and the same for sub mul div mod\n"
            + "  neg ( TERM )";

    private static final String BOOL_FORMS =
            "A condition has one of these forms\n"
            + "  eq ( TERM TERM )    larger ( TERM TERM )    lesser ( TERM TERM )\n"
            + "  and ( BOOL BOOL )   or ( BOOL BOOL )        not ( BOOL )";

    private final List<Token> tokens;
    private SyntaxTree tree;
    private int position;

    public Parser(List<Token> tokens) {
        this.tokens = Collections.unmodifiableList(new ArrayList<>(tokens));
    }

    /**
     * parses the whole token list as SPL_PROG.
     *
     * @return the syntax tree with SPL_PROG at the root
     * @throws SyntaxError if the tokens do not form an SPL program
     */
    public SyntaxTree parse() {
        tree = new SyntaxTree();
        position = 0;
        Node root = tree.createRoot("SPL_PROG");
        if (tokens.isEmpty()) {
            throw new SyntaxError("The file contains no SPL tokens.", 1, 1, 0,
                    "Even an empty program needs its two separators. The shortest SPL program is  : :");
        }
        program(root, Ending.END_OF_FILE, null);
        return tree;
    }

    // P -> V_DECL : F_DECL : ALGO
    private void program(Node parent, Ending ending, Token function) {
        Node node = tree.createInner("P", parent);
        String layout = function == null ? PROGRAM_LAYOUT : BODY_LAYOUT;
        int variables = variables(node);
        expect(COLON, node, variables == 0 ? "a variable name or ':'" : "':' after the variable names", layout);
        int functions = functions(node);
        expect(COLON, node, functions == 0 ? "a function or ':'" : "':' after the functions", layout);
        algorithm(node, ending, function);
    }

    // V_DECL -> NAME V_DECL | ε
    private int variables(Node parent) {
        Node node = tree.createInner("V_DECL", parent);
        int count = 0;
        while (at(NAME)) {
            leaf(node);
            node = tree.createInner("V_DECL", node);
            count++;
        }
        return count;
    }

    // F_DECL -> F_TYPE F_DECL | ε
    private int functions(Node parent) {
        Node node = tree.createInner("F_DECL", parent);
        int count = 0;
        while (at(VOID) || at(NUM_KW)) {
            function(node);
            node = tree.createInner("F_DECL", node);
            count++;
        }
        return count;
    }

    // F_TYPE -> void NAME ( V_DECL ) { P return }
    // F_TYPE -> num NAME ( V_DECL ) { P return ( TERM ) }
    private void function(Node parent) {
        Node node = tree.createInner("F_TYPE", parent);
        Token kind = leaf(node);
        boolean returnsValue = kind.type() == NUM_KW;
        Token name = expect(NAME, node, "a function name after '" + kind.value() + "'", FUNCTION_FORMS);
        expect(LPAREN, node, "'(' after the function name " + name.value(),
                "The parameters follow the name in brackets as in  " + name.value()
                        + " ( #a #b ) . A function without parameters is written with  ( ) .");
        variables(node);
        expect(RPAREN, node, "a parameter name or ')'",
                "Parameters are names that start with '#' and are separated by spaces.");
        expect(LBRACE, node, "'{' to start the body of " + name.value(), FUNCTION_FORMS);
        program(node, Ending.RETURN, name);
        leaf(node);
        if (returnsValue) {
            expect(LPAREN, node, "'(' after 'return'",
                    "A num function has to return a value as in  return ( #result ) }");
            term(node, "the term that " + name.value() + " returns");
            expect(RPAREN, node, "')' after the returned term", "A num function returns exactly one term.");
        }
        expect(RBRACE, node, "'}' to close the function " + name.value(), returnsValue
                ? "A num function ends with  return ( TERM ) }"
                : "A void function returns no value and its body ends with  return } .\n"
                        + "Declare the function with num if it has to return something.");
    }

    // ALGO -> INSTR ; ALGO | ε
    private void algorithm(Node parent, Ending ending, Token opener) {
        Node node = tree.createInner("ALGO", parent);
        while (FIRST_INSTR.contains(peekType())) {
            Token first = peek();
            instruction(node);
            if (!at(SEMI)) {
                throw error("Found " + describe() + " but expected ';' to end the instruction.", semicolonHint(first));
            }
            leaf(node);
            node = tree.createInner("ALGO", node);
        }
        checkEnding(ending, opener);
    }

    private String semicolonHint(Token first) {
        String where = " The instruction that needs it starts on line " + first.line() + ".";
        if (at(RBRACE)) {
            return "The last instruction before a '}' needs a ';' as well." + where;
        }
        TokenType type = first.type();
        if (type == IF || type == WHILE || type == UNTIL || type == DO) {
            return "An if or a loop is an instruction too and needs a ';' after its last part." + where;
        }
        return "Every instruction must end with ';'." + where;
    }

    //The ALGO has ended because the next token cannot start an instruction. It Checks that the token is the one that has to follow in this place
    private void checkEnding(Ending ending, Token opener) {
        if (ending == Ending.END_OF_FILE) {
            if (atEnd()) {
                return;
            }
            if (at(RBRACE)) {
                throw error("Found '}' but there is no '{' for it to close.",
                        "Check that every '{' has exactly one matching '}'.");
            }
            if (at(RETURN)) {
                throw error("Found 'return' outside of a function.",
                        "The word return may only end the body of a function.");
            }
            throw error("Found " + describe() + " but expected an instruction or the end of the file.",
                    INSTRUCTION_FORMS);
        }
        if (ending == Ending.RETURN) {
            if (at(RETURN)) {
                return;
            }
            if (atEnd()) {
                throw error("The file ended inside the function " + opener.value() + " from line "
                        + opener.line() + ".", "A function body ends with  return }  or with  return ( TERM ) } .");
            }
            if (at(RBRACE)) {
                throw error("Found '}' but expected 'return' at the end of the function " + opener.value() + ".",
                        "Every function body must end with the word return before its closing '}'.");
            }
            throw error("Found " + describe() + " but expected an instruction or 'return'.", INSTRUCTION_FORMS);
        }
        if (at(RBRACE)) {
            return;
        }
        if (atEnd()) {
            throw error("The file ended before the '{' from line " + opener.line() + " was closed.",
                    "Add the missing '}'. Remember that the instruction around the block needs a ';' after it.");
        }
        if (at(RETURN)) {
            throw error("Found 'return' inside a block.",
                    "The word return may only end the body of a function. It cannot appear inside an if or a loop.");
        }
        throw error("Found " + describe() + " but expected an instruction or '}'.", INSTRUCTION_FORMS);
    }

    // INSTR -> print OUTP | nop | comment STRING | ASSIGN | BRANCH | LOOP | CALL
    private void instruction(Node parent) {
        Node node = tree.createInner("INSTR", parent);
        switch (peekType()) {
            case PRINT:
                leaf(node);
                output(node);
                break;
            case NOP:
                leaf(node);
                break;
            case COMMENT:
                leaf(node);
                expect(STRING, node, "a string after 'comment'",
                        "A comment is written as  comment \"text\"  with the text in double quotes.");
                break;
            case NAME:
                assignmentOrCall(node);
                break;
            case IF:
                branch(node);
                break;
            default:
                loop(node);
        }
    }

    // OUTP -> ( TERM ) | STRING
    private void output(Node parent) {
        Node node = tree.createInner("OUTP", parent);
        if (at(STRING)) {
            leaf(node);
        } else if (at(LPAREN)) {
            leaf(node);
            term(node, "the term to print");
            expect(RPAREN, node, "')' after the printed term", "print ( TERM ) prints exactly one term.");
        } else {
            throw error("Found " + describe() + " but expected a string or '(' after 'print'.",
                    "Use  print \"text\"  for a string and  print ( TERM )  for a value as in  print ( #x ) .");
        }
    }

    // ASSIGN -> NAME = TERM and CALL -> NAME ( INPUT ) both start with a name and the name is read first and the token behind it selects the production
    private void assignmentOrCall(Node instruction) {
        Token name = advance();
        if (at(ASSIGN)) {
            Node node = tree.createInner("ASSIGN", instruction);
            tree.createLeaf(name.value(), node);
            leaf(node);
            term(node, "a term after '='");
        } else if (at(LPAREN)) {
            call(instruction, name);
        } else {
            throw error("Found " + describe() + " but expected '=' or '(' after the name " + name.value() + ".",
                    "An instruction that starts with a name is either an assignment  " + name.value()
                            + " = TERM\nor a function call  " + name.value() + " ( ARGUMENTS ) .");
        }
    }

    // CALL -> NAME ( INPUT ) where the name has already been read
    private void call(Node parent, Token name) {
        Node node = tree.createInner("CALL", parent);
        tree.createLeaf(name.value(), node);
        leaf(node);
        arguments(node);
        expect(RPAREN, node, "a term or ')' in the call of " + name.value(),
                "The arguments of a call are terms separated by spaces as in  " + name.value() + " ( #a 3 add ( #b 1 ) ) .");
    }

    // INPUT -> TERM INPUT | ε
    private void arguments(Node parent) {
        Node node = tree.createInner("INPUT", parent);
        while (FIRST_TERM.contains(peekType())) {
            term(node, "an argument");
            node = tree.createInner("INPUT", node);
        }
    }

    // TERM -> NAME | NUM | CALL | mod ( TERM TERM ) | add ( TERM TERM ) | sub ( TERM TERM )  | mul ( TERM TERM ) | div ( TERM TERM ) | neg ( TERM )
    // the role describes the expected term in the error messages
    private void term(Node parent, String role) {
        if (!FIRST_TERM.contains(peekType())) {
            throw error("Found " + describe() + " but expected " + role + ".", termHint());
        }
        Node node = tree.createInner("TERM", parent);
        Token first = advance();
        switch (first.type()) {
            case NAME:
                if (at(LPAREN)) {
                    call(node, first);
                } else {
                    tree.createLeaf(first.value(), node);
                }
                break;
            case NUM:
                tree.createLeaf(first.value(), node);
                break;
            case NEG:
                tree.createLeaf(first.value(), node);
                open(node, first);
                term(node, "the term inside 'neg'");
                close(node, first, "neg takes exactly one term as in  neg ( #x ) .");
                break;
            default:
                String operator = first.value();
                tree.createLeaf(operator, node);
                open(node, first);
                term(node, "the first term of '" + operator + "'");
                term(node, "the second term of '" + operator + "'");
                close(node, first, operator + " takes exactly two terms. Nest it for more as in  "
                        + operator + " ( #a " + operator + " ( #b #c ) ) .");
        }
    }

    private String termHint() {
        TokenType type = peekType();
        if (FIRST_BOOL.contains(type)) {
            return "'" + peek().value() + "' builds a condition. Conditions belong after if or while or until.\n"
                    + "They cannot be used where a term is expected.";
        }
        if (type == LPAREN) {
            return "Brackets are only written after an operator or a function name.\n"
                    + "A term cannot be put in brackets on its own.";
        }
        return TERM_FORMS;
    }

    // BRANCH -> if BOOL then { ALGO } else { ALGO }
    private void branch(Node parent) {
        Node node = tree.createInner("BRANCH", parent);
        leaf(node);
        condition(node, "a condition after 'if'");
        expect(THEN, node, "'then' after the condition",
                "An if-instruction is written as  if BOOL then { ... } else { ... }");
        block(node, "'{' after 'then'");
        expect(ELSE, node, "'else' after the then-block",
                "Every if needs an else part. Write  else { }  when nothing should happen in that case.");
        block(node, "'{' after 'else'");
    }

    // the part { ALGO } of a BRANCH or a LOOP
    private void block(Node parent, String expected) {
        Token open = expect(LBRACE, parent, expected, "A block of instructions is written between '{' and '}'.");
        algorithm(parent, Ending.CLOSING_BRACE, open);
        leaf(parent);
    }

    // LOOP -> COND BOOL do { ALGO } | do { ALGO } COND BOOL
    private void loop(Node parent) {
        Node node = tree.createInner("LOOP", parent);
        if (at(DO)) {
            leaf(node);
            block(node, "'{' after 'do'");
            Token cond = loopCondition(node);
            condition(node, "a condition after '" + cond.value() + "'");
        } else {
            Token cond = loopCondition(node);
            condition(node, "a condition after '" + cond.value() + "'");
            expect(DO, node, "'do' after the loop condition",
                    "A loop is written as  while BOOL do { ... }  or as  do { ... } until BOOL");
            block(node, "'{' after 'do'");
        }
    }

    // COND -> while | until
    private Token loopCondition(Node parent) {
        if (!at(WHILE) && !at(UNTIL)) {
            throw error("Found " + describe() + " but expected 'while' or 'until' after the do-block.",
                    "A do-loop ends with its condition as in  do { ... } while BOOL  or  do { ... } until BOOL");
        }
        Node node = tree.createInner("COND", parent);
        return leaf(node);
    }

    // BOOL -> not ( BOOL ) | and ( BOOL BOOL ) | or ( BOOL BOOL ) | eq ( TERM TERM ) | larger ( TERM TERM ) | lesser ( TERM TERM )
    private void condition(Node parent, String role) {
        if (!FIRST_BOOL.contains(peekType())) {
            throw error("Found " + describe() + " but expected " + role + ".", conditionHint());
        }
        Node node = tree.createInner("BOOL", parent);
        Token operator = leaf(node);
        String name = "'" + operator.value() + "'";
        open(node, operator);
        switch (operator.type()) {
            case NOT:
                condition(node, "the condition inside " + name);
                close(node, operator, "not takes exactly one condition as in  not ( eq ( #a #b ) ) .");
                break;
            case AND:
            case OR:
                condition(node, "the first condition of " + name);
                condition(node, "the second condition of " + name);
                close(node, operator, operator.value() + " takes exactly two conditions. Nest it for more as in  "
                        + operator.value() + " ( BOOL " + operator.value() + " ( BOOL BOOL ) ) .");
                break;
            default:
                term(node, "the first term of " + name);
                term(node, "the second term of " + name);
                close(node, operator, operator.value() + " compares exactly two terms.");
        }
    }

    private String conditionHint() {
        TokenType type = peekType();
        if (type == NAME || type == NUM) {
            return "A value on its own is not a condition. Compare it with eq or larger or lesser as in  eq ( #x 1 ) .";
        }
        if (type == ADD || type == SUB || type == MUL || type == DIV || type == MOD || type == NEG) {
            return "'" + peek().value() + "' computes a number. Compare numbers with eq or larger or lesser to get a condition.";
        }
        return BOOL_FORMS;
    }

    private void open(Node node, Token operator) {
        expect(LPAREN, node, "'(' after '" + operator.value() + "'",
                "Operations and conditions are written in prefix form with brackets as in  "
                        + operator.value() + " ( ... ) .");
    }

    private void close(Node node, Token operator, String hint) {
        expect(RPAREN, node, "')' to close '" + operator.value() + "'", hint);
    }

    private boolean atEnd() {
        return position >= tokens.size();
    }

    private Token peek() {
        return tokens.get(position);
    }

    //the type of the next token or null at the end of the file
    private TokenType peekType() {
        return atEnd() ? null : peek().type();
    }

    private boolean at(TokenType type) {
        return peekType() == type;
    }

    private Token advance() {
        return tokens.get(position++);
    }

    // it eats the next token and hangs it under parent as a leaf
    private Token leaf(Node parent) {
        Token token = advance();
        tree.createLeaf(token.value(), parent);
        return token;
    }

    private Token expect(TokenType type, Node parent, String expected, String hint) {
        if (!at(type)) {
            throw error("Found " + describe() + " but expected " + expected + ".", hint);
        }
        return leaf(parent);
    }

    private String describe() {
        return atEnd() ? "the end of the file" : "'" + peek().value() + "'";
    }

    private SyntaxError error(String message, String hint) {
        if (atEnd()) {
            Token last = tokens.get(tokens.size() - 1);
            return new SyntaxError(message, last.line(), last.column() + last.value().length(), 0, hint);
        }
        Token token = peek();
        return new SyntaxError(message, token.line(), token.column(), token.value().length(), hint);
    }
}