// ========================================================================
// io/github/jayhost/LambdaCompiler.java
//
// Compiles a Lisp function or lambda into a new JVM class.
// ========================================================================
package io.github.jayhost;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.util.*;
import static org.objectweb.asm.Opcodes.*;

public class LambdaCompiler {
    private final LispJitCompiler compiler;

    public LambdaCompiler(LispJitCompiler compiler) {
        this.compiler = compiler;
    }

    public byte[] compileFunction(ListExpr defOrLambda,
                              String parentClassName,
                              Map<String,Integer> parentScope) {

    boolean isLambda =
        (defOrLambda.elements.get(0) instanceof SymbolExpr head) &&
        "lambda".equals(head.name);

    /* ---------- split out proto, params, body ---------- */
    List<SymbolExpr> params = new ArrayList<>();
    Expr             body;
    String           funcName;

    if (isLambda) {
        // (lambda (p1 p2 ...) body)
        ListExpr paramList = (ListExpr) defOrLambda.elements.get(1);
        for (Expr p : paramList.elements) params.add((SymbolExpr) p);
        body      = defOrLambda.elements.get(2);
        funcName  = "_lambda_" + LispJitCompiler.DYNAMIC_CLASS_COUNTER.incrementAndGet();
    } else {
        // (def (name p1 p2 ...) body)   ← existing path
        ListExpr proto = (ListExpr) defOrLambda.elements.get(1);
        funcName = ((SymbolExpr) proto.elements.get(0)).name;
        for (int i = 1; i < proto.elements.size(); i++)
            params.add((SymbolExpr) proto.elements.get(i));
        body = defOrLambda.elements.get(2);
    }
    String className = "io/github/jayhost/dynamic/Lambda_" + funcName + "_" +
    LispJitCompiler.DYNAMIC_CLASS_COUNTER.incrementAndGet();
        // The scope for capture analysis contains ONLY the function's own parameters.
        Set<String> analysisScope = new HashSet<>();
        for (SymbolExpr p : params) analysisScope.add(p.name);
        Set<String> captured = CaptureAnalyzer.findCapturedVars(body, analysisScope, compiler);

        // The compilation context for the body has the parameters as its local variables.
        CompilationContext ctx = new CompilationContext(className, parentClassName, new HashMap<>(), captured);
        
        ctx.classWriter.visit(V1_8, ACC_PUBLIC | ACC_SUPER, className, null, "java/lang/Object",
                new String[]{Type.getInternalName(LispCallable.class)});
                // if (captured.isEmpty()) {
                //     // Only add a no-arg constructor if BytecodeGenerator.createConstructor won't
                //     MethodVisitor init = ctx.classWriter.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                //     init.visitCode();
                //     init.visitVarInsn(ALOAD, 0);
                //     init.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                //     init.visitInsn(RETURN);
                //     init.visitMaxs(1, 1);
                //     init.visitEnd();
                // }
        BytecodeGenerator.addRuntimeHelpers(ctx.classWriter);
        
        Map<String, Type> fields = new LinkedHashMap<>();
        for (String v : captured) {
            ctx.classWriter.visitField(
                    ACC_PRIVATE | ACC_FINAL,
                    v,
                    "Ljava/lang/Object;",   // descriptor
                    null,
                    null).visitEnd();
        
            fields.put(v, Type.getType(Object.class));
        }
        BytecodeGenerator.createConstructor(ctx.classWriter, className, fields);

        MethodVisitor mv = ctx.classWriter.visitMethod(ACC_PUBLIC, "apply", "([Ljava/lang/Object;)Ljava/lang/Object;", null, null);
        mv.visitCode();
        ctx.nextVarIndex = 2;
        for (int i = 0; i < params.size(); i++) {
            mv.visitVarInsn(ALOAD, 1);
            mv.visitLdcInsn(i);
            mv.visitInsn(AALOAD);
            int slot = ctx.nextVarIndex++;
            mv.visitVarInsn(ASTORE, slot);
            ctx.localVars.put(params.get(i).name, slot);
        }
        
        new ExpressionCompiler(compiler, ctx).compileExpression(body, mv);
        mv.visitInsn(ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        ctx.classWriter.visitEnd();
        return ctx.classWriter.toByteArray();
    }
}
