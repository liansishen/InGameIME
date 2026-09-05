package dev.ingameime.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import dev.ingameime.rime.RimeSnapshot;

public final class ClientEvents {

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        ClientIme.getInstance()
            .clearComposition();
    }

    @SubscribeEvent
    public void onDrawScreen(GuiScreenEvent.DrawScreenEvent.Post event) {
        ClientIme ime = ClientIme.getInstance();
        if (!ime.isActive() || !(event.gui instanceof GuiChat)) {
            return;
        }

        RimeSnapshot snapshot = ime.getSnapshot();
        String schemaNotice = ime.getSchemaNotice();
        if (!snapshot.isVisible() && schemaNotice.isEmpty()) {
            return;
        }

        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        int maxWidth = Math.max(20, event.gui.width - 12);
        schemaNotice = font.trimStringToWidth(schemaNotice.isEmpty() ? "" : "[" + schemaNotice + "]", maxWidth);
        String preedit = font.trimStringToWidth(snapshot.getPreedit(), maxWidth);
        String candidates = font.trimStringToWidth(candidateLine(snapshot), maxWidth);
        int lines = (schemaNotice.isEmpty() ? 0 : 1) + (preedit.isEmpty() ? 0 : 1) + (candidates.isEmpty() ? 0 : 1);
        int boxWidth = Math.max(
            font.getStringWidth(schemaNotice),
            Math.max(font.getStringWidth(preedit), font.getStringWidth(candidates)));
        int x = 4;
        int y = event.gui.height - 18 - lines * font.FONT_HEIGHT;

        Gui.drawRect(x - 2, y - 2, x + boxWidth + 2, y + lines * font.FONT_HEIGHT + 2, 0xd0000000);
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
            if (!candidate.getComment()
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
