/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package l3diskex.ui.dirItem;

import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JSpinner;

import l3diskex.basicfmt.DiskBasicDirItem.DiskBasicDirItemAttr;
import l3diskex.basicfmt.diritem.DiskBasicDirItemCPM;
import l3diskex.basicfmt.diritem.DiskBasicDirItemCPM.EnTypeNameCPM;
import l3diskex.basicfmt.diritem.DiskBasicDirItemCPM.EnTypeNameCPM2;
import l3diskex.basicfmt.DiskBasicError;
import l3diskex.ui.IntNameBox;
import l3diskex.ui.UiDirItem;

import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_ARCHIVE_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BINARY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_READONLY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_SYSTEM_MASK;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemCPM.typeNameCPM;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemCPM.typeNameCPM_2;


/**
 * UiDirItemCPM.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2025-10-19 nsano initial version <br>
 */
public class UiDirItemCPM extends UiDirItem {

    static final ResourceBundle rb = ResourceBundle.getBundle("messages");

    // UI component IDs (assuming these are constant integers for FindWindow)
    public static final int IDC_SPIN_USERID = 51;
    public static final int IDC_CHECK_READONLY = 52;
    public static final int IDC_CHECK_SYSTEM = 53;
    public static final int IDC_CHECK_ARCHIVE = 54;
    public static final int IDC_RADIO_BINASC = 55;

    DiskBasicDirItemCPM dirItem;

    //
    // ダイアログ用
    //

    // Assuming IntNameBox, wxSpinCtrl, JCheckBox, wxRadioBox, BoxLayout,
    // wxStaticText, wxBoxLayout, DimensionrFlags, wxArrayString are implemented/interfaced
    // by Java UI libraries or placeholder classes.

    /**
     * 属性からリストの位置を返す(プロパティダイアログ用)
     */
    public int GetFileType1Pos() {
        return dirItem.getFileType1();
    }

    /**
     * 属性からリストの位置を返す(プロパティダイアログ用)
     */
    public int GetFileType2Pos() {
        return dirItem.getFileType2();
    }

    /**
     * ダイアログ用に属性を設定する
     * ダイアログ表示前にファイルの属性を設定
     */
    public void SetFileTypeForAttrDialog(int show_flags, String name, int[] file_type_1, int[] file_type_2) {
        // Assuming INTNAME_NEW_FILE is a defined constant
        if ((show_flags & 0x01) != 0 /*INTNAME_NEW_FILE*/) {
            // 外部からインポート時
            file_type_2[0] = dirItem.convFileTypeFromFileName(name);
        }
    }

    /**
     * ダイアログ内の属性部分のレイアウトを作成
     */
    @Override
    public void createControlsForAttrDialog(IntNameBox parent, int show_flags, String file_path, BoxLayout sizer, Object flags) {
        int[] file_type_1 = new int[] {GetFileType1Pos()};
        int[] file_type_2 = new int[] {GetFileType2Pos()};
        ButtonGroup radBinAsc;
        JSpinner spnUserId;
        JCheckBox chkReadOnly;
        JCheckBox chkSystem;
        JCheckBox chkArchive;

        SetFileTypeForAttrDialog(show_flags, file_path, file_type_1, file_type_2);

        List<String> choices = new ArrayList<>();
        for (int i = 0; typeNameCPM_2[i] != null; i++) {
            choices.add(rb.getString(typeNameCPM_2[i]));
        }
        radBinAsc = new ButtonGroup(parent, IDC_RADIO_BINASC, "Select File Type", null, null, choices, 0, 0); // wxRA_SPECIFY_COLS
        radBinAsc.setSelected((file_type_2[0] & FILE_TYPE_BINARY_MASK.getValue()) != 0 ? EnTypeNameCPM2.TYPE_NAME_CPM_BINARY.getValue() : EnTypeNameCPM2.TYPE_NAME_CPM_ASCII.getValue());
        sizer.Add(radBinAsc, flags);

        BoxLayout staType4 = new BoxLayout(new StaticBox(parent, wxID_ANY, "File Attributes"), 1); // wxVERTICAL
        BoxLayout hbox = new BoxLayout(0); // wxHORIZONTAL
        hbox.Add(new StaticText(parent, wxID_ANY, "User ID"), new DimensionrFlags().Align(0x0200 /*wxALIGN_CENTER_VERTICAL*/));
        spnUserId = new wxSpinCtrl(parent, IDC_SPIN_USERID, "", null, null, 0x1000 /*wxSP_ARROW_KEYS*/ | 0x0001 /*wxALIGN_LEFT*/, 0, 15, file_type_1[0]);
        hbox.Add(spnUserId, flags);
        staType4.Add(hbox);

        hbox = new BoxLayout(0); // wxHORIZONTAL
        chkReadOnly = new JCheckBox(parent, IDC_CHECK_READONLY, rb.getString(typeNameCPM[EnTypeNameCPM.TYPE_NAME_CPM_READ_ONLY.getValue()]));
        chkReadOnly.SetValue((file_type_2[0] & FILE_TYPE_READONLY_MASK.getValue()) != 0);
        hbox.Add(chkReadOnly, flags);
        chkSystem = new JCheckBox(parent, IDC_CHECK_SYSTEM, rb.getString(typeNameCPM[EnTypeNameCPM.TYPE_NAME_CPM_SYSTEM.getValue()]));
        chkSystem.SetValue((file_type_2[0] & FILE_TYPE_SYSTEM_MASK.getValue()) != 0);
        hbox.Add(chkSystem, flags);
        chkArchive = new JCheckBox(parent, IDC_CHECK_ARCHIVE, rb.getString(typeNameCPM[EnTypeNameCPM.TYPE_NAME_CPM_ARCHIVE.getValue()]));
        chkArchive.SetValue((file_type_2[0] & FILE_TYPE_ARCHIVE_MASK.getValue()) != 0);
        hbox.Add(chkArchive, flags);

        staType4.Add(hbox);
        sizer.Add(staType4, flags);
    }

    /**
     * 属性を変更した際に呼ばれるコールバック
     */
    @Override
    public void changeTypeInAttrDialog(IntNameBox parent) {
    }

    /**
     * 機種依存の属性を設定する
     */
    @Override
    public boolean setAttrInAttrDialog(IntNameBox parent, DiskBasicDirItemAttr attr, DiskBasicError errinfo) {
        JSpinner spnUserId = (JSpinner) parent.getComponent(IDC_SPIN_USERID);
        ButtonGroup radBinAsc = (ButtonGroup) parent.getComponent(IDC_RADIO_BINASC);
        JCheckBox chkReadOnly = (JCheckBox) parent.getComponent(IDC_CHECK_READONLY);
        JCheckBox chkSystem = (JCheckBox) parent.getComponent(IDC_CHECK_SYSTEM);
        JCheckBox chkArchive = (JCheckBox) parent.getComponent(IDC_CHECK_ARCHIVE);

        int user_id = (int) spnUserId.getValue();
        // val = (val << FILETYPE_CPM_USERID_POS) & FILETYPE_CPM_USERID_MASK; // User ID is SetFileType1()
        int val = chkReadOnly.isSelected() ? FILE_TYPE_READONLY_MASK.getValue() : 0;
        val |= chkSystem.isSelected() ? FILE_TYPE_SYSTEM_MASK.getValue() : 0;
        val |= chkArchive.isSelected() ? FILE_TYPE_ARCHIVE_MASK.getValue() : 0;
        val |= radBinAsc.isSelected() == EnTypeNameCPM2.TYPE_NAME_CPM_BINARY.getValue() ? FILE_TYPE_BINARY_MASK : 0;

        attr.setFileAttr(dirItem.getBasic().getFormatTypeNumber(), val, user_id);

        return true;
    }
}
