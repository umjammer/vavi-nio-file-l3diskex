/*
 * (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt.diritem;

import java.io.IOException;
import java.util.ResourceBundle;

import l3diskex.Utils;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;

import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_HIDDEN_MASK;


/** ディレクトリ１アイテム MSX-DOS */
public class DiskBasicDirItemMSX extends DiskBasicDirItemMSDOS {

    static final ResourceBundle rb = ResourceBundle.getBundle("messages");

    /** */
    public DiskBasicDirItemMSX(DiskBasic basic) {
        super(basic);
    }

    /** */
    public DiskBasicDirItemMSX(DiskBasic basic,
                               DiskImageSector sector,
                               int secPos,
                               byte[] data, int dataP) {
        super(basic, sector, secPos, data, dataP);
    }

    /** */
    public DiskBasicDirItemMSX(DiskBasic basic,
                               int num,
                               DiskBasicGroupItem gitem,
                               DiskImageSector sector,
                               int secPos,
                               byte[] data, int dataP,
                               SectorParam next,
                               boolean[] unuse) throws IOException {
        super(basic, num, gitem, sector, secPos, data, dataP, next, unuse);
    }

    /** 属性の文字列を返す(ファイル一覧画面表示用) */
    @Override
    public String getFileAttrStr() {
        String attr = null;
        // MSX-DOS
        if (getFileAttr().matchType(FILE_TYPE_HIDDEN_MASK.getValue(), FILE_TYPE_HIDDEN_MASK.getValue())) {
            attr = rb.getString(Utils.keyAt(typeNameMS, TYPE_NAME_MS_HIDDEN));
        }

        if (attr == null || attr.isEmpty()) {
            attr = "---";
        }
        return attr;
    }

    //
    // ダイアログ用
    //

    /** 日付のタイトル名（ダイアログ用） */
    @Override
    public String getFileCreateDateTimeTitle() {
        return "Created Date";
    }
}
