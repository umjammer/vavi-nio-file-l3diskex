package l3diskex.ui;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Vector;
import javax.swing.Box;
import javax.swing.DefaultListModel;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;

import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.BasicCommon.KeyValArray;
import l3diskex.basicfmt.BasicCommon.KeyValItem;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.DiskBasicDirItem.DiskBasicDirItemAttr;


public class IntNameBox extends JDialog {

    private static final String INTNAMEBOX_CLASSNAME = "INTNAMEBOX";
    private static final int INTNAME_COLUMN_WIDTH = 120;
    private static final int INTNAME_LISTCOL_WIDTH = 42;

    public static final int INTNAME_SHOW_TEXT = 0x0001;
    /** 内部ファイル名を表示する */
    public static final int INTNAME_SHOW_ATTR = 0x0002;
    /** 属性を表示する */
    public static final int INTNAME_SHOW_PROPERTY = 0x0004;
    /** プロパティ表示（グループ一覧表示） */
    public static final int INTNAME_SHOW_SKIP_DIALOG = 0x0008;
    /** スキップするかチェックボックス表示 */
    public static final int INTNAME_NEW_FILE = 0x0010;
    /** 新規ファイル時 */
    public static final int INTNAME_IMPORT_INTERNAL = 0x0020;
    /** アプリ内インポート */
    public static final int INTNAME_SPECIFY_FILE_NAME = 0x0100;
    /** ファイル名を別途指定 */
    public static final int INTNAME_SPECIFY_CDATE_TIME = 0x0200;
    /** 作成日時を別途指定 */
    public static final int INTNAME_SPECIFY_MDATE_TIME = 0x0400;
    /** 更新日時を別途指定 */
    public static final int INTNAME_SPECIFY_ADATE_TIME = 0x0800;
    /** アクセス日時を別途指定 */

    // Control IDs
    public static final int IDC_TEXT_INTNAME = 1;
    public static final int IDC_TEXT_START_ADDR = 2;
    public static final int IDC_TEXT_END_ADDR = 3;
    public static final int IDC_TEXT_EXEC_ADDR = 4;
    public static final int IDC_TEXT_CDATE = 5;
    public static final int IDC_TEXT_CTIME = 6;
    public static final int IDC_TEXT_MDATE = 7;
    public static final int IDC_TEXT_MTIME = 8;
    public static final int IDC_TEXT_ADATE = 9;
    public static final int IDC_TEXT_ATIME = 10;
    public static final int IDC_CHK_IGNORE_DATE = 11;
    public static final int IDC_TEXT_FILE_SIZE = 12;
    public static final int IDC_TEXT_GROUPS = 13;
    public static final int IDC_TEXT_GROUP_SIZE = 14;
    public static final int IDC_LIST_GROUPS = 15;
    public static final int IDC_LIST_INTERNAL = 16;
    public static final int IDC_CHK_SKIP_DLG = 17;

    private UiDiskProcess frame;
    private int uniqueNumber;
    private DiskBasic basic;
    private DiskBasicDirItem item;

    private JTextField txtIntName;
    private int mNameMaxLen;

    private int fileSize;
    private int userData;

    private JTextField txtStartAddr;
    private JTextField txtEndAddr;
    private JTextField txtExecAddr;

    private JTextField txtCDate;
    private JTextField txtCTime;
    private JTextField txtMDate;
    private JTextField txtMTime;
    private JTextField txtADate;
    private JTextField txtATime;
    private JCheckBox chkIgnoreDate;

    private JTextField txtFileSize;
    private JTextField txtGroups;
    private JTextField txtGrpSize;
    private JList<String> lstGroups;
    private JList<String> lstInternal;

    private JCheckBox chkSkipDlg;

    public IntNameBox(UiDiskProcess frame, Window parent, int id, String caption, String message,
                      DiskBasic basic, DiskBasicDirItem item, String filePath, String fileName, int fileSize,
                      DiskBasicDirItemAttr dateTime, int showFlags) {
        super(parent, caption, ModalityType.APPLICATION_MODAL);
        createBox(frame, parent, id, caption, message, basic, item, filePath, fileName, fileSize, dateTime, showFlags);
    }

    private void createBox(UiDiskProcess frame, Window parent, int id, String caption, String message,
                           DiskBasic basic, DiskBasicDirItem item, String filePath, String fileName, int fileSize,
                           DiskBasicDirItemAttr dateTime, int showFlags) {
        this.frame = frame;
        this.item = item;
        this.uniqueNumber = frame.getUniqueNumber();
        this.basic = basic;
        this.fileSize = fileSize;

        setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(4, 4, 4, 4);

        mNameMaxLen = item.getFileNameStrSize();

        Font font = frame.getDefaultListFont();

        String filename = ((showFlags & IntNameBoxShowFlags.INTNAME_SPECIFY_FILE_NAME) != 0) ?
                fileName : item.getFileNameStr();

        if (message != null && !message.isEmpty()) {
            JLabel lblMessage = new JLabel(message);
            c.gridx = 0;
            c.gridy = 0;
            c.gridwidth = 2;
            add(lblMessage, c);
        }

        if ((showFlags & IntNameBoxShowFlags.INTNAME_SHOW_TEXT) != 0) {
            JLabel lblIntName = new JLabel("File Name In The Disk Image");
            c.gridy++;
            add(lblIntName, c);

            txtIntName = new JTextField(20);
            if (item.isFileNameEditable()) {
                txtIntName.setText(filename);
                txtIntName.setDocument(new LimitedDocument(mNameMaxLen));
            } else {
                txtIntName.setText(filename);
                txtIntName.setEditable(false);
            }
            txtIntName.setFont(font);
            c.gridy++;
            add(txtIntName, c);
        }

        // Continue with rest of UI components...
        // Add action listeners and other initialization code
    }

    private static class LimitedDocument extends javax.swing.text.PlainDocument {

        private final int limit;

        LimitedDocument(int limit) {
            super();
            this.limit = limit;
        }

        @Override
        public void insertString(int offset, String str, javax.swing.text.AttributeSet attr)
                throws javax.swing.text.BadLocationException {
            if (str == null) return;
            if ((getLength() + str.length()) <= limit) {
                super.insertString(offset, str, attr);
            }
        }
    }

    private void createDateTime(JPanel parent, int dateId, int timeId,
                                String label, boolean hasDateTime, boolean hasDate, boolean hasTime, boolean ignore,
                                Dimension size, GridBagConstraints c, GridBagLayout grid,
                                JTextField dateField, JTextField timeField) {

        if (hasDateTime) {
            JLabel lbl = new JLabel(label);
            lbl.setPreferredSize(size);
            grid.setConstraints(lbl, c);
            parent.add(lbl);

            if (hasDate) {
                dateField = new JTextField();
                dateField.setDocument(new DateTimeDocument(false, !ignore));
                dateField.setPreferredSize(getDateTextExtent(dateField));
                dateField.setDocument(new LimitedDocument(10));
                grid.setConstraints(dateField, c);
                parent.add(dateField);
            } else {
                parent.add(Box.createHorizontalStrut(1));
            }

            if (hasTime) {
                timeField = new JTextField();
                timeField.setDocument(new DateTimeDocument(true, !ignore));
                timeField.setPreferredSize(getTimeTextExtent(timeField));
                timeField.setDocument(new LimitedDocument(8));
                grid.setConstraints(timeField, c);
                parent.add(timeField);
            } else {
                parent.add(Box.createHorizontalStrut(1));
            }
        }
    }

    private void createFileSize(JPanel parent, int id,
                                String label, int maxLength, boolean isBytes,
                                Dimension size, GridBagConstraints c, GridBagLayout grid,
                                JTextField textField) {

        JLabel lbl = new JLabel(label);
        lbl.setPreferredSize(size);
        grid.setConstraints(lbl, c);
        parent.add(lbl);

        textField = new JTextField();
        textField.setHorizontalAlignment(JTextField.RIGHT);
        textField.setEditable(false);
        Dimension txtSize = getTextExtent(textField, "0".repeat(maxLength));
        txtSize.height = -1;
        textField.setPreferredSize(txtSize);
        grid.setConstraints(textField, c);
        parent.add(textField);

        if (isBytes) {
            JLabel bytesLbl = new JLabel("bytes");
            grid.setConstraints(bytesLbl, c);
            parent.add(bytesLbl);
        } else {
            parent.add(Box.createHorizontalStrut(1));
        }
    }

    private class DateTimeDocument extends PlainDocument {

        private final boolean isTime;
        private final boolean required;

        public DateTimeDocument(boolean isTime, boolean required) {
            this.isTime = isTime;
            this.required = required;
        }

        @Override
        public void insertString(int offset, String str, AttributeSet attr)
                throws BadLocationException {
            if (str == null) return;

            String text = getText(0, getLength());
            String newText = text.substring(0, offset) + str + text.substring(offset);

            if (isTime) {
                if (isValidTimeFormat(newText)) {
                    super.insertString(offset, str, attr);
                }
            } else {
                if (isValidDateFormat(newText)) {
                    super.insertString(offset, str, attr);
                }
            }
        }

        private boolean isValidTimeFormat(String text) {
            try {
                if (!required && text.trim().isEmpty()) return true;
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
                sdf.setLenient(false);
                sdf.parse(text);
                return true;
            } catch (ParseException e) {
                return false;
            }
        }

        private boolean isValidDateFormat(String text) {
            try {
                if (!required && text.trim().isEmpty()) return true;
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd");
                sdf.setLenient(false);
                sdf.parse(text);
                return true;
            } catch (ParseException e) {
                return false;
            }
        }
    }

    private Dimension getDateTextExtent(JTextField field) {
        FontMetrics fm = field.getFontMetrics(field.getFont());
        int width = fm.stringWidth("0000/00/00");
        int height = fm.getHeight();
        return new Dimension(width, height);
    }

    private Dimension getTimeTextExtent(JTextField field) {
        FontMetrics fm = field.getFontMetrics(field.getFont());
        int width = fm.stringWidth("00:00:00");
        int height = fm.getHeight();
        return new Dimension(width, height);
    }

    private Dimension getTextExtent(JTextField field, String text) {
        FontMetrics fm = field.getFontMetrics(field.getFont());
        int width = fm.stringWidth(text);
        int height = fm.getHeight();
        return new Dimension(width, height);
    }

    public void setFileSize(int val) {
        if (txtFileSize != null) {
            String str = convFileSize(val);
            txtFileSize.setText(str);
        }
    }

    public static String convFileSize(int val) {
        if (val >= 0) {
            NumberFormat nf = NumberFormat.getNumberInstance();
            return String.format("%s (0x%x)", nf.format(val), val);
        } else {
            return "---";
        }
    }

    public void setGroups(DiskBasicGroups vals) {
        int val = vals.getNums();

        if (txtGroups != null) {
            String str;
            if (val >= 0) {
                NumberFormat nf = NumberFormat.getNumberInstance();
                str = String.format("%s (0x%x)", nf.format(val), val);
            } else {
                str = "---";
            }
            txtGroups.setText(str);
        }

        if (txtGrpSize != null) {
            String str;
            if (val >= 0) {
                val = val * vals.getSizePerGroup();
                NumberFormat nf = NumberFormat.getNumberInstance();
                str = String.format("%s (0x%x)", nf.format(val), val);
            } else {
                str = "---";
            }
            txtGrpSize.setText(str);
        }

        if (lstGroups != null) {
            DefaultListModel<String> model = (DefaultListModel<String>) lstGroups.getModel();
            model.clear();

            for (int i = 0; i < vals.size(); i++) {
                DiskBasicGroupItem item = vals.get(i);
                Vector<String> row = new Vector<>();
                row.add(String.format("%02x", item.group));
                row.add(String.format("%d", item.track));
                row.add(String.format("%d", item.side));
                row.add(String.format("%d", item.sectorStart));
                row.add(String.format("%d", item.sectorEnd));
                row.add(String.format("%d/%d", item.divNum + 1, item.divNums));
                model.addElement(row);
            }
        }
    }

    public void setInternalDatas(KeyValArray vals) {
        if (lstInternal != null) {
            DefaultListModel<String> model = (DefaultListModel<String>) lstInternal.getModel();
            model.clear();

            Dimension sz = this.getSize();
            Vector<String> sizeRow = new Vector<>();
            sizeRow.add("size");
            sizeRow.add(String.format("(%d,%d)", sz.width, sz.height));
            model.addElement(sizeRow);

            for (int i = 0; i < vals.size(); i++) {
                KeyValItem item = vals.getItem(i);
                Vector<String> row = new Vector<>();
                row.add(item.getKey());
                row.add(item.getValueString());
                model.addElement(row);
            }
        }
    }

    public boolean isSkipDialog(boolean defVal) {
        return (chkSkipDlg != null ? chkSkipDlg.isSelected() : defVal);
    }

    // Action listener implementations
    private class ChangeStartAddrListener implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent e) {
            calcEndAddress();
        }
    }

    private class ChangeIgnoreDateListener implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent e) {
            JCheckBox cb = (JCheckBox) e.getSource();
            changedIgnoreDate(cb.isSelected());
        }
    }

    private class MyListSelectionListener implements ListSelectionListener {

        @Override
        public void valueChanged(ListSelectionEvent e) {
            if (!e.getValueIsAdjusting()) {
                int idx = lstGroups.getSelectedIndex();
                if (idx != -1) {
                    handleListItemSelected(idx);
                }
            }
        }
    }

    private void handleListItemSelected(int idx) {
        if (frame.getBinDumpFrame() != null) {
            L3DiskEx.UiDiskFileList fileList = frame.getFileListPanel();
            if (fileList == null) return;

            Vector<String> row = (Vector<String>) lstGroups.getModel().getElementAt(idx);
            int grp = Integer.parseInt(row.get(0), 16);
            int trk = Integer.parseInt(row.get(1));
            int sid = Integer.parseInt(row.get(2));
            int secStart = Integer.parseInt(row.get(3));
            int secEnd = Integer.parseInt(row.get(4));

            fileList.setDumpData(trk, sid, secStart, secEnd);
        }
    }
}