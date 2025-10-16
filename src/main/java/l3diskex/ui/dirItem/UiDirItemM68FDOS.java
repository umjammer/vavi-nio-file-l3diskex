/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package l3diskex.ui.dirItem;

import java.util.ResourceBundle;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JWindow;

import l3diskex.basicfmt.DiskBasicDirItem.DiskBasicDirItemAttr;
import l3diskex.basicfmt.DiskBasicDirItemM68FDOS;
import l3diskex.basicfmt.DiskBasicDirItemM68FDOS.enTypeNameM68FDOS;
import l3diskex.basicfmt.DiskBasicError;
import l3diskex.ui.IntNameBox;
import l3diskex.ui.UiDirItem;

import static l3diskex.basicfmt.DiskBasicError.gDiskBasicErrorMsgs;
import static l3diskex.ui.IntNameBox.INTNAME_NEW_FILE;


/**
 * UiDirItemM68FDOS.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2025-10-20 nsano initial version <br>
 */
public class UiDirItemM68FDOS extends UiDirItem {

    private static final ResourceBundle rb = ResourceBundle.getBundle("messages");

    DiskBasicDirItemM68FDOS dirItem;

    /// インポート時ダイアログ表示前にファイルの属性を設定
    public void SetFileTypeForAttrDialog(int show_flags, String name, int[] file_type_1, int[] file_type_2) {
        if ((show_flags & INTNAME_NEW_FILE) != 0) {
            // 外部からインポート時
            file_type_1[0] = dirItem.convOriginalTypeFromFileName(name);
            file_type_2[0] = 0;
        }
    }

    /// 属性1を得る
    private int GetFileType1InAttrDialog(IntNameBox parent) {
        JCheckBox chkAttr = null;
        int val = 0;
        for (int i = 0; i < enTypeNameM68FDOS.TYPE_NAME_M68_FDOS_END; i++) {
            // Placeholder for FindWindow, assuming a way to get the control is available
            // chkAttr = (JCheckBox)parent.FindWindow(53 + i); // IDC_CHECK_ATTR + i
            // if (chkAttr == null) continue;
            // if (chkAttr.GetValue()) {
            //     val |= M68FDOSData.gTypeNameM68FDOS[i].value;
            // }
        }
        return val;
    }

    /// 属性2を得る
    private int GetFileType2InAttrDialog(IntNameBox parent) {
        return 0;
    }

    /// @name プロパティダイアログ用
    // @{

    /// ダイアログ内の属性部分のレイアウトを作成
    public void CreateControlsForAttrDialog(IntNameBox parent, int show_flags, String file_path, BoxLayout sizer, Object flags) {
        int type1 = dirItem.getFileType1();
        // int type2 = GetFileType2(); // not used

        int[] file_type_1 = {type1};
        // int[] file_type_2 = {type2}; // not used

        // JCheckBox chkAttr; // not used
        // wxTextCtrl txtRev; // not used

        SetFileTypeForAttrDialog(show_flags, file_path, file_type_1, new int[1]);

        // DimensionrFlags expand = new DimensionrFlags().Expand(); // not used

        // BoxLayout staType1 = new BoxLayout(new wxStaticBox(parent, new wxID_ANY(), String.wxGetTranslation("File Attributes")), 1); // wxVERTICAL
        // wxBoxLayout hbox = null;

        for (int i = 0; i < enTypeNameM68FDOS.TYPE_NAME_M68_FDOS_END; i++) {
            // if ((i % 2) == 0) {
            //     hbox = new wxBoxLayout(0); // wxHORIZONTAL
            //     staType1.Add(hbox);
            // }
            // chkAttr = new JCheckBox(parent, 53 + i, M68FDOSData.gTypeNameM68FDOS[i].name); // IDC_CHECK_ATTR + i
            // chkAttr.SetValue((file_type_1[0] & M68FDOSData.gTypeNameM68FDOS[i].value) != 0);
            // hbox.Add(chkAttr, flags);
        }

        // sizer.Add(staType1, flags);

        // hbox = new wxBoxLayout(0); // wxHORIZONTAL
        // hbox.Add(new wxStaticText(parent, new wxID_ANY(), String.wxGetTranslation("Revision")), flags);
        // txtRev = new wxTextCtrl(parent, 51); // IDC_TEXT_REV
        // txtRev.SetValue(GetRevisionStr());
        // txtRev.Enable(false);
        // hbox.Add(txtRev, expand);

        // sizer.Add(hbox, flags);
    }

    /// 機種依存の属性を設定する
    public boolean SetAttrInAttrDialog(IntNameBox parent, DiskBasicDirItemAttr attr, DiskBasicError errinfo) {
        int t1 = GetFileType1InAttrDialog(parent);
        int t2 = dirItem.getFileType2();
        int t3 = dirItem.getFileType3();
        short rev = dirItem.getRevision();

        attr.setFileAttr(dirItem.getBasic().getFormatTypeNumber(), 0, t1, t2, (t3 << 16) | (rev & 0xffff));

        return true;
    }

    /// ファイルサイズが適正か
    public boolean IsFileValidSize(IntNameBox parent, int size, int[] limit) {
        return true;
    }

    /// ダイアログ入力後のファイル名チェック
    public boolean ValidateFileName(JWindow parent, String filename, String[] errormsg) {
        // 空白はNG
        if (filename.isEmpty()) {
            errormsg[0] = rb.getString(gDiskBasicErrorMsgs[DiskBasicError.ERR_FILENAME_EMPTY]);
            return false;
        }
        return true;
    }

    /// ダイアログの終了アドレスを編集できるか
    public boolean IsEndAddressEditableInAttrDialog(IntNameBox parent) {
        return false;
    }

}
