package l3diskex;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.prefs.Preferences;


/** 設定ファイル入出力 */
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

        // ファイルパス
        filePath = ini.get("Path", "");

        // エクスポート先パス
        exportFilePath = ini.get("ExportPath", "");

        // 最近使用したファイル
        for (int i = 0; i < MAX_RECENT_FILES; i++) {
            sVal = ini.get("Recent%d".formatted(i), "");
            if (!sVal.isEmpty()) {
                recentFiles.add(sVal);
            }
        }

        // キャラクターコードマップ名
        charCode = ini.get("CharCode", "");

        // リストウィンドウのフォント名
        listFontName = ini.get("ListFontName", "");

        // リストウィンドウのフォントサイズ
        listFontSize = ini.getInt("ListFontSize", 12);

        // ダンプウィンドウのフォント名
        dumpFontName = ini.get("DumpFontName", "");

        // ダンプウィンドウのフォントサイズ
        dumpFontSize = ini.getInt("DumpFontSize", 12);

        // 未使用データを切り落とすか
        trimUnusedData = ini.getBoolean("TrimUnusedData", false);

        // 削除したファイルを表示するか
        showDeletedFile = ini.getBoolean("ShowDeletedFile", false);

        // エクスポート時に属性から拡張子を追加するか
        addExtExport = ini.getBoolean("AddExtensionWhenExport", false);

        // エクスポート時に現在日時を設定するか
        currentDateExport = ini.getBoolean("SetCurrentDateTimeWhenExport", false);

        // インポート時に拡張子で属性を決定したら拡張子を削除するか
        decideAttrImport = ini.getBoolean("DeleteExtensionWhenImport", false);

        // インポートやプロパティ変更時に日時を無視するか
        ignoreDateTime = ini.getBoolean("IgnoreDateTime", false);

        // インポート時に現在日時を設定するか
        currentDateImport = ini.getBoolean("SetCurrentDateTimeWhenImport", false);

        // プロパティで内部データをリストで表示するか
        showInterDirItem = ini.getBoolean("ShowInterDirItem", false);

        // 一度に処理できるディレクトリの深さ
        iVal = ini.getInt("DirectoriesDepth", 0);
        if (iVal >= 1 && iVal <= 100) dirDepth = iVal;

        // ウィンドウ幅
        windowWidth = ini.getInt("WindowWidth", windowWidth);

        // ウィンドウ高さ
        windowHeight = ini.getInt("WindowHeight", windowHeight);

        // テンポラリフォルダのパス
        temporaryFolder = ini.get("TemporaryFolder", "");

        // バイナリエディタのパス
        binaryEditor = ini.get("BinaryEditor", "");

        // テキストエディタのパス
        textEditor = ini.get("TextEditor", "");

        // 言語
        language = ini.get("Language", "");

        // リストのカラム幅
        for (int id = LISTCOL_NAME; id < LISTCOL_END; id++) {
            String key = "ListColumn" + uiDiskFileListColumnDefs[id] + "Width";
            listColumnWidth[id] = ini.getInt(key, 0);
        }

        // リストのカラム位置
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

        // ファイルパス
        ini.put("Path", filePath);

        // エクスポート先パス
        ini.put("ExportPath", exportFilePath);

        // 最近使用したファイル
        for (int i = 0, row = 0; row < MAX_RECENT_FILES && i < recentFiles.size(); i++) {
            String sval = recentFiles.get(i);
            if (sval.isEmpty()) continue;
            ini.put("Recent%d".formatted(row), sval);
            row++;
        }

        // キャラクターコードマップ名
        ini.put("CharCode", charCode);

        // リストウィンドウのフォント名
        ini.put("ListFontName", listFontName);

        // リストウィンドウのフォントサイズ
        ini.putInt("ListFontSize", listFontSize);

        // ダンプウィンドウのフォント名
        ini.put("DumpFontName", dumpFontName);

        // ダンプウィンドウのフォントサイズ
        ini.putInt("DumpFontSize", dumpFontSize);

        // 未使用データを切り落とすか
        ini.putBoolean("TrimUnusedData", trimUnusedData);

        // 削除したファイルを表示するか
        ini.putBoolean("ShowDeletedFile", showDeletedFile);

        // エクスポート時に属性から拡張子を追加するか
        ini.putBoolean("AddExtensionWhenExport", addExtExport);

        // エクスポート時に現在日時を設定するか
        ini.putBoolean("SetCurrentDateTimeWhenExport", currentDateExport);

        // インポート時に拡張子で属性を決定したら拡張子を削除するか
        ini.putBoolean("DeleteExtensionWhenImport", decideAttrImport);

        // インポートやプロパティ変更時に日時を無視するか
        ini.putBoolean("IgnoreDateTime", ignoreDateTime);

        // インポート時に現在日時を設定するか
        ini.putBoolean("SetCurrentDateTimeWhenImport", currentDateImport);

        // プロパティで内部データをリストで表示するか
        ini.putBoolean("ShowInterDirItem", showInterDirItem);

        // 一度に処理できるディレクトリの深さ
        ini.putInt("DirectoriesDepth", dirDepth);

        // ウィンドウ幅
        ini.putInt("WindowWidth", windowWidth);

        // ウィンドウ高さ
        ini.putInt("WindowHeight", windowHeight);

        // テンポラリフォルダのパス
        ini.put("TemporaryFolder", temporaryFolder);

        // バイナリエディタのパス
        ini.put("BinaryEditor", binaryEditor);

        // テキストエディタのパス
        ini.put("TextEditor", textEditor);

        // 言語
        ini.put("Language", language);

        // リストのカラム幅
        for (int id = LISTCOL_NAME; id < LISTCOL_END; id++) {
            String key = "ListColumn" + uiDiskFileListColumnDefs[id] + "Width";
            ini.putInt(key, listColumnWidth[id]);
        }

        // リストのカラム位置
        for (int id = LISTCOL_NAME; id < LISTCOL_END; id++) {
            String key = "ListColumn" + uiDiskFileListColumnDefs[id] + "Pos";
            ini.putInt(key, listColumnPos[id]);
        }
    }

    public static final Config config = new Config();
}

/** 設定ファイルパラメータ */
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
