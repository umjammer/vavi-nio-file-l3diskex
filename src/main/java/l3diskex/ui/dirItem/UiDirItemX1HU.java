/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package l3diskex.ui.dirItem;

import java.awt.GridLayout;
import java.time.LocalDateTime;
import java.util.ResourceBundle;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;

import l3diskex.Utils;
import l3diskex.basicfmt.DiskBasicDirItem.DiskBasicDirItemAttr;
import l3diskex.basicfmt.diritem.DiskBasicDirItemX1HU;
import l3diskex.basicfmt.DiskBasicError;
import l3diskex.ui.IntNameBox;
import l3diskex.ui.UiDirItem;

import static l3diskex.basicfmt.diritem.DiskBasicDirItemSDOS.IDC_COMBO_TYPE1;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemX1HU.DATATYPE_X1HU_PASSWORD_NONE;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemX1HU.EXTERNAL_X1_DEFAULT;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemX1HU.EXTERNAL_X1_RANDOM;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemX1HU.EXTERNAL_X1_SWORD;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemX1HU.FILETYPE_X1HU_ASCII;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemX1HU.FILETYPE_X1HU_BASIC;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemX1HU.FILETYPE_X1HU_BINARY;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemX1HU.FILETYPE_X1HU_DIRECTORY;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemX1HU.TYPE_NAME_X1HU_ASCII;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemX1HU.TYPE_NAME_X1HU_BASIC;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemX1HU.TYPE_NAME_X1HU_BINARY;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemX1HU.TYPE_NAME_X1HU_DIRECTORY;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemX1HU.TYPE_NAME_X1HU_END;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemX1HU.TYPE_NAME_X1HU_PASSWORD;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemX1HU.TYPE_NAME_X1HU_RANDOM;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemX1HU.TYPE_NAME_X1HU_READ_ONLY;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemX1HU.TYPE_NAME_X1HU_SWORD;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemX1HU.gTypeNameX1HU_1;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemX1HU.gTypeNameX1HU_2;
import static l3diskex.ui.IntNameBox.INTNAME_NEW_FILE;
import static l3diskex.ui.dirItem.UiDirItemFLEX.IDC_CHECK_ATTR1;


/**
 * UiDirItemX1HU.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2025-10-20 nsano initial version <br>
 */
public class UiDirItemX1HU extends UiDirItem {

    private static final ResourceBundle rb = ResourceBundle.getBundle("messages");

    DiskBasicDirItemX1HU dirItem;

    // Create controls for attribute dialog
    @Override
    public void createControlsForAttrDialog(IntNameBox parent, int show_flags, String file_path, BoxLayout sizer, Object flags) {
        int t1 = (dirItem.getFileType1() | (dirItem.externalAttr << 16));

        if ((show_flags & INTNAME_NEW_FILE) != 0) {
            // 外部からインポート時 (When importing from outside)
            t1 = dirItem.convOriginalTypeFromFileName(file_path);
        }

        int file_type_1 = dirItem.getFileType1Pos(t1);
        int file_type_2 = (t1 & 0xff);

        JComboBox comType1;
        JCheckBox chkAttr1;
        JCheckBox chkEncrypt = null;

        BoxLayout staType1 = new BoxLayout(null, StaticBox.newWxStaticBox(parent, wxID_ANY, rb.getString("File Type")), wxVERTICAL);

        String[] types1 = new String[TYPE_NAME_X1HU_END];
        for (int i = TYPE_NAME_X1HU_BINARY; i < TYPE_NAME_X1HU_END; i++) {
            types1[i] = rb.getString(Utils.keyAt(gTypeNameX1HU_1, i));
        }

        comType1 = new JComboBox(parent, IDC_COMBO_TYPE1, new WxDefaultPosition(), new WxDefaultSize(), types1);
        if (file_type_1 >= 0) {
            comType1.setSelection(file_type_1);
        }
        staType1.add(comType1, flags);
        sizer.add(staType1, flags);

        BoxLayout staType4 = new WxStaticBoxLayout(null, WxStaticBox.newWxStaticBox(parent, wxID_ANY, rb.getString("File Attributes")), wxVERTICAL);
        GridLayout szrG = new GridLayout(2, 2, 4);
        for (int i = 0; gTypeNameX1HU_2.size(); i++) {
            chkAttr1 = new JCheckBox(parent, IDC_CHECK_ATTR1 + i, rb.getString(Utils.keyAt(gTypeNameX1HU_2, i)));
            chkAttr1.setValue((file_type_2 & (int) Utils.valueAt(gTypeNameX1HU_2, i)) != 0);
            szrG.add(chkAttr1);
            if (i == TYPE_NAME_X1HU_PASSWORD) {
                chkEncrypt = chkAttr1;
            }
        }

        // ユーザ定義データ X1ではファイルパスワード (User defined data: file password in X1)
        int passwd;
        if ((show_flags & INTNAME_NEW_FILE) != 0) {
            passwd = DATATYPE_X1HU_PASSWORD_NONE;
        } else {
            passwd = dirItem.getFileType2();
        }
        parent.setUserData(passwd);
        if (chkEncrypt != null) {
            chkEncrypt.setValue(passwd != DATATYPE_X1HU_PASSWORD_NONE);
            chkEncrypt.enable(false);
        }

        staType4.add(szrG, flags);
        sizer.add(staType4, flags);

        // event handler
        parent.bind(wxEVT_CHOICE, parent::onChangeType1, parent, IDC_COMBO_TYPE1); // Placeholder for event binding
    }

    // Initialize for attribute dialog
    @Override
    public void initializeForAttrDialog(IntNameBox parent, int show_flags, int[] user_data) {
        // 日付が０なら日付を無視するにチェック (Check ignore date if date is 0)
        if ((show_flags & INTNAME_NEW_FILE) == 0) {
            LocalDateTime tm = LocalDateTime.now();
            dirItem.getFileCreateDateTime(tm); // Assumed method call
            parent.ignoreDateTime(new gConfig.DoesIgnoreDateTime() || tm.ignorable()); // Assumed tm.ignorable()
        }
    }

    // Callback when type is changed in attribute dialog
    @Override
    public void changeTypeInAttrDialog(IntNameBox parent) {
        JComboBox comType1 = (JComboBox) parent.getComponent(IDC_COMBO_TYPE1);
        if (comType1 == null) return;

        boolean enable = (comType1.getSelection() == TYPE_NAME_X1HU_BINARY);
        parent.enableStartAddress(enable);
        parent.enableExecuteAddress(enable);
    }

    // Get attribute 1 from dialog
    public int getFileType1InAttrDialog(IntNameBox parent) {
        JComboBox comType1 = (JComboBox) parent.getComponent(IDC_COMBO_TYPE1);
        return comType1.getSelection();
    }

    // Get attribute 2 from dialog
    public int getFileType2InAttrDialog(IntNameBox parent) {
        int val = 0;

        for (int i = 0; i <= TYPE_NAME_X1HU_READ_ONLY; i++) {
            JCheckBox chkAttr1 = (JCheckBox) parent.getComponent(IDC_CHECK_ATTR1 + i);
            if (chkAttr1.getValue()) {
                val |= (int) Utils.valueAt(gTypeNameX1HU_2, i);
            }
        }
        return val;
    }

    // Calculate file type from list position
    private int calcFileTypeFromPos(int pos) {
        int val = 0;
        int ext = 0;
        switch (pos) {
            case TYPE_NAME_X1HU_BINARY:
                val = FILETYPE_X1HU_BINARY;
                break;
            case TYPE_NAME_X1HU_BASIC:
                val = FILETYPE_X1HU_BASIC;
                break;
            case TYPE_NAME_X1HU_ASCII:
                ext = EXTERNAL_X1_DEFAULT;
                val = FILETYPE_X1HU_ASCII;
                break;
            case TYPE_NAME_X1HU_DIRECTORY:
                val = FILETYPE_X1HU_DIRECTORY;
                break;
            case TYPE_NAME_X1HU_RANDOM:
                ext = EXTERNAL_X1_RANDOM;
                val = FILETYPE_X1HU_ASCII;
                break;
            case TYPE_NAME_X1HU_SWORD:
                ext = EXTERNAL_X1_SWORD;
                val = FILETYPE_X1HU_ASCII;
                break;
        }
        return (ext << 16 | val);
    }

    // Set machine-dependent attributes
    @Override
    public boolean setAttrInAttrDialog(IntNameBox parent, DiskBasicDirItemAttr attr, DiskBasicError errinfo) {
        int t1 = calcFileTypeFromPos(getFileType1InAttrDialog(parent));
        t1 |= getFileType2InAttrDialog(parent);

        // ユーザ定義データ X1ではファイルパスワード (User defined data: file password in X1)
        JCheckBox chkEncrypt = (JCheckBox) parent.getComponent(IDC_CHECK_ATTR1 + TYPE_NAME_X1HU_PASSWORD);
        int passwd = DATATYPE_X1HU_PASSWORD_NONE;
        if (chkEncrypt.getValue()) {
            passwd = parent.getUserData();
        }

        attr.setFileAttr(dirItem.getBasic().getFormatTypeNumber(), 0, (passwd << 8) | t1);

        return true;
    }

    // Check if file size is valid
    @Override
    public boolean isFileValidSize(IntNameBox parent, int size, int[] limit) {
        int limit_size = 0xffff;
        if (limit != null) limit[0] = limit_size;

        int file_type1 = getFileType1InAttrDialog(parent);

        if (file_type1 == TYPE_NAME_X1HU_BINARY || file_type1 == TYPE_NAME_X1HU_BASIC) {
            return (size <= limit_size);
        } else {
            return true;
        }
    }
}
