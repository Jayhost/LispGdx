// ========================================================================
// io/github/jayhost/JitDriver.java
//
// The main driver, updated to work with the new stateful compiler.
// ========================================================================
package io.github.jayhost;

import java.util.List;

public class JitDriver {

    public static void main(String[] args) throws Exception {
        // --- Test 1: Fibonacci ---
        runFibonacciBenchmark();

        // --- Test 2: Let Bindings ---
        runLetBindingTest();

        // --- Test 3: Closures and Lambdas ---
        runClosureTest();

        // --- Test 4: Strings and Java Interop ---
        runStringAndInteropTest();
    }

    public static void runFibonacciBenchmark() throws Exception {
        String source =
                "(def (fib n) (if (< n 2.0) n (+ (fib (- n 1.0)) (fib (- n 2.0)))))" +
                "(fib 35.0)";

        System.out.println("\n--- Fibonacci Benchmark (n=35) ---");

        Parser parser = new Parser(source);
        List<Expr> asts = parser.parseMultiple();
        LispJitCompiler compiler = new LispJitCompiler(new Environment()); // <-- FIXED
        LispCallable fib35 = compiler.compile(asts);

        System.out.println("Executing compiled fib(35)...");
        long startTime = System.nanoTime();
        Object result = fib35.apply(); // The main callable takes no args
        long endTime = System.nanoTime();

        System.out.println("=====================================");
        System.out.println("Fib(35) Result: " + result);
        System.out.println("JIT Execution Time: " + (endTime - startTime) / 1_000_000.0 + " ms");
        System.out.println("=====================================");
    }

    public static void runLetBindingTest() throws Exception {
        String source =
                ";; A function using 'let' for local variables\n" +
                "(def (test-let x)\n" +
                "  (let (y (+ x 5.0) z 2.0)\n" +
                "    (* y z)))\n" +
                "\n" +
                ";; Call the function. Expected: (10 + 5) * 2 = 30\n" +
                "(test-let 10.0)";

        System.out.println("\n--- Let Binding Test ---");
        System.out.println("Lisp Source:\n" + source);

        Parser parser = new Parser(source);
        List<Expr> asts = parser.parseMultiple();
        LispJitCompiler compiler = new LispJitCompiler(new Environment()); // <-- FIXED
        LispCallable letTest = compiler.compile(asts);

        System.out.println("Executing compiled let-test...");
        Object result = letTest.apply();

        System.out.println("=====================================");
        System.out.println("Result of (test-let 10.0): " + result);
        System.out.println("=====================================");
    }

    public static void runClosureTest() throws Exception {
        String source =
                ";; A higher-order function that returns a closure.\n" +
                "(def (make-adder x)\n" +
                "  ;; This lambda captures 'x' from the parent scope.\n" +
                "  (lambda (y) (+ x y)))\n" +
                "\n" +
                ";; Create a specific 'add-5' closure.\n" +
                "(let (add5 (make-adder 5.0))\n" +
                "  ;; Call the closure.\n" +
                "  (add5 10.0)) ;; Expected: 15.0";

        System.out.println("\n--- Closure 'lambda' Test ---");
        System.out.println("Lisp Source:\n" + source);

        Parser parser = new Parser(source);
        List<Expr> asts = parser.parseMultiple();
        LispJitCompiler compiler = new LispJitCompiler(new Environment()); // <-- FIXED
        LispCallable closureTest = compiler.compile(asts);

        System.out.println("Executing compiled closure test...");
        Object result = closureTest.apply();

        System.out.println("=====================================");
        System.out.println("Result of closure test: " + result);
        System.out.println("=====================================");
    }

    public static void runStringAndInteropTest() throws Exception {
        String source =
                ";; --- String and Java Interop Test ---\n" +
                ";; Concatenate two strings.\n" +
                "(let (greeting (string-concat \"Hello, \" \"World!\"))\n" +
                "  ;; Call a static Java method: System.out.println(greeting)\n" +
                "  (java-call \"java.lang.System\" \"out\" \"println\" greeting))";

        System.out.println("\n--- String and Java Interop Test ---");
        System.out.println("Lisp Source:\n" + source);

        Parser parser = new Parser(source);
        List<Expr> asts = parser.parseMultiple();
        LispJitCompiler compiler = new LispJitCompiler(new Environment()); // <-- FIXED
        LispCallable interopTest = compiler.compile(asts);

        System.out.println("Executing compiled string/interop test...");
        Object result = interopTest.apply(); // Should print "Hello, World!"

        System.out.println("=====================================");
        System.out.println("Result of interop test (should be null): " + result);
        System.out.println("=====================================");
    }
}
