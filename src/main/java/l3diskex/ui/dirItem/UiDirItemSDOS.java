/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package l3diskex.ui.dirItem;

import java.awt.Choice;
import java.util.ResourceBundle;
import javax.swing.BoxLayout;

import l3diskex.Utils;
import l3diskex.basicfmt.DiskBasicDirItem.DiskBasicDirItemAttr;
import l3diskex.basicfmt.DiskBasicDirItemSDOS;
import l3diskex.basicfmt.DiskBasicError;
import l3diskex.ui.IntNameBox;
import l3diskex.ui.UiDirItem;

import static l3diskex.basicfmt.DiskBasicDirItemSDOS.FILETYPE_SDOS_BAS1;
import static l3diskex.basicfmt.DiskBasicDirItemSDOS.FILETYPE_SDOS_BAS2;
import static l3diskex.basicfmt.DiskBasicDirItemSDOS.IDC_COMBO_TYPE1;
import static l3diskex.basicfmt.DiskBasicDirItemSDOS.TYPE_NAME_SDOS_DAT;
import static l3diskex.basicfmt.DiskBasicDirItemSDOS.TYPE_NAME_SDOS_OBJ;
import static l3diskex.basicfmt.DiskBasicDirItemSDOS.gTypeNameSDOS_1;
import static l3diskex.ui.IntNameBox.INTNAME_NEW_FILE;


/**
 * UiDirItemSDOS.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2025-10-19 nsano initial version <br>
 */
public class UiDirItemSDOS extends UiDirItem {

    private static final ResourceBundle rb = ResourceBundle.getBundle("messages");

    public static final int IDC_COMBO_TYPE1 = 51;

    DiskBasicDirItemSDOS dirItem;

    /**
     * ダイアログ用に属性を設定する
     *
     * @param show_flags  ダイアログ表示フラグ
     * @param name        ファイル名
     * @param file_type_1 CreateControlsForAttrDialog()に渡す
     * @param file_type_2 CreateControlsForAttrDialog()に渡す
     */
    private void SetFileTypeForAttrDialog(int show_flags, String name, int[] file_type_1, int[] file_type_2) {
        if ((show_flags & INTNAME_NEW_FILE) != 0) {
            // 外部からインポート時
            file_type_1[0] = dirItem.convOriginalTypeFromFileName(name);
        }
    }

    /**
     * ダイアログ内の属性部分のレイアウトを作成
     *
     * @param parent     プロパティダイアログ
     * @param show_flags ダイアログ表示フラグ
     * @param file_path  外部からインポート時のファイルパス
     * @param sizer      sizer (Placeholder for a Java container/layout)
     * @param flags      flags (Placeholder for layout constraints)
     */
    @Override
    public void createControlsForAttrDialog(IntNameBox parent, int show_flags, String file_path, BoxLayout sizer, Object flags) {
        int file_type_1 = dirItem.getFileType1Pos();
        int file_type_2 = dirItem.getFileType2Pos();
        // wxChoice *comType1; // Java equivalent: JComboBox or AWT Choice

        int[] type1_arr = {file_type_1};
        int[] type2_arr = {file_type_2};
        SetFileTypeForAttrDialog(show_flags, file_path, type1_arr, type2_arr);
        file_type_1 = type1_arr[0];
        // file_type_2 = type2_arr[0]; // Not used in the original C++ snippet after this call

        String[] types1 = new String[gTypeNameSDOS_1.size()];
        int i = 0;
        for (String k : gTypeNameSDOS_1.keySet()) {
            types1[i++] = rb.getString(k);
        }

        // Placeholder for GUI component creation
        // wxStaticBoxSizer *staType1 = new wxStaticBoxSizer(new wxStaticBox(parent, wxID_ANY, _("File Type")), wxVERTICAL);
        // Choice comType1 = new Choice(parent, IDC_COMBO_TYPE1, wxDefaultPosition, wxDefaultSize, types1);
        Choice comType1 = new Choice(); // Java Choice component

        if (file_type_1 >= 0) {
            comType1.SetSelection(file_type_1);
        }
        // staType1->Add(comType1, flags);
        // sizer->Add(staType1, flags);

        // parent->Bind(wxEVT_CHOICE, &IntNameBox::OnChangeType1, parent, IDC_COMBO_TYPE1);
        parent.Bind(0, parent, IDC_COMBO_TYPE1); // Placeholder for binding
    }

    /**
     * 属性を変更した際に呼ばれるコールバック
     *
     * @param parent プロパティダイアログ
     */
    @Override
    public void changeTypeInAttrDialog(IntNameBox parent) {
        // wxChoice *comType1 = (wxChoice *)parent->FindWindow(IDC_COMBO_TYPE1);
        Choice comType1 = (Choice) parent.getComponent(IDC_COMBO_TYPE1);

        int sel = comType1.GetSelection();
        boolean editable = (sel >= TYPE_NAME_SDOS_DAT && sel <= TYPE_NAME_SDOS_OBJ);

        parent.setEditableStartAddress(editable);
        parent.SetEditableExecuteAddress(editable);
    }

    /**
     * 機種依存の属性を設定する
     *
     * @param parent  プロパティダイアログ
     * @param attr    プロパティの属性値
     * @param errinfo エラー情報
     * @return true
     */
    @Override
    public boolean setAttrInAttrDialog(IntNameBox parent, DiskBasicDirItemAttr attr, DiskBasicError errinfo) {
        // wxChoice *comType1 = (wxChoice *)parent->FindWindow(IDC_COMBO_TYPE1);
        Choice comType1 = (Choice) parent.getComponent(IDC_COMBO_TYPE1);

        int t1 = comType1.getSelection();
        if (t1 < 0) t1 = FILETYPE_SDOS_BAS1;

        // Map list position (TYPE_NAME_SDOS_XXX) to actual file type value (FILETYPE_SDOS_XXX)
        int fileTypeOrigin = (int) Utils.valueAt(gTypeNameSDOS_1, t1);

        attr.setFileAttr(dirItem.getBasic().getFormatTypeNumber(), 0, fileTypeOrigin);

        return true;
    }

    /**
     * 属性値を加工する
     *
     * @param attr    プロパティの属性値
     * @param errinfo エラー情報
     * @return true
     */
    public boolean processAttr(DiskBasicDirItemAttr attr, DiskBasicError errinfo) {
        int t1 = attr.getFileOriginAttr(0);

        // BASICの固定アドレス設定
        switch (t1) {
            case FILETYPE_SDOS_BAS1:
            case FILETYPE_SDOS_BAS2:
                // BASICの場合、ロードアドレス、実行アドレスを固定で設定
                attr.setStartAddress(dirItem.getBasic().diskBasicParam.getVariousIntegerParam("DefaultStartAddress"));
                attr.setExecuteAddress(dirItem.getBasic().diskBasicParam.getVariousIntegerParam("DefaultExecuteAddress"));
                break;
        }
        return true;
    }
}
