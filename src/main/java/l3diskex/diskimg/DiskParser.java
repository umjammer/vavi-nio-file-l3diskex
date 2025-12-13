/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.diskimg;

import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Path;
import java.util.List;
import java.util.ServiceLoader;

import l3diskex.Utils;
import l3diskex.diskimg.DiskImage.DiskImageFile;
import l3diskex.diskimg.FileParam.DiskTypeHint;
import l3diskex.diskimg.FileParam.FileParamFormat;

import static l3diskex.diskimg.FileParam.fileTypes;


/**
 * ディスクパーサー
 */
public class DiskParser {

    private static final Logger logger = System.getLogger(DiskParser.class.getName());

    private final Path filepath;
    private final InputStream stream;
    private final DiskImageFile file;
    private final DiskResult result;
    private String imageType;

    /**
     * コンストラクタ
     *
     * @param filepath 解析するファイルのパス
     * @param stream   上記ファイルのストリーム
     * @param file     [in,out] 既存のディスクイメージ
     * @param result   [out] 結果
     */
    public DiskParser(String filepath, InputStream stream,
                      DiskImageFile file, DiskResult result) {
        this.filepath = Path.of(filepath);
        this.stream = stream;
        this.file = file;
        this.result = result;
        this.imageType = "";
    }

    /**
     * ディスクイメージを新たに解析する
     *
     * @param fileFormat ファイルの形式名 ("d88", "plain" など)
     * @param paramHint  ディスクパラメータヒント ("plain" 時のみ)
     */
    public int parse(String fileFormat, DiskParam paramHint) throws IOException {
        return parse(fileFormat, paramHint, DiskImageFile.MODIFY_NONE);
    }

    /**
     * 指定ディスクを解析してこれを既存のディスクイメージに追加する
     *
     * @param fileFormat ファイルの形式名 ("d88", "plain" など)
     * @param paramHint  ディスクパラメータヒント ("plain" 時のみ)
     */
    public int parseAdd(String fileFormat, DiskParam paramHint) throws IOException {
        return parse(fileFormat, paramHint, DiskImageFile.MODIFY_ADD);
    }

    /**
     * ディスクイメージをチェック
     *
     * @param fileFormat  [in,out] ファイルの形式名 ("d88", "plain", "", など)
     * @param diskParams  [out] ディスクパラメータの候補
     * @param manualParam [out] 候補がないときのパラメータヒント
     */
    public int check(String[] fileFormat, List<DiskParam> diskParams, DiskParam manualParam) throws IOException {
        return check(fileFormat, diskParams, manualParam, DiskImageFile.MODIFY_NONE);
    }

    public String getImageType() {
        return imageType;
    }

    /**
     * ディスクイメージの解析
     *
     * @param fileFormat ファイルの形式名 ("d88", "plain" など)
     * @param paramHint  ディスクパラメータヒント ("plain" 時のみ)
     * @param modFlags   オープン/追加 DiskImageFile#add()
     * @return 0: 正常, -1: エラーあり, 1: 警告あり
     */
    private int parse(String fileFormat, DiskParam paramHint, short modFlags) throws IOException {
        boolean[] support = {false};
        int rc = -1;

        imageType = "";
        if (!fileFormat.isEmpty()) {
            // ファイル形式の指定あり
            rc = selectParser(fileFormat, paramHint, modFlags, support);
            if (rc >= 0) {
                imageType = fileFormat;
            }
        }
        if (!support[0]) {
            result.setError(DiskResult.ERR_UNSUPPORTED);
            return result.getValid();
        }
        return rc;
    }

    /**
     * ディスクイメージのチェック
     *
     * @param fileFormat  [in,out] ファイルの形式名 ("d88", "plain", "", など)
     * @param diskParams  [out] ディスクパラメータの候補
     * @param manualParam [out] 候補がないときのパラメータヒント
     * @param modFlags    オープン/追加 DiskImageFile::Add()
     * @return 0: 正常, -1: エラーあり
     */
    private int check(String[] fileFormat, List<DiskParam> diskParams,
                      DiskParam manualParam, short modFlags) throws IOException {
        boolean[] support = {false};
        int rc = -1;

        if (fileFormat[0].isEmpty()) {
            // ファイル形式の指定がない場合

            // 拡張子で判定
            String ext = Utils.getExt(filepath.getFileName().toString());

            // サポートしているファイルか
            FileParam fItem = fileTypes.findExt(ext);
            if (fItem == null) {
                result.setError(DiskResult.ERR_UNSUPPORTED);
                return result.getValid();
            }

            // 指定形式で解析する
            List<FileParamFormat> formats = fItem.getFormats();
logger.log(Level.TRACE, "formats: %d, %s".formatted(formats.size(), formats));
            for (FileParamFormat format : formats) {
                rc = selectChecker(format.getType(), format.getHints(), null, diskParams, manualParam, modFlags, support);
logger.log(Level.TRACE, "selectChecker: %d, %s".formatted(rc, format.getType()));
                if (rc >= 0) {
                    fileFormat[0] = format.getType();
                    break;
                }
            }

        } else {
            // ファイル形式の指定あり

            rc = selectChecker(fileFormat[0], null, null, diskParams, manualParam, modFlags, support);
        }
        if (!support[0]) {
            result.setError(DiskResult.ERR_UNSUPPORTED);
            return result.getValid();
        }
        return rc;
    }

    /**
     * ファイルの解析方法を選択
     *
     * @param type      ファイルの形式名 ("d88", "plain" など)
     * @param diskParam ディスクパラメータ ("plain" 時のみ)
     * @param modFlags  オープン/追加 DiskImageFile#add()
     * @param support   [out] サポートしているファイルか
     * @return 1: 警告, 0: 正常, -1: エラー
     */
    private int selectParser(String type, DiskParam diskParam,
                             short modFlags, boolean[] support) throws IOException {

        ServiceLoader<DiskImageParser> serviceLoader = ServiceLoader.load(DiskImageParser.class);
        for (DiskImageParser parser : serviceLoader) {
            if (parser.isSupported(type)) {
                parser.init(file, modFlags, result);
                int rc = -1;
                if (parser.needsCheck()) {
                    if (parser.check(stream) != 0) {
                        rc = parser.parse(stream, diskParam);
                    }
                } else {
                    rc = parser.parse(stream, diskParam);
                }
                support[0] = true;
                return rc;
            }
        }

        logger.log(Level.WARNING, type + " is not supported");
        return -1;
    }

    /**
     * ファイルのチェック方法を選択
     *
     * @param type        ファイルの形式名("d88","plain"など)
     * @param diskHints   ディスクパラメータヒント("plain"時のみ)
     * @param diskParam   ディスクパラメータ("plain"時のみ)
     * @param diskParams  [out] ディスクパラメータの候補
     * @param manualParam [out] 候補がないときのパラメータヒント
     * @param modFlags    オープン/追加 DiskImageFile::Add()
     * @param support     [out] サポートしているファイルか
     * @return 1: 候補がないので改めてディスク種類を選択してもらう, 0: 候補あり正常, -1: エラー終了
     */
    private int selectChecker(String type, List<DiskTypeHint> diskHints,
                              DiskParam diskParam, List<DiskParam> diskParams,
                              DiskParam manualParam, short modFlags,
                              boolean[] support) throws IOException {

        ServiceLoader<DiskImageParser> serviceLoader = ServiceLoader.load(DiskImageParser.class);
        for (DiskImageParser parser : serviceLoader) {
logger.log(Level.TRACE, type + " is supported: " + parser.isSupported(type));
            if (parser.isSupported(type)) {
                parser.init(file, modFlags, result);
                int rc = parser.check(stream, diskHints, diskParam, diskParams, manualParam);
                support[0] = true;
                return rc;
            }
        }

logger.log(Level.WARNING, type + " is not supported");
        return -1;
    }

    /** ディスクパーサー */
    public static abstract class DiskImageParser {

        protected DiskImageFile file;
        protected short modFlags;
        protected DiskResult result;

        /** type string is supported nor not */
        public abstract boolean isSupported(String type);

        /** does check need before parse */
        public boolean needsCheck() {
            return false;
        }

        /** returns teh condition if the check is needed */
        public boolean checkCondition(InputStream iStream) throws IOException {
            return false;
        }

        public void init(DiskImageFile file, short modFlags, DiskResult result) {
            this.file = file;
            this.modFlags = modFlags;
            this.result = result;
        }

        /**
         * チェック
         *
         * @param iStream 解析対象データ
         * @return 1: 選択ダイアログ表示, 0: 正常（候補が複数ある時はダイアログ表示）
         */
        public int check(InputStream iStream) throws IOException {
            return result.getValid();
        }

        /**
         * チェック
         *
         * @param iStream     解析対象データ
         * @param hints       ディスクパラメータヒント("2D"など)
         * @param diskParam   ディスクパラメータ disk_hints指定時はNullable
         * @param diskParams  [out] ディスクパラメータの候補
         * @param manualParam [out] 候補がないときのパラメータヒント
         * @return 1: 選択ダイアログ表示,  0: 正常（候補が複数ある時はダイアログ表示）
         */
        public int check(InputStream iStream, List<DiskTypeHint> hints,
                         DiskParam diskParam, List<DiskParam> diskParams,
                         DiskParam manualParam) throws IOException {
            return result.getValid();
        }

        /**
         * ファイルイメージを解析
         *
         * @param iStream   解析対象データ
         * @param diskParam ディスクパラメータ
         * @return 0: 正常, -1: エラーあり, 1: 警告あり
         */
        public int parse(InputStream iStream, DiskParam diskParam /* = null */) throws IOException {
            return result.getValid();
        }
    }
}
