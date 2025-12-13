package l3diskex.ui;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.MenuItem;
import java.awt.event.ActionEvent;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import javax.swing.JPanel;
import javax.swing.JToolBar;

import l3diskex.Utils;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.diskimg.DiskD88;
import l3diskex.diskimg.DiskImage;
import l3diskex.ui.Main.UiDiskAbout;
import l3diskex.ui.Main.UiDiskPanel;
import l3diskex.ui.UIBinDump.UiDiskBinDumpFrame;
import l3diskex.ui.UiDiskFatArea.UiDiskFatAreaFrame;

import static l3diskex.Config.config;
import static l3diskex.ui.UiDiskList.IDM_ADD_DISK_FROM_FILE;
import static l3diskex.ui.UiDiskList.IDM_ADD_DISK_NEW;
import static l3diskex.ui.UiDiskList.IDM_DELETE_DISK_FROM_FILE;
import static l3diskex.ui.UiDiskList.IDM_FORMAT_DISK;
import static l3diskex.ui.UiDiskList.IDM_INITIALIZE_DISK;
import static l3diskex.ui.UiDiskList.IDM_RENAME_DISK;
import static l3diskex.ui.UiDiskList.IDM_REPLACE_DISK_FROM_FILE;
import static l3diskex.ui.UiDiskList.IDM_SAVE_DISK;


// uimainframe
public class UiDiskFrame extends UiDiskProcess {

    enum enEditorTypes {
        EDITOR_TYPE_BINARY,
        EDITOR_TYPE_TEXT,
    }

    static class StatusCounter {

        private int m_current;
        private int m_count;
        private int m_using;
        private String m_message;

        public StatusCounter() {
            m_current = 0;
            m_count = 0;
            m_using = 0;
        }

        public void Clear() {
            m_current = 0;
            m_count = 0;
            m_using = 0;
            m_message = "";
        }

        public void Start(int count, String message) {
            m_current = 0;
            m_count = count;
            m_using = 1;
            m_message = message;
        }

        public void Append(int count) {
            m_count += count;
        }

        public void Increase() {
            m_current++;
        }

        public void Finish(String message) {
            m_message = message;
            m_using = 3;
        }

        public int Current() {
            return m_current;
        }

        public int Count() {
            return m_count;
        }

        public boolean IsIdle() {
            return (m_using == 0);
        }

        public boolean IsFinished() {
            return (m_using == 3);
        }

        public String GetCurrentMessage() {
            String str = "%d/%d ".formatted(m_current, m_count);
            str += m_message;
            return str;
        }
    }

    static class StatusCounters {

        private static final int StatusCountersMax = 3;
        private final StatusCounter[] m_sc;
        private final Object m_delay; // wxTimer placeholder

        public static final int IDT_STATUS_COUNTER = 555;

        public StatusCounters() {
            m_sc = new StatusCounter[StatusCountersMax];
            for (int i = 0; i < StatusCountersMax; i++) {
                m_sc[i] = new StatusCounter();
            }
            m_delay = new Object();
        }

        public StatusCounter Item(int idx) {
            if (idx < 0 || idx >= StatusCountersMax) idx = 0;
            return m_sc[idx];
        }

        public void Clear() {
            for (int idx = 0; idx < StatusCountersMax; idx++) {
                StatusCounter sc = m_sc[idx];
                if (sc.IsFinished()) {
                    sc.Clear();
                }
            }
        }

        public int Start(int count, String message) {
            int decide = -1;
            for (int idx = 0; idx < StatusCountersMax; idx++) {
                StatusCounter sc = m_sc[idx];
                if (sc.IsIdle()) {
                    sc.Start(count, message);
                    // m_delay.Stop(); // Mocked
                    Clear();
                    decide = idx;
                    break;
                }
            }
            return decide;
        }

        public void Append(int idx, int count) {
            if (idx < 0 || idx >= StatusCountersMax) idx = 0;
            m_sc[idx].Append(count);
        }

        public void Increase(int idx) {
            if (idx < 0 || idx >= StatusCountersMax) idx = 0;
            m_sc[idx].Increase();
        }

        public void Finish(int idx, String message, WindowEvent owner) {
            if (idx < 0 || idx >= StatusCountersMax) idx = 0;
            m_sc[idx].Finish(message);
            // m_delay.SetOwner(owner, IDT_STATUS_COUNTER); // Mocked
            // m_delay.StartOnce(5000); // Mocked
        }

        public int Current(int idx) {
            if (idx < 0 || idx >= StatusCountersMax) idx = 0;
            return m_sc[idx].Current();
        }

        public int Count(int idx) {
            if (idx < 0 || idx >= StatusCountersMax) idx = 0;
            return m_sc[idx].Count();
        }

        public String GetCurrentMessage(int idx) {
            if (idx < 0 || idx >= StatusCountersMax) idx = 0;
            return m_sc[idx].GetCurrentMessage();
        }
    }

    private MyMenu menuFile;
    private MyMenu menuRecentFiles;
    private MyMenu menuData;
    private MyMenu menuMode;
    private MyMenu menuView;
    private MyMenu menuHelp;

    private final UiDiskPanel panel;
    private final UiDiskBinDumpFrame bindump_frame;
    private final UiDiskFatAreaFrame fatarea_frame;

    private DiskImage p_image;

    private StatusCounters stat_counters;

    private final Utils.StopWatch m_sw_export;
    private final Utils.StopWatch m_sw_import;

    private static final int IDT_TOOLBAR = 500;
    private static final int TOOLBAR_STYLE = 0; // Mocked wxTB_FLAT | wxTB_DOCKABLE | wxTB_TEXT

    public UiDiskFrame(String title, Dimension size) {
        super(title);

        p_image = new DiskD88.DiskD88Image();

        MakeMenu();

        RecreateStatusbar();

        RecreateToolbar();

        panel = new UiDiskPanel(this);

        bindump_frame = null;
        fatarea_frame = null;

        m_sw_export = new Utils.StopWatch();
        m_sw_import = new Utils.StopWatch();
    }

    // Java equivalent of destructor logic
    public void cleanup() {
        // Dimension sz = GetSize() / GetClientSize() mocked
        config.setWindowWidth(0);
        config.setWindowHeight(0);

        p_image = null; // delete p_image;
    }

    /** フレーム部の初期処理 */
    public boolean Init(String in_file) {
        boolean valid = false;

        if (in_file != null && !in_file.isEmpty()) {
            valid = PreOpenDataFile(in_file);
        }
        if (!valid) {
            UpdateMenuFile();
            UpdateMenuDisk();
            UpdateMenuMode();
            UpdateToolBar();
        }
        if (panel != null) {
            SetDefaultCharCode();
        }

        return true;
    }

    // --- event procedures ---

    /** ウィンドウを閉じたとき */
    public void OnClose(WindowEvent event) {
        if (!CloseDataFile(!event.CanVeto())) {
            event.Veto();
            return;
        }
        event.Skip();
    }

    /** メニュー 終了選択 */
    public void OnQuit(ActionEvent event) {
        Close(false); // Mocked
    }

    /// メニュー Aboutダイアログ表示選択
    public void OnAbout(ActionEvent event) {
        new UiDiskAbout(this, 0).setVisible(true);
    }

    /** メニュー 新規作成選択 */
    public void OnCreateFile(ActionEvent event) {
        ShowCreateFileDialog(); // Mocked
    }

    /** メニュー 開く選択 */
    public void OnOpenFile(ActionEvent event) {
        ShowOpenFileDialog(); // Mocked
    }

    /** メニュー 最近使用したファイル開く選択 */
    public void OnOpenRecentFile(ActionEvent event) {
        MenuItem item = menuRecentFiles.FindItem(event.GetId());
        if (item == null) return;
        Path path = Path.of("dummy_path"); // item.GetItemLabel() mocked
        if (!CloseDataFile()) return;
        PreOpenDataFile(path.toAbsolutePath().toString());
    }

    /** メニュー 閉じる選択 */
    public void OnCloseFile(ActionEvent event) {
        CloseDataFile();
    }

    /** メニュー 名前を付けて保存選択 */
    public void OnSaveAsFile(ActionEvent event) {
        ShowSaveFileDialog(); // Mocked
    }

    /// メニュー ディスク1枚を保存選択
    public void OnSaveDisk(ActionEvent event) {
        UiDiskList list = GetDiskListPanel();
        if (list == null) return;
        list.showSaveDiskDialog();
    }

    /** メニュー ディスクを新規に追加選択 */
    public void OnAddNewDisk(ActionEvent event) {
        ShowAddNewDiskDialog(); // Mocked
    }

    /** メニュー ディスクをファイルから追加選択 */
    public void OnAddDiskFromFile(ActionEvent event) {
        ShowAddFileDialog(); // Mocked
    }

    /** メニュー ディスクを置換選択 */
    public void OnReplaceDisk(ActionEvent event) {
        UiDiskList list = GetDiskListPanel();
        if (list == null) return;
        list.replaceDisk();
    }

    /** メニュー ファイルからディスクを削除選択 */
    public void OnDeleteDiskFromFile(ActionEvent event) {
        DeleteDisk(); // Mocked
    }

    /** メニュー ディスク名を変更選択 */
    public void OnRenameDisk(ActionEvent event) {
        RenameDisk(); // Mocked
    }

    /** メニュー 初期化選択 */
    public void OnInitializeDisk(ActionEvent event) {
        InitializeDisk(); // Mocked
    }

    /** メニュー フォーマット選択 */
    public void OnFormatDisk(ActionEvent event) {
        FormatDisk(); // Mocked
    }

    /** メニュー エクスポート選択 */
    public void OnExportDataFromDisk(ActionEvent event) {
        ExportDataFromDisk(); // Mocked
    }

    /** メニュー インポート選択 */
    public void OnImportDataToDisk(ActionEvent event) {
        ImportDataToDisk(); // Mocked
    }

    /** メニュー 削除選択 */
    public void OnDeleteDataFromDisk(ActionEvent event) {
        DeleteDataFromDisk(); // Mocked
    }

    /** メニュー リネーム選択 */
    public void OnRenameDataOnDisk(ActionEvent event) {
        RenameDataOnDisk(); // Mocked
    }

    /** メニュー コピー選択 */
    public void OnCopyDataFromDisk(ActionEvent event) {
        CopyDataFromDisk(); // Mocked
    }

    /** メニュー ペースト選択 */
    public void OnPasteDataToDisk(ActionEvent event) {
        PasteDataToDisk(); // Mocked
    }

    /** メニュー ディレクトリ作成選択 */
    public void OnMakeDirectoryOnDisk(ActionEvent event) {
        MakeDirectoryOnDisk(); // Mocked
    }

    /** メニュー ファイル編集選択 */
    public void OnEditFileOnDisk(ActionEvent event) {
        EditFileOnDisk(event.getActionCommand() == Global.IDM_EDIT_FILE_BINARY ? enEditorTypes.EDITOR_TYPE_BINARY : enEditorTypes.EDITOR_TYPE_TEXT);
    }

    /** メニュー プロパティ選択 */
    public void OnPropertyOnDisk(ActionEvent event) {
        PropertyOnDisk(); // Mocked
    }

    /** ファイルモード選択 */
    public void OnBasicMode(ActionEvent event) {
        ChangeRPanel(0, null); // Mocked
    }

    /// Rawディスクモード選択
    public void OnRawDiskMode(ActionEvent event) {
        ChangeRPanel(1, null); // Mocked
    }

    /** キャラクターコード選択 */
    public void OnChangeCharCode(ActionEvent event) {
        int sel = event.GetId() - Global.IDM_CHAR_0;
        String name = Global.gCharCodeChoices.GetItemName("main", sel);
        ChangeCharCode(name); // Mocked
    }

    /** 未使用データを切り落とすか */
    public void OnTrimData(ActionEvent event) {
        Global.gConfig.TrimUnusedData(event.IsChecked());
    }

    /** 削除ファイルを表示するか */
    public void OnShowDeletedFile(ActionEvent event) {
        Global.gConfig.ShowDeletedFile(event.IsChecked());
        SetFileListData(); // Mocked
    }

    /** ダンプウィンドウ選択 */
    public void OnOpenBinDump(ActionEvent event) {
        if (bindump_frame == null) {
            OpenBinDumpWindow(); // Mocked
        } else {
            CloseBinDumpWindow(); // Mocked
        }
    }

    /** 使用状況ウィンドウ選択 */
    public void OnOpenFatArea(ActionEvent event) {
        if (fatarea_frame == null) {
            OpenFatAreaWindow(); // Mocked
        } else {
            CloseFatAreaWindow(); // Mocked
        }
    }

    /** ファイルリストの列選択 */
    public void OnChangeColumnsOfFileList(ActionEvent event) {
        ChangeColumnsOfFileList(); // Mocked
    }

    /** フォント変更選択 */
    public void OnChangeFont(ActionEvent event) {
        ShowListFontDialog(); // Mocked
    }

    /** メニュー 設定ダイアログ選択 */
    public void OnConfigure(ActionEvent event) {
        ShowConfigureDialog(); // Mocked
    }

    /** ステータスカウンター終了タイマー */
    public void OnTimerStatusCounter() {
        ClearStatusCounter(); // Mocked
    }

    // --- ウィンドウ操作 ---

    /** パネル全体を返す */
    private UiDiskPanel GetPanel() {
        return panel;
    }

    /** メニューの作成 */
    private void MakeMenu() {
        menuFile = new MyMenu();
        menuData = new MyMenu();
        menuMode = new MyMenu();
        menuView = new MyMenu();
        menuHelp = new MyMenu();
        MyMenu sm;

        menuFile.Append(IDM_NEW_FILE, "&New...\tCTRL+N");
        menuFile.Append(IDM_OPEN_FILE, "&Open...\tCTRL+O");
        menuFile.appendSeparator();
        menuFile.Append(IDM_CLOSE_FILE, "&Close");
        menuFile.Append(IDM_SAVEAS_FILE, "Save &As...\tCTRL+S");
        menuFile.Append(IDM_SAVE_DISK, "Save A Disk...");
        menuFile.appendSeparator();
        sm = new MyMenu();
        sm.Append(IDM_ADD_DISK_NEW, "&New Disk...");
        sm.Append(IDM_ADD_DISK_FROM_FILE, "From &File...");
        menuFile.AppendSubMenu(sm, "Add D&isk");
        menuFile.AppendSeparator();
        menuFile.Append(IDM_REPLACE_DISK_FROM_FILE, "R&eplace Disk Data...");
        menuFile.AppendSeparator();
        menuFile.Append(IDM_DELETE_DISK_FROM_FILE, "&Delete Disk...");
        menuFile.Append(IDM_RENAME_DISK, "&Rename Disk");
        menuFile.AppendSeparator();
        menuFile.Append(IDM_INITIALIZE_DISK, "I&nitialize...");
        menuFile.Append(IDM_FORMAT_DISK, "&Format For BASIC...");
        menuFile.AppendSeparator();
        menuRecentFiles = new MyMenu();
        UpdateMenuRecentFiles();
        menuFile.AppendSubMenu(menuRecentFiles, "&Reccent Files");
        menuFile.AppendSeparator();
        menuFile.Append(wxID_EXIT, "E&xit\tALT+F4");

        menuData.Append(IDM_EXPORT_DATA, "&Export...");
        menuData.Append(IDM_IMPORT_DATA, "&Import...");
        menuData.AppendSeparator();
        menuData.Append(IDM_DELETE_DATA, "&Delete...");
        menuData.Append(IDM_RENAME_DATA_ON_DISK, "Rena&me File");
        menuData.AppendSeparator();
        menuData.Append(IDM_COPY_DATA, "&Copy");
        menuData.Append(IDM_PASTE_DATA, "&Paste...");
        menuData.AppendSeparator();
        menuData.Append(Global.IDM_EDIT_FILE_BINARY, "Edit using binary editor...");
        menuData.Append(Global.IDM_EDIT_FILE_TEXT, "Edit using text editor...");
        menuData.AppendSeparator();
        menuData.Append(Global.IDM_MAKE_DIRECTORY_ON_DISK, "Make Directory(&F...");
        menuData.AppendSeparator();
        menuData.Append(Global.IDM_PROPERTY_DATA, "&Property");

        menuMode.AppendRadioItem(Global.IDM_BASIC_MODE, "BASIC Mode");
        menuMode.AppendRadioItem(Global.IDM_RAWDISK_MODE, "Raw Disk Mode");
        menuMode.AppendSeparator();
        sm = new MyMenu();
        CharCodeChoice choice = Global.gCharCodeChoices.Find("main");
        if (choice != null) {
            for (int i = 0; i < choice.Count(); i++) {
                CharCodeMap map = choice.Item(i);
                sm.AppendRadioItem(Global.IDM_CHAR_0 + i, map.GetDescription());
            }
        }
        menuMode.AppendSubMenu(sm, "&Charactor Code");
        menuMode.AppendSeparator();
        sm = new MyMenu();
        sm.AppendCheckItem(Global.IDM_TRIM_DATA, "Trim unused data when save the disk image.");
        sm.AppendCheckItem(Global.IDM_SHOW_DELFILE, "Show deleted and hidden files on the file list.");
        menuMode.AppendSubMenu(sm, "Quick &Settings");
        menuMode.AppendSeparator();
        menuMode.Append(Global.IDM_CONFIGURE, "C&onfigure...");

        menuView.AppendCheckItem(Global.IDM_WINDOW_BINDUMP, "&Dump Window");
        menuView.AppendCheckItem(Global.IDM_WINDOW_FATAREA, "&Availability Window");
        menuView.AppendSeparator();
        menuView.Append(Global.IDM_FILELIST_COLUMN, "Columns of File &List...");
        menuView.AppendSeparator();
        menuView.Append(Global.IDM_CHANGE_FONT, "&Font...");
        // if (WXOSX) menuView.AppendSeparator(); // Mocked

        menuHelp.Append(Global.wxID_ABOUT, "&About...");

        MyMenuBar menuBar = new MyMenuBar();
        menuBar.Append(menuFile, "&File");
        menuBar.Append(menuData, "&Data");
        menuBar.Append(menuMode, "&Mode");
        menuBar.Append(menuView, "&View");
        // if (WXOSX) menuBar.Append(new JMenu(), WX._("&Window")); // Mocked
        menuBar.Append(menuHelp, "&Help");

        // SetMenuBar(menuBar); // Mocked
    }

    /** ツールバーの再生成 */
    private void RecreateToolbar() {
        JToolBar toolBar = GetToolBar();
        int style = toolBar != null ? toolBar.GetWindowStyle() : TOOLBAR_STYLE;
        // delete toolBar;
        SetToolBar(null);

        // style &= ~(...); style |= ... Mocked style manipulation
        style = 0;

        toolBar = CreateToolBar(style, IDT_TOOLBAR);

        PopulateToolbar(toolBar);
    }

    /** ツールバーの構築 */
    private void PopulateToolbar(JToolBar toolBar) {
        final int fd_5inch_16_new = 0;
        final int fd_5inch_16_open = 1;
        final int fd_5inch_16_1 = 2;
        final int fd_5inch_16_add = 3;
        final int fd_5inch_16_delete = 4;
        final int fd_5inch_16_export = 5;
        final int fd_5inch_16_import = 6;
        final int fileicon_delete = 7;
        final int Tool_Max = 8;

        Object[] toolBarBitmaps = new Object[Tool_Max];

        for (int i = 0; i < Tool_Max; i++) toolBarBitmaps[i] = new Object(); // INIT_TOOL_BMP mock

        Dimension toolSize = new Dimension(); // Mocked size

        toolBar.SetToolBitmapSize(toolSize);

        final int wxITEM_NORMAL = 0;
        final int wxITEM_DROPDOWN = 1;
        Object wxNullBitmap = null;

        toolBar.AddTool(Global.IDM_NEW_FILE, "New",
                toolBarBitmaps[fd_5inch_16_new], wxNullBitmap, wxITEM_NORMAL,
                "New disk image");
        toolBar.add(Global.IDM_OPEN_FILE, "Open",
                toolBarBitmaps[fd_5inch_16_open], wxNullBitmap, wxITEM_NORMAL,
                "Open disk image");
        toolBar.add(Global.IDM_SAVEAS_FILE, "Save As",
                toolBarBitmaps[fd_5inch_16_1], wxNullBitmap, wxITEM_NORMAL,
                "Save disk image");
        toolBar.add(Global.IDM_ADD_DISK, "Add",
                toolBarBitmaps[fd_5inch_16_add], wxNullBitmap, wxITEM_DROPDOWN,
                "Add a disk on disk image");
        MyMenu sm = new MyMenu();
        sm.Append(Global.IDM_ADD_DISK_NEW, "&New Disk...");
        sm.Append(Global.IDM_ADD_DISK_FROM_FILE, "From &File...");
        toolBar.SetDropdownMenu(Global.IDM_ADD_DISK, sm);
        // toolBar->AddTool(IDM_DELETE_DISK_FROM_FILE, ... ignored
        toolBar.addSeparator();
        toolBar.add(Global.IDM_EXPORT_DATA, "Export",
                toolBarBitmaps[fd_5inch_16_export], wxNullBitmap, wxITEM_NORMAL,
                "Export a file from the disk");
        toolBar.add(Global.IDM_IMPORT_DATA, "Import",
                toolBarBitmaps[fd_5inch_16_import], wxNullBitmap, wxITEM_NORMAL,
                "Import a file to the disk");
        // toolBar->AddTool(IDM_DELETE_DISK, ... ignored

        toolBar.Realize();
        // toolBar.SetRows(...); // Mocked
    }

    /** ステータスバーの再生成 */
    private void RecreateStatusbar() {
        // delete statBar;
        SetStatusBar(null);

        int style = 0; // Mocked wxSTB_DEFAULT_STYLE

        JPanel statBar = new JPanel(this, 0, style);
        statBar.SetFieldsCount(2);

        // SetStatusBar(statBar); // Mocked

        PositionStatusBar(); // Mocked
    }

    // #ifdef USE_MENU_OPEN // void OnMenuOpen(JMenuEvent& event) { ... } #endif

    /// ドロップされたファイルを開く (Not in .h, but useful context)
    public void OpenDroppedFile(String path) {
        if (!CloseDataFile()) return;
        PreOpenDataFile(path);
    }

    // --- ウィンドウ操作 (続き) ---

    /** メニューのファイル項目を更新 */
    public void UpdateMenuFile() {
        boolean opened = (p_image.GetFile() != null);
        menuFile.enable(Global.IDM_CLOSE_FILE, opened);
        menuFile.enable(Global.IDM_ADD_DISK_NEW, opened);
        menuFile.enable(Global.IDM_ADD_DISK_FROM_FILE, opened);

        opened = (opened && p_image.CountDisks() > 0);
        menuFile.enable(Global.IDM_SAVEAS_FILE, opened);

        UiDiskList list = GetDiskListPanel();
        if (list != null) {
            UpdateMenuDiskList(list);
        }
    }

    /** メニューのディスク項目を更新 */
    public void UpdateMenuDisk() {
        UiDiskFileList list = GetFileListPanel();
        if (list != null) {
            UpdateMenuFileList(list);
            return;
        }

        UiDiskRawPanel rawpl = GetDiskRawPanel();
        if (rawpl != null) {
            UpdateMenuRawDisk(rawpl);
            return;
        }
    }

    /** メニューのディスク項目を更新 */
    public void UpdateMenuDiskList(UiDiskList list) {
        boolean opened = (list != null && list.IsSelectedDiskImage());
        menuFile.enable(Global.IDM_REPLACE_DISK_FROM_FILE, opened);
        menuFile.enable(Global.IDM_SAVE_DISK, opened);
        menuFile.enable(Global.IDM_DELETE_DISK_FROM_FILE, opened);
        menuFile.enable(Global.IDM_RENAME_DISK, opened);
        menuFile.enable(Global.IDM_INITIALIZE_DISK, opened);
        menuFile.enable(Global.IDM_FORMAT_DISK, opened && IsFormattableDisk());
    }

    /** メニューのファイル項目を更新 */
    public void UpdateMenuFileList(UiDiskFileList list) {
        UiDiskList lpanel = GetLPanel();
        menuData.enable(Global.IDM_PROPERTY_DATA, (lpanel.IsSelectedDiskImage()));
        boolean opened = (list != null && list.CanUseBasicDisk());
        menuFile.enable(Global.IDM_FORMAT_DISK, opened);
        menuData.enable(Global.IDM_MAKE_DIRECTORY_ON_DISK, opened && CanMakeDirectory(list.GetDiskBasic()));
        opened = (opened && list.IsAssignedBasicDisk());
        menuData.enable(Global.IDM_IMPORT_DATA, opened);
        menuData.enable(Global.IDM_PASTE_DATA, opened);
        int cnt = list.GetListSelectedItemCount();
        opened = (opened && cnt > 0);
        menuData.enable(Global.IDM_EXPORT_DATA, opened);
        menuData.enable(Global.IDM_DELETE_DATA, opened);
        menuData.enable(Global.IDM_COPY_DATA, opened);
        opened = (opened && cnt == 1);
        menuData.enable(Global.IDM_RENAME_DATA_ON_DISK, opened);
        menuData.enable(Global.IDM_EDIT_FILE_BINARY, opened);
        menuData.enable(Global.IDM_EDIT_FILE_TEXT, opened);
    }

    /** メニューの生ディスク項目を更新 */
    public void UpdateMenuRawDisk(UiDiskRawPanel rawpanel) {
        // Implementation logic needed here, stubbing as it was not in .cpp snippet
    }

    /** メニューのモード項目を更新 */
    public void UpdateMenuMode() {
        int sel = Global.gCharCodeChoices.IndexOf("main", Global.gConfig.GetCharCode());
        MenuItem mitem = menuMode.FindItem(Global.IDM_CHAR_0 + sel);
        if (mitem != null) mitem.Check(true);

        menuMode.Check(Global.IDM_TRIM_DATA, Global.gConfig.IsTrimUnusedData());
        menuMode.Check(Global.IDM_SHOW_DELFILE, Global.gConfig.IsShownDeletedFile());
    }

    /** 最近使用したファイル一覧を更新 */
    public void UpdateMenuRecentFiles() {
        String[] names = Global.gConfig.GetRecentFiles();
        for (int i = 0; i < Global.MAX_RECENT_FILES && i < names.length; i++) {
            // if (menuRecentFiles.FindItem(Global.IDM_RECENT_FILE_0 + i) != null) menuRecentFiles.Delete(Global.IDM_RECENT_FILE_0 + i);
            menuRecentFiles.Append(Global.IDM_RECENT_FILE_0 + i, names[i]);
        }
    }

    /** ツールバーを更新 */
    public void UpdateToolBar() {
        JToolBar toolBar = GetToolBar();
        if (toolBar == null) return;
        boolean opened = (p_image.GetFile() != null);

        toolBar.EnableTool(Global.IDM_ADD_DISK, opened);
        toolBar.EnableTool(Global.IDM_ADD_DISK_NEW, opened);
        toolBar.EnableTool(Global.IDM_ADD_DISK_FROM_FILE, opened);
        opened = (opened && p_image.CountDisks() > 0);
        toolBar.EnableTool(Global.IDM_SAVEAS_FILE, opened);

        UiDiskList dlist = GetDiskListPanel();
        if (dlist != null) {
            UpdateToolBarDiskList(dlist);
        }
        UiDiskFileList flist = GetFileListPanel();
        if (flist != null) {
            UpdateToolBarFileList(flist);
            return;
        }
        UiDiskRawPanel rawpl = GetDiskRawPanel();
        if (rawpl != null) {
            UpdateToolBarRawDisk(rawpl);
            return;
        }
    }

    /** ツールバーのディスクリスト項目を更新 */
    public void UpdateToolBarDiskList(UiDiskList list) {
        JToolBar toolBar = GetToolBar();
        if (toolBar == null) return;
        boolean opened = (list != null && list.IsSelectedDiskImage());
        toolBar.EnableTool(Global.IDM_SAVE_DISK, opened);
        toolBar.EnableTool(Global.IDM_DELETE_DISK_FROM_FILE, opened);
    }

    /** メニューとツールバーのディスクリスト項目を更新 */
    public void UpdateMenuAndToolBarDiskList(UiDiskList list) {
        UpdateMenuDiskList(list);
        UpdateToolBarDiskList(list);
    }

    /** ツールバーのファイルリスト項目を更新 */
    public void UpdateToolBarFileList(UiDiskFileList list) {
        JToolBar toolBar = GetToolBar();
        if (toolBar == null) return;
        boolean opened = (list != null && list.IsFormattedBasicDisk());
        toolBar.EnableTool(Global.IDM_IMPORT_DATA, opened);
        int cnt = list.GetListSelectedItemCount();
        opened = (opened && cnt > 0);
        toolBar.EnableTool(Global.IDM_EXPORT_DATA, opened);
        toolBar.EnableTool(Global.IDM_DELETE_DATA, opened);
    }

    /** ツールバーの生ディスク項目を更新 */
    public void UpdateToolBarRawDisk(UiDiskRawPanel rawpanel) {
        // Implementation logic needed here, stubbing as it was not in .cpp snippet
    }

    /** メニューとツールバーのファイルリスト項目を更新 */
    public void UpdateMenuAndToolBarFileList(UiDiskFileList list) {
        UpdateMenuFileList(list);
        UpdateToolBarFileList(list);
    }

    /** メニューとツールバーの生ディスク項目を更新 */
    public void UpdateMenuAndToolBarRawDisk(UiDiskRawPanel rawpanel) {
        UpdateMenuRawDisk(rawpanel);
        UpdateToolBarRawDisk(rawpanel);
    }

    /** ウィンドウ上のデータを更新 */
    public void UpdateDataOnWindow(boolean keep) {
        // Stub
    }

    /** ウィンドウ上のデータを更新 タイトルバーにファイルパスを表示 */
    public void UpdateDataOnWindow(String path, boolean keep) {
        // Stub
    }

    /** 保存後のウィンドウ上のデータを更新 */
    public void UpdateSavedDataOnWindow(String path) {
        // Stub
    }

    /** ウィンドウ上のファイルパスを更新 */
    public void UpdateFilePathOnWindow(String path) {
        // Stub
    }

    /** キャラクターコード選択 */
    public void ChangeCharCode(String name) {
        // Stub
    }

    /** キャラクターコードを返す */
    public final String GetCharCode() {
        return ""; // Stub
    }

    /** キャラクターコード設定 */
    public void SetDefaultCharCode() {
        // Stub
    }

    /** フォント変更ダイアログ */
    public void ShowListFontDialog() {
        // Stub
    }

    /** リストウィンドウのフォント変更 */
    public void SetListFont(Font font) {
        // Stub
    }

    /** リストウィンドウのデフォルトフォントを得る */
    public void GetDefaultListFont(Font font) {
        // Stub
    }

    /** ファイルリストの列を変更 */
    public void ChangeColumnsOfFileList() {
        // Stub
    }

    // --- カウンター操作ヘルパー (mocked/internal) ---

    // These methods were not explicitly defined in the C++ but are used in the counter logic.
    private int StartStatusCounter(int count, String message) {
        return stat_counters.Start(count, message);
    }

    private void AppendStatusCounter(int id, int count) {
        stat_counters.Append(id, count);
    }

    private void IncreaseStatusCounter(int id) {
        stat_counters.Increase(id);
    }

    private void FinishStatusCounter(int id, String message) {
        stat_counters.Finish(id, message, null);
    } // Mocked EvtHandler is null

    /** エクスポート用カウンタを開始 */
    @Override
    public void StartExportCounter(int count, String message) {
        m_sw_export.Start();
        m_sw_export.setID(StartStatusCounter(count, message));
    }

    /** エクスポート用カウンタの母数を追加 */
    @Override
    public void AppendExportCounter(int count) {
        AppendStatusCounter(m_sw_export.getID(), count);
    }

    /** エクスポート用カウンタの数を＋１ */
    @Override
    public void IncreaseExportCounter() {
        IncreaseStatusCounter(m_sw_export.getID());
    }

    /** エクスポート用カウンタのアイコンを時計にする */
    @Override
    public void BeginBusyCursorExportCounterIfNeed() {
        if (m_sw_export.Time() > 3000) {
            m_sw_export.busy();
        }
    }

    /** エクスポート用カウンタを終了 */
    @Override
    public void FinishExportCounter(String message) {
        FinishStatusCounter(m_sw_export.getID(), message);
        m_sw_export.finish();
    }

    /** エクスポート用カウンタを再スタート */
    public void RestartExportCounter() {
        m_sw_export.restart();
    }

    /** インポート用カウンタを開始 */
    @Override
    public void StartImportCounter(int count, String message) {
        m_sw_import.Start();
        m_sw_import.setID(StartStatusCounter(count, message));
    }

    /** インポート用カウンタの母数を追加 */
    @Override
    public void AppendImportCounter(int count) {
        AppendStatusCounter(m_sw_import.getID(), count);
    }

    /** インポート用カウンタの数を＋１ */
    @Override
    public void IncreaseImportCounter() {
        IncreaseStatusCounter(m_sw_import.getID());
    }

    /** インポート用カウンタのアイコンを時計にする */
    @Override
    public void BeginBusyCursorImportCounterIfNeed() {
        if (m_sw_import.Time() > 3000) {
            m_sw_import.busy();
        }
    }

    /** インポート用カウンタを終了 */
    @Override
    public void FinishImportCounter(String message) {
        FinishStatusCounter(m_sw_import.getID(), message);
        m_sw_import.finish();
    }

    /** インポート用カウンタを再スタート */
    @Override
    public void RestartImportCounter() {
        m_sw_import.restart();
    }

    // --- その他のUI ---

    /** 指定ファイルを引数にして外部エディタを起動する */
    public static boolean OpenFileWithEditor(enEditorTypes editor_type, Path file) {
        // Logic mocked
        return false;
    }

    // --- property ---

    /** ディスク操作用のインスタンス */
    public DiskImage GetDiskImage() {
        return p_image;
    }

    // --- Mocked methods for structural completeness (not found in CPP/Header are external or internal) ---
    public boolean PreOpenDataFile(String path) {
        return false;
    }

    public boolean CloseDataFile() {
        return false;
    }

    public boolean CloseDataFile(boolean force) {
        return false;
    }

    public JToolBar GetToolBar() {
        return null;
    }

    public void SetToolBar(JToolBar tb) {
    }

    public JToolBar CreateToolBar(int style, int id) {
        return null;
    }

    public void SetStatusBar(JPanel sb) {
    }

    public void PositionStatusBar() {
    }

    public void Close(boolean force) {
    }

    public void ShowCreateFileDialog() {
    }

    public void ShowOpenFileDialog() {
    }

    public void ShowSaveFileDialog() {
    }

    @Override
    public UiDiskList GetDiskListPanel() {
        return null;
    }

    public void ShowAddNewDiskDialog() {
    }

    public void ShowAddFileDialog() {
    }

    public void DeleteDisk() {
    }

    public void RenameDisk() {
    }

    public void InitializeDisk() {
    }

    public void FormatDisk() {
    }

    public void ExportDataFromDisk() {
    }

    public void ImportDataToDisk() {
    }

    public void DeleteDataFromDisk() {
    }

    public void RenameDataOnDisk() {
    }

    public void CopyDataFromDisk() {
    }

    public void PasteDataToDisk() {
    }

    public void MakeDirectoryOnDisk() {
    }

    public void EditFileOnDisk(enEditorTypes type) {
    }

    public void PropertyOnDisk() {
    }

    public void ShowConfigureDialog() {
    }

    public void ChangeRPanel(int mode, Object diskBasic) {
    }

    public void SetFileListData() {
    }

    public void OpenBinDumpWindow() {
    }

    public void CloseBinDumpWindow() {
    }

    public void OpenFatAreaWindow() {
    }

    public void CloseFatAreaWindow() {
    }

    @Override
    public UiDiskFileList GetFileListPanel() {
        return null;
    }

    public UiDiskRawPanel GetDiskRawPanel() {
        return null;
    }

    public boolean IsFormattableDisk() {
        return false;
    }

    public UiDiskList GetLPanel() {
        return null;
    }

    @Override
    public boolean CanMakeDirectory(DiskBasic basic) {
        return false;
    }
}