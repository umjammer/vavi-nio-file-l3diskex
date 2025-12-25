package l3diskex;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.prefs.Preferences;


/** Configuration file I/O */
public class Config extends Params {

    private String iniFile;

    public Config() {
        iniFile = "";
    }

    // No explicit destructor in Java

    public void setFileName(String file) {
        iniFile = file;
    }

    public void load() {
        if (iniFile.isEmpty()) return;

        int iVal = 0;
        String sVal = "";

        // load ini file
        Preferences ini = Preferences.userNodeForPackage(Config.class);

        // File path
        filePath = ini.get("Path", "");

        // Export destination path
        exportFilePath = ini.get("ExportPath", "");

        // Recently used files
        for (int i = 0; i < MAX_RECENT_FILES; i++) {
            sVal = ini.get("Recent%d".formatted(i), "");
            if (!sVal.isEmpty()) {
                recentFiles.add(sVal);
            }
        }

        // Character code map name
        charCode = ini.get("CharCode", "");

        // List window font name
        listFontName = ini.get("ListFontName", "");

        // List window font size
        listFontSize = ini.getInt("ListFontSize", 12);

        // Dump window font name
        dumpFontName = ini.get("DumpFontName", "");

        // Dump window font size
        dumpFontSize = ini.getInt("DumpFontSize", 12);

        // Whether to trim unused data
        trimUnusedData = ini.getBoolean("TrimUnusedData", false);

        // Whether to show deleted files
        showDeletedFile = ini.getBoolean("ShowDeletedFile", false);

        // Whether to add extension from attribute during export
        addExtExport = ini.getBoolean("AddExtensionWhenExport", false);

        // Whether to set current date and time during export
        currentDateExport = ini.getBoolean("SetCurrentDateTimeWhenExport", false);

        // Whether to delete extension if attribute is determined by extension during import
        decideAttrImport = ini.getBoolean("DeleteExtensionWhenImport", false);

        // Whether to ignore date and time during import or property change
        ignoreDateTime = ini.getBoolean("IgnoreDateTime", false);

        // Whether to set current date and time during import
        currentDateImport = ini.getBoolean("SetCurrentDateTimeWhenImport", false);

        // Whether to display internal data as a list in properties
        showInterDirItem = ini.getBoolean("ShowInterDirItem", false);

        // Directory depth that can be processed at once
        iVal = ini.getInt("DirectoriesDepth", 0);
        if (iVal >= 1 && iVal <= 100) dirDepth = iVal;

        // Window width
        windowWidth = ini.getInt("WindowWidth", windowWidth);

        // Window height
        windowHeight = ini.getInt("WindowHeight", windowHeight);

        // Path to temporary folder
        temporaryFolder = ini.get("TemporaryFolder", "");

        // Path to binary editor
        binaryEditor = ini.get("BinaryEditor", "");

        // Path to text editor
        textEditor = ini.get("TextEditor", "");

        // Language
        language = ini.get("Language", "");

        // Column width of list
        for (int id = LISTCOL_NAME; id < LISTCOL_END; id++) {
            String key = "ListColumn" + uiDiskFileListColumnDefs[id] + "Width";
            listColumnWidth[id] = ini.getInt(key, 0);
        }

        // List column position
        for (int id = LISTCOL_NAME; id < LISTCOL_END; id++) {
            String key = "ListColumn" + uiDiskFileListColumnDefs[id] + "Pos";
            listColumnPos[id] = ini.getInt(key, 0);
        }
    }

    public void load(String file) {
        setFileName(file);
        load();
    }

    public void save() {
        if (iniFile.isEmpty()) return;

        // save ini file
        Preferences ini = Preferences.userNodeForPackage(Config.class);

        // File path
        ini.put("Path", filePath);

        // Export destination path
        ini.put("ExportPath", exportFilePath);

        // Recently used files
        for (int i = 0, row = 0; row < MAX_RECENT_FILES && i < recentFiles.size(); i++) {
            String sval = recentFiles.get(i);
            if (sval.isEmpty()) continue;
            ini.put("Recent%d".formatted(row), sval);
            row++;
        }

        // Character code map name
        ini.put("CharCode", charCode);

        // List window font name
        ini.put("ListFontName", listFontName);

        // List window font size
        ini.putInt("ListFontSize", listFontSize);

        // Dump window font name
        ini.put("DumpFontName", dumpFontName);

        // Dump window font size
        ini.putInt("DumpFontSize", dumpFontSize);

        // Whether to trim unused data
        ini.putBoolean("TrimUnusedData", trimUnusedData);

        // Whether to display deleted files
        ini.putBoolean("ShowDeletedFile", showDeletedFile);

        // Whether to add extension from attribute during export
        ini.putBoolean("AddExtensionWhenExport", addExtExport);

        // Whether to set current date and time during export
        ini.putBoolean("SetCurrentDateTimeWhenExport", currentDateExport);

        // Whether to delete extension if attribute is determined by extension during import
        ini.putBoolean("DeleteExtensionWhenImport", decideAttrImport);

        // Whether to ignore date and time during import or property change
        ini.putBoolean("IgnoreDateTime", ignoreDateTime);

        // Whether to set current date and time during import
        ini.putBoolean("SetCurrentDateTimeWhenImport", currentDateImport);

        // Whether to display internal data as a list in properties
        ini.putBoolean("ShowInterDirItem", showInterDirItem);

        // Directory depth that can be processed at once
        ini.putInt("DirectoriesDepth", dirDepth);

        // Window width
        ini.putInt("WindowWidth", windowWidth);

        // Window height
        ini.putInt("WindowHeight", windowHeight);

        // Path to temporary folder
        ini.put("TemporaryFolder", temporaryFolder);

        // Path to binary editor
        ini.put("BinaryEditor", binaryEditor);

        // Path to text editor
        ini.put("TextEditor", textEditor);

        // Language
        ini.put("Language", language);

        // Column width of list
        for (int id = LISTCOL_NAME; id < LISTCOL_END; id++) {
            String key = "ListColumn" + uiDiskFileListColumnDefs[id] + "Width";
            ini.putInt(key, listColumnWidth[id]);
        }

        // List column position
        for (int id = LISTCOL_NAME; id < LISTCOL_END; id++) {
            String key = "ListColumn" + uiDiskFileListColumnDefs[id] + "Pos";
            ini.putInt(key, listColumnPos[id]);
        }
    }

    public static final Config config = new Config();
}

/** Configuration file parameters */
class Params {

    // Placeholder for unreferenced C++ types and constants
    public static String[] uiDiskFileListColumnDefs = {
            "Name",
            "Size",
            "Date",
            "Time",
            "Attr",
    };

    public static final int LISTCOL_NAME = 0;

    public static final int LISTCOL_END = 5;

    public static final int MAX_RECENT_FILES = 20;

    protected String filePath;
    protected String exportFilePath;
    protected List<String> recentFiles;
    protected String charCode;
    protected String listFontName;
    protected int listFontSize;
    protected String dumpFontName;
    protected int dumpFontSize;
    protected boolean trimUnusedData;
    protected boolean showDeletedFile;
    protected boolean addExtExport;
    protected boolean currentDateExport;
    protected boolean decideAttrImport;
    protected boolean skipImportDialog;
    protected boolean ignoreDateTime;
    protected boolean currentDateImport;
    protected boolean showInterDirItem;
    protected int dirDepth;
    protected int windowWidth;
    protected int windowHeight;
    protected String temporaryFolder;
    protected String binaryEditor;
    protected String textEditor;
    protected String language;
    protected int[] listColumnWidth;
    protected int[] listColumnPos;

    public Params() {
        // default value
        listColumnWidth = new int[LISTCOL_END];
        listColumnPos = new int[LISTCOL_END];

        filePath = "";
        exportFilePath = "";
        recentFiles = new ArrayList<>();
        charCode = "";
        listFontName = "";
        listFontSize = 0;
        dumpFontName = "";
        dumpFontSize = 0;
        trimUnusedData = true;
        showDeletedFile = false;
        addExtExport = true;
        currentDateExport = false;
        decideAttrImport = true;
        skipImportDialog = false;
        ignoreDateTime = false;
        currentDateImport = false;
        // _DEBUG equivalent, assuming false for release build translation
        showInterDirItem = false;
        dirDepth = 20;
        windowWidth = 1000;
        windowHeight = 600;
        temporaryFolder = "";
        binaryEditor = "";
        textEditor = "";
        language = "";

        for (int id = LISTCOL_NAME; id < LISTCOL_END; id++) {
            listColumnWidth[id] = -1;
            listColumnPos[id] = id;
        }
    }

    // @name properties
    public void setFilePath(String val) {
        filePath = Path.of(val).getParent().toString();
    }

    public final String getFilePath() {
        return filePath;
    }

    public void setExportFilePath(String val, boolean isDir) {
        if (isDir) {
            exportFilePath = Path.of(val).toAbsolutePath().toString();
        } else {
            exportFilePath = Path.of(val).toString();
        }
    }

    public final String getExportFilePath() {
        if (exportFilePath.isEmpty()) {
            return filePath;
        } else {
            return exportFilePath;
        }
    }

    public void addRecentFile(String val) {
        Path fpath = Path.of(val);
        filePath = fpath.getParent().toString();
        // Check if the same file exists
        String fullPath = fpath.toAbsolutePath().toString();
        int pos = recentFiles.indexOf(fullPath);
        if (pos >= 0) {
            // Remove it
            recentFiles.remove(pos);
        }
        // Add
        recentFiles.addFirst(fullPath);
        // Remove those exceeding the limit
        if (recentFiles.size() > MAX_RECENT_FILES) {
            recentFiles.remove(MAX_RECENT_FILES);
        }
    }

    public final String getRecentFile() {
        return !recentFiles.isEmpty() ? recentFiles.getFirst() : filePath;
    }

    public final List<String> getRecentFiles() {
        return Collections.unmodifiableList(recentFiles);
    }

    public void setCharCode(String val) {
        charCode = val;
    }

    public final String getCharCode() {
        return charCode;
    }

    public void setListFontName(String val) {
        listFontName = val;
    }

    public final String getListFontName() {
        return listFontName;
    }

    public void setListFontSize(int val) {
        listFontSize = val;
    }

    public int getListFontSize() {
        return listFontSize;
    }

    public void setDumpFontName(String val) {
        dumpFontName = val;
    }

    public final String getDumpFontName() {
        return dumpFontName;
    }

    public void setDumpFontSize(int val) {
        dumpFontSize = val;
    }

    public int getDumpFontSize() {
        return dumpFontSize;
    }

    public void trimUnusedData(boolean val) {
        trimUnusedData = val;
    }

    public boolean isTrimUnusedData() {
        return trimUnusedData;
    }

    public void showDeletedFile(boolean val) {
        showDeletedFile = val;
    }

    public boolean isShownDeletedFile() {
        return showDeletedFile;
    }

    public void addExtensionExport(boolean val) {
        addExtExport = val;
    }

    public boolean isAddExtensionExport() {
        return addExtExport;
    }

    public void setCurrentDateExport(boolean val) {
        currentDateExport = val;
    }

    public boolean isSetCurrentDateExport() {
        return currentDateExport;
    }

    public void decideAttrImport(boolean val) {
        decideAttrImport = val;
    }

    public boolean isDecideAttrImport() {
        return decideAttrImport;
    }

    public void skipImportDialog(boolean val) {
        skipImportDialog = val;
    }

    public boolean isSkipImportDialog() {
        return skipImportDialog;
    }

    public void ignoreDateTime(boolean val) {
        ignoreDateTime = val;
    }

    public boolean doesIgnoreDateTime() {
        return ignoreDateTime;
    }

    public void setCurrentDateImport(boolean val) {
        currentDateImport = val;
    }

    public boolean isSetCurrentDateImport() {
        return currentDateImport;
    }

    public void showInterDirItem(boolean val) {
        showInterDirItem = val;
    }

    public boolean doesShowInterDirItem() {
        return showInterDirItem;
    }

    public void setDirDepth(int val) {
        dirDepth = val;
    }

    public int getDirDepth() {
        return dirDepth;
    }

    public void setWindowWidth(int val) {
        windowWidth = val;
    }

    public int getWindowWidth() {
        return windowWidth;
    }

    public void setWindowHeight(int val) {
        windowHeight = val;
    }

    public int getWindowHeight() {
        return windowHeight;
    }

    public void setTemporaryFolder(String val) {
        temporaryFolder = Path.of(val).toAbsolutePath().toString();
    }

    public final String getTemporaryFolder() {
        return temporaryFolder;
    }

    public void clearTemporaryFolder() {
        temporaryFolder = "";
    }

    public void setBinaryEditor(String val) {
        binaryEditor = Path.of(val).toAbsolutePath().toString();
    }

    public final String getBinaryEditor() {
        return binaryEditor;
    }

    public void setTextEditor(String val) {
        textEditor = Path.of(val).toAbsolutePath().toString();
    }

    public final String getTextEditor() {
        return textEditor;
    }

    public void setLanguage(String val) {
        language = val;
    }

    public final String getLanguage() {
        return language;
    }

    public void setListColumnWidth(int id, int val) {
        listColumnWidth[id] = val;
    }

    public int getListColumnWidth(int id) {
        return listColumnWidth[id];
    }

    public void setListColumnPos(int id, int val) {
        listColumnPos[id] = val;
    }

    public int getListColumnPos(int id) {
        return listColumnPos[id];
    }
}
