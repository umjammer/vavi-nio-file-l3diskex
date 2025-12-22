/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package l3diskex.ui.dirItem;

import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JTextField;
import javax.swing.JWindow;

import l3diskex.basicfmt.DiskBasicDirItem.DiskBasicDirItemAttr;
import l3diskex.basicfmt.diritem.DiskBasicDirItemMZFDOS;
import l3diskex.basicfmt.diritem.DiskBasicDirItemMZFDOS.enTypeNameMZFDOS;
import l3diskex.basicfmt.diritem.DiskBasicDirItemMZFDOS.en_file_type_mz_fdos;
import l3diskex.basicfmt.DiskBasicError;
import l3diskex.ui.IntNameBox;
import l3diskex.ui.UiDirItem;

import static l3diskex.basicfmt.diritem.DiskBasicDirItemMZFDOS.enTypeNameMZFDOS.TYPE_NAME_MZ_FDOS_OBJ;


/**
 * UiDirItemMZFDOS.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2025-10-19 nsano initial version <br>
 */
public class UiDirItemMZFDOS extends UiDirItem {

    DiskBasicDirItemMZFDOS dirItem;

    // Set file attributes before displaying dialog
    private void SetFileTypeForAttrDialog(int show_flags, String name, int[] file_type_1, int[] file_type_2) {
        // INTNAME_NEW_FILE is a constant not defined here. Assuming its value.
        final int INTNAME_NEW_FILE = 1;
        if ((show_flags & INTNAME_NEW_FILE) != 0) {
            // When importing from external
            file_type_1[0] = dirItem.convOriginalTypeFromFileName(name);
            file_type_2[0] = file_type_1[0] >> 8;
            file_type_1[0] &= 0xff;
        }
    }

    // Create layout for attribute part in dialog
    public void CreateControlsForAttrDialog(IntNameBox parent, int show_flags, String file_path, BoxLayout sizer, Object flags) {
        int type1 = dirItem.getFileType1();
        int type2 = dirItem.getFileType2();

        parent.SetUserData(type2 << 8 | type1);

        int[] file_type_1 = {dirItem.convFileType1Pos(type1)};
        int[] file_type_2 = {type2};

        JComboBox comType1 = null; // Placeholder
        JTextField txtAttr1 = null; // Placeholder

        SetFileTypeForAttrDialog(show_flags, file_path, file_type_1, file_type_2);

        // DimensionrFlags expand = DimensionrFlags().Expand(); // Placeholder
        // wxGridSizer gszr = new wxGridSizer(1, 2, 4, 4); // Placeholder

        // BoxLayout staType1 = new BoxLayout(new wxStaticBox(parent, wxID_ANY, _("File Type")), wxVERTICAL); // Placeholder

        // ArrayList<String> types1 = null; // Placeholder
        // CreateChoiceForAttrDialog(basic, gTypeNameMZFDOS, TYPE_NAME_MZ_FDOS_END, types1); // Placeholder

        // comType1 = new wxChoice(parent, IDC_COMBO_TYPE1, wxDefaultPosition, wxDefaultSize, types1); // Placeholder
        // file_type_1[0] = basic.SelectChoiceForAttrDialog(basic, file_type_1[0], TYPE_NAME_MZ_FDOS_END, TYPE_NAME_MZ_FDOS_UNKNOWN); // Placeholder
        // comType1.SetSelection(file_type_1[0]);
        // staType1.Add(comType1, expand);
        // gszr.Add(staType1, expand);

        // BoxLayout staType2 = new BoxLayout(new wxStaticBox(parent, wxID_ANY, _("File Attributes")), wxVERTICAL); // Placeholder

        // byte attr1[4]; // Placeholder
        // attr1[0] = (file_type_2[0] >> 8) & 0xff;
        // attr1[1] = (file_type_2[0]) & 0xff;
        // attr1[2] = 0;
        // txtAttr1 = new wxTextCtrl(parent, IDC_TEXT_ATTR1, attr1, wxDefaultPosition, wxDefaultSize, wxTE_READONLY); // Placeholder
        // staType2.Add(txtAttr1, expand);
        // gszr.Add(staType2, expand);

        // sizer.Add(gszr, flags); // Placeholder

        // event handler
        // parent.Bind(wxEVT_CHOICE, parent.OnChangeType1, parent, IDC_COMBO_TYPE1); // Placeholder
    }

    // Callback called when attribute is changed
    public void ChangeTypeInAttrDialog(IntNameBox parent) {
        JComboBox comType1 = null; // (wxChoice)parent.FindWindow(IDC_COMBO_TYPE1); // Placeholder

        int selected_idx = 0;
        if (comType1 != null) {
            // selected_idx = comType1.GetSelection(); // Placeholder
        }

        int TYPE_NAME_MZ_FDOS_OBJ_VAL = TYPE_NAME_MZ_FDOS_OBJ.ordinal();
        int TYPE_NAME_MZ_FDOS_SYS_VAL = enTypeNameMZFDOS.TYPE_NAME_MZ_FDOS_SYS.ordinal();
        int TYPE_NAME_MZ_FDOS_END_VAL = enTypeNameMZFDOS.TYPE_NAME_MZ_FDOS_END.ordinal();
        final int FILE_TYPE_MACHINE_MASK_VAL = 0x02;
        final int FILE_TYPE_SYSTEM_MASK_VAL = 0x04;


        boolean enable = (selected_idx == TYPE_NAME_MZ_FDOS_OBJ_VAL || selected_idx == TYPE_NAME_MZ_FDOS_SYS_VAL);
        if (selected_idx >= TYPE_NAME_MZ_FDOS_END_VAL) {
            int t = dirItem.calcSpecialFileTypeFromPos(dirItem.getBasic(), selected_idx, TYPE_NAME_MZ_FDOS_END_VAL);
            enable = (((t & FILE_TYPE_MACHINE_MASK_VAL) != 0) || ((t & FILE_TYPE_SYSTEM_MASK_VAL) != 0));
        }
        parent.enableStartAddress(enable);
        parent.EnableExecuteAddress(enable);
    }

    // Get attribute 1
    public int GetFileType1InAttrDialog(IntNameBox parent) {
        JComboBox comType1 = null; // (wxChoice)parent.FindWindow(IDC_COMBO_TYPE1); // Placeholder

        // return comType1.GetSelection(); // Placeholder
        return 0;
    }

    // Get attribute 2
    public int GetFileType2InAttrDialog(IntNameBox parent) {
        JTextField txtAttr1 = null; // (wxTextCtrl)parent.FindWindow(IDC_TEXT_ATTR1); // Placeholder

        // wxCharBuffer buf = txtAttr1.GetValue().To8BitData(); // Placeholder
        String buf = "0S";
        int attr1 = 0x3053;    // "0S"
        if (buf.length() == 2) {
            attr1 = buf.charAt(0);
            attr1 <<= 8;
            attr1 |= buf.charAt(1);
        }
        return attr1;
    }

    // Return attribute from list position (for property dialog)
    private int CalcFileTypeFromPos(int pos) {
        int val = 0;
        int TYPE_NAME_MZ_FDOS_OBJ_VAL = TYPE_NAME_MZ_FDOS_OBJ.ordinal();
        int TYPE_NAME_MZ_FDOS_GRH_VAL = enTypeNameMZFDOS.TYPE_NAME_MZ_FDOS_GRH.ordinal();
        int TYPE_NAME_MZ_FDOS_END_VAL = enTypeNameMZFDOS.TYPE_NAME_MZ_FDOS_END.ordinal();

        if (TYPE_NAME_MZ_FDOS_OBJ_VAL <= pos && pos <= TYPE_NAME_MZ_FDOS_GRH_VAL) {
            val = pos;
        } else {
            val = dirItem.calcSpecialOriginalTypeFromPos(dirItem.getBasic(), pos, TYPE_NAME_MZ_FDOS_END_VAL);
        }
        return val;
    }

    // Set machine dependent attributes
    public boolean SetAttrInAttrDialog(IntNameBox parent, DiskBasicDirItemAttr attr, DiskBasicError errinfo) {
        int val = GetFileType1InAttrDialog(parent);

        int t1 = CalcFileTypeFromPos(val);
        if (t1 < 0) {
            t1 = parent.GetUserData() & 0xff;
        }

        t1 |= (GetFileType2InAttrDialog(parent) << 8);

        attr.setFileAttr(dirItem.getBasic().getFormatTypeNumber(), 0, t1);

        return true;
    }

    // Process attribute values
    public boolean ProcessAttr(DiskBasicDirItemAttr attr, DiskBasicError errinfo) {
        int t1 = (attr.getFileOriginAttr(0) & 0xff);
        int FILETYPE_MZ_FDOS_OBJ_VAL = en_file_type_mz_fdos.FILETYPE_MZ_FDOS_OBJ.getValue();
        int FILETYPE_MZ_FDOS_SYS_VAL = en_file_type_mz_fdos.FILETYPE_MZ_FDOS_SYS.getValue();

        if (t1 != FILETYPE_MZ_FDOS_OBJ_VAL && t1 != FILETYPE_MZ_FDOS_SYS_VAL) {
            // Address is fixed except for binary
            attr.setStartAddress(0);
            attr.setExecuteAddress(0xffff);
        }
        return true;
    }

    // Is file size appropriate?
    public boolean IsFileValidSize(JWindow parent, int size, int[] limit) {
        return true;
    }

    // Check file name after dialog input
    public boolean ValidateFileName(JWindow parent, String filename, String[] errormsg) {
        // Empty is NG
        if (filename.isEmpty()) {
            errormsg[0] = "File name is empty"; // wxGetTranslation(gDiskBasicErrorMsgs[DiskBasicError::ERR_FILENAME_EMPTY]); // Placeholder
            return false;
        }
        return true;
    }

    // Set extended attribute associated with file name
    public int GetOptionalNameInAttrDialog(IntNameBox parent) {
        int val = GetFileType1InAttrDialog(parent);
        if (val >= 0) {
            // val = gTypeNameMZFDOS[val].value; // Placeholder
            val = 0;
        } else {
            val = 0;
        }
        return val;
    }
}
