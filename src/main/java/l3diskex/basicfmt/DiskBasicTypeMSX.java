/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt;

import java.io.IOException;

import l3diskex.basicfmt.BasicCommon.DirectoryMsDos;
import l3diskex.basicfmt.BasicFat.DiskBasicFat;
import l3diskex.basicfmt.BasicFmt.DiskBasic;
import l3diskex.basicfmt.BasicFmt.DiskBasicIdentifiedData;
import l3diskex.diskimg.DiskImage.DiskImageSector;


public class DiskBasicTypeMSX extends DiskBasicTypeMSDOS {

    /* -
     *  Excluded keywords – the last element is a sentinel (null)
     * - */
    private static final String[] C_EXCLUDE_KEYWORDS = {
        "IO      SYS",
        "IBMDOS",
        "MSDOS",
        "IBM",
        null
    };

    public DiskBasicTypeMSX(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryMsDos> dir) {
        super(basic, fat, dir);
    }

    /* -
     *  Check / assign FAT area
     * - */
    /**
     *  ディスクから各パラメータを取得＆必要なパラメータを計算
     *
     *  @param  is_formatting  フォーマット中か
     *  @return 1.0 正常
     *          0.0 - 1.0 警告あり
     *          <0.0 エラーあり
     */
    @Override
    public double parseParamOnDisk(boolean is_formatting) throws IOException {
        if (is_formatting) {
            return 0.0;
        }

        /* ① ParseMSDOSParamOnDisk()  */
        double valid_ratio = ParseMSDOSParamOnDisk(
                basic.getDisk(), is_formatting);

        if (valid_ratio >= 0.0) {
            /* ② セクタ情報を取得  */
            DiskImageSector sector = basic.getSector(0, 0, 1);
            if (sector == null) {
                return -1.0;
            }

            byte[] datas = sector.getSectorBuffer();
            if (datas == null) {
                return -1.0;
            }

            /* ③ MSXDOS という文字列があれば 0.8 を加算  */
            if (sector.find("MSXDOS".getBytes(), 6) >= 0) {
                valid_ratio += 0.8;
            } else if (sector.find("MSX".getBytes(), 3) >= 0) {
                /* ④ MSX があれば 0.4 を加算  */
                valid_ratio += 0.4;
            }

            /* ⑤ 除外するキーワードを検索  */
            for (int i = 0; C_EXCLUDE_KEYWORDS[i] != null; i++) {
                if (sector.find(C_EXCLUDE_KEYWORDS[i].getBytes(),
                        C_EXCLUDE_KEYWORDS[i].length()) >= 0) {
                    valid_ratio -= 0.5;
                    break;
                }
            }
        }

        /* ⑥ valid_ratio を -1.0 〜 1.0 の範囲にクリップ  */
        if (valid_ratio > 1.0) {
            valid_ratio = 1.0;
        } else if (valid_ratio < -1.0) {
            valid_ratio = -1.0;
        }

        return valid_ratio;
    }

    /* -
     *  Directory
     * - */
    /**
     *  サブディレクトリを作成できるか
     */
    @Override
    public boolean canMakeDirectory() {
        return false;
    }

    /* -
     *  Format
     * - */
    /**
     *  セクタデータを埋めた後の個別処理
     *         フォーマット IPL の書き込み
     */
    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) {
        byte[] buf = null;

        /* ① BIOS パラメータブロックを作成  */
        if (!createBiosParameterBlock("\u00eb\u00fe\u0090", "MSX", buf)) {
            return false;
        }

        /* ② 起動時の実行コードを置換  */
        if (buf != null) {
            buf[0x1e] = (byte) 0xd0;   // RET NC
        }

        return true;
    }

    /* -
     *  Helper / Stub methods – in a real implementation these would be
     *  provided by the base class or other library code.
     * - */

    /**
     *  Stub for the C++ method:
     *      bool CreateBiosParameterBlock(final char *code,
     *                                    final char *sig,
     *                                    byte *&buf);
     *  In Java we emulate the same behaviour by returning a byte[]
     *  that is passed back by reference.
     */
    private boolean createBiosParameterBlock(String code, String sig, byte[] buf) {
        /* ここでは実際の実装は省略している。 */
        return true;
    }

    /**
     *  Stub for ParseMSDOSParamOnDisk – returns a dummy value.
     */
    private double parseMSDOSParamOnDisk(Object disk, boolean isFormatting) {
        /* ここでは実際の実装は省略している。 */
        return 1.0;
    }

    /**
     *  Stub to get the DiskBasic instance – replace with real implementation.
     */
    private DiskBasic getBasic() {
        /* ここでは実際の実装は省略している。 */
        return null;
    }
}
