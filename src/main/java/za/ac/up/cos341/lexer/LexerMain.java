package za.ac.up.cos341.lexer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public final class LexerMain {

    public static void main(String[] args) {
        Path input = Path.of(args.length > 0 ? args[0] : "SPL.txt");
        try {
            List<Token> tokens = new Lexer().tokenize(input);
            tokens.forEach(System.out::println);
        } catch (LexicalException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        } catch (IOException e) {
            System.err.println("Could not read " + input + ": " + e.getMessage());
            System.exit(2);
        }
    }
}
