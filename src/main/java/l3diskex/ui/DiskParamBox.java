package l3diskex.ui;

import java.awt.Dialog;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Window;
import java.text.NumberFormat;
import java.util.ArrayList;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.ButtonGroup;
import javax.swing.JTextField;

import l3diskex.basicfmt.DiskBasicParam.DiskBasicParamPtrs;
import l3diskex.diskimg.DiskImage;
import l3diskex.diskimg.DiskImage.DiskImageDisk;
import l3diskex.diskimg.DiskParam;

import static l3diskex.diskimg.DiskImage.DiskImageSector.gSectorSizes;


public class DiskParamBox extends JDialog {

    public enum OpeFlags {
        SELECT_DISK_TYPE(1),
        ADD_NEW_DISK(2),
        CREATE_NEW_DISK(3),
        CHANGE_DISK_PARAM(4),
        SHOW_DISK_PARAM(5),
        REBUILD_TRACKS(6);

        private final int value;

        OpeFlags(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public static final int SHOW_ALL = 0xffff;
    public static final int SHOW_CATEGORY = 0x0001;
    public static final int SHOW_TEMPLATE = 0x0002;
    public static final int SHOW_TEMPLATE_ALL = 0x0003;
    public static final int SHOW_DISKLABEL_ALL = 0x0010;

    private JComboBox<String> comCategory;
    private JComboBox<String> comTemplate;
    private JTextField txtTracks;
    private JTextField txtSides;
    private JTextField txtSectors;
    private JComboBox<String> comSecSize;
    private JTextField txtSecIntl;
    private JComboBox<String> comNumbSec;
    private JTextField txtFirstTrack;
    private JTextField txtFirstSide;
    private JTextField txtFirstSector;
    private JTextField txtDiskSize;

    private JTextField txtDiskName;
    private JComboBox<String> comDensity;
    private JCheckBox chkWprotect;
    private final ButtonGroup[] radSingle = new ButtonGroup[4];
    private JTextField txtSingleSectors;
    private JComboBox<String> comSingleSecSize;

    private final DiskImage diskImage;
    private final OpeFlags opeFlags;
    private final int showFlags;
    private final DiskBasicParamPtrs diskParams;
    private final DiskParam manualParam;
    private boolean nowManualSetting;

    private final ArrayList<String> typeNames = new ArrayList<>();

    private static final String[] NUMBER_SECTOR = {
            "By each side (default)",
            "By each track (for FLEX)"
    };

    public static final int IDC_COMBO_CATEGORY = 1;
    public static final int IDC_COMBO_TEMPLATE = 2;
    public static final int IDC_TEXT_TRACKS = 3;
    public static final int IDC_TEXT_SIDES = 4;
    public static final int IDC_TEXT_SECTORS = 5;
    public static final int IDC_COMBO_SECSIZE = 6;
    public static final int IDC_TEXT_INTERLEAVE = 7;
    public static final int IDC_COMBO_NUMBSEC = 8;
    public static final int IDC_TEXT_FIRST_TRACK = 9;
    public static final int IDC_TEXT_FIRST_SIDE = 10;
    public static final int IDC_TEXT_FIRST_SECTOR = 11;
    public static final int IDC_TEXT_DISKSIZE = 12;
    public static final int IDC_TEXT_DISKNAME = 13;
    public static final int IDC_COMBO_DENSITY = 14;
    public static final int IDC_CHK_WPROTECT = 15;
    public static final int IDC_RADIO_SINGLE_NONE = 16;
    public static final int IDC_RADIO_SINGLE_ALL = 17;
    public static final int IDC_RADIO_SINGLE_T00 = 18;
    public static final int IDC_RADIO_SINGLE_T0A = 19;
    public static final int IDC_TEXT_SINGLE_SECTORS = 20;
    public static final int IDC_COMBO_SINGLE_SECSIZE = 21;

    public DiskParamBox(Window parent, int id, DiskImage image, OpeFlags opeFlags,
                        int selectNumber, DiskImageDisk disk, DiskBasicParamPtrs params,
                        DiskParam manualParam, int showFlags) {
        super(parent, "Disk Parameters", Dialog.ModalityType.APPLICATION_MODAL);

        this.diskImage = image;
        this.opeFlags = opeFlags;
        this.showFlags = showFlags;
        this.diskParams = params;
        this.manualParam = manualParam;
        this.nowManualSetting = false;

        initializeComponents();
        setupEventListeners();

        if (comCategory != null) comCategory.setSelectedIndex(0);
        setTemplateValues(true);

        if (manualParam != null) {
            setParamToControl(manualParam);
        }
        if (disk != null) {
            setParamFromDisk(disk);
        }

        boolean useTemplate = ((showFlags & SHOW_TEMPLATE_ALL) != 0);
        if (useTemplate) {
            int selNum = 0;
            if (manualParam != null) {
                selNum = gDiskTemplates.size();
            } else if (selectNumber >= 0) {
                selNum = selectNumber;
            } else if (disk != null) {
                selNum = findTemplate(disk);
            }
            comTemplate.setSelectedIndex(selNum);
            setParamOfIndex(selNum);
        } else {
            boolean ena = ((showFlags & SHOW_DISKLABEL_ALL) != 0);
            if (txtDiskName != null) txtDiskName.setEnabled(ena);
            if (comDensity != null) comDensity.setEnabled(ena);
            if (chkWprotect != null) chkWprotect.setEnabled(ena);
        }

        pack();
        setLocationRelativeTo(parent);
    }

    private void initializeComponents() {
        // Create and initialize all UI components here
        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        // Initialize all components similar to the C++ version
        // Example for one component:
        comCategory = new JComboBox<>();
        comTemplate = new JComboBox<>();
        txtTracks = new JTextField(4);
        // ... Initialize all other components
    }

    private void setupEventListeners() {
        comCategory.addActionListener(e -> onCategoryChanged());
        comTemplate.addActionListener(e -> onTemplateChanged());
        // Add other listeners
    }

    private int findTemplate(DiskImageDisk disk) {
        int idx = gDiskTemplates.indexOf(disk.getDiskTypeName());
        if (idx < 0) {
            idx = gDiskTemplates.size();
        }
        return idx;
    }

    // Implement all other methods from the C++ class
    public void setTemplateValues(boolean all) {
        if (diskParams != null && manualParam == null) {
            setTemplateValuesFromParams();
        } else {
            setTemplateValuesFromGlobals(all);
        }
    }

    // More instance fields and getters/setters
    private void setParamFromTemplate(DiskParam item) {
        nowManualSetting = false;

        setParamToControl(item);
        setDensity(item.getParamDensity());

        txtTracks.setEnabled(false);
        txtSides.setEnabled(false);
        txtSectors.setEnabled(false);
        comSecSize.setEnabled(false);
        txtSecIntl.setEnabled(false);
        comNumbSec.setEnabled(false);
        if (comDensity != null) comDensity.setEnabled(false);
        txtFirstTrack.setEnabled(false);
        txtFirstSide.setEnabled(false);
        txtFirstSector.setEnabled(false);
        radSingle[0].setEnabled(false);
        radSingle[1].setEnabled(false);
        radSingle[2].setEnabled(false);
        radSingle[3].setEnabled(false);
        txtSingleSectors.setEnabled(false);
        comSingleSecSize.setEnabled(false);
    }

    private void setParamFromDisk(DiskImageDisk disk) {
        nowManualSetting = false;

        setParamToControl(disk);
        if (txtDiskName != null) txtDiskName.setText(disk.getName(true));
        if (chkWprotect != null) chkWprotect.setSelected(disk.isWriteProtected());
        if (comDensity != null) comDensity.setSelectedIndex(diskImage.findDensity(disk.getDensity()));

        // Disable controls
        txtTracks.setEnabled(false);
        txtSides.setEnabled(false);
        txtSectors.setEnabled(false);
        comSecSize.setEnabled(false);
        txtSecIntl.setEnabled(false);
        comNumbSec.setEnabled(false);
        txtFirstTrack.setEnabled(false);
        txtFirstSide.setEnabled(false);
        txtFirstSector.setEnabled(false);
        radSingle[0].setEnabled(false);
        radSingle[1].setEnabled(false);
        radSingle[2].setEnabled(false);
        radSingle[3].setEnabled(false);
        txtSingleSectors.setEnabled(false);
        comSingleSecSize.setEnabled(false);
    }

    private void setParamForManual() {
        // Enable manual input controls
        txtTracks.setEnabled(true);
        txtSides.setEnabled(true);
        txtSectors.setEnabled(true);
        comSecSize.setEnabled(true);
        txtSecIntl.setEnabled(true);
        comNumbSec.setEnabled(true);
        if (comDensity != null) comDensity.setEnabled((showFlags & SHOW_DISKLABEL_ALL) != 0);
        txtFirstTrack.setEnabled(true);
        txtFirstSide.setEnabled(true);
        txtFirstSector.setEnabled(true);
        radSingle[0].setEnabled(true);
        radSingle[1].setEnabled(true);
        radSingle[2].setEnabled(true);
        radSingle[3].setEnabled(true);
        txtSingleSectors.setEnabled(true);
        comSingleSecSize.setEnabled(true);

        nowManualSetting = true;
    }

    private void setParamToControl(DiskParam item) {
        txtTracks.setText(String.valueOf(item.getTracksPerSide()));
        txtSides.setText(String.valueOf(item.getSidesPerDisk()));
        txtSectors.setText(String.valueOf(item.getSectorsPerTrack()));
        comSecSize.setSelectedItem(String.valueOf(item.getSectorSize()));
        txtSecIntl.setText(String.valueOf(item.getInterleave()));
        comNumbSec.setSelectedIndex(item.getNumberingSector());
        txtFirstTrack.setText(String.valueOf(item.getTrackNumberBaseOnDisk()));
        txtFirstSide.setText(String.valueOf(item.getSideNumberBaseOnDisk()));
        txtFirstSector.setText(String.valueOf(item.getSectorNumberBaseOnDisk()));

        txtDiskSize.setText(NumberFormat.getInstance().format(item.calcDiskSize()));

        int singleSecs = 0;
        int singleSize = 0;
        int singlePos = item.hasSingleDensity(new int[] {singleSecs}, new int[] {singleSize});
        radSingle[singlePos].setSelected(true);
        txtSingleSectors.setText(String.valueOf(singleSecs));
        comSingleSecSize.setSelectedItem(String.valueOf(singleSize));
    }

    public String GetCategory() {
        String str = "";
        if (comCategory != null) {
            int num = comCategory.getSelectedIndex();
            if (num > 0) {
                str = gDiskBasicTemplates.getCategoryName(num-1);
            }
        }
        return str;
    }

    public int GetTracksPerSide() {
        try {
            return Integer.parseInt(txtTracks.getText());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public int GetSidesPerDisk() {
        try {
            return Integer.parseInt(txtSides.getText());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public int GetSectorsPerTrack() {
        try {
            return Integer.parseInt(txtSectors.getText());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public int GetSectorSize() {
        int idx = comSecSize.getSelectedIndex();
        return gSectorSizes[idx];
    }

    public int GetInterleave() {
        try {
            return Integer.parseInt(txtSecIntl.getText());
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    public int GetNumberingSector() {
        return comNumbSec.getSelectedIndex();
    }

    public int GetFirstTrackNumber() {
        try {
            return Integer.parseInt(txtFirstTrack.getText());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public int GetFirstSideNumber() {
        try {
            return Integer.parseInt(txtFirstSide.getText());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public int GetFirstSectorNumber() {
        try {
            return Integer.parseInt(txtFirstSector.getText());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public String GetDiskName() {
        return txtDiskName != null ? txtDiskName.getText() : "";
    }

    public int GetDensity() {
        return comDensity != null ? comDensity.getSelectedIndex() : 0;
    }

    public byte GetDensityValue() {
        return diskImage.getDensity(GetDensity());
    }

    public boolean IsWriteProtected() {
        return chkWprotect != null ? chkWprotect.isSelected() : false;
    }

    public int GetSingleNumber() {
        int val = 0;
        val = (radSingle[1].isSelected() ? 1 :
                (radSingle[2].isSelected() ? 2 :
                        (radSingle[3].isSelected() ? 3 : 0)));
        return val;
    }

    public int GetSingleSectorsPerTrack() {
        try {
            return Integer.parseInt(txtSingleSectors.getText());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public int GetSingleSectorSize() {
        int idx = comSingleSecSize.getSelectedIndex();
        return gSectorSizes[idx];
    }
}
