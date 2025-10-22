package l3diskex.ui;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EventObject;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JList;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;

import l3diskex.Config;
import l3diskex.ui.UICListCtrl.MyCListValue;


// MyCListCtrl.java
public class UICListCtrl extends JList {

    public class MyCListValue extends ListItem {
        public MyCListValue() {
            setMask(ListMask.TEXT);
        }

        public void set(int row, int icon, String value) {
            setId((int)row);
            setImage(icon);
            setText(value);
            setMask(ListMask.IMAGE | ListMask.TEXT);
        }

        public void set(int row, String value) {
            setId((int)row);
            setText(value);
        }
    }

    // MyCListColumn.java
    public class MyCListColumn {
        private int idx;
        private int col;
        private ListColumns info;
        private String label;
        private int width;
        private int sortDir;

        public MyCListColumn(int idx, ListColumns info, int width) {
            set(idx, info, width);
        }

        public void set(int idx, ListColumns info, int width) {
            this.idx = idx;
            this.col = idx;
            this.info = info;
            this.width = width;
            this.sortDir = 0;
            this.label = info.getLabel(); // Assuming translation is handled elsewhere
        }

        public int getIndex() {
            return idx;
        }

        public int getColumn() {
            return col;
        }

        public void setColumn(int val) {
            col = val;
        }

        public boolean haveIcon() {
            return info.haveIcon();
        }

        public int getWidth() {
            return width;
        }

        public void setWidth(int val) {
            width = val;
        }

        public String getText() {
            return label;
        }

        public ListColumnFormat getAlign() {
            if (info.getAlign() == Align.RIGHT) {
                return ListColumnFormat.RIGHT;
            } else if (info.getAlign() == Align.CENTER) {
                return ListColumnFormat.CENTER;
            }
            return ListColumnFormat.LEFT;
        }

        public boolean isSortable() {
            return info.isSortable();
        }

        public int getSortDir() {
            return sortDir;
        }

        public void setSortDir(int val) {
            sortDir = val;
        }
    }

    private final UiDiskFrame frame;
    private final List<MyCListColumn> columns = new ArrayList<>();
    private final List<Integer> indexes = new ArrayList<>();
    private int idOnFirstColumn;
    private final int iconSortDown;
    private final int iconSortUp;
    private final Config ini;

    public UICListCtrl(UiDiskFrame parentframe, Window parent, int id,
                       ListColumns[] columns, int iconSortDown, int iconSortUp,
                       Config ini, int style, Point pos, Dimension size) {
        super(parent, id, pos, size, style | ListStyle.REPORT);

        this.frame = parentframe;
        this.ini = ini;
        this.iconSortDown = iconSortDown;
        this.iconSortUp = iconSortUp;

        // Set column data
        for (int idx = 0; columns[idx] != null; idx++) {
            ListColumns c = columns[idx];
            int w = ini != null ? ini.getListColumnWidth(idx) : -1;
            this.columns.add(new MyCListColumn(idx, c, w >= 0 ? w : c.getWidth()));
            this.indexes.add(-1);
        }

        // Set column positions
        int columnCount = this.columns.size();
        idOnFirstColumn = 0;
        for (int idx = 0; idx < columnCount; idx++) {
            int col = ini != null ? ini.getListColumnPos(idx) : idx;
            if (idx == 0 && col < 0) {
                col = 0;
            }
            this.columns.get(idx).setColumn(col);
        }

        insertListColumns();
    }

    public void insertListColumns() {
        int columnCount = columns.size();

        for (int idx = 0; idx < indexes.size(); idx++) {
            indexes.set(idx, -1);
        }

        List<MyCListColumn> arr = new ArrayList<>(columns);
        arr.sort((c1, c2) -> {
            int n1 = c1.getColumn();
            int n2 = c2.getColumn();
            if (n1 < 0) n1 = -n1 - 1;
            if (n2 < 0) n2 = -n2 - 1;
            int n = n1 - n2;
            if (n == 0) n = c1.getIndex() - c2.getIndex();
            return n;
        });

        int nCol = 0;
        for (MyCListColumn column : arr) {
            if (column.getColumn() >= 0) {
                int nIdx = column.getIndex();
                column.setColumn(nCol);
                indexes.set(nCol, nIdx);
                if (nCol == 0) {
                    idOnFirstColumn = nIdx;
                }
                nCol++;
            }
        }

        // Insert columns into list
        for (int col = 0; col < columnCount; col++) {
            insertListColumn(col);
        }
    }

    // Additional methods would be implemented similarly...
}

class MyCListRow {
    private final List<MyCListValue> values = new ArrayList<>();
    private int data;

    public MyCListRow() {
        data = 0;
    }

    public MyCListRow(MyCListValue[] values, int count, int data) {
        set(values, count, data);
    }

    public void set(MyCListValue[] values, int count, int data) {
        for (int i = 0; i < count; i++) {
            MyCListValue val = new MyCListValue(values[i]);
            this.values.add(val);
        }
        this.data = data;
    }

    public void replace(MyCListValue[] values, int count, int data) {
        for (int i = 0; i < count; i++) {
            MyCListValue val = this.values.get(i);
            if (val != null) {
                val = values[i];
            }
        }
        this.data = data;
    }

    public void clear() {
        values.clear();
    }

    public int count() {
        return values.size();
    }

    public MyCListValue item(int idx) {
        return values.get(idx);
    }

    public int getImage() {
        int image = -1;
        for (int idx = 0; idx < values.size(); idx++) {
            int i = values.get(idx).getImage();
            if (i >= 0) {
                image = i;
                break;
            }
        }
        return image;
    }

    public int getImage(int idx) {
        return values.get(idx).getImage();
    }

    public String getText(int idx) {
        return values.get(idx).getText();
    }

    public int getData() {
        return data;
    }
}

// filepath: MyCListRows.java
class MyCListRows extends ArrayList<MyCListRow> {
    private static ListCtrlCompare fnSortCallBack;
    private static int sortData;

    public MyCListRows() {
        super();
    }

    public void insert(MyCListValue[] values, int count, int data) {
        add(new MyCListRow(values, count, data));
    }

    public void set(int row, MyCListValue[] values, int count, int data) {
        if (row >= size()) return;

        MyCListRow item = get(row);
        item.replace(values, count, data);
    }

    public void clearAll() {
        clear();
    }

    public boolean sortItems(ListCtrlCompare callback, int sortData) {
        fnSortCallBack = callback;
        MyCListRows.sortData = sortData;

        Collections.sort(this, new Comparator<MyCListRow>() {
            @Override
            public int compare(MyCListRow item1, MyCListRow item2) {
                int i1 = item1.getData();
                int i2 = item2.getData();
                return fnSortCallBack.compare(i1, i2, sortData);
            }
        });

        return true;
    }
}

// filepath: MyCListRearrangeBox.java
class MyCListRearrangeBox extends RearrangeDialog {

    public MyCListRearrangeBox(UICListCtrl parent, int[] order, String[] items) {
        super(parent, "Configure the columns shown:", "Arrange Column Order", order, items);

        Dimension sz = getSize();
        if (sz.width < 360) {
            sz.width = 360;
        }
        setSize(sz);
    }
}

// Interface for sort callback
interface ListCtrlCompare {
    int compare(int item1, int item2, int data);
}

class MyCListCtrl extends JTable {
    // Custom sorting listener
    class HeaderClickListener extends MouseAdapter {
        @Override
        public void mouseClicked(MouseEvent e) {
            JTableHeader header = getTableHeader();
            int column = header.columnAtPoint(e.getPoint());
            int idx = -1;
            boolean matchCol = false;
            int dir = selectColumnSortDir(column, idx, matchCol);

            if (column >= 0 && matchCol) {
                setColumnSortIcon(idx);

                // Notify sort event
                ListEvent event = new ListEvent(ListEvent.COLUMN_SORTED, column);
                fireListEvent(event);
            }
        }
    }

    // Implement remaining methods

    public void setColumnSortIcon(int idx) {
        int dir = columns.get(idx).getSortDir();
        int col = columns.get(idx).getColumn();
        if (col < 0) return;

        TableColumn tableColumn = getColumnModel().getColumn(col);
        TableHeaderRenderer renderer = (TableHeaderRenderer)tableColumn.getHeaderRenderer();

        String text = columns.get(idx).getText();
        if (dir > 0) {
            text += " ▲";
        } else if (dir < 0) {
            text += " ▼";
        }
        renderer.setText(text);
        getTableHeader().repaint();
    }

    public void selectAllListItems() {
        setRowSelectionInterval(0, getRowCount() - 1);
    }

    public void selectListItem(int item) {
        setRowSelectionInterval(item, item);
    }

    public void selectListRow(int row) {
        setRowSelectionInterval(row, row);
    }

    public void unselectAllListItems() {
        clearSelection();
    }

    public void unselectListItem(int item) {
        removeRowSelectionInterval(item, item);
    }

    public int getListFocusedItem() {
        return getSelectionModel().getLeadSelectionIndex();
    }

    public void focusListItem(int item) {
        setRowSelectionInterval(item, item);
        scrollRectToVisible(getCellRect(item, 0, true));
    }

    public void editListItem(int item) {
        editCellAt(item, 0);
    }

    public boolean deleteAllListItems() {
        DefaultTableModel model = (DefaultTableModel)getModel();
        model.setRowCount(0);
        return true;
    }

    public int getListItemData(int item) {
        TableModel model = getModel();
        return (long)model.getValueAt(item, getColumnCount() - 1);
    }

    public int getListItemDataByRow(int row) {
        return getListItemData(row);
    }

    public String getColumnText(int idx) {
        return columns.get(idx).getText();
    }

    public boolean columnIsShown(int idx) {
        return columns.get(idx).getColumn() >= 0;
    }

    public boolean hasItemAtPoint(int x, int y) {
        Point pt = new Point(x, y);
        return rowAtPoint(pt) != -1;
    }

    public int getItemAtPoint(int x, int y) {
        Point pt = new Point(x, y);
        return rowAtPoint(pt);
    }

    public boolean sortItems(Comparator<Object> comparator) {
        TableRowSorter<TableModel> sorter = new TableRowSorter<>(getModel());
        sorter.setComparator(getSelectedColumn(), comparator);
        setRowSorter(sorter);
        return true;
    }
}

// Custom table header renderer for sort icons
class TableHeaderRenderer extends DefaultTableCellRenderer {
    private String text;

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected, boolean hasFocus, int row, int column) {

        Component c = super.getTableCellRendererComponent(table, value,
                isSelected, hasFocus, row, column);

        setText(text);
        setHorizontalAlignment(SwingConstants.CENTER);
        setBorder(BorderFactory.createEtchedBorder());

        return c;
    }

    @Override
    public void setText(String text) {
        this.text = text;
    }
}

// ListCtrl event handling
class ListEvent extends EventObject {
    public static final int COLUMN_SORTED = 1;
    private final int column;

    public ListEvent(int type, int column) {
        super(type);
        this.column = column;
    }

    public int getColumn() {
        return column;
    }
}

enum ListColumnFormat {
    LEFT,
    RIGHT,
    CENTER
}

// filepath: ListColumns.java
class ListColumns {
    private final String name;
    private final String label;
    private final int width;
    private final int align;
    private final boolean haveIcon;
    private final boolean sortable;

    public ListColumns(String name, String label, int width, int align, boolean haveIcon, boolean sortable) {
        this.name = name;
        this.label = label;
        this.width = width;
        this.align = align;
        this.haveIcon = haveIcon;
        this.sortable = sortable;
    }

    public String getName() { return name; }
    public String getLabel() { return label; }
    public int getWidth() { return width; }
    public int getAlign() { return align; }
    public boolean haveIcon() { return haveIcon; }
    public boolean isSortable() { return sortable; }
}
