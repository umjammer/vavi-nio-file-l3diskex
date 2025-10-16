package l3diskex.ui;

import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ItemEvent;
import java.util.ArrayList;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.border.EmptyBorder;

import l3diskex.diskimg.DiskImage.DiskImageDisk;
import l3diskex.diskimg.DiskParam;


/**
 * Disk attribute panel – equivalent to UiDiskDiskAttr (wxPanel)
 */
public class UiDiskDiskAttr extends JPanel {

    /* -- */
    /*                      Constants / IDs (wx style)                        */
    /* -- */
    private static final int TEXT_ATTR_SIZE = 500;

    public static final int IDC_TXT_ATTR    = 1;
    public static final int IDC_BTN_CHANGE  = 2;
    public static final int IDC_COM_DENSITY = 3;
    public static final int IDC_CHK_WPROTECT = 4;

    /* -- */
    /*                              Fields                                    */
    /* -- */
    private JComponent parent;          // parent window (JWindow equivalent)
    private UiDiskFrame frame;          // reference to the main frame

    private JTextArea   txtAttr;        // disk description area
    private JButton     btnChange;      // “Change” button
    private JComboBox<String> comDensity;  // density selector
    private JCheckBox  chkWprotect;      // write‑protect checkbox
    private JPanel      szrButtons;      // horizontal box for buttons

    private DiskImageDisk p_disk;        // current disk image

    /* -- */
    /*                        Constructor / Initialisation                  */
    /* -- */
    public UiDiskDiskAttr(UiDiskFrame parentframe, JComponent parent) {
        super();
        this.parent = parent;
        this.frame  = parentframe;
        this.p_disk = null;

        /* Layout – vertical box */
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(new EmptyBorder(2, 2, 2, 2));

        /* ----------------------------------------------------------------- */
        /* txtAttr – read‑only text area                                     */
        /* ----------------------------------------------------------------- */
        txtAttr = new JTextArea();
        txtAttr.setEditable(false);
        txtAttr.setColumns(TEXT_ATTR_SIZE);
        txtAttr.setLineWrap(false);
        txtAttr.setWrapStyleWord(false);
        txtAttr.setBorder(new EmptyBorder(2, 2, 2, 2));
        add(txtAttr);

        /* ----------------------------------------------------------------- */
        /* szrButtons – horizontal box for the buttons                       */
        /* ----------------------------------------------------------------- */
        szrButtons = new JPanel();
        szrButtons.setLayout(new BoxLayout(szrButtons, BoxLayout.X_AXIS));

        /* ----- btnChange ------------------------------------------------ */
        btnChange = new JButton("Change");
        btnChange.setEnabled(false);
        btnChange.addActionListener(e -> OnButtonChange(e));
        szrButtons.add(btnChange);

        /* ----- comDensity ------------------------------------------------ */
        comDensity = new JComboBox<>();
        // Populate the list via the frame’s DiskImage (stubbed below)
        frame.getDiskImage().getDensityNames(new ArrayList<>());
        if (comDensity.getItemCount() == 0) {
            comDensity.addItem("Density 1");
        }
        comDensity.setSelectedIndex(0);
        comDensity.addActionListener(e -> OnComboDensity(e));
        szrButtons.add(comDensity);

        /* ----- chkWprotect ---------------------------------------------- */
        chkWprotect = new JCheckBox("Write Protect");
        chkWprotect.setEnabled(false);
        chkWprotect.addItemListener(e -> OnCheckWriteProtect(e));
        szrButtons.add(chkWprotect);

        add(szrButtons);

        /* ----------------------------------------------------------------- */
        /* Set default font from the main frame                              */
        /* ----------------------------------------------------------------- */
        Font font = frame.getDefaultListFont();
        txtAttr.setFont(font);

        /* ----------------------------------------------------------------- */
        /* Resize listener – simplified implementation                       */
        /* ----------------------------------------------------------------- */
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                OnSize(e);
            }
        });

        /* ----------------------------------------------------------------- */
        /* Initialise with empty data                                         */
        /* ----------------------------------------------------------------- */
        ClearData();
    }

    /* -- */
    /*                              Destructor                               */
    /* -- */
    // In Java the GC handles destruction – no explicit destructor needed.

    /* -- */
    /*                          Event Handlers                                */
    /* -- */
    /**
     * Called when the component is resized.
     * The original wxWidgets code moves the controls manually;
     * here we simply let Swing layout it.
     */
    public void OnSize(ComponentEvent event) {
        // Placeholder – layout managers handle resizing in Swing.
    }

    /**
     * Called when the “Change” button is pressed.
     */
    public void OnButtonChange(ActionEvent event) {
        ShowChangeDisk();
    }

    /**
     * Called when the density combo box changes.
     */
    public void OnComboDensity(ActionEvent event) {
        if (p_disk == null) return;
        p_disk.setDensity(GetDiskDensity());
    }

    /**
     * Called when the write‑protect checkbox is toggled.
     */
    public void OnCheckWriteProtect(ItemEvent event) {
        if (p_disk == null) return;
        p_disk.setWriteProtect(event.getStateChange() == ItemEvent.SELECTED);
    }

    /* -- */
    /*                            Helper Methods                             */
    /* -- */
    /**
     * Show a dialog to change disk parameters.
     */
    public void ShowChangeDisk() {
        if (p_disk == null) return;

        DiskParamBox dlg = new DiskParamBox(
                this,
                frame.getDiskImage(),
                DiskParamBox.CHANGE_DISK_PARAM,
                p_disk
        );

        int sts = dlg.showModal();
        if (sts == DiskParamBox.ID_OK) {
            DiskParam param = dlg.getParam();
            DiskParam titem = gDiskTemplates.find(param);
            if (titem != null) {
                param = titem;
            }

            p_disk.setDiskParam(param);
            p_disk.setParamChanged(!param.matchesExceptName(p_disk.getOriginalParam()));
            p_disk.setModify();
            p_disk.getFile().setBasicTypeHint(dlg.getCategory());
            p_disk.clearDiskBasics();

            frame.reSelectDiskList();
        }
    }

    /**
     * Set the current disk image and update the UI.
     */
    public void SetAttr(DiskImageDisk newdisk) {
        p_disk = newdisk;
        if (p_disk == null) return;

        String desc = p_disk.getDiskDescription();
        if (p_disk.isParamChanged()) {
            desc += " (Original: " + p_disk.getOriginalParam().getDiskDescription() + ")";
        }

        SetAttrText(desc);
        btnChange.setEnabled(true);
        SetDiskDensity(p_disk.getDensity());
        SetWriteProtect(p_disk.isWriteProtected());
    }

    /**
     * Set the text displayed in the attribute area.
     */
    public void SetAttrText(String val) {
        txtAttr.setText(val);
    }

    /**
     * Set the disk density.
     */
    public void SetDiskDensity(int val) {
        if (comDensity == null) return;

        if (val < 0) {
            comDensity.setEnabled(false);
            val = 0;
        } else {
            comDensity.setEnabled(true);
            int match = frame.getDiskImage().findDensity(val);
            if (match >= 0) {
                comDensity.setSelectedIndex(match);
            } else {
                String str = String.format("0x%02x", val);
                boolean found = false;
                for (int i = 0; i < comDensity.getItemCount(); i++) {
                    if (comDensity.getItemAt(i).equals(str)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    comDensity.addItem(str);
                }
                comDensity.setSelectedItem(str);
            }
        }
    }

    /**
     * Get the currently selected density value.
     */
    public int GetDiskDensity() {
        if (comDensity == null) return 0;

        int index = comDensity.getSelectedIndex();
        int match = frame.getDiskImage().findDensityByIndex(index);
        if (match < 0) {
            String str = comDensity.getSelectedItem();
            match = Utils.toInt(str);
        }
        return match;
    }

    /**
     * Set the write‑protect checkbox.
     */
    public void SetWriteProtect(boolean val, boolean enable) {
        chkWprotect.setEnabled(enable);
        chkWprotect.setSelected(val);
    }

    /**
     * Return the state of the write‑protect checkbox.
     */
    public boolean GetWriteProtect() {
        return chkWprotect.isSelected();
    }

    /**
     * Clear all data from the UI.
     */
    public void ClearData() {
        SetAttrText("");
        btnChange.setEnabled(false);
        SetDiskDensity(-1);
        SetWriteProtect(false, false);
    }

    /**
     * Set the font of the attribute text area.
     */
    public void SetListFont(Font font) {
        txtAttr.setFont(font);
    }

    /** Utility methods. */
    public static class Utils {
        public static int toInt(String str) {
            try {
                return Integer.parseInt(str.replaceAll("[^0-9a-fA-F]", ""), 16);
            } catch (Exception e) {
                return 0;
            }
        }
    }
}
