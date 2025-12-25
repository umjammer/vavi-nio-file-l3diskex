/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package l3diskex.ui.dirItem;

import javax.swing.JComboBox;

import l3diskex.basicfmt.diritem.DiskBasicDirItemCDOS;
import l3diskex.ui.IntNameBox;
import l3diskex.ui.UiDirItem;

import static l3diskex.basicfmt.diritem.DiskBasicDirItemSDOS.IDC_COMBO_TYPE1;


/**
 * UiDirItemCDOS.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2025-10-20 nsano initial version <br>
 */
public class UiDirItemCDOS extends UiDirItem {

    DiskBasicDirItemCDOS dirItem;

    /** Get attribute 1 */
    private int GetFileType1InAttrDialog(IntNameBox parent) {
        Object obj = parent.getComponent(IDC_COMBO_TYPE1);
        if (obj instanceof JComboBox) {
            return ((JComboBox<?>) obj).getSelectedIndex();
        }
        return -1;
    }

    /** Set file attributes before displaying dialog on import */
    private void SetFileTypeForAttrDialog(int show_flags, String name, int[] file_type_1, int[] file_type_2) {
        // empty in original
    }

    @Override
    public boolean isFileValidSize(IntNameBox parent, int size, int[] limit) {
        int limit_size = 0xffff;
        if (limit != null) limit[0] = limit_size;
        return size <= limit_size;
    }
}
