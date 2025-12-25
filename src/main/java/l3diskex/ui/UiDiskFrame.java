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

    /** Initial processing of the frame part */
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

    /** When the window is closed */
    public void OnClose(WindowEvent event) {
        if (!CloseDataFile(!event.CanVeto())) {
            event.Veto();
            return;
        }
        event.Skip();
    }

    /** Menu: Quit selection */
    public void OnQuit(ActionEvent event) {
        Close(false); // Mocked
    }

    /// Menu: About dialog display selection
    public void OnAbout(ActionEvent event) {
        new UiDiskAbout(this, 0).setVisible(true);
    }

    /** Menu: Create new selection */
    public void OnCreateFile(ActionEvent event) {
        ShowCreateFileDialog(); // Mocked
    }

    /** Menu: Open selection */
    public void OnOpenFile(ActionEvent event) {
        ShowOpenFileDialog(); // Mocked
    }

    /** Menu: Open recently used file selection */
    public void OnOpenRecentFile(ActionEvent event) {
        MenuItem item = menuRecentFiles.FindItem(event.GetId());
        if (item == null) return;
        Path path = Path.of("dummy_path"); // item.GetItemLabel() mocked
        if (!CloseDataFile()) return;
        PreOpenDataFile(path.toAbsolutePath().toString());
    }

    /** Menu: Close selection */
    public void OnCloseFile(ActionEvent event) {
        CloseDataFile();
    }

    /** Menu: Save as selection */
    public void OnSaveAsFile(ActionEvent event) {
        ShowSaveFileDialog(); // Mocked
    }

    /// Menu: Save one disk selection
    public void OnSaveDisk(ActionEvent event) {
        UiDiskList list = GetDiskListPanel();
        if (list == null) return;
        list.showSaveDiskDialog();
    }

    /** Menu: Add new disk selection */
    public void OnAddNewDisk(ActionEvent event) {
        ShowAddNewDiskDialog(); // Mocked
    }

    /** Menu: Add disk from file selection */
    public void OnAddDiskFromFile(ActionEvent event) {
        ShowAddFileDialog(); // Mocked
    }

    /** Menu: Replace disk selection */
    public void OnReplaceDisk(ActionEvent event) {
        UiDiskList list = GetDiskListPanel();
        if (list == null) return;
        list.replaceDisk();
    }

    /** Menu: Delete disk from file selection */
    public void OnDeleteDiskFromFile(ActionEvent event) {
        DeleteDisk(); // Mocked
    }

    /** Menu: Rename disk selection */
    public void OnRenameDisk(ActionEvent event) {
        RenameDisk(); // Mocked
    }

    /** Menu: Initialize selection */
    public void OnInitializeDisk(ActionEvent event) {
        InitializeDisk(); // Mocked
    }

    /** Menu: Format selection */
    public void OnFormatDisk(ActionEvent event) {
        FormatDisk(); // Mocked
    }

    /** Menu: Export selection */
    public void OnExportDataFromDisk(ActionEvent event) {
        ExportDataFromDisk(); // Mocked
    }

    /** Menu: Import selection */
    public void OnImportDataToDisk(ActionEvent event) {
        ImportDataToDisk(); // Mocked
    }

    /** Menu: Delete selection */
    public void OnDeleteDataFromDisk(ActionEvent event) {
        DeleteDataFromDisk(); // Mocked
    }

    /** Menu: Rename selection */
    public void OnRenameDataOnDisk(ActionEvent event) {
        RenameDataOnDisk(); // Mocked
    }

    /** Menu: Copy selection */
    public void OnCopyDataFromDisk(ActionEvent event) {
        CopyDataFromDisk(); // Mocked
    }

    /** Menu: Paste selection */
    public void OnPasteDataToDisk(ActionEvent event) {
        PasteDataToDisk(); // Mocked
    }

    /** Menu: Create directory selection */
    public void OnMakeDirectoryOnDisk(ActionEvent event) {
        MakeDirectoryOnDisk(); // Mocked
    }

    /** Menu: Edit file selection */
    public void OnEditFileOnDisk(ActionEvent event) {
        EditFileOnDisk(event.getActionCommand() == Global.IDM_EDIT_FILE_BINARY ? enEditorTypes.EDITOR_TYPE_BINARY : enEditorTypes.EDITOR_TYPE_TEXT);
    }

    /** Menu: Property selection */
    public void OnPropertyOnDisk(ActionEvent event) {
        PropertyOnDisk(); // Mocked
    }

    /** File mode selection */
    public void OnBasicMode(ActionEvent event) {
        ChangeRPanel(0, null); // Mocked
    }

    /// Raw disk mode selection
    public void OnRawDiskMode(ActionEvent event) {
        ChangeRPanel(1, null); // Mocked
    }

    /** Character code selection */
    public void OnChangeCharCode(ActionEvent event) {
        int sel = event.GetId() - Global.IDM_CHAR_0;
        String name = Global.gCharCodeChoices.GetItemName("main", sel);
        ChangeCharCode(name); // Mocked
    }

    /** Whether to trim unused data */
    public void OnTrimData(ActionEvent event) {
        Global.gConfig.TrimUnusedData(event.IsChecked());
    }

    /** Whether to show deleted files */
    public void OnShowDeletedFile(ActionEvent event) {
        Global.gConfig.ShowDeletedFile(event.IsChecked());
        SetFileListData(); // Mocked
    }

    /** Dump window selection */
    public void OnOpenBinDump(ActionEvent event) {
        if (bindump_frame == null) {
            OpenBinDumpWindow(); // Mocked
        } else {
            CloseBinDumpWindow(); // Mocked
        }
    }

    /** Availability window selection */
    public void OnOpenFatArea(ActionEvent event) {
        if (fatarea_frame == null) {
            OpenFatAreaWindow(); // Mocked
        } else {
            CloseFatAreaWindow(); // Mocked
        }
    }

    /** Column selection for file list */
    public void OnChangeColumnsOfFileList(ActionEvent event) {
        ChangeColumnsOfFileList(); // Mocked
    }

    /** Change font selection */
    public void OnChangeFont(ActionEvent event) {
        ShowListFontDialog(); // Mocked
    }

    /** Menu: Settings dialog selection */
    public void OnConfigure(ActionEvent event) {
        ShowConfigureDialog(); // Mocked
    }

    /** Status counter finish timer */
    public void OnTimerStatusCounter() {
        ClearStatusCounter(); // Mocked
    }

    // --- Window operations ---

    /** Return the entire panel */
    private UiDiskPanel GetPanel() {
        return panel;
    }

    /** Create menu */
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

    /** Recreate toolbar */
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

    /** Construct toolbar */
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

    /** Recreate status bar */
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

    /// Open dropped file (Not in .h, but useful context)
    public void OpenDroppedFile(String path) {
        if (!CloseDataFile()) return;
        PreOpenDataFile(path);
    }

    // --- Window operations (continued) ---

    public void UpdateMenuFile() {
    /** Update file items in menu */
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

    public void UpdateMenuDisk() {
    /** Update disk items in menu */
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

    /** Update disk items in menu */
    public void UpdateMenuDiskList(UiDiskList list) {
        boolean opened = (list != null && list.IsSelectedDiskImage());
        menuFile.enable(Global.IDM_REPLACE_DISK_FROM_FILE, opened);
        menuFile.enable(Global.IDM_SAVE_DISK, opened);
        menuFile.enable(Global.IDM_DELETE_DISK_FROM_FILE, opened);
        menuFile.enable(Global.IDM_RENAME_DISK, opened);
        menuFile.enable(Global.IDM_INITIALIZE_DISK, opened);
        menuFile.enable(Global.IDM_FORMAT_DISK, opened && IsFormattableDisk());
    }

    /** Update file items in menu */
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

    /** Update raw disk items in menu */
    public void UpdateMenuRawDisk(UiDiskRawPanel rawpanel) {
        // Implementation logic needed here, stubbing as it was not in .cpp snippet
    }

    /** Update mode items in menu */
    public void UpdateMenuMode() {
        int sel = Global.gCharCodeChoices.IndexOf("main", Global.gConfig.GetCharCode());
        MenuItem mitem = menuMode.FindItem(Global.IDM_CHAR_0 + sel);
        if (mitem != null) mitem.Check(true);

        menuMode.Check(Global.IDM_TRIM_DATA, Global.gConfig.IsTrimUnusedData());
        menuMode.Check(Global.IDM_SHOW_DELFILE, Global.gConfig.IsShownDeletedFile());
    }

    /** Update recently used file list */
    public void UpdateMenuRecentFiles() {
        String[] names = Global.gConfig.GetRecentFiles();
        for (int i = 0; i < Global.MAX_RECENT_FILES && i < names.length; i++) {
            // if (menuRecentFiles.FindItem(Global.IDM_RECENT_FILE_0 + i) != null) menuRecentFiles.Delete(Global.IDM_RECENT_FILE_0 + i);
            menuRecentFiles.Append(Global.IDM_RECENT_FILE_0 + i, names[i]);
        }
    }

    /** Update toolbar */
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

    /** Update disk list items in toolbar */
    public void UpdateToolBarDiskList(UiDiskList list) {
        JToolBar toolBar = GetToolBar();
        if (toolBar == null) return;
        boolean opened = (list != null && list.IsSelectedDiskImage());
        toolBar.EnableTool(Global.IDM_SAVE_DISK, opened);
        toolBar.EnableTool(Global.IDM_DELETE_DISK_FROM_FILE, opened);
    }

    /** Update disk list items in menu and toolbar */
    public void UpdateMenuAndToolBarDiskList(UiDiskList list) {
        UpdateMenuDiskList(list);
        UpdateToolBarDiskList(list);
    }

    /** Update file list items in toolbar */
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

    /** Update raw disk items in toolbar */
    public void UpdateToolBarRawDisk(UiDiskRawPanel rawpanel) {
        // Implementation logic needed here, stubbing as it was not in .cpp snippet
    }

    /** Update file list items in menu and toolbar */
    public void UpdateMenuAndToolBarFileList(UiDiskFileList list) {
        UpdateMenuFileList(list);
        UpdateToolBarFileList(list);
    }

    /** Update raw disk items in menu and toolbar */
    public void UpdateMenuAndToolBarRawDisk(UiDiskRawPanel rawpanel) {
        UpdateMenuRawDisk(rawpanel);
        UpdateToolBarRawDisk(rawpanel);
    }

    /** Update data on window */
    public void UpdateDataOnWindow(boolean keep) {
        // Stub
    }

    /** Update data on window. Display file path in title bar */
    public void UpdateDataOnWindow(String path, boolean keep) {
        // Stub
    }

    /** Update data on window after saving */
    public void UpdateSavedDataOnWindow(String path) {
        // Stub
    }

    /** Update file path on window */
    public void UpdateFilePathOnWindow(String path) {
        // Stub
    }

    /** Character code selection */
    public void ChangeCharCode(String name) {
        // Stub
    }

    /** Return character code */
    public final String GetCharCode() {
        return ""; // Stub
    }

    /** Character code setting */
    public void SetDefaultCharCode() {
        // Stub
    }

    /** Font change dialog */
    public void ShowListFontDialog() {
        // Stub
    }

    /** Font change for list window */
    public void SetListFont(Font font) {
        // Stub
    }

    /** Get default font for list window */
    public void GetDefaultListFont(Font font) {
        // Stub
    }

    /** Change columns of file list */
    public void ChangeColumnsOfFileList() {
        // Stub
    }

    // --- Counter operation helpers (mocked/internal) ---

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

    /** Start counter for export */
    @Override
    public void StartExportCounter(int count, String message) {
        m_sw_export.Start();
        m_sw_export.setID(StartStatusCounter(count, message));
    }

    /** Append denominator for export counter */
    @Override
    public void AppendExportCounter(int count) {
        AppendStatusCounter(m_sw_export.getID(), count);
    }

    /** Increment export counter by 1 */
    @Override
    public void IncreaseExportCounter() {
        IncreaseStatusCounter(m_sw_export.getID());
    }

    /** Set export counter icon to clock */
    @Override
    public void BeginBusyCursorExportCounterIfNeed() {
        if (m_sw_export.Time() > 3000) {
            m_sw_export.busy();
        }
    }

    /** Finish export counter */
    @Override
    public void FinishExportCounter(String message) {
        FinishStatusCounter(m_sw_export.getID(), message);
        m_sw_export.finish();
    }

    /** Restart export counter */
    public void RestartExportCounter() {
        m_sw_export.restart();
    }

    /** Start counter for import */
    @Override
    public void StartImportCounter(int count, String message) {
        m_sw_import.Start();
        m_sw_import.setID(StartStatusCounter(count, message));
    }

    /** Append denominator for import counter */
    @Override
    public void AppendImportCounter(int count) {
        AppendStatusCounter(m_sw_import.getID(), count);
    }

    /** Increment import counter by 1 */
    @Override
    public void IncreaseImportCounter() {
        IncreaseStatusCounter(m_sw_import.getID());
    }

    /** Set import counter icon to clock */
    @Override
    public void BeginBusyCursorImportCounterIfNeed() {
        if (m_sw_import.Time() > 3000) {
            m_sw_import.busy();
        }
    }

    /** Finish import counter */
    @Override
    public void FinishImportCounter(String message) {
        FinishStatusCounter(m_sw_import.getID(), message);
        m_sw_import.finish();
    }

    /** Restart import counter */
    @Override
    public void RestartImportCounter() {
        m_sw_import.restart();
    }

    // --- Other UI ---

    /** Start external editor with the specified file as an argument */
    public static boolean OpenFileWithEditor(enEditorTypes editor_type, Path file) {
        // Logic mocked
        return false;
    }

    // --- property ---

    /** Instance for disk operations */
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