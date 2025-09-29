// ========================================================================
// io/github/jayhost/CaptureAnalyzer.java
//
// Analyzes an expression to find all free variables that need to be
// captured from an enclosing scope.
// ========================================================================
package io.github.jayhost;

import java.util.HashSet;
import java.util.Set;

public class CaptureAnalyzer {

    public static Set<String> findCapturedVars(Expr expr, Set<String> localScope, LispJitCompiler compiler) {
        Set<String> captured = new HashSet<>();
        find(expr, localScope, captured, compiler);
        return captured;
    }

    private static void find(Expr expr, Set<String> localScope, Set<String> captured, LispJitCompiler compiler) {
        Environment env = compiler.getEnvironment();

        if (expr instanceof SymbolExpr sym) {
            String n = sym.name;
            if (!Helpers.isBuiltIn(n) && !localScope.contains(n) &&
                !env.functionTable.containsKey(n) && !env.globalVarTable.containsKey(n)) {
                captured.add(n);
            }
        } else if (expr instanceof ListExpr list) {
            if (list.elements.isEmpty()) return;
            String opName = (list.elements.get(0) instanceof SymbolExpr)
                ? ((SymbolExpr) list.elements.get(0)).name : "";

            switch (opName) {
                case "let":
                    analyzeLet(list, localScope, captured, compiler);
                    break;
                case "lambda":
                    // Lambdas are opaque; their bodies are analyzed when they are compiled,
                    // not during capture analysis of an outer scope.
                    break; 
                case "if":
                case "java-call":
                    // FIXED: For 'if' and 'java-call', which are special syntax,
                    // we only analyze their arguments (from index 1 onwards) for captures.
                    for (int i = 1; i < list.elements.size(); i++) {
                        find(list.elements.get(i), localScope, captured, compiler);
                    }
                    break;
                default:
                    // For a normal function call, we must analyze all elements.
                    // The first element is the function itself, which could be a captured closure.
                    for (Expr element : list.elements) {
                        find(element, localScope, captured, compiler);
                    }
                    break;
            }
        }
    }

    private static void analyzeLet(ListExpr list, Set<String> localScope, Set<String> captured, LispJitCompiler compiler) {
        Set<String> letScope = new HashSet<>(localScope);
        ListExpr bindings = (ListExpr) list.elements.get(1);
        for (int i = 0; i < bindings.elements.size(); i += 2) {
            // Analyze the binding's value expression within the current scope.
            find(bindings.elements.get(i + 1), letScope, captured, compiler);
            // Then add the new variable to the scope for subsequent expressions.
            letScope.add(((SymbolExpr) bindings.elements.get(i)).name);
        }
        // Analyze the body of the 'let' with the newly extended scope.
        for (int i = 2; i < list.elements.size(); i++) {
            find(list.elements.get(i), letScope, captured, compiler);
        }
    }
}
