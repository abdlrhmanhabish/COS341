package za.ac.up.cos341.codegen;

import za.ac.up.cos341.tree.Node;

// Phase 3b translates function calls by inlining. Until then every call is reported as not supported yet.
public interface CallTranslator {

    // INSTR -> CALL   a call of a void function
    String instruction(CodeGenerator generator, Node call);

    // TERM -> CALL    a call of a num function whose value goes into result
    String term(CodeGenerator generator, Node call, String result);

    CallTranslator NOT_YET = new CallTranslator() {
        @Override
        public String instruction(CodeGenerator generator, Node call) {
            throw new UnsupportedOperationException(message(generator.getTree().child(call, 0)));
        }

        @Override
        public String term(CodeGenerator generator, Node call, String result) {
            throw new UnsupportedOperationException(message(generator.getTree().child(call, 0)));
        }

        private String message(Node name) {
            return "The call of " + name.getContents() + " needs the inlining of phase 3b.";
        }
    };
}
