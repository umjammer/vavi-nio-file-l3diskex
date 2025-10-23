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

import l3diskex.Utils;
import l3diskex.diskimg.DiskImage.DiskImageFile;
import l3diskex.diskimg.FileParam.DiskTypeHint;
import l3diskex.diskimg.FileParam.FileParamFormat;

import static l3diskex.diskimg.FileParam.gFileTypes;


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
     * @param file_format ファイルの形式名("d88","plain"など)
     * @param param_hint  ディスクパラメータヒント("plain"時のみ)
     */
    public int parse(String file_format, DiskParam param_hint) throws IOException {
        return parse(file_format, param_hint, DiskImageFile.MODIFY_NONE);
    }

    /**
     * 指定ディスクを解析してこれを既存のディスクイメージに追加する
     *
     * @param file_format ファイルの形式名("d88","plain"など)
     * @param param_hint  ディスクパラメータヒント("plain"時のみ)
     */
    public int parseAdd(String file_format, DiskParam param_hint) throws IOException {
        return parse(file_format, param_hint, DiskImageFile.MODIFY_ADD);
    }

    /**
     * ディスクイメージをチェック
     *
     * @param file_format  [in,out] ファイルの形式名("d88","plain"など)
     * @param disk_params  [out] ディスクパラメータの候補
     * @param manual_param [out] 候補がないときのパラメータヒント
     */
    public int check(String file_format, List<DiskParam> disk_params,
                     DiskParam manual_param) throws IOException {
        return check(file_format, disk_params, manual_param,
                DiskImageFile.MODIFY_NONE);
    }

    public String getImageType() {
        return imageType;
    }

    /**
     * ディスクイメージの解析
     *
     * @param file_format ファイルの形式名("d88","plain"など)
     * @param param_hint  ディスクパラメータヒント("plain"時のみ)
     * @param mod_flags   オープン/追加 DiskImageFile::Add()
     * @return 0: 正常, -1: エラーあり, 1: 警告あり
     */
    private int parse(String file_format, DiskParam param_hint, short mod_flags) throws IOException {
        boolean[] support = {false};
        int rc = -1;

        imageType = "";
        if (!file_format.isEmpty()) {
            // ファイル形式の指定あり
            rc = selectPerser(file_format, param_hint, mod_flags, support);
            if (rc >= 0) {
                imageType = file_format;
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
     * @param file_format  [in,out] ファイルの形式名("d88","plain"など)
     * @param disk_params  [out] ディスクパラメータの候補
     * @param manual_param [out] 候補がないときのパラメータヒント
     * @param mod_flags    オープン/追加 DiskImageFile::Add()
     * @return 0: 正常, -1: エラーあり
     */
    private int check(String file_format, List<DiskParam> disk_params,
                      DiskParam manual_param, short mod_flags) throws IOException {
        boolean[] support = {false};
        int rc = -1;

        if (file_format.isEmpty()) {
            // ファイル形式の指定がない場合

            // 拡張子で判定
            String ext = Utils.getExt(filepath.getFileName().toString());

            // サポートしているファイルか
            FileParam fitem = gFileTypes.findExt(ext);
            if (fitem == null) {
                result.setError(DiskResult.ERR_UNSUPPORTED);
                return result.getValid();
            }

            // 指定形式で解析する
            List<FileParamFormat> formats = fitem.getFormats();
            for (int i = 0; i < formats.size(); i++) {
                FileParamFormat param_format = formats.get(i);
                rc = selectChecker(param_format.getType(), param_format.getHints(), null, disk_params, manual_param, mod_flags, support);
                if (rc >= 0) {
                    file_format = param_format.getType();
                    break;
                }
            }

        } else {
            // ファイル形式の指定あり

            rc = selectChecker(file_format, null, null, disk_params, manual_param, mod_flags, support);

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
     * @param type       ファイルの形式名("d88","plain"など)
     * @param disk_param ディスクパラメータ("plain"時のみ)
     * @param mod_flags  オープン/追加 DiskImageFile::Add()
     * @param support    [out] サポートしているファイルか
     * @return 1: 警告, 0: 正常, -1: エラー
     */
    private int selectPerser(String type, DiskParam disk_param,
                             short mod_flags, boolean[] support) throws IOException {
        int rc = -1;
        if ("d88".equalsIgnoreCase(type)) {
            DiskD88Parser ps = new DiskD88Parser(file, mod_flags, result);
            rc = ps.parse(stream, null);
            support[0] = true;
        } else if ("cpcdsk".equalsIgnoreCase(type)) {
            DiskDskParser ps = new DiskDskParser(file, mod_flags, result);
            if (ps.check(stream) != 0) {
                rc = ps.parse(stream);
            }
            support[0] = true;
        } else if ("fdi".equalsIgnoreCase(type)) {
            DiskFDIParser ps = new DiskFDIParser(file, mod_flags, result);
            rc = ps.parse(stream, disk_param);
            support[0] = true;
        } else if ("cqmimg".equalsIgnoreCase(type)) {
            DiskCQMParser ps = new DiskCQMParser(file, mod_flags, result);
            rc = ps.parse(stream, disk_param);
            support[0] = true;
        } else if ("teletd0".equalsIgnoreCase(type)) {
            DiskTD0Parser ps = new DiskTD0Parser(file, mod_flags, result);
            rc = ps.parse(stream, null);
            support[0] = true;
        } else if ("difcdim".equalsIgnoreCase(type)) {
            DiskDIMParser ps = new DiskDIMParser(file, mod_flags, result);
            rc = ps.parse(stream, disk_param);
            support[0] = true;
        } else if ("v98fdd".equalsIgnoreCase(type)) {
            DiskVFDParser ps = new DiskVFDParser(file, mod_flags, result);
            rc = ps.parse(stream, null);
            support[0] = true;
        } else if ("imd".equalsIgnoreCase(type)) {
            DiskIMDParser ps = new DiskIMDParser(file, mod_flags, result);
            rc = ps.parse(stream, null);
            support[0] = true;
        } else if ("dskstr".equalsIgnoreCase(type)) {
            DiskSTRParser ps = new DiskSTRParser(file, mod_flags, result);
            rc = ps.parse(stream, null);
            support[0] = true;
        } else if ("g64".equalsIgnoreCase(type)) {
            DiskG64Parser ps = new DiskG64Parser(file, mod_flags, result);
            rc = ps.parse(stream, null);
            support[0] = true;
        } else if ("2mg".equalsIgnoreCase(type)) {
            Disk2MGParser ps = new Disk2MGParser(file, mod_flags, result);
            rc = ps.parse(stream, disk_param);
            support[0] = true;
        } else if ("adc".equalsIgnoreCase(type)) {
            DiskADCParser ps = new DiskADCParser(file, mod_flags, result);
            rc = ps.parse(stream, disk_param);
            support[0] = true;
        } else if ("dmk".equalsIgnoreCase(type)) {
            DiskDmkParser ps = new DiskDmkParser(file, mod_flags, result);
            if (ps.check(stream) >= 0) {
                rc = ps.parse(stream, null);
            }
            support[0] = true;
        } else if ("jv3".equalsIgnoreCase(type)) {
            DiskJV3Parser ps = new DiskJV3Parser(file, mod_flags, result);
            rc = ps.parse(stream, null);
            support[0] = true;
        } else if ("hfe".equalsIgnoreCase(type)) {
            DiskHfeParser ps = new DiskHfeParser(file, mod_flags, result);
            rc = ps.parse(stream, null);
            support[0] = true;
        } else if ("plain".equalsIgnoreCase(type)) {
            DiskPlainParser ps = new DiskPlainParser(file, mod_flags, result);
            rc = ps.parse(stream, disk_param);
            support[0] = true;
        } else {
            logger.log(Level.INFO, type + " is not supported");
        }
        return rc;
    }

    /**
     * ファイルのチェック方法を選択
     *
     * @param type         ファイルの形式名("d88","plain"など)
     * @param disk_hints   ディスクパラメータヒント("plain"時のみ)
     * @param disk_param   ディスクパラメータ("plain"時のみ)
     * @param disk_params  [out] ディスクパラメータの候補
     * @param manual_param [out] 候補がないときのパラメータヒント
     * @param mod_flags    オープン/追加 DiskImageFile::Add()
     * @param support      [out] サポートしているファイルか
     * @return 1: 候補がないので改めてディスク種類を選択してもらう, 0: 候補あり正常, -1: エラー終了
     */
    private int selectChecker(String type, List<DiskTypeHint> disk_hints,
                              DiskParam disk_param, List<DiskParam> disk_params,
                              DiskParam manual_param, short mod_flags,
                              boolean[] support) throws IOException {
        int rc = -1;
        if ("d88".equalsIgnoreCase(type)) {
            DiskD88Parser ps = new DiskD88Parser(file, mod_flags, result);
            rc = ps.check(stream);
            support[0] = true;
        } else if ("cpcdsk".equalsIgnoreCase(type)) {
            DiskDskParser ps = new DiskDskParser(file, mod_flags, result);
            rc = ps.check(stream);
            support[0] = true;
        } else if ("fdi".equalsIgnoreCase(type)) {
            DiskFDIParser ps = new DiskFDIParser(file, mod_flags, result);
            rc = ps.check(stream, disk_hints, disk_param, disk_params, manual_param);
            support[0] = true;
        } else if ("cqmimg".equalsIgnoreCase(type)) {
            DiskCQMParser ps = new DiskCQMParser(file, mod_flags, result);
            rc = ps.check(stream, disk_hints, disk_param, disk_params, manual_param);
            support[0] = true;
        } else if ("teletd0".equalsIgnoreCase(type)) {
            DiskTD0Parser ps = new DiskTD0Parser(file, mod_flags, result);
            rc = ps.check(stream);
            support[0] = true;
        } else if ("difcdim".equalsIgnoreCase(type)) {
            DiskDIMParser ps = new DiskDIMParser(file, mod_flags, result);
            rc = ps.check(stream, disk_hints, disk_param, disk_params, manual_param);
            support[0] = true;
        } else if ("v98fdd".equalsIgnoreCase(type)) {
            DiskVFDParser ps = new DiskVFDParser(file, mod_flags, result);
            rc = ps.check(stream);
            support[0] = true;
        } else if ("imd".equalsIgnoreCase(type)) {
            DiskIMDParser ps = new DiskIMDParser(file, mod_flags, result);
            rc = ps.check(stream);
            support[0] = true;
        } else if ("dskstr".equalsIgnoreCase(type)) {
            DiskSTRParser ps = new DiskSTRParser(file, mod_flags, result);
            rc = ps.check(stream);
            support[0] = true;
        } else if ("g64".equalsIgnoreCase(type)) {
            DiskG64Parser ps = new DiskG64Parser(file, mod_flags, result);
            rc = ps.check(stream);
            support[0] = true;
        } else if ("2mg".equalsIgnoreCase(type)) {
            Disk2MGParser ps = new Disk2MGParser(file, mod_flags, result);
            rc = ps.check(stream, disk_hints, disk_param, disk_params, manual_param);
            support[0] = true;
        } else if ("adc".equalsIgnoreCase(type)) {
            DiskADCParser ps = new DiskADCParser(file, mod_flags, result);
            rc = ps.check(stream, disk_hints, disk_param, disk_params, manual_param);
            support[0] = true;
        } else if ("dmk".equalsIgnoreCase(type)) {
            DiskDmkParser ps = new DiskDmkParser(file, mod_flags, result);
            rc = ps.check(stream);
            support[0] = true;
        } else if ("jv3".equalsIgnoreCase(type)) {
            DiskJV3Parser ps = new DiskJV3Parser(file, mod_flags, result);
            rc = ps.check(stream);
            support[0] = true;
        } else if ("hfe".equalsIgnoreCase(type)) {
            DiskHfeParser ps = new DiskHfeParser(file, mod_flags, result);
            rc = ps.check(stream);
            support[0] = true;
        } else if ("plain".equalsIgnoreCase(type)) {
            DiskPlainParser ps = new DiskPlainParser(file, mod_flags, result);
            rc = ps.check(stream, disk_hints, disk_param, disk_params, manual_param);
            support[0] = true;
        }
        return rc;
    }

    /// ディスクパーサー
    public static abstract class DiskImageParser {

        protected DiskImageFile file;
        protected short modFlags;
        protected DiskResult result;

        public DiskImageParser(DiskImageFile file, short mod_flags,
                               DiskResult result) {
            this.file = file;
            this.modFlags = mod_flags;
            this.result = result;
        }

        /**
         * チェック
         *
         * @param istream 解析対象データ
         * @return 1: 選択ダイアログ表示, 0: 正常（候補が複数ある時はダイアログ表示）
         */
        public int check(InputStream istream) throws IOException {
            return result.getValid();
        }

        /**
         * チェック
         *
         * @param istream      解析対象データ
         * @param hints        ディスクパラメータヒント("2D"など)
         * @param disk_param   ディスクパラメータ disk_hints指定時はNullable
         * @param disk_params  [out] ディスクパラメータの候補
         * @param manual_param [out] 候補がないときのパラメータヒント
         * @return 1: 選択ダイアログ表示,  0: 正常（候補が複数ある時はダイアログ表示）
         */
        public int check(InputStream istream, List<DiskTypeHint> hints,
                         DiskParam disk_param, List<DiskParam> disk_params,
                         DiskParam manual_param) throws IOException {
            return result.getValid();
        }

        /**
         * ファイルイメージを解析
         *
         * @param istream    解析対象データ
         * @param disk_param ディスクパラメータ
         * @return 0: 正常, -1: エラーあり, 1: 警告あり
         */
        public int parse(InputStream istream, DiskParam disk_param /* = null */) throws IOException {
            return result.getValid();
        }
    }
}
