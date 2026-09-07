package dev.ingameime.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import dev.ingameime.Config;
import dev.ingameime.rime.RimeSnapshot;

public final class ClientEvents {

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        ClientIme.getInstance()
            .onGuiOpened(event.gui);
    }

    @SubscribeEvent
    public void onDrawScreen(GuiScreenEvent.DrawScreenEvent.Post event) {
        ClientIme ime = ClientIme.getInstance();
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
        modeNotice = font.trimStringToWidth(modeNotice, maxWidth);
        schemaNotice = font.trimStringToWidth(schemaNotice.isEmpty() ? "" : "[" + schemaNotice + "]", maxWidth);
        String preedit = font.trimStringToWidth(snapshot.getPreedit(), maxWidth);
        String candidates = font.trimStringToWidth(candidateLine(snapshot), maxWidth);
        int lines = (modeNotice.isEmpty() ? 0 : 1) + (schemaNotice.isEmpty() ? 0 : 1)
            + (preedit.isEmpty() ? 0 : 1)
            + (candidates.isEmpty() ? 0 : 1);
        int boxWidth = Math.max(
            Math.max(font.getStringWidth(modeNotice), font.getStringWidth(schemaNotice)),
            Math.max(font.getStringWidth(preedit), font.getStringWidth(candidates)));
        InputBounds panel = placePanel(
            target.bounds(),
            event.gui.width,
            event.gui.height,
            boxWidth + 4,
            lines * font.FONT_HEIGHT + 4);
        int x = panel.x + 2;
        int y = panel.y + 2;

        Gui.drawRect(panel.x, panel.y, panel.x + panel.width, panel.y + panel.height, 0xd0000000);
        if (!modeNotice.isEmpty()) {
            font.drawStringWithShadow(modeNotice, x, y, ime.isAsciiMode() ? 0xb0b0b0 : 0x80ff80);
            y += font.FONT_HEIGHT;
        }
        if (!schemaNotice.isEmpty()) {
            font.drawStringWithShadow(schemaNotice, x, y, 0x80ff80);
            y += font.FONT_HEIGHT;
        }
        if (!preedit.isEmpty()) {
            font.drawStringWithShadow(preedit, x, y, 0xffffff);
            y += font.FONT_HEIGHT;
        }
        if (!candidates.isEmpty()) {
            font.drawStringWithShadow(candidates, x, y, 0xffffff);
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

    private static String candidateLine(RimeSnapshot snapshot) {
        StringBuilder line = new StringBuilder();
        List<RimeSnapshot.Candidate> candidates = snapshot.getCandidates();
        for (int index = 0; index < candidates.size(); index++) {
            if (index > 0) {
                line.append("  ");
            }
            RimeSnapshot.Candidate candidate = candidates.get(index);
            if (index == snapshot.getHighlightedCandidate()) {
                line.append("\u00a7e");
            }
            line.append(candidate.getLabel())
                .append('.')
                .append(candidate.getText());
            if (Config.showCandidateComments && !candidate.getComment()
                .isEmpty()) {
                line.append(' ')
                    .append(candidate.getComment());
            }
            if (index == snapshot.getHighlightedCandidate()) {
                line.append("\u00a7r");
            }
        }
        return line.toString();
    }
}
