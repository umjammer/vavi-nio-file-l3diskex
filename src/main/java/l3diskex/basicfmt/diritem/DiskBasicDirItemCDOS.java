/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt.diritem;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ResourceBundle;

import l3diskex.Utils;
import l3diskex.basicfmt.BasicCommon.DirectoryT;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.KeyValArray;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.diritem.DiskBasicDirItemCDOS.DirectoryCdos;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;

import static l3diskex.Config.gConfig;
import static l3diskex.Parambase.MyAttributes.findValue;
import static l3diskex.Parambase.MyAttributes.getTypeByValue;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_ASCII_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BINARY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_DATA_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_MACHINE_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_READONLY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_SYSTEM_MASK;


/// ディレクトリ１アイテム C-DOS
public class DiskBasicDirItemCDOS extends DiskBasicDirItemMZBase<DirectoryCdos> {

    private static final ResourceBundle rb = ResourceBundle.getBundle("messages");

    /**
     * ディレクトリエントリ C-DOS (32bytes)
     */
    @Serdes(bigEndian = false)
    public static class DirectoryCdos implements DirectoryT {

        @Element(sequence = 1)
        public byte type;
        @Element(sequence = 2)
        public byte[] name = new byte[17]; // file name ends with $0D
        @Element(sequence = 3)
        public byte type2;
        @Element(sequence = 4)
        public byte byteOrder;
        @Element(sequence = 5)
        public short fileSize;
        @Element(sequence = 6)
        public short loadAddr;
        @Element(sequence = 7)
        public short execAddr;
        @Element(sequence = 8)
        public byte yy;
        @Element(sequence = 9)
        public byte mm;
        @Element(sequence = 10)
        public byte dd;
        @Element(sequence = 11)
        public byte reserved2;
        @Element(sequence = 12)
        public byte track;
        @Element(sequence = 13)
        public byte sector;

        public static final int SIZE = 32;
    }

    /* type name enum */
    public static final int TYPE_NAME_CDOS_UNKNOWN = 0;
    public static final int TYPE_NAME_CDOS_OBJ = 1;
    public static final int TYPE_NAME_CDOS_TEX = 2;
    public static final int TYPE_NAME_CDOS_CMD = 3;
    public static final int TYPE_NAME_CDOS_SYS = 4;
    public static final int TYPE_NAME_CDOS_END = 5;

    /** 属性名 */
    public static final Map<String, Object> gTypeNameCDOS = new LinkedHashMap<>() {{
        put("???", TYPE_NAME_CDOS_UNKNOWN);
        put("OBJECT", TYPE_NAME_CDOS_OBJ);
        put("TEXT", TYPE_NAME_CDOS_TEX);
        put("COMMAND", TYPE_NAME_CDOS_CMD);
        put("SYSTEM", TYPE_NAME_CDOS_SYS);
    }};

    /* type name 2 array */
    public static final String[] gTypeNameCDOS2 = {
            /*rb.getString(*/"Write Protected"/*)*/,
    };

    /* type name 2 enum constant */
    public static final int TYPE_NAME_CDOS2_READ_ONLY = 0;

    /// C-DOS 属性
    public static final int FILETYPE_CDOS_OBJ = 1;
    public static final int FILETYPE_CDOS_TEX = 2;
    public static final int FILETYPE_CDOS_CMD = 3;
    public static final int FILETYPE_CDOS_SYS = 4;

    /* data type mask constants */
    public static final int DATATYPE_CDOS_READ_ONLY = 0x01;

    //
    //
    //

    /* directory data */
    private DiskBasicDirData<DirectoryCdos> m_data = new DiskBasicDirData<>();

    public DiskBasicDirItemCDOS(DiskBasic basic) {
        super(basic);

        m_data.alloc(DirectoryCdos.class);
    }

    public DiskBasicDirItemCDOS(DiskBasic basic, DiskImageSector sector, int secpos, byte[] data, int dataP) throws IOException {
        super(basic, sector, secpos, data, dataP);

        m_data.attach(DirectoryCdos.class, data, dataP);
    }

    public DiskBasicDirItemCDOS(DiskBasic basic, int n_num, DiskBasicGroupItem gitem, DiskImageSector sector,
                                int secpos, byte[] data, int dataP, SectorParam next, boolean[] n_unuse) throws IOException {
        super(basic, n_num, gitem, sector, secpos, data, dataP, next, n_unuse);

        m_data.attach(DirectoryCdos.class, data, dataP);

        used(checkUsed(n_unuse[0]));
        if (getFileType1() == 0xfe) {
            // IPL部分は表示しない
            visible(false);
        }

        calcFileSize();
    }

    /**
     * アイテムへのポインタを設定
     *
     * @param n_num    通し番号
     * @param gitem    トラック番号などのデータ
     * @param sector   セクタ
     * @param n_secpos セクタ内のディレクトリエントリの位置
     * @param n_data   ディレクトリアイテム
     * @param n_next   [out] 次のセクタ
     */
    @Override
    public void setDataPtr(int n_num, DiskBasicGroupItem gitem, DiskImageSector sector,
                           int n_secpos, byte[] n_data, int dataP, SectorParam n_next) throws IOException {
        super.setDataPtr(n_num, gitem, sector, n_secpos, n_data, dataP, n_next);

        m_data.attach(DirectoryCdos.class, n_data, dataP);
    }

    /**
     * ディレクトリアイテムのチェック
     *
     * @param last [in,out] チェックを終了するか
     * @return チェックOK
     */
    @Override
    public boolean check(boolean[] last) {
        if (!m_data.isValid()) return false;

        boolean valid = true;
        int t = getFileType1();
        if (num != 0 && (t & 0x70) != 0 && findValue(basic.diskBasicParam.getSpecialAttributes(), t) == null) {
            valid = false;
        }
        return valid;
    }

    /** ファイル名を格納する位置を返す */
    @Override
    protected byte[] getFileNamePos(int num, int[] size, int[] len) {
        if (num == 0) {
            size[0] = m_data.data().name.length;
            len[0] = size[0] - 1;
            return m_data.data().name;
        } else {
            size[0] = len[0] = 0;
            return null;
        }
    }

    /** 属性１を返す */
    @Override
    protected int getFileType1() {
        return basic.invertUint8(m_data.data().type) & 0xff;
    }

    /** 属性２を返す */
    @Override
    public int getFileType2() {
        return basic.invertUint8(m_data.data().type2) & 0xff;
    }

    /** 属性３を返す */
    @Override
    protected int getFileType3() {
        return basic.invertUint8(m_data.data().byteOrder) & 0xff;
    }

    /** 属性１のセット */
    @Override
    protected void setFileType1(int val) {
        m_data.data().type = basic.invertUint8((byte) val);
    }

    /** 属性２のセット */
    @Override
    protected void setFileType2(int val) {
        m_data.data().type2 = basic.invertUint8((byte) val);
    }

    /** 属性３のセット */
    @Override
    protected void setFileType3(int val) {
        m_data.data().byteOrder = basic.invertUint8((byte) val);
    }

    /** 使用しているアイテムか */
    @Override
    public boolean checkUsed(boolean unuse) {
        return true;
    }

    /** 属性を変換 */
    private int convToNativeType(int file_type) {
        int val = 0;
        if ((file_type & FILE_TYPE_SYSTEM_MASK.getValue()) != 0) {
            val = FILETYPE_CDOS_SYS;
        } else if ((file_type & FILE_TYPE_MACHINE_MASK.getValue()) != 0) {
            val = FILETYPE_CDOS_CMD;
        } else if ((file_type & FILE_TYPE_BINARY_MASK.getValue()) != 0) {
            val = FILETYPE_CDOS_OBJ;
        } else if ((file_type & FILE_TYPE_ASCII_MASK.getValue()) != 0) {
            val = FILETYPE_CDOS_TEX;
        }
        return val;
    }

    /** 属性からリストの位置を返す(プロパティダイアログ用) */
    private int convFileType1Pos(int native_type) {
        int pos = TYPE_NAME_CDOS_UNKNOWN;
        switch (native_type) {
            case FILETYPE_CDOS_OBJ:
                pos = TYPE_NAME_CDOS_OBJ;
                break;
            case FILETYPE_CDOS_TEX:
                pos = TYPE_NAME_CDOS_TEX;
                break;
            case FILETYPE_CDOS_CMD:
                pos = TYPE_NAME_CDOS_CMD;
                break;
            case FILETYPE_CDOS_SYS:
                pos = TYPE_NAME_CDOS_SYS;
                break;
            default:
                pos = native_type;
                break;
        }
        return pos;
    }

    /** 属性からリストの位置を返す(プロパティダイアログ用) */
    private int convFileType2Pos(int native_type) {
        int val = 0;
        if ((native_type & DATATYPE_CDOS_READ_ONLY) != 0) {
            // write protect
            val |= FILE_TYPE_READONLY_MASK.getValue();
        }
        return val;
    }

    /** リストの位置から属性を返す(プロパティダイアログ用) */
    private int calcFileTypeFromPos(int pos) {
        int val = 0;
        switch (pos) {
            case TYPE_NAME_CDOS_OBJ:
                val = FILETYPE_CDOS_OBJ;
                break;
            case TYPE_NAME_CDOS_TEX:
                val = FILETYPE_CDOS_TEX;
                break;
            case TYPE_NAME_CDOS_CMD:
                val = FILETYPE_CDOS_CMD;
                break;
            case TYPE_NAME_CDOS_SYS:
                val = FILETYPE_CDOS_SYS;
                break;
            default:
                val = DiskBasicDirItem.calcSpecialOriginalTypeFromPos(basic, pos, TYPE_NAME_CDOS_END);
                break;
        }
        return val;
    }

    /** データ内にファイルサイズをセット */
    @Override
    public void setFileSizeBase(int val) {
        m_data.data().fileSize = basic.invertUint16((short) val);
    }

    /** データ内のファイルサイズを返す */
    @Override
    public int getFileSizeBase() {
        return basic.invertUint16(m_data.data().fileSize) & 0xffff;
    }

    /// 削除
    /// @return true:OK
    @Override
    public boolean delete() {
        // 削除はエントリの先頭にコードを入れるだけ
        m_data.fill(basic.invertUint8(basic.diskBasicParam.getDeleteCode()), 1);
        used(false);
        return true;
    }

    @Override
    public void setFileAttr(DiskBasicFileType file_type) {
        int ftype = file_type.getType();
        if (ftype == -1) return;

        int t1 = 0;
        int t2 = 0;
        if (file_type.getFormat() == basic.getFormatTypeNumber()) {
            // 同じフォーマット
            t1 = file_type.getOrigin() & 0xff;
            t2 = (file_type.getOrigin() >> 8) & 0xff;
        } else {
            // 異なるフォーマット
            t1 = convToNativeType(ftype);
            if ((ftype & FILE_TYPE_READONLY_MASK.getValue()) != 0) {
                t2 |= DATATYPE_CDOS_READ_ONLY;
            }
        }
        setFileType1(t1);
        setFileType2(t2);
        setFileType3(basic.isBigEndian() ? 1 : 0);
    }

    @Override
    public DiskBasicFileType getFileAttr() {
        int t1 = getFileType1();
        int val = 0;
        switch (t1) {
            case FILETYPE_CDOS_OBJ:
                val = FILE_TYPE_DATA_MASK.getValue();       // DATA
                val |= FILE_TYPE_BINARY_MASK.getValue();    // binary
                break;
            case FILETYPE_CDOS_TEX:
                val = FILE_TYPE_DATA_MASK.getValue();       // DATA
                val |= FILE_TYPE_ASCII_MASK.getValue();     // ascii
                break;
            case FILETYPE_CDOS_CMD:
                val = FILE_TYPE_MACHINE_MASK.getValue();    // machine
                val |= FILE_TYPE_BINARY_MASK.getValue();    // binary
                break;
            case FILETYPE_CDOS_SYS:
                val = FILE_TYPE_SYSTEM_MASK.getValue();

                break;
            default:
                val = getTypeByValue(basic.diskBasicParam.getSpecialAttributes(), t1);
                break;
        }
        int t2 = getFileType2();
        if ((t2 & DATATYPE_CDOS_READ_ONLY) != 0) {
            // write protect
            val |= FILE_TYPE_READONLY_MASK.getValue();
        }

        return new DiskBasicFileType(basic.getFormatTypeNumber(), val, t2 << 8 | t1);
    }

    @Override
    public String getFileAttrStr() {
        String[] attr = new String[1];
        getFileAttrName(convFileType1Pos(getFileType1()), gTypeNameCDOS, attr, TYPE_NAME_CDOS_UNKNOWN);

        int t2 = getFileType2();
        if ((t2 & DATATYPE_CDOS_READ_ONLY) != 0) {
            // write protect
            attr[0] += ", ";
            attr[0] += rb.getString(gTypeNameCDOS2[TYPE_NAME_CDOS2_READ_ONLY]);
        }

        return attr[0];
    }

    @Override
    public void setStartGroup(int fileunit_num, int val, int size) {
        int track = (val >> 8) & 0xff;
        int sector = val & 0xff;
        m_data.data().track = basic.invertUint8((byte) track);
        m_data.data().sector = basic.invertUint8((byte) sector);
    }

    @Override
    public int getStartGroup(int fileunit_num) {
        int track = basic.invertUint8(m_data.data().track);
        int sector = basic.invertUint8(m_data.data().sector);
        return ((track << 8) | sector);
    }

    @Override
    public boolean hasCreateDateTime() {
        return true;
    }

    @Override
    public boolean hasCreateDate() {
        return true;
    }

    @Override
    public int canIgnoreDateTime() {
        return DiskBasicDirItem.DATETIME_ALL;
    }

    @Override
    public LocalDate getFileCreateDate(LocalDateTime tm) {
        int yy = basic.invertUint8(m_data.data().yy);
        int mm = basic.invertUint8(m_data.data().mm);
        int dd = basic.invertUint8(m_data.data().dd);
        return LocalDate.of(yy, mm, dd);
    }

    @Override
    public String getFileCreateDateStr() {
        LocalDateTime tm = LocalDateTime.now();
        LocalDate ld = getFileCreateDate(tm);
        return Utils.formatYMDStr(ld);
    }

    @Override
    public void setFileCreateDate(LocalDateTime tm) {
        m_data.data().yy = basic.invertUint8((byte) tm.getYear());
        m_data.data().mm = basic.invertUint8((byte) tm.getMonth().ordinal());
        m_data.data().dd = basic.invertUint8((byte) tm.getDayOfMonth());
    }

    @Override
    public boolean hasAddress() {
        return true;
    }

    @Override
    public int getStartAddress() {
        return basic.invertUint16(m_data.data().loadAddr) & 0xffff;
    }

    @Override
    public int getExecuteAddress() {
        return basic.invertUint16(m_data.data().execAddr) & 0xffff;
    }

    @Override
    public void setStartAddress(int val) {
        m_data.data().loadAddr = basic.invertUint16((short) val);
    }

    @Override
    public void setExecuteAddress(int val) {
        m_data.data().execAddr = basic.invertUint16((short) val);
    }

    @Override
    public int getDataSize() {
        return m_data.getDataSize();
    }

    @Override
    public DirectoryCdos getData() {
        return m_data.data();
    }

    @Override
    public boolean copyData(byte[] val) {
        m_data.copy(val);
        return true;
    }

    @Override
    public void clearData() {
        if (!m_data.isValid()) return;
        m_data.fill(0);
        Arrays.fill(m_data.data().name, 0, m_data.data().name.length, (byte) 0x0d);
        basic.invertMem(m_data.getRawData(), getDataSize()); // invert
    }

    @Override
    public boolean preExportDataFile(String[] filename) {
        if (!gConfig.isAddExtensionExport()) return true;

        String[] ext = new String[1];
        if (getFileAttrName(convFileType1Pos(getFileType1()), gTypeNameCDOS, ext)) {
            filename[0] += ".";
            if (Utils.isUpperString(filename[0])) {
                filename[0] += ext[0].toUpperCase();
            } else {
                filename[0] += ext[0].toLowerCase();
            }
        }
        return true;
    }

    @Override
    public boolean preImportDataFile(String[] filename) {
        if (gConfig.isDecideAttrImport()) {
            isContainAttrByExtension(filename[0], gTypeNameCDOS, TYPE_NAME_CDOS_OBJ, TYPE_NAME_CDOS_SYS, filename, null, null);
        }
        filename[0] = remakeFileNameAndExtStr(filename[0]);
        return true;
    }

    @Override
    public int convOriginalTypeFromFileName(String filename) {
        // 拡張子で属性を設定する
        int[] t1 = {0};
        if (!isContainAttrByExtension(filename, gTypeNameCDOS, TYPE_NAME_CDOS_OBJ, TYPE_NAME_CDOS_SYS, null, t1, null)) {
            t1[0] = TYPE_NAME_CDOS_TEX;
        }
        return t1[0];
    }

    @Override
    public void setInternalDataInAttrDialog(KeyValArray vals) {
        vals.add("inverted", basic.isDataInverted());
        vals.add("TYPE", m_data.data().type, basic.isDataInverted());
        vals.add("NAME", m_data.data().name, m_data.data().name.length, basic.isDataInverted());
        vals.add("TYPE2", m_data.data().type2, basic.isDataInverted());
        vals.add("BYTE_ORDER", m_data.data().byteOrder, basic.isDataInverted());
        vals.add("FILE_SIZE", m_data.data().fileSize, basic.isBigEndian(), basic.isDataInverted());
        vals.add("LOAD_ADDR", m_data.data().loadAddr, basic.isBigEndian(), basic.isDataInverted());
        vals.add("EXEC_ADDR", m_data.data().execAddr, basic.isBigEndian(), basic.isDataInverted());
        vals.add("YEAR", m_data.data().yy, basic.isDataInverted());
        vals.add("MONTH", m_data.data().mm, basic.isDataInverted());
        vals.add("DAY", m_data.data().dd, basic.isDataInverted());
    }
}
