package l3diskex.ui;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import javax.swing.InputVerifier;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import l3diskex.Parambase.ValidNameRule;
import l3diskex.basicfmt.BasicFmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicParam;
import l3diskex.basicfmt.DiskBasicParam.DiskBasicFormat;
import l3diskex.basicfmt.DiskBasicParam.DiskBasicParamPtrs;
import l3diskex.basicfmt.DiskBasicTemplates;
import l3diskex.diskimg.DiskImage.DiskImageDisk;
import l3diskex.diskimg.DiskParam.DiskParamName;
import l3diskex.ui.UiDiskDiskAttr.Utils;


// ----------
//  Main public class – mirrors the C++ BasicSelBox class
// ----------
public class BasicSelBox extends JDialog {

    // ------
    //  Constants (mirroring the C++ enum values)
    // ------
    public static final int IDC_LIST_BASIC = 1;
    public static final int IDC_VOLUME_CTRL = 2;
    public static final int SHOW_ATTR_CONTROLS = 0x01;

    // ------
    //  UI / data members
    // ------
    private final JComboBox<String> comBasic;                // list box
    private final VolumeCtrl volumeCtrl = new VolumeCtrl();  // volume controls
    private final DiskBasicParamPtrs params = new DiskBasicParamPtrs();
    private final DiskImageDisk p_disk;                      // disk passed in ctor

    // ------
    //  Constructor (mirrors the C++ BasicSelBox constructor)
    // ------
    public BasicSelBox(Frame parent,
                       int id,
                       DiskImageDisk disk,
                       DiskBasic basic,
                       int showFlags)
    {
        super(parent, "Select BASIC Type", true);

        this.p_disk = disk;

        // --
        //  List box
        // --
        comBasic = new JComboBox<>();
        this.getContentPane().add(comBasic, BorderLayout.CENTER);

        // --
        //  Populate list box from disk information
        // --
        List<DiskParamName> types = disk.getBasicTypes();           // placeholder
        String category = disk.getFile().getBasicTypeHint();   // placeholder

        // Dummy template object that provides params
        DiskBasicTemplates gDiskBasicTemplates = new DiskBasicTemplates();
        gDiskBasicTemplates.findParams(types, params);

        int cur_num = 0;
        int pos = 0;
        for (int n = 0; n < params.size(); n++) {
            DiskBasicParam param = params.get(n);
            if (param.getBasicTypeName().equals(basic.getBasicTypeName())) {
                cur_num = pos;
            } else if (param.getBasicCategoryName().equals(category)) {
                cur_num = pos;
            }
            comBasic.addItem(param.getBasicDescription());
            pos++;
        }
        if (comBasic.getItemCount() > 0) {
            comBasic.setSelectedIndex(cur_num);
        }

        // --
        //  Optional volume controls
        // --
        if ((showFlags & SHOW_ATTR_CONTROLS) != 0) {
            JPanel volPanel = volumeCtrl.createVolumePanel(this, IDC_VOLUME_CTRL);
            this.getContentPane().add(volPanel, BorderLayout.SOUTH);
        }

        // --
        //  Buttons
        // --
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new FlowLayout(FlowLayout.RIGHT));
        JButton okBtn = new JButton("OK");
        JButton cancelBtn = new JButton("Cancel");
        buttonPanel.add(okBtn);
        buttonPanel.add(cancelBtn);
        this.getContentPane().add(buttonPanel, BorderLayout.NORTH);

        // --
        //  Event wiring
        // --
        comBasic.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                int sel = comBasic.getSelectedIndex();
                if (sel >= 0) changeBasic(sel);
            }
        });

        okBtn.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                onOK();
            }
        });

        cancelBtn.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });

        // --
        //  Initial state
        // --
        changeBasic(cur_num);

        this.pack();
        this.setLocationRelativeTo(parent);
    }

    // ------
    //  Show modal dialog (mirrors wxDialog::ShowModal)
    // ------
    public int showModal() {
        this.setVisible(true);
        return this.getModalResult();
    }

    // ------
    //  OK button handler (mirrors BasicSelBox::OnOK)
    // ------
    private void onOK() {
        // Here we would validate and transfer data.  For the stub we simply close.
        this.setModalResult(JOptionPane.OK_OPTION);
        this.dispose();
    }

    // ------
    //  Change the selected BASIC type (mirrors BasicSelBox::ChangeBasic)
    // ------
    private void changeBasic(int sel) {
        DiskBasicParam param = params.get(sel);
        if (param == null) return;

        DiskBasicFormat fmt = param.getFormatType();
        if (fmt == null) return;

        volumeCtrl.enableVolumeName(
                fmt.HasVolumeName(),
                fmt.getValidVolumeName().getMaxLength(),
                fmt.getValidVolumeName());

        volumeCtrl.enableVolumeNumber(fmt.HasVolumeNumber());
        volumeCtrl.enableVolumeDate(fmt.HasVolumeDate());
    }

    // ------
    //  Retrieve the currently selected BASIC parameter (mirrors GetBasicParam)
    // ------
    public DiskBasicParam getBasicParam() {
        int num = comBasic.getSelectedIndex();
        if (num < 0) return null;
        return params.get(num);
    }

    // ------
    //  VolumeCtrl – mirrors the C++ VolumeCtrl class
    // ------
    private class VolumeCtrl {
        private static final int VOLUME_ROWS = 3;
        private final JLabel[] lblVolume = new JLabel[VOLUME_ROWS];
        private final JTextField[] txtVolume = new JTextField[VOLUME_ROWS];

        // Create the panel with the controls
        public JPanel createVolumePanel(Container parent, int id) {
            JPanel panel = new JPanel(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(4, 4, 4, 4);
            gbc.fill = GridBagConstraints.HORIZONTAL;

            String[] titles = { "Volume Name", "Volume Number", "Volume Date" };

            for (int i = 0; i < VOLUME_ROWS; i++) {
                lblVolume[i] = new JLabel(titles[i]);
                txtVolume[i] = new JTextField();

                gbc.gridx = 0;
                gbc.gridy = i;
                panel.add(lblVolume[i], gbc);

                gbc.gridx = 1;
                panel.add(txtVolume[i], gbc);
            }
            return panel;
        }

        // --
        //  Enable / disable helpers
        // --
        public void enableVolumeName(boolean enable, int maxLength, ValidNameRule rule) {
            if (lblVolume[0] != null) lblVolume[0].setEnabled(enable);
            if (txtVolume[0] != null) {
                txtVolume[0].setEnabled(enable);
                if (enable) {
                    if (maxLength == 0) maxLength = 64;
                    txtVolume[0].setDocument(new LimitedDocument(maxLength));
                    txtVolume[0].setInputVerifier(
                            new IntNameVerifier(maxLength, "volume name", rule));
                } else {
                    txtVolume[0].setInputVerifier(null);
                }
            }
        }

        public void enableVolumeNumber(boolean enable) {
            if (lblVolume[1] != null) lblVolume[1].setEnabled(enable);
            if (txtVolume[1] != null) txtVolume[1].setEnabled(enable);
        }

        public void enableVolumeDate(boolean enable) {
            if (lblVolume[2] != null) lblVolume[2].setEnabled(enable);
            if (txtVolume[2] != null) txtVolume[2].setEnabled(enable);
        }

        // --
        //  Setters (mirroring SetVolumeName / Number / Date)
        // --
        public void setVolumeName(String val) {
            if (txtVolume[0] != null) txtVolume[0].setText(val);
        }

        public void setVolumeNumber(int val, boolean isHex) {
            if (txtVolume[1] != null) {
                String s = isHex ? String.format("0x%X", val) : String.valueOf(val);
                txtVolume[1].setText(s);
            }
        }

        public void setVolumeDate(String val) {
            if (txtVolume[2] != null) txtVolume[2].setText(val);
        }

        // --
        //  Getters
        // --
        public String getVolumeName() {
            return (txtVolume[0] != null) ? txtVolume[0].getText() : "";
        }

        public int getVolumeNumber() {
            String txt = (txtVolume[1] != null) ? txtVolume[1].getText() : "0";
            return Utils.toInt(txt);
        }

        public String getVolumeDate() {
            return (txtVolume[2] != null) ? txtVolume[2].getText() : "";
        }
    }

    // ------
    //  Helpers & placeholders
    // ------

    // Dummy document that limits max characters
    private class LimitedDocument extends javax.swing.text.PlainDocument {
        private final int maxLen;
        public LimitedDocument(int maxLen) { this.maxLen = maxLen; }
        @Override
        public void insertString(int offs, String str, javax.swing.text.AttributeSet a)
                throws javax.swing.text.BadLocationException {
            if (str == null) return;
            if ((getLength() + str.length()) <= maxLen) {
                super.insertString(offs, str, a);
            }
        }
    }

    // Dummy verifier – would normally check against a rule
    private class IntNameVerifier extends InputVerifier {
        private final int maxLen;
        private final String label;
        private final ValidNameRule rule;
        public IntNameVerifier(int maxLen, String label, ValidNameRule rule) {
            this.maxLen = maxLen;
            this.label = label;
            this.rule = rule;
        }
        @Override
        public boolean verify(JComponent input) {
            // Simplified – always accept
            return true;
        }
    }

    // ------
    //  Main method – entry point for manual testing
    // ------
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Parent");
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setSize(400, 300);
            f.setLocationRelativeTo(null);
            f.setVisible(true);

            BasicSelBox dlg = new BasicSelBox(
                    f,
                    100,
                    new DiskImageDisk(),
                    new DiskBasic(),
                    0);
            int ret = dlg.showModal();
            System.out.println("Dialog returned: " + ret);
        });
    }
}
