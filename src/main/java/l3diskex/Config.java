package l3diskex;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.prefs.Preferences;


public class Config {

    // Placeholder for unreferenced C++ types and constants
    public static class gUiDiskFileListColumnDefs {

        // Placeholder for array of column definitions
        public static class ColumnDef {

            public String name;
        }

        public static final ColumnDef[] definitions = new ColumnDef[] {
                new ColumnDef() {{
                    name = "Name";
                }},
                new ColumnDef() {{
                    name = "Size";
                }},
                new ColumnDef() {{
                    name = "Date";
                }},
                new ColumnDef() {{
                    name = "Time";
                }},
                new ColumnDef() {{
                    name = "Attr";
                }}
        };
    }

    public static class LISTCOL_NAME {

        public static final int value = 0;
    }

    public static class LISTCOL_END {

        public static final int value = 5;
    }

    public static final class Constants {

        public static final int MAX_RECENT_FILES = 20;
    }

    /// 設定ファイルパラメータ
    public static class Params {

        protected String mFilePath;
        protected String mExportFilePath;
        protected List<String> mRecentFiles;
        protected String mCharCode;
        protected String mListFontName;
        protected int mListFontSize;
        protected String mDumpFontName;
        protected int mDumpFontSize;
        protected boolean mTrimUnusedData;
        protected boolean mShowDeletedFile;
        protected boolean mAddExtExport;
        protected boolean mCurrentDateExport;
        protected boolean mDecideAttrImport;
        protected boolean mSkipImportDialog;
        protected boolean mIgnoreDateTime;
        protected boolean mCurrentDateImport;
        protected boolean mShowInterDirItem;
        protected int mDirDepth;
        protected int mWindowWidth;
        protected int mWindowHeight;
        protected String mTemporaryFolder;
        protected String mBinaryEditor;
        protected String mTextEditor;
        protected String mLanguage;
        protected int[] mListColumnWidth;
        protected int[] mListColumnPos;

        public Params() {
            // default value
            mListColumnWidth = new int[LISTCOL_END.value];
            mListColumnPos = new int[LISTCOL_END.value];

            mFilePath = "";
            mExportFilePath = "";
            mRecentFiles = new ArrayList<>();
            mCharCode = "";
            mListFontName = "";
            mListFontSize = 0;
            mDumpFontName = "";
            mDumpFontSize = 0;
            mTrimUnusedData = true;
            mShowDeletedFile = false;
            mAddExtExport = true;
            mCurrentDateExport = false;
            mDecideAttrImport = true;
            mSkipImportDialog = false;
            mIgnoreDateTime = false;
            mCurrentDateImport = false;
            // _DEBUG equivalent, assuming false for release build translation
            mShowInterDirItem = false;
            mDirDepth = 20;
            mWindowWidth = 1000;
            mWindowHeight = 600;
            mTemporaryFolder = "";
            mBinaryEditor = "";
            mTextEditor = "";
            mLanguage = "";

            for (int id = LISTCOL_NAME.value; id < LISTCOL_END.value; id++) {
                mListColumnWidth[id] = -1;
                mListColumnPos[id] = id;
            }
        }

        // Since Java doesn't have destructors like C++, the virtual destructor is omitted,
        // but the intention is noted if this were an interface.
        // public void finalize() {} // Not good practice in modern Java

        // @name properties
        public void SetFilePath(final String val) {
            mFilePath = Path.of(val).getParent().toString();
        }

        public final String GetFilePath() {
            return mFilePath;
        }

        public void SetExportFilePath(final String val, boolean is_dir) {
            if (is_dir) {
                mExportFilePath = Path.of(val).toAbsolutePath().toString();
            } else {
                mExportFilePath = Path.of(val).toString();
            }
        }

        public final String GetExportFilePath() {
            if (mExportFilePath.isEmpty()) {
                return mFilePath;
            } else {
                return mExportFilePath;
            }
        }

        public void AddRecentFile(final String val) {
            Path fpath = Path.of(val);
            mFilePath = fpath.getParent().toString();
            // Check if the same file exists
            String fullPath = fpath.toAbsolutePath().toString();
            int pos = mRecentFiles.indexOf(fullPath);
            if (pos >= 0) {
                // Remove it
                mRecentFiles.remove(pos);
            }
            // Add
            mRecentFiles.add(0, fullPath);
            // Remove those exceeding the limit
            if (mRecentFiles.size() > Constants.MAX_RECENT_FILES) {
                mRecentFiles.remove(Constants.MAX_RECENT_FILES);
            }
        }

        public final String GetRecentFile() {
            return mRecentFiles.size() > 0 ? mRecentFiles.get(0) : mFilePath;
        }

        public final List<String> GetRecentFiles() {
            return Collections.unmodifiableList(mRecentFiles);
        }

        public void SetCharCode(final String val) {
            mCharCode = val;
        }

        public final String GetCharCode() {
            return mCharCode;
        }

        public void SetListFontName(final String val) {
            mListFontName = val;
        }

        public final String GetListFontName() {
            return mListFontName;
        }

        public void SetListFontSize(int val) {
            mListFontSize = val;
        }

        public int GetListFontSize() {
            return mListFontSize;
        }

        public void SetDumpFontName(final String val) {
            mDumpFontName = val;
        }

        public final String GetDumpFontName() {
            return mDumpFontName;
        }

        public void SetDumpFontSize(int val) {
            mDumpFontSize = val;
        }

        public int GetDumpFontSize() {
            return mDumpFontSize;
        }

        public void TrimUnusedData(boolean val) {
            mTrimUnusedData = val;
        }

        public boolean IsTrimUnusedData() {
            return mTrimUnusedData;
        }

        public void ShowDeletedFile(boolean val) {
            mShowDeletedFile = val;
        }

        public boolean IsShownDeletedFile() {
            return mShowDeletedFile;
        }

        public void AddExtensionExport(boolean val) {
            mAddExtExport = val;
        }

        public boolean IsAddExtensionExport() {
            return mAddExtExport;
        }

        public void SetCurrentDateExport(boolean val) {
            mCurrentDateExport = val;
        }

        public boolean IsSetCurrentDateExport() {
            return mCurrentDateExport;
        }

        public void DecideAttrImport(boolean val) {
            mDecideAttrImport = val;
        }

        public boolean IsDecideAttrImport() {
            return mDecideAttrImport;
        }

        public void SkipImportDialog(boolean val) {
            mSkipImportDialog = val;
        }

        public boolean IsSkipImportDialog() {
            return mSkipImportDialog;
        }

        public void IgnoreDateTime(boolean val) {
            mIgnoreDateTime = val;
        }

        public boolean DoesIgnoreDateTime() {
            return mIgnoreDateTime;
        }

        public void SetCurrentDateImport(boolean val) {
            mCurrentDateImport = val;
        }

        public boolean IsSetCurrentDateImport() {
            return mCurrentDateImport;
        }

        public void ShowInterDirItem(boolean val) {
            mShowInterDirItem = val;
        }

        public boolean DoesShowInterDirItem() {
            return mShowInterDirItem;
        }

        public void SetDirDepth(int val) {
            mDirDepth = val;
        }

        public int GetDirDepth() {
            return mDirDepth;
        }

        public void SetWindowWidth(int val) {
            mWindowWidth = val;
        }

        public int GetWindowWidth() {
            return mWindowWidth;
        }

        public void SetWindowHeight(int val) {
            mWindowHeight = val;
        }

        public int GetWindowHeight() {
            return mWindowHeight;
        }

        public void SetTemporaryFolder(final String val) {
            mTemporaryFolder = Path.of(val).toAbsolutePath().toString();
        }

        public final String GetTemporaryFolder() {
            return mTemporaryFolder;
        }

        public void ClearTemporaryFolder() {
            mTemporaryFolder = "";
        }

        public void SetBinaryEditor(final String val) {
            mBinaryEditor = Path.of(val).toAbsolutePath().toString();
        }

        public final String GetBinaryEditor() {
            return mBinaryEditor;
        }

        public void SetTextEditor(final String val) {
            mTextEditor = Path.of(val).toAbsolutePath().toString();
        }

        public final String GetTextEditor() {
            return mTextEditor;
        }

        public void SetLanguage(final String val) {
            mLanguage = val;
        }

        public final String GetLanguage() {
            return mLanguage;
        }

        public void SetListColumnWidth(int id, int val) {
            mListColumnWidth[id] = val;
        }

        public int GetListColumnWidth(int id) {
            return mListColumnWidth[id];
        }

        public void SetListColumnPos(int id, int val) {
            mListColumnPos[id] = val;
        }

        public int GetListColumnPos(int id) {
            return mListColumnPos[id];
        }
    }

    /// 設定ファイル入出力
    public static class Config_ extends Params {

        private String ini_file;

        public Config_() {
            super();
            ini_file = "";
        }

        // No explicit destructor in Java

        public void SetFileName(final String file) {
            ini_file = file;
        }

        public void Load() {
            if (ini_file.isEmpty()) return;

            int[] ival = {0};
            String[] sval_arr = {""};
            boolean[] bval_arr = {false};

            // load ini file
            Preferences ini = Preferences.userNodeForPackage(Config.class);

            // ファイルパス
            mFilePath = ini.get("Path", "");

            // エクスポート先パス
            mExportFilePath = ini.get("ExportPath", "");

            // 最近使用したファイル
            for (int i = 0; i < Constants.MAX_RECENT_FILES; i++) {
                sval_arr[0] = ini.get("Recent%d".formatted(i), "");
                if (!sval_arr[0].isEmpty()) {
                    mRecentFiles.add(sval_arr[0]);
                }
            }

            // キャラクターコードマップ名
            mCharCode = ini.get("CharCode", "");

            // リストウィンドウのフォント名
            mListFontName = ini.get("ListFontName", "");

            // リストウィンドウのフォントサイズ
            mListFontSize = ini.getInt("ListFontSize", 12);

            // ダンプウィンドウのフォント名
            mDumpFontName = ini.get("DumpFontName", "");

            // ダンプウィンドウのフォントサイズ
            mDumpFontSize = ini.getInt("DumpFontSize", 12);

            // 未使用データを切り落とすか
            mTrimUnusedData = ini.getBoolean("TrimUnusedData", false);

            // 削除したファイルを表示するか
            mShowDeletedFile = ini.getBoolean("ShowDeletedFile", false);

            // エクスポート時に属性から拡張子を追加するか
            mAddExtExport = ini.getBoolean("AddExtensionWhenExport", false);

            // エクスポート時に現在日時を設定するか
            mCurrentDateExport = ini.getBoolean("SetCurrentDateTimeWhenExport", false);

            // インポート時に拡張子で属性を決定したら拡張子を削除するか
            mDecideAttrImport = ini.getBoolean("DeleteExtensionWhenImport", false);

            // インポートやプロパティ変更時に日時を無視するか
            mIgnoreDateTime = ini.getBoolean("IgnoreDateTime", false);

            // インポート時に現在日時を設定するか
            bval_arr[0] = mCurrentDateImport;
            ini.get("SetCurrentDateTimeWhenImport", "");
            mCurrentDateImport = bval_arr[0];

            // プロパティで内部データをリストで表示するか
            bval_arr[0] = mShowInterDirItem;
            ini.get("ShowInterDirItem", "");
            mShowInterDirItem = bval_arr[0];

            // 一度に処理できるディレクトリの深さ
            ival[0] = ini.getInt("DirectoriesDepth", 0);
            if (ival[0] >= 1 && ival[0] <= 100) mDirDepth = ival[0];

            // ウィンドウ幅
            mWindowWidth = ini.getInt("WindowWidth", mWindowWidth);

            // ウィンドウ高さ
            mWindowHeight = ini.getInt("WindowHeight", mWindowHeight);

            // テンポラリフォルダのパス
            sval_arr[0] = "";
            ini.get("TemporaryFolder", "");
            mTemporaryFolder = sval_arr[0];

            // バイナリエディタのパス
            sval_arr[0] = "";
            ini.get("BinaryEditor", "");
            mBinaryEditor = sval_arr[0];
            if (mBinaryEditor.isEmpty()) {
                sval_arr[0] = "";
                ini.get("BinaryEditer", "");
                mBinaryEditor = sval_arr[0];
            }

            // テキストエディタのパス
            sval_arr[0] = "";
            ini.get("TextEditor", "");
            mTextEditor = sval_arr[0];

            // 言語
            mLanguage = ini.get("Language", "");

            // リストのカラム幅
            for (int id = LISTCOL_NAME.value; id < LISTCOL_END.value; id++) {
                String key = "ListColumn"
                        + gUiDiskFileListColumnDefs.definitions[id].name
                        + "Width";
                ival[0] = mListColumnWidth[id];
                ival[0] = ini.getInt(key, ival[0]);
                mListColumnWidth[id] = ival[0];
            }

            // リストのカラム位置
            for (int id = LISTCOL_NAME.value; id < LISTCOL_END.value; id++) {
                String key = "ListColumn"
                        + gUiDiskFileListColumnDefs.definitions[id].name
                        + "Pos";
                ival[0] = mListColumnPos[id];
                ival[0] = ini.getInt(key, ival[0]);
                mListColumnPos[id] = ival[0];
            }

            // In Java, object destruction is handled by the GC,
            // so `delete ini;` is omitted.
        }

        public void Load(final String file) {
            SetFileName(file);
            Load();
        }

        public void Save() {
            if (ini_file.isEmpty()) return;

            // save ini file
            Preferences ini = Preferences.userNodeForPackage(Config.class);

            // ファイルパス
            ini.put("Path", mFilePath);

            // エクスポート先パス
            ini.put("ExportPath", mExportFilePath);

            // 最近使用したファイル
            for (int i = 0, row = 0; row < Constants.MAX_RECENT_FILES && i < mRecentFiles.size(); i++) {
                String sval = mRecentFiles.get(i);
                if (sval.isEmpty()) continue;
                ini.put("Recent%d".formatted(row), sval);
                row++;
            }

            // キャラクターコードマップ名
            ini.put("CharCode", mCharCode);

            // リストウィンドウのフォント名
            ini.put("ListFontName", mListFontName);

            // リストウィンドウのフォントサイズ
            ini.putInt("ListFontSize", mListFontSize);

            // ダンプウィンドウのフォント名
            ini.put("DumpFontName", mDumpFontName);

            // ダンプウィンドウのフォントサイズ
            ini.putInt("DumpFontSize", mDumpFontSize);

            // 未使用データを切り落とすか
            ini.putBoolean("TrimUnusedData", mTrimUnusedData);

            // 削除したファイルを表示するか
            ini.putBoolean("ShowDeletedFile", mShowDeletedFile);

            // エクスポート時に属性から拡張子を追加するか
            ini.putBoolean("AddExtensionWhenExport", mAddExtExport);

            // エクスポート時に現在日時を設定するか
            ini.putBoolean("SetCurrentDateTimeWhenExport", mCurrentDateExport);

            // インポート時に拡張子で属性を決定したら拡張子を削除するか
            ini.putBoolean("DeleteExtensionWhenImport", mDecideAttrImport);

            // インポートやプロパティ変更時に日時を無視するか
            ini.putBoolean("IgnoreDateTime", mIgnoreDateTime);

            // インポート時に現在日時を設定するか
            ini.putBoolean("SetCurrentDateTimeWhenImport", mCurrentDateImport);

            // プロパティで内部データをリストで表示するか
            ini.putBoolean("ShowInterDirItem", mShowInterDirItem);

            // 一度に処理できるディレクトリの深さ
            ini.putInt("DirectoriesDepth", mDirDepth);

            // ウィンドウ幅
            ini.putInt("WindowWidth", mWindowWidth);

            // ウィンドウ高さ
            ini.putInt("WindowHeight", mWindowHeight);

            // テンポラリフォルダのパス
            ini.put("TemporaryFolder", mTemporaryFolder);

            // バイナリエディタのパス
            ini.put("BinaryEditor", mBinaryEditor);
            ini.remove("BinaryEditer");

            // テキストエディタのパス
            ini.put("TextEditor", mTextEditor);

            // 言語
            ini.put("Language", mLanguage);

            // リストのカラム幅
            for (int id = LISTCOL_NAME.value; id < LISTCOL_END.value; id++) {
                String key = "ListColumn"
                        + gUiDiskFileListColumnDefs.definitions[id].name
                        + "Width";
                ini.putInt(key, mListColumnWidth[id]);
            }

            // リストのカラム位置
            for (int id = LISTCOL_NAME.value; id < LISTCOL_END.value; id++) {
                String key = "ListColumn"
                        + gUiDiskFileListColumnDefs.definitions[id].name
                        + "Pos";
                ini.putInt(key, mListColumnPos[id]);
            }

            // write - In a real wxFileConfig scenario, the delete might commit the changes.
            // For a Java equivalent, this would be a save/flush call.
        }
    }

    public static final Config_ gConfig = new Config_();
}