/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt.type;

import java.io.IOException;

import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasic.DiskBasicIdentifiedData;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicFat;
import l3diskex.basicfmt.diritem.DiskBasicDirItemMSDOS.DirectoryMsDos;
import l3diskex.diskimg.DiskImage.DiskImageSector;


/**
 * MSX BASIC / MSX-DOSの処理
 * <p>
 * DiskBasicParam
 *
 * <li>MediaID : メディアID</li>
 */
public class DiskBasicTypeMSX extends DiskBasicTypeMSDOS {

    private static final String[] EXCLUDE_KEYWORDS = {
            "IO      SYS",
            "IBMDOS",
            "MSDOS",
            "IBM",
    };

    public DiskBasicTypeMSX(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryMsDos> dir) {
        super(basic, fat, dir);
    }

    /**
     * ディスクから各パラメータを取得＆必要なパラメータを計算
     *
     * @param isFormatting フォーマット中か
     * @return 1.0: 正常, 0.0 - 1.0: 警告あり, <0.0: エラーあり
     */
    @Override
    public double parseParamOnDisk(boolean isFormatting) throws IOException {
        if (isFormatting) return 0.0;

        double validRatio = parseMSDOSParamOnDisk(basic.getDisk(), isFormatting);
        if (validRatio >= 0.0) {
            DiskImageSector sector = basic.getSector(0, 0, 1);
            if (sector == null) return -1.0;
            byte[] data = sector.getSectorBuffer();
            if (data == null) return -1.0;
            // MSXDOS という文字列があれば確実
            if (sector.find("MSXDOS".getBytes(), 6) >= 0) {
                validRatio += 0.8;
            } else if (sector.find("MSX".getBytes(), 3) >= 0) {
                validRatio += 0.4;
            }
            // 除外するキーワード
            for (String excludeKeyword : EXCLUDE_KEYWORDS) {
                if (sector.find(excludeKeyword.getBytes(), excludeKeyword.length()) >= 0) {
                    validRatio -= 0.5;
                    break;
                }
            }
        }
        if (validRatio > 1.0) validRatio = 1.0;
        else if (validRatio < -1.0) validRatio = -1.0;

        return validRatio;
    }

    /**
     * サブディレクトリを作成できるか
     */
    @Override
    public boolean canMakeDirectory() {
        return false;
    }

    /**
     * セクタデータを埋めた後の個別処理
     * フォーマット IPL の書き込み
     */
    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) throws IOException {
        byte[][] buf = new byte[1][];
        if (!createBiosParameterBlock("\u00eb\u00fe\u0090", "MSX", buf)) {
            return false;
        }

        // 起動時の実行コード
        if (buf[0] != null) {
            buf[0][0x1e] = (byte) 0xd0;   // RET NC
        }

        return true;
    }
}
