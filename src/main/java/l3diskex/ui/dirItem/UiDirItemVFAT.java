/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package l3diskex.ui.dirItem;

import javax.swing.BoxLayout;

import l3diskex.basicfmt.DiskBasicDirItem.DiskBasicDirItemAttr;
import l3diskex.basicfmt.diritem.DiskBasicDirItemMSDOS.DiskBasicDirItemVFAT;
import l3diskex.basicfmt.DiskBasicDirItemVFAT;
import l3diskex.basicfmt.DiskBasicError;
import l3diskex.ui.IntNameBox;
import l3diskex.ui.UiDirItem;


/**
 * UiDirItemVFAT.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2025-10-19 nsano initial version <br>
 */
public class UiDirItemVFAT extends UiDirItem {

    DiskBasicDirItemVFAT dirItem;

    /** Create layout for attribute part in dialog */
    @Override
    public void createControlsForAttrDialog(IntNameBox parent, int show_flags, String file_path, BoxLayout sizer, Object flags) {
        // int[] is used to pass int by reference
        int[] file_type_1 = new int[] {dirItem.getFileAttr().getType()};
        int[] file_type_2 = new int[1];

        dirItem.setFileTypeForAttrDialog(show_flags, file_path, file_type_1, file_type_2);

        // Create attribute check box
        dirItem.createControlsSubForAttrDialog(parent, show_flags, sizer, flags, file_type_1[0]);
    }

    /** Set machine dependent attributes */
    @Override
    public boolean setAttrInAttrDialog(IntNameBox parent, DiskBasicDirItemAttr attr, DiskBasicError errinfo) {
        // Attribute
        dirItem.setAttrSubInAttrDialog(parent, attr);

        return true;
    }
}
