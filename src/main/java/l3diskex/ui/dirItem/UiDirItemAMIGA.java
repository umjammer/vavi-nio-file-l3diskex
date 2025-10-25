/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package l3diskex.ui.dirItem;

import javax.swing.BoxLayout;

import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicDirItem.DiskBasicDirItemAttr;
import l3diskex.basicfmt.DiskBasicDirItemAmiga;
import l3diskex.ui.IntNameBox;
import l3diskex.ui.UiDirItem;


/**
 * UiDirItemAMIGA.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2025-10-20 nsano initial version <br>
 */
public class UiDirItemAMIGA extends UiDirItem {

    DiskBasicDirItemAmiga dirItem;

    public void CreateControlsForAttrDialog(IntNameBox parent, int show_flags, String file_path, BoxLayout sizer, Object flags) {
    } // wxString -> String

    public void InitializeForAttrDialog(IntNameBox parent, int show_flags, int[] user_data) {
    } // int * -> int[]

    public void ChangeTypeInAttrDialog(IntNameBox parent) {
    }

    public boolean SetAttrInAttrDialog(IntNameBox parent, DiskBasicDirItemAttr attr, DiskBasic basic) {
        return false;
    }
}
