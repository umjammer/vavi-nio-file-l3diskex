/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package l3diskex.ui;

import java.util.Map;
import javax.swing.BoxLayout;

import l3diskex.basicfmt.DiskBasicDirItem.DiskBasicDirItemAttr;
import l3diskex.basicfmt.DiskBasicError;


/**
 * UiDirItem.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2025-10-19 nsano initial version <br>
 */
public abstract class UiDirItem {

    public void createControlsForAttrDialog(IntNameBox parent, int showFlags, String filePath, BoxLayout sizer, Object flags) {
    }

    public void initializeForAttrDialog(IntNameBox parent, int showFlags, int[] userData) {
    }

    public void changeTypeInAttrDialog(IntNameBox parent) {
    }

    public boolean setAttrInAttrDialog(IntNameBox parent, DiskBasicDirItemAttr attr, DiskBasicError errinfo) {
        return true;
    }

    public boolean isFileValidSize(IntNameBox parent, int size, int[] limit) {
        return true;
    }

    public boolean validateFileName(IntNameBox parent, String filename, String[] errormsg) {
        return true;
    }

    public int getOptionalNameInAttrDialog(IntNameBox parent) {
        return 0;
    }

    public void comittedAttrInAttrDialog(IntNameBox parent, boolean status) {
    }

    public int getEndAddressInAttrDialog(IntNameBox parent) {
//        return getEndAddress();
    }

    public boolean isEndAddressEditableInAttrDialog(IntNameBox parent) {
//        return isAddressEditable();
    }

    public void setCommonDataInAttrDialog(Map<String, Object> vals) {
//        vals.put("num", num);
//        vals.put("position", position);
//        vals.put("flags", flags);
//        vals.put("external_attr", externalAttr);
    }
}
