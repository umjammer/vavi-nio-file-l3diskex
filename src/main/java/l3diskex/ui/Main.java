package l3diskex.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.TransferHandler;


/** */
public class Main {

    public static final String APPLICATION_NAME = "L3DiskEx";
    public static final String APPLICATION_XPMICON_NAME = "l3diskex_xpm";
    public static final String APPLICATION_FULLNAME = "L3DiskEx Full Name";
    public static final String APPLICATION_VERSION = "1.0.0";
    public static final String PLATFORM = System.getProperty("os.name");
    public static final String APP_COPYRIGHT = "© 2023 Sasaji. All rights reserved.";
    public static final String APPLICATION_XPMICON = "";  // placeholder for XPM icon

    /**
     * UiDiskApp (wxApp)
     */
    public static class UiDiskApp {

        /* Member variables (String → String) */
        private String appPath;
        private String iniPath;
        private String resPath;
        /* wxLocale → Locale (simplified) */
        private Locale locale;

        /* References to main frame */
        private UiDiskFrame frame;
        private String inFile;

        /* Mod key capture (optional) */
        private int modKeys;
        private int modCnt;

        /* Temporary directories list */
        private final java.util.List<String> tmpDirs = new ArrayList<>();

        /* Constructor */
        public UiDiskApp() {
            /* In C++ this would call wxApp::OnInit */
        }

        /*  Initialisation  */
        public boolean onInit() {
            setAppPath();
            // Load configuration, create frame, etc.
            // (All wxWidgets specific code omitted)
            frame = new UiDiskFrame();
            // Example: frame.setVisible(true);
            return true;
        }

        /*  Command line handling  */
        public void onInitCmdLine(String[] args) {
            // Build a parser – omitted
        }

        public boolean onCmdLineParsed(String[] args) {
            // Parse args – omitted
            return true;
        }

        /*  Termination  */
        public int onExit() {
            removeTempDirs();
            return 0;
        }

        /*  Idle event handling  */
        public void onAppIdle() {
            // Placeholder
        }

        public int filterEvent() {
            // Placeholder
            return 0;
        }

        /*  Mac file handling  */
        public void macOpenFile(String fileName) {
            // Placeholder
        }

        public void macOpenFiles(List<String> fileNames) {
            // Placeholder
        }

        /*  Path getters  */
        public String getAppPath() {
            return appPath;
        }

        public String getIniPath() {
            return iniPath;
        }

        public String getResPath() {
            return resPath;
        }

        /*  Temp directory handling  */
        public boolean makeTempDir(StringBuilder tmpDirPath) {
            // Simplified: create a temp directory
            try {
                File tmp = File.createTempFile("l3diskex", "");
                if (tmp.exists()) tmp.delete();
                if (tmp.mkdirs()) {
                    tmpDirs.add(tmp.getAbsolutePath());
                    tmpDirPath.setLength(0);
                    tmpDirPath.append(tmp.getAbsolutePath());
                    return true;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return false;
        }

        public void removeTempDir(String tmpDirPath) {
            removeTempDir(tmpDirPath, 0);
            tmpDirs.remove(tmpDirPath);
        }

        private void removeTempDir(String tmpDirPath, int depth) {
            if (tmpDirPath == null || tmpDirPath.isEmpty() || depth > 20) return;
            File dir = new File(tmpDirPath);
            if (dir.isDirectory()) {
                for (File f : Objects.requireNonNull(dir.listFiles())) {
                    if (f.isDirectory()) {
                        removeTempDir(f.getAbsolutePath(), depth + 1);
                    } else {
                        f.delete();
                    }
                }
                dir.delete();
            }
        }

        public void removeTempDirs() {
            for (String d : new ArrayList<>(tmpDirs)) {
                removeTempDir(d);
            }
        }

        /* -- Path setup --- */
        private void setAppPath() {
            appPath = System.getProperty("user.dir");
            iniPath = appPath + File.separator + "config.ini";
            resPath = appPath + File.separator + "resources";
        }
    }

    /* -- */
    /* ------------- UiDiskPanel (wxSplitterWindow) ------------- */
    /* -- */
    public static class UiDiskPanel extends JSplitPane {

        private final UiDiskFrame frame;

        private final UiDiskList lpanel;
        private final UiDiskRPanel rpanel;

        public UiDiskPanel(UiDiskFrame parent) {
            super(JSplitPane.HORIZONTAL_SPLIT, true);
            this.frame = parent;
            this.setDividerLocation(200);
            this.setDividerSize(5);
            this.setBorder(BorderFactory.createEmptyBorder());
            this.setContinuousLayout(true);

            lpanel = new UiDiskList(frame, this);
            rpanel = new UiDiskRPanel(frame, this, frame.getSelectedMode());

            this.setLeftComponent(lpanel);
            this.setRightComponent(rpanel);

            // Drag‑and‑drop support
            this.setTransferHandler(new UiDiskPanelDropTarget(parent, this));
        }

        public UiDiskList getLPanel() {
            return lpanel;
        }

        public UiDiskRPanel getRPanel() {
            return rpanel;
        }

        /* Process dropped files */
        public boolean processDroppedFiles(int x, int y, List<String> filenames) {
            if (filenames == null || filenames.isEmpty()) return false;

            int dropType = 0;
            int sashPos = this.getDividerLocation();

            boolean diskIsEmpty = frame.getDiskImage().isEmpty();
            boolean includeDir = false;

            for (String fn : filenames) {
                File f = new File(fn);
                if (f.isDirectory()) {
                    includeDir = true;
                    break;
                }
            }

            if (diskIsEmpty) {
                dropType = 2; // no disk image open
            } else if (x < sashPos) {
                // left side drop – placeholder check
                dropType = 1;
            } else {
                dropType = 2;
            }

            boolean sts = false;
            switch (dropType) {
                case 0:
                    UiDiskFileList fileList = rpanel.getFileListPanel();
                    if (fileList != null) {
                        sts = fileList.dropDataFiles(this, x, y, filenames, includeDir);
                    }
                    UiDiskRawPanel rawPanel = rpanel.getRawPanel();
                    if (rawPanel != null) {
                        for (String fn : filenames) {
                            sts = rawPanel.showImportTrackRangeDialog(fn);
                        }
                    }
                    break;
                case 1:
                    if (lpanel != null) {
                        sts = lpanel.dropDataFiles(this, x, y, filenames, includeDir);
                    }
                    break;
                case 2:
                    if (!includeDir) {
                        frame.openDroppedFile(filenames.get(0));
                    }
                    break;
                default:
                    break;
            }
            return sts;
        }
    }

    /** */
    public static class UiDiskPanelDropTarget extends TransferHandler {

        private final UiDiskPanel parent;
        private final UiDiskFrame frame;

        public UiDiskPanelDropTarget(UiDiskFrame parentFrame, UiDiskPanel parentWindow) {
            this.parent = parentWindow;
            this.frame = parentFrame;
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (!support.isDataFlavorSupported(DataFlavor.javaFileListFlavor))
                return false;
            try {
                @SuppressWarnings("unchecked")
                List<File> files = (List<File>) support.getTransferable()
                        .getTransferData(DataFlavor.javaFileListFlavor);
                List<String> names = new ArrayList<>();
                for (File f : files) names.add(f.getAbsolutePath());
                return parent.processDroppedFiles(
                        support.getDropLocation().getDropPoint().x,
                        support.getDropLocation().getDropPoint().y,
                        names);
            } catch (UnsupportedFlavorException | IOException e) {
                e.printStackTrace();
            }
            return false;
        }
    }

    /* -- */
    /* ------------- UiDiskOpenFileDialog ------------- */
    /* -- */
    public static class UiDiskOpenFileDialog extends JFileChooser {

        public UiDiskOpenFileDialog(String message) {
            super();
            this.setDialogTitle(message);
        }

        public UiDiskOpenFileDialog(String message, String defaultDir,
                                    String defaultFile, String wildcard, int style) {
            super();
            this.setDialogTitle(message);
            this.setCurrentDirectory(new File(defaultDir));
            this.setSelectedFile(new File(defaultFile));
            // Wildcard handling omitted
        }
    }

    /* -- */
    /* ------------- UiDiskSaveFileDialog ------------- */
    /* -- */
    public static class UiDiskSaveFileDialog extends JFileChooser {

        public UiDiskSaveFileDialog(String message, String defaultDir,
                                    String defaultFile, String wildcard) {
            super();
            this.setDialogTitle(message);
            this.setCurrentDirectory(new File(defaultDir));
            this.setSelectedFile(new File(defaultFile));
            // Wildcard handling omitted
        }
    }

    /* -- */
    /* ------------- UiDiskDirDialog ------------- */
    /* -- */
    public static class UiDiskDirDialog extends JFileChooser {

        public UiDiskDirDialog(String message, String defaultDir, int style) {
            super();
            this.setDialogTitle(message);
            this.setCurrentDirectory(new File(defaultDir));
            // Style handling omitted
        }
    }

    /* -- */
    /* ------------- UiDiskAbout ------------- */
    /* -- */
    public static class UiDiskAbout extends JDialog {

        public UiDiskAbout(Frame parent, int id) {
            super(parent, "About...", true);
            this.setLayout(new BorderLayout(4, 4));

            // Left panel – icon
            JLabel iconLabel = new JLabel();
            iconLabel.setIcon(new ImageIcon(APPLICATION_XPMICON_NAME));
            iconLabel.setPreferredSize(new Dimension(64, 64));

            // Right panel – text
            String sb = APPLICATION_FULLNAME + ", Version " + APPLICATION_VERSION +
                    " \"" + PLATFORM + "\"" + "\n\n" +
                    "using " + System.getProperty("java.version") + "\n\n" +
                    APP_COPYRIGHT;

            JLabel textLabel = new JLabel("<html>" + sb.replace("\n", "<br>") + "</html>");

            // Assemble
            JPanel left = new JPanel();
            left.add(iconLabel);
            JPanel right = new JPanel();
            right.add(textLabel);
            JPanel main = new JPanel(new BorderLayout());
            main.add(left, BorderLayout.WEST);
            main.add(right, BorderLayout.CENTER);

            JButton ok = new JButton("OK");
            ok.addActionListener(e -> dispose());

            this.getContentPane().add(main, BorderLayout.CENTER);
            this.getContentPane().add(ok, BorderLayout.SOUTH);
            this.pack();
            this.setLocationRelativeTo(parent);
        }
    }
}
