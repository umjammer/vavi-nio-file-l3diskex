/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package l3diskex.ui.dirItem;

import java.util.ArrayList;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;

import l3diskex.basicfmt.DiskBasicDirItem.DiskBasicDirItemAttr;
import l3diskex.basicfmt.diritem.DiskBasicDirItemN88;
import l3diskex.basicfmt.DiskBasicError;
import l3diskex.ui.IntNameBox;
import l3diskex.ui.UiDirItem;

import static l3diskex.basicfmt.BasicCommon.DiskBasicFormatType.FORMAT_TYPE_UNKNOWN;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_ASCII_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BINARY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_ENCRYPTED_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_MACHINE_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_RANDOM_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_READONLY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_READWRITE_MASK;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemN88.TYPE_NAME_N88_1;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemN88.TYPE_NAME_N88_2;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemN88.TYPE_NAME_N88_ASCII;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemN88.TYPE_NAME_N88_BINARY;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemN88.TYPE_NAME_N88_ENCRYPTED;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemN88.TYPE_NAME_N88_MACHINE;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemN88.TYPE_NAME_N88_RANDOM;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemN88.TYPE_NAME_N88_READ_ONLY;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemN88.TYPE_NAME_N88_READ_WRITE;


/**
 * UiDirItemN88.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2025-10-19 nsano initial version <br>
 */
public class UiDirItemN88 extends UiDirItem {

    static final int IDC_RADIO_TYPE1 = 51;
    static final int IDC_CHECK_READONLY = 52;
    static final int IDC_CHECK_READWRITE = 53;
    static final int IDC_CHECK_ENCRYPT = 54;
    static final int IDC_RADIO_TYPE2 = 55;

    DiskBasicDirItemN88 dirItem;

    // C++ method: SetFileTypeForAttrDialog
    // Set file attributes before displaying dialog
    public void setFileTypeForAttrDialog(int showFlags, String name, int[] fileType1, int[] fileType2) {
        if ((showFlags & 0x01) != 0) { // Assuming INTNAME_NEW_FILE is 0x01
            // When importing from external
            fileType1[0] = dirItem.convOriginalTypeFromFileName(name);
        }
        // fileType2 is not modified for N88 in the C++ code for INTNAME_NEW_FILE.
    }

    // C++ method: CreateControlsForAttrDialog
    // Note: wxWidget classes replaced by placeholder types and methods.
    @Override
    public void createControlsForAttrDialog(IntNameBox parent, int showFlags, String filePath, BoxLayout sizer, Object flags) {
        ButtonGroup radType1;
        JCheckBox chkReadOnly;
        JCheckBox chkReadWrite;
        JCheckBox chkEncrypt;

        int fileType1 = dirItem.getFileType1();
        int fileType2 = dirItem.getFileAttr().getType();
        int[] fileType1Arr = {fileType1};
        int[] fileType2Arr = {fileType2};
        setFileTypeForAttrDialog(showFlags, filePath, fileType1Arr, fileType2Arr);
        fileType1 = fileType1Arr[0];
        fileType2 = fileType2Arr[0];

        fileType1 = dirItem.convFileType1Pos(fileType1);

        BoxLayout gszr = new BoxLayout(); // wxHORIZONTAL

        List<String> types1 = new ArrayList<>();
        for (int i = 0; TYPE_NAME_N88_1[i] != null; i++) {
            types1.add(TYPE_NAME_N88_1[i]);
        }
        // wxDefaultPosition, wxDefaultSize, wxRA_SPECIFY_ROWS are placeholders/constants
        radType1 = new ButtonGroup(parent, IDC_RADIO_TYPE1, "File Type", null, null, types1.toArray(String[]::new), 0, null); // Assuming helper method for List<String> to String[] and constants are handled
        radType1.setSelection(fileType1);
        // gszr->Add(radType1, flags);
        // Assuming gszr.add(radType1, flags) for placeholder

        // wxStaticBoxSizer *staType4 = new wxStaticBoxSizer(new wxStaticBox(parent, wxID_ANY, _("File Attributes")), wxVERTICAL);
        // Placeholder for static box sizer
        WxStaticBoxSizer staType4 = new WxStaticBoxSizer(null, null);

        chkReadOnly = new JCheckBox(parent, IDC_CHECK_READONLY, TYPE_NAME_N88_2[TYPE_NAME_N88_READ_ONLY]);
        chkReadOnly.setValue((fileType2 & FILE_TYPE_READONLY_MASK.getValue()) != 0);
        // staType4->Add(chkReadOnly, flags);
        // Assuming staType4.add(chkReadOnly, flags)

        chkReadWrite = new JCheckBox(parent, IDC_CHECK_READWRITE, TYPE_NAME_N88_2[TYPE_NAME_N88_READ_WRITE]);
        chkReadWrite.setValue((fileType2 & FILE_TYPE_READWRITE_MASK.getValue()) != 0);
        // staType4->Add(chkReadWrite, flags);
        // Assuming staType4.add(chkReadWrite, flags)

        chkEncrypt = new JCheckBox(parent, IDC_CHECK_ENCRYPT, TYPE_NAME_N88_2[TYPE_NAME_N88_ENCRYPTED]);
        chkEncrypt.setValue((fileType2 & FILE_TYPE_ENCRYPTED_MASK.getValue()) != 0);
        // staType4->Add(chkEncrypt, flags);
        // Assuming staType4.add(chkEncrypt, flags)

        // gszr->Add(staType4, flags);
        // sizer->Add(gszr);

        // event handler
        // parent->Bind(wxEVT_RADIOBOX, &IntNameBox::OnChangeType1, parent, IDC_RADIO_TYPE1);
        // Placeholder for event binding
        // parent.bind(null, null, parent, IDC_RADIO_TYPE1);
    }

    // C++ method: ChangeTypeInAttrDialog
    @Override
    public void changeTypeInAttrDialog(IntNameBox parent) {
        // Controls are retrieved by ID from the parent dialog
        ButtonGroup radType1 = (ButtonGroup) parent.getComponent(IDC_RADIO_TYPE1);
        JCheckBox chkReadOnly = (JCheckBox) parent.getComponent(IDC_CHECK_READONLY);
        JCheckBox chkReadWrite = (JCheckBox) parent.getComponent(IDC_CHECK_READWRITE);
        JCheckBox chkEncrypt = (JCheckBox) parent.getComponent(IDC_CHECK_ENCRYPT);

        int selectedIdx = 0;
        if (radType1 != null) {
            selectedIdx = radType1.getSelection();
        }

        if (chkReadOnly == null || chkReadWrite == null || chkEncrypt == null) return; // Should not happen in real app

        switch (selectedIdx) {
            case TYPE_NAME_N88_MACHINE:
                // machine
                chkReadOnly.setEnabled(false);
                chkReadWrite.setEnabled(false);
                chkEncrypt.setEnabled(false);
                break;
            case TYPE_NAME_N88_ASCII:
            case TYPE_NAME_N88_RANDOM:
                // ascii
                chkReadOnly.setEnabled(true);
                chkReadWrite.setEnabled(true);
                chkEncrypt.setEnabled(false);
                break;
            case TYPE_NAME_N88_BINARY:
                // binary
                chkReadOnly.setEnabled(true);
                chkReadWrite.setEnabled(true);
                chkEncrypt.setEnabled(true);
                break;
        }
    }

    // C++ method: CalcFileTypeFromPos
    // Return attribute from list position (for property dialog)
    public int calcFileTypeFromPos(int pos) {
        int val = 0;
        if (pos == TYPE_NAME_N88_MACHINE) {
            val = FILE_TYPE_MACHINE_MASK.getValue();
        } else if (pos == TYPE_NAME_N88_BINARY) {
            val = FILE_TYPE_BINARY_MASK.getValue();
        } else {
            val = FILE_TYPE_ASCII_MASK.getValue();
            if (pos == TYPE_NAME_N88_RANDOM) {
                val |= FILE_TYPE_RANDOM_MASK.getValue();
            }
        }
        return val;
    }

    // C++ method: SetAttrInAttrDialog
    @Override
    public boolean setAttrInAttrDialog(IntNameBox parent, DiskBasicDirItemAttr attr, DiskBasicError errinfo) {
        ButtonGroup radType1 = (ButtonGroup) parent.getComponent(IDC_RADIO_TYPE1);
        JCheckBox chkReadOnly = (JCheckBox) parent.getComponent(IDC_CHECK_READONLY);
        JCheckBox chkReadWrite = (JCheckBox) parent.getComponent(IDC_CHECK_READWRITE);
        JCheckBox chkEncrypt = (JCheckBox) parent.getComponent(IDC_CHECK_ENCRYPT);

        if (radType1 == null || chkReadOnly == null || chkReadWrite == null || chkEncrypt == null) return false;

        int val = calcFileTypeFromPos(radType1.getButtonCount());
        val |= chkReadOnly.isSelected() ? FILE_TYPE_READONLY_MASK.getValue() : 0;
        val |= chkEncrypt.isSelected() ? FILE_TYPE_ENCRYPTED_MASK.getValue() : 0;
        val |= chkReadWrite.isSelected() ? FILE_TYPE_READWRITE_MASK.getValue() : 0;

        attr.setFileAttr(FORMAT_TYPE_UNKNOWN, val, 0);

        return true;
    }
}
