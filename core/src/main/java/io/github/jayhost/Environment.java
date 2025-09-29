// ========================================================================
// io/github/jayhost/Environment.java
//
// Holds the shared state for a Lisp interpreter session, including
// defined functions and global variables.
// ========================================================================
package io.github.jayhost;

import org.objectweb.asm.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents the execution environment for the Lisp interpreter.
 * <p>
 * This class holds the symbol tables for global variables and functions,
 * allowing state to be preserved across multiple calls to the JIT compiler
 * and shared between different components (e.g., a game and a REPL).
 */
public class Environment {

    /** Stores signatures of all globally defined functions. */
    final Map<String, FunctionSignature> functionTable = new HashMap<>();

    /** Stores types of all globally defined variables. */
    final Map<String, Type> globalVarTable = new HashMap<>();

    /**
     * Constructs a new, empty environment.
     */
    public Environment() {
        // The maps are initialized and ready for use.
    }
}
