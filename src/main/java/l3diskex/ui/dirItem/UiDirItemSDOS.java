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
import l3diskex.basicfmt.diritem.DiskBasicDirItemSDOS;
import l3diskex.basicfmt.DiskBasicError;
import l3diskex.ui.IntNameBox;
import l3diskex.ui.UiDirItem;

import static l3diskex.basicfmt.diritem.DiskBasicDirItemSDOS.FILETYPE_SDOS_BAS1;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemSDOS.FILETYPE_SDOS_BAS2;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemSDOS.IDC_COMBO_TYPE1;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemSDOS.TYPE_NAME_SDOS_DAT;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemSDOS.TYPE_NAME_SDOS_OBJ;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemSDOS.typeNameSdos1;
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
     * Set attributes for dialog
     *
     * @param show_flags  Dialog display flag
     * @param name        File name
     * @param file_type_1 Passed to CreateControlsForAttrDialog()
     * @param file_type_2 Passed to CreateControlsForAttrDialog()
     */
    private void SetFileTypeForAttrDialog(int show_flags, String name, int[] file_type_1, int[] file_type_2) {
        if ((show_flags & INTNAME_NEW_FILE) != 0) {
            // When importing from external
            file_type_1[0] = dirItem.convOriginalTypeFromFileName(name);
        }
    }

    /**
     * Create layout for attribute part in dialog
     *
     * @param parent     Property dialog
     * @param show_flags Dialog display flag
     * @param file_path  File path when importing from external
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

        String[] types1 = new String[typeNameSdos1.size()];
        int i = 0;
        for (String k : typeNameSdos1.keySet()) {
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
     * Callback called when attribute is changed
     *
     * @param parent Property dialog
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
     * Set machine dependent attributes
     *
     * @param parent  Property dialog
     * @param attr    Attribute value of property
     * @param errinfo Error information
     * @return true
     */
    @Override
    public boolean setAttrInAttrDialog(IntNameBox parent, DiskBasicDirItemAttr attr, DiskBasicError errinfo) {
        // wxChoice *comType1 = (wxChoice *)parent->FindWindow(IDC_COMBO_TYPE1);
        Choice comType1 = (Choice) parent.getComponent(IDC_COMBO_TYPE1);

        int t1 = comType1.getSelection();
        if (t1 < 0) t1 = FILETYPE_SDOS_BAS1;

        // Map list position (TYPE_NAME_SDOS_XXX) to actual file type value (FILETYPE_SDOS_XXX)
        int fileTypeOrigin = (int) Utils.valueAt(typeNameSdos1, t1);

        attr.setFileAttr(dirItem.getBasic().getFormatTypeNumber(), 0, fileTypeOrigin);

        return true;
    }

    /**
     * Process attribute values
     *
     * @param attr    Attribute value of property
     * @param errinfo Error information
     * @return true
     */
    public boolean processAttr(DiskBasicDirItemAttr attr, DiskBasicError errinfo) {
        int t1 = attr.getFileOriginAttr(0);

        // BASIC fixed address setting
        switch (t1) {
            case FILETYPE_SDOS_BAS1:
            case FILETYPE_SDOS_BAS2:
                // For BASIC, set load address and execution address to fixed values
                attr.setStartAddress(dirItem.getBasic().diskBasicParam.getVariousIntegerParam("DefaultStartAddress"));
                attr.setExecuteAddress(dirItem.getBasic().diskBasicParam.getVariousIntegerParam("DefaultExecuteAddress"));
                break;
        }
        return true;
    }
}
