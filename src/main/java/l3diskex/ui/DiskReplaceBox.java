package l3diskex.ui;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JWindow;

import l3diskex.basicfmt.DiskBasicDirItemMSX;
import l3diskex.diskimg.DiskImage.DiskImageDisk;
import l3diskex.diskimg.DiskImage.DiskImageFile;


// ----------------------------------
// The translated DiskReplaceBox class
// ----------------------------------
public class DiskReplaceBox extends JDialog {

    /*------------------------------
        Fields corresponding to the dialog widgets
    ------------------------------*/
    private JCheckBox comDisk;
    private List<DiskReplaceNumber> numDisk = new ArrayList<>();

    /*------------------------------
        Constants (IDs)
    ------------------------------*/
    public static final int IDC_COMBO_DISK = 1;

    /*
        Constructor
    */
    public DiskReplaceBox(JWindow parent, int id,
                          int side_number,
                          DiskImageFile src_file,
                          DiskImageDisk tag_disk) {
        super(parent, id, "Replace data in a disk",
              wxDefaultPosition, wxDefaultSize,
              wxCAPTION | wxCLOSE_BOX);

        // --- layout stubs (no real effect) ---------------------------------------
        DiskBasicDirItemMSX.wxBoxLayout szrAll = new DiskBasicDirItemMSX.wxBoxLayout(wxVERTICAL);

        wxStaticText lbl;
        lbl = new wxStaticText(this, -1, "Select a source disk to replace:");
        szrAll.Add(lbl, 0);

        comDisk = new JComboBox(this, IDC_COMBO_DISK, 0, 0);
        szrAll.Add(comDisk, 0);

        /* ---- build the disk list ------------------------------------------- */
        String str;
        for (int idx = 0; idx < src_file.Count(); idx++) {
            DiskImageDisk disk = src_file.GetDisk(idx);
            String sstr = "";
            str = String.format("[disk %d] ", idx) + disk.GetDiskDescription();

            if (disk.IsReversible() &&
                (side_number >= 0 || tag_disk.GetSidesPerDisk() == 1)) {
                /* AB 面があり、片面だけ置換する場合 */
                sstr = " ( side %c ( side %d ))";
                sstr = String.format(sstr, 'A', 0);
                comDisk.Append(str + sstr);
                numDisk.add(new DiskReplaceNumber(idx, 0));
            }
            comDisk.Append(str + sstr);
            numDisk.add(new DiskReplaceNumber(idx, 0));

            if (disk.IsReversible() &&
                (side_number >= 0 || tag_disk.GetSidesPerDisk() == 1)) {
                sstr = " ( side %c ( side %d ))";
                sstr = String.format(sstr, 'B', 1);
                comDisk.Append(str + sstr);
                numDisk.add(new DiskReplaceNumber(idx, 1));
            }
        }
        comDisk.SetSelection(0);

        /* ---- target disk description --------------------------------------- */
        lbl = new wxStaticText(this, -1, "Target disk:");
        szrAll.Add(lbl, 0);

        String tdiskStr = String.format("[disk %d] ", tag_disk.GetNumber()) + tag_disk.GetDiskDescription();
        if (side_number >= 0) {
            tdiskStr += " ( side " + (char)('A' + side_number) + " ( side " + side_number + " ))";
        }
        wxStaticText txtTDisk = new wxStaticText(this, -1, tdiskStr, wxDefaultPosition, wxDefaultSize, wxBORDER_THEME);
        szrAll.Add(txtTDisk, 0);

        /* ---- confirmation message ------------------------------------------ */
        lbl = new wxStaticText(this, -1,
                "Are you sure to replace data in target disk by the selected disk?");
        szrAll.Add(lbl, 0);

        /* ---- OK / Cancel buttons ------------------------------------------- */
        CreateButtonSizer(wxOK | wxCANCEL);
        szrAll.Add(new wxStaticText(this, -1, ""), 0);   // placeholder for buttons

        SetSizerAndFit(szrAll);
    }

    /*
        Public helper functions
    */
    public int ShowModal() {
        return super.ShowModal();
    }

    /*
        Event handlers (no real event table – just simple method calls)
    */
    public void OnOK(ActionEvent event) {
        if (IsModal()) {
            EndModal(wxID_OK);
        } else {
            SetReturnCode(wxID_OK);
            this.Show(false);
        }
    }

    public void OnCancel(ActionEvent event) {
        if (IsModal()) {
            EndModal(wxID_CANCEL);
        } else {
            SetReturnCode(wxID_CANCEL);
            this.Show(false);
        }
    }

    /*
        Property getters
    */
    public int GetSelectedDiskNumber() {
        int idx = comDisk.GetSelection();
        return numDisk.get(idx).disknum;
    }

    public int GetSelectedSideNumber() {
        int idx = comDisk.GetSelection();
        return numDisk.get(idx).sidenum;
    }

    /*
        Inner class definitions (replicating the C++ header)
    */
    /* DiskReplaceNumber – a simple data holder */
    private class DiskReplaceNumber {
        public int disknum;
        public int sidenum;

        public DiskReplaceNumber() {
            disknum = 0;
            sidenum = 0;
        }

        public DiskReplaceNumber(int disknum_, int sidenum_) {
            disknum = disknum_;
            sidenum = sidenum_;
        }

        @Override
        public String toString() {
            return "DiskReplaceNumber{" + "disknum=" + disknum + ", sidenum=" + sidenum + '}';
        }
    }

    /* -----------------------------------
        DiskReplaceNumbers – a dynamic list
    -----------------------------------*/
    /*  The original C++ code uses WX_DECLARE_OBJARRAY, which expands to a
        dynamically sized array.  In Java an ArrayList is the natural equivalent.
        The field is named exactly as in the header. */
}
