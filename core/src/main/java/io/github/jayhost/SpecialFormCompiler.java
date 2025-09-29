// ========================================================================
// io/github/jayhost/SpecialFormCompiler.java
//
// Handles compilation of special forms like `if`, `let`, and `lambda`.
// ========================================================================
package io.github.jayhost;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.objectweb.asm.Opcodes.*;

public class SpecialFormCompiler {
    private final ExpressionCompiler parent;
    private final CompilationContext ctx;

    public SpecialFormCompiler(ExpressionCompiler parent, CompilationContext ctx) {
        this.parent = parent;
        this.ctx = ctx;
    }

    public boolean compileSpecialForm(String op, ListExpr list, MethodVisitor mv) {
        switch (op) {
            case "if": compileIf(list, mv); return true;
            case "let": compileLet(list, mv); return true;
            case "lambda": compileLambda(list, mv); return true;
            case "java-call":compileJavaCall(list, mv); return true; 
        }
        return false;
    }

    private void compileIf(ListExpr ifExpr, MethodVisitor mv) {
        Label elseLbl = new Label();
        Label endLbl = new Label();
        parent.compileExpression(ifExpr.elements.get(1), mv); // cond
        mv.visitMethodInsn(INVOKESTATIC, parent.getRootClassName(), "isTruthy", "(Ljava/lang/Object;)Z", false);
        mv.visitJumpInsn(IFEQ, elseLbl);
        parent.compileExpression(ifExpr.elements.get(2), mv); // then
        mv.visitJumpInsn(GOTO, endLbl);
        mv.visitLabel(elseLbl);
        if (ifExpr.elements.size() > 3) parent.compileExpression(ifExpr.elements.get(3), mv); // else
        else mv.visitInsn(ACONST_NULL);
        mv.visitLabel(endLbl);
    }

    private void compileLet(ListExpr letExpr, MethodVisitor mv) {
        Map<String, Integer> originalLocals = new HashMap<>(ctx.localVars);
        int originalNextVarIndex = ctx.nextVarIndex;

        ListExpr bindings = (ListExpr) letExpr.elements.get(1);
        for (int i = 0; i < bindings.elements.size(); i += 2) {
            parent.compileExpression(bindings.elements.get(i + 1), mv);
            mv.visitVarInsn(ASTORE, ctx.nextVarIndex);
            ctx.localVars.put(((SymbolExpr) bindings.elements.get(i)).name, ctx.nextVarIndex++);
        }
        for (int i = 2; i < letExpr.elements.size(); i++) {
            parent.compileExpression(letExpr.elements.get(i), mv);
            if (i < letExpr.elements.size() - 1) mv.visitInsn(POP);
        }
        ctx.localVars.clear();
        ctx.localVars.putAll(originalLocals);
        ctx.nextVarIndex = originalNextVarIndex;
    }

    private void compileLambda(ListExpr lambdaExpr, MethodVisitor mv) {
        LambdaCompiler lambdaCompiler = new LambdaCompiler(parent.compiler);
        // Pass an empty map for the parent scope. This forces the lambda to correctly
        // analyze its own captures relative to its own parameters.
        byte[] bytecode = lambdaCompiler.compileFunction(lambdaExpr, ctx.className, Collections.emptyMap());

        String lambdaClassName = Helpers.getClassNameFromBytecode(bytecode);
        parent.compiler.defineClass(lambdaClassName.replace('/', '.'), bytecode); // <-- FIXED
        
        mv.visitTypeInsn(NEW, lambdaClassName);
        mv.visitInsn(DUP);
        
        // Analyze captures at the call site to push constructor arguments
        Set<String> lambdaScope = new HashSet<>();
        ((ListExpr)lambdaExpr.elements.get(1)).elements.forEach(p -> lambdaScope.add(((SymbolExpr)p).name));
        Set<String> captured = CaptureAnalyzer.findCapturedVars(lambdaExpr.elements.get(2), lambdaScope, parent.compiler);

        StringBuilder ctorDesc = new StringBuilder("(");
        for (String var : captured) {
            ctorDesc.append("Ljava/lang/Object;");
            // Compile the expression for the captured variable from the parent's context.
            parent.compileExpression(new SymbolExpr(var, -1), mv);
        }
        ctorDesc.append(")V");

        mv.visitMethodInsn(INVOKESPECIAL, lambdaClassName, "<init>", ctorDesc.toString(), false);
    }

    private void compileJavaCall(ListExpr call, MethodVisitor mv) {
        try {
            /* ---------- parse form ---------- */
            Expr   targetExpr = call.elements.get(1);     // class literal or instance expr
            String member1    = ((StringExpr) call.elements.get(2)).value;
    
            int    idx        = 3;                         // cursor in elements list
    
            /* ================================================================
               CASE A – target is a *class literal* ->  static method or field
               ================================================================= */
            if (targetExpr instanceof StringExpr classLit) {
                String className = classLit.value;
                Class<?> clazz   = Class.forName(className);
    
                int argCount = call.elements.size() - idx; // remaining args
    
                /* ---------- 1. try static METHOD first ---------- */
                Method m = tryFindMethod(clazz, member1, argCount, /*static*/true);
                if (m != null) {
                    // compile arg expressions 1-by-1
                    for (int i = idx; i < call.elements.size(); i++)
                        parent.compileExpression(call.elements.get(i), mv);
    
                    mv.visitMethodInsn(INVOKESTATIC,
                                       className.replace('.', '/'),
                                       member1,
                                       org.objectweb.asm.Type.getMethodDescriptor(m),
                                       false);
                    boxAndPadReturn(mv, m.getReturnType());
                    return;
                }
    
                /* ---------- 2. else treat member1 as static FIELD ---------- */
                Field f = clazz.getField(member1); // throws if not found
    
                // GETSTATIC <class>.<field>
                mv.visitFieldInsn(GETSTATIC,
                                  className.replace('.', '/'),
                                  member1,
                                  org.objectweb.asm.Type.getDescriptor(f.getType()));
    
                /* (java-call "java.lang.System" "out")  → just returns PrintStream */
                if (idx == call.elements.size()) {
                    boxAndPadReturn(mv, f.getType());
                    return;
                }
    
                /* But if more tokens follow, member2 must be an instance method */
                Expr m2Expr = call.elements.get(idx++);
                if (!(m2Expr instanceof StringExpr))
                    throw new RuntimeException("Expected instance-method name after field '" +
                                               member1 + '\'');
    
                String   instMethod = ((StringExpr) m2Expr).value;
                Class<?> instType   = f.getType();
                int instArgCount    = call.elements.size() - idx;
    
                Method instM = tryFindMethod(instType, instMethod, instArgCount, /*static*/false);
                if (instM == null)
                    throw new NoSuchMethodException("Method " + instMethod + " on " +
                                                    instType.getName() + " with " +
                                                    instArgCount + " args");
    
                // compile instance-method args
                for (int i = idx; i < call.elements.size(); i++)
                    parent.compileExpression(call.elements.get(i), mv);
    
                mv.visitMethodInsn(INVOKEVIRTUAL,
                                   org.objectweb.asm.Type.getInternalName(instType),
                                   instMethod,
                                   org.objectweb.asm.Type.getMethodDescriptor(instM),
                                   false);
                boxAndPadReturn(mv, instM.getReturnType());
                return;
            }
    
            /* ================================================================
               CASE B – target is an *expression instance*
               ================================================================= */
            // not yet supported – feel free to improve
            throw new UnsupportedOperationException(
                    "General instance java-call not yet implemented.");
    
        } catch (Throwable ex) {
            throw new RuntimeException("Error compiling java-call: " +
                                       ex.getMessage(), ex);
        }
    }
    
    /* ------------------------------------------------------------------ */
    /* helpers                                                            */
    /* ------------------------------------------------------------------ */
    private Method tryFindMethod(Class<?> cls, String name,
                                 int paramCount, boolean wantStatic) {
        for (Method m : cls.getMethods()) {
            if (java.lang.reflect.Modifier.isStatic(m.getModifiers()) != wantStatic) continue;
            if (!m.getName().equals(name)) continue;
            if (m.getParameterCount() != paramCount) continue;
            return m;
        }
        return null;
    }
    
    private void boxAndPadReturn(MethodVisitor mv, Class<?> ret) {
        if (ret == Void.TYPE) {
            mv.visitInsn(ACONST_NULL);           // pad to produce an Object
        } else if (ret.isPrimitive()) {          // box primitives
            String wrapper, desc;
            if (ret == int.class)    { wrapper = "java/lang/Integer"; desc = "(I)Ljava/lang/Integer;";}
            else if (ret == long.class){ wrapper = "java/lang/Long";   desc = "(J)Ljava/lang/Long;";}
            else if (ret == float.class){wrapper = "java/lang/Float";  desc = "(F)Ljava/lang/Float;";}
            else if (ret == double.class){wrapper="java/lang/Double";  desc = "(D)Ljava/lang/Double;";}
            else if (ret == boolean.class){wrapper="java/lang/Boolean";desc = "(Z)Ljava/lang/Boolean;";}
            else if (ret == char.class){wrapper="java/lang/Character";desc="(C)Ljava/lang/Character;";}
            else if (ret == byte.class){wrapper="java/lang/Byte";    desc="(B)Ljava/lang/Byte;";}
            else                     {wrapper="java/lang/Short";   desc="(S)Ljava/lang/Short;";}
            mv.visitMethodInsn(INVOKESTATIC, wrapper, "valueOf", desc, false);
        }
    }
}
