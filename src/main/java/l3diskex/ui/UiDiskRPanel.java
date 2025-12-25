/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.ui;


import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;


/* ------------------------------------------------------------------
 *  2. Right Panel (UiDiskRPanel)
 * ------------------------------------------------------------------ */
public class UiDiskRPanel extends JSplitPane {

    private final Component parent;
    /* ------------- Member variables ---------------------------- */
    private final UiDiskFrame frame;

    private final UiDiskDiskAttr diskattr;
    private final UiDiskRBPanel bpanel;   // Bottom panel

    /* ---------------- Constructor -------------------- */
    public UiDiskRPanel(UiDiskFrame parentframe, Component parentwindow, int selected_window) {
        super(VERTICAL_SPLIT);
        this.parent = parentwindow;
        this.frame = parentframe;

        /* Size adjustment */
        setPreferredSize(parentwindow.getPreferredSize());

        /* Create bottom panel */
        diskattr = new UiDiskDiskAttr(parentframe, this);
        bpanel = new UiDiskRBPanel(parentframe, this, selected_window);

        /* Split */
        setTopComponent(diskattr);
        setBottomComponent(bpanel);

        /* Default splitter setting */
        setResizeWeight(0.0);        // Fix top
        setDividerSize(4);

        /* Minimum size */
        setMinimumSize(new Dimension(10, 10));
    }

    /* ---------------- Switch panel -------------------- */
    public void changePanel(int num) {
        if (bpanel != null) bpanel.changePanel(num);
    }

    /* ---------------- Get disk attribute panel -------------------- */
    public UiDiskDiskAttr getDiskAttrPanel() {
        return diskattr;
    }

    /* ---------------- Get file list panel -------------------- */
    public UiDiskFileList getFileListPanel(boolean inst) {
        if (bpanel != null) return bpanel.getFileListPanel(inst);
        return null;
    }

    /* ---------------- Get Raw panel -------------------- */
    public UiDiskRawPanel getRawPanel(boolean inst) {
        if (bpanel != null) return bpanel.getRawPanel(inst);
        return null;
    }

    /* ---------------- Font setting -------------------- */
    public void setListFont(Font font) {
        if (diskattr != null) diskattr.setListFont(font);
        UiDiskFileList flist = getFileListPanel(true);
        if (flist != null) flist.setListFont(font);
        UiDiskRawPanel rlist = getRawPanel(true);
        if (rlist != null) rlist.setListFont(font);
    }

    /* ------------------------------------------------------------------
     *  3. Implement Right Bottom Panel (UiDiskRBPanel) as inner class
     * ------------------------------------------------------------------ */
    public static class UiDiskRBPanel extends JSplitPane {

        /* ------------- Member variables ---------------------------- */
        private final UiDiskRPanel parent;
        private final UiDiskFrame frame;

        private final UiDiskFileList filelist;
        private final UiDiskRawPanel rawpanel;
        private final JPanel proppanel;   // omitted

        /* ---------------- Constructor -------------------- */
        public UiDiskRBPanel(UiDiskFrame parentframe, UiDiskRPanel parentwindow, int selected_window) {
            super(HORIZONTAL_SPLIT);
            this.parent = parentwindow;
            this.frame = parentframe;

            /* Size adjustment */
            setPreferredSize(parentwindow.getPreferredSize());

            /* Create components */
            filelist = new UiDiskFileList(parentframe, this);
            rawpanel = new UiDiskRawPanel(parentframe, this);
            proppanel = new JPanel(this);

            /* Adjust position */
            setResizeWeight(0.0);
            setDividerSize(4);
            setMinimumSize(new Dimension(10, 10));

            /* Initial mode setting */
            switch (selected_window) {
                case 1:      // RAW mode
                    setLeftComponent(rawpanel);
                    setRightComponent(proppanel);
                    filelist.setVisible(false);
                    break;
                default:     // BASIC mode
                    setLeftComponent(filelist);
                    setRightComponent(proppanel);
                    rawpanel.setVisible(false);
                    break;
            }
        }

        /* ---------------- Switch panel -------------------- */
        public void changePanel(int num) {
            switch (num) {
                case 1:   // RAW
                    if (getLeftComponent() == filelist) {
                        setLeftComponent(rawpanel);
                        rawpanel.setVisible(true);
                        filelist.setVisible(false);
                        filelist.clearAttr();
                        filelist.clearFiles();
                    }
                    break;
                default:  // BASIC
                    if (getLeftComponent() == rawpanel) {
                        setLeftComponent(filelist);
                        filelist.setVisible(true);
                        rawpanel.setVisible(false);
                        rawpanel.clearTrackListData();
                        rawpanel.clearSectorListData();
                    }
                    break;
            }
        }

        /* ---------------- Get file list -------------------- */
        public UiDiskFileList getFileListPanel(boolean inst) {
            if (filelist != null && (inst || filelist.isVisible())) return filelist;
            return null;
        }

        /* ---------------- Get Raw panel -------------------- */
        public UiDiskRawPanel getRawPanel(boolean inst) {
            if (rawpanel != null && (inst || rawpanel.isVisible())) return rawpanel;
            return null;
        }
    }

    /* ------------------------------------------------------------------
     *  4. main for debug
     * ------------------------------------------------------------------ */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            UiDiskFrame frame = new UiDiskFrame();
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(600, 400);

            UiDiskRPanel panel = new UiDiskRPanel(frame, frame, 0);
            frame.add(panel);

            frame.setVisible(true);

            // Demo: Switch to RAW mode after a few seconds
            new Timer(3000, e -> {
                panel.changePanel(1);
                panel.setListFont(new Font("Serif", Font.PLAIN, 12));
            }).start();
        });
    }
}
