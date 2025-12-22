/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package l3diskex.ui.dirItem;

import java.util.ResourceBundle;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;

import l3diskex.basicfmt.DiskBasicDirItem.DiskBasicDirItemAttr;
import l3diskex.basicfmt.diritem.DiskBasicDirItemMSX;
import l3diskex.basicfmt.DiskBasicError;
import l3diskex.ui.IntNameBox;
import l3diskex.ui.UiDirItem;

import static l3diskex.basicfmt.BasicCommon.DiskBasicFormatType.FORMAT_TYPE_UNKNOWN;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_HIDDEN_MASK;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemMSX.TYPE_NAME_MS_HIDDEN;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemMSX.gTypeNameMS_l;
import static l3diskex.ui.dirItem.UiDirItemTFDOS.IDC_CHECK_HIDDEN;


/**
 * UiDirItemMSX.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2025-10-20 nsano initial version <br>
 */
public class UiDirItemMSX extends UiDirItem {

    private static final ResourceBundle rb = ResourceBundle.getBundle("messages");

    DiskBasicDirItemMSX dirItem;

    /* -- */
    /*  Dialog helpers                                                        */
    /* -- */

    /** Set file attributes before displaying dialog */
    @Override
    public void setFileTypeForAttrDialog(int showFlags,
                                         String name,
                                         int[] fileType1,
                                         int[] fileType2) {
        // C++ implementation is empty – keep it that way
    }

    /** Create layout for attribute part in dialog */
    @Override
    public void createControlsForAttrDialog(IntNameBox parent,
                                            int showFlags,
                                            String filePath,
                                            BoxLayout sizer,
                                            Object flags) {

        int fileType1 = dirItem.getFileAttr().getType();
        int fileType2 = 0;

        setFileTypeForAttrDialog(showFlags, filePath, new int[] {fileType1}, new int[] {fileType2});

        BoxLayout staType1 =
                new BoxLayout(new BoxLayout(parent, wxID_ANY, "File Attributes"), wxVERTICAL);

        JCheckBox chkHidden = new JCheckBox(parent, IDC_CHECK_HIDDEN,
                rb.getString(gTypeNameMS_l[TYPE_NAME_MS_HIDDEN]));
        chkHidden.setValue((fileType1 & FILE_TYPE_HIDDEN_MASK.getValue()) != 0);

        staType1.add(chkHidden, flags);
        sizer.add(staType1, flags);
    }

    /** Set machine dependent attributes */
    @Override
    public boolean setAttrInAttrDialog(IntNameBox parent,
                                       DiskBasicDirItemAttr attr,
                                       DiskBasicError errinfo) {

        JCheckBox chkHidden = (JCheckBox) parent.getComponent(IDC_CHECK_HIDDEN);
        int val = chkHidden.isSelected() ? FILE_TYPE_HIDDEN_MASK.getValue() : 0;
        attr.setFileAttr(FORMAT_TYPE_UNKNOWN, val, 0);
        return true;
    }
}
