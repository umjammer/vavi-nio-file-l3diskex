/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package l3diskex.ui.dirItem;

import java.awt.Dimension;
import java.util.ResourceBundle;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JSpinner;

import l3diskex.basicfmt.DiskBasicDirItem.DiskBasicDirItemAttr;
import l3diskex.basicfmt.diritem.DiskBasicDirItemTRSDOS;
import l3diskex.basicfmt.DiskBasicError;
import l3diskex.ui.IntNameBox;
import l3diskex.ui.UiDirItem;

import static l3diskex.basicfmt.diritem.DiskBasicDirItemTRSDOS.FILETYPE_MASK_TRSDOS_ACCESS;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemTRSDOS.FILETYPE_MASK_TRSDOS_INUSE;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemTRSDOS.FILETYPE_MASK_TRSDOS_INVISIBLE;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemTRSDOS.FILETYPE_MASK_TRSDOS_OVERFLOW;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemTRSDOS.FILETYPE_MASK_TRSDOS_SYSTEM;


/**
 * UiDirItemTRSDOS.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2025-10-19 nsano initial version <br>
 */
public class UiDirItemTRSDOS extends UiDirItem {

    private static final ResourceBundle rb = ResourceBundle.getBundle("messages");

    DiskBasicDirItemTRSDOS<?> dirItem;

    // プロパティダイアログ用

    static final int IDC_CHECK_INVISIBLE = 51;
    static final int IDC_CHECK_SYSTEM = 52;
    static final int IDC_CHECK_INUSE = 53;
    static final int IDC_CHECK_OVERFLOW = 54;
    static final int IDC_SPIN_ACCESS = 55;

    /**
     * インポート時ダイアログ表示前にファイルの属性を設定
     */
    protected void SetFileTypeForAttrDialog(int show_flags, String name, int[] file_type_1, int[] file_type_2) {
        if ((show_flags & INTNAME_NEW_FILE) != 0) {
            // 外部からインポート時
            // file_type_1[0] = FILETYPE_MASK_PRODOS_SEEDING << 8 | FILETYPE_MASK_PRODOS_ACCESS_ALL;
            // file_type_2[0] = ConvOriginalTypeFromFileName(name);
        }
    }

    /**
     * ダイアログ内の属性部分のレイアウトを作成
     */
    @Override
    public void createControlsForAttrDialog(IntNameBox parent, int show_flags, String file_path, BoxLayout sizer, Object flags) {
        int file_type_1 = dirItem.getFileType1();
        int file_type_2 = 0;
        JCheckBox chkInvisible;
        JCheckBox chkSystem;
        JCheckBox chkInUse;
        JCheckBox chkOverflow;
        JSpinner spnAccess;

        int[] ft1 = {file_type_1};
        int[] ft2 = {file_type_2};
        setFileTypeForAttrDialog(show_flags, file_path, ft1, ft2);
        file_type_1 = ft1[0];
        // file_type_2 = ft2[0]; // file_type_2 is not used immediately
        parent.SetUserData(file_type_1);

        DimensionrFlags expand = new DimensionrFlags().Expand();

        BoxLayout staType1 = new BoxLayout(new StaticBox(parent, -1, rb.getString("File Attributes")), BoxLayout.VERTICAL);
        BoxLayout hbox = new BoxLayout(BoxLayout.HORIZONTAL);

        chkInvisible = new JCheckBox(parent, IDC_CHECK_INVISIBLE, rb.getString("Invisible"));
        chkInvisible.SetValue((file_type_1 & FILETYPE_MASK_TRSDOS_INVISIBLE) != 0);
        chkSystem = new JCheckBox(parent, IDC_CHECK_SYSTEM, rb.getString("System"));
        chkSystem.SetValue((file_type_1 & FILETYPE_MASK_TRSDOS_SYSTEM) != 0);
        chkInUse = new JCheckBox(parent, IDC_CHECK_INUSE, rb.getString("In Use"));
        chkInUse.SetValue((file_type_1 & FILETYPE_MASK_TRSDOS_INUSE) != 0);
        chkInUse.enable(false);
        chkOverflow = new JCheckBox(parent, IDC_CHECK_OVERFLOW, rb.getString("Overflow"));
        chkOverflow.SetValue((file_type_1 & FILETYPE_MASK_TRSDOS_OVERFLOW) != 0);
        chkOverflow.enable(false);

        hbox.Add(chkInvisible, expand);
        hbox.Add(chkSystem, expand);
        hbox.Add(chkInUse, expand);
        hbox.Add(chkOverflow, expand);
        staType1.Add(hbox, flags);
        sizer.Add(staType1, flags);

        Dimension sz = new Dimension(64, -1);
        BoxLayout staType2 = new BoxLayout(new wxStaticBox(parent, -1, rb.getString("Protection Level")), wxBoxLayout.VERTICAL);
        hbox = new BoxLayout(BoxLayout.HORIZONTAL);
        spnAccess = new JSpinner(parent, IDC_SPIN_ACCESS, "", null, sz);
        spnAccess.SetRange(0, 7);
        spnAccess.SetValue(file_type_1 & FILETYPE_MASK_TRSDOS_ACCESS);

        hbox.Add(spnAccess, expand);
        staType2.Add(hbox, flags);
        sizer.Add(staType2, flags);
    }

    /**
     * ダイアログ内の値を設定
     */
    @Override
    public void initializeForAttrDialog(IntNameBox parent, int show_flags, int[] user_data) {
        // Default empty implementation
    }

    /**
     * 機種依存の属性を設定する
     */
    @Override
    public boolean setAttrInAttrDialog(IntNameBox parent, DiskBasicDirItemAttr attr, DiskBasicError errinfo) {
        JCheckBox chkInvisible = (JCheckBox) parent.getComponent(IDC_CHECK_INVISIBLE);
        JCheckBox chkSystem = (JCheckBox) parent.getComponent(IDC_CHECK_SYSTEM);
        JCheckBox chkInUse = (JCheckBox) parent.getComponent(IDC_CHECK_INUSE);
        JCheckBox chkOverflow = (JCheckBox) parent.getComponent(IDC_CHECK_OVERFLOW);
        JSpinner spnAccess = (JSpinner) parent.getComponent(IDC_SPIN_ACCESS);

        int val = 0;
        int ori = parent.GetUserData();

        ori &= ~(FILETYPE_MASK_TRSDOS_INVISIBLE
                | FILETYPE_MASK_TRSDOS_SYSTEM
                | FILETYPE_MASK_TRSDOS_INUSE
                | FILETYPE_MASK_TRSDOS_OVERFLOW
                | 7);

        ori |= (chkInvisible.isSelected() ? FILETYPE_MASK_TRSDOS_INVISIBLE : 0);
        ori |= (chkSystem.isSelected() ? FILETYPE_MASK_TRSDOS_SYSTEM : 0);
        ori |= (chkInUse.isSelected() ? FILETYPE_MASK_TRSDOS_INUSE : 0);
        ori |= (chkOverflow.isSelected() ? FILETYPE_MASK_TRSDOS_OVERFLOW : 0);
        ori |= (((int) spnAccess.getValue()) & 7);

        attr.setFileAttr(dirItem.getBasic().getFormatTypeNumber(), val, ori);

        return true;
    }
}
