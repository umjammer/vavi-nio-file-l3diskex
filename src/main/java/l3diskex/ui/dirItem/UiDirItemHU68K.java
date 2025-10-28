/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package l3diskex.ui.dirItem;

import l3diskex.basicfmt.diritem.DiskBasicDirItemHU68K;
import l3diskex.ui.UiDirItem;

import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_ARCHIVE_MASK;
import static l3diskex.ui.IntNameBox.INTNAME_IMPORT_INTERNAL;
import static l3diskex.ui.IntNameBox.INTNAME_NEW_FILE;


/**
 * UiDirItemFP.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2025-10-20 nsano initial version <br>
 */
public class UiDirItemHU68K extends UiDirItem {

    DiskBasicDirItemHU68K dirItem;

    /*------------------------------------------------------------------
     *  Override: Set file type information for the attribute dialog.
     *------------------------------------------------------------------*/
    public void SetFileTypeForAttrDialog(int show_flags,
                                         String name,
                                         int[] file_type_1,
                                         int[] file_type_2) {
        if ((show_flags & INTNAME_NEW_FILE) != 0) {
            // External import
            file_type_1[0] = FILE_TYPE_ARCHIVE_MASK.getValue();
        }
        if ((show_flags & INTNAME_IMPORT_INTERNAL) != 0) {
            // Internal import
            file_type_1[0] |= FILE_TYPE_ARCHIVE_MASK.getValue();
        }
    }
}
