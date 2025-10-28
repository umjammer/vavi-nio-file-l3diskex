/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.diskimg;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import l3diskex.Utils;
import l3diskex.diskimg.FileParam.FileParamFormat;
import l3diskex.diskimg.writer.DiskD88Writer;
import l3diskex.diskimg.writer.DiskPlainWriter;

import static l3diskex.diskimg.FileParam.gFileTypes;


/**
 * ディスクライター
 */
public class DiskWriter extends DiskWriteOptions {

    public static final String[] cFormatTypeNamesForSave = {
            "", "d88", "plain", null
    };

    private final String m_file_path;
    private final DiskImage p_image;
    private OutputStream p_ostream;
    private boolean m_ownstream;
    private final DiskResult p_result;

    /// 拡張子をさがす
    /// @param disk_number ディスク番号
    /// @param side_number サイド番号
    private int canSaveDiskByExt(int disk_number, int side_number) {
        int rc = 0;

        // ファイル形式の指定がない場合
        String fpath = m_file_path;

        // 拡張子で判定
        String ext = Utils.getExt(fpath);

        // サポートしているファイルか
        FileParam fitem = gFileTypes.findExt(ext);
        if (fitem == null) {
            p_result.setError(DiskResult.ERR_UNSUPPORTED);
            return p_result.getValid();
        }

        // 指定した形式でファイル出力
        List<FileParamFormat> formats = fitem.getFormats();
        for (int i = 0; i < formats.size(); i++) {
            FileParamFormat param_format = formats.get(i);
            rc = selectCanSaveDisk(param_format.getType(), disk_number, side_number);
            if (rc >= 0) {
                break;
            }
        }

        return rc;
    }

    /// 拡張子で保存形式を判定＆保存できるか
    /// @param file_format ファイルフォーマット
    /// @param disk_number ディスク番号
    /// @param side_number サイド番号
    private int selectCanSaveDisk(String file_format, int disk_number, int side_number) {
        int rc = -1;
        // C++: wxT("d88") -> "d88" (assuming wxT converts to String)
        if (file_format.equals("d88")) {
            // d88形式
            DiskD88Writer wr = new DiskD88Writer(this, p_result);
            rc = wr.validateDisk(p_image, disk_number, side_number);
//        } else if (file_format.equals("cpcdsk")) {
//            // CPC DSK形式
//            DiskDskWriter wr (result);
//            rc = wr.ValidateDisk(p_image, disk_number, side_number);
        } else if (file_format.equals("plain")) {
            // ベタ
            DiskPlainWriter wr = new DiskPlainWriter(this, p_result);
            rc = wr.validateDisk(p_image, disk_number, side_number);
        }
        return rc;
    }

    /**
     拡張子をさがす
     @param disk_number ディスク番号
     @param side_number サイド番号
     @param support   [out] 対応しているフォーマットならtrue
     */
    private int saveDiskByExt(int disk_number, int side_number, boolean[] support) throws IOException {
        int rc = 0;

        // ファイル形式の指定がない場合
        String fpath = m_file_path;

        // 拡張子で判定
        String ext = Utils.getExt(fpath);

        // サポートしているファイルか
        FileParam fitem = gFileTypes.findExt(ext);
        if (fitem == null) {
            p_result.setError(DiskResult.ERR_UNSUPPORTED);
            return p_result.getValid();
        }

        // 指定した形式でファイル出力
        List<FileParamFormat> formats = fitem.getFormats();
        for (int i = 0; i < formats.size(); i++) {
            FileParamFormat param_format = formats.get(i);
            rc = selectSaveDisk(param_format.getType(), disk_number, side_number, support);
            if (rc >= 0) {
                break;
            }
        }

        return rc;
    }

    // 拡張子で保存形式を判定
    private int selectSaveDisk(String file_format, int disk_number, int side_number, boolean[] support) throws IOException {
        int rc = -1;
        if (file_format.equals("d88")) {
            // d88形式
            DiskD88Writer wr = new DiskD88Writer(this, p_result);
            rc = wr.saveDisk(p_image, disk_number, side_number, p_ostream);
            support[0] = true;
//        } else if (file_format.equals("cpcdsk")) {
//            // CPC DSK形式
//            DiskDskWriter wr = new DiskDskWriter(result);
//            rc = wr.SaveDisk(p_image, disk_number, side_number, p_ostream);
//            support[0] = true;
        } else if (file_format.equals("plain")) {
            // ベタ
            DiskPlainWriter wr = new DiskPlainWriter(this, p_result);
            rc = wr.saveDisk(p_image, disk_number, side_number, p_ostream);
            support[0] = true;
        }

        if (support[0] && rc >= 0) {
            // 保存したファイル名を持っておく
            p_image.setFileName(m_file_path);
        }
        return rc;
    }

    //
    // 形式ごとの保存
    //

    /**
     * @param image   ディスクイメージ
     * @param path    ファイルパス
     * @param options 出力時のオプション
     * @param result  結果
     */
    public DiskWriter(DiskImage image, String path, DiskWriteOptions options, DiskResult result) throws FileNotFoundException {
        // C++: DiskWriter(DiskImage *image, const wxString &path, const DiskWriteOptions &options, DiskResult *result) : DiskWriteOptions(options)
        super(options.m_trim_unused_data);
        p_image = image;
        m_file_path = path;
        p_result = result;
        p_ostream = null;
        open(path);
    }

    /**
     * @param image  ディスクイメージ
     * @param result 結果
     */
    public DiskWriter(DiskImage image, DiskResult result) {
        p_image = image;
        m_file_path = ""; // wxEmptyString equivalent
        p_result = result;
        p_ostream = null;
        m_ownstream = false;
    }

    // Java uses finalizers/try-with-resources for cleanup, but converting C++ destructor logic:
    public void close() {
        if (m_ownstream) {
            if (p_ostream != null) {
                try {
                    p_ostream.close();
                } catch (IOException e) {
                    // Handle exception if needed
                }
            }
            // In C++, the stream object is deleted, here we just ensure it's closed and dereferenced.
            // Java Garbage Collector handles memory deallocation.
        }
        p_ostream = null;
    }

    /**
     * 出力先を開く
     *
     * @param path 出力先ファイルパス
     * @return 結果
     */
    public int open(String path) throws FileNotFoundException {
        FileOutputStream fstream;
        fstream = new FileOutputStream(path);
        p_ostream = fstream;
        m_ownstream = true;
        return p_result.getValid();
    }

    /**
     * 出力先がオープンしているか
     *
     * @return true if open and ready, false otherwise
     */
    public boolean isOk() {
        return p_ostream != null;
    }

    /**
     * 対応しているディスクイメージか
     *
     * @param file_format ファイルフォーマット
     * @return true if supported, false otherwise
     */
    public static boolean supportedFormat(String file_format) {
        boolean match = false;
        for (int i = 1; cFormatTypeNamesForSave[i] != null; i++) {
            if (file_format.equals(cFormatTypeNamesForSave[i])) {
                match = true;
                break;
            }
        }
        return match;
    }

    /**
     * ディスクイメージを保存できるか
     *
     * @param file_format ファイルフォーマット
     * @return 0:できる, 1:警告あり (>=0 success, <0 error)
     */
    public int canSave(String file_format) {
        return canSaveDisk(-1, -1, file_format);
    }

    /**
     * ストリームの内容をファイルに保存できるか
     *
     * @param disk_number ディスク番号
     * @param side_number サイド番号
     * @param file_format ファイルフォーマット
     * @return 0:できる, 1:警告あり (>=0 success, <0 error)
     */
    public int canSaveDisk(int disk_number, int side_number, String file_format) {
        int rc = 0;
        if (file_format.isEmpty()) {
            // ファイル形式の指定がない場合
            rc = canSaveDiskByExt(disk_number, side_number);
        } else {
            // ファイル形式の指定あり
            rc = selectCanSaveDisk(file_format, disk_number, side_number);
        }
        return rc;
    }

    /**
     * ディスクイメージの保存
     *
     * @param file_format ファイルフォーマット
     * @return 結果
     */
    public int save(String file_format) throws IOException {
        return saveDisk(-1, -1, file_format);
    }

    /**
     * ストリームの内容をファイルに保存
     *
     * @param disk_number ディスク番号
     * @param side_number サイド番号
     * @param file_format ファイルフォーマット
     * @return 結果
     */
    public int saveDisk(int disk_number, int side_number, String file_format) throws IOException {
        int rc = 0;
        boolean[] support = {false};

        if (!isOk()) {
            p_result.setError(DiskResult.ERR_CANNOT_SAVE);
            return p_result.getValid();
        }

        if (file_format.isEmpty()) {
            // ファイル形式の指定がない場合
            rc = saveDiskByExt(disk_number, side_number, support);
        } else {
            // ファイル形式の指定あり
            rc = selectSaveDisk(file_format, disk_number, side_number, support);
        }
        if (!support[0]) {
            p_result.setError(DiskResult.ERR_UNSUPPORTED);
            return p_result.getValid();
        }
        return rc;
    }

    /**
     * 形式ごとのディスクライター
     */
    public static class DiskImageWriter {

        protected DiskWriter p_dw;
        protected DiskResult p_result;

        public DiskImageWriter(DiskWriter dw_, DiskResult result_) {
            p_dw = dw_;
            p_result = result_;
        }

        /**
         * ストリームの内容をファイルに保存できるか
         *
         * @param image       ディスクイメージ
         * @param disk_number ディスク番号(0-) / -1のときは全体
         * @param side_number サイド番号(0-) / -1のときは両面
         * @return 0 正常
         */
        public int validateDisk(DiskImage image, int disk_number, int side_number) {
            p_result.clear();

            return 0;
        }

        /**
         * ストリームの内容をファイルに保存
         *
         * @param image       ディスクイメージ
         * @param disk_number ディスク番号(0-) / -1のときは全体
         * @param side_number サイド番号(0-) / -1のときは両面
         * @param ostream     出力先
         * @return 0 正常
         */
        public int saveDisk(DiskImage image, int disk_number, int side_number, OutputStream ostream) throws IOException {
            p_result.clear();

            return 0;
        }
    }
}

/**
 * ディスクライト時のオプション
 */
class DiskWriteOptions {

    protected boolean m_trim_unused_data;

    public DiskWriteOptions() {
        m_trim_unused_data = false;
    }

    public DiskWriteOptions(boolean n_trim_unused_data) {
        m_trim_unused_data = n_trim_unused_data;
    }

    public boolean isTrimUnusedData() {
        return m_trim_unused_data;
    }
}

