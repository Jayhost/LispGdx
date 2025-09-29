// ========================================================================
// io/github/jayhost/ReplSession.java
//
// Tiny read-eval-print loop that uses a stateful LispJitCompiler.
// Can be embedded in an application with a shared environment.
// Type :quit (or Ctrl-D) to exit the standalone REPL.
// ========================================================================
package io.github.jayhost;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * One interactive session. Keeps a global environment alive by using a
 * stateful compiler that operates on a shared Environment object.
 */
public final class ReplSession {

    /** The JIT compiler that holds the session's state and environment. */
    private final LispJitCompiler jit;

    /** Accumulates all top-level definition forms to provide context to the compiler. */
    private final List<Expr> topLevelForms;

    /**
     * Constructs a new REPL session with its own isolated environment.
     */
    public ReplSession() {
        this(new Environment());
    }

    /**
     * Constructs a new REPL session that uses a shared environment.
     * This is crucial for embedding the interpreter in another application.
     *
     * @param sharedEnv The environment to share.
     */
    public ReplSession(Environment sharedEnv) {
        this(sharedEnv, new ArrayList<>());
    }

    /**
     * Constructs a new REPL session with a shared environment and pre-existing definitions.
     * @param sharedEnv The environment to share.
     * @param initialForms The list of definition forms from a previous session.
     */
    public ReplSession(Environment sharedEnv, List<Expr> initialForms) {
        this.jit = new LispJitCompiler(sharedEnv);
        this.topLevelForms = new ArrayList<>(initialForms);
    }

    /**
     * Returns the environment being used by this REPL session.
     *
     * @return The underlying Lisp environment.
     */
    public Environment getEnvironment() {
        return jit.getEnvironment();
    }

    /**
     * Returns the list of top-level definition forms accumulated in this session.
     * @return The list of definition expressions.
     */
    public List<Expr> getTopLevelForms() {
        return this.topLevelForms;
    }

    /**
     * Evaluates one or more Lisp expressions from a source string and returns the
     * value of the last expression. Definitions are added to the shared environment.
     *
     * @param source A string containing Lisp code.
     * @return The result of the evaluation.
     * @throws Exception if parsing or evaluation fails.
     */
    public Object eval(String source) throws Exception {
        Parser p = new Parser(source);
        List<Expr> currentForms = p.parseMultiple();
        if (currentForms.isEmpty()) {
            return null;
        }

        List<Expr> formsToCompile = new ArrayList<>(this.topLevelForms);
        formsToCompile.addAll(currentForms);
    
        LispCallable script = jit.compile(formsToCompile);
        Object result = script.apply(new Object[0]);

        for (Expr form : currentForms) {
            if (Helpers.isSpecialForm(form, "def") || Helpers.isSpecialForm(form, "defvar")) {
                this.topLevelForms.add(form);
            }
        }
    
        return result;
    }

    /**
     * Reads from the input stream until a complete Lisp form (balanced parentheses)
     * has been entered.
     */
    private static String readCompleteForm(BufferedReader in) throws Exception {
        StringBuilder src = new StringBuilder();
        int parens = 0;
    
        while (true) {
            String line = in.readLine();
            if (line == null) return null;
    
            src.append(line).append('\n');
    
            for (char c : line.toCharArray()) {
                if (c == '(') parens++;
                else if (c == ')') parens--;
            }
            if (src.length() > 0 && parens <= 0) break;
            
            System.out.print(".... ");
        }
        return src.toString();
    }

    /* ------------------------------------------------------------------ */
    /* Command-line REPL for both standalone and embedded use             */
    /* ------------------------------------------------------------------ */
    /**
     * Standard main entry point for running the REPL as a standalone application.
     */
    public static void main(String[] args) throws Exception {
        main(new Environment());
    }
    
    /**
     * Starts a REPL main-loop using a provided environment but no prior definitions.
     */
    public static void main(Environment sharedEnv) throws Exception {
        main(sharedEnv, new ArrayList<>());
    }

    /**
     * A main-loop that runs the REPL using a provided, shared environment and
     * a list of initial definitions. This is the correct method to call when
     * starting a REPL from an already-running interpreter.
     */
    public static void main(Environment sharedEnv, List<Expr> initialForms) throws Exception {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        // Create a REPL session that knows about the previous definitions.
        ReplSession repl = new ReplSession(sharedEnv, initialForms);

        System.out.println("LispJ REPL. Type :quit or press Ctrl-D to exit.");

        for (;;) {
            System.out.print("lisp> ");
            String formSrc = readCompleteForm(in);
            
            if (formSrc == null || formSrc.trim().equals(":quit")) {
                break;
            }
            if (formSrc.trim().isEmpty()) {
                continue;
            }
        
            try {
                Object v = repl.eval(formSrc);
                if (v != null) {
                   System.out.println("⇒ " + v);
                }
            } catch (ParseException e) {
                System.err.println("Parse error: " + e.getMessage());
            } catch (Throwable t) {
                t.printStackTrace(System.err);
            }
        }
    }
}
