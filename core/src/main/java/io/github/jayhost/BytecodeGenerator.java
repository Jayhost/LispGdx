// ========================================================================
// io/github/jayhost/BytecodeGenerator.java
//
// A utility class with static methods for generating common JVM
// bytecode patterns using ASM.
// ========================================================================
package io.github.jayhost;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.objectweb.asm.Opcodes.*;

public class BytecodeGenerator {

    public static void createConstructor(ClassWriter cw, String className, Map<String, Type> fields) {
        StringBuilder desc = new StringBuilder("(");
        List<Map.Entry<String,Type>> ordered = new ArrayList<>(fields.entrySet());
        for (Map.Entry<String,Type> e : ordered) desc.append(e.getValue().getDescriptor());
        desc.append(")V");

        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", desc.toString(), null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        int slot = 1;
        for (Map.Entry<String,Type> e : ordered) {
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, slot++);
            mv.visitFieldInsn(PUTFIELD, className, e.getKey(), e.getValue().getDescriptor());
        }
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    public static void addRuntimeHelpers(ClassWriter cw) {
        addIsTruthyHelper(cw);
        addBinaryOpHelper(cw, "op_add", DADD);
        addBinaryOpHelper(cw, "op_sub", DSUB);
        addBinaryOpHelper(cw, "op_mul", DMUL);
        addBinaryOpHelper(cw, "op_div", DDIV);
        addComparisonHelper(cw, "op_lt", IFLT);
        addComparisonHelper(cw, "op_gt", IFGT); // FIXED: Added the greater-than helper.
        addStringConcatHelper(cw);
    }
    
    private static void addIsTruthyHelper(ClassWriter cw) {
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "isTruthy", "(Ljava/lang/Object;)Z", null, null);
        mv.visitCode();
        Label falseLbl = new Label();
        Label endLbl   = new Label();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitJumpInsn(IFNULL, falseLbl);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETSTATIC, "java/lang/Boolean", "FALSE", "Ljava/lang/Boolean;");
        mv.visitJumpInsn(IF_ACMPEQ, falseLbl);
        mv.visitInsn(ICONST_1);
        mv.visitJumpInsn(GOTO, endLbl);
        mv.visitLabel(falseLbl);
        mv.visitInsn(ICONST_0);
        mv.visitLabel(endLbl);
        mv.visitInsn(IRETURN);
        mv.visitMaxs(2, 1);
        mv.visitEnd();
    }
    
    private static void addBinaryOpHelper(ClassWriter cw, String name, int opcode) {
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, name, "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitTypeInsn(CHECKCAST, "java/lang/Number");
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Number", "doubleValue", "()D", false);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitTypeInsn(CHECKCAST, "java/lang/Number");
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Number", "doubleValue", "()D", false);
        mv.visitInsn(opcode);
        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
        mv.visitInsn(ARETURN);
        mv.visitMaxs(4, 2);
        mv.visitEnd();
    }

    private static void addComparisonHelper(ClassWriter cw, String name, int jump) {
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, name, "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", null, null);
        mv.visitCode();
        Label trueLbl = new Label();
        Label endLbl  = new Label();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitTypeInsn(CHECKCAST, "java/lang/Number");
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Number", "doubleValue", "()D", false);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitTypeInsn(CHECKCAST, "java/lang/Number");
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Number", "doubleValue", "()D", false);
        mv.visitInsn(DCMPL);
        mv.visitJumpInsn(jump, trueLbl);
        mv.visitFieldInsn(GETSTATIC, "java/lang/Boolean", "FALSE", "Ljava/lang/Boolean;");
        mv.visitJumpInsn(GOTO, endLbl);
        mv.visitLabel(trueLbl);
        mv.visitFieldInsn(GETSTATIC, "java/lang/Boolean", "TRUE", "Ljava/lang/Boolean;");
        mv.visitLabel(endLbl);
        mv.visitInsn(ARETURN);
        mv.visitMaxs(4, 2);
        mv.visitEnd();
    }

    private static void addStringConcatHelper(ClassWriter cw) {
        MethodVisitor mv = cw.visitMethod(
                ACC_PUBLIC | ACC_STATIC,
                "op_string_concat",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/String;", // correct descriptor
                null,
                null);
        mv.visitCode();
    
        mv.visitTypeInsn(NEW, "java/lang/StringBuilder");
        mv.visitInsn(DUP);
        mv.visitMethodInsn(INVOKESPECIAL,
                           "java/lang/StringBuilder",
                           "<init>",
                           "()V",
                           false);
    
        // append first arg
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKEVIRTUAL,
                           "java/lang/StringBuilder",
                           "append",
                           "(Ljava/lang/Object;)Ljava/lang/StringBuilder;",
                           false);
    
        // append second arg
        mv.visitVarInsn(ALOAD, 1);
        mv.visitMethodInsn(INVOKEVIRTUAL,
                           "java/lang/StringBuilder",
                           "append",
                           "(Ljava/lang/Object;)Ljava/lang/StringBuilder;",
                           false);
    
        // toString
        mv.visitMethodInsn(INVOKEVIRTUAL,
                           "java/lang/StringBuilder",
                           "toString",
                           "()Ljava/lang/String;",
                           false);
    
        mv.visitInsn(ARETURN);
        mv.visitMaxs(3, 2);   // 3 stack elements max, 2 locals (arg0, arg1)
        mv.visitEnd();
    }
}
