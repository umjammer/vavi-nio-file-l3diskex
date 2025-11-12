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
import l3diskex.basicfmt.BasicCommon.Directory;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.KeyValArray;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.diritem.DiskBasicDirItemTFDOS.DirectoryTfDos;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;

import static l3diskex.Config.config;
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
public class DiskBasicDirItemTFDOS extends DiskBasicDirItemMZBase<DirectoryTfDos> {

    private static final ResourceBundle rb = ResourceBundle.getBundle("messages");

    /**
     * ディレクトリエントリ TF-DOS (16bytes)
     */
    @Serdes(bigEndian = false)
    public static class DirectoryTfDos implements Directory {

        @Element(sequence = 1)
        public byte type; // byte
        @Element(sequence = 2)
        public byte[] name = new byte[8]; // file name ends with $0D and fills rest with $20
        @Element(sequence = 3)
        public short fileSize;
        @Element(sequence = 4)
        public short loadAddress;
        @Element(sequence = 5)
        public short execAddress;
        @Element(sequence = 6)
        public byte track; // byte

        public static final int SIZE = 16;
    }

    /// TF-DOS属性名
    public static final Map<String, Object> typeNameTfDos = new HashMap<>() {{
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
    private final DiskBasicDirData<DirectoryTfDos> data = new DiskBasicDirData<>();

    public DiskBasicDirItemTFDOS(DiskBasic basic) {
        super(basic);

        data.alloc(DirectoryTfDos.class);
        externalAttr = 2;	// TXTの時、BASE互換かを自動判定
    }

    public DiskBasicDirItemTFDOS(DiskBasic basic, DiskImageSector sector, int secPos, byte[] data, int dataP) {
        super(basic, sector, secPos, data, dataP);

        this.data.attach(DirectoryTfDos.class, data, dataP);
        externalAttr = 2;	// TXTの時、BASE互換かを自動判定
    }

    public DiskBasicDirItemTFDOS(DiskBasic basic, int num, DiskBasicGroupItem groupItem,
                                 DiskImageSector sector, int secPos, byte[] data, int dataP,
                                 SectorParam next, boolean[] unuse) throws IOException {
        super(basic, num, groupItem, sector, secPos, data, dataP, next, unuse);

        this.data.attach(DirectoryTfDos.class, data, dataP);
        externalAttr = 2;	// TXTの時、BASE互換かを自動判定

        used(checkUsed(unuse[0]));

        calcFileSize();
    }

    @Override
    public void setData(int num, DiskBasicGroupItem groupItem, DiskImageSector sector,
                        int sectorPos, byte[] data, int dataPos, SectorParam next) throws IOException {
        super.setData(num, groupItem, sector, sectorPos, data, dataPos, next);

        this.data.attach(DirectoryTfDos.class, data, dataPos);
    }

    @Override
    protected byte[] getFileNamePos(int num, int[] size, int[] len) {
        if (num == 0) {
            size[0] = len[0] = data.data().name.length;
            return data.data().name;
        } else {
            size[0] = len[0] = 0;
            return null;
        }
    }

    @Override
    public int getFileType1() {
        return basic.invertUint8(data.data().type); // invert
    }

    @Override
    protected void setFileType1(int val) {
        data.data().type = basic.invertUint8((byte) val); // invert
    }

    @Override
    public boolean checkUsed(boolean unuse) {
        return getFileType1() != 0;
    }

    @Override
    public boolean check(boolean[] last) {
        if (!data.isValid()) return false;

        boolean valid = true;
        int t = getFileType1();
        if ((t & 0x3f) >= 16) {
            valid = false;
        }
        return valid;
    }

    @Override
    public boolean delete() {
        data.fill(basic.invertUint8(basic.getDeleteCode()), 1);
        used(false);
        return true;
    }

    @Override
    public void setFileAttr(DiskBasicFileType fileType) {
        int fType = fileType.getType();
        if (fType == -1) return;

        int t1 = fileType.getOrigin();
        int val = 0;
        if (fileType.getFormat() == basic.getFormatTypeNumber()) {
            // 同じOSの場合は元の属性をそのままセット
            val = t1;
        } else {
            // 別OSからの場合、近い属性をセット
            if ((fType & FILE_TYPE_BINARY_MASK.getValue()) != 0) {
                if ((fType & FILE_TYPE_BASIC_MASK.getValue()) != 0) val = FILETYPE_TFDOS_CMD;
                else if ((fType & FILE_TYPE_MACHINE_MASK.getValue()) != 0) val = FILETYPE_TFDOS_SYS;
                else val = FILETYPE_TFDOS_OBJ;
            } else if ((fType & FILE_TYPE_DATA_MASK.getValue()) != 0) {
                val = FILETYPE_TFDOS_DAT;
            } else if ((fType & FILE_TYPE_ASCII_MASK.getValue()) != 0) {
                val = FILETYPE_TFDOS_TEX;
            }

            if ((fType & FILE_TYPE_READONLY_MASK.getValue()) != 0) {
                val |= DATATYPE_TFDOS_READ_ONLY;
            }
            if ((fType & FILE_TYPE_HIDDEN_MASK.getValue()) != 0) {
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
        String attr = rb.getString(Utils.keyAt(typeNameTfDos, convFileType1Pos(t1)));

        if ((t1 & DATATYPE_TFDOS_READ_ONLY) != 0) {
            attr += ", ";
            attr += rb.getString(Utils.keyAt(typeNameTfDos, TYPE_NAME_TFDOS_READ_ONLY));
        }
        if ((t1 & DATATYPE_TFDOS_HIDDEN) != 0) {
            attr += ", ";
            attr += rb.getString(Utils.keyAt(typeNameTfDos, TYPE_NAME_TFDOS_HIDDEN));
        }
        return attr;
    }

    @Override
    protected void setFileSizeBase(int val) {
        data.data().fileSize = basic.invertAndOrderUint16((short) val); // invert
    }

    @Override
    protected int getFileSizeBase() {
        return basic.invertAndOrderUint16(data.data().fileSize); // invert
    }

    @Override
    public int getStartAddress() {
        return basic.invertAndOrderUint16(data.data().loadAddress); // invert
    }

    @Override
    public int getExecuteAddress() {
        return basic.invertAndOrderUint16(data.data().execAddress); // invert
    }

    @Override
    public void setStartAddress(int val) {
        data.data().loadAddress = basic.invertAndOrderUint16((short) val); // invert
    }

    @Override
    public void setExecuteAddress(int val) {
        data.data().execAddress = basic.invertAndOrderUint16((short) val); // invert
    }

    @Override
    public int getDataSize() {
        return data.getDataSize();
    }

    @Override
    public DirectoryTfDos getData() {
        return data.data();
    }

    @Override
    public boolean copyData(byte[] val) {
        return data.copy(val, getDataSize());
    }

    @Override
    public void clearData() {
        data.fill((byte) 0, getDataSize());
        Arrays.fill(data.data().name, (byte) 0x0d);
        basic.invertMemory(data.getRawData(), getDataSize()); // invert
    }

    @Override
    public void setStartGroup(int fileUnitNum, int val, int size) {
        data.data().track = basic.invertUint8((byte) (val & 0xff)); // invert
    }

    @Override
    public int getStartGroup(int fileUnitNum) {
        return basic.invertUint8(data.data().track); // invert
    }

    @Override
    public boolean preExportDataFile(String[] filename) {
        if (!config.isAddExtensionExport()) return true;

        String[] ext = new String[1];
        if (getFileAttrName(convFileType1Pos(getFileType1()), typeNameTfDos, ext)) {
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
        if (config.isDecideAttrImport()) {
            isContainAttrByExtension(filename[0], typeNameTfDos, TYPE_NAME_TFDOS_OBJ, TYPE_NAME_TFDOS_DBB, filename, null, null);
        }
        filename[0] = remakeFileNameAndExtStr(filename[0]);
        return true;
    }

    @Override
    public int convOriginalTypeFromFileName(String filename) {
        int[] t1 = {0};
        if (!isContainAttrByExtension(filename, typeNameTfDos, TYPE_NAME_TFDOS_OBJ, TYPE_NAME_TFDOS_DBB, null, t1, null)) {
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

        vals.add("TYPE", data.data().type, basic.isDataInverted());
        vals.add("NAME", data.data().name, data.data().name.length, basic.isDataInverted());
        vals.add("FILE_SIZE", data.data().fileSize, basic.isBigEndian(), basic.isDataInverted());
        vals.add("LOAD_ADDR", data.data().loadAddress, basic.isBigEndian(), basic.isDataInverted());
        vals.add("EXEC_ADDR", data.data().execAddress, basic.isBigEndian(), basic.isDataInverted());
        vals.add("TRACK", data.data().track, basic.isDataInverted());
    }
}
