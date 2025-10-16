/*
 * (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt;

import java.io.IOException;
import java.util.ResourceBundle;

import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicFmt.DiskBasic;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;

import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_HIDDEN_MASK;


public class DiskBasicDirItemMSX extends DiskBasicDirItemMSDOS {

    static final ResourceBundle rb = ResourceBundle.getBundle("messages");

    /** ctor with DiskBasic pointer */
    public DiskBasicDirItemMSX(DiskBasic basic) {
        super(basic);
    }

    /** ctor with sector information */
    public DiskBasicDirItemMSX(DiskBasic basic,
                               DiskImageSector sector,
                               int secPos,
                               byte[] data) {
        super(basic, sector, secPos, data);
    }

    /** ctor with all parameters */
    public DiskBasicDirItemMSX(DiskBasic basic,
                               int num,
                               DiskBasicGroupItem gitem,
                               DiskImageSector sector,
                               int secPos,
                               byte[] data,
                               SectorParam next,
                               boolean[] unuseRef) throws IOException {   // boolean by reference
        super(basic, num, gitem, sector, secPos, data, next, unuseRef);
    }

    /** 属性の文字列を返す(ファイル一覧画面表示用) */
    @Override
    public String getFileAttrStr() {
        String attr = null;

        // MSX-DOS
        if (getFileAttr().matchType(FILE_TYPE_HIDDEN_MASK.getValue(), FILE_TYPE_HIDDEN_MASK.getValue())) {
            attr = rb.getString(gTypeNameMS[TYPE_NAME_MS_HIDDEN]);
        }

        if (attr == null || attr.isEmpty()) {
            attr = "---";
        }
        return attr;
    }

    /** 日付のタイトル名（ダイアログ用） */
    @Override
    public String getFileCreateDateTimeTitle() {
        return "Created Date";
    }

    // Type names indices (placeholder)
    public static final int TYPE_NAME_MS_HIDDEN = 0;

    // TypeName array for MS (placeholder)
    public static final String[] gTypeNameMS = {
            "hidden"
    };

    // TypeName array for MS (int name) – placeholder
    public static final String[] gTypeNameMS_l = {
            "Hidden"
    };
}
