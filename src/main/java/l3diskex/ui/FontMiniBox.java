/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.ui;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** */
public class FontMiniBox extends JDialog {

    /* ---------------------------------------------------------------- */
    /*  Constants – event IDs are kept for compatibility but are not
     *  used directly in the Java version.
     * ---------------------------------------------------------------- */
    public static final int IDC_COMBO_FONTNAME   = 1;
    public static final int IDC_COMBO_FONTSIZE   = 2;
    public static final int IDC_BUTTON_DEFAULT   = 3;

    /* ---------------------------------------------------------------- */
    /*  GUI components
     * ---------------------------------------------------------------- */
    private JComboBox<String>   comFontName;
    private JComboBox<String>   comFontSize;
    private JButton             btnDefault;

    /* ---------------------------------------------------------------- */
    /*  Data containers – equivalent to ArrayList<String>
     * ---------------------------------------------------------------- */
    private java.util.List<String> mFontNames = new ArrayList<>();
    private java.util.List<String> mFontSizes = new ArrayList<>();

    /* ---------------------------------------------------------------- */
    /*  Font data – the default font that is passed in, and the
     *  currently selected name/size.
     * ---------------------------------------------------------------- */
    private Font               mDefaultFont;
    private String             mSelectedName;
    private int                mSelectedSize;

    /* ---------------------------------------------------------------- */
    /*  Flag used by ShowModal to know whether OK was pressed
     * ---------------------------------------------------------------- */
    private boolean okPressed = false;

    /* ---------------------------------------------------------------- */
    /*  Constructor – builds the UI and fills the combo boxes
     * ---------------------------------------------------------------- */
    public FontMiniBox(Frame parent, int id, Font default_font) {
        super(parent, "Font", true);               // modal dialog
        this.mDefaultFont = default_font;

        /* ---------------------------------------------------------------- */
        /*  Layout – use BorderLayout for simplicity, BoxLayout for
         *  vertical stacking of controls.
         * ---------------------------------------------------------------- */
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(new EmptyBorder(8, 8, 8, 8));
        setContentPane(mainPanel);

        /* ---------------------------------------------------------------- */
        /*  Font name combo box – read‑only drop‑down.
         * ---------------------------------------------------------------- */
        int comboWidth = 200;                     // approximate DEFAULT_TEXTWIDTH*2
        comFontName = new JComboBox<>();
        comFontName.setPreferredSize(new Dimension(comboWidth, -1));
        comFontName.setEditable(false);

        /* ---------------------------------------------------------------- */
        /*  Font size combo box – numeric input only, editable combo.
         * ---------------------------------------------------------------- */
        comboWidth = 80;
        comFontSize = new JComboBox<>();
        comFontSize.setPreferredSize(new Dimension(comboWidth, -1));
        comFontSize.setEditable(true);
        /* In Swing we cannot apply a validator like wxTextValidator,
         * but we will restrict the input in the OnTextSize handler. */

        /* ---------------------------------------------------------------- */
        /*  Horizontal panel for the two combo boxes.
         * ---------------------------------------------------------------- */
        JPanel hbox = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        hbox.add(comFontName);
        hbox.add(comFontSize);
        mainPanel.add(hbox);

        /* ---------------------------------------------------------------- */
        /*  Bottom panel – Default button + OK/Cancel
         * ---------------------------------------------------------------- */
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 4));
        btnDefault = new JButton("Default");
        bottomPanel.add(btnDefault);

        /* OK / Cancel buttons – use standard Swing buttons */
        JButton btnOK     = new JButton("OK");
        JButton btnCancel = new JButton("Cancel");
        bottomPanel.add(btnOK);
        bottomPanel.add(btnCancel);
        mainPanel.add(bottomPanel);

        /* ---------------------------------------------------------------- */
        /*  Event listeners ------------------------------------------------ */
        /* ---------------------------------------------------------------- */

        /* Text change in size combo – limit to two characters. */
        comFontSize.getEditor().getEditorComponent().addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                OnTextSize();
            }
        });

        /* Default button click */
        btnDefault.addActionListener(e -> OnButtonDefault());

        /* OK button */
        btnOK.addActionListener(e -> {
            okPressed = true;
            term_dialog();                // store selections
            dispose();                    // close dialog
        });

        /* Cancel button */
        btnCancel.addActionListener(e -> {
            okPressed = false;
            dispose();
        });

        /* ---------------------------------------------------------------- */
        /*  Initialize dialog contents
         * ---------------------------------------------------------------- */
        init_dialog();

        /* ---------------------------------------------------------------- */
        /*  Final layout adjustments
         * ---------------------------------------------------------------- */
        pack();
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent we) {
                okPressed = false;
                dispose();
            }
        });
    }

    /* ---------------------------------------------------------------- */
    /*  ShowModal – displays the dialog, returns the result.
     *  Equivalent to wxDialog::ShowModal().  The return value is
     *  0 for OK, 1 for Cancel.  In a real application you might
     *  return the actual ID values; here we keep it simple.
     * ---------------------------------------------------------------- */
    public int ShowModal() {
        setVisible(true);                     // modal
        return okPressed ? 0 : 1;
    }

    /* ---------------------------------------------------------------- */
    /*  init_dialog – populate the combo boxes with font names and
     *  sizes.  Mirrors the wxWidgets implementation.
     * ---------------------------------------------------------------- */
    private void init_dialog() {
        /* Get system font family names */
        String[] systemFonts = GraphicsEnvironment.getLocalGraphicsEnvironment()
                                                  .getAvailableFontFamilyNames();

        /* Add them to the list, sort */
        mFontNames.clear();
        Collections.addAll(mFontNames, systemFonts);
        Arrays.sort(mFontNames.toArray(new String[0]));

        /* Ensure default font name is present */
        if (!mFontNames.contains(mDefaultFont.getFamily())) {
            mFontNames.add(mDefaultFont.getFamily());
            Collections.sort(mFontNames);
        }

        /* Create font size strings 4..24 */
        mFontSizes.clear();
        for (int i = 4; i <= 24; i++) {
            mFontSizes.add(Integer.toString(i));
        }

        /* Populate combo boxes */
        comFontName.removeAllItems();
        for (String name : mFontNames) {
            comFontName.addItem(name);
        }
        comFontSize.removeAllItems();
        for (String sz : mFontSizes) {
            comFontSize.addItem(sz);
        }

        /* Set the default selections */
        SetFontName(mDefaultFont.getFamily());
        SetFontSize(mDefaultFont.getSize());
    }

    /* ---------------------------------------------------------------- */
    /*  term_dialog – called when OK is pressed; saves the current
     *  selections into mSelectedName / mSelectedSize.
     * ---------------------------------------------------------------- */
    private void term_dialog() {
        mSelectedName = comFontName.getSelectedItem() != null
                        ? comFontName.getSelectedItem().toString()
                        : "";
        String sizeText = comFontSize.getSelectedItem() != null
                          ? comFontSize.getSelectedItem().toString()
                          : "";
        try {
            int val = Long.parseLong(sizeText);
            if (val < 1) val = 1;
            if (val > 99) val = 99;
            mSelectedSize = (int) val;
        } catch (NumberFormatException nfe) {
            mSelectedSize = 12;                 // fallback
        }
    }

    /* ---------------------------------------------------------------- */
    /*  SetFontName – set the selected name in the combo box
     * ---------------------------------------------------------------- */
    public void SetFontName(String val) {
        mSelectedName = val;
        comFontName.setSelectedItem(mSelectedName);
    }

    /* ---------------------------------------------------------------- */
    /*  SetFontSize – set the selected size in the combo box
     * ---------------------------------------------------------------- */
    public void SetFontSize(int val) {
        mSelectedSize = val;
        comFontSize.setSelectedItem(Integer.toString(mSelectedSize));
    }

    /* ---------------------------------------------------------------- */
    /*  OnTextSize – event procedure when the user types in the size
     *  combo box.  The original code only kept the first two characters.
     * ---------------------------------------------------------------- */
    private void OnTextSize() {
        String input = comFontSize.getEditor().getItem().toString();
        if (input.length() > 2) {
            input = input.substring(0, 2);
        }
        comFontSize.setEditor(new DefaultCellEditor(new JTextField(input)));
    }

    /* ---------------------------------------------------------------- */
    /*  OnButtonDefault – reset selections to the defaults
     * ---------------------------------------------------------------- */
    private void OnButtonDefault() {
        SetFontName(mDefaultFont.getFamily());
        SetFontSize(mDefaultFont.getSize());
    }

    /* ---------------------------------------------------------------- */
    /*  Properties – getters and setters
     * ---------------------------------------------------------------- */
    public String getFontName() {
        return mSelectedName;
    }

    public int getFontSize() {
        return mSelectedSize;
    }

    /* ---------------------------------------------------------------- */
    /*  Main method – simple test harness
     * ---------------------------------------------------------------- */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Frame dummy = new Frame(); // dummy parent
            Font defaultFont = new Font("Arial", Font.PLAIN, 12);
            FontMiniBox dlg = new FontMiniBox(dummy, 0, defaultFont);
            int rc = dlg.ShowModal();
            if (rc == 0) {
                System.out.println("Selected font: " + dlg.getFontName());
                System.out.println("Selected size: " + dlg.getFontSize());
            } else {
                System.out.println("Dialog cancelled");
            }
        });
    }
}
