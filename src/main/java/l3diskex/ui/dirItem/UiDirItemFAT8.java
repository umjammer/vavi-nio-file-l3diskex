/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package l3diskex.ui.dirItem;

import javax.swing.ButtonGroup;
import javax.swing.JTextField;

import l3diskex.basicfmt.DiskBasicDirItem.DiskBasicDirItemAttr;
import l3diskex.basicfmt.DiskBasicDirItemFAT8;
import l3diskex.basicfmt.DiskBasicDirItemFAT8.TypeNames;
import l3diskex.basicfmt.DiskBasicError;
import l3diskex.ui.IntNameBox;
import l3diskex.ui.UiDirItem;

import static l3diskex.basicfmt.BasicCommon.DiskBasicFormatType.FORMAT_TYPE_UNKNOWN;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_ASCII_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BINARY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_RANDOM_MASK;


/**
 * UiDirItemFAT8.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2025-10-19 nsano initial version <br>
 */
public class UiDirItemFAT8 extends UiDirItem {

    DiskBasicDirItemFAT8 dirItem;

    // UI methods (assuming wxWidgets counterparts are mapped to Java UI/listeners)

    // The methods GetFileType1Pos() and GetFileType2Pos() are protected but used here.

    // Assuming the Java UI components (RadioBox, Sizer, etc.) and helper classes (IntNameBox) exist.
    public void CreateControlsForAttrDialog(IntNameBox parent, int show_flags, String file_path, Object sizer, Object flags) {
        int[] file_type_1 = {dirItem.getFileType1Pos()};
        int[] file_type_2 = {dirItem.getFileType2Pos()};
        ButtonGroup radType1;
        ButtonGroup radType2;

        dirItem.SetFileTypeForAttrDialog(show_flags, file_path, file_type_1, file_type_2);

        String[] types1 = new String[TypeNames.TYPE_NAME_1_MACHINE + 1];
        for (int i = 0; i <= TypeNames.TYPE_NAME_1_MACHINE; i++) {
            types1[i] = TypeNames.G_TYPE_NAME_1[i]; // Using direct string for translation
        }
        // Assuming RadioBox and related UI classes
        radType1 = new ButtonGroup(parent, TypeNames.ATTR_DIALOG_IDC_RADIO_TYPE1, "File Type", types1); // Constructor simplified
        radType1.SetSelection(file_type_1[0]);
        // sizer.Add(radType1, flags); // Assuming sizer/flags are correctly mapped

        String[] types2 = new String[TypeNames.TYPE_NAME_2_RANDOM + 1];
        for (int i = 0; i <= TypeNames.TYPE_NAME_2_RANDOM; i++) {
            types2[i] = TypeNames.G_TYPE_NAME_2[i]; // Using direct string for translation
        }

        radType2 = new ButtonGroup(parent, TypeNames.ATTR_DIALOG_IDC_RADIO_TYPE2, "Data Type", types2); // Constructor simplified
        radType2.SetSelection(file_type_2[0]);
        // sizer.Add(radType2, flags); // Assuming sizer/flags are correctly mapped

        // event handler - This is highly platform/toolkit dependent.
        // Assuming a method exists in IntNameBox to handle the event.
        // parent.Bind(Event.RADIOBOX, parent::OnChangeType1, TypeNames.ATTR_DIALOG_IDC_RADIO_TYPE1);
    }

    // Assuming RadioBox, TextCtrl, and other UI classes are mapped/mocked.
    // IntNameBox::OnChangeType1 should call this method, which is defined in the base C++ class.
    public void ChangeTypeInAttrDialog(IntNameBox parent) {
        // FindWindow methods are assumed to exist and return the correct type
        JTextField txtIntName = (JTextField) parent.getComponent(IntNameBox.IDC_TEXT_INTNAME);
        ButtonGroup radType1 = (ButtonGroup)parent.getComponent(TypeNames.ATTR_DIALOG_IDC_RADIO_TYPE1);
        ButtonGroup radType2 = (ButtonGroup)parent.getComponent(TypeNames.ATTR_DIALOG_IDC_RADIO_TYPE2);

        int selected_idx = 0;
        if (radType1 != null) {
            selected_idx = radType1.GetSelection();
        }

        if (radType2 != null) {
            int cnt = radType2.GetCount();
            int cur_pos = radType2.GetSelection();

            radType2.setEnabled(0, true);
            radType2.setEnabled(1, true);
            if (cnt > 2) radType2.setEnabled(2, true);

            if (selected_idx == 0) {
                // BASIC
                if (cnt > 2) {
                    if (cur_pos == 2) {
                        radType2.SetSelection(0);
                    }
                    radType2.setEnabled(2, false);	// ランダムアクセス指定不可
                }
            } else if (selected_idx == 1) {
                // データ
                if (cur_pos == 0) {
                    radType2.SetSelection(1);
                }
                radType2.enable(0, false);	// バイナリ指定不可
            } else if (selected_idx == 2) {
                // 機械語
                radType2.SetSelection(0);
                radType2.enable(1, false);	// アスキー指定不可
                if (cnt > 2) {
                    radType2.enable(2, false);	// ランダムアクセス指定不可
                }
            }
            // 拡張子を付加
            if (txtIntName != null) {
                txtIntName.setText(AddExtension(selected_idx, txtIntName.getValue()));
            }
        }
    }

    public boolean SetAttrInAttrDialog(IntNameBox parent, DiskBasicDirItemAttr attr, DiskBasicError errinfo) {
        ButtonGroup radType1 = (ButtonGroup)parent.getComponent(TypeNames.ATTR_DIALOG_IDC_RADIO_TYPE1);
        ButtonGroup radType2 = (ButtonGroup)parent.getComponent(TypeNames.ATTR_DIALOG_IDC_RADIO_TYPE2);

        int pos1 = radType1.getSelection();
        int pos2 = radType2.GetSelection();
        int val = (pos1 >= TypeNames.TYPE_NAME_1_BASIC && pos1 <= TypeNames.TYPE_NAME_1_MACHINE ? 1 << pos1 : 0);

        // Constants are assumed to be defined in DiskBasic or a global constants class
        switch(pos2) {
            case TypeNames.TYPE_NAME_2_BINARY:
                val |= FILE_TYPE_BINARY_MASK.getValue();
                break;
            case TypeNames.TYPE_NAME_2_ASCII:
                val |= FILE_TYPE_ASCII_MASK.getValue();
                break;
            case TypeNames.TYPE_NAME_2_RANDOM:
                val |= FILE_TYPE_RANDOM_MASK.getValue();
                break;
        }

        // Constants are assumed to be defined in DiskBasic or a global constants class
        attr.setFileAttr(FORMAT_TYPE_UNKNOWN.getValue(), val);

        return true;
    }
}
