/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt.type;

import java.io.IOException;
import java.time.LocalDateTime;

import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasic.DiskBasicIdentifiedData;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.DiskBasicFat;
import l3diskex.basicfmt.diritem.DiskBasicDirItemMSDOS.DirectoryMsDos;
import l3diskex.diskimg.DiskImage.DiskImageSector;

import static l3diskex.basicfmt.BasicCommon.DiskBasicFormatType.FORMAT_TYPE_UNKNOWN;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_VOLUME_MASK;


/**
 Human68kの処理

 DiskBasicParam 固有のパラメータ
 <li>IPLString : IPL文字列</li>
 <li>IPLCompareString : OS判定時に使用する</li>
 <li>IgnoreParameter : セクタ１にあるパラメータを無視するか</li>
 <li>MediaID : メディアID</li>
 */
public class DiskBasicTypeHU68K extends DiskBasicTypeMSDOS {

    /** */
    public DiskBasicTypeHU68K(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryMsDos> dir) {
        super(basic, fat, dir);
    }

    /**
     * ディスクから各パラメータを取得＆必要なパラメータを計算
     *
     * @param isFormatting フォーマット中か
     * @return 1.0 正常, <1.0 警告あり, <0.0 エラーあり
     */
    @Override
    public double parseParamOnDisk(boolean isFormatting) throws IOException {
        if (isFormatting) return 0.0;

        double validRatio = 1.0;
        if (!basic.getVariousBoolParam("IgnoreParameter")) {
            validRatio = parseMSDOSParamOnDisk(basic.getDisk(), isFormatting);
        }
        if (validRatio >= 0.0) {
            // セクタ0
            DiskImageSector sector = basic.getSector(0, basic.getSideNumberBaseOnDisk(), 1);
            if (sector == null) {
                return -1.0;
            }

            // IPLに"X68IPL"が含まれるか
            // "Human"が含まれるか
            int found = -1;
            String istr = null;
            for (int i = 0; i < 3; i++) {
                found = -1;
                istr = switch (i) {
                    case 0 -> basic.getVariousStringParam("IPLString");
                    case 1 -> basic.getVariousStringParam("IPLCompareString");
                    case 2 -> "Human";
                    default -> istr;
                };
                if (istr != null && !istr.isEmpty()) {
                    found = sector.find(istr.getBytes(), istr.length());
                }
                if (found >= 0) {
                    validRatio = 1.0;
                    break;
                }
            }
            if (found < 0) {
                validRatio = 0.1;
            }
        }

        return validRatio;
    }

    /**
     * セクタデータを埋めた後の個別処理
     *
     * フォーマット IPLの書き込み
     * @param data 形式判定で取得したデータ
     * @return true/false
     */
    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) throws IOException {
        if (!createBiosParameterBlock("\u0060\u003c\u0090", "X68IPL30", null)) {
            return false;
        }

        // ボリュームラベルを設定
        int dirStart = basic.getReservedSectors() + basic.getNumberOfFats() * basic.getSectorsPerFat();
        DiskImageSector sector = basic.getSectorFromSectorPos(dirStart);
        DiskBasicDirItem<DirectoryMsDos> dItem = dir.newItem(sector, 0, sector.getSectorBuffer(), 0);

        dItem.setFileNameStr(data.getVolumeName());
        dItem.setFileAttr(FORMAT_TYPE_UNKNOWN, FILE_TYPE_VOLUME_MASK.getValue(), 0);
        LocalDateTime tm = LocalDateTime.now();
        dItem.setFileCreateDateTime(tm);

        return true;
    }
}
