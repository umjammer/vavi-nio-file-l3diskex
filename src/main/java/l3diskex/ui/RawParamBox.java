package l3diskex.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;


/**
 * Raw parameter dialog.
 * <p>
 * The original C++ code uses wxWidgets; this Java version uses Swing.
 * All class names, method names and field names are kept identical
 * to the source as far as possible.
 */
public class RawParamBox extends JDialog {

    /* ----------  PRIVATE DATA  ---------- */

    private final int type;
    private final int maxvalue;

    private JTextField txtValue;     // corresponds to wxTextCtrl
    private JComboBox<String> comValue; // corresponds to wxChoice

    /* ----------  PUBLIC ENUMS  ---------- */

    public static final int IDC_TEXT_VALUE = 1;
    public static final int IDC_COMBO_VALUE = 2;

    public static final int TYPE_IDC = 0;
    public static final int TYPE_IDH = 1;
    public static final int TYPE_IDR = 2;
    public static final int TYPE_IDN = 3;
    public static final int TYPE_NUM_OF_SECTORS = 4;
    public static final int TYPE_SECTOR_SIZE = 5;

    /* ----------  PUBLIC METHODS  ---------- */

    /**
     * Constructor.
     *
     * @param parent  the parent window (may be {@code null})
     * @param id      the window id (unused in Swing)
     * @param title   the dialog title
     * @param type    the parameter type
     * @param value   the initial value
     * @param maxvalue the maximum allowed value
     */
    public RawParamBox(Window parent, int id, String title,
                       int type, int value, int maxvalue) {
        super(parent, title, ModalityType.APPLICATION_MODAL);
        this.type = type;
        this.maxvalue = maxvalue;

        // ---  layout & components  ---------------------------------------------------------

        // Text for the type and current value
        String[] typenames = {
                "ID C", "ID H", "ID R", "ID N",
                "NumOfSectors", "SectorSize"
        };
        int[] maxlens = {3, 3, 3, 3, 3, 4};

        JPanel allPanel = new JPanel(new BorderLayout(4, 4));
        JPanel hbox = new JPanel(new FlowLayout(FlowLayout.LEFT));

        String typestr = typenames[type] + " : " +
                String.format("%d  --> ", value);
        hbox.add(new JLabel(typestr));

        // ---  value input ---------------------------------------------------------------
        if (type != TYPE_SECTOR_SIZE) {
            // Numeric input
            txtValue = new JTextField(10);
            txtValue.setDocument(new JTextFieldLimit(maxlens[type]));
            hbox.add(txtValue);
        } else {
            // Choice of sector sizes
            comValue = new JComboBox<>();
            int choice = 0;
            for (int i = 0; i <= 4; i++) {
                int nsize = 128 * (1 << i);
                comValue.addItem(String.valueOf(nsize));
                if (value == nsize) {
                    choice = i;
                }
            }
            comValue.setSelectedIndex(choice);
            hbox.add(comValue);
        }

        allPanel.add(hbox, BorderLayout.NORTH);

        // ---  button bar ---------------------------------------------------------------
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okBtn = new JButton("OK");
        JButton cancelBtn = new JButton("Cancel");

        okBtn.addActionListener(e -> onOK(e));
        cancelBtn.addActionListener(e -> dispose());

        buttonPanel.add(okBtn);
        buttonPanel.add(cancelBtn);

        allPanel.add(buttonPanel, BorderLayout.SOUTH);

        setContentPane(allPanel);
        pack();
        setLocationRelativeTo(parent);
    }

    /**
     * Show the dialog modally.
     *
     * @return {@code 0} if OK, {@code 1} if Cancel
     */
    public int ShowModal() {
        setVisible(true);   // modal dialog blocks until disposed
        return okPressed ? 0 : 1;
    }

    /**
     * Validate the entered parameter.
     *
     * @return {@code true} if valid, {@code false} otherwise
     */
    public boolean ValidateParam() {
        int val = GetValue();
        if (val > maxvalue) {
            JOptionPane.showMessageDialog(this,
                    String.format("The value need less equal %d.", maxvalue),
                    "Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        return true;
    }

    /**
     * Return the numeric value entered by the user.
     *
     * @return the integer value
     */
    public int GetValue() {
        int val = 0;
        if (txtValue != null) {
            try {
                val = Long.parseLong(txtValue.getText().trim());
            } catch (NumberFormatException ex) {
                val = 0;
            }
        } else if (comValue != null) {
            int sel = comValue.getSelectedIndex();
            val = 128L * (1 << sel);
        }
        return val;
    }

    /* ----------  PRIVATE HELPERS  ---------- */

    private boolean okPressed = false;

    private void onOK(ActionEvent e) {
        if (ValidateParam()) {
            okPressed = true;
            dispose();
        }
    }

    /* ----------  INNER CLASS FOR TEXT LIMIT  ---------- */

    /**
     * Utility to limit the number of characters in a {@link JTextField}.
     */
    private static class JTextFieldLimit extends javax.swing.text.PlainDocument {
        private final int limit;

        JTextFieldLimit(int limit) {
            super();
            this.limit = limit;
        }

        @Override
        public void insertString(int offs, String str, javax.swing.text.AttributeSet a)
                throws javax.swing.text.BadLocationException {
            if (str == null) return;
            if ((getLength() + str.length()) <= limit) {
                super.insertString(offs, str, a);
            }
        }
    }

    /* ----------  DENSITY PARAM BOX  ---------- */

    /**
     * Inner class that implements the density parameter dialog.
     * It mirrors the original wxWidgets implementation but uses
     * Swing components.
     */
    public static class DensityParamBox extends JDialog {

        private final ButtonGroup radDouble;
        private final ButtonGroup radSingle;

        public static final int IDC_RADIO_DENSITY = 1;

        public DensityParamBox(Window parent, int id, boolean sdensity) {
            super(parent, "Density Parameter", ModalityType.APPLICATION_MODAL);

            // Radio buttons
            radDouble = new ButtonGroup("Double Density");
            radSingle = new ButtonGroup("Single Density");

            JTextField group = new JTextField();
            group.add(radDouble);
            group.add(radSingle);

            radDouble.setSelected(!sdensity);
            radSingle.setSelected(sdensity);

            JPanel radioPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            radioPanel.setBorder(BorderFactory.createTitledBorder("Density"));
            radioPanel.add(radDouble);
            radioPanel.add(radSingle);

            // Button bar
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton okBtn = new JButton("OK");
            JButton cancelBtn = new JButton("Cancel");

            okBtn.addActionListener(e -> dispose());
            cancelBtn.addActionListener(e -> dispose());

            buttonPanel.add(okBtn);
            buttonPanel.add(cancelBtn);

            // Layout
            JPanel mainPanel = new JPanel(new BorderLayout(4, 4));
            mainPanel.add(radioPanel, BorderLayout.CENTER);
            mainPanel.add(buttonPanel, BorderLayout.SOUTH);

            setContentPane(mainPanel);
            pack();
            setLocationRelativeTo(parent);
        }

        /**
         * Return {@code true} if the user selected Single Density.
         */
        public boolean IsSingleDensity() {
            return radSingle.isSelected();
        }
    }
}
