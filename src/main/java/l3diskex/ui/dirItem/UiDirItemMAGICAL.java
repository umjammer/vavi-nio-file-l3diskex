/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package l3diskex.ui.dirItem;

import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComponent;

import l3diskex.basicfmt.DiskBasicDirItem.DiskBasicDirItemAttr;
import l3diskex.basicfmt.DiskBasicDirItemMAGICAL;
import l3diskex.basicfmt.DiskBasicDirItemMAGICAL.en_data_type_magical;
import l3diskex.basicfmt.DiskBasicDirItemMAGICAL.en_file_type_magical;
import l3diskex.basicfmt.DiskBasicDirItemMAGICAL.en_type_name_magical_1;
import l3diskex.basicfmt.DiskBasicDirItemMAGICAL.en_type_name_magical_2;
import l3diskex.basicfmt.DiskBasicDirItemMAGICAL.en_type_name_magical_3;
import l3diskex.basicfmt.DiskBasicError;
import l3diskex.ui.IntNameBox;
import l3diskex.ui.UiDirItem;

import static l3diskex.basicfmt.DiskBasicDirItemMAGICAL.gTypeNameMAGICALMap;
import static l3diskex.basicfmt.DiskBasicDirItemMAGICAL.gTypeNameMAGICAL_1;
import static l3diskex.basicfmt.DiskBasicDirItemMAGICAL.gTypeNameMAGICAL_2;
import static l3diskex.basicfmt.DiskBasicDirItemMAGICAL.gTypeNameMAGICAL_3;


/**
 * UiDirItemMAGICAL.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2025-10-19 nsano initial version <br>
 */
public class UiDirItemMAGICAL extends UiDirItem {

    static final ResourceBundle rb = ResourceBundle.getBundle("message");

    DiskBasicDirItemMAGICAL dirItem;

    private static final int IDC_COMBO_TYPE1 = 51;
    private static final int IDC_COMBO_MEMBANK = 52;
    private static final int IDC_CHECK_READONLY = 53;
    private static final int IDC_CHECK_HIDDEN = 54;
    private static final int IDC_CHECK_SYSTEM = 55;
    private static final int IDC_CHECK_SUPER = 56;

    @Override
    public void createControlsForAttrDialog(IntNameBox parent, int show_flags, String file_path, BoxLayout sizer, Object flags) {
        int t1 = dirItem.getFileType1();
        int t2 = dirItem.getFileType2();

        if ((show_flags & 0x01) != 0) { // INTNAME_NEW_FILE is assumed to be 1
            // 外部からインポート時
            // 拡張子で属性を設定する
            t1 = dirItem.convOriginalTypeFromFileName(file_path);
            t2 = t1 >> 8;
            t1 &= 0xff;
        }

        int file_type_1 = ConvFileType1Pos(t1);
        int file_type_2 = t2;
        JComponent comType1;
        JComponent comMemBank;
        JCheckBox chkReadOnly;
        JCheckBox chkHidden;
        JCheckBox chkSystem;
        JCheckBox chkSuper;

        GridLayout gszr = new GridLayout(2, 1, 1, 1); // rows, cols, vgap, hgap

        List<String> types1 = new ArrayList<>();
        for (String nv : gTypeNameMAGICAL_1.keySet()) {
            types1.add(rb.getString(nv));
        }
        BoxLayout staType1 = new BoxLayout(new wxStaticBox(parent, 0, "File Type"), 1); // wxVERTICAL = 1
        comType1 = new JCheckBox(parent, IDC_COMBO_TYPE1, types1.toArray());
        if (file_type_1 >= 0) {
            comType1.SetSelection(file_type_1);
        }
        staType1.Add(comType1, flags);
        gszr.Add(staType1, flags);

        List<String> types2 = new ArrayList<>();
        for (String s : gTypeNameMAGICAL_3) {
            if (s == null) break;
            types2.add(rb.getString(s));
        }
        BoxLayout staType2 = new BoxLayout(new wxStaticBox(parent, 0, "Memory Bank"), 1); // wxVERTICAL = 1
        comMemBank = new JCheckBox(parent, IDC_COMBO_MEMBANK, types2.toArray());
        int mem_bank = (file_type_2 & 0xf);
        if (mem_bank >= 8) mem_bank = en_type_name_magical_3.TYPE_NAME_MAGICAL_BANK_Unknown.ordinal();
        comMemBank.SetSelection(mem_bank);
        staType2.Add(comMemBank, flags);
        gszr.Add(staType2, flags);

        sizer.Add(gszr, new DimensionrFlags().Expand());

        BoxLayout staType4 = new BoxLayout(new wxStaticBox(parent, 0, "File Attributes"), 1); // wxVERTICAL = 1

        BoxLayout hszr = new BoxLayout(0); // wxHORIZONTAL = 0
        chkReadOnly = new JCheckBox(parent, IDC_CHECK_READONLY, rb.getString(gTypeNameMAGICAL_2[en_type_name_magical_2.TYPE_NAME_MAGICAL_READONLY.ordinal()]));
        chkReadOnly.SetValue((file_type_2 & en_data_type_magical.DATATYPE_MAGICAL_MASK_READONLY.ordinal()) != 0);
        hszr.Add(chkReadOnly, flags);
        chkHidden = new JCheckBox(parent, IDC_CHECK_HIDDEN, rb.getString(gTypeNameMAGICAL_2[en_type_name_magical_2.TYPE_NAME_MAGICAL_HIDDEN.ordinal()]));
        chkHidden.SetValue((file_type_2 & en_data_type_magical.DATATYPE_MAGICAL_MASK_HIDDEN.ordinal()) != 0);
        hszr.Add(chkHidden, flags);
        staType4.Add(hszr);

        hszr = new wxBoxLayout(0); // wxHORIZONTAL = 0
        chkSystem = new JCheckBox(parent, IDC_CHECK_SYSTEM, rb.getString(gTypeNameMAGICAL_2[en_type_name_magical_2.TYPE_NAME_MAGICAL_SYSTEM.ordinal()]));
        chkSystem.SetValue((file_type_2 & en_data_type_magical.DATATYPE_MAGICAL_MASK_SYSTEM.ordinal()) != 0);
        hszr.Add(chkSystem, flags);
        chkSuper = new JCheckBox(parent, IDC_CHECK_SUPER, rb.getString(gTypeNameMAGICAL_2[en_type_name_magical_2.TYPE_NAME_MAGICAL_SUPER.ordinal()]));
        chkSuper.SetValue((file_type_2 & en_data_type_magical.DATATYPE_MAGICAL_MASK_SUPER.ordinal()) != 0);
        hszr.Add(chkSuper, flags);
        staType4.Add(hszr);

        sizer.Add(staType4, flags);

        parent.SetUserData(file_type_2);
    }

    @Override
    public boolean setAttrInAttrDialog(IntNameBox parent, DiskBasicDirItemAttr attr, DiskBasicError errinfo) {
        JCheckBox comType1 = (JCheckBox) parent.getComponent(IDC_COMBO_TYPE1);
        JCheckBox comMemBank = (JCheckBox) parent.getComponent(IDC_COMBO_MEMBANK);
        JCheckBox chkReadOnly = (JCheckBox) parent.getComponent(IDC_CHECK_READONLY);
        JCheckBox chkHidden = (JCheckBox) parent.getComponent(IDC_CHECK_HIDDEN);
        JCheckBox chkSystem = (JCheckBox) parent.getComponent(IDC_CHECK_SYSTEM);
        JCheckBox chkSuper = (JCheckBox) parent.getComponent(IDC_CHECK_SUPER);

        int t1 = comType1.GetSelection();
        if (t1 >= en_type_name_magical_1.TYPE_NAME_MAGICAL_SYS.ordinal() && t1 < en_type_name_magical_1.TYPE_NAME_MAGICAL_UNKNOWN.ordinal()) {
            t1 = gTypeNameMAGICALMap[t1];
        } else {
            t1 = en_file_type_magical.FILETYPE_MAGICAL_UNKNOWN.ordinal();
        }
        int t2 = comMemBank.GetSelection();
        if (t2 == en_type_name_magical_3.TYPE_NAME_MAGICAL_BANK_Unknown.ordinal()) {
            t2 = parent.GetUserData();
        }
        t2 &= 0x0f;
        t2 |= chkReadOnly.isSelected() ? en_data_type_magical.DATATYPE_MAGICAL_MASK_READONLY.ordinal() : 0;
        t2 |= chkHidden.isSelected() ? en_data_type_magical.DATATYPE_MAGICAL_MASK_HIDDEN.ordinal() : 0;
        t2 |= chkSystem.isSelected() ? en_data_type_magical.DATATYPE_MAGICAL_MASK_SYSTEM.ordinal() : 0;
        t2 |= chkSuper.isSelected() ? en_data_type_magical.DATATYPE_MAGICAL_MASK_SUPER.ordinal() : 0;

        attr.setFileAttr(dirItem.getBasic().getFormatTypeNumber(), 0, (t2 << 8) | t1);

        return true;
    }
}
