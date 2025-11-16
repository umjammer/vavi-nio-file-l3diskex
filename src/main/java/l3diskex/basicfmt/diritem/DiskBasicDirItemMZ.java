/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt.diritem;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import javax.swing.JWindow;

import l3diskex.Utils;
import l3diskex.basicfmt.BasicCommon.Directory;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileName;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicFormatType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.KeyValArray;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.diritem.DiskBasicDirItemMZ.DirectoryMz;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;

import static l3diskex.Config.config;
import static l3diskex.Parambase.MyAttributes.findValue;
import static l3diskex.Parambase.MyAttributes.getTypeByValue;
import static l3diskex.basicfmt.BasicCommon.DiskBasicFormatType.FORMAT_TYPE_MZ;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_ASCII_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BINARY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_DIRECTORY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_MACHINE_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_RANDOM_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_READONLY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_TEMPORARY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_VOLUME_MASK;
import static l3diskex.basicfmt.DiskBasicError.ERR_FILENAME_EMPTY;
import static l3diskex.basicfmt.DiskBasicError.gDiskBasicErrorMsgs;
import static l3diskex.basicfmt.DiskBasicType.INVALID_GROUP_NUMBER;


/** ディレクトリ１アイテム MZ DISK BASIC */
public class DiskBasicDirItemMZ extends DiskBasicDirItemMZBase<DirectoryMz> {

    static final ResourceBundle rb = ResourceBundle.getBundle("messages");

    /**
     * ディレクトリエントリ MZ DISK BASIC
     */
    @Serdes(bigEndian = false)
    public static class DirectoryMz implements Directory {

        @Element(sequence = 1)
        public byte type;
        @Element(sequence = 2)
        public byte[] name = new byte[17]; // file name has $0D on the end of string
        @Element(sequence = 3)
        public byte type2;
        @Element(sequence = 4)
        public byte reserved;
        @Element(sequence = 5)
        public short fileSize;
        @Element(sequence = 6)
        public short loadAddress;
        @Element(sequence = 7)
        public short execAddress;
        @Element(sequence = 8)
        public byte[] dateTime = new byte[4];
        @Element(sequence = 9)
        public short startSector;

        public static final int SIZE = 32;
    }

    // MZ S-BASIC 属性

    /** マシン語のファイル */
    static final int FILETYPE_MZ_OBJ = 1;
    /** BASIC Text File */
    static final int FILETYPE_MZ_BTX = 2;
    /** BASIC シーケンシャルDATA */
    static final int FILETYPE_MZ_BSD = 3;
    /** BASIC Random Access, Data File */
    static final int FILETYPE_MZ_BRD = 4;
    static final int FILETYPE_MZ_DIR = 0xf;
    static final int FILETYPE_MZ_VOL = 0x80;
    static final int FILETYPE_MZ_VOLSWAP = 0x81;

    static final int DATATYPE_MZ_READ_ONLY = 0x01;
    static final int DATATYPE_MZ_SEAMLESS = 0x80;

    static final int DATATYPE_MZ_SEAMLESS_POS = 20;

    static final int TYPE_NAME_MZ_UNKNOWN = 0;
    static final int TYPE_NAME_MZ_OBJ = 1;
    static final int TYPE_NAME_MZ_BTX = 2;
    static final int TYPE_NAME_MZ_BSD = 3;
    static final int TYPE_NAME_MZ_BRD = 4;
    static final int TYPE_NAME_MZ_DIR = 5;
    static final int TYPE_NAME_MZ_VOL = 6;
    static final int TYPE_NAME_MZ_VOLSWAP = 7;

    static final int TYPE_NAME_MZ2_READ_ONLY = 0;
    static final int TYPE_NAME_MZ2_SEAMLESS = 1;

    /// MZ属性名
    public static final Map<String, Object> typeNameMz = new HashMap<>() {{
        put("???", TYPE_NAME_MZ_UNKNOWN);
        put("OBJ", FILETYPE_MZ_OBJ);
        put("BTX", FILETYPE_MZ_BTX);
        put("BSD", FILETYPE_MZ_BSD);
        put("BRD", FILETYPE_MZ_BRD);
        put("DIR", FILETYPE_MZ_DIR);
        put(/*rb.getString(*/"<VOL>"/*)*/, FILETYPE_MZ_VOL);
        put(/*rb.getString(*/"<VOL> SWAP"/*)*/, FILETYPE_MZ_VOLSWAP);
    }};

    public static final String[] typeNameMz2 = {
            /*rb.getString(*/"Write Protected"/*)*/,
            /*rb.getString(*/"Seamless"/*)*/,
    };

    /** ディレクトリデータ */
    private final DiskBasicDirData<DirectoryMz> data = new DiskBasicDirData<>();

    //
    //
    //

    @Override
    public boolean isSupported(DiskBasicFormatType formatType) {
        return formatType == FORMAT_TYPE_MZ;
    }

    @Override
    public void init(DiskBasic basic) throws IOException {
        super.init(basic);

        data.alloc(DirectoryMz.class);
    }

    @Override
    public void init(DiskBasic basic, DiskImageSector sector, int sectorPos, byte[] data, int dataP) throws IOException {
        super.init(basic, sector, sectorPos, data, dataP);

        this.data.attach(DirectoryMz.class, data, dataP);
    }

    @Override
    public void init(DiskBasic basic, int num, DiskBasicGroupItem groupItem, DiskImageSector sector, int sectorPos,
                     byte[] data, int dataP, SectorParam next, boolean[] unuse) throws IOException {
        super.init(basic, num, groupItem, sector, sectorPos, data, dataP, next, unuse);

        // MZ
        this.data.attach(DirectoryMz.class, data, dataP);

        used(checkUsed(unuse[0]));

        calcFileSize();

        // カレント or 親ディレクトリはツリーに表示しない
        String name = getFileNamePlainStr();
        visibleOnTree(!(isDirectory() && (name.equals(".") || name.equals(".."))));
    }

    @Override
    public void setData(int num, DiskBasicGroupItem groupItem, DiskImageSector sector, int sectorPos,
                        byte[] data, int dataPos, SectorParam next) throws IOException {
        super.setData(num, groupItem, sector, sectorPos, data, dataPos, next);

        this.data.attach(DirectoryMz.class, data, dataPos);
    }

    @Override
    protected byte[] getFileNamePos(int num, int[] size, int[] len) {
        if (num == 0) {
            size[0] = data.data().name.length;
            len[0] = size[0] - 1;
            return data.data().name;
        } else {
            size[0] = 0;
            len[0] = 0;
            return null;
        }
    }

    @Override
    public int getFileType1() {
        return basic.invertUint8(data.data().type) & 0xff; // invert
    }

    @Override
    public int getFileType2() {
        return basic.invertUint8(data.data().type2) & 0xff; // invert
    }

    @Override
    protected void setFileType1(int val) {
        data.data().type = basic.invertUint8((byte) val); // invert
    }

    @Override
    protected void setFileType2(int val) {
        data.data().type2 = basic.invertUint8((byte) val); // invert
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
        if ((t & 0x70) != 0 && findValue(basic.getSpecialAttributes(), t) == null) {
            valid = false;
        }
        return valid;
    }

//    public boolean delete() {
//        data.fill(basic.invertUint8(basic.getDeleteCode()), 1);
//        used(false);
//        return true;
//    }

    @Override
    public void setFileAttr(DiskBasicFileType fileType) {
        int fType = fileType.getType();
        if (fType == -1) return;

        int t1 = 0;
        int t2 = 0;
        if (fileType.getFormat() == basic.getFormatTypeNumber()) {
            t1 = fileType.getOrigin() & 0xff;
            t2 = (fileType.getOrigin() >> 8) & 0xff;
        } else {
            t1 = convToNativeType(fType);
            if ((fType & FILE_TYPE_READONLY_MASK.getValue()) != 0) {
                t2 |= DATATYPE_MZ_READ_ONLY;
            }
        }
        setFileType1(t1);
        setFileType2(t2);
    }

    private int convToNativeType(int fileType) {
        int val = 0;
        if ((fileType & FILE_TYPE_MACHINE_MASK.getValue()) != 0) {
            val = FILETYPE_MZ_OBJ;
        } else if ((fileType & FILE_TYPE_BINARY_MASK.getValue()) != 0) {
            val = FILETYPE_MZ_BTX;
        } else if ((fileType & FILE_TYPE_ASCII_MASK.getValue()) != 0) {
            val = FILETYPE_MZ_BSD;
        } else if ((fileType & FILE_TYPE_RANDOM_MASK.getValue()) != 0) {
            val = FILETYPE_MZ_BRD;
        } else if ((fileType & FILE_TYPE_DIRECTORY_MASK.getValue()) != 0) {
            val = FILETYPE_MZ_DIR;
        } else if ((fileType & FILE_TYPE_VOLUME_MASK.getValue()) != 0) {
            val = FILETYPE_MZ_VOL;
            if ((fileType & FILE_TYPE_TEMPORARY_MASK.getValue()) != 0) val = FILETYPE_MZ_VOLSWAP;
        }
        return val;
    }

    @Override
    public DiskBasicFileType getFileAttr() {
        int t1 = getFileType1();
        int val = 0;
        switch (t1) {
            case FILETYPE_MZ_OBJ:
                val = FILE_TYPE_MACHINE_MASK.getValue();    // machine
                val |= FILE_TYPE_BINARY_MASK.getValue();    // binary
                break;
            case FILETYPE_MZ_BTX:
                val = FILE_TYPE_MACHINE_MASK.getValue();    // BASIC
                val |= FILE_TYPE_BINARY_MASK.getValue();    // binary
                break;
            case FILETYPE_MZ_BSD:
                val = FILE_TYPE_MACHINE_MASK.getValue();    // BASIC
                val |= FILE_TYPE_ASCII_MASK.getValue();     // ascii
                break;
            case FILETYPE_MZ_BRD:
                val = FILE_TYPE_RANDOM_MASK.getValue();     // DATA
                val |= FILE_TYPE_RANDOM_MASK.getValue();    // random acces
                break;
            case FILETYPE_MZ_DIR:
                val = FILE_TYPE_DIRECTORY_MASK.getValue();  // Sub directory
                break;
            case FILETYPE_MZ_VOL:
                val = FILE_TYPE_VOLUME_MASK.getValue();     // Volume
                break;
            case FILETYPE_MZ_VOLSWAP:
                val = FILE_TYPE_VOLUME_MASK.getValue();     // Volume
                val |= FILE_TYPE_TEMPORARY_MASK.getValue(); // temporary
                break;
            default:
                val = getTypeByValue(basic.getSpecialAttributes(), t1);
                break;
        }
        int t2 = getFileType2();
        if ((t2 & DATATYPE_MZ_READ_ONLY) != 0) {
            // write protect
            val |= FILE_TYPE_READONLY_MASK.getValue();
        }
        val |= ((t2 & DATATYPE_MZ_SEAMLESS) << DATATYPE_MZ_SEAMLESS_POS);

        return new DiskBasicFileType(basic.getFormatTypeNumber(), val, (t2 << 8) | t1);
    }

    @Override
    public String getFileAttrStr() {
        String[] attr = new String[1];
        getFileAttrName(convFileType1Pos(getFileType1()), typeNameMz, attr, TYPE_NAME_MZ_UNKNOWN);

        int t2 = getFileType2();
        if ((t2 & DATATYPE_MZ_READ_ONLY) != 0) {
            // write protect
            attr[0] += ", ";
            attr[0] += rb.getString(typeNameMz2[TYPE_NAME_MZ2_READ_ONLY]);
        }

        return attr[0];
    }

    @Override
    protected void setFileSizeBase(int val) {
        data.data().fileSize = basic.invertAndOrderUint16((short) val); // invert
    }

    @Override
    public void setFileSize(int val) {
        groups.setSize(val);
        if (getFileType1() == FILETYPE_MZ_BRD) {
            // BRD file
            val = ((val + 31) / 32);
        }
        setFileSizeBase(val);
    }

    @Override
    protected int getFileSizeBase() {
        int val = basic.invertAndOrderUint16(data.data().fileSize) & 0xffff;
        if (getFileType1() == FILETYPE_MZ_BRD) {
            // BRD file
            val *= 32;
        }
        return val;
    }

    /// MZ BRD形式のマップ
    private static class StBrdParams {

        int pos;
        int count;
        short[] maps;
    }

    @Override
    protected void preCalcFileSize() {
        //if (getFileType1() == FILETYPE_MZ_BRD) {
        //    m_file_size *= 32;
        //}
    }

    @Override
    protected void preCalcAllGroups(int[] calcFlags, int[] groupNum, int[] remain, int[] sectorSize, Object[] userData) {
        boolean isChain = needChainInData();
        boolean isBRD = getFileType1() == FILETYPE_MZ_BRD;
        calcFlags[0] = (isChain ? 1 : 0) | (isBRD ? 2 : 0);

        StBrdParams brd = new StBrdParams();
        brd.pos = 0;
        brd.count = 0;
        brd.maps = null;

        if (isChain) {
            // 各セクタの最後2バイト分を減算
            sectorSize[0] -= 2;
        }
        if (isBRD) {
            DiskImageSector sector = basic.getSectorFromGroup(groupNum[0]);
            if (sector != null) {
                // This is the pointer map to each start sector
                ShortBuffer buffer = ByteBuffer.wrap(sector.getSectorBuffer()).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
                brd.maps = new short[buffer.capacity() / Short.BYTES];
                buffer.get(brd.maps);

                groupNum[0] = basic.invertAndOrderUint16(brd.maps[brd.pos]); // invert
                groupNum[0] /= basic.getSectorsPerGroup();

                // 残りサイズは16セクタ分で丸める
                int blockSize = (16 * basic.getSectorSize());
                remain[0] = ((remain[0] + blockSize - 1) / blockSize) * blockSize;
            }
        }

        userData[0] = brd;
    }

    @Override
    protected void calcAllGroups(int calcFlags, int[] groupNum, int[] remain, int[] sectorSize, int[] endSector, Object userData) {
        boolean isChain = ((calcFlags & 1) != 0);
        boolean isBRD = ((calcFlags & 2) != 0);
        StBrdParams brd = (StBrdParams) userData;

        if (isChain) {
            // BSD
            groupNum[0] = type.getNextGroupNumber(groupNum[0], endSector[0]);
        } else {
            // BTX,OBJ
            groupNum[0]++;
            if (isBRD) {
                // BRD
                brd.count += basic.getSectorsPerGroup();
                if (brd.count >= 16) {
                    brd.count = 0;
                    if (((brd.pos + 1) * 2) < basic.getSectorSize()) {
                        brd.pos++;
                    }
                    if (brd.maps != null && brd.pos < brd.maps.length) {
                        groupNum[0] = basic.invertAndOrderUint16(brd.maps[brd.pos]);
                        groupNum[0] /= basic.getSectorsPerGroup();
                    }
                }
            }
        }
    }

    @Override
    protected void postCalcAllGroups(Object userData) {
    }

    @Override
    public LocalDate getFileCreateDate(LocalDateTime tm) {
        int ymd;
        ymd = (data.data().dateTime[0] & 0xFF) << 16 | (data.data().dateTime[1] & 0xFF) << 8 | (data.data().dateTime[2] & 0xFF);
        int inverted_ymd = basic.invertUint32(ymd); // invert
        return LocalDate.of(
                ((inverted_ymd >> 20) & 0x0f) * 10 + ((inverted_ymd >> 16) & 0x0f) +
                        (tm.getYear() < 80 ? 100 : 0),
                ((inverted_ymd >> 15) & 1) * 10 + ((inverted_ymd >> 11) & 0x0f) - 1,
                ((inverted_ymd >> 9) & 3) * 10 + ((inverted_ymd >> 5) & 0x0f)
        );
    }

    @Override
    public LocalTime getFileCreateTime(LocalDateTime tm) {
        int hms;
        hms = (data.data().dateTime[2] & 0xFF) << 8 | (data.data().dateTime[3] & 0xFF);
        int inverted_hms = basic.invertUint32(hms); // invert
        return LocalTime.of(
                ((inverted_hms >> 11) & 3) * 10 + ((inverted_hms >> 7) & 0x0f),
                ((inverted_hms >> 4) & 0x7) * 10 + (inverted_hms & 0x0f),
                0);
    }

    @Override
    public String getFileCreateDateStr() {
        LocalDateTime tm = LocalDateTime.now();
        LocalDate ld = getFileCreateDate(tm);
        return Utils.formatYMDStr(ld);
    }

    @Override
    public String getFileCreateTimeStr() {
        LocalDateTime tm = LocalDateTime.now();
        LocalTime lt = getFileCreateTime(tm);
        return Utils.formatHMStr(lt);
    }

    @Override
    public void setFileCreateDate(LocalDateTime tm) {
        if (tm.getYear() < 0 || tm.getMonth().ordinal() < -1) return;

        int tmp = (data.data().dateTime[0] & 0xFF) << 16 | (data.data().dateTime[1] & 0xFF) << 8 | (data.data().dateTime[2] & 0xFF);
        int invertedTmp = basic.invertUint32(tmp);
        invertedTmp &= 0x1f;
        invertedTmp |= (((tm.getYear() / 10) % 10) << 20) | ((tm.getYear() % 10) << 16);
        invertedTmp |= ((((tm.getMonth().ordinal() + 1) / 10) & 1) << 15) | (((tm.getMonth().ordinal() + 1) % 10) << 11);
        invertedTmp |= (((tm.getDayOfMonth() / 10) & 3) << 9) | ((tm.getDayOfMonth() % 10) << 5);
        int finalTmp = basic.invertUint32(invertedTmp);

        data.data().dateTime[0] = (byte) (finalTmp >> 16);
        data.data().dateTime[1] = (byte) (finalTmp >> 8);
        data.data().dateTime[2] = (byte) (finalTmp & 0xff);
    }

    @Override
    public void setFileCreateTime(LocalDateTime tm) {
        if (tm.getHour() < 0 || tm.getMinute() < 0) return;

        int tmp = (data.data().dateTime[2] & 0xFF) << 8 | (data.data().dateTime[3] & 0xFF);
        int invertedTmp = basic.invertUint32(tmp & 0xFFFF_FFFF);
        invertedTmp &= ~0x1fff;
        invertedTmp |= (((tm.getHour() / 10) & 3) << 11) | ((tm.getHour() % 10) << 7);
        invertedTmp |= (((tm.getMinute() / 10) & 7) << 4) | (tm.getMinute() % 10);
        int finalTmp = basic.invertUint32(invertedTmp & 0xFFFF_FFFF);

        data.data().dateTime[2] = (byte) (finalTmp >> 8);
        data.data().dateTime[3] = (byte) (finalTmp & 0xff);
    }

    @Override
    public int getStartAddress() {
        return basic.invertAndOrderUint16(data.data().loadAddress) & 0xffff;
    }

    @Override
    public int getExecuteAddress() {
        return basic.invertAndOrderUint16(data.data().execAddress) & 0xffff;
    }

    @Override
    public void setStartAddress(int val) {
        data.data().loadAddress = basic.invertAndOrderUint16((short) val);
    }

    @Override
    public void setExecuteAddress(int val) {
        data.data().execAddress = basic.invertAndOrderUint16((short) val);
    }

    @Override
    public int getDataSize() {
        return data.getDataSize();
    }

    @Override
    public DirectoryMz getData() {
        return data.data();
    }

    @Override
    public boolean copyData(byte[] val) {
        return data.copy(val, getDataSize());
    }

    @Override
    public void clearData() {
        if (!data.isValid()) return;
        data.fill(0);
        Arrays.fill(data.getRawData(), 1, 1 + 17, (byte) 0x0d); // name
        basic.invertMemory(data.getRawData(), data.getDataSize()); // invert
    }

    @Override
    public void setStartGroup(int fileUnitNum, int val, int size) {
        int sVal = val * basic.getSectorsPerGroup();
        sVal = basic.invertAndOrderUint16((short) sVal);
        data.data().startSector = (short) sVal;
    }

    @Override
    public int getStartGroup(int fileUnitNum) {
        int sVal = basic.invertAndOrderUint16(data.data().startSector);
        return sVal / basic.getSectorsPerGroup();
    }

    @Override
    public int getExtraGroup() {
        int val = INVALID_GROUP_NUMBER;
        if (getFileType1() == FILETYPE_MZ_BRD) {
            val = getStartGroup(0);
        }
        return val;
    }

    @Override
    public void getExtraGroups(List<Integer> arr) {
        if (getFileType1() == FILETYPE_MZ_BRD) {
            arr.add(getStartGroup(0));
        }
    }

    @Override
    public boolean isSameFileName(DiskBasicFileName filename, boolean caseInsensitive) {
        int t1 = getFileType1();
        if (t1 == 0 || t1 == FILETYPE_MZ_VOL) return false;

        return super.isSameFileName(filename, caseInsensitive);
    }

    @Override
    public boolean isSameFileName(DiskBasicDirItem src, boolean iCase) {
        int t1 = getFileType1();
        if (t1 == 0 || t1 == FILETYPE_MZ_VOL) return false;

        return super.isSameFileName(src, iCase);
    }

    @Override
    public boolean needChainInData() {
        return getFileAttr().matchType(FILE_TYPE_ASCII_MASK.getValue(), FILE_TYPE_ASCII_MASK.getValue());
    }

    @Override
    public boolean preExportDataFile(String[] filename) {
        if (!config.isAddExtensionExport()) return true;

        if (!isDirectory()) {
            String[] ext = new String[1];
            if (getFileAttrName(convFileType1Pos(getFileType1()), typeNameMz, ext, TYPE_NAME_MZ_UNKNOWN)) {
                filename[0] += ".";
                if (Utils.isUpperString(filename[0])) {
                    filename[0] += ext[0].toUpperCase();
                } else {
                    filename[0] += ext[0].toLowerCase();
                }
            }
        }
        return true;
    }

    @Override
    public boolean preImportDataFile(String[] filename) {
        if (config.isDecideAttrImport()) {
            isContainAttrByExtension(filename[0], typeNameMz, TYPE_NAME_MZ_OBJ, TYPE_NAME_MZ_DIR, filename, null, null);
        }
        filename[0] = remakeFileNameAndExtStr(filename[0]);
        return true;
    }

    @Override
    public int convOriginalTypeFromFileName(String filename) {
        int[] t1 = new int[1];
        if (!isContainAttrByExtension(filename, typeNameMz, TYPE_NAME_MZ_OBJ, TYPE_NAME_MZ_DIR, null, t1, null)) {
            t1[0] = FILETYPE_MZ_BSD;
        }
        return t1[0];
    }

    public int convFileType1Pos(int nativeType) {
        int pos = switch (nativeType) {
            case FILETYPE_MZ_OBJ -> TYPE_NAME_MZ_OBJ;
            case FILETYPE_MZ_BTX -> TYPE_NAME_MZ_BTX;
            case FILETYPE_MZ_BSD -> TYPE_NAME_MZ_BSD;
            case FILETYPE_MZ_BRD -> TYPE_NAME_MZ_BRD;
            case FILETYPE_MZ_DIR -> TYPE_NAME_MZ_DIR;
            case FILETYPE_MZ_VOL -> TYPE_NAME_MZ_VOL;
            case FILETYPE_MZ_VOLSWAP -> TYPE_NAME_MZ_VOLSWAP;
            default -> -nativeType;
        };
        return pos;
    }

    public int convFileType2Pos(int nativeType) {
        int val = 0;
        if ((nativeType & DATATYPE_MZ_READ_ONLY) != 0) {
            val |= FILE_TYPE_READONLY_MASK.getValue();
        }
        val |= ((nativeType & DATATYPE_MZ_SEAMLESS) << DATATYPE_MZ_SEAMLESS_POS);
        return val;
    }

    public void setFileTypeForAttrDialog(int showFlags, String name, int[] fileType1, int[] fileType2) {
        if ((showFlags & 1) != 0) { // INTNAME_NEW_FILE is assumed to be 1
            fileType1[0] = convOriginalTypeFromFileName(name);
        }
    }

    public boolean validateFileName(JWindow parent, String filename, String[] errorMessage) {
        if (filename.isEmpty()) {
            errorMessage[0] = rb.getString(gDiskBasicErrorMsgs[ERR_FILENAME_EMPTY]);
            return false;
        }
        return true;
    }

    @Override
    public void setInternalDataInAttrDialog(KeyValArray vals) {
        vals.add("inverted", basic.isDataInverted());

        vals.add("TYPE", (byte) (data.data().type & 0xFF), basic.isDataInverted());
        vals.add("NAME", data.data().name, data.data().name.length, basic.isDataInverted());
        vals.add("TYPE2", (byte) (data.data().type2 & 0xFF), basic.isDataInverted());
        vals.add("RESERVED", (byte) (data.data().reserved & 0xFF), basic.isDataInverted());
        vals.add("FILE_SIZE", data.data().fileSize & 0xFFFF, basic.isBigEndian(), basic.isDataInverted());
        vals.add("LOAD_ADDR", data.data().loadAddress & 0xFFFF, basic.isBigEndian(), basic.isDataInverted());
        vals.add("EXEC_ADDR", data.data().execAddress & 0xFFFF, basic.isBigEndian(), basic.isDataInverted());
        vals.add("DATE_TIME", data.data().dateTime, data.data().dateTime.length, basic.isDataInverted());
        vals.add("START_SECTOR", data.data().startSector & 0xFFFF, basic.isBigEndian(), basic.isDataInverted());
    }
}