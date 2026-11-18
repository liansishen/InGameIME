package dev.ingameime.rime.jna;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.sun.jna.Function;
import com.sun.jna.IntegerType;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;

import dev.ingameime.rime.RimeBackend;
import dev.ingameime.rime.RimeKeyResult;
import dev.ingameime.rime.RimeRuntimeConfig;
import dev.ingameime.rime.RimeSnapshot;

public final class JnaRimeBackend implements RimeBackend {

    private static final Object[] NO_ARGUMENTS = new Object[0];
    private static final int MAX_SCHEMA_COUNT = 4096;
    private static final int MAX_CANDIDATE_COUNT = 64;
    private static final int GET_INPUT_FUNCTION_INDEX = 69;
    private static final int SCHEMA_LIST_ITEM_SIZE = new RimeSchemaListItem().size();
    private static final int CANDIDATE_SIZE = new RimeCandidate().size();

    private final RimeCommit reusableCommit = new RimeCommit();
    private final RimeContext reusableContext = new RimeContext();
    private final RimeStatus reusableStatus = new RimeStatus();
    private final Memory asciiModeOption = utf8("ascii_mode");

    private NativeLibrary library;
    private TraitsHolder traits;
    private Function finalizeRime;
    private Function destroySession;
    private Function processKey;
    private Function clearComposition;
    private Function getCommit;
    private Function freeCommit;
    private Function getContext;
    private Function freeContext;
    private Function getStatus;
    private Function freeStatus;
    private Function setOption;
    private Function getInput;
    private Function getSchemaList;
    private Function freeSchemaList;
    private Function selectSchema;
    private final Set<String> availableSchemaIds = new LinkedHashSet<>();

    private Pointer session;
    private boolean initialized;
    private RimeSnapshot snapshot = RimeSnapshot.EMPTY;
    private String currentRawInput = "";
    private String activeSchemaId = "";
    private String activeSchemaName = "default schema";
    private boolean activeAsciiMode;

    public JnaRimeBackend(RimeRuntimeConfig config) {
        library = NativeLibrary.getInstance(
            config.getNativeLibrary()
                .getAbsolutePath());
        try {
            RimeApi api = loadApi(library);

            Function setup = function(api.setup);
            Function initialize = function(api.initialize);
            finalizeRime = function(api.finalizeRime);
            Function startMaintenance = function(api.startMaintenance);
            Function joinMaintenanceThread = function(api.joinMaintenanceThread);
            Function createSession = function(api.createSession);
            destroySession = function(api.destroySession);
            processKey = function(api.processKey);
            clearComposition = function(api.clearComposition);
            getCommit = function(api.getCommit);
            freeCommit = function(api.freeCommit);
            getContext = function(api.getContext);
            freeContext = function(api.freeContext);
            getStatus = function(api.getStatus);
            freeStatus = function(api.freeStatus);
            setOption = function(api.setOption);
            getInput = apiFunction(api, GET_INPUT_FUNCTION_INDEX, "get_input");
            getSchemaList = function(api.getSchemaList);
            freeSchemaList = function(api.freeSchemaList);
            selectSchema = function(api.selectSchema);
            traits = new TraitsHolder(config);

            setup.invokeVoid(new Object[] { traits.pointer() });
            initialize.invokeVoid(new Object[] { traits.pointer() });
            initialized = true;

            if (startMaintenance.invokeInt(new Object[] { 0 }) != 0) {
                joinMaintenanceThread.invokeVoid(NO_ARGUMENTS);
            }
            verifyModules(config.getRequiredModules());

            session = createSession.invokePointer(NO_ARGUMENTS);
            if (session == null) {
                throw new IllegalStateException("librime could not create a session after deployment");
            }
            availableSchemaIds.addAll(readSchemaIds());

            if (!config.getSchemaId()
                .isEmpty()) {
                verifySchemaExists(config.getSchemaId());
                Memory schemaId = utf8(config.getSchemaId());
                if (selectSchema.invokeInt(new Object[] { session, schemaId }) == 0) {
                    throw new IllegalStateException("librime rejected schema: " + config.getSchemaId());
                }
            }
            updateStatus(readStatus(config.getSchemaId()));
            snapshot = readContext();
            refreshRawInput();
        } catch (RuntimeException | Error failure) {
            try {
                close();
            } catch (RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    @Override
    public RimeKeyResult processKey(int keycode, int modifiers) {
        boolean consumed = processKey.invokeInt(new Object[] { session, keycode, modifiers }) != 0;
        String commitText = readCommit();
        snapshot = readContext();
        refreshRawInput();
        SessionStatus status = readStatus("");
        activeAsciiMode = status.asciiMode;
        if (availableSchemaIds.contains(status.schemaId)) {
            updateStatus(status);
        }
        return new RimeKeyResult(consumed, commitText, snapshot, activeSchemaId, activeSchemaName, activeAsciiMode);
    }

    @Override
    public RimeSnapshot clearComposition() {
        clearComposition.invokeVoid(new Object[] { session });
        snapshot = readContext();
        currentRawInput = "";
        return snapshot;
    }

    @Override
    public RimeKeyResult changeAsciiMode(boolean asciiMode, boolean commitRawInput) {
        String commitText = commitRawInput ? readRawInput() : "";
        clearComposition.invokeVoid(new Object[] { session });
        setOption.invokeVoid(new Object[] { session, asciiModeOption, asciiMode ? 1 : 0 });
        snapshot = readContext();
        refreshRawInput();
        SessionStatus status = readStatus("");
        activeAsciiMode = status.asciiMode;
        if (availableSchemaIds.contains(status.schemaId)) {
            updateStatus(status);
        }
        return new RimeKeyResult(true, commitText, snapshot, activeSchemaId, activeSchemaName, activeAsciiMode);
    }

    @Override
    public RimeKeyResult reloadSchema() {
        String schemaId = activeSchemaId;
        boolean asciiMode = activeAsciiMode;
        Memory requestedSchema = utf8(schemaId);
        if (selectSchema.invokeInt(new Object[] { session, requestedSchema }) == 0) {
            throw new IllegalStateException("librime rejected schema reload: " + schemaId);
        }
        setOption.invokeVoid(new Object[] { session, asciiModeOption, asciiMode ? 1 : 0 });
        snapshot = readContext();
        refreshRawInput();
        updateStatus(readStatus(schemaId));
        return new RimeKeyResult(true, "", snapshot, activeSchemaId, activeSchemaName, activeAsciiMode);
    }

    @Override
    public String getDescription() {
        return activeSchemaName + " from "
            + library.getFile()
                .getAbsolutePath();
    }

    @Override
    public String getSchemaId() {
        return activeSchemaId;
    }

    @Override
    public String getSchemaName() {
        return activeSchemaName;
    }

    @Override
    public boolean isAsciiMode() {
        return activeAsciiMode;
    }

    @Override
    public void close() {
        Pointer currentSession = session;
        NativeLibrary currentLibrary = library;
        session = null;
        library = null;
        currentRawInput = "";
        try {
            if (currentSession != null && destroySession != null) {
                destroySession.invokeInt(new Object[] { currentSession });
            }
        } finally {
            try {
                if (initialized && finalizeRime != null) {
                    initialized = false;
                    finalizeRime.invokeVoid(NO_ARGUMENTS);
                }
            } finally {
                if (currentLibrary != null) {
                    currentLibrary.dispose();
                }
            }
        }
    }

    private void verifyModules(List<String> requiredModules) {
        if (requiredModules.isEmpty()) {
            return;
        }

        Function findModule;
        try {
            findModule = library.getFunction("RimeFindModule");
        } catch (UnsatisfiedLinkError failure) {
            throw new IllegalStateException("the installed librime cannot report module capabilities", failure);
        }
        for (String module : requiredModules) {
            Memory moduleName = utf8(module);
            if (findModule.invokePointer(new Object[] { moduleName }) == null) {
                throw new IllegalStateException("required librime module is unavailable: " + module);
            }
        }
    }

    private Set<String> readSchemaIds() {
        RimeSchemaList schemas = new RimeSchemaList();
        schemas.write();
        if (getSchemaList.invokeInt(new Object[] { schemas.getPointer() }) == 0) {
            throw new IllegalStateException("librime could not enumerate deployed schemas");
        }

        try {
            schemas.read();
            long count = schemas.size.longValue();
            if (count < 0 || count > MAX_SCHEMA_COUNT || (count > 0 && schemas.list == null)) {
                throw new IllegalStateException("librime returned an invalid schema list");
            }
            Set<String> schemaIds = new LinkedHashSet<>();
            for (long index = 0; index < count; index++) {
                RimeSchemaListItem item = new RimeSchemaListItem(schemas.list.share(index * SCHEMA_LIST_ITEM_SIZE));
                String schemaId = string(item.schemaId);
                if (!schemaId.isEmpty()) {
                    schemaIds.add(schemaId);
                }
            }
            return schemaIds;
        } finally {
            freeSchemaList.invokeVoid(new Object[] { schemas.getPointer() });
        }
    }

    private void verifySchemaExists(String requestedSchema) {
        if (!availableSchemaIds.contains(requestedSchema)) {
            throw new IllegalStateException("schema is not deployed: " + requestedSchema);
        }
    }

    private SessionStatus readStatus(String requestedSchema) {
        reusableStatus.prepare();
        if (getStatus.invokeInt(new Object[] { session, reusableStatus.getPointer() }) == 0) {
            throw new IllegalStateException("librime could not read session status");
        }

        try {
            reusableStatus.read();
            String schemaId = string(reusableStatus.schemaId);
            String schemaName = string(reusableStatus.schemaName);
            if (reusableStatus.isDisabled != 0) {
                throw new IllegalStateException("the selected Rime schema is disabled");
            }
            if (schemaId.isEmpty()) {
                throw new IllegalStateException("librime returned an empty schema id");
            }
            if (!requestedSchema.isEmpty() && !requestedSchema.equals(schemaId)) {
                throw new IllegalStateException("librime selected " + schemaId + " instead of " + requestedSchema);
            }
            return new SessionStatus(
                schemaId,
                schemaName.isEmpty() ? schemaId : schemaName,
                reusableStatus.isAsciiMode != 0);
        } finally {
            freeStatus.invokeInt(new Object[] { reusableStatus.getPointer() });
        }
    }

    private void updateStatus(SessionStatus status) {
        activeSchemaId = status.schemaId;
        activeSchemaName = status.schemaName;
        activeAsciiMode = status.asciiMode;
    }

    private String readCommit() {
        reusableCommit.prepare();
        if (getCommit.invokeInt(new Object[] { session, reusableCommit.getPointer() }) == 0) {
            return "";
        }
        try {
            reusableCommit.read();
            return string(reusableCommit.text);
        } finally {
            freeCommit.invokeInt(new Object[] { reusableCommit.getPointer() });
        }
    }

    private RimeSnapshot readContext() {
        reusableContext.prepare();
        if (getContext.invokeInt(new Object[] { session, reusableContext.getPointer() }) == 0) {
            return RimeSnapshot.EMPTY;
        }

        try {
            reusableContext.read();
            String preedit = string(reusableContext.composition.preedit);
            int count = reusableContext.menu.numCandidates;
            if (count < 0 || count > MAX_CANDIDATE_COUNT || (count > 0 && reusableContext.menu.candidates == null)) {
                throw new IllegalStateException("librime returned an invalid candidate list");
            }

            List<RimeSnapshot.Candidate> candidates = new ArrayList<>(count);
            String selectKeys = string(reusableContext.menu.selectKeys);
            for (int index = 0; index < count; index++) {
                RimeCandidate candidate = new RimeCandidate(
                    reusableContext.menu.candidates.share((long) index * CANDIDATE_SIZE));
                candidates.add(
                    new RimeSnapshot.Candidate(
                        candidateLabel(reusableContext, selectKeys, index),
                        string(candidate.text),
                        string(candidate.comment)));
            }
            boolean composing = reusableContext.composition.length > 0 || !preedit.isEmpty() || count > 0;
            return new RimeSnapshot(
                preedit,
                reusableContext.composition.cursorPosition,
                composing,
                candidates,
                reusableContext.menu.highlightedCandidateIndex);
        } finally {
            freeContext.invokeInt(new Object[] { reusableContext.getPointer() });
        }
    }

    private static String candidateLabel(RimeContext context, String selectKeys, int index) {
        if (context.selectLabels != null) {
            Pointer label = context.selectLabels.getPointer((long) index * Native.POINTER_SIZE);
            if (label != null) {
                String text = string(label);
                if (!text.isEmpty()) {
                    return text;
                }
            }
        }
        if (index < selectKeys.length()) {
            return String.valueOf(selectKeys.charAt(index));
        }
        return Integer.toString(index + 1);
    }

    private static RimeApi loadApi(NativeLibrary library) {
        Pointer apiPointer = library.getFunction("rime_get_api")
            .invokePointer(NO_ARGUMENTS);
        if (apiPointer == null) {
            throw new IllegalStateException("rime_get_api returned null");
        }
        RimeApi api = new RimeApi(apiPointer);
        if (api.dataSize < api.size() - Integer.BYTES) {
            throw new IllegalStateException("the installed librime API is older than the required v1 API");
        }
        api.requireFunctions();
        return api;
    }

    private static Function apiFunction(RimeApi api, int functionIndex, String name) {
        int firstFunctionOffset = Native.POINTER_SIZE == 8 ? 8 : 4;
        int offset = firstFunctionOffset + functionIndex * Native.POINTER_SIZE;
        if (Integer.BYTES + api.dataSize <= offset) {
            throw new IllegalStateException("the installed librime API does not provide " + name);
        }
        Pointer pointer = api.getPointer()
            .getPointer(offset);
        if (pointer == null) {
            throw new IllegalStateException("the installed librime API does not provide " + name);
        }
        return function(pointer);
    }

    private static Function function(Pointer pointer) {
        return Function.getFunction(pointer);
    }

    private static Memory utf8(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        Memory memory = new Memory(bytes.length + 1L);
        memory.write(0, bytes, 0, bytes.length);
        memory.setByte(bytes.length, (byte) 0);
        return memory;
    }

    private static String string(Pointer pointer) {
        return pointer == null ? "" : pointer.getString(0, StandardCharsets.UTF_8.name());
    }

    @Override
    public String getRawInput() {
        return currentRawInput;
    }

    private void refreshRawInput() {
        currentRawInput = snapshot.isComposing() ? readRawInput() : "";
    }

    private String readRawInput() {
        return string(getInput.invokePointer(new Object[] { session }));
    }

    private static final class SessionStatus {

        private final String schemaId;
        private final String schemaName;
        private final boolean asciiMode;

        SessionStatus(String schemaId, String schemaName, boolean asciiMode) {
            this.schemaId = schemaId;
            this.schemaName = schemaName;
            this.asciiMode = asciiMode;
        }
    }

    private static final class TraitsHolder {

        private final RimeTraits traits = new RimeTraits();
        private final List<Memory> strings = new ArrayList<>();

        TraitsHolder(RimeRuntimeConfig config) {
            traits.sharedDataDir = keep(
                config.getSharedDataDirectory()
                    .getAbsolutePath());
            traits.userDataDir = keep(
                config.getUserDataDirectory()
                    .getAbsolutePath());
            traits.distributionName = keep("InGameIME");
            traits.distributionCodeName = keep("ingameime");
            traits.distributionVersion = keep("1");
            traits.appName = keep("rime.ingameime");
            traits.modules = null;
            traits.minLogLevel = 2;
            traits.logDir = keep("");
            traits.prebuiltDataDir = null;
            traits.stagingDir = null;
            traits.dataSize = traits.size() - Integer.BYTES;
            traits.write();
        }

        Pointer pointer() {
            return traits.getPointer();
        }

        private Pointer keep(String value) {
            Memory memory = utf8(value);
            strings.add(memory);
            return memory;
        }
    }

    @Structure.FieldOrder({ "dataSize", "setup", "setNotificationHandler", "initialize", "finalizeRime",
        "startMaintenance", "isMaintenanceMode", "joinMaintenanceThread", "deployerInitialize", "prebuild", "deploy",
        "deploySchema", "deployConfigFile", "syncUserData", "createSession", "findSession", "destroySession",
        "cleanupStaleSessions", "cleanupAllSessions", "processKey", "commitComposition", "clearComposition",
        "getCommit", "freeCommit", "getContext", "freeContext", "getStatus", "freeStatus", "setOption", "getOption",
        "setProperty", "getProperty", "getSchemaList", "freeSchemaList", "getCurrentSchema", "selectSchema" })
    public static final class RimeApi extends Structure {

        public int dataSize;
        public Pointer setup;
        public Pointer setNotificationHandler;
        public Pointer initialize;
        public Pointer finalizeRime;
        public Pointer startMaintenance;
        public Pointer isMaintenanceMode;
        public Pointer joinMaintenanceThread;
        public Pointer deployerInitialize;
        public Pointer prebuild;
        public Pointer deploy;
        public Pointer deploySchema;
        public Pointer deployConfigFile;
        public Pointer syncUserData;
        public Pointer createSession;
        public Pointer findSession;
        public Pointer destroySession;
        public Pointer cleanupStaleSessions;
        public Pointer cleanupAllSessions;
        public Pointer processKey;
        public Pointer commitComposition;
        public Pointer clearComposition;
        public Pointer getCommit;
        public Pointer freeCommit;
        public Pointer getContext;
        public Pointer freeContext;
        public Pointer getStatus;
        public Pointer freeStatus;
        public Pointer setOption;
        public Pointer getOption;
        public Pointer setProperty;
        public Pointer getProperty;
        public Pointer getSchemaList;
        public Pointer freeSchemaList;
        public Pointer getCurrentSchema;
        public Pointer selectSchema;

        RimeApi(Pointer pointer) {
            super(pointer);
            read();
        }

        void requireFunctions() {
            List<Pointer> required = Arrays.asList(
                setup,
                initialize,
                finalizeRime,
                startMaintenance,
                joinMaintenanceThread,
                createSession,
                destroySession,
                processKey,
                clearComposition,
                getCommit,
                freeCommit,
                getContext,
                freeContext,
                getStatus,
                freeStatus,
                setOption,
                getSchemaList,
                freeSchemaList,
                selectSchema);
            if (required.contains(null)) {
                throw new IllegalStateException("the installed librime API is missing a required function");
            }
        }
    }

    @Structure.FieldOrder({ "dataSize", "sharedDataDir", "userDataDir", "distributionName", "distributionCodeName",
        "distributionVersion", "appName", "modules", "minLogLevel", "logDir", "prebuiltDataDir", "stagingDir" })
    public static final class RimeTraits extends Structure {

        public int dataSize;
        public Pointer sharedDataDir;
        public Pointer userDataDir;
        public Pointer distributionName;
        public Pointer distributionCodeName;
        public Pointer distributionVersion;
        public Pointer appName;
        public Pointer modules;
        public int minLogLevel;
        public Pointer logDir;
        public Pointer prebuiltDataDir;
        public Pointer stagingDir;
    }

    @Structure.FieldOrder({ "length", "cursorPosition", "selectionStart", "selectionEnd", "preedit" })
    public static final class RimeComposition extends Structure {

        public int length;
        public int cursorPosition;
        public int selectionStart;
        public int selectionEnd;
        public Pointer preedit;
    }

    @Structure.FieldOrder({ "text", "comment", "reserved" })
    public static final class RimeCandidate extends Structure {

        public Pointer text;
        public Pointer comment;
        public Pointer reserved;

        RimeCandidate() {}

        RimeCandidate(Pointer pointer) {
            super(pointer);
            read();
        }
    }

    @Structure.FieldOrder({ "pageSize", "pageNumber", "isLastPage", "highlightedCandidateIndex", "numCandidates",
        "candidates", "selectKeys" })
    public static final class RimeMenu extends Structure {

        public int pageSize;
        public int pageNumber;
        public int isLastPage;
        public int highlightedCandidateIndex;
        public int numCandidates;
        public Pointer candidates;
        public Pointer selectKeys;
    }

    @Structure.FieldOrder({ "dataSize", "text" })
    public static final class RimeCommit extends Structure {

        public int dataSize;
        public Pointer text;

        void prepare() {
            clear();
            dataSize = size() - Integer.BYTES;
            writeField("dataSize");
        }
    }

    @Structure.FieldOrder({ "dataSize", "composition", "menu", "commitTextPreview", "selectLabels" })
    public static final class RimeContext extends Structure {

        public int dataSize;
        public RimeComposition composition = new RimeComposition();
        public RimeMenu menu = new RimeMenu();
        public Pointer commitTextPreview;
        public Pointer selectLabels;

        void prepare() {
            clear();
            dataSize = size() - Integer.BYTES;
            writeField("dataSize");
        }
    }

    @Structure.FieldOrder({ "dataSize", "schemaId", "schemaName", "isDisabled", "isComposing", "isAsciiMode",
        "isFullShape", "isSimplified", "isTraditional", "isAsciiPunctuation" })
    public static final class RimeStatus extends Structure {

        public int dataSize;
        public Pointer schemaId;
        public Pointer schemaName;
        public int isDisabled;
        public int isComposing;
        public int isAsciiMode;
        public int isFullShape;
        public int isSimplified;
        public int isTraditional;
        public int isAsciiPunctuation;

        void prepare() {
            clear();
            dataSize = size() - Integer.BYTES;
            writeField("dataSize");
        }
    }

    public static final class SizeT extends IntegerType {

        public SizeT() {
            this(0);
        }

        public SizeT(long value) {
            super(Native.SIZE_T_SIZE, value, true);
        }
    }

    @Structure.FieldOrder({ "schemaId", "name", "reserved" })
    public static final class RimeSchemaListItem extends Structure {

        public Pointer schemaId;
        public Pointer name;
        public Pointer reserved;

        RimeSchemaListItem() {}

        RimeSchemaListItem(Pointer pointer) {
            super(pointer);
            read();
        }
    }

    @Structure.FieldOrder({ "size", "list" })
    public static final class RimeSchemaList extends Structure {

        public SizeT size = new SizeT();
        public Pointer list;
    }
}
