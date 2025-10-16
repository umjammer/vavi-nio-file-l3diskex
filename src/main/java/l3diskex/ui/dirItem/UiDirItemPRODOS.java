/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package l3diskex.ui.dirItem;

import java.util.ResourceBundle;

import l3diskex.basicfmt.DiskBasicDirItem.DiskBasicDirItemAttr;
import l3diskex.basicfmt.DiskBasicDirItemProDOS;
import l3diskex.basicfmt.DiskBasicError;
import l3diskex.ui.IntNameBox;
import l3diskex.ui.UiDirItem;

import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_DIRECTORY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_VOLUME_MASK;
import static l3diskex.basicfmt.DiskBasicDirItemProDOS.FILETYPE_MASK_PRODOS_ACCESS_ALL;
import static l3diskex.basicfmt.DiskBasicDirItemProDOS.FILETYPE_MASK_PRODOS_DIR;
import static l3diskex.basicfmt.DiskBasicDirItemProDOS.FILETYPE_MASK_PRODOS_SEEDING;
import static l3diskex.basicfmt.DiskBasicDirItemProDOS.FILETYPE_MASK_PRODOS_SUBDIR;
import static l3diskex.basicfmt.DiskBasicDirItemProDOS.FILETYPE_MASK_PRODOS_SUBVOL;
import static l3diskex.basicfmt.DiskBasicDirItemProDOS.FILETYPE_MASK_PRODOS_VOLUME;
import static l3diskex.basicfmt.DiskBasicDirItemProDOS.TYPE_NAME_PRODOS_SUBDIR;
import static l3diskex.basicfmt.DiskBasicDirItemProDOS.TYPE_NAME_PRODOS_VOLUME;
import static l3diskex.ui.IntNameBox.INTNAME_NEW_FILE;


/**
 * UiDirItemPRODOS.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2025-10-19 nsano initial version <br>
 */
public class UiDirItemPRODOS extends UiDirItem {

    private static final ResourceBundle rb = ResourceBundle.getBundle("messages");

    DiskBasicDirItemProDOS dirItem;

    // @name プロパティダイアログ用

    // Private helper from CPP
    private void setFileTypeForAttrDialog(int showFlags, String name, int[] fileType1, int[] fileType2) {
        if ((showFlags & INTNAME_NEW_FILE) != 0) {
            fileType1[0] = FILETYPE_MASK_PRODOS_SEEDING << 8 | FILETYPE_MASK_PRODOS_ACCESS_ALL;
            fileType2[0] = dirItem.convOriginalTypeFromFileName(name);
        }
    }

    // @Override
    public void createControlsForAttrDialog(IntNameBox parent, int showFlags, String filePath, Object sizer, Object flags) {
        int[] file_type_1 = {dirItem.getFileType1() << 8 | dirItem.getFileType3()};
        int[] file_type_2 = {dirItem.getFileType2()};

        setFileTypeForAttrDialog(showFlags, filePath, file_type_1, file_type_2);

        int file_type_3 = file_type_1[0] & 0xff;
        file_type_1[0] >>= 8;

        // wxChoice comType1, wxComboBox comType2, wxTextCtrl txtType4, wxTextCtrl txtType5 are local variables used for GUI control references

        // ... GUI creation logic omitted for brevity and reliance on non-standard types ...
    }

    // @Override
    @Override
    public void initializeForAttrDialog(IntNameBox parent, int showFlags, int[] userData) {
        if ((showFlags & INTNAME_NEW_FILE) != 0) {
            // wxComboBox comType2 = (wxComboBox)parent.FindWindow(IDC_COMBO_TYPE2);
            // if (comType2 == null) return;

            // int type2pos = comType2.GetSelection();
            // switch(type2pos) {
            // case TYPE_NAME_PRODOS_BAS:
            //     parent.SetStartAddress(0x801);
            //     break;
            // }
        }
    }

    // @Override
    @Override
    public void changeTypeInAttrDialog(IntNameBox parent) {
        // wxChoice comType1 = (wxChoice)parent.FindWindow(IDC_COMBO_TYPE1);
        // if (comType1 == null) return;
        // wxComboBox comType2 = (wxComboBox)parent.FindWindow(IDC_COMBO_TYPE2);
        // if (comType2 == null) return;

        // int type1pos = comType1.GetSelection();
        // int type2pos = comType2.GetSelection();

        // if (type1pos != TYPE_NAME_PRODOS_VOLUME) {
        //     if (type2pos == TYPE_NAME_PRODOS_DIR) {
        //         comType1.SetSelection(TYPE_NAME_PRODOS_SUBDIR);
        //     } else {
        //         comType1.SetSelection(TYPE_NAME_PRODOS_FILE);
        //     }
        // }
    }

    // @Override
    @Override
    public boolean setAttrInAttrDialog(IntNameBox parent, DiskBasicDirItemAttr attr, DiskBasicError errinfo) {
        // Requires wxWidgets components (omitted)

        int val = 0;
        int ori = 0;

        int type1pos = 0; // Placeholder
        int type2pos = 0; // Placeholder

        int type1 = 0;
        int type2 = 0;
        int type3 = 0;
        int type4 = 0;
        int version = 0;

        switch (type1pos) {
            case TYPE_NAME_PRODOS_VOLUME:
                val = FILE_TYPE_VOLUME_MASK.getValue();
                if (dirItem.mDirGroupNum <= 2) {
                    type1 = FILETYPE_MASK_PRODOS_VOLUME;
                } else {
                    type1 = FILETYPE_MASK_PRODOS_SUBVOL;
                }
                break;
            case TYPE_NAME_PRODOS_SUBDIR:
                val = FILE_TYPE_DIRECTORY_MASK.getValue();
                type1 = FILETYPE_MASK_PRODOS_SUBDIR;
                type2 = FILETYPE_MASK_PRODOS_DIR;
                break;
            default:
                type1 = dirItem.getFileType1();
                // Complex type2 logic involving combo box selection (omitted)
                break;
        }

        // ACCESS logic involving checkboxes (omitted)

        // AUX_TYPE and VERSION logic involving text controls (omitted)

        ori = (type1 << 16) | (type2 << 8) | type3;

        attr.setFileAttr(dirItem.getBasic().getFormatTypeNumber(), val, ori, type4, version);

        return true;
    }
}
