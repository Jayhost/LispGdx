// ========================================================================
// io/github/jayhost/CallCompiler.java
//
// Handles the compilation of built-in operators and function calls.
// ========================================================================
package io.github.jayhost;

import org.objectweb.asm.MethodVisitor;
import java.util.List;
import static org.objectweb.asm.Opcodes.*;

public class CallCompiler {
    private final ExpressionCompiler parent;
    private final CompilationContext ctx;

    public CallCompiler(ExpressionCompiler parent, CompilationContext ctx) {
        this.parent = parent;
        this.ctx = ctx;
    }

    public void compileFunctionCall(ListExpr callExpr, MethodVisitor mv) {
        if (callExpr.elements.get(0) instanceof SymbolExpr sym && Helpers.isBuiltIn(sym.name)) {
            compileBuiltInOperator(callExpr, mv);
            return;
        }

        int argc = callExpr.elements.size() - 1;
        mv.visitLdcInsn(argc);
        mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");
        for (int i = 0; i < argc; i++) {
            mv.visitInsn(DUP);
            mv.visitLdcInsn(i);
            parent.compileExpression(callExpr.elements.get(i + 1), mv);
            mv.visitInsn(AASTORE);
        }
        parent.compileExpression(callExpr.elements.get(0), mv);
        mv.visitInsn(SWAP);
        mv.visitMethodInsn(INVOKEINTERFACE, "io/github/jayhost/LispCallable",
                           "apply", "([Ljava/lang/Object;)Ljava/lang/Object;", true);
    }
    
    private void compileBuiltInOperator(ListExpr opExpr, MethodVisitor mv) {
        String opName = ((SymbolExpr) opExpr.elements.get(0)).name;
        for (int i = 1; i < opExpr.elements.size(); i++) {
            parent.compileExpression(opExpr.elements.get(i), mv);
        }
        String helperMethod = Helpers.getValidMethodNameForOperator(opName);
        String descriptor = opName.equals("string-concat") 
            ? "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/String;"
            : "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;";
        mv.visitMethodInsn(INVOKESTATIC, parent.getRootClassName(), helperMethod, descriptor, false);
    }
}
