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

    private static final String GUI_SCREEN_CLASS = "net.minecraft.client.gui.GuiScreen";
    private static final String KEYBOARD = "org/lwjgl/input/Keyboard";
    private static final String HOOK_OWNER = "dev/ingameime/client/KeyboardHook";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!GUI_SCREEN_CLASS.equals(name) && !GUI_SCREEN_CLASS.equals(transformedName)) {
            return basicClass;
        }

        ClassNode classNode = new ClassNode();
        new ClassReader(basicClass).accept(classNode, 0);

        for (MethodNode method : classNode.methods) {
            if (injectHooks(method, classNode.name)) {
                ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
                classNode.accept(writer);
                return writer.toByteArray();
            }
        }

        throw new IllegalStateException("Could not locate GuiScreen keyboard dispatch in " + name);
    }

    private static boolean injectHooks(MethodNode method, String guiScreenOwner) {
        JumpInsnNode eventAvailable = null;
        MethodInsnNode guiHandler = null;

        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode)) {
                continue;
            }
            MethodInsnNode call = (MethodInsnNode) instruction;
            if (isKeyboardNext(call)) {
                AbstractInsnNode branch = nextExecutable(call);
                if (branch instanceof JumpInsnNode && branch.getOpcode() == Opcodes.IFEQ) {
                    eventAvailable = (JumpInsnNode) branch;
                }
            } else if (isGuiKeyboardHandler(call, guiScreenOwner)) {
                guiHandler = call;
            }
        }

        if (eventAvailable == null || guiHandler == null) {
            return false;
        }
        injectEventObserver(method, eventAvailable);
        injectInputGuard(method, guiHandler);
        return true;
    }

    private static void injectEventObserver(MethodNode method, JumpInsnNode eventAvailable) {
        InsnList observer = new InsnList();
        observer.add(new VarInsnNode(Opcodes.ALOAD, 0));
        observer.add(
            new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                HOOK_OWNER,
                "observeKeyboardEvent",
                "(Ljava/lang/Object;)V",
                false));
        method.instructions.insert(eventAvailable, observer);
    }

    private static void injectInputGuard(MethodNode method, MethodInsnNode guiHandler) {
        LabelNode callHandler = new LabelNode();
        LabelNode afterHandler = new LabelNode();
        method.instructions.insert(guiHandler, afterHandler);

        InsnList guard = new InsnList();
        guard.add(new InsnNode(Opcodes.DUP));
        guard.add(
            new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                HOOK_OWNER,
                "handleKeyboardInput",
                "(Ljava/lang/Object;)Z",
                false));
        guard.add(new JumpInsnNode(Opcodes.IFEQ, callHandler));
        guard.add(new InsnNode(Opcodes.POP));
        guard.add(new JumpInsnNode(Opcodes.GOTO, afterHandler));
        guard.add(callHandler);
        method.instructions.insertBefore(guiHandler, guard);
    }

    private static boolean isKeyboardNext(MethodInsnNode call) {
        return call.getOpcode() == Opcodes.INVOKESTATIC && KEYBOARD.equals(call.owner)
            && "next".equals(call.name)
            && "()Z".equals(call.desc);
    }

    private static boolean isGuiKeyboardHandler(MethodInsnNode call, String guiScreenOwner) {
        return call.getOpcode() == Opcodes.INVOKEVIRTUAL && guiScreenOwner.equals(call.owner)
            && "()V".equals(call.desc)
            && ("handleKeyboardInput".equals(call.name) || "func_146282_l".equals(call.name) || "l".equals(call.name));
    }

    private static AbstractInsnNode nextExecutable(AbstractInsnNode instruction) {
        for (AbstractInsnNode current = instruction.getNext(); current != null; current = current.getNext()) {
            if (current.getOpcode() >= 0) {
                return current;
            }
        }
        return null;
    }

}
