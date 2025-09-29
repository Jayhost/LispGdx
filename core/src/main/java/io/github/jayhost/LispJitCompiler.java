// ========================================================================
// io/github/jayhost/LispJitCompiler.java
//
// The core orchestrator for the JIT compilation process. It now operates
// on a shared Environment to maintain state across compilations.
// ========================================================================
package io.github.jayhost;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.objectweb.asm.Opcodes.*;

public class LispJitCompiler {
    public static final AtomicLong DYNAMIC_CLASS_COUNTER = new AtomicLong();
    private final DynamicClassLoader classLoader = new DynamicClassLoader();
    private final Environment env; // <-- The shared environment

    /**
     * Constructs a JIT compiler that will operate on a given environment.
     * @param env The shared environment for functions and variables.
     */
    public LispJitCompiler(Environment env) {
        this.env = env;
    }

    /**
     * Returns the environment associated with this compiler.
     * @return The shared Environment object.
     */
    public Environment getEnvironment() {
        return this.env;
    }
    
    /**
     * Defines a class using the internal dynamic class loader. This provides
     * controlled access without exposing the class loader itself.
     * @param name The fully qualified name of the class to define.
     * @param bytecode The class's raw bytecode.
     * @return The newly loaded Class object.
     */
    public Class<?> defineClass(String name, byte[] bytecode) {
        return classLoader.defineClass(name, bytecode);
    }

    public LispCallable compile(List<Expr> topLevelForms) throws Exception {
        // NOTE: We NO LONGER clear the tables here. State is now persistent
        // in the shared 'env' object.

        String mainClassName = "io/github/jayhost/dynamic/LispScript" + DYNAMIC_CLASS_COUNTER.incrementAndGet();
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(V1_8, ACC_PUBLIC | ACC_SUPER, mainClassName, null, "java/lang/Object",
                new String[]{Type.getInternalName(LispCallable.class)});
        BytecodeGenerator.addRuntimeHelpers(cw);
        BytecodeGenerator.createConstructor(cw, mainClassName, Collections.emptyMap());
        
        // Pass 1: Discover top-level definitions and add them to the shared environment
        for (Expr form : topLevelForms) {
            if (Helpers.isSpecialForm(form, "def")) {
                ListExpr defList = (ListExpr) form;
                ListExpr proto = (ListExpr) defList.elements.get(1);
                String fname = ((SymbolExpr) proto.elements.get(0)).name;
                // Add to the shared environment's function table
                env.functionTable.put(fname, new FunctionSignature(fname, proto.elements.size() - 1));
            } else if (Helpers.isSpecialForm(form, "defvar")) {
                String varName = ((SymbolExpr) ((ListExpr) form).elements.get(1)).name;
                // Add to the shared environment's variable table
                env.globalVarTable.put(varName, Type.getType(Object.class));
            }
        }

        // Pass 2: Compile
        TopLevelCompiler topLevelCompiler = new TopLevelCompiler(this, cw, mainClassName);
        Expr lastExpr = topLevelCompiler.compileTopLevelForms(topLevelForms);

        // Compile the main `apply` method to execute the last expression
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "apply", "([Ljava/lang/Object;)Ljava/lang/Object;", null, null);
        mv.visitCode();
        if (lastExpr != null) {
            CompilationContext mainCtx = new CompilationContext(mainClassName, null, Collections.emptyMap(), Collections.emptySet());
            ExpressionCompiler exprCompiler = new ExpressionCompiler(this, mainCtx);
            exprCompiler.compileExpression(lastExpr, mv);
        } else {
            mv.visitInsn(ACONST_NULL);
        }
        mv.visitInsn(ARETURN);
        mv.visitMaxs(0, 0); // COMPUTE_MAXS will calculate this
        mv.visitEnd();
        cw.visitEnd();

        byte[] bytecode = cw.toByteArray();
        Class<?> clazz = defineClass(mainClassName.replace('/', '.'), bytecode);
        return (LispCallable) clazz.getDeclaredConstructor().newInstance();
    }
}
