// ========================================================================
// io/github/jayhost/TopLevelCompiler.java
//
// Handles the compilation of top-level `def` and `defvar` forms.
// ========================================================================
package io.github.jayhost;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import java.util.Collections;
import java.util.List;
import static org.objectweb.asm.Opcodes.*;

public class TopLevelCompiler {
    private final LispJitCompiler compiler;
    private final ClassWriter cw;
    private final String mainClassName;
    private MethodVisitor staticInitializerMv;

    public TopLevelCompiler(LispJitCompiler compiler, ClassWriter cw, String mainClassName) {
        this.compiler = compiler;
        this.cw = cw;
        this.mainClassName = mainClassName;
    }

    public Expr compileTopLevelForms(List<Expr> forms) {
        Expr lastExpr = null;
        for (Expr form : forms) {
            if (Helpers.isSpecialForm(form, "def")) {
                compileTopLevelDef((ListExpr) form);
            } else if (Helpers.isSpecialForm(form, "defvar")) {
                compileTopLevelVar((ListExpr) form);
            } else if (form != null) {
                lastExpr = form;
            }
        }
        if (staticInitializerMv != null) {
            staticInitializerMv.visitInsn(RETURN);
            staticInitializerMv.visitMaxs(0, 0);
            staticInitializerMv.visitEnd();
        }
        return lastExpr;
    }

    private void ensureClinit() {
        if (staticInitializerMv == null) {
            staticInitializerMv = cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
            staticInitializerMv.visitCode();
        }
    }

    private void compileTopLevelVar(ListExpr varList) {
        String varName = ((SymbolExpr) varList.elements.get(1)).name;
        cw.visitField(ACC_PUBLIC | ACC_STATIC, varName, "Ljava/lang/Object;", null, null).visitEnd();
        ensureClinit();
        
        ExpressionCompiler exprCompiler =
        new ExpressionCompiler(compiler,
        new CompilationContext(mainClassName, null, Collections.emptyMap(), Collections.emptySet()));
        exprCompiler.compileExpression(varList.elements.get(2), staticInitializerMv);
        
        staticInitializerMv.visitFieldInsn(PUTSTATIC, mainClassName, varName, "Ljava/lang/Object;");
    }

    private void compileTopLevelDef(ListExpr defList) {
        String funcName = ((SymbolExpr) ((ListExpr) defList.elements.get(1)).elements.get(0)).name;
        LambdaCompiler lambdaCompiler = new LambdaCompiler(compiler);
        byte[] funcBytecode = lambdaCompiler.compileFunction(defList, mainClassName, Collections.emptyMap());
        
        String funcClassName = Helpers.getClassNameFromBytecode(funcBytecode);
        compiler.defineClass(funcClassName.replace('/', '.'), funcBytecode); // <-- FIXED
        
        ensureClinit();
        cw.visitField(ACC_PUBLIC | ACC_STATIC, funcName, "Lio/github/jayhost/LispCallable;", null, null).visitEnd();
        staticInitializerMv.visitTypeInsn(NEW, funcClassName);
        staticInitializerMv.visitInsn(DUP);
        staticInitializerMv.visitMethodInsn(INVOKESPECIAL, funcClassName, "<init>", "()V", false);
        staticInitializerMv.visitFieldInsn(PUTSTATIC, mainClassName, funcName, "Lio/github/jayhost/LispCallable;");
    }
}
