package dev.ingameime.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;

import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import dev.ingameime.Config;
import dev.ingameime.rime.RimeSnapshot;

public final class ClientEvents {

    private static final double PANEL_Z_OFFSET = 800.0D;

    private final List<String> candidateRows = new ArrayList<>(10);
    private FontRenderer cachedFont;
    private RimeSnapshot cachedSnapshot;
    private String cachedModeNotice;
    private String cachedSchemaNotice;
    private int cachedMaxWidth = -1;
    private boolean cachedShowComments;
    private String trimmedModeNotice = "";
    private String trimmedSchemaNotice = "";
    private String trimmedPreedit = "";
    private int cachedLines;
    private int cachedBoxWidth;

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        ClientIme.getInstance()
            .onGuiOpened(event.gui);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDrawScreen(GuiScreenEvent.DrawScreenEvent.Post event) {
        ClientIme ime = ClientIme.getInstance();
        if (!Display.isActive()) {
            if (ime.isActive()) {
                ime.clearComposition();
            }
            return;
        }
        if (!ime.isActive()) {
            return;
        }

        InputTarget target = InputTargets.find(event.gui);
        ime.updateInputTarget(event.gui, target);
        if (!ime.isActive() || target == null) {
            return;
        }

        RimeSnapshot snapshot = ime.getSnapshot();
        String modeNotice = ime.getModeNotice();
        String schemaNotice = ime.getSchemaNotice();
        if (!snapshot.isVisible() && modeNotice.isEmpty() && schemaNotice.isEmpty()) {
            return;
        }

        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        int maxWidth = Math.max(20, event.gui.width - 12);
        updateLayoutCache(font, snapshot, modeNotice, schemaNotice, maxWidth, Config.showCandidateComments);

        InputBounds panel = placePanel(
            target.bounds(),
            event.gui.width,
            event.gui.height,
            cachedBoxWidth + 4,
            cachedLines * font.FONT_HEIGHT + 4);
        drawPanelWithGlGuard(panel, font, ime.isAsciiMode());
    }

    private void updateLayoutCache(FontRenderer font, RimeSnapshot snapshot, String modeNotice, String schemaNotice,
        int maxWidth, boolean showComments) {
        if (font == cachedFont && snapshot == cachedSnapshot
            && maxWidth == cachedMaxWidth
            && showComments == cachedShowComments
            && modeNotice.equals(cachedModeNotice)
            && schemaNotice.equals(cachedSchemaNotice)) {
            return;
        }

        cachedFont = font;
        cachedSnapshot = snapshot;
        cachedModeNotice = modeNotice;
        cachedSchemaNotice = schemaNotice;
        cachedMaxWidth = maxWidth;
        cachedShowComments = showComments;

        trimmedModeNotice = modeNotice.isEmpty() ? "" : font.trimStringToWidth(modeNotice, maxWidth);
        trimmedSchemaNotice = schemaNotice.isEmpty() ? "" : font.trimStringToWidth("[" + schemaNotice + "]", maxWidth);
        trimmedPreedit = snapshot.getPreedit()
            .isEmpty() ? "" : font.trimStringToWidth(snapshot.getPreedit(), maxWidth);

        candidateRows.clear();
        List<RimeSnapshot.Candidate> visibleCandidates = snapshot.getCandidates();
        for (int i = 0; i < visibleCandidates.size(); i++) {
            RimeSnapshot.Candidate candidate = visibleCandidates.get(i);
            String label = candidate.getLabel();
            String text = candidate.getText();
            String comment = candidate.getComment();
            boolean highlighted = i == snapshot.getHighlightedCandidate();
            boolean includeComment = showComments && !comment.isEmpty();

            StringBuilder row = new StringBuilder(
                label.length() + text.length() + (includeComment ? comment.length() + 1 : 0) + 4);
            if (highlighted) {
                row.append("\u00a7e");
            }
            if (!label.isEmpty()) {
                row.append(label)
                    .append('.');
            }
            row.append(text);
            if (includeComment) {
                row.append(' ')
                    .append(comment);
            }
            candidateRows.add(font.trimStringToWidth(row.toString(), maxWidth));
        }

        cachedLines = (trimmedModeNotice.isEmpty() ? 0 : 1) + (trimmedSchemaNotice.isEmpty() ? 0 : 1)
            + (trimmedPreedit.isEmpty() ? 0 : 1)
            + candidateRows.size();
        int width = Math.max(
            Math.max(font.getStringWidth(trimmedModeNotice), font.getStringWidth(trimmedSchemaNotice)),
            font.getStringWidth(trimmedPreedit));
        for (int i = 0; i < candidateRows.size(); i++) {
            width = Math.max(width, font.getStringWidth(candidateRows.get(i)));
        }
        cachedBoxWidth = width;
    }

    private void drawPanelWithGlGuard(InputBounds panel, FontRenderer font, boolean asciiMode) {
        boolean lightingEnabled = GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean depthTestEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean alphaTestEnabled = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);

        GL11.glPushMatrix();
        GL11.glTranslated(0.0D, 0.0D, PANEL_Z_OFFSET);
        if (lightingEnabled) {
            GL11.glDisable(GL11.GL_LIGHTING);
        }
        if (depthTestEnabled) {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
        }
        if (!blendEnabled) {
            GL11.glEnable(GL11.GL_BLEND);
        }
        if (!alphaTestEnabled) {
            GL11.glEnable(GL11.GL_ALPHA_TEST);
        }
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        try {
            int x = panel.x + 2;
            int y = panel.y + 2;
            Gui.drawRect(panel.x, panel.y, panel.x + panel.width, panel.y + panel.height, 0xd0000000);
            if (!trimmedModeNotice.isEmpty()) {
                font.drawStringWithShadow(trimmedModeNotice, x, y, asciiMode ? 0xb0b0b0 : 0x80ff80);
                y += font.FONT_HEIGHT;
            }
            if (!trimmedSchemaNotice.isEmpty()) {
                font.drawStringWithShadow(trimmedSchemaNotice, x, y, 0x80ff80);
                y += font.FONT_HEIGHT;
            }
            if (!trimmedPreedit.isEmpty()) {
                font.drawStringWithShadow(trimmedPreedit, x, y, 0xffffff);
                y += font.FONT_HEIGHT;
            }
            for (int i = 0; i < candidateRows.size(); i++) {
                font.drawStringWithShadow(candidateRows.get(i), x, y, 0xffffff);
                y += font.FONT_HEIGHT;
            }
        } finally {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            if (blendEnabled) {
                GL11.glEnable(GL11.GL_BLEND);
            } else {
                GL11.glDisable(GL11.GL_BLEND);
            }
            if (alphaTestEnabled) {
                GL11.glEnable(GL11.GL_ALPHA_TEST);
            } else {
                GL11.glDisable(GL11.GL_ALPHA_TEST);
            }
            if (depthTestEnabled) {
                GL11.glEnable(GL11.GL_DEPTH_TEST);
            } else {
                GL11.glDisable(GL11.GL_DEPTH_TEST);
            }
            if (lightingEnabled) {
                GL11.glEnable(GL11.GL_LIGHTING);
            } else {
                GL11.glDisable(GL11.GL_LIGHTING);
            }
            GL11.glPopMatrix();
        }
    }

    static InputBounds placePanel(InputBounds target, int screenWidth, int screenHeight, int panelWidth,
        int panelHeight) {
        int margin = 2;
        int gap = 3;
        if (target == null) {
            return new InputBounds(
                margin,
                Math.max(margin, screenHeight - margin - panelHeight),
                panelWidth,
                panelHeight);
        }
        int maxX = Math.max(margin, screenWidth - margin - panelWidth);
        int x = Math.max(margin, Math.min(target.x, maxX));
        int above = target.y - gap - panelHeight;
        int below = target.y + target.height + gap;
        int y = above >= margin ? above : below;
        int maxY = Math.max(margin, screenHeight - margin - panelHeight);
        return new InputBounds(x, Math.max(margin, Math.min(y, maxY)), panelWidth, panelHeight);
    }
}
