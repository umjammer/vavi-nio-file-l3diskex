/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package l3diskex.ui.dirItem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;

import l3diskex.basicfmt.DiskBasicDirItem.DiskBasicDirItemAttr;
import l3diskex.basicfmt.DiskBasicDirItemFP;
import l3diskex.basicfmt.DiskBasicError;
import l3diskex.ui.IntNameBox;
import l3diskex.ui.UiDirItem;

import static l3diskex.basicfmt.BasicCommon.DiskBasicFormatType.FORMAT_TYPE_UNKNOWN;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_ASCII_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BASIC_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BINARY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_DATA_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_MACHINE_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_RANDOM_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_READONLY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_READWRITE_MASK;
import static l3diskex.basicfmt.DiskBasicDirItemFAT8.TypeNames.G_TYPE_NAME_1;
import static l3diskex.basicfmt.DiskBasicDirItemFAT8.TypeNames.G_TYPE_NAME_2;
import static l3diskex.basicfmt.DiskBasicDirItemFAT8.TypeNames.TYPE_NAME_1_BASIC;
import static l3diskex.basicfmt.DiskBasicDirItemFAT8.TypeNames.TYPE_NAME_1_DATA;
import static l3diskex.basicfmt.DiskBasicDirItemFAT8.TypeNames.TYPE_NAME_1_MACHINE;
import static l3diskex.basicfmt.DiskBasicDirItemFAT8.TypeNames.TYPE_NAME_2_ASCII;
import static l3diskex.basicfmt.DiskBasicDirItemFAT8.TypeNames.TYPE_NAME_2_BINARY;
import static l3diskex.basicfmt.DiskBasicDirItemFAT8.TypeNames.TYPE_NAME_2_RANDOM;


/**
 * UiDirItemFP.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2025-10-19 nsano initial version <br>
 */
public class UiDirItemFP extends UiDirItem {

    static final int IDC_RADIO_TYPE1 = 51;
    static final int  IDC_RADIO_TYPE2 = 52;
    static final int  IDC_CHECK_READONLY = 53;
    static final int  IDC_CHECK_READWRITE = 54;

    DiskBasicDirItemFP dirItem;

    @Override
    public void createControlsForAttrDialog(IntNameBox parent, int show_flags, String file_path, BoxLayout sizer, Object flags) {
        int[] file_type_1 = {dirItem.getFileType1Pos()};
        int[] file_type_2 = {dirItem.getFileType2Pos()};

        dirItem.SetFileTypeForAttrDialog(show_flags, file_path, file_type_1, file_type_2);

        List<String> types1 = new ArrayList<>(Arrays.asList(G_TYPE_NAME_1).subList(0, 3));
        // GUI component creation logic is simplified/omitted per constraint
        // wxRadioBox radType1 = new wxRadioBox(parent, IDC_RADIO_TYPE1, _("File Type"), ...);
        // radType1.SetSelection(file_type_1[0]);
        // sizer.Add(radType1, flags);

        // wxGridSizer *gszr = new wxGridSizer(2, 1, 1);

        List<String> types2 = new ArrayList<>(Arrays.asList(G_TYPE_NAME_2).subList(0, 3));
        // wxRadioBox radType2 = new wxRadioBox(parent, IDC_RADIO_TYPE2, _("Data Type"), ...);
        // radType2.SetSelection(file_type_2[0]);
        // gszr.Add(radType2, flags);

        int file_attr = dirItem.getFileType2();
        // wxStaticBoxSizer *staType4 = ...
        // wxCheckBox chkReadOnly = new wxCheckBox(parent, IDC_CHECK_READONLY, wxGetTranslation(gTypeNameFP_2[TYPE_NAME_FP_READ_ONLY]));
        // chkReadOnly.SetValue((file_attr & DATATYPE_MASK_FP_READ_ONLY) != 0);
        // staType4.Add(chkReadOnly, flags);
        // wxCheckBox chkReadWrite = new wxCheckBox(parent, IDC_CHECK_READWRITE, wxGetTranslation(gTypeNameFP_2[TYPE_NAME_FP_READ_WRITE]));
        // chkReadWrite.SetValue((file_attr & DATATYPE_MASK_FP_READ_WRITE) != 0);
        // staType4.Add(chkReadWrite, flags);
        // gszr.Add(staType4, flags);

        // sizer.Add(gszr);

        // parent.Bind(wxEVT_RADIOBOX, &IntNameBox::OnChangeType1, parent, IDC_RADIO_TYPE1);
    }

    @Override
    public void changeTypeInAttrDialog(IntNameBox parent) {
        ButtonGroup radType1 = (ButtonGroup)parent.getComponent(IDC_RADIO_TYPE1);
        ButtonGroup radType2 = (ButtonGroup)parent.getComponent(IDC_RADIO_TYPE2);

        int selected_idx = 0;
        if (radType1 != null) {
            selected_idx = radType1.getSelection();
        }

        if (radType2 == null) return;

        switch(selected_idx) {
            case TYPE_NAME_1_BASIC:
                if (radType2.getSelection() == TYPE_NAME_2_RANDOM) {
                    radType2.SetSelection(TYPE_NAME_2_ASCII);
                }
                radType2.setEnabled(TYPE_NAME_2_BINARY, true);
                radType2.setEnabled(TYPE_NAME_2_ASCII, true);
                radType2.setEnabled(TYPE_NAME_2_RANDOM, false);
                parent.setEditableStartAddress(false);
                parent.SetEditableEndAddress(false);
                parent.SetEditableExecuteAddress(false);
                break;
            case TYPE_NAME_1_DATA:
                if (radType2.getSelection() == TYPE_NAME_2_BINARY) {
                    radType2.SetSelection(TYPE_NAME_2_ASCII);
                }
                radType2.setEnabled(TYPE_NAME_2_BINARY, false);
                radType2.setEnabled(TYPE_NAME_2_ASCII, true);
                radType2.setEnabled(TYPE_NAME_2_RANDOM, true);
                parent.SetEditableStartAddress(false);
                parent.SetEditableEndAddress(false);
                parent.SetEditableExecuteAddress(false);
                break;
            case TYPE_NAME_1_MACHINE:
                radType2.SetSelection(TYPE_NAME_2_BINARY);
                radType2.setEnabled(TYPE_NAME_2_BINARY, true);
                radType2.setEnabled(TYPE_NAME_2_ASCII, false);
                radType2.setEnabled(TYPE_NAME_2_RANDOM, false);
                parent.SetEditableStartAddress(true);
                parent.SetEditableEndAddress(false);
                parent.SetEditableExecuteAddress(true);
                break;
        }

        parent.calcEndAddress();
    }

    @Override
    public boolean setAttrInAttrDialog(IntNameBox parent, DiskBasicDirItemAttr attr, DiskBasicError errinfo) {
        ButtonGroup radType1 = (ButtonGroup)parent.getComponent(IDC_RADIO_TYPE1);
        ButtonGroup radType2 = (ButtonGroup)parent.getComponent(IDC_RADIO_TYPE2);
        JCheckBox chkReadOnly = (JCheckBox)parent.getComponent(IDC_CHECK_READONLY);
        JCheckBox chkReadWrite = (JCheckBox)parent.getComponent(IDC_CHECK_READWRITE);

        int ftype = 0;
        int sel;

        if (radType1 != null) {
            sel = radType1.getSelection();
            switch(sel) {
                case TYPE_NAME_1_BASIC:
                    ftype = FILE_TYPE_BASIC_MASK.getValue();
                    break;
                case TYPE_NAME_1_MACHINE:
                    ftype = FILE_TYPE_MACHINE_MASK.getValue();
                    break;
                default:
                    ftype = FILE_TYPE_DATA_MASK.getValue();
                    break;
            }
        }

        if (radType2 != null) {
            sel = radType2.GetSelection();
            switch(sel) {
                case TYPE_NAME_2_RANDOM:
                    ftype |= FILE_TYPE_RANDOM_MASK.getValue();
                    break;
                case TYPE_NAME_2_ASCII:
                    ftype |= FILE_TYPE_ASCII_MASK.getValue();
                    break;
                default:
                    ftype |= FILE_TYPE_BINARY_MASK.getValue();
                    break;
            }
        }

        if (chkReadOnly != null && chkReadOnly.isSelected()) {
            ftype |= FILE_TYPE_READONLY_MASK;
        }
        if (chkReadWrite != null && chkReadWrite.isSelected()) {
            ftype |= FILE_TYPE_READWRITE_MASK;
        }

        attr.setFileAttr(FORMAT_TYPE_UNKNOWN, ftype, 0);

        return true;
    }

    public int getEndAddressInAttrDialog(IntNameBox parent) {
        ButtonGroup radType1 = (ButtonGroup)parent.getComponent(IDC_RADIO_TYPE1);
        ButtonGroup radType2 = (ButtonGroup)parent.getComponent(IDC_RADIO_TYPE2);
        if (radType1 == null || radType2 == null) return -1;

        int val = 0;
        int sel1 = radType1.GetSelection();
        int sel2 = radType2.GetSelection();
        switch(sel2) {
            case TYPE_NAME_2_BINARY:
                if (sel1 == TYPE_NAME_1_BASIC) {
                    val = dirItem.getFileSize();
                } else {
                    val = -1;
                }
                break;
            case TYPE_NAME_2_ASCII:
            case TYPE_NAME_2_RANDOM:
                // ASCII or Random files usually have no end address set in the directory for this format
                val = 0;
                break;
        }
        return val;
    }
}
