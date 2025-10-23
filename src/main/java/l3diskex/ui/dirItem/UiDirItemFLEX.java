/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package l3diskex.ui.dirItem;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JPanel;

import l3diskex.Utils;
import l3diskex.basicfmt.DiskBasicDirItem.DiskBasicDirItemAttr;
import l3diskex.basicfmt.DiskBasicDirItemFLEX;
import l3diskex.basicfmt.DiskBasicDirItemFLEX.en_type_name_flex;
import l3diskex.basicfmt.DiskBasicError;
import l3diskex.ui.IntNameBox;
import l3diskex.ui.UiDirItem;

import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_RANDOM_MASK;
import static l3diskex.basicfmt.DiskBasicDirItemFLEX.gTypeNameFLEX;


/**
 * DiskBasicDirItemFLEX.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2025-10-19 nsano initial version <br>
 */
public class UiDirItemFLEX extends UiDirItem {

    DiskBasicDirItemFLEX dirItem;

    /// ダイアログ内の属性部分のレイアウトを作成
    @Override
    public void createControlsForAttrDialog(IntNameBox parent, int show_flags, String file_path, BoxLayout sizer, Object flags) {
        int file_type_1 = dirItem.getFileAttr().getType();
        int file_type_2 = dirItem.getFileType2();

        int[] ft1 = {file_type_1};
        int[] ft2 = {file_type_2};
        dirItem.SetFileTypeForAttrDialog(show_flags, file_path, ft1, ft2);
        file_type_1 = ft1[0];
        file_type_2 = ft2[0];

        // wxStaticBoxSizer -> TitledBorder on JPanel + BoxLayout
        JPanel staType1 = new JPanel();
        staType1.setBorder(BorderFactory.createTitledBorder("File Attributes"));
        staType1.setLayout(new BoxLayout(staType1, BoxLayout.Y_AXIS));

        for (int i = 0; i <= en_type_name_flex.TYPE_NAME_FLEX_RANDOM.ordinal(); i++) {
            JCheckBox chkAttr1 = new JCheckBox(Utils.keyAt(gTypeNameFLEX, i));
            chkAttr1.setName(String.valueOf(IDC_CHECK_ATTR1 + i)); // Set name for FindWindow
            chkAttr1.setSelected((file_type_1 & (int) Utils.valueAt(gTypeNameFLEX, i)) != 0);
            staType1.add(chkAttr1);
        }

        // Assuming sizer is a JPanel with a layout manager like BoxLayout
        sizer.add(staType1);

        // ユーザ定義データ(ランダムファイル属性値)
        parent.SetUserData(file_type_2);
    }

    // wxWidgets to Swing helper IDs
    public static final int IDC_CHECK_ATTR1 = 51;

    /// 属性を変更した際に呼ばれるコールバック
    @Override
    public void changeTypeInAttrDialog(IntNameBox parent) {
    }

    /// 機種依存の属性を設定する
    @Override
    public boolean setAttrInAttrDialog(IntNameBox parent, DiskBasicDirItemAttr attr, DiskBasicError errinfo) {
        int val = 0;
        for (int i = 0; i <= en_type_name_flex.TYPE_NAME_FLEX_RANDOM.ordinal(); i++) {
            JCheckBox chkAttr1 = (JCheckBox) parent.getComponent(IDC_CHECK_ATTR1 + i);
            if (chkAttr1 != null && chkAttr1.isSelected()) {
                val |= Utils.valueAt(gTypeNameFLEX, i);
            }
        }

        // ユーザ定義データ(ランダムファイル属性値)
        int random = 0;
        if ((val & FILE_TYPE_RANDOM_MASK.getValue()) != 0) {
            random = parent.GetUserData();
        }

        attr.SetFileAttr(dirItem.getBasic().getFormatTypeNumber(), val, random);

        return true;
    }

    /// ダイアログ入力後のファイル名チェック
    public boolean validateFileName(IntNameBox parent, String filename, StringBuilder errormsg) {
        // wxFileName fn(filename) equivalent
        int lastDot = filename.lastIndexOf('.');
        String ext = (lastDot == -1 || lastDot == filename.length() - 1) ? "" : filename.substring(lastDot + 1);

        if (ext.isEmpty()) {
            errormsg.append(DiskBasicError.gDiskBasicErrorMsgs[DiskBasicError.ERR_FILEEXT_EMPTY]);
            return false;
        }
        return true;
    }
}
