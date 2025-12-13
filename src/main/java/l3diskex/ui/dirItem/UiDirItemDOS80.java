/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package l3diskex.ui.dirItem;

import javax.swing.BoxLayout;

import l3diskex.basicfmt.BasicCommon.KeyValArray;
import l3diskex.basicfmt.DiskBasicDirItem.DiskBasicDirItemAttr;
import l3diskex.basicfmt.diritem.DiskBasicDirItemDOS80;
import l3diskex.basicfmt.DiskBasicError;
import l3diskex.ui.IntNameBox;
import l3diskex.ui.UiDirItem;


/**
 * UiDirItemDOS80.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2025-10-20 nsano initial version <br>
 */
public class UiDirItemDOS80 extends UiDirItem {

    DiskBasicDirItemDOS80 dirItem;

    /**
     * GUI / dialog helper methods – kept as placeholders
     **/
    @Override
    public void createControlsForAttrDialog(IntNameBox parent,
                                            int showFlags,
                                            String filePath,
                                            BoxLayout sizer,
                                            Object flags) {
        // Implementation would create GUI controls, similar to C++.
        // This placeholder keeps the original method signature.
    }

    @Override
    public void initializeForAttrDialog(IntNameBox parent,
                                        int showFlags,
                                        int[] userData) {
        // Empty – original code does nothing
    }

    @Override
    public void changeTypeInAttrDialog(IntNameBox parent) {
        // GUI callback – see C++ source for details
    }

    @Override
    public boolean setAttrInAttrDialog(IntNameBox parent,
                                       DiskBasicDirItemAttr attr,
                                       DiskBasicError err) {
        // Validate dialog input and update attr
        return true;
    }

    public void committedAttrInAttrDialog(IntNameBox parent, boolean status) {
        // Post‑commit logic
    }

    @Override
    public boolean isEndAddressEditableInAttrDialog(IntNameBox parent) {
        // Determine if end address field should be editable
        return true;
    }

    //
    // ダイアログ用
    //

    @Override
    public void setInternalDataInAttrDialog(KeyValArray vals) {
        vals.add("NAME", dirItem.data.data().name, dirItem.data.data().name.length);

        if (!dirItem.data2.isValid()) return;

        // TODO serialize?
//        vals.add("GRPS", dirItem.data2.data().grps, dirItem.data2.data().grps.length);
//        vals.add("RESERVED", dirItem.data2.data().reserved, dirItem.data2.data().reserved));
    }
}
