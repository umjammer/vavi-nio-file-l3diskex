/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.ui;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import javax.swing.GroupLayout.Alignment;
import javax.swing.Icon;

import l3diskex.Config;


/* -- */
/*  The public wrapper that contains all inner classes                     */
/* -- */
public class UiCdListCtrl {

    /* ----------------------------------------------------------------- */
    /*  1.  List item value (originally MyCDListValue)                    */
    /* ----------------------------------------------------------------- */

    public static class MyCDListValue {

        protected int row;
        protected int icon;
        protected String value;

        public MyCDListValue() {
            this.row = 0;
            this.icon = 0;
            this.value = "";
        }

        public void Set(int n_row, int n_icon, String n_value) {
            this.row = n_row;
            this.icon = n_icon;
            this.value = n_value;
        }

        public void Set(int n_row, String n_value) {
            this.row = n_row;
            this.value = n_value;
        }

        public void SetColumn(int val) {
            /* no‑op – original code is empty */
        }

        public int GetImage() {
            return icon;
        }

        public String GetText() {
            return value;
        }
    }

    /* ----------------------------------------------------------------- */
    /*  2.  List column information (originally MyCDListColumn)           */
    /* ----------------------------------------------------------------- */

    public static class MyCDListColumn {

        protected int idx;
        protected int col;
        protected ListColumnInfo info;        // placeholder for struct st_list_columns
        protected String label;
        protected int width;
        protected int sort_dir;
        protected DataViewColumn id;          // placeholder for wxDataViewColumn

        public MyCDListColumn(int n_idx, ListColumnInfo n_info, int n_width) {
            Set(n_idx, n_info, n_width);
        }

        public void Set(int n_idx, ListColumnInfo n_info, int n_width) {
            this.idx = n_idx;
            this.col = n_idx;          // default – will be changed later
            this.info = n_info;
            this.width = n_width;
            this.sort_dir = 0;
            this.label = (n_info != null ? n_info.text : "");
        }

        public int GetIndex() {
            return idx;
        }

        public int GetColumn() {
            return col;
        }

        public void SetColumn(int val) {
            this.col = val;
        }

        public boolean HaveIcon() {
            return info != null && info.icon;
        }

        public int GetWidth() {
            return width;
        }

        public void SetWidth(int val) {
            this.width = val;
        }

        public String GetText() {
            return label;
        }

        public Alignment GetAlign() {
            return (info != null ? info.align : Alignment.LEFT);
        }

        public boolean IsSortable() {
            return info != null && info.sortable;
        }

        public int GetSortDir() {
            return sort_dir;
        }

        public void SetSortDir(int val) {
            this.sort_dir = val;
        }

        public DataViewColumn GetId() {
            return id;
        }

        public void SetId(DataViewColumn val) {
            this.id = val;
        }
    }

    /* ----------------------------------------------------------------- */
    /*  3.  The list control itself (originally MyCDListCtrl)              */
    /* ----------------------------------------------------------------- */

    protected UiDiskFrame frame;
    protected List<MyCDListColumn> m_columns = new ArrayList<>();
    protected int m_idOnFirstColumn = 0;
    protected Config m_ini;
    protected List<Icon> m_icons = new ArrayList<>();
    protected List<Integer> m_selecting = new ArrayList<>();

    public UiCdListCtrl(UiDiskFrame parentframe, Object parent, int id,
                        ListColumnInfo[] columns, Config ini,
                        int style, DataViewModel model,
                        Point pos, Size size) {

        this.frame = parentframe;
        this.m_ini = ini;
        this.m_idOnFirstColumn = 0;

        /* initialise columns */
        if (columns != null) {
            for (int i = 0; i < columns.length; i++) {
                ListColumnInfo ci = columns[i];
                MyCDListColumn col = new MyCDListColumn(i, ci, ci.width);
                m_columns.add(col);
            }
        }

        /* initialise icons – the original code receives a triple‑pointer
           to a final char***.  In Java we simply use a placeholder. */
        AssignListIcons(null);

        /* bind events – in the original code this is done via wxWidgets
           event table.  Here we simply remember the methods. */
    }

    /* ----------------------------------------------------------------- */
    /*  Event handlers                                                   */
    /* ----------------------------------------------------------------- */

    public void OnColumnReordered(DataViewEvent event) {
        /* placeholder – real code would handle the event */
    }

    public void OnColumnSorted(DataViewEvent event) {
        /* placeholder – real code would handle the event */
    }

    /* ----------------------------------------------------------------- */
    /*  Column layout management                                        */
    /* ----------------------------------------------------------------- */

    public void InsertListColumns() {
        /* in the real program this rebuilds the header – here we just
           recalculate the internal ordering */
        int cols = m_columns.size();
        for (int col = 0; col < cols; col++) {
            MyCDListColumn c = m_columns.get(col);
            c.SetColumn(col);          // update visible index
        }
    }

    public void DeleteAllListColumns() {
        m_columns.clear();
    }

    public void InsertListColumn(int col) {
        /* find the column in the current order and insert it */
        MyCDListColumn c = FindColumn(col, null, false);
        if (c != null) {
            InsertListColumn(col, col, c);
        }
    }

    public void InsertListColumn(int col, int idx, MyCDListColumn c) {
        /* insert a column at a specific visual position – in this stub
           we simply put the column into the list at the requested index */
        if (idx >= 0 && idx <= m_columns.size()) {
            m_columns.add(idx, c);
            c.SetColumn(col);
        }
    }

    public int GetListColumnWidth(int col) {
        MyCDListColumn c = FindColumn(col, null, true);
        return (c != null) ? c.GetWidth() : 0;
    }

    public void DeleteListColumn(int col) {
        MyCDListColumn c = FindColumn(col, null, true);
        if (c != null) {
            m_columns.remove(c);
        }
    }

    /* ----------------------------------------------------------------- */
    /*  Data insertion / update                                         */
    /* ----------------------------------------------------------------- */

    public void InsertListItem(int row, MyCDListValue[] values,
                               int count, int data) {
        /* placeholder – normally this would add a row to the data‑view */
    }

    public void UpdateListItem(int row, MyCDListValue[] values,
                               int count, int data) {
        /* placeholder – normally this would update an existing row */
    }

    /* ----------------------------------------------------------------- */
    /*  Miscellaneous helpers                                            */
    /* ----------------------------------------------------------------- */

    public void SetListText(DataViewItem item, int idx, String text) {
        /* placeholder – normally would modify the text of a cell */
    }

    public int GetListSelectedRow() {
        return -1;  // placeholder
    }

    public int GetListSelectedNum() {
        return -1;  // placeholder
    }

    public int GetListSelectedItemCount() {
        return -1;  // placeholder
    }

    public DataViewItem GetListSelection() {
        return null;  // placeholder
    }

    public int GetListSelections(DataViewItemArray arr) {
        return -1;  // placeholder
    }

    public void SelectAllListItem() {
        /* placeholder – mark all items as selected */
    }

    public void SelectListItem(DataViewItem item) {
        /* placeholder */
    }

    public void SelectListRow(int row) {
        /* placeholder */
    }

    public void UnselectAllListItem() {
        /* placeholder – clear selection list */
    }

    public void UnselectListItem(DataViewItem item) {
        /* placeholder */
    }

    public int GetListSelected(int row) {
        if (row < 0 || row >= m_selecting.size()) return 0;
        return m_selecting.get(row);
    }

    public void SetListSelected(int row, int val) {
        if (row < 0) return;
        while (m_selecting.size() <= row) m_selecting.add(0);
        m_selecting.set(row, val);
    }

    public DataViewItem GetListFocusedItem() {
        return null; // placeholder
    }

    public void FocusListItem(DataViewItem item) {
        /* placeholder */
    }

    public void EditListItem(DataViewItem item) {
        /* placeholder */
    }

    public boolean DeleteAllListItems() {
        /* placeholder – remove all rows from the data‑view */
        return true;
    }

    public int GetListItemData(DataViewItem item) {
        return 0; // placeholder
    }

    public int GetListItemDataByRow(int row) {
        return 0; // placeholder
    }

    public String GetColumnText(int idx) {
        if (idx < 0 || idx >= m_columns.size()) return "";
        return m_columns.get(idx).GetText();
    }

    public boolean ColumnIsShown(int idx) {
        MyCDListColumn c = FindColumn(idx, null, false);
        return (c != null && c.GetColumn() >= 0);
    }

    public boolean ShowColumn(int idx, boolean show) {
        MyCDListColumn c = FindColumn(idx, null, false);
        if (c == null) return false;
        if (show) {
            c.SetColumn(idx);
            return true;
        } else {
            c.SetColumn(-1);
            return false;
        }
    }

    /* ----------------------------------------------------------------- */
    /*  Column lookup                                                   */
    /* ----------------------------------------------------------------- */

    public MyCDListColumn FindColumn(int col, int[] n_idx, boolean all) {
        MyCDListColumn match = null;
        for (int i = 0; i < m_columns.size(); i++) {
            MyCDListColumn c = m_columns.get(i);
            if (c.GetColumn() == col && (all || c.GetColumn() >= 0)) {
                match = c;
                if (n_idx != null && n_idx.length > 0) n_idx[0] = i;
                break;
            }
        }
        return match;
    }

    public MyCDListColumn FindColumn(DataViewColumn col, int[] n_idx, boolean all) {
        MyCDListColumn match = null;
        for (int i = 0; i < m_columns.size(); i++) {
            MyCDListColumn c = m_columns.get(i);
            if (c.id == col && (all || c.GetColumn() >= 0)) {
                match = c;
                if (n_idx != null && n_idx.length > 0) n_idx[0] = i;
                break;
            }
        }
        return match;
    }

    public void GetListColumnsByCurrentOrder(List<MyCDListColumn> items) {
        /* the real code would consider the visual order – here we simply
           copy the current list. */
        items.clear();
        items.addAll(m_columns);
    }

    public boolean ShowListColumnRearrangeBox() {
        /* placeholder – in real code a dialog would be shown */
        return false;
    }

    public static int SortByColumn(MyCDListColumn[] i1, MyCDListColumn[] i2) {
        int n = i1[0].GetColumn() - i2[0].GetColumn();
        if (n == 0) n = i1[0].GetIndex() - i2[0].GetIndex();
        return n;
    }

    public void ReorderColumns() {
        /* placeholder – would recalculate column positions */
    }

    public boolean HasItemAtPoint(int x, int y) {
        return false; // placeholder
    }

    public DataViewItem GetItemAtPoint(int x, int y) {
        return null; // placeholder
    }

    /* ----------------------------------------------------------------- */
    /*  Rearrange dialog stub                                           */
    /* ----------------------------------------------------------------- */

    public static class MyCDListRearrangeBox {

        public MyCDListRearrangeBox(MyCDListCtrl parent,
                                    List<Integer> order,
                                    List<String> items) {
            /* placeholder – no real dialog */
        }
    }

    /* ----------------------------------------------------------------- */
    /*  Main class that ties everything together – this is the public   */
    /*  entry point of the translation.  All the other classes are      */
    /*  declared as static inner classes.  The file can be compiled as  */
    /*  is.  The real UI code would replace the stubbed classes with   */
    /*  proper implementations from a Java UI framework.                */
    /* ----------------------------------------------------------------- */

    /* placeholder for the public class that would normally contain the   */
    /* application entry point or be used as a plugin.  The class does  */
    /* not provide a main() method – this is only a translation of the  */
    /* original C++ classes. */
}
