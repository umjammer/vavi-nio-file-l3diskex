/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package l3diskex.ui.dirItem;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JTextField;

import l3diskex.basicfmt.DiskBasicDirItem.DiskBasicDirItemAttr;
import l3diskex.basicfmt.DiskBasicDirItemMZ;
import l3diskex.basicfmt.DiskBasicDirItemMZ.Globals;
import l3diskex.basicfmt.DiskBasicError;
import l3diskex.ui.IntNameBox;
import l3diskex.ui.UiDirItem;

import static l3diskex.Config.gConfig;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_MACHINE_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_READONLY_MASK;
import static l3diskex.basicfmt.DiskBasicDirItemMZ.MZConstants.DATATYPE_MZ_READ_ONLY;
import static l3diskex.basicfmt.DiskBasicDirItemMZ.MZConstants.DATATYPE_MZ_SEAMLESS;
import static l3diskex.basicfmt.DiskBasicDirItemMZ.MZConstants.DATATYPE_MZ_SEAMLESS_POS;
import static l3diskex.basicfmt.DiskBasicDirItemMZ.MZConstants.FILETYPE_MZ_BRD;
import static l3diskex.basicfmt.DiskBasicDirItemMZ.MZConstants.FILETYPE_MZ_BSD;
import static l3diskex.basicfmt.DiskBasicDirItemMZ.MZConstants.FILETYPE_MZ_BTX;
import static l3diskex.basicfmt.DiskBasicDirItemMZ.MZConstants.FILETYPE_MZ_DIR;
import static l3diskex.basicfmt.DiskBasicDirItemMZ.MZConstants.FILETYPE_MZ_OBJ;
import static l3diskex.basicfmt.DiskBasicDirItemMZ.MZConstants.FILETYPE_MZ_VOL;
import static l3diskex.basicfmt.DiskBasicDirItemMZ.MZConstants.FILETYPE_MZ_VOLSWAP;
import static l3diskex.basicfmt.DiskBasicDirItemMZ.MZConstants.TYPE_NAME_MZ_BRD;
import static l3diskex.basicfmt.DiskBasicDirItemMZ.MZConstants.TYPE_NAME_MZ_BSD;
import static l3diskex.basicfmt.DiskBasicDirItemMZ.MZConstants.TYPE_NAME_MZ_BTX;
import static l3diskex.basicfmt.DiskBasicDirItemMZ.MZConstants.TYPE_NAME_MZ_DIR;
import static l3diskex.basicfmt.DiskBasicDirItemMZ.MZConstants.TYPE_NAME_MZ_END;
import static l3diskex.basicfmt.DiskBasicDirItemMZ.MZConstants.TYPE_NAME_MZ_OBJ;
import static l3diskex.basicfmt.DiskBasicDirItemMZ.MZConstants.TYPE_NAME_MZ_UNKNOWN;
import static l3diskex.basicfmt.DiskBasicDirItemMZ.MZConstants.TYPE_NAME_MZ_VOL;
import static l3diskex.basicfmt.DiskBasicDirItemMZ.MZConstants.TYPE_NAME_MZ_VOLSWAP;


/**
 * UiDirItemMZ.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2025-10-19 nsano initial version <br>
 */
public class UiDirItemMZ extends UiDirItem {

    DiskBasicDirItemMZ dirItem;

    @Override
    public void createControlsForAttrDialog(IntNameBox parent, int show_flags, String file_path, BoxLayout sizer, Object flags) {
        int type1 = dirItem.getFileType1();
        int type2 = dirItem.getFileType2();

        parent.SetUserData((type2 << 8) | type1);

        int[] file_type_1_arr = new int[]{type1};
        int[] file_type_2_arr = new int[]{type2};
        dirItem.setFileTypeForAttrDialog(show_flags, file_path, file_type_1_arr, file_type_2_arr);
        type1 = file_type_1_arr[0];
        type2 = file_type_2_arr[0];

        int file_type_1 = dirItem.convFileType1Pos(type1);
        int file_type_2 = dirItem.convFileType2Pos(type2);

        JComboBox comType1;
        JCheckBox chkReadOnly;
        JCheckBox chkSeamless;

        BoxLayout staType1 = new BoxLayout(new BoxLayout(parent, wxID_ANY, "File Type"), wxVERTICAL); // _("File Type")

        List<String> types1 = new ArrayList<>();
        dirItem.createChoiceForAttrDialog(basic, Globals.gTypeNameMZ, TYPE_NAME_MZ_END, types1);

        final int IDC_COMBO_TYPE1 = 51;
        comType1 = new JComboBox(parent, IDC_COMBO_TYPE1, new wxDefaultPosition(), new wxDefaultSize(), types1);
        file_type_1 = dirItem.selectChoiceForAttrDialog(dirItem.getBasic(), file_type_1, TYPE_NAME_MZ_END, TYPE_NAME_MZ_UNKNOWN);
        comType1.SetSelection(file_type_1);
        staType1.Add(comType1, flags);
        sizer.Add(staType1, flags);

        BoxLayout staType4 = new BoxLayout(new BoxLayout(parent, wxID_ANY, "File Attributes"), wxVERTICAL); // _("File Attributes")
        final int IDC_CHECK_READONLY = 52;
        chkReadOnly = new JCheckBox(parent, IDC_CHECK_READONLY, "Write Protect"); // _("Write Protect")
        chkReadOnly.SetValue((file_type_2 & FILE_TYPE_READONLY_MASK.getValue()) != 0);
        staType4.Add(chkReadOnly, flags);
        final int IDC_CHECK_SEAMLESS = 53;
        chkSeamless = new JCheckBox(parent, IDC_CHECK_SEAMLESS, "Seamless"); // _("Seamless")
        chkSeamless.SetValue((file_type_2 & (DATATYPE_MZ_SEAMLESS << DATATYPE_MZ_SEAMLESS_POS)) != 0);
        chkSeamless.setEnabled(false);
        staType4.Add(chkSeamless, flags);
        sizer.Add(staType4, flags);

        // event handler (mocked)
        parent.Bind(wxEVT_CHOICE, null, parent, IDC_COMBO_TYPE1); // Mock binding
    }

    @Override
    public void initializeForAttrDialog(IntNameBox parent, int show_flags, int[] user_data) {
        LocalDateTime tm = dirItem.getFileCreateDateTime();
        parent.IgnoreDateTime(gConfig.DoesIgnoreDateTime() || tm.Ignorable());
    }

    @Override
    public void changeTypeInAttrDialog(IntNameBox parent) {
        final int IDC_COMBO_TYPE1 = 51;
        JComboBox comType1 = (JComboBox) parent.getComponent(IDC_COMBO_TYPE1);
        JTextField txtStartAddr = (JTextField) parent.getComponent(IntNameBox.IDC_TEXT_START_ADDR);
        JTextField txtExecAddr = (JTextField) parent.getComponent(IntNameBox.IDC_TEXT_EXEC_ADDR);

        int selected_idx = 0;
        if (comType1 != null) {
            selected_idx = comType1.GetSelection();
        }

        boolean enable = (selected_idx == TYPE_NAME_MZ_OBJ);
        if (selected_idx >= TYPE_NAME_MZ_END) {
            int t = dirItem.calcSpecialFileTypeFromPos(dirItem.getBasic(), selected_idx, TYPE_NAME_MZ_END);
            enable = ((t & FILE_TYPE_MACHINE_MASK.getValue()) != 0);
        }
        if (txtStartAddr != null) {
            txtStartAddr.enable(enable);
        }
        if (txtExecAddr != null) {
            txtExecAddr.enable(enable);
        }
    }

    private int GetFileType1InAttrDialog(IntNameBox parent) {
        final int IDC_COMBO_TYPE1 = 51;
        JComboBox comType1 = (JComboBox) parent.getComponent(IDC_COMBO_TYPE1);

        return comType1 != null ? comType1.GetSelection() : 0;
    }

    private int CalcFileTypeFromPos(int pos) {
        int val = -1;
        switch(pos) {
            case TYPE_NAME_MZ_OBJ: val = FILETYPE_MZ_OBJ; break;
            case TYPE_NAME_MZ_BTX: val = FILETYPE_MZ_BTX; break;
            case TYPE_NAME_MZ_BSD: val = FILETYPE_MZ_BSD; break;
            case TYPE_NAME_MZ_BRD: val = FILETYPE_MZ_BRD; break;
            case TYPE_NAME_MZ_DIR: val = FILETYPE_MZ_DIR; break;
            case TYPE_NAME_MZ_VOL: val = FILETYPE_MZ_VOL; break;
            case TYPE_NAME_MZ_VOLSWAP: val = FILETYPE_MZ_VOLSWAP; break;
            default: val = dirItem.calcSpecialOriginalTypeFromPos(dirItem.getBasic(), pos, TYPE_NAME_MZ_END); break;
        }
        return val;
    }

    @Override
    public boolean setAttrInAttrDialog(IntNameBox parent, DiskBasicDirItemAttr attr, DiskBasicError errinfo) {
        final int IDC_CHECK_READONLY = 52;
        final int IDC_CHECK_SEAMLESS = 53;
        JCheckBox chkReadOnly = (JCheckBox)parent.getComponent(IDC_CHECK_READONLY);
        JCheckBox chkSeamless = (JCheckBox)parent.getComponent(IDC_CHECK_SEAMLESS);

        int t1 = CalcFileTypeFromPos(GetFileType1InAttrDialog(parent));
        if (t1 < 0) {
            t1 = parent.GetUserData() & 0xff;
        }
        int t2 = 0;
        t2 |= chkReadOnly.isSelected() ? DATATYPE_MZ_READ_ONLY : 0;
        t2 |= chkSeamless.isSelected() ? DATATYPE_MZ_SEAMLESS : 0;

        attr.setFileAttr(dirItem.getBasic().getFormatTypeNumber(), 0, (t2 << 8) | t1);

        return true;
    }

    @Override
    public boolean isFileValidSize(IntNameBox parent, int size, int[] limit) {
        int limit_size = 0xffff;
        int file_type1 = GetFileType1InAttrDialog(parent);
        if (file_type1 == TYPE_NAME_MZ_BRD) {
            limit_size = (128 * 256 * 16 - 1);
        }
        if (limit != null) limit[0] = limit_size;

        return (size <= limit_size);
    }
}
