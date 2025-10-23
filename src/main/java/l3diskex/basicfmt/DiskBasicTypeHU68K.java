/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt;

import java.io.IOException;
import java.time.LocalDateTime;

import l3diskex.basicfmt.BasicFat.DiskBasicFat;
import l3diskex.basicfmt.BasicFmt.DiskBasic;
import l3diskex.basicfmt.BasicFmt.DiskBasicIdentifiedData;
import l3diskex.diskimg.DiskImage.DiskImageSector;

import static l3diskex.basicfmt.BasicCommon.DiskBasicFormatType.FORMAT_TYPE_UNKNOWN;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_VOLUME_MASK;


/* -- */
/*  Human68k specific disk type                                           */
/* -- */
public class DiskBasicTypeHU68K extends DiskBasicTypeMSDOS {

    /** Public constructor */
    public DiskBasicTypeHU68K(DiskBasic basic, DiskBasicFat fat, DiskBasicDir dir) {
        super(basic, fat, dir);
    }

    /* ----------------------------------------------------------------- */
    /*  Check / assign FAT area                                         */
    /* ----------------------------------------------------------------- */

    /**
     * ディスクから各パラメータを取得＆必要なパラメータを計算
     *
     * @param isFormatting フォーマット中か
     * @return 1.0 正常, <1.0 警告あり, <0.0 エラーあり
     */
    @Override
    public double parseParamOnDisk(boolean isFormatting) throws IOException {
        if (isFormatting) {
            return 0.0;
        }

        double validRatio = 1.0;
        if (!basic.diskBasicParam.getVariousBoolParam("IgnoreParameter")) {
            validRatio = ParseMSDOSParamOnDisk(basic.getDisk(), isFormatting);
        }

        if (validRatio >= 0.0) {
            /* セクタ0の取得 */
            DiskImageSector sector = basic.getSector(0, basic.getSideNumberBaseOnDisk(), 1);
            if (sector == null) {
                return -1.0;
            }

            /* IPLに"X68IPL"が含まれるか、"Human"が含まれるか */
            int found = -1;
            String istr = null;
            for (int i = 0; i < 3; i++) {
                found = -1;
                switch (i) {
                    case 0:
                        istr = basic.diskBasicParam.getVariousStringParam("IPLString");
                        break;
                    case 1:
                        istr = basic.diskBasicParam.getVariousStringParam("IPLCompareString");
                        break;
                    case 2:
                        istr = "Human";
                        break;
                }
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

    /* ----------------------------------------------------------------- */
    /*  Format                                                         */
    /* ----------------------------------------------------------------- */

    /**
     * セクタデータを埋めた後の個別処理
     *
     * @param data 形式判定で取得したデータ
     * @return true/false
     */
    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) throws IOException {
        /* フォーマット IPLの書き込み */
        if (!CreateBiosParameterBlock("\u0060\u003c\u0090", "X68IPL30", null)) {
            return false;
        }

        /* ボリュームラベルを設定 */
        int dirStart = basic.diskBasicParam.getReservedSectors()
                + basic.diskBasicParam.getNumberOfFats() * basic.diskBasicParam.getSectorsPerFat();
        DiskImageSector sec = basic.getSectorFromSectorPos(dirStart);
        DiskBasicDirItem ditem = dir.newItem(sec, 0, sec.getSectorBuffer());

        ditem.setFileNameStr(data.getVolumeName());
        ditem.setFileAttr(FORMAT_TYPE_UNKNOWN, FILE_TYPE_VOLUME_MASK.getValue(), 0);
        LocalDateTime tm = LocalDateTime.now();
        ditem.setFileCreateDateTime(tm);

        /* 参照解放（Javaでは不要） */
        // delete ditem;   // Java では GC が担当

        return true;
    }
}
