package l3diskex.ui;

import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;


/**
 * Raw sector dialog.
 */
public class RawSectorBox extends JDialog {

    /* ------------------------------------------------------------------ */
    /*  PRIVATE FIELDS                                                   */
    /* ------------------------------------------------------------------ */

    private final JTextField txtID_C;
    private final JTextField txtID_H;
    private final JTextField txtID_R;
    private final JTextField txtID_N;

    private final JCheckBox chkDeleted;
    private final JCheckBox chkDensity;

    private JTextField txtSecNums;   // may be null if hidden
    private final JTextField txtStatus;

    private boolean okPressed = false;

    /* ------------------------------------------------------------------ */
    /*  PUBLIC ENUM / CONSTANTS                                         */
    /* ------------------------------------------------------------------ */

    public static final int IDC_TEXT_ID_C = 1;
    public static final int IDC_TEXT_ID_H = 2;
    public static final int IDC_TEXT_ID_R = 3;
    public static final int IDC_TEXT_ID_N = 4;
    public static final int IDC_CHK_DELETED = 5;
    public static final int IDC_CHK_DENSITY = 6;
    public static final int IDC_TEXT_SECNUMS = 7;
    public static final int IDC_TEXT_STATUS = 8;

    public static final int SECTORBOX_HIDE_SECTOR_NUMS = 0x0040;

    /* ------------------------------------------------------------------ */
    /*  CONSTRUCTOR                                                      */
    /* ------------------------------------------------------------------ */

    /**
     * @param parent     parent window (may be {@code null})
     * @param id         window id (unused in Swing)
     * @param caption    dialog title
     * @param id_c       initial C value
     * @param id_h       initial H value
     * @param id_r       initial R value
     * @param id_n       initial N value
     * @param sec_nums   initial sector count
     * @param deleted    initial deleted flag
     * @param sdensity   initial single density flag
     * @param status     initial status (hex)
     * @param hide_flags flags that may hide some controls
     */
    public RawSectorBox(Window parent, int id, String caption,
                        int id_c, int id_h, int id_r, int id_n,
                        int sec_nums, boolean deleted, boolean sdensity,
                        int status, int hide_flags) {

        super(parent, caption, ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        /* ---- Layout helpers ------------------------------------------------ */
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        JPanel mainPanel = new JPanel(new GridBagLayout());
        int row = 0;

        /* ---- 1st row – C H R N -------------------------------------------- */
        txtID_C = createNumericField(3);
        txtID_H = createNumericField(3);
        txtID_R = createNumericField(3);
        txtID_N = createNumericField(3);

        txtID_C.setText(String.format("%d", id_c));
        txtID_H.setText(String.format("%d", id_h));
        txtID_R.setText(String.format("%d", id_r));
        txtID_N.setText(String.format("%d", id_n));

        JPanel idPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        idPanel.add(new JLabel("C"));
        idPanel.add(txtID_C);
        idPanel.add(new JLabel("H"));
        idPanel.add(txtID_H);
        idPanel.add(new JLabel("R"));
        idPanel.add(txtID_R);
        idPanel.add(new JLabel("N"));
        idPanel.add(txtID_N);

        gbc.gridx = 0;
        gbc.gridy = row++;
        gbc.gridwidth = 2;
        mainPanel.add(idPanel, gbc);

        /* ---- 2nd row – Deleted / Density ----------------------------------- */
        chkDeleted = new JCheckBox("Deleted Mark");
        chkDeleted.setSelected(deleted);

        chkDensity = new JCheckBox("Single Density");
        chkDensity.setSelected(sdensity);

        JPanel checkPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        checkPanel.add(chkDeleted);
        checkPanel.add(chkDensity);

        gbc.gridy = row++;
        mainPanel.add(checkPanel, gbc);

        /* ---- 3rd row – Sector numbers (may be hidden) --------------------- */
        if ((hide_flags & SECTORBOX_HIDE_SECTOR_NUMS) == 0) {
            txtSecNums = createNumericField(4);
            txtSecNums.setText(String.format("%d", sec_nums));

            JPanel sectorPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            sectorPanel.add(new JLabel("Number Of Sector"));
            sectorPanel.add(txtSecNums);

            gbc.gridy = row++;
            mainPanel.add(sectorPanel, gbc);
        }

        /* ---- 4th row – Status (hex) --------------------------------------- */
        txtStatus = createHexField(8);
        txtStatus.setText(String.format("%x", status));

        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        statusPanel.add(new JLabel("Status (Hex)"));
        statusPanel.add(txtStatus);

        gbc.gridy = row++;
        mainPanel.add(statusPanel, gbc);

        /* ---- Button bar --------------------------------------------------- */
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        JButton okBtn = new JButton("OK");
        JButton cancelBtn = new JButton("Cancel");

        okBtn.addActionListener(e -> onOK());
        cancelBtn.addActionListener(e -> dispose());

        buttonPanel.add(okBtn);
        buttonPanel.add(cancelBtn);

        gbc.gridy = row++;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.EAST;
        mainPanel.add(buttonPanel, gbc);

        /* ---- Finalise ----------------------------------------------------- */
        setContentPane(mainPanel);
        pack();
        setLocationRelativeTo(parent);
    }

    /* ------------------------------------------------------------------ */
    /*  PUBLIC METHODS                                                   */
    /* ------------------------------------------------------------------ */

    /**
     * Shows the dialog modally.
     *
     * @return 0 if OK was pressed, 1 otherwise.
     */
    public int ShowModal() {
        setVisible(true);          // blocks until disposed
        return okPressed ? 0 : 1;
    }

    /* ------------------------------------------------------------------ */
    /*  PROPERTY ACCESSORS                                               */
    /* ------------------------------------------------------------------ */

    public int GetIdC() {
        return parseInt(txtID_C);
    }

    public int GetIdH() {
        return parseInt(txtID_H);
    }

    public int GetIdR() {
        return parseInt(txtID_R);
    }

    public int GetIdN() {
        return parseInt(txtID_N);
    }

    public int GetSectorNums() {
        return txtSecNums != null ? parseInt(txtSecNums) : 0;
    }

    public boolean GetDeletedMark() {
        return chkDeleted.isSelected();
    }

    public boolean GetSingleDensity() {
        return chkDensity.isSelected();
    }

    public int GetStatus() {
        try {
            String txt = txtStatus.getText().trim();
            return txt.isEmpty() ? 0 : Integer.parseInt(txt, 16);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    /* ------------------------------------------------------------------ */
    /*  PRIVATE HELPERS                                                  */
    /* ------------------------------------------------------------------ */

    private void onOK() {
        // In the original code Validate() / TransferDataFromWindow() were called.
        // Here we rely on the input filters and simply close the dialog.
        okPressed = true;
        dispose();
    }

    private int parseInt(JTextField field) {
        try {
            String txt = field.getText().trim();
            return txt.isEmpty() ? 0 : Integer.parseInt(txt);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private JTextField createNumericField(int maxLen) {
        JTextField tf = new JTextField();
        tf.setColumns(maxLen);
        tf.setDocument(new LimitedDocument(maxLen, "0123456789"));
        return tf;
    }

    private JTextField createHexField(int maxLen) {
        JTextField tf = new JTextField();
        tf.setColumns(maxLen);
        tf.setDocument(new LimitedDocument(maxLen, "0123456789abcdefABCDEF"));
        return tf;
    }

    /* ------------------------------------------------------------------ */
    /*  DOCUMENT FILTERS                                                 */
    /* ------------------------------------------------------------------ */

    /**
     * A Document that limits the number of characters and the
     * allowed character set.
     */
    private static class LimitedDocument extends PlainDocument {

        private final int maxLen;
        private final String allowedChars;

        LimitedDocument(int maxLen, String allowedChars) {
            this.maxLen = maxLen;
            this.allowedChars = allowedChars;
        }

        @Override
        public void insertString(int offset, String str, AttributeSet attr)
                throws BadLocationException {

            if (str == null) return;

            StringBuilder sb = new StringBuilder(getText(0, getLength()));
            sb.insert(offset, str);

            if (sb.length() > maxLen) return;          // too long
            for (int i = 0; i < str.length(); i++) {
                if (allowedChars.indexOf(str.charAt(i)) == -1) {
                    return;                            // forbidden char
                }
            }
            super.insertString(offset, str, attr);
        }
    }
}
