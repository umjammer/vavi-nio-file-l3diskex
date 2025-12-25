/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package l3diskex.ui.dirItem;

import java.util.ArrayList;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JTextField;

import l3diskex.Utils;
import l3diskex.basicfmt.DiskBasicDirItem.DiskBasicDirItemAttr;
import l3diskex.basicfmt.diritem.DiskBasicDirItemC1541;
import l3diskex.basicfmt.DiskBasicError;
import l3diskex.ui.IntNameBox;
import l3diskex.ui.UiDirItem;

import static l3diskex.basicfmt.diritem.DiskBasicDirItemC1541.FILETYPE_MASK_C1541_REL;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemC1541.TYPE_NAME_C1541_DEL;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemC1541.TYPE_NAME_C1541_REL;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemC1541.typeNameC1541;
import static l3diskex.ui.IntNameBox.INTNAME_NEW_FILE;


/**
 * UiDirItemC1541.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2025-10-20 nsano initial version <br>
 */
public class UiDirItemC1541 extends UiDirItem {

    static final int IDC_COMBO_TYPE1 = 51;
    static final int IDC_TEXT_RECSIZE = 52;
    static final int IDC_TEXT_SIDESEC = 53;

    DiskBasicDirItemC1541 dirItem;

    /**
     * Get start position of Allocation Map (for dialog)
     */
    @Override
    public void getStartNumOnFat(int[] track_num, int[] side_num, int[] sector_num) {
        track_num[0] = basic.getManagedTrackNumber();
        side_num[0] = 0;
        sector_num[0] = basic.getSectorNumberBase();
    }

    /**
     * Get end position of Allocation Map (for dialog)
     */
    @Override
    public void getEndNumOnFat(int[] track_num, int[] side_num, int[] sector_num) {
        track_num[0] = basic.getManagedTrackNumber();
        side_num[0] = 0;
        sector_num[0] = basic.getSectorNumberBase();
    }

    /**
     * Title name (for dialog)
     */
    @Override
    public String getTitleForFat() {
        return "Allocation Map";
    }

    /** Set file attributes before displaying dialog on import */
    public void setFileTypeForAttrDialog(int show_flags, String name, int[] file_type_1, int[] file_type_2) {
        // INTNAME_NEW_FILE is a mock constant for new file
        if ((show_flags & 0x01) != 0) { // Assuming INTNAME_NEW_FILE = 0x01
            // When importing from external
            file_type_1[0] = dirItem.convOriginalTypeFromFileName(name);
        }
    }

    /// Set attributes in dialog
    /// @param show_flags      Dialog display flag
    /// @param  name           File name
    /// @param [out] file_type_1    Passed to CreateControlsForAttrDialog()
    /// @param [out] file_type_2    Passed to CreateControlsForAttrDialog()
    // This is the private method SetFileTypeForAttrDialog, implemented above as a helper.

    /** Create layout for attribute part in dialog */
    @Override
    public void createControlsForAttrDialog(IntNameBox parent, int show_flags, String file_path, BoxLayout sizer, Object flags) {
        int[] file_type_1_arr = {dirItem.getFileType1()};
        int[] file_type_2_arr = {0};
        JComboBox comType1 = new JComboBox(); // Mock
        JTextField txtRecSize = new JTextField(); // Mock
        JTextField txtSideSec = new JTextField(); // Mock

        setFileTypeForAttrDialog(show_flags, file_path, file_type_1_arr, file_type_2_arr);
        int file_type_1 = file_type_1_arr[0];

        List<String> types1 = new ArrayList<>();
        for (int i = TYPE_NAME_C1541_DEL; i <= TYPE_NAME_C1541_REL; i++) {
            types1.add(Utils.keyAt(typeNameC1541, i)); // Mock of wxGetTranslation
        }
        // Mocking UI creation
        // BoxLayout *staType1 = new BoxLayout(...);
        // comType1 = new wxChoice(...);
        int type1pos = dirItem.convFileType1Pos(file_type_1);
        comType1.setSelectedIndex(type1pos);
        // staType1->Add(comType1, flags);
        // sizer->Add(staType1, flags);

        // Dimension tsize(INTNAME_COLUMN_WIDTH, -1);
        // DimensionrFlags atitle = DimensionrFlags().Align(wxALIGN_CENTER_VERTICAL);

        // BoxLayout *staType2 = new BoxLayout(...);
        // wxFlexGridSizer *szrG = new wxFlexGridSizer(3, 4, 4);

        Object[] txtRecSizeArr = {txtRecSize};
        // IntNameBox.CreateFileSize(parent, IDC_TEXT_RECSIZE, "Record Length", 12, true, null, null, null, txtRecSizeArr); // Mock call
        txtRecSize = (JTextField) txtRecSizeArr[0]; // Assuming it's set by the mock call

        txtRecSize.setColumns(3);
        int rec_len = dirItem.getRecordLength();
        txtRecSize.setText(String.valueOf(rec_len));
        txtRecSize.setEditable((show_flags & INTNAME_NEW_FILE) != 0 && file_type_1 == FILETYPE_MASK_C1541_REL);
        // staType2->Add(szrG, flags);

        if ((show_flags & INTNAME_NEW_FILE) == 0) {
            // wxFlexGridSizer *szrG = new wxFlexGridSizer(3, 4, 4);
            Object[] txtSideSecArr = {txtSideSec};
            // IntNameBox::CreateFileSize(parent, IDC_TEXT_SIDESEC, "Size of Side Sector", 20, true, null, null, szrG, txtSideSecArr); // Mock call
            txtSideSec = (JTextField) txtSideSecArr[0]; // Assuming it's set by the mock call

            int sid_size = dirItem.ssGroups.getSize();
            String sid_size_str = IntNameBox.convFileSize(sid_size);
            txtSideSec.setText(sid_size_str);
            txtSideSec.setEditable(false);
            // staType2->Add(szrG, flags);
        }

        // sizer->Add(staType2, flags);
    }

    /** Set values in dialog */
    @Override
    public void initializeForAttrDialog(IntNameBox parent, int show_flags, int[] user_data) {
        // Implementation is empty in C++, so it remains empty here.
    }

    /** Callback called when attribute is changed */
    @Override
    public void changeTypeInAttrDialog(IntNameBox parent) {
        // Implementation is empty in C++, so it remains empty here.
    }

    /** Set machine dependent attributes */
    @Override
    public boolean setAttrInAttrDialog(IntNameBox parent, DiskBasicDirItemAttr attr, DiskBasicError errinfo) {
        JComboBox comType1 = (JComboBox) parent.getComponent(IDC_COMBO_TYPE1);
        JTextField txtRecSize = (JTextField) parent.getComponent(IDC_TEXT_RECSIZE);
        boolean valid = true;
        int ori = 0;

        int type1pos = comType1.getSelectedIndex();

        ori = (int) Utils.valueAt(typeNameC1541, type1pos);

        int rec_siz = Integer.parseInt(txtRecSize.getText());

        if (type1pos == TYPE_NAME_C1541_REL) {
            // Check record size for REL format on new file
            valid &= (0 < rec_siz && rec_siz < 256);
            if (!valid) {
                // errinfo.SetError(DiskBasicError::ERRV_INVALID_VALUE_IN, _("Record Length").t_str()); // Mock call
            }
        } else {
            valid = true;
        }
        ori |= (rec_siz << 8);

        attr.setFileAttr(dirItem.getBasic().getFormatTypeNumber(), 0, ori);

        return valid;
    }

    // Skipping ValidateFileName and SetOptionalAttr as they are commented out in C++
}
