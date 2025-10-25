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
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;


/**
 * ファイル種類選択ボックス
 * <p>
 * ※ wxWidgets の API を Java の Swing に置き換えて
 * ほぼそのまま構造を維持しています。GUI の
 * 実装は簡易的にしています。
 */
public class FileSelBox extends JDialog {

    /* ------------------------------------------------------------
     *  1. メンバ変数
     * ------------------------------------------------------------ */
    private final JComboBox<String> comFile;      // ファイル選択用コンボボックス

    /* ------------------------------------------------------------
     *  2. 定数
     * ------------------------------------------------------------ */
    public static final int IDC_COMBO_FILE = 1;   // 使わないが置き換え用

    /* ------------------------------------------------------------
     *  3. グローバル（仮想）ファイルタイプ情報
     * ------------------------------------------------------------ */
    private static final FileTypes gFileTypes = new FileTypes();   // 既存のファイル形式リスト

    /* ------------------------------------------------------------
     *  4. コンストラクタ
     * ------------------------------------------------------------ */

    /**
     * @param parent 親ウィンドウ（JFrame 等）
     * @param id     ウィンドウID（使わないが置き換え用）
     */
    public FileSelBox(Frame parent, int id) {
        super(parent, "Select File Type", true);   // タイトルとモーダル設定

        // --- レイアウト設定 --------------------------------------------------
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        // --- ファイル選択コンボボックス --------------------------------------
        comFile = new JComboBox<>();
        panel.add(comFile);

        // --- ファイル形式のリストを取得してコンボに追加 ----------------------
        FileFormats fmts = gFileTypes.getFormats();
        for (int n = 0; n < fmts.count(); n++) {
            FileFormat fmt = fmts.item(n);
            if (fmt == null) continue;
            comFile.addItem(fmt.getDescription());
        }
        comFile.setSelectedIndex(0);

        // --- OK/Cancel ボタン -----------------------------------------------
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okBtn = new JButton("OK");
        JButton cancelBtn = new JButton("Cancel");
        btnPanel.add(okBtn);
        btnPanel.add(cancelBtn);
        panel.add(btnPanel);

        // --- ボタンイベント -------------------------------------------------
        okBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onOK(e);
            }
        });

        cancelBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onCancel(e);
            }
        });

        // --- ダイアログにパネルを設定 ---------------------------------------
        getContentPane().add(panel);
        pack();
        setLocationRelativeTo(parent);
    }

    /* ------------------------------------------------------------
     *  5. イベントハンドラ
     * ------------------------------------------------------------ */

    /** OK ボタン押下時の処理 */
    private void onOK(ActionEvent event) {
        // モーダルの場合は dispose で閉じる
        if (isModal()) {
            setModalExclusionType(Dialog.ModalExclusionType.NO_EXCLUDE);
            dispose();               // モーダルダイアログは dispose で終了
        } else {
            setVisible(false);
        }
    }

    /** Cancel ボタン押下時の処理 */
    private void onCancel(ActionEvent event) {
        dispose();
    }

    /* ------------------------------------------------------------
     *  6. public API
     * ------------------------------------------------------------ */

    /** モーダル表示して終了コードを取得 */
    public int showModal() {
        setVisible(true);          // モーダル表示
        return 0;                  // ここでは簡易化のため 0 を返す
    }

    /** 選択中のインデックスを取得 */
    public int getSelection() {
        return comFile.getSelectedIndex();
    }

    /** 選択中のファイル形式名を取得 */
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
     *  7. 内部クラス（ファイル形式情報）
     * ------------------------------------------------------------ */

    /** 1 つのファイル形式情報 */
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

    /** ファイル形式のリスト */
    private static class FileFormats {

        private final List<FileFormat> list = new ArrayList<>();

        public int count() {
            return list.size();
        }

        public FileFormat item(int idx) {
            if (idx < 0 || idx >= list.size()) return null;
            return list.get(idx);
        }

        /** 形式を追加（内部でのみ使用） */
        private void add(FileFormat fmt) {
            list.add(fmt);
        }
    }

    /** ファイル形式を管理するクラス（グローバルに 1 つ保持） */
    private static class FileTypes {

        private final FileFormats formats = new FileFormats();

        public FileFormats getFormats() {
            return formats;
        }

        /** コンストラクタでダミーデータを生成 */
        public FileTypes() {
            formats.add(new FileFormat("format_a", "Format A"));
            formats.add(new FileFormat("format_b", "Format B"));
            formats.add(new FileFormat("format_c", "Format C"));
        }
    }
}
