// ========================================================================
// io/github/jayhost/ExpressionCompiler.java
//
// Compiles individual Lisp expressions into bytecode.
// ========================================================================
package io.github.jayhost;

import org.objectweb.asm.MethodVisitor;
import static org.objectweb.asm.Opcodes.*;

public class ExpressionCompiler {
    public final LispJitCompiler compiler;
    private final CompilationContext ctx;

    public ExpressionCompiler(LispJitCompiler compiler, CompilationContext ctx) {
        this.compiler = compiler;
        this.ctx = ctx;
    }

    public void compileExpression(Expr expr, MethodVisitor mv) {
        if (expr instanceof NumberExpr num) {
            mv.visitLdcInsn(num.value);
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
        } else if (expr instanceof StringExpr str) {
            mv.visitLdcInsn(str.value);
        } else if (expr instanceof SymbolExpr sym) {
            compileSymbol(sym.name, mv);
        } else if (expr instanceof ListExpr list) {
            compileList(list, mv);
        } else {
            throw new IllegalArgumentException("Unknown expression type: " + expr);
        }
    }

    private void compileList(ListExpr list, MethodVisitor mv) {
        if (list.elements.isEmpty()) { mv.visitInsn(ACONST_NULL); return; }
        if (!(list.elements.get(0) instanceof SymbolExpr opSym)) {
            new CallCompiler(this, ctx).compileFunctionCall(list, mv); return;
        }
        SpecialFormCompiler sfCompiler = new SpecialFormCompiler(this, ctx);
        if (sfCompiler.compileSpecialForm(opSym.name, list, mv)) { return; }
        new CallCompiler(this, ctx).compileFunctionCall(list, mv);
    }

    private void compileSymbol(String name, MethodVisitor mv) {
        // Get the shared environment from the compiler
        Environment env = compiler.getEnvironment();

        if (ctx.localVars.containsKey(name)) {
            mv.visitVarInsn(ALOAD, ctx.localVars.get(name));
        } else if (ctx.capturedVars.contains(name)) {
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, ctx.className, name, "Ljava/lang/Object;");
        } else if (env.functionTable.containsKey(name)) { // <-- FIXED
            mv.visitFieldInsn(GETSTATIC, getRootClassName(), name, "Lio/github/jayhost/LispCallable;");
        } else if (env.globalVarTable.containsKey(name)) { // <-- FIXED
            mv.visitFieldInsn(GETSTATIC, getRootClassName(), name, "Ljava/lang/Object;");
        } else {
            throw new RuntimeException("Unresolved symbol: " + name);
        }
    }

    String getRootClassName() {
        return ctx.parentClassName != null ? ctx.parentClassName : ctx.className;
    }
}
