package dev.ingameime.asm;

import net.minecraft.launchwrapper.IClassTransformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class GuiScreenTransformer implements IClassTransformer {

    private static final String GUI_SCREEN = "net.minecraft.client.gui.GuiScreen";
    private static final String HOOK_OWNER = "dev/ingameime/client/KeyboardHook";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!GUI_SCREEN.equals(transformedName)) {
            return basicClass;
        }

        ClassNode classNode = new ClassNode();
        new ClassReader(basicClass).accept(classNode, 0);

        for (MethodNode method : classNode.methods) {
            if (isKeyboardHandler(method) && injectHook(method)) {
                ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
                classNode.accept(writer);
                return writer.toByteArray();
            }
        }

        throw new IllegalStateException("Could not locate GuiScreen.handleKeyboardInput in " + name);
    }

    private static boolean isKeyboardHandler(MethodNode method) {
        return "()V".equals(method.desc)
            && ("handleKeyboardInput".equals(method.name) || "func_146282_l".equals(method.name)
                || "l".equals(method.name));
    }

    private static boolean injectHook(MethodNode method) {
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode)) {
                continue;
            }
            MethodInsnNode call = (MethodInsnNode) instruction;
            if (call.getOpcode() != Opcodes.INVOKEVIRTUAL || !"(CI)V".equals(call.desc)
                || !("keyTyped".equals(call.name) || "func_73869_a".equals(call.name) || "a".equals(call.name))) {
                continue;
            }

            LabelNode callKeyTyped = new LabelNode();
            LabelNode afterKeyTyped = new LabelNode();
            method.instructions.insert(call, afterKeyTyped);

            InsnList guard = new InsnList();
            guard.add(new VarInsnNode(Opcodes.ALOAD, 0));
            guard.add(
                new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    HOOK_OWNER,
                    "handleKeyboardInput",
                    "(Ljava/lang/Object;)Z",
                    false));
            guard.add(new JumpInsnNode(Opcodes.IFEQ, callKeyTyped));
            guard.add(new InsnNode(Opcodes.POP));
            guard.add(new InsnNode(Opcodes.POP));
            guard.add(new InsnNode(Opcodes.POP));
            guard.add(new JumpInsnNode(Opcodes.GOTO, afterKeyTyped));
            guard.add(callKeyTyped);
            method.instructions.insertBefore(call, guard);
            return true;
        }
        return false;
    }
}
