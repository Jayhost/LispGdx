// ========================================================================
// io/github/jayhost/Helpers.java
//
// Miscellaneous helper methods and data structures for the JIT compiler.
// ========================================================================
package io.github.jayhost;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

record FunctionSignature(String name, int argCount, String descriptor) {
    FunctionSignature(String name, int argCount) {
        this(name, argCount, "([Ljava/lang/Object;)Ljava/lang/Object;");
    }
}

// Converted from a record to a class to allow for mutable state required by the compiler.
class CompilationContext {
    public final String className;
    public final String parentClassName;
    public final Map<String, Integer> localVars;
    public final Set<String> capturedVars;
    public final ClassWriter classWriter;
    public int nextVarIndex;

    public CompilationContext(String className, String parentClassName, Map<String, Integer> localVars, Set<String> capturedVars) {
        this.className = className;
        this.parentClassName = parentClassName;
        this.localVars = localVars != null ? new HashMap<>(localVars) : new HashMap<>();
        this.capturedVars = capturedVars != null ? capturedVars : Collections.emptySet();
        this.classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        // Default starting index for local variables. Callers can modify this as needed.
        this.nextVarIndex = 1;
    }
}

class Helpers {
    public static boolean isSpecialForm(Expr expr, String name) {
        return (expr instanceof ListExpr list &&
               !list.elements.isEmpty() &&
               list.elements.get(0) instanceof SymbolExpr sym &&
               sym.name.equals(name));
    }
    public static boolean isBuiltIn(String n) {
        // FIXED: Added ">" to the list of recognized built-in operators.
        return switch (n) { case "+", "-", "*", "/", "<", ">", "string-concat" -> true; default -> false; };
    }
    public static String getValidMethodNameForOperator(String op) {
        return switch (op) {
            case "+" -> "op_add";
            case "-" -> "op_sub";
            case "*" -> "op_mul";
            case "/" -> "op_div";
            case "<" -> "op_lt";
            case ">" -> "op_gt"; // FIXED: Added a corresponding method name for ">".
            case "string-concat" -> "op_string_concat";
            default -> throw new IllegalArgumentException("Bad op: " + op);
        };
    }
    public static String getClassNameFromBytecode(byte[] bc) {
        return new ClassReader(bc).getClassName();
    }
}
