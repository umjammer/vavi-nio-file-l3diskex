///
/// Copyright (c) Sasaji. All rights reserved.
///

package l3diskex.basicfmt.diritem;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

import l3diskex.Utils;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.KeyValArray;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.diritem.DiskBasicDirItemMAGICAL.DirectoryMagical;
import l3diskex.basicfmt.diritem.DiskBasicDirItemXDOS.DirectoryXDos;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;

import static l3diskex.Config.config;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_ASCII_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BASIC_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BINARY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_DATA_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_DIRECTORY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_HIDDEN_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_MACHINE_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_READONLY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_SYSTEM_MASK;
import static l3diskex.basicfmt.type.DiskBasicTypeMAGICAL.FORMAT_TYPE_MAGICAL;


/** Directory 1 item Magical DOS */
public class DiskBasicDirItemMAGICAL extends DiskBasicDirItemXDOSBase<DirectoryMagical> {

    static final ResourceBundle rb = ResourceBundle.getBundle("messages");

    /**
     * Magical DOS segment information
     */
    @Serdes
    public static class MagicalSeg {

        @Element(sequence = 1)
        public byte track;
        @Element(sequence = 2)
        public byte sector;
        @Element(sequence = 3)
        public byte size;
    }

    /**
     * Directory entry Magical DOS
     */
    @Serdes
    public static class DirectoryMagical extends DirectoryXDos {

        @Element(sequence = 1)
        public byte type; // 1
        @Element(sequence = 2)
        public byte[] name = new byte[31];
        @Element(sequence = 3)
        public byte type2; // 1
        @Element(sequence = 4)
        public short loadAddress; // 2
        @Element(sequence = 5)
        public short fileSize; // 2
        @Element(sequence = 6)
        public short execAddress; // 2
        @Element(sequence = 7)
        public byte[] date = new byte[2];
        @Element(sequence = 8)
        public byte[] time = new byte[2];
        @Element(sequence = 9)
        public byte[] reserved = new byte[2];
        @Element(sequence = 10)
        public MagicalSeg start = new MagicalSeg(); // 3

        public static final int SIZE = 48;
    }

    // Enums and Constants from basicdiritem_magical.h
    public enum TypeNameMagical1 {
        TYPE_NAME_MAGICAL_SYS,
        TYPE_NAME_MAGICAL_BAS,
        TYPE_NAME_MAGICAL_OBJ,
        TYPE_NAME_MAGICAL_ASC,
        TYPE_NAME_MAGICAL_DIR,
        TYPE_NAME_MAGICAL_CDT,
        TYPE_NAME_MAGICAL_PDT,
        TYPE_NAME_MAGICAL_GRA,
        TYPE_NAME_MAGICAL_GAK,
        TYPE_NAME_MAGICAL_SBA,
        TYPE_NAME_MAGICAL_SOB,
        TYPE_NAME_MAGICAL_REP,
        TYPE_NAME_MAGICAL_MDT,
        TYPE_NAME_MAGICAL_ARC,
        TYPE_NAME_MAGICAL_KTY,
        TYPE_NAME_MAGICAL_CGP,
        TYPE_NAME_MAGICAL_BGM,
        TYPE_NAME_MAGICAL_UNKNOWN,
    }

    public enum FileTypeMagical {
        FILETYPE_MAGICAL_SYS(0x01),
        FILETYPE_MAGICAL_BAS(0x22),
        FILETYPE_MAGICAL_OBJ(0x03),
        FILETYPE_MAGICAL_ASC(0x44),
        FILETYPE_MAGICAL_DIR(0x05),
        FILETYPE_MAGICAL_CDT(0x06),
        FILETYPE_MAGICAL_PDT(0x07),
        FILETYPE_MAGICAL_GRA(0x08),
        FILETYPE_MAGICAL_GAK(0x49),
        FILETYPE_MAGICAL_SBA(0x2a),
        FILETYPE_MAGICAL_SOB(0x0b),
        FILETYPE_MAGICAL_REP(0x4c),
        FILETYPE_MAGICAL_MDT(0x0d),
        FILETYPE_MAGICAL_ARC(0x4e),
        FILETYPE_MAGICAL_KTY(0x4f),
        FILETYPE_MAGICAL_CGP(0x50),
        FILETYPE_MAGICAL_BGM(0x51),
        FILETYPE_MAGICAL_UNKNOWN(0x80);
        final int v;

        FileTypeMagical(int v) {
            this.v = v;
        }
    }

    public enum TypeNameMagical2 {
        TYPE_NAME_MAGICAL_READONLY,
        TYPE_NAME_MAGICAL_HIDDEN,
        TYPE_NAME_MAGICAL_SYSTEM,
        TYPE_NAME_MAGICAL_SUPER,
    }

    public enum DataTypeMagical {
        DATATYPE_MAGICAL_MASK_b(0x00),
        DATATYPE_MAGICAL_MASK_r(0x01),
        DATATYPE_MAGICAL_MASK_g(0x02),
        DATATYPE_MAGICAL_MASK_m(0x03),
        DATATYPE_MAGICAL_MASK_A(0x04),
        DATATYPE_MAGICAL_MASK_B(0x05),
        DATATYPE_MAGICAL_MASK_C(0x06),
        DATATYPE_MAGICAL_MASK_D(0x07),
        DATATYPE_MAGICAL_MASK_E(0x08),
        DATATYPE_MAGICAL_MASK_F(0x09),
        DATATYPE_MAGICAL_MASK_G(0x0a),
        DATATYPE_MAGICAL_MASK_H(0x0b),
        DATATYPE_MAGICAL_MASK_I(0x0c),
        DATATYPE_MAGICAL_MASK_J(0x0d),
        DATATYPE_MAGICAL_MASK_K(0x0e),
        DATATYPE_MAGICAL_MASK_L(0x0f),
        DATATYPE_MAGICAL_MASK_READONLY(0x10),
        DATATYPE_MAGICAL_MASK_HIDDEN(0x20),
        DATATYPE_MAGICAL_MASK_SYSTEM(0x40),
        DATATYPE_MAGICAL_MASK_SUPER(0x80);
        final int v;

        DataTypeMagical(int v) {
            this.v = v;
        }
    }

    public enum TypeNameMagical3 {
        TYPE_NAME_MAGICAL_BANK_b,
        TYPE_NAME_MAGICAL_BANK_r,
        TYPE_NAME_MAGICAL_BANK_g,
        TYPE_NAME_MAGICAL_BANK_m,
        TYPE_NAME_MAGICAL_BANK_A,
        TYPE_NAME_MAGICAL_BANK_B,
        TYPE_NAME_MAGICAL_BANK_C,
        TYPE_NAME_MAGICAL_BANK_D,
        TYPE_NAME_MAGICAL_BANK_Unknown
    }

    /// Magical DOS attribute name
    public static final Map<String, Object> typeNameMagical1 = new LinkedHashMap<>() {{
        put("SYS", FileTypeMagical.FILETYPE_MAGICAL_SYS.ordinal()); // 0x01
        put("BAS", FileTypeMagical.FILETYPE_MAGICAL_BAS.ordinal()); // 0x22
        put("OBJ", FileTypeMagical.FILETYPE_MAGICAL_OBJ.ordinal()); // 0x03
        put("ASC", FileTypeMagical.FILETYPE_MAGICAL_ASC.ordinal()); // 0x44
        put("DIR", FileTypeMagical.FILETYPE_MAGICAL_DIR.ordinal()); // 0x05
        put("CDT", FileTypeMagical.FILETYPE_MAGICAL_CDT.ordinal()); // 0x06
        put("PDT", FileTypeMagical.FILETYPE_MAGICAL_PDT.ordinal()); // 0x07
        put("GRA", FileTypeMagical.FILETYPE_MAGICAL_GRA.ordinal()); // 0x08
        put("GAK", FileTypeMagical.FILETYPE_MAGICAL_GAK.ordinal()); // 0x49
        put("SBA", FileTypeMagical.FILETYPE_MAGICAL_SBA.ordinal()); // 0x2a
        put("SOB", FileTypeMagical.FILETYPE_MAGICAL_SOB.ordinal()); // 0x0b
        put("REP", FileTypeMagical.FILETYPE_MAGICAL_REP.ordinal()); // 0x4c
        put("MDT", FileTypeMagical.FILETYPE_MAGICAL_MDT.ordinal()); // 0x0d
        put("ARC", FileTypeMagical.FILETYPE_MAGICAL_ARC.ordinal()); // 0x4e
        put("KTY", FileTypeMagical.FILETYPE_MAGICAL_KTY.ordinal()); // 0x4f
        put("CGP", FileTypeMagical.FILETYPE_MAGICAL_CGP.ordinal()); // 0x50
        put("BGM", FileTypeMagical.FILETYPE_MAGICAL_BGM.ordinal()); // 0x51
        put("???", FileTypeMagical.FILETYPE_MAGICAL_UNKNOWN.ordinal()); // 0x80
    }};

    public static final int[] typeNameMagicalMap = {
            FileTypeMagical.FILETYPE_MAGICAL_SYS.ordinal(),
            FileTypeMagical.FILETYPE_MAGICAL_BAS.ordinal(),
            FileTypeMagical.FILETYPE_MAGICAL_OBJ.ordinal(),
            FileTypeMagical.FILETYPE_MAGICAL_ASC.ordinal(),
            FileTypeMagical.FILETYPE_MAGICAL_DIR.ordinal(),
            FileTypeMagical.FILETYPE_MAGICAL_CDT.ordinal(),
            FileTypeMagical.FILETYPE_MAGICAL_PDT.ordinal(),
            FileTypeMagical.FILETYPE_MAGICAL_GRA.ordinal(),
            FileTypeMagical.FILETYPE_MAGICAL_GAK.ordinal(),
            FileTypeMagical.FILETYPE_MAGICAL_SBA.ordinal(),
            FileTypeMagical.FILETYPE_MAGICAL_SOB.ordinal(),
            FileTypeMagical.FILETYPE_MAGICAL_REP.ordinal(),
            FileTypeMagical.FILETYPE_MAGICAL_MDT.ordinal(),
            FileTypeMagical.FILETYPE_MAGICAL_ARC.ordinal(),
            FileTypeMagical.FILETYPE_MAGICAL_KTY.ordinal(),
            FileTypeMagical.FILETYPE_MAGICAL_CGP.ordinal(),
            FileTypeMagical.FILETYPE_MAGICAL_BGM.ordinal(),
            FileTypeMagical.FILETYPE_MAGICAL_UNKNOWN.ordinal(),
    };

    public static final String[] typeNameMagical2 = {
            /*rb.getString(*/"Write Protected"/*)*/,
            /*rb.getString(*/"Hidden"/*)*/,
            /*rb.getString(*/"System"/*)*/,
            /*rb.getString(*/"Super User"/*)*/,
    };

    private static final String typeNameMagical3s = "brgmABCDEFGHIJKL";

    public static final String[] typeNameMagical3 = {
            "GRAM blue",
            "GRAM red",
            "GRAM green",
            "main",
            "ERAM A",
            "ERAM B",
            "ERAM C",
            "ERAM D",
            /*rb.getString(*/"Unknown"/*)*/,
    };

    /** Directory data */
    private final DiskBasicDirData<DirectoryMagical> data = new DiskBasicDirData<>();

    /** Pointer inside sector */
    private final DirItemSectorBoundary sectorData = new DirItemSectorBoundary();

    @Override
    public boolean isSupported(int formatType) {
        return formatType == FORMAT_TYPE_MAGICAL;
    }

    @Override
    public void init(DiskBasic basic) throws IOException {
        super.init(basic);

        data.alloc(DirectoryMagical.class);
        allocateItem(null);
    }

    @Override
    public void init(DiskBasic basic, DiskImageSector sector, int sectorPos, byte[] data, int dataP) throws IOException {
        super.init(basic, sector, sectorPos, data, dataP);

        this.data.attach(DirectoryMagical.class, data, dataP);
        allocateItem(null);
    }

    @Override
    public void init(DiskBasic basic, int num, DiskBasicGroupItem groupItem, DiskImageSector sector, int sectorPos,
                     byte[] data, int dataP, SectorParam next, boolean[] unuse) throws IOException {
        super.init(basic, num, groupItem, sector, sectorPos, data, dataP, next, unuse, true);

        this.data.attach(DirectoryMagical.class, data, dataP);
        allocateItem(next);

        used(checkUsed(unuse[0]));

        // Set pointer to chain sector
        if (isUsed()) {
            attachChain(getStartGroup(0));
        }

        // Calculate file size and number of groups
        calcFileSize();
    }

    @Override
    public void setData(int num, DiskBasicGroupItem groupItem, DiskImageSector sector, int sectorPos,
                        byte[] data, int dataPos, SectorParam next) throws IOException {
        super.setData(num, groupItem, sector, sectorPos, data, dataPos, next);

        this.data.attach(DirectoryMagical.class, data, dataPos);
        allocateItem(next);
    }

    @Override
    protected boolean allocateItem(SectorParam next) throws IOException {
        sectorData.clear();
        boolean bound = sectorData.set(basic, sector, position, data.getRawData(), getDataSize(), next);

        if (bound) {
            // When spanning sectors, data is allocated internally
            data.fill((byte) 0);
        }

        // Copy
        sectorData.copyTo(data.getRawData());

        return true;
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
        return data.data().type & 0xff;
    }

    protected String convFileType1Str(int t1) {
        return rb.getString(Utils.keyAt(typeNameMagical1, convFileType1Pos(t1)));
    }

    @Override
    protected void setFileType1(int val) {
        data.data().type = (byte) (val & 0xff);
    }

    @Override
    public int getFileType2() {
        return data.data().type2 & 0xff;
    }

    @Override
    protected void setFileType2(int val) {
        data.data().type2 = (byte) (val & 0xff);
    }

    @Override
    public boolean checkUsed(boolean unuse) {
        int t1 = getFileType1();
        return (!unuse && t1 != 0 && t1 != 0xff);
    }

    @Override
    public void setNativeName(byte[] filename, int size, int length) {
        super.setNativeName(filename, size, length);
    }

    @Override
    public void getNativeFileName(byte[] name, int[] nLen, byte[] ext, int[] eLen) {
        super.getNativeFileName(name, nLen, ext, eLen);
    }

    @Override
    public boolean check(boolean[] last) {
        if (!data.isValid()) return false;

        boolean valid = true;
        if (getFileType1() == 0xff) {
            last[0] = true;
            return valid;
        }
        return valid;
    }

    @Override
    public boolean isDeletable() {
        return true;
    }

    @Override
    public boolean delete() {
        // Deletion only requires putting a code at the beginning of the entry
        data.fill(basic.invertUint8(basic.getDeleteCode()), 1);
        used(false);
        return true;
    }

    @Override
    public int recalcFileSizeOnSave(InputStream iStream, int fileSize) throws IOException {
        if (needCheckEofCode()) {
            // Check if the end of the file ends with a terminator
            // However, if the file size matches the cluster size, a terminator is not necessary
            if ((fileSize % (basic.getSectorSize() * basic.getSectorsPerGroup())) != 0) {
                fileSize = checkEofCode(iStream, fileSize);
            }
        }
        return fileSize;
    }

    @Override
    public void setFileAttr(DiskBasicFileType fileType) {
        int fType = fileType.getType();
        if (fType == -1) return;

        int t1 = 0;
        int t2 = DataTypeMagical.DATATYPE_MAGICAL_MASK_m.ordinal();
        if (fileType.isDirectory()) {
            // If directory
            t1 = FileTypeMagical.FILETYPE_MAGICAL_DIR.ordinal();
        } else if (fileType.getFormat() == basic.getFormatTypeNumber()) {
            // If same OS
            t1 = fileType.getOrigin();
            t2 = t1 >> 8;
            t1 &= 0xff;
        } else {
            // If different OS
            if ((fType & FILE_TYPE_BINARY_MASK.getValue()) != 0) {
                if ((fType & FILE_TYPE_SYSTEM_MASK.getValue()) != 0) {
                    t1 = FileTypeMagical.FILETYPE_MAGICAL_SYS.ordinal();
                } else if ((fType & FILE_TYPE_BASIC_MASK.getValue()) != 0) {
                    t1 = FileTypeMagical.FILETYPE_MAGICAL_BAS.ordinal();
                } else {
                    t1 = FileTypeMagical.FILETYPE_MAGICAL_OBJ.ordinal();
                }
            } else {
                t1 = FileTypeMagical.FILETYPE_MAGICAL_ASC.ordinal();
            }
        }
        setFileType1(t1);
        setFileType2(t2);
    }

    @Override
    public DiskBasicFileType getFileAttr() {
        int t1 = getFileType1();
        int val = 0;
        switch (t1) {
            case 0x01: // FILETYPE_MAGICAL_SYS
                val = FILE_TYPE_SYSTEM_MASK.getValue();        // system
                val |= FILE_TYPE_BINARY_MASK.getValue();       // binary
                break;
            case 0x03: // FILETYPE_MAGICAL_OBJ
                val = FILE_TYPE_MACHINE_MASK.getValue();       // machine
                val |= FILE_TYPE_BINARY_MASK.getValue();       // binary
                break;
            case 0x22: // FILETYPE_MAGICAL_BAS
                val = FILE_TYPE_BASIC_MASK.getValue();         // basic
                val |= FILE_TYPE_BINARY_MASK.getValue();       // binary
                break;
            case 0x44: // FILETYPE_MAGICAL_ASC
                val = FILE_TYPE_DATA_MASK.getValue();          // data
                val |= FILE_TYPE_ASCII_MASK.getValue();        // ascii
                break;
            case 0x05: // FILETYPE_MAGICAL_DIR
                val = FILE_TYPE_DIRECTORY_MASK.getValue();     // directory
                break;
            default:
                val = FILE_TYPE_DATA_MASK.getValue();          // data
                val |= FILE_TYPE_BINARY_MASK.getValue();       // binary
                break;
        }

        int t2 = getFileType2();
        if ((t2 & DataTypeMagical.DATATYPE_MAGICAL_MASK_READONLY.ordinal()) != 0) {
            val |= FILE_TYPE_READONLY_MASK.getValue();
        }
        if ((t2 & DataTypeMagical.DATATYPE_MAGICAL_MASK_HIDDEN.ordinal()) != 0) {
            val |= FILE_TYPE_HIDDEN_MASK.getValue();
        }
        if ((t2 & DataTypeMagical.DATATYPE_MAGICAL_MASK_SYSTEM.ordinal()) != 0) {
            val |= FILE_TYPE_SYSTEM_MASK.getValue();
        }
        if ((t2 & DataTypeMagical.DATATYPE_MAGICAL_MASK_SUPER.ordinal()) != 0) {
            val |= FILE_TYPE_SYSTEM_MASK.getValue();
        }

        return new DiskBasicFileType(basic.getFormatTypeNumber(), val, (t2 << 8) | t1);
    }

    @Override
    public String getFileAttrStr() {
        StringBuilder attr = new StringBuilder(convFileType1Str(getFileType1()));
        //
        int t2 = getFileType2();

        attr.append(", ");
        attr.append(typeNameMagical3s.charAt(t2 & 0xf));

        if ((t2 & DataTypeMagical.DATATYPE_MAGICAL_MASK_READONLY.ordinal()) != 0) {
            attr.append(", ");
            attr.append(rb.getString(typeNameMagical2[TypeNameMagical2.TYPE_NAME_MAGICAL_READONLY.ordinal()]));
        }
        if ((t2 & DataTypeMagical.DATATYPE_MAGICAL_MASK_HIDDEN.ordinal()) != 0) {
            attr.append(", ");
            attr.append(rb.getString(typeNameMagical2[TypeNameMagical2.TYPE_NAME_MAGICAL_HIDDEN.ordinal()]));
        }
        if ((t2 & DataTypeMagical.DATATYPE_MAGICAL_MASK_SYSTEM.ordinal()) != 0) {
            attr.append(", ");
            attr.append(rb.getString(typeNameMagical2[TypeNameMagical2.TYPE_NAME_MAGICAL_SYSTEM.ordinal()]));
        }
        if ((t2 & DataTypeMagical.DATATYPE_MAGICAL_MASK_SUPER.ordinal()) != 0) {
            attr.append(", ");
            attr.append(rb.getString(typeNameMagical2[TypeNameMagical2.TYPE_NAME_MAGICAL_SUPER.ordinal()]));
        }
        return attr.toString();
    }

    @Override
    public void setFileSize(int val) {
        short size = basic.orderUint16((short) val);
        data.data().fileSize = size;
    }

    @Override
    public int getFileSize() {
        short size = data.data().fileSize;
        return basic.orderUint16(size);
    }

    @Override
    public void setStartGroup(int fileUnitNum, int val, int size) {
        byte track = (byte) ((val / basic.getSectorsPerTrackOnBasic()) & 0xff);
        byte sector = (byte) (((val % basic.getSectorsPerTrackOnBasic()) + 1) & 0xff);
        data.data().start.track = track;
        data.data().start.sector = sector;
        data.data().start.size = (byte) size;
    }

    @Override
    public int getStartGroup(int fileUnitNum) {
        int track = data.data().start.track & 0xff;
        int sector = data.data().start.sector & 0xff;
        return track * basic.getSectorsPerTrackOnBasic() + sector - 1;
    }

    @Override
    public void setExtraGroup(int val) {
        byte track = (byte) ((val / basic.getSectorsPerTrackOnBasic()) & 0xff);
        byte sector = (byte) (((val % basic.getSectorsPerTrackOnBasic()) + 1) & 0xff);
        data.data().start.track = track;
        data.data().start.sector = sector;
        data.data().start.size = 1;
    }

    @Override
    public int getExtraGroup() {
        int track = data.data().start.track & 0xff;
        int sector = data.data().start.sector & 0xff;
        return track * basic.getSectorsPerTrackOnBasic() + sector - 1;
    }

    @Override
    public void getExtraGroups(List<Integer> arr) {
        arr.add(getExtraGroup());
    }

    @Override
    public LocalDate getFileCreateDate(LocalDateTime tm) {
        int d0 = data.data().date[0] & 0xff;
        int d1 = data.data().date[1] & 0xff;

        int date = (d0 << 8) | d1;
        return convDateToTm((short) date);
    }

    @Override
    public LocalTime getFileCreateTime(LocalDateTime tm) {
        int t0 = data.data().time[0] & 0xff;
        int t1 = data.data().time[1] & 0xff;

        int time = (t0 << 8) | t1;
        return convTimeToTm((short) time);
    }

    @Override
    public void setFileCreateDate(LocalDateTime tm) {
        if (tm.getYear() >= 0 && tm.getMonth().ordinal() >= -1) {
            short date = convTmToDate(tm);
            data.data().date[0] = (byte) (date >> 8);
            data.data().date[1] = (byte) (date & 0xff);
        }
    }

    @Override
    public void setFileCreateTime(LocalDateTime tm) {
        if (tm.getHour() >= 0 && tm.getMinute() >= 0) {
            short time = convTmToTime(tm);
            data.data().time[0] = (byte) (time >> 8);
            data.data().time[1] = (byte) (time & 0xff);
        }
    }

    @Override
    public String getFileCreateDateTimeTitle() {
        return "Created Date";
    }

    private static LocalDate convDateToTm(short date) {
        int d = date & 0xffff;
        int yy = ((d & 0xfe00) >> 9) + 80;
        if (yy >= 128) yy -= 28;
        return LocalDate.of(
                yy,
                ((d & 0x01e0) >> 5) - 1,
                d & 0x001f);
    }

    private static LocalTime convTimeToTm(short time) {
        int t = time & 0xffff;
        return LocalTime.of(
                (t & 0xf800) >> 11,
                (t & 0x07e0) >> 5,
                (t & 0x001f) << 1);
    }

    private static short convTmToDate(LocalDateTime tm) {
        int yy = tm.getYear();
        if (yy >= 100) yy += 28;
        return (short)
                ((((yy - 80) & 0x7f) << 9)
                        | (((tm.getMonth().ordinal() + 1) & 0xf) << 5)
                        | (tm.getDayOfMonth() & 0x1f));
    }

    private static short convTmToTime(LocalDateTime tm) {
        return (short) (
                ((tm.getHour() & 0x1f) << 11)
                        | ((tm.getMinute() & 0x3f) << 5)
                        | ((tm.getSecond() & 0x3f) >> 1));
    }

    @Override
    public int getStartAddress() {
        short addr = data.data().loadAddress;
        return basic.orderUint16(addr);
    }

    @Override
    public int getExecuteAddress() {
        short addr = data.data().execAddress;
        return basic.orderUint16(addr);
    }

    @Override
    public void setStartAddress(int val) {
        data.data().loadAddress = basic.orderUint16((short) val);
    }

    @Override
    public void setExecuteAddress(int val) {
        data.data().execAddress = basic.orderUint16((short) val);
    }

    @Override
    public int getDataSize() {
        return data.getDataSize();
    }

    @Override
    public DirectoryMagical getData() {
        return data.data();
    }

    @Override
    public boolean copyData(byte[] val) {
        boolean sts = data.copy(val);
        sectorData.copyFrom(data.getRawData());
        return sts;
    }

    @Override
    public void clearData() {
        data.fill(basic.getDeleteCode());
        sectorData.copyFrom(data.getRawData());
    }

    @Override
    public void initialData() {
        data.fill(basic.getFillCodeOnDir());
            sectorData.copyFrom(data.getRawData());
    }

    @Override
    public boolean preExportDataFile(String[] filename) {
        if (!config.isAddExtensionExport()) return true;

        // Append extension
        if (!isDirectory()) {
            String ext = convFileType1Str(getFileType1());
            filename[0] += ".";
            if (Utils.isUpperString(filename[0])) {
                ext = ext.toUpperCase();
            } else {
                ext = ext.toLowerCase();
            }
            filename[0] += ext;
        }
        return true;
    }

    @Override
    public boolean preImportDataFile(String[] filename) {
        if (config.isDecideAttrImport()) {
            isContainAttrByExtension(filename[0], typeNameMagical1, TypeNameMagical1.TYPE_NAME_MAGICAL_SYS.ordinal(), TypeNameMagical1.TYPE_NAME_MAGICAL_BGM.ordinal(), filename, null, null);
        }
        filename[0] = remakeFileNameAndExtStr(filename[0]);
        return true;
    }

    @Override
    public int convOriginalTypeFromFileName(String filename) {
        int[] t1 = {0};
        // Set attribute by extension
        if (!isContainAttrByExtension(filename, typeNameMagical1, TypeNameMagical1.TYPE_NAME_MAGICAL_SYS.ordinal(), TypeNameMagical1.TYPE_NAME_MAGICAL_BGM.ordinal(), null, t1, null)) {
            // Unknown extension
            t1[0] = FileTypeMagical.FILETYPE_MAGICAL_ASC.v;
        }
        //int extType = getFileTypeFromExtension(filename);
        //if (extType != -1) {
        //    t1 = extType;
        //} else {
        //    // Unknown extension
        //    t1 = en_file_type_magical.FILETYPE_MAGICAL_ASC.ordinal();
        //}
        t1[0] |= (DataTypeMagical.DATATYPE_MAGICAL_MASK_m.ordinal() << 8);
        return t1[0];
    }

    @Override
    public void setModify() {
        sectorData.copyFrom(data.getRawData());
    }

    private int convFileType1Pos(int t1) {
        int pos = TypeNameMagical1.TYPE_NAME_MAGICAL_UNKNOWN.ordinal();
        for (int i = 0; ; i++) {
            if (i >= typeNameMagicalMap.length || typeNameMagicalMap[i] == 0) {
                break;
            }
            int val = typeNameMagicalMap[i];
            if (t1 == val) {
                pos = i;
                break;
            }
        }
        return pos;
    }

    @Override
    public void setInternalDataInAttrDialog(KeyValArray vals) {
        vals.add("TYPE", data.data().type);
        vals.add("NAME", data.data().name, data.data().name.length);
        vals.add("TYPE2", data.data().type2);
        vals.add("LOAD_ADDR", (byte) data.data().loadAddress, basic.isBigEndian());
        vals.add("FILE_SIZE", (byte) data.data().fileSize, basic.isBigEndian());
        vals.add("EXEC_ADDR", (byte) data.data().execAddress, basic.isBigEndian());
        vals.add("DATE", data.data().date, data.data().date.length);
        vals.add("TIME", data.data().time, data.data().time.length);
        vals.add("RESERVED", data.data().reserved, data.data().reserved.length);
        vals.add("START.TRACK", data.data().start.track);
        vals.add("START.SECTOR", data.data().start.sector);
        vals.add("START.SIZE", data.data().start.size);
    }
}