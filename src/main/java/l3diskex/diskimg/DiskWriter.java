/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.diskimg;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import l3diskex.Utils;
import l3diskex.diskimg.FileParam.FileParamFormat;
import l3diskex.diskimg.writer.DiskD88Writer;
import l3diskex.diskimg.writer.DiskPlainWriter;
import vavi.io.SeekableDataOutputStream;

import static l3diskex.diskimg.FileParam.fileTypes;


/**
 * ディスクライター
 */
public class DiskWriter extends DiskWriteOptions {

    public static final String[] formatTypeNamesForSave = {
            "", "d88", "plain", null
    };

    private final String filePath;
    private final DiskImage image;
    private OutputStream oStream;
    private boolean ownStream;
    private final DiskResult result;

    /**
     * 拡張子をさがす
     * @param diskNumber ディスク番号
     * @param sideNumber サイド番号
     */
    private int canSaveDiskByExt(int diskNumber, int sideNumber) {
        int rc = 0;

        // ファイル形式の指定がない場合
        String fpath = filePath;

        // 拡張子で判定
        String ext = Utils.getExt(fpath);

        // サポートしているファイルか
        FileParam fItem = fileTypes.findExt(ext);
        if (fItem == null) {
            result.setError(DiskResult.ERR_UNSUPPORTED);
            return result.getValid();
        }

        // 指定した形式でファイル出力
        List<FileParamFormat> formats = fItem.getFormats();
        for (FileParamFormat format : formats) {
            rc = selectCanSaveDisk(format.getType(), diskNumber, sideNumber);
            if (rc >= 0) {
                break;
            }
        }

        return rc;
    }

    /**
     * 拡張子で保存形式を判定＆保存できるか
     * @param fileFormat ファイルフォーマット
     * @param diskNumber ディスク番号
     * @param sideNumber サイド番号
     */
    private int selectCanSaveDisk(String fileFormat, int diskNumber, int sideNumber) {
        int rc = -1;
        // C++: wxT("d88") -> "d88" (assuming wxT converts to String)
        if (fileFormat.equals("d88")) {
            // d88形式
            DiskD88Writer writer = new DiskD88Writer(this, result);
            rc = writer.validateDisk(image, diskNumber, sideNumber);
//        } else if (fileFormat.equals("cpcdsk")) {
//            // CPC DSK形式
//            DiskDskWriter writer (result);
//            rc = wr.ValidateDisk(image, diskNumber, sideNumber);
        } else if (fileFormat.equals("plain")) {
            // ベタ
            DiskPlainWriter writer = new DiskPlainWriter(this, result);
            rc = writer.validateDisk(image, diskNumber, sideNumber);
        }
        return rc;
    }

    /**
     * 拡張子をさがす
     *
     * @param diskNumber ディスク番号
     * @param sideNumber サイド番号
     * @param support    [out] 対応しているフォーマットならtrue
     */
    private int saveDiskByExt(int diskNumber, int sideNumber, boolean[] support) throws IOException {
        int rc = 0;

        // ファイル形式の指定がない場合
        String fpath = filePath;

        // 拡張子で判定
        String ext = Utils.getExt(fpath);

        // サポートしているファイルか
        FileParam fitem = fileTypes.findExt(ext);
        if (fitem == null) {
            result.setError(DiskResult.ERR_UNSUPPORTED);
            return result.getValid();
        }

        // 指定した形式でファイル出力
        List<FileParamFormat> formats = fitem.getFormats();
        for (FileParamFormat format : formats) {
            rc = selectSaveDisk(format.getType(), diskNumber, sideNumber, support);
            if (rc >= 0) {
                break;
            }
        }

        return rc;
    }

    /** 拡張子で保存形式を判定 */
    private int selectSaveDisk(String fileFormat, int diskNumber, int sideNumber, boolean[] support) throws IOException {
        int rc = -1;
        if (fileFormat.equals("d88")) {
            // d88形式
            DiskD88Writer writer = new DiskD88Writer(this, result);
            rc = writer.saveDisk(image, diskNumber, sideNumber, oStream);
            support[0] = true;
//        } else if (fileFormat.equals("cpcdsk")) {
//            // CPC DSK形式
//            DiskDskWriter writer = new DiskDskWriter(result);
//            rc = writer.SaveDisk(image, diskNumber, sideNumber, oStream);
//            support[0] = true;
        } else if (fileFormat.equals("plain")) {
            // ベタ
            DiskPlainWriter writer = new DiskPlainWriter(this, result);
            rc = writer.saveDisk(image, diskNumber, sideNumber, oStream);
            support[0] = true;
        }

        if (support[0] && rc >= 0) {
            // 保存したファイル名を持っておく
            image.setFileName(filePath);
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
    public DiskWriter(DiskImage image, String path, DiskWriteOptions options, DiskResult result) throws IOException {
        super(options.trimUnusedData);
        this.image = image;
        filePath = path;
        this.result = result;
        oStream = null;
        open(path);
    }

    /**
     * @param image  ディスクイメージ
     * @param result 結果
     */
    public DiskWriter(DiskImage image, DiskResult result) {
        this.image = image;
        filePath = "";
        this.result = result;
        oStream = null;
        ownStream = false;
    }

    /**
     * 出力先を開く
     *
     * @param path 出力先ファイルパス
     * @return 結果
     */
    public int open(String path) throws IOException {
        OutputStream fStream = new SeekableDataOutputStream(Files.newByteChannel(Path.of(path)));
        oStream = fStream;
        ownStream = true;
        return result.getValid();
    }

    /**
     * 出力先がオープンしているか
     *
     * @return true if open and ready, false otherwise
     */
    public boolean isOk() {
        return oStream != null;
    }

    /**
     * 対応しているディスクイメージか
     *
     * @param fileFormat ファイルフォーマット
     * @return true if supported, false otherwise
     */
    public static boolean supportedFormat(String fileFormat) {
        boolean match = false;
        for (int i = 1; formatTypeNamesForSave[i] != null; i++) {
            if (fileFormat.equals(formatTypeNamesForSave[i])) {
                match = true;
                break;
            }
        }
        return match;
    }

    /**
     * ディスクイメージを保存できるか
     *
     * @param fileFormat ファイルフォーマット
     * @return 0: できる, 1: 警告あり (>=0: success, <0: error)
     */
    public int canSave(String fileFormat) {
        return canSaveDisk(-1, -1, fileFormat);
    }

    /**
     * ストリームの内容をファイルに保存できるか
     *
     * @param diskNumber ディスク番号
     * @param sideNumber サイド番号
     * @param fileFormat ファイルフォーマット
     * @return 0: できる, 1: 警告あり (>=0: success, <0: error)
     */
    public int canSaveDisk(int diskNumber, int sideNumber, String fileFormat) {
        int rc = 0;
        if (fileFormat.isEmpty()) {
            // ファイル形式の指定がない場合
            rc = canSaveDiskByExt(diskNumber, sideNumber);
        } else {
            // ファイル形式の指定あり
            rc = selectCanSaveDisk(fileFormat, diskNumber, sideNumber);
        }
        return rc;
    }

    /**
     * ディスクイメージの保存
     *
     * @param fileFormat ファイルフォーマット
     * @return 結果
     */
    public int save(String fileFormat) throws IOException {
        return saveDisk(-1, -1, fileFormat);
    }

    /**
     * ストリームの内容をファイルに保存
     *
     * @param diskNumber ディスク番号
     * @param sideNumber サイド番号
     * @param fileFormat ファイルフォーマット
     * @return 結果
     */
    public int saveDisk(int diskNumber, int sideNumber, String fileFormat) throws IOException {
        int rc = 0;
        boolean[] support = {false};

        if (!isOk()) {
            result.setError(DiskResult.ERR_CANNOT_SAVE);
            return result.getValid();
        }

        if (fileFormat.isEmpty()) {
            // ファイル形式の指定がない場合
            rc = saveDiskByExt(diskNumber, sideNumber, support);
        } else {
            // ファイル形式の指定あり
            rc = selectSaveDisk(fileFormat, diskNumber, sideNumber, support);
        }
        if (!support[0]) {
            result.setError(DiskResult.ERR_UNSUPPORTED);
            return result.getValid();
        }
        return rc;
    }

    /** 形式ごとのディスクライター */
    public static class DiskImageWriter {

        protected DiskWriter writer;
        protected DiskResult result;

        public DiskImageWriter(DiskWriter writer, DiskResult result) {
            this.writer = writer;
            this.result = result;
        }

        /**
         * ストリームの内容をファイルに保存できるか
         *
         * @param image       ディスクイメージ
         * @param diskNumber 0~: ディスク番号, -1: のときは全体
         * @param sideNumber 0~: サイド番号, -1: のときは両面
         * @return 0 正常
         */
        public int validateDisk(DiskImage image, int diskNumber, int sideNumber) {
            result.clear();

            return 0;
        }

        /**
         * ストリームの内容をファイルに保存
         *
         * @param image       ディスクイメージ
         * @param diskNumber 0~: ディスク番号, -1: のときは全体
         * @param sideNumber 0~: サイド番号, -1: のときは両面
         * @param oStream     出力先
         * @return 0 正常
         */
        public int saveDisk(DiskImage image, int diskNumber, int sideNumber, OutputStream oStream) throws IOException {
            result.clear();

            return 0;
        }
    }
}

/**
 * ディスクライト時のオプション
 */
class DiskWriteOptions {

    protected boolean trimUnusedData;

    public DiskWriteOptions() {
        trimUnusedData = false;
    }

    public DiskWriteOptions(boolean trimUnusedData) {
        this.trimUnusedData = trimUnusedData;
    }

    public boolean isTrimUnusedData() {
        return trimUnusedData;
    }
}

