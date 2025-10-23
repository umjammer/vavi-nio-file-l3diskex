package l3diskex.ui;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.time.LocalDateTime;
import java.util.Comparator;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JWindow;
import javax.swing.ListModel;

import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicFmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.DiskBasicDirItem.DiskBasicDirItemAttr;
import l3diskex.basicfmt.DiskBasicDirItem.DiskBasicDirItems;
import l3diskex.basicfmt.DiskBasicDirItemMZ.Globals;


public class UiDiskFileList {

    // Defines for column indices
    public static final int LISTCOL_NAME = 0;
    public static final int LISTCOL_ATTR = 1;
    public static final int LISTCOL_SIZE = 2;
    public static final int LISTCOL_GROUPS = 3;
    public static final int LISTCOL_START = 4;
    public static final int LISTCOL_TRACK = 5;
    public static final int LISTCOL_SIDE = 6;
    public static final int LISTCOL_SECTOR = 7;
    public static final int LISTCOL_DIVISION = 8;
    public static final int LISTCOL_DATE = 9;
    public static final int LISTCOL_STADDR = 10;
    public static final int LISTCOL_EDADDR = 11;
    public static final int LISTCOL_EXADDR = 12;
    public static final int LISTCOL_NUM = 13;
    public static final int LISTCOL_END = 14;

    public static final int ICON_FOR_LIST_NONE = -1;
    public static final int ICON_FOR_LIST_FILE_NORMAL = 0;
    public static final int ICON_FOR_LIST_FOLDER = 1;
    public static final int ICON_FOR_LIST_LABEL = 2;
    public static final int ICON_FOR_LIST_FILE_DELETE = 3;
    public static final int ICON_FOR_LIST_FILE_HIDDEN = 4;

    public static final int IDC_VIEW_LIST = 10001; // placeholder ID
    public static final int IDC_TEXT_ATTR = 10002;
    public static final int IDC_BTN_CHANGE = 10003;
    public static final int IDC_COMBO_CHAR_CODE = 10004;
    public static final int IDM_EXPORT_FILE = 20001;
    public static final int IDM_IMPORT_FILE = 20002;
    public static final int IDM_DELETE_FILE = 20003;
    public static final int IDM_RENAME_FILE = 20004;
    public static final int IDM_COPY_FILE = 20005;
    public static final int IDM_PASTE_FILE = 20006;
    public static final int IDM_EDIT_FILE_BINARY = 20007;
    public static final int IDM_EDIT_FILE_TEXT = 20008;
    public static final int IDM_MAKE_DIRECTORY = 20009;
    public static final int IDM_PROPERTY = 20010;
    public static final int IDM_COLUMN_0 = 30000;
    public static final int wxNOT_FOUND = -1;
    public static final int TEXT_ATTR_SIZE = 440;
    public static final int EDITOR_TYPE_BINARY = 0;
    public static final int EDITOR_TYPE_TEXT = 1;

    // Type aliases/placeholders (assuming USE_LIST_CTRL_ON_FILE_LIST = true for simplicity in UiDiskFileListCtrl)
    static class MyFileListItem {

        int value;

        MyFileListItem() {
            this.value = wxNOT_FOUND;
        }

        public boolean IsOk() {
            return value != wxNOT_FOUND;
        }
    }

    private final JComponent parent;
    private final UiDiskFrame frame;

    private final JTextArea textAttr;
    private final JButton btnChange;
    private final JLabel lblCharCode;
    private final JComboBox comCharCode;
    private UiDiskFileListCtrl listCtrl;
    private final JPanel szrButtons;
    private MyMenu menuPopup;
    private final JMenu menuColumnPopup;

    private final DiskBasic m_current_basic;
    private boolean m_initialized;
    private final boolean m_disk_selecting;
    private final MyFileListItem m_dragging_item;

    public UiDiskFileList(UiDiskFrame parentframe, JComponent parentwindow) {
        // Constructor logic translation
        // Simulating wxPanel inheritance and initialization
        this.m_initialized = false;
        this.parent = parentwindow;
        this.frame = parentframe;
        this.m_current_basic = null;
        this.listCtrl = null;
        this.m_disk_selecting = false;

        this.m_dragging_item = new MyFileListItem();

        BoxLayout box = new BoxLayout();
        this.szrButtons = new BoxLayout();
        // DimensionrFlags flags = new DimensionrFlags().Expand().Border(wxALL, 2); // Simplified

        Dimension size = new Dimension(TEXT_ATTR_SIZE, -1);
        this.textAttr = new wxTextCtrl(this, IDC_TEXT_ATTR, "", new Dimension(), size, 0); // wxTE_READONLY | wxTE_LEFT simplified
        // hbox->Add(textAttr, DimensionrFlags().Expand().Border(wxBOTTOM | wxTOP, 2)); // Simplified

        size.x = 60;
        this.btnChange = new JButton(this, IDC_BTN_CHANGE, "Change", new Point(), size);
        this.btnChange.setEnabled(false);
        // szrButtons->Add(btnChange, flags); // Simplified

        this.lblCharCode = new wxStaticText(this, 0, "Charactor Code");
        // szrButtons->Add(lblCharCode, DimensionrFlags().Center().Border(wxBOTTOM | wxTOP, 2).Border(wxLEFT | wxRIGHT, 8)); // Simplified
        this.comCharCode = new JComboBox(this, IDC_COMBO_CHAR_CODE, new Point(), new Dimension());
        CharCodeChoice choice = Globals.gCharCodeChoices.Find("main");
        if (choice != null) {
            for (int i = 0; i < choice.Count(); i++) {
                CharCodeMap map = choice.Item(i);
                this.comCharCode.Append(map.GetDescription());
            }
        }
        // szrButtons->Add(comCharCode, flags); // Simplified

        // hbox->Add(szrButtons); // Simplified
        // vbox->Add(hbox); // Simplified

        wxFont font = new wxFont();
        this.frame.GetDefaultListFont(font);

        this.listCtrl = new UiDiskFileListCtrl(parentframe, this, IDC_VIEW_LIST);
        // textAttr.SetFont(font); // Simplified
        // listCtrl.SetFont(font); // Simplified
        // vbox->Add(listCtrl, DimensionrFlags().Expand().Border(wxALL, 1)); // Simplified

        // SetSizerAndFit(vbox); // Simplified
        // Layout(); // Simplified

        // popup menu
        MakePopupMenu();

        // popup on list column header
        this.menuColumnPopup = null;

        // key
        this.listCtrl.Bind(null, null, this); // Simplified Bind

        this.m_initialized = true;
    }

    public void OnSize(ListEvent event) {
        if (!m_initialized) {
            // event.Skip();
            return;
        }
        // if (event.GetEventObject() != this) {
        //     event.Skip();
        //     return;
        // }

        Dimension size = event.GetSize();
        if (size.x < 32) return;

        Dimension sizz = szrButtons.GetSize();
        if (sizz.x == 0) return;

        Point listpt = new Point(); // listCtrl.GetPosition()
        // listCtrl.SetSize(size.x - listpt.x, size.y - listpt.y);

        int pos_x = size.x - sizz.x;
        if (pos_x < 0) return;

        Point bp = btnChange.GetPosition();
        pos_x -= bp.x;

        Dimension tz = textAttr.GetSize();
        tz.x += pos_x;
        if (tz.x < TEXT_ATTR_SIZE) return;

        // textAttr.SetSize(tz);

        bp.x += pos_x;

        // Simplified Sizer logic
        // DimensionrItemList *slist = &szrButtons->GetChildren();
        // for(DimensionrItemList::iterator it = slist->begin(); it != slist->end(); it++) {
        //     DimensionrItem *item = *it;
        //     if (item->IsWindow()) {
        //         JWindow *win = item->GetWindow();
        //         bp = win->GetPosition();
        //         bp.x += pos_x;
        //         win->SetPosition(bp);
        //     }
        // }
    }

    public void OnSelect(ListEvent event) {
        if (!m_initialized || m_current_basic == null) return;
        int selected_item = event.GetIndex();
        if (selected_item == wxNOT_FOUND) return;
        SelectItem(new MyFileListItem(), listCtrl.GetListSelectedItemCount()); // Simplified
    }

    public void OnDeselect(ListEvent event) {
        if (!m_initialized || m_current_basic == null) return;
        int deselected_item = event.GetIndex();
        if (deselected_item == wxNOT_FOUND) return;
        UnselectItem(new MyFileListItem(), listCtrl.GetListSelectedItemCount()); // Simplified
    }

    public void OnSelectionChanged(ListEvent event) {
        if (!m_initialized || m_current_basic == null) return;
        int count = listCtrl.GetListSelectedItemCount();
        for (int row = 0; row < listCtrl.GetItemCount(); row++) {
            boolean sel = listCtrl.IsRowSelected(row);
            int tog = listCtrl.GetListSelected(row);
            MyFileListItem item = listCtrl.RowToItem(row);
            if (sel && (tog == 0)) {
                SelectItem(item, count);
            } else if (!sel && (tog != 0)) {
                UnselectItem(item, count);
            }
            listCtrl.SetListSelected(row, sel ? 1 : 0);
        }
    }

    public void OnFileNameStartEditing(ListEvent event) {
        if (m_current_basic == null) return;
        MyFileListItem listitem = GetEventItem(event);
        int pos = (int) listCtrl.GetListItemData(listitem);
        DiskBasicDirItem ditem = m_current_basic.GetDirItem(pos);
        if (ditem == null || !ditem.IsFileNameEditable()) {
            event.Veto();
        }
    }

    public void OnFileNameEditingStarted(ListEvent event) {
        // For USE_LIST_CTRL_ON_FILE_LIST, this method might be empty or different.
        // Translating the DataView path (#ifndef) just in case, but simplifying internals.
        // if (m_current_basic == null) return;
        // wxDataViewColumn column = event.GetDataViewColumn();
        // wxDataViewRenderer renderer = column.GetRenderer();
        // if (renderer == null) return;
        // wxTextCtrl text = renderer.GetEditorCtrl();
        // if (text == null) return;
        // wxDataViewItem listitem = event.GetItem();
        // int pos = (int)listCtrl.GetListItemData(listitem);
        // DiskBasicDirItem ditem = m_current_basic.GetDirItem(pos);
        // if (ditem != null && ditem.IsFileNameEditable()) {
        //     int max_len = ditem.GetFileNameStrSize();
        //     IntNameValidator validate = new IntNameValidator(ditem, "file name", m_current_basic.GetValidFileName());
        //     text.SetMaxLength(max_len);
        //     text.SetValidator(validate);
        // }
    }

    public void OnFileNameEditedDone(ListEvent event) {
        if (event.IsEditCancelled()) return;
        // Assuming #else path (ListCtrl)
        int listitem = event.GetIndex();
        String newname = event.GetLabel();
        RenameDataFile(listitem, newname);
        event.Veto();
    }

    public void OnListContextMenu(ListEvent event) {
        ShowPopupMenu();
    }

    public void OnListColumnContextMenu(ListEvent event) {
        ShowColumnPopupMenu();
    }

    public void OnColumnClick(ListEvent event) {
        int col = event.GetColumn();
        listCtrl.SortDataItems(m_current_basic, col);
    }

    public void OnContextMenu(ListEvent event) {
        ShowPopupMenu();
    }

    public void OnListActivated(ListEvent event) {
        DoubleClicked();
    }

    public void OnBeginDrag(ListEvent event) {
        DragDataSource();
    }

    public void OnExportFile(ListEvent event) {
        ShowExportDataFileDialog();
    }

    public void OnImportFile(ListEvent event) {
        ShowImportDataFileDialog();
    }

    public void OnDeleteFile(ListEvent event) {
        DeleteDataFile();
    }

    public void OnRenameFile(ListEvent event) {
        StartEditingFileName();
    }

    public void OnCopyFile(ListEvent event) {
        CopyToClipboard();
    }

    public void OnPasteFile(ListEvent event) {
        PasteFromClipboard();
    }

    public void OnMakeDirectory(ListEvent event) {
        ShowMakeDirectoryDialog();
    }

    public void OnEditFile(ListEvent event) {
        // EditDataFile(event.GetId() == IDM_EDIT_FILE_BINARY ? EDITOR_TYPE_BINARY : EDITOR_TYPE_TEXT);
        EditDataFile(IDM_EDIT_FILE_BINARY); // Simplified call
    }

    public void OnProperty(ListEvent event) {
        ShowFileAttr();
    }

    public void OnChar(ListEvent event) {
        // Simplified key code handling
        int kc = 0; // event.GetKeyCode()
        switch (kc) {
            // case WXK_RETURN:
            case 0: // Placeholder for Enter
                DoubleClicked();
                break;
            // case WXK_DELETE:
            // case WXK_BACK:
            case 1: // Placeholder for Delete/Back
                DeleteDataFile();
                break;
            // case WXK_CONTROL_C:
            case 2: // Placeholder for Ctrl+C
                CopyToClipboard();
                break;
            // case WXK_CONTROL_V:
            case 3: // Placeholder for Ctrl+V
                PasteFromClipboard();
                break;
            // case WXK_CONTROL_A:
            case 4: // Placeholder for Ctrl+A
                SelectAll();
                break;
            default:
                // event.Skip();
                break;
        }
    }

    public void OnButtonChange(ListEvent event) {
        ChangeBasicType();
    }

    public void OnChangeCharCode(ListEvent event) {
        ChangeCharCode();
    }

    private DiskBasicDirItem GetSelectedDirItem() {
        return null;
    }

    private DiskBasicDirItem GetDirItem(MyFileListItem view_item, int[] item_pos) {
        return null;
    }

    private DiskBasicDirItem GetFileName(MyFileListItem view_item, String[] name, int[] item_pos) {
        return null;
    }

    private void ShowPopupMenu() {
    }

    private void ShowColumnPopupMenu() {
    }

    private void DoubleClicked() {
    }

    private void ShowExportDataFileDialog() {
    }

    private void ShowImportDataFileDialog() {
    }

    private void ShowMakeDirectoryDialog() {
    }

    private void DragDataSource() {
    }

    private void CopyToClipboard() {
    }

    private void PasteFromClipboard() {
    }

    private void DeleteDataFile() {
    }

    private void StartEditingFileName() {
    }

    private void RenameDataFile(int listitem, String newname) {
    }

    private void SelectAll() {
    }

    private void ChangeBasicType() {
    }

    private void ChangeCharCode() {
    }

    private void EditDataFile(int editorType) {
    }

    private void SelectItem(MyFileListItem item, int count) {
    }

    private void UnselectItem(MyFileListItem item, int count) {
    }

    private MyFileListItem GetEventItem(ListEvent event) {
        return new MyFileListItem();
    }

    public DiskBasic GetDiskBasic() {
        return m_current_basic;
    }

    public UiDiskFileListCtrl GetListCtrl() {
        return listCtrl;
    }

    public void ShowFileAttr() {
    }

    public boolean ShowFileAttr(DiskBasicDirItem item) {
        return false;
    }

    public void AcceptSubmittedFileAttr(DiskBasicDirItemAttr dlg) {
    }

    public void CloseAllFileAttr() {
    }

    public boolean IsWritableBasicFile() {
        return m_current_basic != null && m_current_basic.IsWritableIntoDisk();
    }

    public boolean IsDeletableBasicFile() {
        return m_current_basic != null && m_current_basic.IsDeletable();
    }

    public int GetListSelectedItemCount() {
        return listCtrl.GetListSelectedItemCount();
    }

    public boolean CanUseBasicDisk() {
        return m_current_basic != null && m_current_basic.CanUse();
    }

    public boolean IsAssignedBasicDisk() {
        return m_current_basic != null && m_current_basic.IsAssigned();
    }

    public boolean IsFormattableBasicDisk() {
        return m_current_basic != null && m_current_basic.IsFormattable();
    }

    public boolean IsFormattedBasicDisk() {
        return m_current_basic != null && m_current_basic.IsFormatted();
    }

    public void GetFatAvailability(int[] offset, int[] arr) {
    }

    public void SetListFont(Font font) {
    }
}

class UiDiskFileListStoreModel extends DefaultListModel {

    private final UiDiskFrame frame;
    private UiDiskFileList ctrl;

    public UiDiskFileListStoreModel(UiDiskFrame parentframe, JWindow parent) {
        // : wxDataViewListStore() {} // Assuming base constructor call
        this.frame = parentframe;
        this.ctrl = (UiDiskFileList) parent;
    }

    public void SetControl(UiDiskFileList n_ctrl) {
        this.ctrl = n_ctrl;
    }

    public boolean SetValue(Object variant, Object item, int col) {
        return false;
    }

    public int Compare(Object item1, Object item2, int col, boolean ascending) {
        DiskBasic basic = ctrl.GetDiskBasic();
        if (basic == null) return 0;

        DiskBasicDirItems dir_items = basic.GetCurrentDirectoryItems();
        if (dir_items == null) return 0;

        int[] idx = new int[] {-1};
        if (ctrl.GetListCtrl() == null || ctrl.GetListCtrl().FindColumn(col, idx) == 0) return 0;

        int cmp = 0;
        int i1 = GetItemData(item1);
        int i2 = GetItemData(item2);
        int dir = ascending ? 1 : -1;
        switch (idx[0]) {
            case UiDiskFileList.LISTCOL_NAME:
                cmp = UiDiskFileListCtrl.CompareName(dir_items, i1, i2, dir);
                break;
            case UiDiskFileList.LISTCOL_SIZE:
                cmp = UiDiskFileListCtrl.CompareSize(dir_items, i1, i2, dir);
                break;
            case UiDiskFileList.LISTCOL_GROUPS:
                cmp = UiDiskFileListCtrl.CompareGroups(dir_items, i1, i2, dir);
                break;
            case UiDiskFileList.LISTCOL_START:
                cmp = UiDiskFileListCtrl.CompareStart(dir_items, i1, i2, dir);
                break;
            case UiDiskFileList.LISTCOL_DATE:
                cmp = UiDiskFileListCtrl.CompareDate(dir_items, i1, i2, dir);
                break;
            case UiDiskFileList.LISTCOL_NUM:
                cmp = UiDiskFileListCtrl.CompareNum(dir_items, i1, i2, dir);
                break;
            default:
                break;
        }
        return cmp;
    }

    // Placeholder method for GetItemData
    private int GetItemData(Object item) {
        return 0;
    }
}

class UiDiskFileListCtrl extends UICListCtrl {

    public static class ListColumnDef {

        public String name;
        public String label;
        public boolean shown;
        public int width;
        public int align;
        public boolean resizable;

        public ListColumnDef(String name, String label, boolean shown, int width, int align, boolean resizable) {
            this.name = name;
            this.label = label;
            this.shown = shown;
            this.width = width;
            this.align = align;
            this.resizable = resizable;
        }
    }

    public static final ListColumnDef[] gUiDiskFileListColumnDefs = {
            new ListColumnDef("Name", "File Name", true, 160, 1, true), // wxALIGN_LEFT = 1
            new ListColumnDef("Attr", "Attributes", false, 150, 1, true),
            new ListColumnDef("Size", "Size", false, 60, 2, true), // wxALIGN_RIGHT = 2
            new ListColumnDef("Groups", "Groups", false, 40, 2, true),
            new ListColumnDef("Start", "Start Group", false, 40, 2, true),
            new ListColumnDef("Track", "Track", false, 40, 2, false),
            new ListColumnDef("Side", "Side", false, 40, 2, false),
            new ListColumnDef("Sector", "Sector", false, 40, 2, false),
            new ListColumnDef("Division", "Division", false, 40, 2, false),
            new ListColumnDef("Date", "Date Time", false, 150, 1, true),
            new ListColumnDef("StartAddr", "Load Address", false, 60, 2, false),
            new ListColumnDef("EndAddr", "End Address", false, 60, 2, false),
            new ListColumnDef("ExecAddr", "Execute Address", false, 60, 2, false),
            new ListColumnDef("Num", "Num", false, 40, 2, true),
            new ListColumnDef(null, null, false, 0, 1, false)
    };

    public UiDiskFileListCtrl(UiDiskFrame parentframe, JWindow parent, int id) {
        this(parentframe, parent, id, new Point(), new Dimension());
    }

    public UiDiskFileListCtrl(UiDiskFrame parentframe, JWindow parent, int id, Point pos, Dimension size) {
        // Simplified constructor: skipping base class initialization with complex args
        super();
        AssignListIcons(new Object[0]); // Simplified icon assignment
    }

    protected void SetListData(DiskBasic basic, DiskBasicDirItem item, int row, int num, ListModel values) {
        int icon = ChooseIconNumber(item);
        int[] track_num = new int[] {-1};
        int[] side_num = new int[] {-1};
        int[] sector_start = new int[] {-1};
        int[] div_num = new int[] {0};
        int[] div_nums = new int[] {1};
        if (!basic.CalcStartNumFromGroupNum((int) item.GetStartGroup(0), track_num, side_num, sector_start, div_num, div_nums)) {
            track_num[0] = -1;
            side_num[0] = -1;
            sector_start[0] = -1;
        }

        String filename = item.GetFileNameStr();
        String attr = item.GetFileAttrStr();
        int size = item.GetFileSize();
        int groups = item.GetGroupSize();
        int start = (int) item.GetStartGroup(0);
        String date = item.GetFileDateTimeStr();
        int staddr = item.GetStartAddress();
        int edaddr = item.GetEndAddress();
        int exaddr = item.GetExecuteAddress();
        int number = item.GetNumber();

        values[UiDiskFileList.LISTCOL_NAME].Set(row, icon, filename);
        values[UiDiskFileList.LISTCOL_ATTR].Set(row, attr);
        values[UiDiskFileList.LISTCOL_SIZE].Set(row, size >= 0 ? String.valueOf(size) : "---");
        values[UiDiskFileList.LISTCOL_GROUPS].Set(row, groups >= 0 ? String.valueOf(groups) : "---");
        values[UiDiskFileList.LISTCOL_START].Set(row, String.format("%02x", start));
        values[UiDiskFileList.LISTCOL_TRACK].Set(row, track_num[0] >= 0 ? String.format("%d", track_num[0]) : "-");
        values[UiDiskFileList.LISTCOL_SIDE].Set(row, side_num[0] >= 0 ? String.format("%d", side_num[0]) : "-");
        values[UiDiskFileList.LISTCOL_SECTOR].Set(row, sector_start[0] >= 0 ? String.format("%d", sector_start[0]) : "-");
        values[UiDiskFileList.LISTCOL_DIVISION].Set(row, div_nums[0] > 0 ? String.format("%d/%d", div_num[0] + 1, div_nums[0]) : "-");
        values[UiDiskFileList.LISTCOL_DATE].Set(row, date);
        values[UiDiskFileList.LISTCOL_STADDR].Set(row, staddr >= 0 ? String.format("%x", staddr) : "--");
        if (staddr >= 0 && edaddr < 0) edaddr = staddr + size - (size > 0 ? 1 : 0);
        values[UiDiskFileList.LISTCOL_EDADDR].Set(row, staddr >= 0 && edaddr >= 0 ? String.format("%x", edaddr) : "--");
        values[UiDiskFileList.LISTCOL_EXADDR].Set(row, exaddr >= 0 ? String.format("%x", exaddr) : "--");
        values[UiDiskFileList.LISTCOL_NUM].Set(row, String.format("%d", number));
    }

    protected void InsertListData(DiskBasic basic, DiskBasicDirItem item, int row, int num, int data) {
        MyFileListValue[] values = new MyFileListValue[UiDiskFileList.LISTCOL_END];
        for (int i = 0; i < UiDiskFileList.LISTCOL_END; i++) {
            values[i] = new MyFileListValue();
        }

        SetListData(basic, item, row, num, values);

        InsertListItem(row, values, UiDiskFileList.LISTCOL_END, data);
    }

    protected void UpdateListData(DiskBasic basic, DiskBasicDirItem item, int row, int num, int data) {
        MyFileListValue[] values = new MyFileListValue[UiDiskFileList.LISTCOL_END];
        for (int i = 0; i < UiDiskFileList.LISTCOL_END; i++) {
            values[i] = new MyFileListValue();
        }

        SetListData(basic, item, row, num, values);

        UpdateListItem(row, values, UiDiskFileList.LISTCOL_END, data);
    }

    protected int ChooseIconNumber(DiskBasicDirItem item) {
        DiskBasicFileType file_type = item.GetFileAttr();
        int icon = -1;
        if (!item.IsUsed()) {
            icon = UiDiskFileList.ICON_FOR_LIST_FILE_DELETE;
        } else if (!item.IsVisible()) {
            icon = UiDiskFileList.ICON_FOR_LIST_FILE_HIDDEN;
        } else if (file_type.IsDirectory()) {
            icon = UiDiskFileList.ICON_FOR_LIST_FOLDER;
        } else if (file_type.IsVolume()) {
            icon = UiDiskFileList.ICON_FOR_LIST_LABEL;
        } else {
            icon = UiDiskFileList.ICON_FOR_LIST_FILE_NORMAL;
        }
        return icon;
    }

    public void SetListItems(DiskBasic basic) {
        int row = 0;
        int row_count = GetItemCount();

        DiskBasicDirItems dir_items = basic.GetCurrentDirectoryItems();
        if (dir_items != null) {
            boolean show_all = Globals.gConfig.IsShownDeletedFile();

            for (int idx = 0; idx < dir_items.Count(); idx++) {
                DiskBasicDirItem item = dir_items.Item(idx);
                if (!show_all && !item.IsUsedAndVisible()) continue;

                if (row < row_count) {
                    UpdateListData(basic, item, row, idx, idx);
                } else {
                    InsertListData(basic, item, row, idx, idx);
                }
                row++;
            }
        }
        // Assuming #else path (ListCtrl) for deletion/count
        for (int idx = row; idx < row_count; idx++) {
            DeleteItem(row);
        }
        // SetItemCount(row); // For virtual list ctrl
        SortDataItems(basic, -1);
    }

    public void UpdateListItems(DiskBasic basic) {
        DiskBasicDirItems dir_items = basic.GetCurrentDirectoryItems();
        if (dir_items == null) return;

        int count = GetItemCount();
        for (int row = 0; row < count; row++) {
            int idx = (int) GetListItemDataByRow(row);

            DiskBasicDirItem item = dir_items.Item(idx);

            UpdateListData(basic, item, row, idx, idx);
        }
    }

    // struct st_file_list_sort_exp equivalent
    private static class FileListSortExp {

        DiskBasicDirItems items;
        Comparator<Integer> cmpfunc;
        int dir;
    }

    // wxCALLBACK Compare equivalent (Comparator for indices)
    public static class ListCompare implements Comparator<Long> {

        private final FileListSortExp exp;

        public ListCompare(FileListSortExp exp) {
            this.exp = exp;
        }

        @Override
        public int compare(int item1, int item2) {
            int cmp = exp.cmpfunc != null ? exp.cmpfunc.compare(item1.intValue(), item2.intValue()) : 0;
            if (cmp == 0) cmp = (item1.intValue() - item2.intValue());
            return cmp;
        }
    }

    public void SortDataItems(DiskBasic basic, int col) {
        FileListSortExp exp = new FileListSortExp();

        int[] idx = new int[1];
        boolean[] match_col = new boolean[1];
        exp.dir = SelectColumnSortDir(col, idx, match_col);

        DiskBasicDirItems dir_items = null;
        if (basic != null) {
            dir_items = basic.GetCurrentDirectoryItems();
        }

        if (col >= 0 && match_col[0]) {
            if (dir_items != null) {
                exp.items = dir_items;
                switch (idx[0]) {
                    case UiDiskFileList.LISTCOL_NAME:
                        exp.cmpfunc = (i1, i2) -> CompareName(exp.items, i1, i2, exp.dir);
                        break;
                    case UiDiskFileList.LISTCOL_ATTR:
                        exp.cmpfunc = (i1, i2) -> CompareAttr(exp.items, i1, i2, exp.dir);
                        break;
                    case UiDiskFileList.LISTCOL_SIZE:
                        exp.cmpfunc = (i1, i2) -> CompareSize(exp.items, i1, i2, exp.dir);
                        break;
                    case UiDiskFileList.LISTCOL_GROUPS:
                        exp.cmpfunc = (i1, i2) -> CompareGroups(exp.items, i1, i2, exp.dir);
                        break;
                    case UiDiskFileList.LISTCOL_START:
                        exp.cmpfunc = (i1, i2) -> CompareStart(exp.items, i1, i2, exp.dir);
                        break;
                    case UiDiskFileList.LISTCOL_DATE:
                        exp.cmpfunc = (i1, i2) -> CompareDate(exp.items, i1, i2, exp.dir);
                        break;
                    case UiDiskFileList.LISTCOL_NUM:
                        exp.cmpfunc = (i1, i2) -> CompareNum(exp.items, i1, i2, exp.dir);
                        break;
                    default:
                        exp.cmpfunc = null;
                        break;
                }
                SortItems(new ListCompare(exp), 0); // Simplified sortdata
            }
            SetColumnSortIcon(idx[0]);
        }
    }

    public static int CompareName(DiskBasicDirItems items, int i1, int i2, int dir) {
        return items.Item(i1).GetFileNameStr().compareTo(items.Item(i2).GetFileNameStr()) * dir;
    }

    public static int CompareAttr(DiskBasicDirItems items, int i1, int i2, int dir) {
        int cmp = (items.Item(i1).GetFileAttrStr().compareTo(items.Item(i2).GetFileAttrStr()) * dir);
        if (cmp == 0) cmp = (items.Item(i1).GetFileNameStr().compareTo(items.Item(i2).GetFileNameStr()) * dir);
        return cmp;
    }

    public static int CompareSize(DiskBasicDirItems items, int i1, int i2, int dir) {
        return (items.Item(i1).GetFileSize() - items.Item(i2).GetFileSize()) * dir;
    }

    public static int CompareGroups(DiskBasicDirItems items, int i1, int i2, int dir) {
        return (items.Item(i1).GetGroupSize() - items.Item(i2).GetGroupSize()) * dir;
    }

    public static int CompareStart(DiskBasicDirItems items, int i1, int i2, int dir) {
        return ((int) items.Item(i1).GetStartGroup(0) - (int) items.Item(i2).GetStartGroup(0)) * dir;
    }

    public static int CompareDate(DiskBasicDirItems items, int i1, int i2, int dir) {
        DateTime tm1 = LocalDateTime.now();
        DateTime tm2 = LocalDateTime.now();
        int cmp;
        items.Item(i1).GetFileCreateDateTime(tm1);
        items.Item(i2).GetFileCreateDateTime(tm2);
        cmp = TM.Compare(tm1, tm2) * dir;
        if (cmp != 0) return cmp;
        items.Item(i1).GetFileModifyDateTime(tm1);
        items.Item(i2).GetFileModifyDateTime(tm2);
        cmp = TM.Compare(tm1, tm2) * dir;
        return cmp;
    }

    public static int CompareNum(DiskBasicDirItems items, int i1, int i2, int dir) {
        return (items.Item(i1).GetNumber() - items.Item(i2).GetNumber()) * dir;
    }
}