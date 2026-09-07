package dev.ingameime.client.gui;

import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;

import org.lwjgl.input.Keyboard;

import cpw.mods.fml.client.config.GuiCheckBox;
import dev.ingameime.Config;
import dev.ingameime.client.ClientIme;
import dev.ingameime.client.dictionary.GameDictionaryController;
import dev.ingameime.client.dictionary.GameDictionarySnapshot;
import dev.ingameime.rime.RimeRuntimeConfig;

public final class InGameImeConfigScreen extends GuiScreen {

    private static final int DONE = 1;
    private static final int CANCEL = 2;
    private static final int TAB_BASE = 10;
    private static final int MODE_BASE = 100;
    private static final int OPEN_BASE = 110;
    private static final int SHOW_MODE = 120;
    private static final int SHOW_SCHEMA = 121;
    private static final int SHOW_COMMENTS = 122;
    private static final int NOTICE_MINUS = 130;
    private static final int NOTICE_PLUS = 131;
    private static final int GENERATE_DICTIONARY = 140;
    private static final int CANCEL_DICTIONARY = 141;
    private static final int ENABLED = 150;
    private static final int AUTO_DETECT = 151;

    private final GuiScreen parent;
    private final Config.Values draft;
    private Tab tab = Tab.STATUS;
    private GuiCheckBox showMode;
    private GuiCheckBox showSchema;
    private GuiCheckBox showComments;
    private GuiCheckBox enabled;
    private GuiCheckBox autoDetect;
    private GuiButton generateDictionary;
    private GuiButton cancelDictionary;
    private GuiTextField nativeDirectory;
    private GuiTextField sharedDirectory;
    private GuiTextField userDirectory;
    private String implicitNativeDirectory;
    private String implicitSharedDirectory;
    private String implicitUserDirectory;
    private GuiTextField schemaId;
    private GuiTextField requiredModules;

    public InGameImeConfigScreen(GuiScreen parent) {
        this.parent = parent;
        this.draft = Config.snapshot();
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        rebuildControls();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void updateScreen() {
        for (GuiTextField field : textFields()) {
            field.updateCursorCounter();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawRect(0, 0, width, 30, 0xd0181a1d);
        drawCenteredString(fontRendererObj, tr("ingameime.gui.title"), width / 2, 11, 0xffffff);
        drawRect(10, 51, width - 10, 52, 0xff4a4f55);

        switch (tab) {
            case STATUS:
                drawStatus();
                break;
            case INPUT:
                drawInput();
                break;
            case DISPLAY:
                drawDisplay();
                break;
            case DICTIONARY:
                drawDictionary();
                break;
            case ADVANCED:
                drawAdvanced();
                break;
            default:
                break;
        }
        updateDictionaryButtons();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id >= TAB_BASE && button.id < TAB_BASE + Tab.values().length) {
            captureAdvancedFields();
            captureCheckboxes();
            tab = Tab.values()[button.id - TAB_BASE];
            rebuildControls();
            return;
        }
        if (button.id == DONE) {
            captureAdvancedFields();
            captureCheckboxes();
            Config.save(draft);
            mc.displayGuiScreen(parent);
            return;
        }
        if (button.id == CANCEL) {
            mc.displayGuiScreen(parent);
            return;
        }
        if (button.id >= MODE_BASE && button.id < MODE_BASE + 4) {
            draft.modeSwitchKey = new String[] { Config.MODE_SWITCH_SHIFT, Config.MODE_SWITCH_LEFT_SHIFT,
                Config.MODE_SWITCH_CTRL_SHIFT, Config.MODE_SWITCH_DISABLED }[button.id - MODE_BASE];
            rebuildControls();
            return;
        }
        if (button.id >= OPEN_BASE && button.id < OPEN_BASE + 3) {
            draft.openInputMode = new String[] { Config.OPEN_MODE_REMEMBER, Config.OPEN_MODE_CHINESE,
                Config.OPEN_MODE_ENGLISH }[button.id - OPEN_BASE];
            rebuildControls();
            return;
        }
        if (button.id == SHOW_MODE || button.id == SHOW_SCHEMA
            || button.id == SHOW_COMMENTS
            || button.id == ENABLED
            || button.id == AUTO_DETECT) {
            captureCheckboxes();
            return;
        }
        if (button.id == NOTICE_MINUS) {
            draft.modeNoticeMillis = Math.max(1000, draft.modeNoticeMillis - 500);
            rebuildControls();
            return;
        }
        if (button.id == NOTICE_PLUS) {
            draft.modeNoticeMillis = Math.min(10000, draft.modeNoticeMillis + 500);
            rebuildControls();
            return;
        }
        if (button.id == GENERATE_DICTIONARY) {
            GameDictionaryController.getInstance()
                .startGeneration();
            return;
        }
        if (button.id == CANCEL_DICTIONARY) {
            GameDictionaryController.getInstance()
                .cancelGeneration();
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        boolean handled = false;
        for (GuiTextField field : textFields()) {
            handled |= field.textboxKeyTyped(typedChar, keyCode);
        }
        if (handled) {
            return;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parent);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        for (GuiTextField field : textFields()) {
            field.mouseClicked(mouseX, mouseY, mouseButton);
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private void rebuildControls() {
        buttonList.clear();
        clearPageControls();
        int available = Math.max(1, width - 20);
        int tabWidth = Math.max(1, available / Tab.values().length);
        for (Tab value : Tab.values()) {
            int index = value.ordinal();
            int tabX = 10 + tabWidth * index;
            int currentTabWidth = index == Tab.values().length - 1 ? available - tabWidth * index : tabWidth;
            String label = fontRendererObj.trimStringToWidth(tr(value.key), Math.max(1, currentTabWidth - 6));
            GuiButton tabButton = new GuiButton(TAB_BASE + index, tabX, 31, currentTabWidth, 20, label);
            tabButton.enabled = value != tab;
            buttonList.add(tabButton);
        }
        int footerGap = 8;
        int footerWidth = Math.min(100, Math.max(1, (width - 18) / 2));
        int footerLeft = (width - footerWidth * 2 - footerGap) / 2;
        buttonList.add(new GuiButton(DONE, footerLeft, height - 26, footerWidth, 20, tr("gui.done")));
        buttonList.add(
            new GuiButton(
                CANCEL,
                footerLeft + footerWidth + footerGap,
                height - 26,
                footerWidth,
                20,
                tr("gui.cancel")));

        switch (tab) {
            case INPUT:
                addInputControls();
                break;
            case DISPLAY:
                addDisplayControls();
                break;
            case DICTIONARY:
                addDictionaryControls();
                break;
            case ADVANCED:
                addAdvancedControls();
                break;
            default:
                break;
        }
    }

    private void clearPageControls() {
        showMode = null;
        showSchema = null;
        showComments = null;
        enabled = null;
        autoDetect = null;
        generateDictionary = null;
        cancelDictionary = null;
        nativeDirectory = null;
        sharedDirectory = null;
        userDirectory = null;
        implicitNativeDirectory = null;
        implicitSharedDirectory = null;
        implicitUserDirectory = null;
        schemaId = null;
        requiredModules = null;
    }

    private void addInputControls() {
        int margin = Math.min(22, Math.max(1, width / 12));
        int gap = 12;
        int columnWidth = Math.max(1, (width - margin * 2 - gap) / 2);
        int rightColumn = margin + columnWidth + gap;
        String[] modeValues = { Config.MODE_SWITCH_SHIFT, Config.MODE_SWITCH_LEFT_SHIFT, Config.MODE_SWITCH_CTRL_SHIFT,
            Config.MODE_SWITCH_DISABLED };
        String[] modeKeys = { "ingameime.gui.input.shift", "ingameime.gui.input.left_shift",
            "ingameime.gui.input.ctrl_shift", "ingameime.gui.input.disabled" };
        for (int index = 0; index < modeValues.length; index++) {
            buttonList.add(
                new RadioButton(
                    MODE_BASE + index,
                    margin,
                    74 + index * 24,
                    columnWidth,
                    tr(modeKeys[index]),
                    modeValues[index].equals(draft.modeSwitchKey)));
        }
        String[] openValues = { Config.OPEN_MODE_REMEMBER, Config.OPEN_MODE_CHINESE, Config.OPEN_MODE_ENGLISH };
        String[] openKeys = { "ingameime.gui.input.remember", "ingameime.gui.input.chinese",
            "ingameime.gui.input.english" };
        for (int index = 0; index < openValues.length; index++) {
            buttonList.add(
                new RadioButton(
                    OPEN_BASE + index,
                    rightColumn,
                    74 + index * 24,
                    columnWidth,
                    tr(openKeys[index]),
                    openValues[index].equals(draft.openInputMode)));
        }
    }

    private void addDisplayControls() {
        showMode = new GuiCheckBox(
            SHOW_MODE,
            24,
            74,
            tr("ingameime.gui.display.mode_indicator"),
            draft.showModeIndicator);
        showSchema = new GuiCheckBox(
            SHOW_SCHEMA,
            24,
            100,
            tr("ingameime.gui.display.schema_notice"),
            draft.showSchemaNotice);
        showComments = new GuiCheckBox(
            SHOW_COMMENTS,
            24,
            126,
            tr("ingameime.gui.display.candidate_comments"),
            draft.showCandidateComments);
        buttonList.add(showMode);
        buttonList.add(showSchema);
        buttonList.add(showComments);
        buttonList.add(new GuiButton(NOTICE_MINUS, 24, 168, 24, 20, "-"));
        buttonList.add(new GuiButton(NOTICE_PLUS, 150, 168, 24, 20, "+"));
    }

    private void addDictionaryControls() {
        generateDictionary = new GuiButton(
            GENERATE_DICTIONARY,
            22,
            height - 50,
            Math.max(1, Math.min(150, width - 44)),
            20,
            tr("ingameime.gui.dictionary.generate"));
        cancelDictionary = new GuiButton(
            CANCEL_DICTIONARY,
            22,
            height - 50,
            Math.max(1, Math.min(150, width - 44)),
            20,
            tr("ingameime.gui.dictionary.cancel"));
        buttonList.add(generateDictionary);
        buttonList.add(cancelDictionary);
    }

    private void addAdvancedControls() {
        int fieldX = Math.max(1, Math.min(148, width / 2));
        int fieldWidth = Math.max(1, width - fieldX - 22);
        File instanceRoot = new File(mc.mcDataDir, "ingameime");
        implicitNativeDirectory = implicitDirectory(
            draft.nativeLibraryDirectory,
            new File(instanceRoot, "native").getAbsolutePath());
        String effectiveUserDirectory;
        if (!draft.userDataDirectory.trim()
            .isEmpty()) {
            effectiveUserDirectory = new File(draft.userDataDirectory.trim()).getAbsolutePath();
        } else if (draft.autoDetectSystemData) {
            File detected = RimeRuntimeConfig.findSystemUserDataDirectory();
            effectiveUserDirectory = detected == null ? "" : detected.getAbsolutePath();
        } else {
            effectiveUserDirectory = new File(instanceRoot, "user").getAbsolutePath();
        }
        implicitUserDirectory = implicitDirectory(draft.userDataDirectory, effectiveUserDirectory);
        String effectiveSharedDirectory = draft.userDataDirectory.trim()
            .isEmpty() && !draft.autoDetectSystemData ? new File(instanceRoot, "shared").getAbsolutePath()
                : effectiveUserDirectory;
        implicitSharedDirectory = implicitDirectory(draft.sharedDataDirectory, effectiveSharedDirectory);
        nativeDirectory = textField(
            fieldX,
            62,
            fieldWidth,
            displayedDirectory(draft.nativeLibraryDirectory, implicitNativeDirectory),
            512);
        sharedDirectory = textField(
            fieldX,
            86,
            fieldWidth,
            displayedDirectory(draft.sharedDataDirectory, implicitSharedDirectory),
            512);
        userDirectory = textField(
            fieldX,
            110,
            fieldWidth,
            displayedDirectory(draft.userDataDirectory, implicitUserDirectory),
            512);
        schemaId = textField(fieldX, 134, fieldWidth, draft.schemaId, 128);
        requiredModules = textField(fieldX, 158, fieldWidth, draft.requiredModules, 128);
        enabled = new GuiCheckBox(ENABLED, 22, 187, tr("ingameime.gui.advanced.enabled"), draft.enabled);
        autoDetect = new GuiCheckBox(
            AUTO_DETECT,
            Math.min(width / 2, 170),
            187,
            tr("ingameime.gui.advanced.auto_detect"),
            draft.autoDetectSystemData);
        buttonList.add(enabled);
        buttonList.add(autoDetect);
    }

    private GuiTextField textField(int x, int y, int fieldWidth, String value, int maxLength) {
        GuiTextField field = new GuiTextField(fontRendererObj, x, y, fieldWidth, 18);
        field.setMaxStringLength(maxLength);
        field.setText(value);
        return field;
    }

    private void drawStatus() {
        ClientIme ime = ClientIme.getInstance();
        int y = 64;
        y = drawLine(
            y,
            "ingameime.gui.status.state",
            tr(
                "ingameime.state." + ime.getStateName()
                    .toLowerCase(Locale.ROOT)),
            ime.isActive() ? 0x78e08f : 0xe06c75);
        y = drawLine(
            y,
            "ingameime.gui.status.mode",
            tr(ime.isAsciiMode() ? "ingameime.mode.english" : "ingameime.mode.chinese"),
            0xffffff);
        y = drawLine(y, "ingameime.gui.status.schema", value(ime.getActiveSchemaName()), 0xffffff);
        y = drawLine(y, "ingameime.gui.status.schema_id", value(ime.getActiveSchemaId()), 0xb8bec7);
        y = drawLine(y, "ingameime.gui.status.backend", value(ime.getBackendDescription()), 0xb8bec7);
        if (!ime.getDisableReason()
            .isEmpty()) {
            drawWrapped("ingameime.gui.status.reason", ime.getDisableReason(), y, 0xe06c75);
        }
    }

    private void drawInput() {
        int margin = Math.min(22, Math.max(1, width / 12));
        int gap = 12;
        int columnWidth = Math.max(1, (width - margin * 2 - gap) / 2);
        int rightColumn = margin + columnWidth + gap;
        drawString(
            fontRendererObj,
            fontRendererObj.trimStringToWidth(tr("ingameime.gui.input.shortcut"), columnWidth),
            margin,
            61,
            0xdde2e8);
        drawString(
            fontRendererObj,
            fontRendererObj.trimStringToWidth(tr("ingameime.gui.input.open_mode"), columnWidth),
            rightColumn,
            61,
            0xdde2e8);
        drawWrapped("ingameime.gui.input.tap_note", "", 178, 0x9da5ae);
    }

    private void drawDisplay() {
        drawString(fontRendererObj, tr("ingameime.gui.display.title"), 22, 61, 0xdde2e8);
        drawString(
            fontRendererObj,
            tr("ingameime.gui.display.duration", String.format(Locale.ROOT, "%.1f", draft.modeNoticeMillis / 1000.0F)),
            56,
            174,
            0xffffff);
    }

    private void drawDictionary() {
        GameDictionarySnapshot state = GameDictionaryController.getInstance()
            .getSnapshot();
        drawString(fontRendererObj, tr("ingameime.gui.dictionary.target"), 22, 62, 0xdde2e8);
        drawString(
            fontRendererObj,
            fontRendererObj.trimStringToWidth(
                "ingameime_game_phrase.txt + ingameime_game_phrase_flypy.txt",
                Math.max(1, width - 44)),
            22,
            76,
            0xffffff);
        drawString(
            fontRendererObj,
            tr(
                "ingameime.gui.dictionary.phase",
                tr(
                    "ingameime.dictionary.phase." + state.getPhase()
                        .name()
                        .toLowerCase(Locale.ROOT))),
            22,
            98,
            state.getPhase() == GameDictionarySnapshot.Phase.FAILED ? 0xe06c75 : 0xffffff);
        int barLeft = 22;
        int barRight = Math.max(barLeft, width - 22);
        drawRect(barLeft, 116, barRight, 126, 0xff292d32);
        drawRect(barLeft, 116, barLeft + Math.round((barRight - barLeft) * state.getProgress()), 126, 0xff4da36a);
        drawString(
            fontRendererObj,
            tr(
                "ingameime.gui.dictionary.scan_counts",
                state.getScannedItems(),
                state.getTotalItems(),
                state.getCollectedNames()),
            22,
            136,
            0xb8bec7);
        drawString(
            fontRendererObj,
            tr(
                "ingameime.gui.dictionary.result_counts",
                state.getProcessedNames(),
                state.getWrittenEntries(),
                state.getSkippedNames(),
                state.getFailedItems()),
            22,
            149,
            0xb8bec7);
        int messageY = 164;
        if (state.getClassifiedSkippedNames() > 0) {
            String breakdown = tr(
                "ingameime.gui.dictionary.skip_counts",
                state.getNonChineseNames(),
                state.getDuplicateNames(),
                state.getTranslationKeys(),
                state.getEmptyNames());
            drawString(
                fontRendererObj,
                fontRendererObj.trimStringToWidth(breakdown, Math.max(1, width - 44)),
                22,
                162,
                0x8b939d);
            messageY = 177;
        }
        if (!state.getDetail()
            .isEmpty()) {
            drawWrapped("ingameime.gui.dictionary.detail", state.getDetail(), messageY, 0xe06c75);
        } else if (state.isRestartRequired()) {
            drawWrapped("ingameime.gui.dictionary.restart", "", messageY, 0xe5c07b);
        }
        if (state.getUpdatedAt() > 0L) {
            String updated = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(new Date(state.getUpdatedAt()));
            String updatedText = tr("ingameime.gui.dictionary.updated", updated);
            drawString(
                fontRendererObj,
                updatedText,
                Math.max(22, width - 22 - fontRendererObj.getStringWidth(updatedText)),
                height - 43,
                0x8b939d);
        }
    }

    private void drawAdvanced() {
        drawString(fontRendererObj, tr("ingameime.gui.advanced.native"), 22, 67, 0xdde2e8);
        drawString(fontRendererObj, tr("ingameime.gui.advanced.shared"), 22, 91, 0xdde2e8);
        drawString(fontRendererObj, tr("ingameime.gui.advanced.user"), 22, 115, 0xdde2e8);
        drawString(fontRendererObj, tr("ingameime.gui.advanced.schema"), 22, 139, 0xdde2e8);
        drawString(fontRendererObj, tr("ingameime.gui.advanced.modules"), 22, 163, 0xdde2e8);
        for (GuiTextField field : textFields()) {
            field.drawTextBox();
        }
        drawString(fontRendererObj, tr("ingameime.gui.advanced.restart"), 22, 205, 0xe5c07b);
    }

    private int drawLine(int y, String labelKey, String value, int color) {
        String label = tr(labelKey) + ": ";
        drawString(fontRendererObj, label, 22, y, 0xb8bec7);
        drawString(
            fontRendererObj,
            fontRendererObj.trimStringToWidth(value, Math.max(40, width - 34 - fontRendererObj.getStringWidth(label))),
            22 + fontRendererObj.getStringWidth(label),
            y,
            color);
        return y + 16;
    }

    private void drawWrapped(String key, String detail, int y, int color) {
        String text = detail.isEmpty() ? tr(key) : tr(key, detail);
        List<String> lines = fontRendererObj.listFormattedStringToWidth(text, Math.max(1, width - 44));
        for (String line : lines) {
            drawString(fontRendererObj, line, 22, y, color);
            y += fontRendererObj.FONT_HEIGHT + 2;
        }
    }

    private void updateDictionaryButtons() {
        if (generateDictionary == null) {
            return;
        }
        GameDictionarySnapshot state = GameDictionaryController.getInstance()
            .getSnapshot();
        boolean running = state.isRunning();
        boolean cancellable = state.getPhase() == GameDictionarySnapshot.Phase.SCANNING
            || state.getPhase() == GameDictionarySnapshot.Phase.CONVERTING;
        generateDictionary.visible = !running;
        generateDictionary.enabled = true;
        cancelDictionary.visible = cancellable;
    }

    static String displayedDirectory(String configured, String implicit) {
        return implicit == null ? configured : implicit;
    }

    static String capturedDirectory(String displayed, String implicit) {
        return implicit != null && implicit.equals(displayed) ? "" : displayed;
    }

    private static String implicitDirectory(String configured, String effective) {
        return configured.trim()
            .isEmpty() ? effective : null;
    }

    private void captureAdvancedFields() {
        if (nativeDirectory == null) {
            return;
        }
        draft.nativeLibraryDirectory = capturedDirectory(nativeDirectory.getText(), implicitNativeDirectory);
        draft.sharedDataDirectory = capturedDirectory(sharedDirectory.getText(), implicitSharedDirectory);
        draft.userDataDirectory = capturedDirectory(userDirectory.getText(), implicitUserDirectory);
        draft.schemaId = schemaId.getText();
        draft.requiredModules = requiredModules.getText();
    }

    private void captureCheckboxes() {
        if (showMode != null) {
            draft.showModeIndicator = showMode.isChecked();
            draft.showSchemaNotice = showSchema.isChecked();
            draft.showCandidateComments = showComments.isChecked();
        }
        if (enabled != null) {
            draft.enabled = enabled.isChecked();
            draft.autoDetectSystemData = autoDetect.isChecked();
        }
    }

    private List<GuiTextField> textFields() {
        List<GuiTextField> fields = new ArrayList<>();
        if (nativeDirectory != null) {
            fields.add(nativeDirectory);
            fields.add(sharedDirectory);
            fields.add(userDirectory);
            fields.add(schemaId);
            fields.add(requiredModules);
        }
        return fields;
    }

    private static String value(String text) {
        return text == null || text.isEmpty() ? tr("ingameime.gui.value.none") : text;
    }

    private static String tr(String key, Object... arguments) {
        return I18n.format(key, arguments);
    }

    private enum Tab {

        STATUS("ingameime.gui.tab.status"),
        INPUT("ingameime.gui.tab.input"),
        DISPLAY("ingameime.gui.tab.display"),
        DICTIONARY("ingameime.gui.tab.dictionary"),
        ADVANCED("ingameime.gui.tab.advanced");

        final String key;

        Tab(String key) {
            this.key = key;
        }
    }

    private static final class RadioButton extends GuiButton {

        private final boolean selected;

        RadioButton(int id, int x, int y, int width, String label, boolean selected) {
            super(id, x, y, width, 18, label);
            this.selected = selected;
        }

        @Override
        public void drawButton(Minecraft minecraft, int mouseX, int mouseY) {
            if (!visible) {
                return;
            }
            boolean hovered = mouseX >= xPosition && mouseY >= yPosition
                && mouseX < xPosition + width
                && mouseY < yPosition + height;
            int background = hovered ? 0x60464d55 : 0x402e3338;
            drawRect(xPosition, yPosition, xPosition + width, yPosition + height, background);
            drawRect(xPosition + 5, yPosition + 4, xPosition + 15, yPosition + 14, 0xffaeb6bf);
            drawRect(xPosition + 6, yPosition + 5, xPosition + 14, yPosition + 13, 0xff1c2024);
            if (selected) {
                drawRect(xPosition + 8, yPosition + 7, xPosition + 12, yPosition + 11, 0xff69c986);
            }
            String label = minecraft.fontRenderer.trimStringToWidth(displayString, Math.max(1, width - 25));
            minecraft.fontRenderer.drawStringWithShadow(label, xPosition + 21, yPosition + 5, 0xffffff);
        }
    }
}
