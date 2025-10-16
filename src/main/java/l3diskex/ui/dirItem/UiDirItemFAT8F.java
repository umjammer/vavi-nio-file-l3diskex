/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package l3diskex.ui.dirItem;

import l3diskex.Parambase;
import l3diskex.basicfmt.DiskBasicDirItemFAT8.TypeNames;
import l3diskex.basicfmt.DiskBasicDirItemFAT8F;
import l3diskex.ui.UiDirItem;

import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_ASCII_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BASIC_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BINARY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_MACHINE_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_RANDOM_MASK;
import static l3diskex.ui.IntNameBox.INTNAME_NEW_FILE;


/**
 * UiDirItemFAT8F.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2025-10-19 nsano initial version <br>
 */
public class UiDirItemFAT8F extends UiDirItem {

    DiskBasicDirItemFAT8F dirItem;

    public void SetFileTypeForAttrDialog(int show_flags, String name, int[] file_type_1, int[] file_type_2) {
        if ((show_flags & INTNAME_NEW_FILE) != 0) {
            // 外部からインポート時
            // 拡張子で属性を設定する
            String ext = dirItem.getExtension(name); // Assuming getExtension helper method
            Parambase.MyAttribute sa = dirItem.getBasic().diskBasicParam.getAttributesByExtension().findUpperCase(ext);

            if (sa == null) return;

            int ftype = sa.getType();

            // Constants like FILE_TYPE_BASIC_MASK etc. are assumed to be defined in DiskBasic or a global constants class
            if ((ftype & (FILE_TYPE_BASIC_MASK.getValue() | FILE_TYPE_BINARY_MASK.getValue())) == (FILE_TYPE_BASIC_MASK.getValue() | FILE_TYPE_BINARY_MASK.getValue())) {
                file_type_1[0] = TypeNames.TYPE_NAME_1_BASIC;
                file_type_2[0] = TypeNames.TYPE_NAME_2_BINARY;
            } else if ((ftype & (FILE_TYPE_BASIC_MASK.getValue() | FILE_TYPE_ASCII_MASK.getValue())) == (FILE_TYPE_BASIC_MASK.getValue() | FILE_TYPE_ASCII_MASK.getValue())) {
                file_type_1[0] = TypeNames.TYPE_NAME_1_BASIC;
                file_type_2[0] = TypeNames.TYPE_NAME_2_ASCII;
            } else if ((ftype & FILE_TYPE_MACHINE_MASK.getValue()) == FILE_TYPE_MACHINE_MASK.getValue()) {
                file_type_1[0] = TypeNames.TYPE_NAME_1_MACHINE;
                file_type_2[0] = TypeNames.TYPE_NAME_2_BINARY;
            } else if ((ftype & FILE_TYPE_RANDOM_MASK.getValue()) == FILE_TYPE_RANDOM_MASK.getValue()) {
                file_type_1[0] = TypeNames.TYPE_NAME_1_DATA;
                file_type_2[0] = TypeNames.TYPE_NAME_2_RANDOM;
            } else {
                file_type_1[0] = TypeNames.TYPE_NAME_1_DATA;
                file_type_2[0] = TypeNames.TYPE_NAME_2_ASCII;
            }
        }
    }

}
