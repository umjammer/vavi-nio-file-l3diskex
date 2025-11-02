///
/// Copyright (c) Sasaji. All rights reserved.
///

package l3diskex.basicfmt.diritem;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

import l3diskex.Utils;
import l3diskex.basicfmt.BasicCommon.DirectoryT;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.KeyValArray;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.diritem.DiskBasicDirItemTFDOS.DirectoryTfdos;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;

import static l3diskex.Config.gConfig;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_ASCII_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BASIC_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BINARY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_DATA_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_HIDDEN_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_MACHINE_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_READONLY_MASK;


/**
 ディレクトリ１アイテム TF-DOS

 {@link #externalAttr} 1: BASE互換, 2: BASE互換かを自動判定
 */
public class DiskBasicDirItemTFDOS extends DiskBasicDirItemMZBase<DirectoryTfdos> {

    private static final ResourceBundle rb = ResourceBundle.getBundle("messages");

    /**
     * ディレクトリエントリ TF-DOS (16bytes)
     */
    @Serdes(bigEndian = false)
    public static class DirectoryTfdos implements DirectoryT {

        @Element(sequence = 1)
        public byte type; // byte
        @Element(sequence = 2)
        public byte[] name = new byte[8]; // file name ends with $0D and fills rest with $20
        @Element(sequence = 3)
        public short fileSize;
        @Element(sequence = 4)
        public short loadAddr;
        @Element(sequence = 5)
        public short execAddr;
        @Element(sequence = 6)
        public byte track; // byte

        public static final int SIZE = 16;
    }

    /// TF-DOS属性名
    public static final Map<String, Object> gTypeNameTFDOS = new HashMap<>() {{
        put("???", 0);
        put("OBJ", FILETYPE_TFDOS_OBJ);
        put("TEX", FILETYPE_TFDOS_TEX);
        put("CMD", FILETYPE_TFDOS_CMD);
        put("SYS", FILETYPE_TFDOS_SYS);
        put("DAT", FILETYPE_TFDOS_DAT);
        put("GRA", FILETYPE_TFDOS_GRA);
        put("DBB", FILETYPE_TFDOS_DBB);
        put("Write Protected", 0);
        put("Hidden", 0);
    }};

    /// TF-DOS 属性位置
    public static final int TYPE_NAME_TFDOS_UNKNOWN = 0;
    private static final int TYPE_NAME_TFDOS_OBJ = 1;
    public static final int TYPE_NAME_TFDOS_TEX = 2;
    private static final int TYPE_NAME_TFDOS_CMD = 3;
    private static final int TYPE_NAME_TFDOS_SYS = 4;
    private static final int TYPE_NAME_TFDOS_DAT = 5;
    private static final int TYPE_NAME_TFDOS_GRA = 6;
    public static final int TYPE_NAME_TFDOS_DBB = 7;
    public static final int TYPE_NAME_TFDOS_READ_ONLY = 8;
    private static final int TYPE_NAME_TFDOS_HIDDEN = 9;

    /// TF-DOS 属性値
    private static final int FILETYPE_TFDOS_OBJ = 0x01;
    private static final int FILETYPE_TFDOS_TEX = 0x02;
    private static final int FILETYPE_TFDOS_CMD = 0x03;
    private static final int FILETYPE_TFDOS_SYS = 0x04;
    private static final int FILETYPE_TFDOS_DAT = 0x05;
    private static final int FILETYPE_TFDOS_GRA = 0x06;
    private static final int FILETYPE_TFDOS_DBB = 0x07;

    public static final int DATATYPE_TFDOS_HIDDEN = 0x40;
    public static final int DATATYPE_TFDOS_READ_ONLY = 0x80;

    /** ディレクトリデータ */
    private final DiskBasicDirData<DirectoryTfdos> m_data = new DiskBasicDirData<>();

    public DiskBasicDirItemTFDOS(DiskBasic basic) {
        super(basic);

        m_data.alloc(DirectoryTfdos.class);
        externalAttr = 2;	// TXTの時、BASE互換かを自動判定
    }

    public DiskBasicDirItemTFDOS(DiskBasic basic, DiskImageSector n_sector, int n_secpos, byte[] n_data, int dataP) {
        super(basic, n_sector, n_secpos, n_data, dataP);

        m_data.attach(DirectoryTfdos.class, n_data, dataP);
        externalAttr = 2;	// TXTの時、BASE互換かを自動判定
    }

    public DiskBasicDirItemTFDOS(DiskBasic basic, int n_num, DiskBasicGroupItem n_gitem,
                                 DiskImageSector n_sector, int n_secpos, byte[] n_data, int dataP,
                                 SectorParam n_next, boolean[] n_unuse) throws IOException {
        super(basic, n_num, n_gitem, n_sector, n_secpos, n_data, dataP, n_next, n_unuse);

        m_data.attach(DirectoryTfdos.class, n_data, dataP);
        externalAttr = 2;	// TXTの時、BASE互換かを自動判定

        used(checkUsed(n_unuse[0]));

        calcFileSize();
    }

    @Override
    public void setDataPtr(int n_num, DiskBasicGroupItem n_gitem, DiskImageSector n_sector,
                           int n_secpos, byte[] n_data, int dataP, SectorParam n_next) throws IOException {
        super.setDataPtr(n_num, n_gitem, n_sector, n_secpos, n_data, dataP, n_next);

        m_data.attach(DirectoryTfdos.class, n_data, dataP);
    }

    @Override
    protected byte[] getFileNamePos(int num, int[] size, int[] len) {
        if (num == 0) {
            size[0] = len[0] = m_data.data().name.length;
            return m_data.data().name;
        } else {
            size[0] = len[0] = 0;
            return null;
        }
    }

    @Override
    public int getFileType1() {
        return basic.invertUint8(m_data.data().type); // invert
    }

    @Override
    protected void setFileType1(int val) {
        m_data.data().type = basic.invertUint8((byte) val); // invert
    }

    @Override
    public boolean checkUsed(boolean unuse) {
        return getFileType1() != 0;
    }

    @Override
    public boolean check(boolean[] last) {
        if (!m_data.isValid()) return false;

        boolean valid = true;
        int t = getFileType1();
        if ((t & 0x3f) >= 16) {
            valid = false;
        }
        return valid;
    }

    @Override
    public boolean delete() {
        m_data.fill(basic.invertUint8(basic.diskBasicParam.getDeleteCode()), 1);
        used(false);
        return true;
    }

    @Override
    public void setFileAttr(DiskBasicFileType file_type) {
        int ftype = file_type.getType();
        if (ftype == -1) return;

        int t1 = file_type.getOrigin();
        int val = 0;
        if (file_type.getFormat() == basic.getFormatTypeNumber()) {
            // 同じOSの場合は元の属性をそのままセット
            val = t1;
        } else {
            // 別OSからの場合、近い属性をセット
            if ((ftype & FILE_TYPE_BINARY_MASK.getValue()) != 0) {
                if ((ftype & FILE_TYPE_BASIC_MASK.getValue()) != 0) val = FILETYPE_TFDOS_CMD;
                else if ((ftype & FILE_TYPE_MACHINE_MASK.getValue()) != 0) val = FILETYPE_TFDOS_SYS;
                else val = FILETYPE_TFDOS_OBJ;
            } else if ((ftype & FILE_TYPE_DATA_MASK.getValue()) != 0) {
                val = FILETYPE_TFDOS_DAT;
            } else if ((ftype & FILE_TYPE_ASCII_MASK.getValue()) != 0) {
                val = FILETYPE_TFDOS_TEX;
            }

            if ((ftype & FILE_TYPE_READONLY_MASK.getValue()) != 0) {
                val |= DATATYPE_TFDOS_READ_ONLY;
            }
            if ((ftype & FILE_TYPE_HIDDEN_MASK.getValue()) != 0) {
                val |= DATATYPE_TFDOS_HIDDEN;
            }
        }
        externalAttr = (val >> 16);
        setFileType1(val);
    }

    @Override
    public DiskBasicFileType getFileAttr() {
        int t1 = getFileType1();
        int val = 0;
        switch (t1 & 0x3f) {
            case FILETYPE_TFDOS_OBJ:
                val = FILE_TYPE_BINARY_MASK.getValue();     // binary
                break;
            case FILETYPE_TFDOS_TEX:
                val = FILE_TYPE_ASCII_MASK.getValue();      // ascii
                break;
            case FILETYPE_TFDOS_CMD:
                val = FILE_TYPE_BASIC_MASK.getValue();      // BASIC
                val |= FILE_TYPE_BINARY_MASK.getValue();    // binary
                break;
            case FILETYPE_TFDOS_SYS:
                val = FILE_TYPE_MACHINE_MASK.getValue();    // machine
                val |= FILE_TYPE_BINARY_MASK.getValue();
                break;
            case FILETYPE_TFDOS_DAT:
                val = FILE_TYPE_DATA_MASK.getValue();       // data
                break;
        }
        if ((t1 & DATATYPE_TFDOS_READ_ONLY) != 0) {
            val |= FILE_TYPE_READONLY_MASK.getValue();
        }
        if ((t1 & DATATYPE_TFDOS_HIDDEN) != 0) {
            val |= FILE_TYPE_HIDDEN_MASK.getValue();
        }
        return new DiskBasicFileType(basic.getFormatTypeNumber(), val, t1);
    }

    @Override
    public String getFileAttrStr() {
        int t1 = getFileType1();
        String attr = rb.getString(Utils.keyAt(gTypeNameTFDOS, convFileType1Pos(t1)));

        if ((t1 & DATATYPE_TFDOS_READ_ONLY) != 0) {
            attr += ", ";
            attr += rb.getString(Utils.keyAt(gTypeNameTFDOS, TYPE_NAME_TFDOS_READ_ONLY));
        }
        if ((t1 & DATATYPE_TFDOS_HIDDEN) != 0) {
            attr += ", ";
            attr += rb.getString(Utils.keyAt(gTypeNameTFDOS, TYPE_NAME_TFDOS_HIDDEN));
        }
        return attr;
    }

    @Override
    protected void setFileSizeBase(int val) {
        m_data.data().fileSize = basic.invertAndOrderUint16((short) val); // invert
    }

    @Override
    protected int getFileSizeBase() {
        return basic.invertAndOrderUint16(m_data.data().fileSize); // invert
    }

    @Override
    public int getStartAddress() {
        return basic.invertAndOrderUint16(m_data.data().loadAddr); // invert
    }

    @Override
    public int getExecuteAddress() {
        return basic.invertAndOrderUint16(m_data.data().execAddr); // invert
    }

    @Override
    public void setStartAddress(int val) {
        m_data.data().loadAddr = basic.invertAndOrderUint16((short) val); // invert
    }

    @Override
    public void setExecuteAddress(int val) {
        m_data.data().execAddr = basic.invertAndOrderUint16((short) val); // invert
    }

    @Override
    public int getDataSize() {
        return m_data.getDataSize();
    }

    @Override
    public DirectoryTfdos getData() {
        return m_data.data();
    }

    @Override
    public boolean copyData(byte[] val) {
        return m_data.copy(val, getDataSize());
    }

    @Override
    public void clearData() {
        m_data.fill((byte) 0, getDataSize());
        Arrays.fill(m_data.data().name, (byte) 0x0d);
        basic.invertMem(m_data.getRawData(), getDataSize()); // invert
    }

    @Override
    public void setStartGroup(int fileunit_num, int val, int size) {
        m_data.data().track = basic.invertUint8((byte) (val & 0xff)); // invert
    }

    @Override
    public int getStartGroup(int fileunit_num) {
        return basic.invertUint8(m_data.data().track); // invert
    }

    @Override
    public boolean preExportDataFile(String[] filename) {
        if (!gConfig.isAddExtensionExport()) return true;

        String[] ext = new String[1];
        if (getFileAttrName(convFileType1Pos(getFileType1()), gTypeNameTFDOS, ext)) {
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
            isContainAttrByExtension(filename[0], gTypeNameTFDOS, TYPE_NAME_TFDOS_OBJ, TYPE_NAME_TFDOS_DBB, filename, null, null);
        }
        filename[0] = remakeFileNameAndExtStr(filename[0]);
        return true;
    }

    @Override
    public int convOriginalTypeFromFileName(String filename) {
        int[] t1 = {0};
        if (!isContainAttrByExtension(filename, gTypeNameTFDOS, TYPE_NAME_TFDOS_OBJ, TYPE_NAME_TFDOS_DBB, null, t1, null)) {
            t1[0] = FILETYPE_TFDOS_TEX;
        }
        return t1[0];
    }

    /** 属性からリストの位置を返す(プロパティダイアログ用) */
    public int convFileType1Pos(int t1) {
        int val = 0;
        t1 = (t1 & 0x3f);
        if (FILETYPE_TFDOS_OBJ <= t1 && t1 <= FILETYPE_TFDOS_DBB) {
            val = t1;
        }
        return val;
    }

    /** 属性からリストの位置を返す(プロパティダイアログ用) */
    public int convFileType2Pos(int t1) {
        int val = 0;
        if ((t1 & DATATYPE_TFDOS_READ_ONLY) != 0) {
            val |= FILE_TYPE_READONLY_MASK.getValue();
        }
        if ((t1 & DATATYPE_TFDOS_HIDDEN) != 0) {
            val |= FILE_TYPE_HIDDEN_MASK.getValue();
        }
        return val;
    }

    @Override
    public void setInternalDataInAttrDialog(KeyValArray vals) {
        vals.add("inverted", basic.isDataInverted());

        vals.add("TYPE", m_data.data().type, basic.isDataInverted());
        vals.add("NAME", m_data.data().name, m_data.data().name.length, basic.isDataInverted());
        vals.add("FILE_SIZE", m_data.data().fileSize, basic.isBigEndian(), basic.isDataInverted());
        vals.add("LOAD_ADDR", m_data.data().loadAddr, basic.isBigEndian(), basic.isDataInverted());
        vals.add("EXEC_ADDR", m_data.data().execAddr, basic.isBigEndian(), basic.isDataInverted());
        vals.add("TRACK", m_data.data().track, basic.isDataInverted());
    }
}
