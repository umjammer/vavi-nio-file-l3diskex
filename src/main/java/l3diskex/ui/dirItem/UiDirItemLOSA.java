/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package l3diskex.ui.dirItem;

import javax.swing.BoxLayout;
import javax.swing.JCheckBox;

import l3diskex.basicfmt.DiskBasicDirItem.DiskBasicDirItemAttr;
import l3diskex.basicfmt.diritem.DiskBasicDirItemMSDOS;
import l3diskex.basicfmt.DiskBasicError;
import l3diskex.ui.IntNameBox;
import l3diskex.ui.UiDirItem;

import static l3diskex.basicfmt.diritem.DiskBasicDirItemLOSA.FILE_TYPE_LOSA_BINARY;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemLOSA.TYPE_NAME_LOSA_BINARY;


/**
 * UiDirItemLOSA.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2025-10-20 nsano initial version <br>
 */
public class UiDirItemLOSA extends UiDirItem {

    DiskBasicDirItemMSDOS dirItem;

    /* ------------------------------------------------------------------ */
    /* --- Property dialog helpers --------------------------------------- */
    /* ------------------------------------------------------------------ */
    @Override
    public void createControlsForAttrDialog(IntNameBox parent,
                                            int showFlags,
                                            String filePath,
                                            BoxLayout sizer,
                                            Object flags) {
        int fileType1 = dirItem.getFileAttr().getType();
        int fileType2 = dirItem.getFileType2();

        // SetFileTypeForAttrDialog – omitted
        BoxLayout box = dirItem.CreateControlsSubForAttrDialog(parent, showFlags, sizer, flags, fileType1);

        // Create the check box for the binary type
        JCheckBox chkLAbin = new JCheckBox(parent, 61, TYPE_NAME_LOSA_BINARY);
        chkLAbin.setValue(fileType2 == FILE_TYPE_LOSA_BINARY);

        BoxLayout hbox = new BoxLayout(HORIZONTAL);
        hbox.add(chkLAbin, flags);
        box.add(hbox);
    }

    @Override
    public boolean setAttrInAttrDialog(IntNameBox parent,
                                       DiskBasicDirItemAttr attr,
                                       DiskBasicError errInfo) {
        // SetAttrSubInAttrDialog – omitted
        dirItem.SetAttrSubInAttrDialog(parent, attr);

        JCheckBox chkLAbin = (JCheckBox) parent.getComponent(61);
        attr.setFileOriginAttr(chkLAbin.isSelected() ? (FILE_TYPE_LOSA_BINARY << 16) : 0);
        return true;
    }
}
