/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.diskimg;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import l3diskex.diskimg.FileParam.FileParamFormat;

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

    // 拡張子をさがす
    private int CanSaveDiskByExt(int disk_number, int side_number) {
        int rc = 0;

        // ファイル形式の指定がない場合
        // C++: wxFileName fpath(m_file_path);
        java.io.File fpath = new java.io.File(m_file_path);

        // 拡張子で判定
        // C++: wxString ext = fpath.GetExt();
        String filename = fpath.getName();
        String ext = "";
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < filename.length() - 1) {
            ext = filename.substring(dotIndex + 1);
        }

        // サポートしているファイルか
        // C++: const FileParam *fitem = gFileTypes.FindExt(ext);
        FileParam fitem = gFileTypes.findExt(ext);
        if (fitem == null) {
            p_result.setError(DiskResult.ERR_UNSUPPORTED);
            return p_result.getValid();
        }

        // 指定した形式でファイル出力
        // C++: const FileParamFormats *formats = &fitem->GetFormats();
        List<FileParamFormat> formats = fitem.getFormats();
        for (int i = 0; i < formats.size(); i++) {
            // C++: const FileParamFormat *param_format = &formats->Item(i);
            FileParamFormat param_format = formats.get(i);
            // C++: rc = SelectCanSaveDisk(param_format->GetType(), disk_number, side_number);
            rc = SelectCanSaveDisk(param_format.getType(), disk_number, side_number);
            if (rc >= 0) {
                break;
            }
        }

        return rc;
    }

    // 拡張子で保存形式を判定
    private int SelectCanSaveDisk(String file_format, int disk_number, int side_number) {
        int rc = -1;
        // C++: wxT("d88") -> "d88" (assuming wxT converts to String)
        if (file_format.equals("d88")) {
            // d88形式
            DiskD88Writer wr = new DiskD88Writer(this, p_result);
            rc = wr.validateDisk(p_image, disk_number, side_number);
            // } else if (file_format.equals("cpcdsk")) {
            // // CPC DSK形式
            // DiskDskWriter wr(result);
            // rc = wr.ValidateDisk(p_image, disk_number, side_number);
        } else if (file_format.equals("plain")) {
            // ベタ
            DiskPlainWriter wr = new DiskPlainWriter(this, p_result);
            rc = wr.validateDisk(p_image, disk_number, side_number);
        }
        return rc;
    }

    // 拡張子をさがす
    private int SaveDiskByExt(int disk_number, int side_number, boolean[] support) {
        int rc = 0;

        // ファイル形式の指定がない場合
        // C++: wxFileName fpath(m_file_path);
        java.io.File fpath = new java.io.File(m_file_path);

        // 拡張子で判定
        // C++: wxString ext = fpath.GetExt();
        String filename = fpath.getName();
        String ext = "";
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < filename.length() - 1) {
            ext = filename.substring(dotIndex + 1);
        }

        // サポートしているファイルか
        // C++: const FileParam *fitem = gFileTypes.FindExt(ext);
        FileParam fitem = gFileTypes.findExt(ext);
        if (fitem == null) {
            p_result.setError(DiskResult.ERR_UNSUPPORTED);
            return p_result.getValid();
        }

        // 指定した形式でファイル出力
        // C++: const FileParamFormats *formats = &fitem->GetFormats();
        List<FileParamFormat> formats = fitem.getFormats();
        for (int i = 0; i < formats.size(); i++) {
            // C++: const FileParamFormat *param_format = &formats->Item(i);
            FileParamFormat param_format = formats.get(i);
            // C++: rc = SelectSaveDisk(param_format->GetType(), disk_number, side_number, support);
            rc = SelectSaveDisk(param_format.getType(), disk_number, side_number, support);
            if (rc >= 0) {
                break;
            }
        }

        return rc;
    }

    // 拡張子で保存形式を判定
    private int SelectSaveDisk(String file_format, int disk_number, int side_number, boolean[] support) {
        int rc = -1;
        support[0] = false; // Initialize support status before switch/if-else

        // C++: wxT("d88") -> "d88" (assuming wxT converts to String)
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

    /**
     * @param image   ディスクイメージ
     * @param path    ファイルパス
     * @param options 出力時のオプション
     * @param result  結果
     */
    public DiskWriter(DiskImage image, String path, DiskWriteOptions options, DiskResult result) {
        // C++: DiskWriter(DiskImage *image, const wxString &path, const DiskWriteOptions &options, DiskResult *result) : DiskWriteOptions(options)
        super(options.m_trim_unused_data);
        p_image = image;
        m_file_path = path;
        p_result = result;
        p_ostream = null;
        Open(path);
    }

    /**
     * @param image  ディスクイメージ
     * @param result 結果
     */
    public DiskWriter(DiskImage image, DiskResult result) {
        // C++: DiskWriter(DiskImage *image, DiskResult *result) : DiskWriteOptions()
        super();
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
    // C++ equivalent: ~DiskWriter()

    /**
     * 出力先を開く
     *
     * @param path 出力先ファイルパス
     * @return 結果
     */
    public int Open(String path) {
        // C++: wxFileOutputStream *fstream = new wxFileOutputStream(path);
        FileOutputStream fstream;
        try {
            fstream = new FileOutputStream(path);
            p_ostream = fstream;
            // C++: if (!fstream->IsOk()) { result->SetError(DiskResult::ERR_CANNOT_SAVE); }
            // For FileOutputStream, IsOk() check equivalent is usually handled by catching FileNotFoundException
            // and checking if stream is null, but since it's constructed, we assume it's "ok" unless an
            // IOException occurs during write. We'll rely on the later IsOk() check for stream validity.
            // The C++ logic only checks after creating the stream.
            // The C++ comment suggests a check *was* there but is commented out. We just create.

        } catch (java.io.FileNotFoundException e) {
            // FileOutputStream constructor throws this.
            p_result.setError(DiskResult.ERR_CANNOT_SAVE);
            p_ostream = null; // Ensure stream is null on failure
        }
        m_ownstream = true;
        return p_result.getValid();
    }

    /**
     * 出力先がオープンしているか
     *
     * @return true if open and ready, false otherwise
     */
    public boolean IsOk() {
        // In Java, an OutputStream is considered "OK" if it's not null, hasn't been closed, and no IOException occurred on last operation.
        // Direct equivalent to wxOutputStream::IsOk() is difficult. We'll approximate.
        return p_ostream != null; // Simplified approximation
    }

    /**
     * 対応しているディスクイメージか
     *
     * @param file_format ファイルフォーマット
     * @return true if supported, false otherwise
     */
    public static boolean SupportedFormat(String file_format) {
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
    public int CanSave(String file_format) {
        return CanSaveDisk(-1, -1, file_format);
    }

    /**
     * ストリームの内容をファイルに保存できるか
     *
     * @param disk_number ディスク番号
     * @param side_number サイド番号
     * @param file_format ファイルフォーマット
     * @return 0:できる, 1:警告あり (>=0 success, <0 error)
     */
    public int CanSaveDisk(int disk_number, int side_number, String file_format) {
        int rc = 0;
        if (file_format.isEmpty()) {
            // ファイル形式の指定がない場合
            rc = CanSaveDiskByExt(disk_number, side_number);
        } else {
            // ファイル形式の指定あり
            rc = SelectCanSaveDisk(file_format, disk_number, side_number);
        }
        return rc;
    }

    /**
     * ディスクイメージの保存
     *
     * @param file_format ファイルフォーマット
     * @return 結果
     */
    public int Save(String file_format) {
        return SaveDisk(-1, -1, file_format);
    }

    /**
     * ストリームの内容をファイルに保存
     *
     * @param disk_number ディスク番号
     * @param side_number サイド番号
     * @param file_format ファイルフォーマット
     * @return 結果
     */
    public int SaveDisk(int disk_number, int side_number, String file_format) {
        int rc = 0;
        // In C++, 'bool support' is an output parameter, in Java, use a mutable object (or Ref class)
        boolean[] support = {false};

        if (!IsOk()) {
            p_result.setError(DiskResult.ERR_CANNOT_SAVE);
            return p_result.getValid();
        }

        if (file_format.isEmpty()) {
            // ファイル形式の指定がない場合
            rc = SaveDiskByExt(disk_number, side_number, support);
        } else {
            // ファイル形式の指定あり
            rc = SelectSaveDisk(file_format, disk_number, side_number, support);
        }
        if (!support[0]) {
            p_result.setError(DiskResult.ERR_UNSUPPORTED);
            return p_result.getValid();
        }
        return rc;
    }

    // ----------------------------------------------------------------------

    /**
     * 形式ごとのディスクライター
     */
    static class DiskImageWriter {

        protected DiskWriter p_dw;
        protected DiskResult p_result;

        public DiskImageWriter(DiskWriter dw_, DiskResult result_) {
            p_dw = dw_;
            p_result = result_;
        }

        // Java equivalent to virtual destructor is no explicit destructor
        // public void close() {}

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
        public int saveDisk(DiskImage image, int disk_number, int side_number, OutputStream ostream) {
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

    // Java doesn't have explicit destructors, so no direct equivalent for virtual ~DiskWriteOptions()

    public boolean IsTrimUnusedData() {
        return m_trim_unused_data;
    }
}

