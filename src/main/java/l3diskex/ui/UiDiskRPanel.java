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
 *  2. 右パネル（UiDiskRPanel）
 * ------------------------------------------------------------------ */
public class UiDiskRPanel extends JSplitPane {

    /* ------------- メンバ変数 ---------------------------- */
    private final Component parent;
    private final UiDiskFrame frame;

    private final UiDiskDiskAttr diskattr;
    private final UiDiskRBPanel bpanel;   // 下部パネル

    /* ---------------- コンストラクタ -------------------- */
    public UiDiskRPanel(UiDiskFrame parentframe, Component parentwindow, int selected_window) {
        super(VERTICAL_SPLIT);
        this.parent = parentwindow;
        this.frame = parentframe;

        /* サイズ合わせ */
        setPreferredSize(parentwindow.getPreferredSize());

        /* 下部パネル作成 */
        diskattr = new UiDiskDiskAttr(parentframe, this);
        bpanel = new UiDiskRBPanel(parentframe, this, selected_window);

        /* 分割 */
        setTopComponent(diskattr);
        setBottomComponent(bpanel);

        /* デフォルトのスプリッタ設定 */
        setResizeWeight(0.0);        // 上部を固定
        setDividerSize(4);

        /* 最小サイズ */
        setMinimumSize(new Dimension(10, 10));
    }

    /* ---------------- パネル切替 -------------------- */
    public void changePanel(int num) {
        if (bpanel != null) bpanel.changePanel(num);
    }

    /* ---------------- ディスク属性パネル取得 -------------------- */
    public UiDiskDiskAttr getDiskAttrPanel() {
        return diskattr;
    }

    /* ---------------- ファイルリストパネル取得 -------------------- */
    public UiDiskFileList getFileListPanel(boolean inst) {
        if (bpanel != null) return bpanel.getFileListPanel(inst);
        return null;
    }

    /* ---------------- Rawパネル取得 -------------------- */
    public UiDiskRawPanel getRawPanel(boolean inst) {
        if (bpanel != null) return bpanel.getRawPanel(inst);
        return null;
    }

    /* ---------------- フォント設定 -------------------- */
    public void setListFont(Font font) {
        if (diskattr != null) diskattr.setListFont(font);
        UiDiskFileList flist = getFileListPanel(true);
        if (flist != null) flist.setListFont(font);
        UiDiskRawPanel rlist = getRawPanel(true);
        if (rlist != null) rlist.setListFont(font);
    }

    /* ------------------------------------------------------------------
     *  3. 右下パネル（UiDiskRBPanel）を内部クラスとして実装
     * ------------------------------------------------------------------ */
    public static class UiDiskRBPanel extends JSplitPane {

        /* ------------- メンバ変数 ---------------------------- */
        private final UiDiskRPanel parent;
        private final UiDiskFrame frame;

        private final UiDiskFileList filelist;
        private final UiDiskRawPanel rawpanel;
        private final JPanel proppanel;   // 省略

        /* ---------------- コンストラクタ -------------------- */
        public UiDiskRBPanel(UiDiskFrame parentframe, UiDiskRPanel parentwindow, int selected_window) {
            super(HORIZONTAL_SPLIT);
            this.parent = parentwindow;
            this.frame = parentframe;

            /* サイズ合わせ */
            setPreferredSize(parentwindow.getPreferredSize());

            /* コンポーネント作成 */
            filelist = new UiDiskFileList(parentframe, this);
            rawpanel = new UiDiskRawPanel(parentframe, this);
            proppanel = new JPanel(this);

            /* 位置調整 */
            setResizeWeight(0.0);
            setDividerSize(4);
            setMinimumSize(new Dimension(10, 10));

            /* 初期モード設定 */
            switch (selected_window) {
                case 1:      // RAWモード
                    setLeftComponent(rawpanel);
                    setRightComponent(proppanel);
                    filelist.setVisible(false);
                    break;
                default:     // BASICモード
                    setLeftComponent(filelist);
                    setRightComponent(proppanel);
                    rawpanel.setVisible(false);
                    break;
            }
        }

        /* ---------------- パネル切替 -------------------- */
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

        /* ---------------- ファイルリスト取得 -------------------- */
        public UiDiskFileList getFileListPanel(boolean inst) {
            if (filelist != null && (inst || filelist.isVisible())) return filelist;
            return null;
        }

        /* ---------------- Rawパネル取得 -------------------- */
        public UiDiskRawPanel getRawPanel(boolean inst) {
            if (rawpanel != null && (inst || rawpanel.isVisible())) return rawpanel;
            return null;
        }
    }

    /* ------------------------------------------------------------------
     *  4. デバッグ用 main
     * ------------------------------------------------------------------ */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            UiDiskFrame frame = new UiDiskFrame();
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(600, 400);

            UiDiskRPanel panel = new UiDiskRPanel(frame, frame, 0);
            frame.add(panel);

            frame.setVisible(true);

            // デモ：数秒後に RAW モードに切り替え
            new Timer(3000, e -> {
                panel.changePanel(1);
                panel.setListFont(new Font("Serif", Font.PLAIN, 12));
            }).start();
        });
    }
}
