/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.ui;


import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JPanel;


/**
 * File type selection box
 * <p>
 * * Replaces wxWidgets API with Java Swing.
 * Keeps structure almost same.
 * GUI implementation is simplified.
 */
public class FileSelBox extends JDialog {

    /* ------------------------------------------------------------
     *  1. Member variables
     * ------------------------------------------------------------ */
    private final JComboBox<String> comFile;      // ComboBox for file selection

    /* ------------------------------------------------------------
     *  2. Constants
     * ------------------------------------------------------------ */
    public static final int IDC_COMBO_FILE = 1;   // For replacement (unused)

    /* ------------------------------------------------------------
     *  3. Global (virtual) file type information
     * ------------------------------------------------------------ */
    private static final FileTypes gFileTypes = new FileTypes();   // Existing file format list

    /* ------------------------------------------------------------
     *  4. Constructor
     * ------------------------------------------------------------ */

    /**
     * @param parent Parent window (JFrame etc.)
     * @param id     Window ID (For replacement (unused))
     */
    public FileSelBox(Frame parent, int id) {
        super(parent, "Select File Type", true);   // Title and modal setting

        // --- Layout settings --------------------------------------------------
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        // --- File selection combo box --------------------------------------
        comFile = new JComboBox<>();
        panel.add(comFile);

        // --- Get file format list and add to combo ----------------------
        FileFormats fmts = gFileTypes.getFormats();
        for (int n = 0; n < fmts.count(); n++) {
            FileFormat fmt = fmts.item(n);
            if (fmt == null) continue;
            comFile.addItem(fmt.getDescription());
        }
        comFile.setSelectedIndex(0);

        // --- OK/Cancel Buttons -----------------------------------------------
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okBtn = new JButton("OK");
        JButton cancelBtn = new JButton("Cancel");
        btnPanel.add(okBtn);
        btnPanel.add(cancelBtn);
        panel.add(btnPanel);

        // --- Button events -------------------------------------------------
        okBtn.addActionListener(e -> onOK(e));

        cancelBtn.addActionListener(e -> onCancel(e));

        // --- Set panel to dialog ---------------------------------------
        getContentPane().add(panel);
        pack();
        setLocationRelativeTo(parent);
    }

    /* ------------------------------------------------------------
     *  5. Event handler
     * ------------------------------------------------------------ */

    /** Process when OK button is pressed */
    private void onOK(ActionEvent event) {
        // If modal, close with dispose
        if (isModal()) {
            setModalExclusionType(Dialog.ModalExclusionType.NO_EXCLUDE);
            dispose();               // Modal dialog ends with dispose
        } else {
            setVisible(false);
        }
    }

    /** Process when Cancel button is pressed */
    private void onCancel(ActionEvent event) {
        dispose();
    }

    /* ------------------------------------------------------------
     *  6. public API
     * ------------------------------------------------------------ */

    /** Display modal and get exit code */
    public int showModal() {
        setVisible(true);          // Modal display
        return 0;                  // Return 0 for simplification here
    }

    /** Get selected index */
    public int getSelection() {
        return comFile.getSelectedIndex();
    }

    /** Get selected file format name */
    public String getFormatType() {
        String type = "";
        FileFormats fmts = gFileTypes.getFormats();
        int sel = getSelection();
        if (sel >= 0 && sel < fmts.count()) {
            FileFormat fmt = fmts.item(sel);
            if (fmt != null) {
                type = fmt.getName();
            }
        }
        return type;
    }

    /* ------------------------------------------------------------
     *  7. Inner class (File format information)
     * ------------------------------------------------------------ */

    /** One file format information */
    private static class FileFormat {

        private final String name;
        private final String description;

        public FileFormat(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }
    }

    /** List of file formats */
    private static class FileFormats {

        private final List<FileFormat> list = new ArrayList<>();

        public int count() {
            return list.size();
        }

        public FileFormat item(int idx) {
            if (idx < 0 || idx >= list.size()) return null;
            return list.get(idx);
        }

        /** Add format (used internally only) */
        private void add(FileFormat fmt) {
            list.add(fmt);
        }
    }

    /** Class to manage file formats (Global singleton) */
    private static class FileTypes {

        private final FileFormats formats = new FileFormats();

        public FileFormats getFormats() {
            return formats;
        }

        /** Generate dummy data in constructor */
        public FileTypes() {
            formats.add(new FileFormat("format_a", "Format A"));
            formats.add(new FileFormat("format_b", "Format B"));
            formats.add(new FileFormat("format_c", "Format C"));
        }
    }
}
