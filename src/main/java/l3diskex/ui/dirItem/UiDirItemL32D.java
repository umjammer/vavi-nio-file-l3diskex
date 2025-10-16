/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package l3diskex.ui.dirItem;

import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JComponent;

import l3diskex.basicfmt.DiskBasicDirItem.DiskBasicDirItemAttr;
import l3diskex.basicfmt.DiskBasicDirItemL31S;
import l3diskex.basicfmt.DiskBasicDirItemL32D;
import l3diskex.basicfmt.DiskBasicDirItemMAGICAL;
import l3diskex.basicfmt.DiskBasicDirItemMAGICAL.en_data_type_magical;
import l3diskex.basicfmt.DiskBasicDirItemMAGICAL.en_file_type_magical;
import l3diskex.basicfmt.DiskBasicDirItemMAGICAL.en_type_name_magical_1;
import l3diskex.basicfmt.DiskBasicDirItemMAGICAL.en_type_name_magical_2;
import l3diskex.basicfmt.DiskBasicDirItemMAGICAL.en_type_name_magical_3;
import l3diskex.basicfmt.DiskBasicError;
import l3diskex.ui.IntNameBox;
import l3diskex.ui.UiDirItem;

import static l3diskex.basicfmt.DiskBasicDirItemFAT8.TypeNames.ATTR_DIALOG_IDC_RADIO_TYPE2;
import static l3diskex.basicfmt.DiskBasicDirItemFAT8.TypeNames.TYPE_NAME_2_ASCII;
import static l3diskex.basicfmt.DiskBasicDirItemFAT8.TypeNames.TYPE_NAME_2_RANDOM;
import static l3diskex.basicfmt.DiskBasicDirItemMAGICAL.gTypeNameMAGICALMap;
import static l3diskex.basicfmt.DiskBasicDirItemMAGICAL.gTypeNameMAGICAL_1;
import static l3diskex.basicfmt.DiskBasicDirItemMAGICAL.gTypeNameMAGICAL_2;
import static l3diskex.basicfmt.DiskBasicDirItemMAGICAL.gTypeNameMAGICAL_3;


/**
 * UiDirItemL32D.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2025-10-19 nsano initial version <br>
 */
public class UiDirItemL32D extends UiDirItem {

    DiskBasicDirItemL32D dirItem;

    /*-------*/
    /*  Set file type for attribute dialog                                     */
    /*-------*/

    @Override
    protected void setFileTypeForAttrDialog(int showFlags, String name,
                                            int[] fileType1, int[] fileType2) {
        if (fileType2[0] == TYPE_NAME_2_RANDOM) {
            // Copy from 1S to 2D – treat random access data as ASCII
            fileType2[0] = TYPE_NAME_2_ASCII;
        }
        // Set attributes from extension
        super.setFileTypeForAttrDialog(showFlags, name, fileType1, fileType2);
    }

    /*-------*/
    /*  Create attribute controls in dialog                                    */
    /*-------*/

    @Override
    public void createControlsForAttrDialog(IntNameBox parent,
                                            int showFlags, String filePath,
                                            BoxLayout sizer, Object flags) {
        super.createControlsForAttrDialog(parent, showFlags, filePath, sizer, flags);

        // Hide random access option
        ButtonGroup radType2 = (ButtonGroup)parent.getComponent(ATTR_DIALOG_IDC_RADIO_TYPE2);
        radType2.show(TYPE_NAME_2_RANDOM, false);
    }
}
