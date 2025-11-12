/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package l3diskex.ui.dirItem;

import java.util.ArrayList;
import java.util.Map;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import l3diskex.basicfmt.DiskBasicDirItem.DiskBasicDirItemAttr;
import l3diskex.basicfmt.diritem.DiskBasicDirItemFROST;
import l3diskex.basicfmt.DiskBasicError;
import l3diskex.ui.IntNameBox;
import l3diskex.ui.UiDirItem;

import static l3diskex.basicfmt.diritem.DiskBasicDirItemFROST.FILETYPE_FROST_BAS;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemFROST.typeNameFROST1;


/**
 * UiDirItemFROST.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2025-10-19 nsano initial version <br>
 */
public class UiDirItemFROST extends UiDirItem {

    DiskBasicDirItemFROST dirItem;

    static final int IDC_RADIO_TYPE1 = 51;
    static final int IDC_CHECK_READONLY = 52;
    static final int IDC_CHECK_READWRITE = 53;
    static final int IDC_CHECK_ENCRYPT = 54;
    static final int IDC_RADIO_TYPE2 = 55;

    //
    // ダイアログ用
    //

    /**
     * 属性からリストの位置を返す(プロパティダイアログ用)
     */
    int GetFileType2Pos() {
        return dirItem.getFileAttr().getType();
    }

    /**
     * ダイアログ表示前にファイルの属性を設定
     */
    private void SetFileTypeForAttrDialog(int show_flags, String name, int[] file_type_1, int[] file_type_2) {
        // Assume INTNAME_NEW_FILE is a constant in IntNameBox
        if ((show_flags & IntNameBox.INTNAME_NEW_FILE) != 0) {
            // 外部からインポート時
            file_type_1[0] = dirItem.convOriginalTypeFromFileName(name);
        }
    }

    /**
     * ダイアログ内の属性部分のレイアウトを作成
     */
    @Override
    public void createControlsForAttrDialog(IntNameBox parent, int show_flags, String file_path, BoxLayout sizer, Object flags) {
        int[] file_type_1 = new int[] {dirItem.getFileType1()};
        int[] file_type_2 = new int[] {GetFileType2Pos()};
        JRadioButton[] radType1;

        SetFileTypeForAttrDialog(show_flags, file_path, file_type_1, file_type_2);

        int type1pos = ConvFileType1Pos(file_type_1[0]);

        // Using JRadioButton for a RadioBox equivalent
        JPanel typePanel = new JPanel();
        typePanel.setLayout(new BoxLayout(typePanel, BoxLayout.Y_AXIS));
        typePanel.setBorder(javax.swing.BorderFactory.createTitledBorder("File Type")); // Placeholder for _("File Type")

        ButtonGroup buttonGroup = new ButtonGroup();
        ArrayList<JRadioButton> buttons = new ArrayList<>();

        int i = 0;
        for (Map.Entry<String, Object> e : typeNameFROST1.entrySet()) {
            JRadioButton button = new JRadioButton(e.getKey());
            button.setActionCommand(String.valueOf(e.getValue())); // Store index as action command
            buttonGroup.add(button);
            typePanel.add(button);
            buttons.add(button);
            // Assign a unique ID for later retrieval
            parent.registerComponent(IDC_RADIO_TYPE1 + i++, button);
        }

        if (type1pos >= 0 && type1pos < buttons.size()) {
            buttons.get(type1pos).setSelected(true);
        }

        // Add the type panel to the main sizer (JPanel)
        // Note: sizer is a JPanel in this conversion, flags is ignored/handled by layout manager
        sizer.add(typePanel);
    }

    /**
     * 機種依存の属性を設定する
     */
    @Override
    public boolean setAttrInAttrDialog(IntNameBox parent, DiskBasicDirItemAttr attr, DiskBasicError errinfo) {
        int t1 = -1;

        // Iterate through the registered radio buttons to find the selected one
        for (Object i : typeNameFROST1.values()) {
            JRadioButton button = (JRadioButton) parent.getComponent(IDC_RADIO_TYPE1 + (int) i);
            if (button != null && button.isSelected()) {
                // Assuming action command is the index 'i', which corresponds to the position in typeNameFROST1
                // and the value is the FILETYPE_FROST_... constant
                t1 = (int) i;
                break;
            }
        }

        if (t1 == -1) t1 = FILETYPE_FROST_BAS; // Default if nothing selected

        attr.setFileAttr(dirItem.getBasic().getFormatTypeNumber(), 0, t1);

        return true;
    }

    /**
     * 属性値を加工する
     */
    @Override
    public boolean processAttr(DiskBasicDirItemAttr attr, DiskBasicError errinfo) {
        int t1 = attr.getFileOriginAttr(0);

        // BASの場合、開始アドレスを1にする
        if (t1 == FILETYPE_FROST_BAS) {
            attr.setStartAddress(1);
        }

        return true;
    }
}
