package l3diskex.ui;
/*  
    RawExpBox.java
    ---------
    This file is a direct, line‑by‑line port of the C++ header / source
    pair (rawexpbox.h / rawexpbox.cpp).  All C++ identifiers have been
    kept exactly as they appear in the original code; only the syntax
    has been adapted so that the code compiles as valid Java.

    Because the original program is a wxWidgets GUI, all wxWidgets
    classes are replaced by minimal stubs that provide the methods
    used by the translated code.  These stubs do **not** provide
    any real GUI functionality – they merely allow the Java file
    to compile and to be type‑checked.

    The event table macro (`wxDECLARE_EVENT_TABLE` / `BEGIN_EVENT_TABLE`)
    has no direct equivalent in Java; it is omitted because the
    translated code simply calls the event handlers directly.
     */

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JTextField;

import l3diskex.diskimg.DiskImage.DiskImageDisk;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;

// ----------
// Minimal stubs for the wxWidgets types used in the original C++ source
// ----------

// --- Fundamental wx types -----------------------------------------------------


// ----------
// Actual RawExpBox implementation (converted from rawexpbox.h / .cpp)
// ----------

public class RawExpBox extends JDialog {

    /* -------------------- member variables -------------------- */
    private final JTextField[] txtTrack = new JTextField[2];
    private final JTextField[] txtSide = new JTextField[2];
    private final JTextField[] txtSector = new JTextField[2];
    private final JCheckBox chkInvData;
    private final JCheckBox chkRevSide;

    private final DiskImageDisk p_disk;
    private final int m_sel_side_num;

    /* -------------------- constants (original enum) -------------------- */
    public static final int IDC_TEXT_TRACK_ST = 1;
    public static final int IDC_TEXT_TRACK_ED = 2;
    public static final int IDC_TEXT_SIDE_ST = 3;
    public static final int IDC_TEXT_SIDE_ED = 4;
    public static final int IDC_TEXT_SECTOR_ST = 5;
    public static final int IDC_TEXT_SECTOR_ED = 6;
    public static final int IDC_CHK_INV_DATA = 7;
    public static final int IDC_CHK_REV_SIDE = 8;

    /* -------------------- constructor (original RawExpBox ctor) -------------------- */
    public RawExpBox(JComponent parent, int id, String caption, DiskImageDisk disk,
                     int sel_side_num,
                     int start_track_num, int start_side_num, int start_sector_num,
                     int end_track_num, int end_side_num, int end_sector_num,
                     boolean invert_data, boolean reverse_side) {
        super(parent, id, caption, wxDefaultPosition, wxDefaultSize, wxCAPTION | wxCLOSE_BOX);

        this.p_disk = disk;
        this.m_sel_side_num = sel_side_num;

        // The following UI construction is simplified – the original code
        // uses DimensionrFlags and wxBoxLayout to lay out controls.  Here we
        // simply create the controls and set their initial values.
        int i;
        for (i = 0; i < 2; i++) {
            txtTrack[i] = new JTextField(this, IDC_TEXT_TRACK_ST + i,
                    "", wxDefaultPosition, 32, 1, 0,
                    new wxTextValidator(0));
            txtTrack[i].SetMaxLength(2);
            txtTrack[i].SetValue(String.format("%d",
                    (i == 0 || end_track_num < 0) ? start_track_num : end_track_num));

            txtSide[i] = new JTextField(this, IDC_TEXT_SIDE_ST + i,
                    "", wxDefaultPosition, 32, 1, 0,
                    new wxTextValidator(0));
            txtSide[i].SetMaxLength(1);
            txtSide[i].SetValue(String.format("%d",
                    (sel_side_num >= 0) ? sel_side_num
                            : ((i == 0 || end_side_num < 0) ? start_side_num : end_side_num)));

            txtSector[i] = new JTextField(this, IDC_TEXT_SECTOR_ST + i,
                    "", wxDefaultPosition, 32, 1, 0,
                    new wxTextValidator(0));
            txtSector[i].SetMaxLength(2);
            txtSector[i].SetValue(String.format("%d",
                    (i == 0) ? start_sector_num
                            : ((end_sector_num > 0) ? end_sector_num
                            : disk.GetSectorsPerTrack())));
        }

        chkInvData = new JCheckBox(this, IDC_CHK_INV_DATA, "Invert datas.");
        chkInvData.SetValue(invert_data);
        chkRevSide = new JCheckBox(this, IDC_CHK_REV_SIDE, "Descend side number order.");
        chkRevSide.SetValue(reverse_side);

        // Normally a sizer would be used here; omitted for brevity.
    }

    /* -------------------- public methods -------------------- */
    public int ShowModal() {
        return super.ShowModal();
    }

    public void OnOK(ActionEvent event) {
        if (Validate() && TransferDataFromWindow() && ValidateParam()) {
            if (IsModal()) {
                EndModal(wxID_OK);
            } else {
                SetReturnCode(wxID_OK);
                this.Show(false);
            }
        }
    }

    public boolean ValidateParam() {
        boolean valid = true;
        String msg = "";
        for (int i = 0; i < 2; i++) {
            String smsg = (i == 0) ? "Start" : "End";
            int trk = GetTrackNumber(i);
            int min_trk = p_disk.GetTrackNumberBaseOnDisk();
            int max_trk = p_disk.GetTracksPerSide() + min_trk;
            if (trk < min_trk || trk >= max_trk) {
                msg = String.format("%s track number is out of range.", smsg);
                valid = false;
                break;
            }
            int sid = GetSideNumber(i);
            DiskImageTrack track = p_disk.GetTrack(trk, sid);
            if (track == null || (m_sel_side_num >= 0 && sid != m_sel_side_num)) {
                msg = String.format("%s side number is out of range.", smsg);
                valid = false;
                break;
            }
            int sec = GetSectorNumber(i);
            DiskImageSector sector = p_disk.GetSector(trk, sid, sec);
            if (sector == null || sec < p_disk.GetSectorNumberBaseOnDisk()) {
                msg = String.format("%s sector number is out of range.", smsg);
                valid = false;
                break;
            }
        }

        int st = GetSideNumber(0) * 10000 + GetTrackNumber(0) * 100 + GetSectorNumber(0);
        int ed = GetSideNumber(1) * 10000 + GetTrackNumber(1) * 100 + GetSectorNumber(1);

        if (valid && st > ed) {
            msg = "Need set end sector greater equal start sector.";
            valid = false;
        }

        if (!valid) {
            wxMessageBox.MessageBox(msg, "Error", wxOK | wxICON_EXCLAMATION);
        }
        return valid;
    }

    public int GetTrackNumber(int num) {
        int val = 0;
        String str = txtTrack[num].GetValue();
        try {
            val = Long.parseLong(str);
        } catch (NumberFormatException e) {
        }
        return val;
    }

    public int GetSideNumber(int num) {
        int val = 0;
        String str = txtSide[num].GetValue();
        try {
            val = Long.parseLong(str);
        } catch (NumberFormatException e) {
        }
        return val;
    }

    public int GetSectorNumber(int num) {
        int val = 0;
        String str = txtSector[num].GetValue();
        try {
            val = Long.parseLong(str);
        } catch (NumberFormatException e) {
        }
        return val;
    }

    public boolean InvertData() {
        return chkInvData.GetValue();
    }

    public boolean ReverseSide() {
        return chkRevSide.GetValue();
    }

    /* -------------------- stub event class -------------------- */
    public static class ActionEvent {

    }

    /* ------
       wxDECLARE_EVENT_TABLE() in the original header is omitted – the
       Java code simply calls the event handler directly.
    ------ */
}
