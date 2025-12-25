/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package l3diskex.ui.dirItem;

import javax.swing.BoxLayout;

import l3diskex.basicfmt.DiskBasicDirItem.DiskBasicDirItemAttr;
import l3diskex.basicfmt.diritem.DiskBasicDirItemMSDOS;
import l3diskex.basicfmt.DiskBasicError;
import l3diskex.ui.IntNameBox;
import l3diskex.ui.UiDirItem;

import static l3diskex.basicfmt.BasicCommon.DiskBasicFormatType.FORMAT_TYPE_UNKNOWN;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_ARCHIVE_MASK;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemMSDOS.TYPE_NAME_MS_ARCHIVE;
import static l3diskex.ui.IntNameBox.INTNAME_NEW_FILE;


/**
 * UiDirItemMSDOS.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2025-10-20 nsano initial version <br>
 */
public class UiDirItemMSDOS extends UiDirItem {

    DiskBasicDirItemMSDOS dirItem;

    /** Set file attributes before displaying dialog */
    public void setFileTypeForAttrDialog(int show_flags, String name, int[] file_type_1, int[] file_type_2) {
        int ft1 = file_type_1[0];
        // file_type_2 is not used in the MS-DOS base class, so we ignore it here

        if ((show_flags & INTNAME_NEW_FILE) != 0) {
            // When importing from external
            ft1 = dirItem.convFileTypeFromFileName(name);
        }
        if ((show_flags & IntNameBox.INTNAME_IMPORT_INTERNAL) != 0) {
            // When importing from internal
            ft1 |= FILE_TYPE_ARCHIVE_MASK.getValue();
        }
        file_type_1[0] = ft1;
    }

    /// Create layout for attribute part in dialog
    // Assuming BoxLayout, IntNameBox, wxBoxLayout, DimensionrFlags are UI-related classes.
    // The translation will focus on the logic and structure, representing UI elements conceptually.
    public Object CreateControlsSubForAttrDialog(IntNameBox parent, int show_flags, Object sizer, Object flags, int file_type_1) {
        // Stub implementation for UI creation
        Object staType1 = new Object(); // Represents BoxLayout
        // ... UI creation logic ...
        return staType1;
    }

    /** Set attribute */
    public void SetAttrSubInAttrDialog(IntNameBox parent, DiskBasicDirItemAttr attr) {
        int val = 0;

        for (int i = 0; i <= TYPE_NAME_MS_ARCHIVE; i++) {
            // Assume parent.FindWindow returns the control
            // and that CheckBox analog has a getValue()
            // Object chkAttr = parent.FindWindow(IntNameBox.IDC_CHECK_ATTR + i);
            // if (((CheckBox)chkAttr).getValue()) {
            // 	val |= gTypeNameMS_l[i].value;
            // }
        }

        // Assuming FORMAT_TYPE_UNKNOWN is defined elsewhere
        // and DiskBasicDirItemAttr has a SetFileAttr method.
        attr.setFileAttr(FORMAT_TYPE_UNKNOWN, val, 0);
    }

    /** Create layout for attribute part in dialog */
    @Override
    public void createControlsForAttrDialog(IntNameBox parent, int show_flags, String file_path, BoxLayout sizer, Object flags) {
        // int[] is used to pass int by reference
        int[] file_type_1 = new int[] {dirItem.getFileAttr().getType()};
        int[] file_type_2 = new int[1];

        setFileTypeForAttrDialog(show_flags, file_path, file_type_1, file_type_2);

        // Create attribute check box
        CreateControlsSubForAttrDialog(parent, show_flags, sizer, flags, file_type_1[0]);
    }

    /** Set machine dependent attributes */
    @Override
    public boolean setAttrInAttrDialog(IntNameBox parent, DiskBasicDirItemAttr attr, DiskBasicError errinfo) {
        // Attribute
        SetAttrSubInAttrDialog(parent, attr);
        return true;
    }

    /** Check file name after dialog input */
    public boolean validateFileName(IntNameBox parent, String filename, StringBuilder errormsg) {
        boolean valid = true;
        String name = filename;
        // Name starting with "." cannot be set
        if (name.startsWith(".")) {
            // errormsg.set(String.format(wxGetTranslation(gDiskBasicErrorMsgs[DiskBasicError.ERRV_CANNOT_SET_NAME]), name));
            valid = false;
        }
        return valid;
    }
}
