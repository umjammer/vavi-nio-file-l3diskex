package l3diskex.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;


/**
 * Main dialog – `ConfigBox`
 */
public class ConfigBox extends JDialog {

    /*  */
    /*  Configuration stub – mimics the original Config interface          */
    /*  */
    static class Config {

        /*  fields  */
        private boolean trimUnusedData = false;
        private boolean showDeletedFile = false;
        private boolean addExtExport = false;
        private boolean dateExport = false;
        private boolean skipImportDialog = false;
        private boolean decideAttrImport = false;
        private boolean dateImport = false;
        private boolean ignoreDate = false;
        private int dirDepth = 1;
        private String temporaryFolder = "";
        private String binaryEditor = "";
        private String textEditor = "";
        private boolean interDirItem = false;
        private String language = "";

        /*  getters  */
        public boolean IsTrimUnusedData() {
            return trimUnusedData;
        }

        public boolean IsShownDeletedFile() {
            return showDeletedFile;
        }

        public boolean IsAddExtensionExport() {
            return addExtExport;
        }

        public boolean IsSetCurrentDateExport() {
            return dateExport;
        }

        public boolean IsSkipImportDialog() {
            return skipImportDialog;
        }

        public boolean IsDecideAttrImport() {
            return decideAttrImport;
        }

        public boolean IsSetCurrentDateImport() {
            return dateImport;
        }

        public boolean DoesIgnoreDateTime() {
            return ignoreDate;
        }

        public boolean DoesShowInterDirItem() {
            return interDirItem;
        }

        public int GetDirDepth() {
            return dirDepth;
        }

        public String GetTemporaryFolder() {
            return temporaryFolder;
        }

        public String GetBinaryEditor() {
            return binaryEditor;
        }

        public String GetTextEditor() {
            return textEditor;
        }

        public String GetLanguage() {
            return language;
        }

        /*  setters - */
        public void TrimUnusedData(boolean v) {
            trimUnusedData = v;
        }

        public void ShowDeletedFile(boolean v) {
            showDeletedFile = v;
        }

        public void AddExtensionExport(boolean v) {
            addExtExport = v;
        }

        public void SetCurrentDateExport(boolean v) {
            dateExport = v;
        }

        public void SkipImportDialog(boolean v) {
            skipImportDialog = v;
        }

        public void DecideAttrImport(boolean v) {
            decideAttrImport = v;
        }

        public void SetCurrentDateImport(boolean v) {
            dateImport = v;
        }

        public void IgnoreDateTime(boolean v) {
            ignoreDate = v;
        }

        public void SetDirDepth(int v) {
            dirDepth = v;
        }

        public void SetTemporaryFolder(String v) {
            temporaryFolder = v;
        }

        public void ClearTemporaryFolder() {
            temporaryFolder = "";
        }

        public void SetBinaryEditor(String v) {
            binaryEditor = v;
        }

        public void SetTextEditor(String v) {
            textEditor = v;
        }

        public void ShowInterDirItem(boolean v) {
            interDirItem = v;
        }

        public void SetLanguage(String v) {
            language = v;
        }

        /*  convenience  */
        public boolean GetLanguageIsEmpty() {
            return language.isEmpty();
        }
    }

    /*  event return codes  */
    public static final int OK = 1;
    public static final int CANCEL = 2;
    private int returnCode = CANCEL;     // value returned by ShowModal()

    /*  component ids  */
    public enum IDC {
        IDC_NONE,
        IDC_CHECK_TRIM_DATA,
        IDC_CHECK_SHOW_DELFILE,
        IDC_CHECK_ADD_EXT_EXPORT,
        IDC_CHECK_DATE_EXPORT,
        IDC_CHECK_SUPP_IMPORT,
        IDC_CHECK_DEC_ATTR_IMPORT,
        IDC_CHECK_DATE_IMPORT,
        IDC_CHECK_IGNORE_DATE,
        IDC_SPIN_DIR_DEPTH,
        IDC_TEXT_TEMP_FOLDER,
        IDC_BUTTON_TEMP_FOLDER,
        IDC_CHECK_TEMP_FOLDER,
        IDC_TEXT_BINARY_EDITOR,
        IDC_BUTTON_BINARY_EDITOR,
        IDC_TEXT_TEXT_EDITOR,
        IDC_BUTTON_TEXT_EDITOR,
        IDC_CHECK_INTER_DIR_ITEM,
        IDC_COMBO_LANGUAGE
    }

    /*  GUI components  */
    private final Config ini;

    private JCheckBox chkTrimData;
    private JCheckBox chkShowDelFile;
    private JCheckBox chkAddExtExport;
    private JCheckBox chkDateExport;
    private JCheckBox chkSuppImport;
    private JCheckBox chkDecAttrImport;
    private JCheckBox chkDateImport;
    private JCheckBox chkIgnoreDate;
    private JSpinner spnDirDepth;
    private JTextField txtTempFolder;
    private JCheckBox chkTempFolder;
    private JButton btnTempFolder;
    private JTextField txtBinaryEditor;
    private JTextField txtTextEditor;
    private JCheckBox chkInterDirItem;
    private JComboBox<String> comLanguage;

    /*  constructor  */
    public ConfigBox(Frame parent, int id, Config ini) {
        super(parent, "Settings", true);          // modal dialog
        this.ini = ini;

        /*
         *  Layout: a vertical box containing a tabbed pane and OK/Cancel
         */
        JPanel all = new JPanel();
        all.setLayout(new BoxLayout(all, BoxLayout.Y_AXIS));
        all.setBorder(new EmptyBorder(10, 10, 10, 10));

        JTabbedPane tab = new JTabbedPane();
        tab.addTab("General", createGeneralPanel());
        tab.addTab("Export", createExportPanel());
        tab.addTab("Import", createImportPanel());
        tab.addTab("Path", createPathPanel());

        all.add(tab);

        /*  */
        /*  Buttons (OK / Cancel)                                          */
        /*  */
        JPanel btnPane = new JPanel();
        btnPane.setLayout(new FlowLayout(FlowLayout.RIGHT));

        JButton ok = new JButton("OK");
        JButton can = new JButton("Cancel");

        ok.addActionListener(e -> onOK(e));
        can.addActionListener(e -> onCancel(e));

        btnPane.add(ok);
        btnPane.add(can);

        all.add(Box.createVerticalStrut(10));
        all.add(btnPane);

        setContentPane(all);
        pack();
        setLocationRelativeTo(parent);   // centre on parent
        setResizable(false);

        /* initialise the temporary folder widgets */
        initializeTempFolder();
    }

    /*  */
    /*  Panels for each tab                                              */
    /*  */
    private JPanel createGeneralPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        /* - Trim data - */
        chkTrimData = new JCheckBox("Trim unused data", ini.IsTrimUnusedData());
        p.add(chkTrimData);

        /* - Show deleted files - */
        chkShowDelFile = new JCheckBox("Show deleted files", ini.IsShownDeletedFile());
        p.add(chkShowDelFile);

        /* - Inter‑directory depth -- */
        JPanel depth = new JPanel(new FlowLayout(FlowLayout.LEFT));
        depth.add(new JLabel("Depth of subdirectories processed at once:"));
        spnDirDepth = new JSpinner(new SpinnerNumberModel(ini.GetDirDepth(), 1, 100, 1));
        depth.add(spnDirDepth);
        p.add(depth);

        /* - Language -- */
        JPanel lang = new JPanel(new FlowLayout(FlowLayout.LEFT));
        lang.add(new JLabel("Language:"));
        comLanguage = new JComboBox<>(getLanguageList());
        int sel = 0;
        if (!ini.GetLanguage().isEmpty()) {
            sel = comLanguage.getItemCount() > 0 ?
                    findStringInCombo(comLanguage, ini.GetLanguage()) : -1;
        }
        if (sel < 0) sel = 1;   // "Unknown"
        comLanguage.setSelectedIndex(sel);
        lang.add(comLanguage);
        p.add(lang);

        return p;
    }

    private JPanel createExportPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        chkAddExtExport = new JCheckBox("Add extension suitable for file attribute to filename",
                ini.IsAddExtensionExport());
        p.add(chkAddExtExport);

        chkDateExport = new JCheckBox("Set current date and time to exported file",
                ini.IsSetCurrentDateExport());
        p.add(chkDateExport);

        return p;
    }

    private JPanel createImportPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        chkSuppImport = new JCheckBox("Suppress confirmation dialog",
                ini.IsSkipImportDialog());
        p.add(chkSuppImport);

        chkDecAttrImport = new JCheckBox("Trim extension in filename when decided file attribute by extension",
                ini.IsDecideAttrImport());
        p.add(chkDecAttrImport);

        chkDateImport = new JCheckBox("Set current date and time to importing file",
                ini.IsSetCurrentDateImport());
        p.add(chkDateImport);

        chkIgnoreDate = new JCheckBox("Ignore date and time when import or change property (Supported system only)",
                ini.DoesIgnoreDateTime());
        p.add(chkIgnoreDate);

        return p;
    }

    private JPanel createPathPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        /* - Temporary folder -- */
        JPanel tmp = new JPanel();
        tmp.setBorder(new TitledBorder("Path of the temporary folder"));
        tmp.setLayout(new BoxLayout(tmp, BoxLayout.Y_AXIS));

        chkTempFolder = new JCheckBox("Use system setting", ini.GetTemporaryFolder().isEmpty());
        chkTempFolder.addItemListener(e -> setEditableTempFolder(!chkTempFolder.isSelected()));
        tmp.add(chkTempFolder);

        JPanel tmpPath = new JPanel(new BorderLayout(5, 0));
        txtTempFolder = new JTextField(20);
        tmpPath.add(txtTempFolder, BorderLayout.CENTER);
        btnTempFolder = new JButton("Folder...");
        btnTempFolder.addActionListener(e -> onClickTempFolder());
        tmpPath.add(btnTempFolder, BorderLayout.EAST);
        tmp.add(tmpPath);

        p.add(tmp);
        initializeTempFolder();

        /* - Binary editor -- */
        JPanel bin = new JPanel();
        bin.setBorder(new TitledBorder("Path of the binary editor"));
        bin.setLayout(new BorderLayout(5, 0));

        txtBinaryEditor = new JTextField(20);
        txtBinaryEditor.setText(ini.GetBinaryEditor());
        bin.add(txtBinaryEditor, BorderLayout.CENTER);
        JButton btnBin = new JButton("File...");
        btnBin.addActionListener(e -> onClickBinaryEditor());
        bin.add(btnBin, BorderLayout.EAST);

        p.add(bin);

        /* - Text editor - */
        JPanel txt = new JPanel();
        txt.setBorder(new TitledBorder("Path of the text editor"));
        txt.setLayout(new BorderLayout(5, 0));

        txtTextEditor = new JTextField(20);
        txtTextEditor.setText(ini.GetTextEditor());
        txt.add(txtTextEditor, BorderLayout.CENTER);
        JButton btnTxt = new JButton("File...");
        btnTxt.addActionListener(e -> onClickTextEditor());
        txt.add(btnTxt, BorderLayout.EAST);

        p.add(txt);

        return p;
    }

    /*  */
    /*  Utility helpers                                                    */
    /*  */

    /**
     * Creates a list of supported languages.
     * (The original used wxTranslations – here we just
     * provide a couple of dummy items.)
     */
    private String[] getLanguageList() {
        return new String[] {"System Dependent", "Unknown", "English", "Japanese", "French"};
    }

    /** Finds the index of a string in a combo box – returns -1 if not found. */
    private int findStringInCombo(JComboBox<String> cb, String val) {
        for (int i = 0; i < cb.getItemCount(); ++i) {
            if (cb.getItemAt(i).equals(val)) return i;
        }
        return -1;
    }

    /*  */
    /*  Event handlers                                                     */
    /*  */
    private void onOK(ActionEvent e) {
        returnCode = OK;
        setVisible(false);
    }

    private void onCancel(ActionEvent e) {
        returnCode = CANCEL;
        setVisible(false);
    }

    private void onCheckTempFolder(ItemEvent e) {
        setEditableTempFolder(!chkTempFolder.isSelected());
    }

    private void onClickTempFolder() {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int rc = fc.showOpenDialog(this);
        if (rc == JFileChooser.APPROVE_OPTION) {
            txtTempFolder.setText(fc.getSelectedFile().getAbsolutePath());
        }
    }

    private void onClickBinaryEditor() {
        JFileChooser fc = new JFileChooser();
        int rc = fc.showOpenDialog(this);
        if (rc == JFileChooser.APPROVE_OPTION) {
            txtBinaryEditor.setText(fc.getSelectedFile().getAbsolutePath());
        }
    }

    private void onClickTextEditor() {
        JFileChooser fc = new JFileChooser();
        int rc = fc.showOpenDialog(this);
        if (rc == JFileChooser.APPROVE_OPTION) {
            txtTextEditor.setText(fc.getSelectedFile().getAbsolutePath());
        }
    }

    /*  */
    /*  Public API (mimics wxWidgets)                                   */
    /*  */

    /** Display the dialog modally and return an int code. */
    public int ShowModal() {
        setModal(true);
        pack();
        setVisible(true);
        return returnCode;
    }

    /**
     * Called when the user presses OK – writes all widgets
     * back into the configuration object.
     */
    public void CommitData() {
        ini.TrimUnusedData(chkTrimData.isSelected());
        ini.ShowDeletedFile(chkShowDelFile.isSelected());
        ini.AddExtensionExport(chkAddExtExport.isSelected());
        ini.SetCurrentDateExport(chkDateExport.isSelected());
        ini.SkipImportDialog(chkSuppImport.isSelected());
        ini.DecideAttrImport(chkDecAttrImport.isSelected());
        ini.SetCurrentDateImport(chkDateImport.isSelected());
        ini.IgnoreDateTime(chkIgnoreDate.isSelected());
        ini.SetDirDepth(((SpinnerNumberModel) spnDirDepth.getModel()).getNumber().intValue());
        if (chkTempFolder.isSelected()) {
            ini.ClearTemporaryFolder();
        } else {
            ini.SetTemporaryFolder(txtTempFolder.getText());
        }
        ini.SetBinaryEditor(txtBinaryEditor.getText());
        ini.SetTextEditor(txtTextEditor.getText());
        ini.ShowInterDirItem(chkInterDirItem.isSelected());

        /* language  */
        int sel = comLanguage.getSelectedIndex();
        String lang = "";
        if (sel == 0) {
            // System dependent – leave empty
        } else if (sel == 1) {
            lang = "unknown";
        } else {
            lang = comLanguage.getItemAt(sel);
        }
        ini.SetLanguage(lang);
    }

    /**
     * Helper – initialise the temporary folder controls
     */
    private void initializeTempFolder() {
        String tmpDir = System.getProperty("java.io.tmpdir");
        String iniTmp = ini.GetTemporaryFolder();
        boolean useSystem = iniTmp.isEmpty();
        if (useSystem) {
            iniTmp = tmpDir;
        }
        txtTempFolder.setText(iniTmp);
        chkTempFolder.setSelected(useSystem);
        setEditableTempFolder(!useSystem);
    }

    private void setEditableTempFolder(boolean enabled) {
        txtTempFolder.setEditable(enabled);
        btnTempFolder.setEnabled(enabled);
    }
}
