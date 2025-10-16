/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package l3diskex.ui.dirItem;

import java.awt.Choice;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;

import l3diskex.basicfmt.DiskBasicDirItem.DiskBasicDirItemAttr;
import l3diskex.basicfmt.DiskBasicDirItemTFDOS;
import l3diskex.basicfmt.DiskBasicError;
import l3diskex.ui.IntNameBox;
import l3diskex.ui.UiDirItem;

import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_HIDDEN_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_READONLY_MASK;
import static l3diskex.basicfmt.DiskBasicDirItemTFDOS.DATATYPE_TFDOS_HIDDEN;
import static l3diskex.basicfmt.DiskBasicDirItemTFDOS.DATATYPE_TFDOS_READ_ONLY;
import static l3diskex.basicfmt.DiskBasicDirItemTFDOS.TYPE_NAME_TFDOS_DBB;
import static l3diskex.basicfmt.DiskBasicDirItemTFDOS.TYPE_NAME_TFDOS_READ_ONLY;
import static l3diskex.basicfmt.DiskBasicDirItemTFDOS.TYPE_NAME_TFDOS_TEX;
import static l3diskex.basicfmt.DiskBasicDirItemTFDOS.TYPE_NAME_TFDOS_UNKNOWN;
import static l3diskex.basicfmt.DiskBasicDirItemTFDOS.gTypeNameTFDOS;
import static l3diskex.ui.IntNameBox.INTNAME_IMPORT_INTERNAL;
import static l3diskex.ui.IntNameBox.INTNAME_NEW_FILE;


/**
 * UiDirItemTFDOS.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2025-10-19 nsano initial version <br>
 */
public class UiDirItemTFDOS extends UiDirItem {

    private static final int IDC_COMBO_TYPE1 = 51;
    public static final int IDC_CHECK_BASECOMP = 52;
    private static final int IDC_CHECK_READONLY = 53;
    public static final int IDC_CHECK_HIDDEN = 54;

    DiskBasicDirItemTFDOS dirItem;

    @Override
    public void createControlsForAttrDialog(IntNameBox parent, int show_flags, String file_path, BoxLayout sizer, Object flags) {
        int t1 = dirItem.getFileType1();
        if ((show_flags & INTNAME_NEW_FILE) != 0) {
            t1 = dirItem.convOriginalTypeFromFileName(file_path);
        }

        int file_type_1 = dirItem.convFileType1Pos(t1);
        int file_type_2 = dirItem.convFileType2Pos(t1);

        dirItem.m_show_flags = show_flags;

        StaticBoxSizer staType1 = new StaticBoxSizer(new StaticBox(parent, "File Type"), VERTICAL);

        String[] types1 = dirItem.createChoiceForAttrDialog(dirItem.getBasic(), gTypeNameTFDOS, TYPE_NAME_TFDOS_READ_ONLY);
        Choice comType1 = new Choice(parent, IDC_COMBO_TYPE1, types1);
        comType1.setSelection(file_type_1);
        staType1.add(comType1, flags);

        int chk_style = CHK_3STATE;
        if ((show_flags & INTNAME_IMPORT_INTERNAL) != 0) {
            dirItem.externalAttr = 0;
        } else if ((show_flags & INTNAME_NEW_FILE) != 0) {
            dirItem.externalAttr = 1;
        } else {
            chk_style |= CHK_ALLOW_3RD_STATE_FOR_USER;
        }
        JCheckBox chkBaseComp = new JCheckBox(parent, IDC_CHECK_BASECOMP, "Treat as BASE compatible text.", chk_style);
        chkBaseComp.setEnabled(file_type_1 == TYPE_NAME_TFDOS_TEX);
        chkBaseComp.set3StateValue(dirItem.externalAttr);
        staType1.add(chkBaseComp, flags);

        sizer.add(staType1, flags);

        StaticBoxSizer staType4 = new StaticBoxSizer(new StaticBox(parent, "File Attributes"), VERTICAL);
        JCheckBox chkReadOnly = new JCheckBox(parent, IDC_CHECK_READONLY, "Write Protect");
        chkReadOnly.setValue((file_type_2 & FILE_TYPE_READONLY_MASK.getValue()) != 0);
        staType4.add(chkReadOnly, flags);
        JCheckBox chkHidden = new JCheckBox(parent, IDC_CHECK_HIDDEN, "Hidden");
        chkHidden.setValue((file_type_2 & FILE_TYPE_HIDDEN_MASK.getValue()) != 0);
        staType4.add(chkHidden, flags);
        sizer.add(staType4, flags);

        parent.bind(EVT_CHOICE, parent::onChangeType1, IDC_COMBO_TYPE1);
    }

    @Override
    public void changeTypeInAttrDialog(IntNameBox parent) {
        Choice comType1 = (Choice)parent.getComponent(IDC_COMBO_TYPE1);
        JCheckBox chkBaseComp = (JCheckBox)parent.getComponent(IDC_CHECK_BASECOMP);
        if (comType1 != null && chkBaseComp != null) {
            int sel = comType1.getSelection();
            chkBaseComp.setEnabled(sel == TYPE_NAME_TFDOS_TEX && (dirItem.m_show_flags & INTNAME_IMPORT_INTERNAL) == 0);
        }
    }

    private int getFileType1InAttrDialog(IntNameBox parent) {
        Choice comType1 = (Choice)parent.getComponent(IDC_COMBO_TYPE1);
        return comType1.getSelection();
    }

    private int calcFileTypeFromPos(int pos) {
        int val = pos;
        if (val < TYPE_NAME_TFDOS_UNKNOWN || val > TYPE_NAME_TFDOS_DBB) {
            val = TYPE_NAME_TFDOS_TEX;
        }
        return val;
    }

    @Override
    public boolean setAttrInAttrDialog(IntNameBox parent, DiskBasicDirItemAttr attr, DiskBasicError errinfo) {
        JCheckBox chkBaseComp = (JCheckBox)parent.getComponent(IDC_CHECK_BASECOMP);
        JCheckBox chkReadOnly = (JCheckBox)parent.getComponent(IDC_CHECK_READONLY);
        JCheckBox chkHidden = (JCheckBox)parent.getComponent(IDC_CHECK_HIDDEN);

        int sel = getFileType1InAttrDialog(parent);
        int ext = 0;
        if (sel == TYPE_NAME_TFDOS_TEX) {
            ext = chkBaseComp.get3StateValue();
        }

        int origin = calcFileTypeFromPos(sel);
        origin |= chkReadOnly.isSelected() ? DATATYPE_TFDOS_READ_ONLY : 0;
        origin |= chkHidden.isSelected() ? DATATYPE_TFDOS_HIDDEN : 0;

        attr.setFileAttr(dirItem.getBasic().getFormatTypeNumber(), 0, ext << 16 | origin);

        return true;
    }

    @Override
    public boolean isFileValidSize(IntNameBox parent, int size, int[] limit) {
        int limit_size = 0xffff;
        if (limit != null) limit[0] = limit_size;
        return (size <= limit_size);
    }

    @Override
    public boolean validateFileName(IntNameBox parent, String filename, String[] errormsg) {
        return true;
    }
}
