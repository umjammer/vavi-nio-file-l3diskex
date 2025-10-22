/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package l3diskex.ui.dirItem;

import java.nio.file.Path;
import java.util.ResourceBundle;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JTextField;
import javax.swing.JWindow;

import l3diskex.Parambase;
import l3diskex.Utils;
import l3diskex.basicfmt.DiskBasicDirItem.DiskBasicDirItemAttr;
import l3diskex.basicfmt.DiskBasicDirItemAppleDOS;
import l3diskex.basicfmt.DiskBasicDirItemAppleDOS.en_file_type_mask_appledos;
import l3diskex.basicfmt.DiskBasicDirItemAppleDOS.en_type_name_appledos;
import l3diskex.basicfmt.DiskBasicError;
import l3diskex.ui.IntNameBox;
import l3diskex.ui.UiDirItem;

import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_ASCII_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BASIC_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BINARY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_DATA_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_INTEGER_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_MACHINE_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_READONLY_MASK;
import static l3diskex.basicfmt.DiskBasicDirItemAppleDOS.APLEDOS_TRACK_LIST_MAX;
import static l3diskex.basicfmt.DiskBasicDirItemAppleDOS.gTypeNameAppleDOS;


/**
 * UiDirItemAppleDOS.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2025-10-19 nsano initial version <br>
 */
public class UiDirItemAppleDOS extends UiDirItem {

    static final ResourceBundle rb = ResourceBundle.getBundle("message");

    DiskBasicDirItemAppleDOS dirItem;

    /**
     * 属性からリストの位置を返す(プロパティダイアログ用)
     *
     * @param type1 属性値
     * @return リストの位置
     */
    public int ConvFileType1Pos(int type1) {
        int p1 = 0;
        if ((type1 & 0x7f) == en_file_type_mask_appledos.FILETYPE_MASK_APLEDOS_IBASIC.getValue()) {
            p1 = en_type_name_appledos.TYPE_NAME_APLEDOS_IBASIC.getValue();
        } else if ((type1 & 0x7f) == en_file_type_mask_appledos.FILETYPE_MASK_APLEDOS_ABASIC.getValue()) {
            p1 = en_type_name_appledos.TYPE_NAME_APLEDOS_ABASIC.getValue();
        } else if ((type1 & 0x7f) == en_file_type_mask_appledos.FILETYPE_MASK_APLEDOS_BINARY.getValue()) {
            p1 = en_type_name_appledos.TYPE_NAME_APLEDOS_BINARY.getValue();
        } else {
            p1 = en_type_name_appledos.TYPE_NAME_APLEDOS_TEXT.getValue();
        }
        return p1;
    }

    /**
     * インポート時ダイアログ表示前にファイルの属性を設定
     *
     * @param show_flags  ダイアログ表示フラグ
     * @param name        ファイル名
     * @param file_type_1 CreateControlsForAttrDialog()に渡す
     */
    public void SetFileTypeForAttrDialog(int show_flags, String name, int[] file_type_1) {
        if ((show_flags & IntNameBox.INTNAME_NEW_FILE) != 0) {
            // 外部からインポート時
            // 拡張子で属性を設定する
            Path fn = Path.of(name);
            // Assuming GetAttributesByExtension returns a map-like object with FindUpperCase
            Object attrs = dirItem.getBasic().diskBasicParam.getAttributesByExtension();
            // MyAttribute sa = basic.GetAttributesByExtension().FindUpperCase(fn.GetExt()); // Need correct API
            // Placeholder logic:
            Parambase.MyAttribute sa = null;
            if (sa != null) {
                int ftype = sa.getType();
                file_type_1[0] = dirItem.convToFileType1(ftype);
            } else {
                file_type_1[0] = 0;
            }
        }
    }

    public static final int IDC_RADIO_TYPE1 = 51;
    public static final int IDC_CHECK_READONLY = 52;

    /**
     * ダイアログ内の属性部分のレイアウトを作成
     *
     * @param parent     プロパティダイアログ
     * @param show_flags ダイアログ表示フラグ
     * @param file_path  外部からインポート時のファイルパス
     * @param sizer
     * @param flags
     */
    @Override
    public void createControlsForAttrDialog(IntNameBox parent, int show_flags, String file_path, BoxLayout sizer, Object flags) {
        int[] file_type_1 = {dirItem.getFileType1()};
        JTextField radType1;
        JCheckBox chkReadOnly;

        SetFileTypeForAttrDialog(show_flags, file_path, file_type_1);

        String[] types1 = new String[en_type_name_appledos.TYPE_NAME_APLEDOS_READ_ONLY.getValue()];
        for (int i = 0; i < en_type_name_appledos.TYPE_NAME_APLEDOS_READ_ONLY.getValue(); i++) {
            types1[i] = rb.getString(Utils.keyAt(gTypeNameAppleDOS, i));
        }
        radType1 = new JTextField(parent, IDC_RADIO_TYPE1, "File Type", new DefaultPosition(), new wxDefaultSize(), types1, 2, wxRA_SPECIFY_COLS);
        radType1.setSelected(ConvFileType1Pos(file_type_1[0]));
        sizer.Add(radType1, flags);

        BoxLayout staType4 = new BoxLayout(null, wxVERTICAL); // New wxStaticBox(parent, wxID_ANY, _("File Attributes"))

        chkReadOnly = new JCheckBox(parent, IDC_CHECK_READONLY, rb.getString(Utils.keyAt(gTypeNameAppleDOS, en_type_name_appledos.TYPE_NAME_APLEDOS_READ_ONLY.getValue())));
        chkReadOnly.setSelected((file_type_1[0] & en_file_type_mask_appledos.FILETYPE_MASK_APLEDOS_READ_ONLY.getValue()) != 0);
        staType4.Add(chkReadOnly, flags);

        sizer.Add(staType4, flags);
    }

    /** 属性を変更した際に呼ばれるコールバック */
    @Override
    public void changeTypeInAttrDialog(IntNameBox parent) {
    }

    /**
     * 機種依存の属性を設定する
     *
     * @param parent  [in,out] プロパティダイアログ
     * @param attr    [in,out] プロパティの属性値
     * @param errinfo [in,out] エラー情報
     * @return true 成功
     */
    @Override
    public boolean setAttrInAttrDialog(IntNameBox parent, DiskBasicDirItemAttr attr, DiskBasicError errinfo) {
        ButtonGroup radType1 = (ButtonGroup) parent.getComponent(IDC_RADIO_TYPE1);
        JCheckBox chkReadOnly = (JCheckBox) parent.getComponent(IDC_CHECK_READONLY);

        int val = 0;
        int ori = 0;

        switch (radType1.getGetSelection()) {
            case 1: // TYPE_NAME_APLEDOS_IBASIC
                val = (FILE_TYPE_INTEGER_MASK.getValue() | FILE_TYPE_BASIC_MASK.getValue());
                ori = en_file_type_mask_appledos.FILETYPE_MASK_APLEDOS_IBASIC.getValue();
                break;
            case 2: // TYPE_NAME_APLEDOS_ABASIC
                val = (FILE_TYPE_BINARY_MASK.getValue() | FILE_TYPE_BASIC_MASK.getValue());
                ori = en_file_type_mask_appledos.FILETYPE_MASK_APLEDOS_ABASIC.getValue();
                break;
            case 3: // TYPE_NAME_APLEDOS_BINARY
                val = (FILE_TYPE_BINARY_MASK.getValue() | FILE_TYPE_MACHINE_MASK.getValue());
                ori = en_file_type_mask_appledos.FILETYPE_MASK_APLEDOS_BINARY.getValue();
                break;
            default: // TYPE_NAME_APLEDOS_TEXT
                val = (FILE_TYPE_ASCII_MASK.getValue() | FILE_TYPE_DATA_MASK.getValue());
                ori = en_file_type_mask_appledos.FILETYPE_MASK_APLEDOS_TEXT.getValue();
                break;
        }
        if (chkReadOnly.isSelected()) {
            val |= FILE_TYPE_READONLY_MASK.getValue();
            ori |= en_file_type_mask_appledos.FILETYPE_MASK_APLEDOS_READ_ONLY.getValue();
        }

        attr.setFileAttr(dirItem.getBasic().getFormatTypeNumber(), val, ori);

        return true;
    }

    /**
     * ダイアログ入力後のファイル名チェック
     *
     * @param parent   親ウィンドウ
     * @param filename ファイル名
     * @param errormsg エラーメッセージ
     * @return true 適正
     */
    public boolean validateFileName(JWindow parent, String filename, String[] errormsg) {
        //	Path fn(filename);
        //	if (fn.GetExt().IsEmpty()) {
        //		errormsg = wxGetTranslation(gDiskBasicErrorMsgs[DiskBasicError::ERR_FILEEXT_EMPTY]);
        //		return false;
        //	}
        return true;
    }

    /**
     * ファイルサイズが適正か
     *
     * @param parent ダイアログ
     * @param size   ファイルサイズ
     * @param limit  [out] 制限サイズ
     * @return true 適正
     */
    @Override
    public boolean isFileValidSize(IntNameBox parent, int size, int[] limit) {
        int limit_size = APLEDOS_TRACK_LIST_MAX * dirItem.getBasic().getSectorSize() - 1;
        if (limit != null && limit.length > 0) limit[0] = limit_size;
        return limit_size >= size;
    }
}
