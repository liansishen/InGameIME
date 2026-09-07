package dev.ingameime.asm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import net.minecraft.client.gui.GuiScreen;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public class GuiScreenTransformerTest {

    private static final String HOOK_OWNER = "dev/ingameime/client/KeyboardHook";
    private static final String GUI_SCREEN = "net/minecraft/client/gui/GuiScreen";

    @Test
    public void observesEveryKeyboardEventAndGuardsGuiKeyboardDispatch() throws Exception {
        byte[] transformed = new GuiScreenTransformer().transform(
            "net.minecraft.client.gui.GuiScreen",
            "net.minecraft.client.gui.GuiScreen",
            classBytes(GuiScreen.class));
        ClassNode guiScreen = new ClassNode();
        new ClassReader(transformed).accept(guiScreen, 0);

        MethodInsnNode observer = findCall(guiScreen, HOOK_OWNER, "observeKeyboardEvent");
        MethodInsnNode hook = findCall(guiScreen, HOOK_OWNER, "handleKeyboardInput");
        MethodInsnNode guiHandler = findGuiKeyboardHandler(guiScreen);
        assertNotNull(observer);
        assertNotNull(hook);
        assertNotNull(guiHandler);

        assertEquals("(Ljava/lang/Object;)V", observer.desc);
        AbstractInsnNode screenValue = previousExecutable(observer);
        assertEquals(Opcodes.ALOAD, screenValue.getOpcode());
        AbstractInsnNode eventBranch = previousExecutable(screenValue);
        assertEquals(Opcodes.IFEQ, eventBranch.getOpcode());
        MethodInsnNode keyboardNext = (MethodInsnNode) previousExecutable(eventBranch);
        assertEquals("org/lwjgl/input/Keyboard", keyboardNext.owner);
        assertEquals("next", keyboardNext.name);

        assertEquals(Opcodes.DUP, previousExecutable(hook).getOpcode());
        assertEquals(Opcodes.INVOKESTATIC, hook.getOpcode());
        assertEquals("(Ljava/lang/Object;)Z", hook.desc);

        JumpInsnNode callBranch = (JumpInsnNode) hook.getNext();
        assertEquals(Opcodes.IFEQ, callBranch.getOpcode());
        AbstractInsnNode discardScreen = callBranch.getNext();
        assertEquals(Opcodes.POP, discardScreen.getOpcode());
        JumpInsnNode skipHandler = (JumpInsnNode) discardScreen.getNext();
        assertEquals(Opcodes.GOTO, skipHandler.getOpcode());
        assertSame(callBranch.label, adjacentLabel(guiHandler, false));
        assertSame(skipHandler.label, adjacentLabel(guiHandler, true));
    }

    @Test
    public void transformsObfuscatedGuiScreenOwner() {
        byte[] transformed = new GuiScreenTransformer()
            .transform("bdw", "net.minecraft.client.gui.GuiScreen", obfuscatedGuiScreen());
        ClassNode guiScreen = new ClassNode();
        new ClassReader(transformed).accept(guiScreen, 0);

        assertEquals("bdw", guiScreen.name);
        assertNotNull(findCall(guiScreen, HOOK_OWNER, "observeKeyboardEvent"));
        assertNotNull(findCall(guiScreen, HOOK_OWNER, "handleKeyboardInput"));
        assertNotNull(findCall(guiScreen, "bdw", "l"));
    }

    private static MethodInsnNode findCall(ClassNode classNode, String owner, String name) {
        for (MethodNode method : classNode.methods) {
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode) instruction;
                    if (owner.equals(call.owner) && name.equals(call.name)) {
                        return call;
                    }
                }
            }
        }
        return null;
    }

    private static MethodInsnNode findGuiKeyboardHandler(ClassNode classNode) {
        for (MethodNode method : classNode.methods) {
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode) instruction;
                    if (GUI_SCREEN.equals(call.owner) && "()V".equals(call.desc)
                        && ("handleKeyboardInput".equals(call.name) || "func_146282_l".equals(call.name)
                            || "l".equals(call.name))) {
                        return call;
                    }
                }
            }
        }
        return null;
    }

    private static LabelNode adjacentLabel(AbstractInsnNode instruction, boolean forward) {
        for (AbstractInsnNode current = forward ? instruction.getNext() : instruction.getPrevious(); current
            != null; current = forward ? current.getNext() : current.getPrevious()) {
            if (current instanceof LabelNode) {
                return (LabelNode) current;
            }
        }
        return null;
    }

    private static AbstractInsnNode previousExecutable(AbstractInsnNode instruction) {
        for (AbstractInsnNode current = instruction.getPrevious(); current != null; current = current.getPrevious()) {
            if (current.getOpcode() >= 0) {
                return current;
            }
        }
        return null;
    }

    private static byte[] obfuscatedGuiScreen() {
        ClassNode guiScreen = new ClassNode();
        guiScreen.version = Opcodes.V1_7;
        guiScreen.access = Opcodes.ACC_PUBLIC;
        guiScreen.name = "bdw";
        guiScreen.superName = "java/lang/Object";

        MethodNode handler = new MethodNode(Opcodes.ACC_PUBLIC, "l", "()V", null, null);
        handler.instructions.add(new InsnNode(Opcodes.RETURN));
        handler.maxLocals = 1;
        guiScreen.methods.add(handler);

        MethodNode input = new MethodNode(Opcodes.ACC_PUBLIC, "k", "()V", null, null);
        LabelNode done = new LabelNode();
        input.instructions
            .add(new MethodInsnNode(Opcodes.INVOKESTATIC, "org/lwjgl/input/Keyboard", "next", "()Z", false));
        input.instructions.add(new JumpInsnNode(Opcodes.IFEQ, done));
        input.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        input.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "bdw", "l", "()V", false));
        input.instructions.add(done);
        input.instructions.add(new InsnNode(Opcodes.RETURN));
        input.maxLocals = 1;
        input.maxStack = 1;
        guiScreen.methods.add(input);

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        guiScreen.accept(writer);
        return writer.toByteArray();
    }

    private static byte[] classBytes(Class<?> type) throws IOException {
        String resource = "/" + type.getName()
            .replace('.', '/') + ".class";
        try (InputStream input = type.getResourceAsStream(resource);
            ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            assertNotNull(input);
            byte[] buffer = new byte[8192];
            for (int read; (read = input.read(buffer)) >= 0;) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }
}
