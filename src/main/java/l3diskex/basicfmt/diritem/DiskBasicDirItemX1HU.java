///
/// Copyright (c) Sasaji. All rights reserved.
///

package l3diskex.basicfmt.diritem;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

import l3diskex.Parambase.MyAttribute;
import l3diskex.Utils;
import l3diskex.basicfmt.BasicCommon.Directory;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.BasicCommon.KeyValArray;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.diritem.DiskBasicDirItemX1HU.DirectoryX1Hu;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;

import static l3diskex.Common.invertMemory;
import static l3diskex.Parambase.MyAttributes.findUpperCase;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_ASCII_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BASIC_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BINARY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_DIRECTORY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_ENCRYPTED_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_HIDDEN_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_MACHINE_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_RANDOM_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_READONLY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_READWRITE_MASK;
import static l3diskex.basicfmt.type.DiskBasicTypeX1HU.FORMAT_TYPE_X1HU;


/**
 * Directory 1 item X1 Hu-BASIC
 *
 * <li>DefaultAsciiType ASCII file (Hu-BASIC or S-OS)</li>
 */
public class DiskBasicDirItemX1HU extends DiskBasicDirItem<DirectoryX1Hu> {

    private static final ResourceBundle rb = ResourceBundle.getBundle("messages");

    /**
     * Directory entry X1 Hu-BASIC
     */
    @Serdes(bigEndian = false)
    public static class DirectoryX1Hu implements Directory {

        @Element(sequence = 1)
        public byte type;
        @Element(sequence = 2)
        public byte[] name = new byte[13];
        @Element(sequence = 3)
        public byte[] ext = new byte[3];
        @Element(sequence = 4)
        public byte password;
        @Element(sequence = 5)
        public short fileSize;
        @Element(sequence = 6)
        public short loadAddress;
        @Element(sequence = 7)
        public short execAddress;
        @Element(sequence = 8)
        public byte[] date = new byte[3]; // yymwdd yy: BCD 00-99, m: HEX 0-C, w: WEEK HEX 0(SUN)-7(SAT), dd: BCD
        @Element(sequence = 9)
        public byte[] time = new byte[2]; // hhmi BCD
        @Element(sequence = 19)
        public byte startGroupH;
        @Element(sequence = 11)
        public short startGroupL;

        public static final int SIZE = 32;
    }

    // X1 Hu-BASIC attribute 1 position
    public static final int TYPE_NAME_X1HU_BINARY = 0;
    public static final int TYPE_NAME_X1HU_BASIC = 1;
    public static final int TYPE_NAME_X1HU_ASCII = 2;
    public static final int TYPE_NAME_X1HU_SWORD = 3;
    public static final int TYPE_NAME_X1HU_RANDOM = 4;
    public static final int TYPE_NAME_X1HU_DIRECTORY = 5;
    public static final int TYPE_NAME_X1HU_END = 6;

    // X1 Hu-BASIC attribute 1 value
    public static final int FILETYPE_X1HU_BINARY = 0x01;
    public static final int FILETYPE_X1HU_BASIC = 0x02;
    public static final int FILETYPE_X1HU_ASCII = 0x04;
    public static final int FILETYPE_X1HU_DIRECTORY = 0x80;
    public static final int FILETYPE_X1HU_MASK = (FILETYPE_X1HU_BINARY | FILETYPE_X1HU_BASIC | FILETYPE_X1HU_ASCII | FILETYPE_X1HU_DIRECTORY);

    // en_external_type_x1
    public static final int EXTERNAL_X1_DEFAULT = 0;
    public static final int EXTERNAL_X1_RANDOM = 1;
    public static final int EXTERNAL_X1_SWORD = 2;

    // X1 Hu-BASIC attribute 2 position
    public static final int TYPE_NAME_X1HU_HIDDEN = 0;
    public static final int TYPE_NAME_X1HU_READ_WRITE = 1;
    public static final int TYPE_NAME_X1HU_READ_ONLY = 2;
    public static final int TYPE_NAME_X1HU_PASSWORD = 3;

    // X1 Hu-BASIC attribute 2 value
    public static final int DATATYPE_X1HU_HIDDEN = 0x10;
    public static final int DATATYPE_X1HU_READ_WRITE = 0x20;
    public static final int DATATYPE_X1HU_READ_ONLY = 0x40;
    public static final int DATATYPE_X1HU_RESERVED = 0x08;
    public static final int DATATYPE_X1HU_MASK = (DATATYPE_X1HU_RESERVED | DATATYPE_X1HU_HIDDEN | DATATYPE_X1HU_READ_WRITE | DATATYPE_X1HU_READ_ONLY);

    public static final int DATATYPE_X1HU_PASSWORD_NONE = 0x20;
    public static final int DATATYPE_X1HU_PASSWORD_MASK = 0xff;

    /** X1 Hu-BASIC */
    public static final Map<String, Object> typeNameX1Hu1 = new HashMap<>() {{
        put("Bin", FILETYPE_X1HU_BINARY);
        put("Bas", FILETYPE_X1HU_BASIC);
        put("Asc(Hu)", FILETYPE_X1HU_ASCII);
        put("Asc(S-OS)", FILETYPE_X1HU_ASCII);
        put("Asc(Random Access)", FILETYPE_X1HU_ASCII);
        put("<DIR>", FILETYPE_X1HU_DIRECTORY);
    }};

    public static final Map<String, Object> typeNameX1Hu2 = new HashMap<>() {{
        put("Hidden", DATATYPE_X1HU_HIDDEN);
        put("Read After Write", DATATYPE_X1HU_READ_WRITE);
        put("Write Protected", DATATYPE_X1HU_READ_ONLY);
        put("Password", DATATYPE_X1HU_RESERVED);
    }};

    /** Directory data */
    private final DiskBasicDirData<DirectoryX1Hu> data = new DiskBasicDirData<>();

    @Override
    public boolean isSupported(int formatType) {
        return formatType == FORMAT_TYPE_X1HU;
    }

    @Override
    public void init(DiskBasic basic) throws IOException {
        super.init(basic);

        data.alloc(DirectoryX1Hu.class);
        externalAttr = basic.getVariousIntegerParam("DefaultAsciiType");
    }

    @Override
    public void init(DiskBasic basic, DiskImageSector sector, int sectorPos, byte[] data, int dataP) throws IOException {
        super.init(basic, sector, sectorPos, data, dataP);

        this.data.attach(DirectoryX1Hu.class, data, dataP);
        externalAttr = basic.getVariousIntegerParam("DefaultAsciiType");
    }

    @Override
    public void init(DiskBasic basic, int num, DiskBasicGroupItem groupItem, DiskImageSector sector, int sectorPos,
                     byte[] data, int dataP, SectorParam next, boolean[] unuse) throws IOException {
        super.init(basic, num, groupItem, sector, sectorPos, data, dataP, next, unuse);

        this.data.attach(DirectoryX1Hu.class, data, dataP);
        externalAttr = basic.getVariousIntegerParam("DefaultAsciiType");

        used(checkUsed(unuse[0]));

        // Calculate the number of groups
        calcFileSize();
    }

    /**
     * Item pointer setting
     *
     * @param num       Serial number
     * @param groupItem Data such as track number
     * @param sector    Sector
     * @param sectorPos Position of directory entry within sector
     * @param data      Directory item
     * @param next      [out] Next sector
     */
    @Override
    public void setData(int num, DiskBasicGroupItem groupItem, DiskImageSector sector, int sectorPos,
                        byte[] data, int dataPos, SectorParam next) throws IOException {
        super.setData(num, groupItem, sector, sectorPos, data, dataPos, next);

        this.data.attach(DirectoryX1Hu.class, data, dataPos);
    }

    /** File name position */
    @Override
    protected byte[] getFileNamePos(int num, int[] size, int[] len) {
        // X1 Hu
        if (num == 0) {
            size[0] = len[0] = data.data().name.length;
            return data.data().name;
        } else {
            size[0] = len[0] = 0;
            return null;
        }
    }

    /** File extension position */
    @Override
    protected byte[] getFileExtPos(int[] len) {
        len[0] = data.data().ext.length;
        return data.data().ext;
    }

    /** Attribute 1 (Type) */
    @Override
    public int getFileType1() {
        return basic.invertUint8(data.data().type) & 0xff;
    }

    /** Attribute 2 (Password/Flags) */
    @Override
    public int getFileType2() {
        return basic.invertUint8(data.data().password) & 0xff;
    }

    /** Set Attribute 1 */
    @Override
    protected void setFileType1(int val) {
        data.data().type = basic.invertUint8((byte) val);
    }

    /** Set Attribute 2 */
    @Override
    protected void setFileType2(int val) {
        data.data().password = basic.invertUint8((byte) val);
    }

    /** Check if item is used */
    @Override
    public boolean checkUsed(boolean unuse) {
        int type1 = getFileType1();
        return (type1 != 0 && type1 != 0xff);
    }

    /**
     * Directory item check
     *
     * @param last [in,out] Whether to end the check
     * @return Check OK
     */
    @Override
    public boolean check(boolean[] last) {
        if (!data.isValid()) return false;

        int type1 = getFileType1();
        boolean valid = true;
        // Attribute is invalid
        if (type1 != 0xff && (type1 & 0x08) != 0) {
            valid = false;
        }
        return valid;
    }

    /** Delete item */
    @Override
    public boolean delete() {
        // Deletion only puts a code at the beginning of the entry
        data.fill(basic.invertUint8(basic.getDeleteCode()), 1);
        used(false);
        return true;
    }

    /** Set file attributes */
    @Override
    public void setFileAttr(DiskBasicFileType fileType) {
        int fType = fileType.getType();
        if (fType == -1) return;

        int t1 = 0;
        int passwd = DATATYPE_X1HU_PASSWORD_NONE;
        if (fileType.getFormat() == basic.getFormatTypeNumber()) {
            t1 = fileType.getOrigin();
            passwd = ((t1 >> 8) & 0xff);
        } else {
            t1 = convToNativeType(fType, getFileType1());

            t1 &= ~DATATYPE_X1HU_MASK;
            t1 |= (fType & FILE_TYPE_HIDDEN_MASK.getValue()) != 0 ? DATATYPE_X1HU_HIDDEN : 0;
            t1 |= (fType & FILE_TYPE_READWRITE_MASK.getValue()) != 0 ? DATATYPE_X1HU_READ_WRITE : 0;
            t1 |= (fType & FILE_TYPE_READONLY_MASK.getValue()) != 0 ? DATATYPE_X1HU_READ_ONLY : 0;

            // password
            if ((fType & FILE_TYPE_ENCRYPTED_MASK.getValue()) != 0) {
                passwd = fileType.getOrigin();
            }
        }
        externalAttr = (t1 >> 16);
        t1 &= 0xff;

        setFileType1(t1);
        setFileType2(passwd);
    }

    /** Convert to native type */
    private int convToNativeType(int fileType, int val) {
        // X1 Hu
        val &= ~FILETYPE_X1HU_MASK;
        if ((fileType & (FILE_TYPE_MACHINE_MASK.getValue() | FILE_TYPE_BINARY_MASK.getValue())) == (FILE_TYPE_MACHINE_MASK.getValue() | FILE_TYPE_BINARY_MASK.getValue())) {
            // bin
            val |= FILETYPE_X1HU_BINARY;
        } else if ((fileType & FILE_TYPE_BASIC_MASK.getValue()) != 0) {
            // bas
            val |= FILETYPE_X1HU_BASIC;
        } else if ((fileType & FILE_TYPE_ASCII_MASK.getValue()) != 0) {
            // asc
            val |= FILETYPE_X1HU_ASCII;
        } else if ((fileType & FILE_TYPE_RANDOM_MASK.getValue()) != 0) {
            // random
            val |= (FILETYPE_X1HU_ASCII | (EXTERNAL_X1_RANDOM << 16));
        } else if ((fileType & FILE_TYPE_DIRECTORY_MASK.getValue()) != 0) {
            // sub directory
            val |= FILETYPE_X1HU_DIRECTORY;
        }
        return val;
    }

    /** Get file attributes */
    @Override
    public DiskBasicFileType getFileAttr() {
        int t1 = getFileType1();
        int val = 0;
        if ((t1 & FILETYPE_X1HU_BINARY) != 0) {
            val = FILE_TYPE_MACHINE_MASK.getValue(); // bin
            val |= FILE_TYPE_BINARY_MASK.getValue();
        } else if ((t1 & FILETYPE_X1HU_BASIC) != 0) {
            val = FILE_TYPE_BASIC_MASK.getValue(); // bas
            val |= FILE_TYPE_BINARY_MASK.getValue();
        } else if ((t1 & FILETYPE_X1HU_ASCII) != 0) {
            val = FILE_TYPE_ASCII_MASK.getValue(); // asc
        } else if ((t1 & FILETYPE_X1HU_DIRECTORY) != 0) {
            val = FILE_TYPE_DIRECTORY_MASK.getValue(); // sub directory
        }

        int passwd = getFileType2();
        if (passwd != DATATYPE_X1HU_PASSWORD_NONE) {
            val |= FILE_TYPE_ENCRYPTED_MASK.getValue();
        }
        return new DiskBasicFileType(basic.getFormatTypeNumber(), val, (externalAttr << 16) | (passwd << 8) | t1);
    }

    /** Get attribute string for display */
    @Override
    public String getFileAttrStr() {
        int t = (getFileType1() | (externalAttr << 16));
        String attr = rb.getString(Utils.keyAt(typeNameX1Hu1, getFileType1Pos(t)));

        for (int i = 0; i <= TYPE_NAME_X1HU_READ_ONLY; i++) {
            if ((t & (int) Utils.valueAt(typeNameX1Hu2, i)) != 0) {
                attr = attr + ", " + rb.getString(Utils.keyAt(typeNameX1Hu2, i));
            }
        }
        if (getFileType2() != DATATYPE_X1HU_PASSWORD_NONE) {
            attr = attr + ", " + rb.getString(Utils.keyAt(typeNameX1Hu2, TYPE_NAME_X1HU_PASSWORD));    // password
        }
        return attr;
    }

    /** Set file size */
    @Override
    public void setFileSize(int val) {
        groups.setSize(val);

        if ((getFileType1() & FILETYPE_X1HU_ASCII) != 0) {
            // File size in directory is 0
            data.data().fileSize = 0;
        } else {
            data.data().fileSize = basic.invertAndOrderUint16((short) val);
        }
    }

    /** Get file size */
    @Override
    public int getFileSize() {
        if ((getFileType1() & FILETYPE_X1HU_ASCII) != 0) {
            // Asc file case
            return groups.getSize();
        } else {
            return basic.invertAndOrderUint16(data.data().fileSize);
        }
    }

    /** Calculate file unit size and group count */
    @Override
    public void calcFileUnitSize(int fileUnitNum) throws IOException {
        if (!isUsed()) return;

        getUnitGroups(fileUnitNum, groups);
    }

    /** Get all groups for a directory */
    @Override
    public void getUnitGroups(int fileUnitNum, DiskBasicGroups groupItems) throws IOException {
        boolean rc = true;
        int calcFileSize = 0;
        int calcGroups = 0;

        // 8bit FAT
        int groupNum = getStartGroup(fileUnitNum);
        boolean working = true;
        int limit = basic.getFatEndGroup() + 1;
        while (working) {
            int nextGroup = type.getGroupNumber(groupNum);
            if (nextGroup == groupNum) {
                // Error if same position
                rc = false;
            } else if (nextGroup >= basic.getGroupFinalCode() && nextGroup <= basic.getGroupSystemCode()) {
                // Final group (0x80 - 0xff)
                basic.getNumsFromGroup(groupNum, nextGroup, basic.getSectorSize(), 0, groupItems);
                calcFileSize += (basic.getSectorSize() * (nextGroup - basic.getGroupFinalCode() + 1));
                calcGroups++;
                calcFileSize = recalcFileSize(groupItems, calcFileSize);
                working = false;
            } else if (nextGroup <= basic.getFatEndGroup()) {
                // Next group
                basic.getNumsFromGroup(groupNum, nextGroup, basic.getSectorSize(), 0, groupItems);
                calcFileSize += (basic.getSectorSize() * basic.getSectorsPerGroup());
                calcGroups++;
                groupNum = nextGroup;
                limit--;
            } else {
                // Group number is strange
                rc = false;
            }
            working = working && rc && (limit >= 0);
        }

        groupItems.setNums(calcGroups);
        groupItems.setSize(calcFileSize);
        groupItems.setSizePerGroup(basic.getSectorSize() * basic.getSectorsPerGroup());

        if (limit < 0) {
            // too large or infinit loop
            rc = false;
        }
    }

    /** Recalculate file size based on last sector */
    @Override
    public int recalcFileSize(DiskBasicGroups groupItems, int occupiedSize) throws IOException {
        if (groupItems.size() == 0) return occupiedSize;

        DiskBasicGroupItem litem = groupItems.last();
        DiskImageSector sector = basic.getSector(litem.track, litem.side, litem.sectorEnd);
        if (sector == null) return occupiedSize;

        int sectorSize = sector.getSectorSize();
        int remainSize = ((occupiedSize + sectorSize - 1) % sectorSize) + 1;
        remainSize = type.calcDataSizeOnLastSector(this, null, null, sector.getSectorBuffer(), 0, sectorSize, remainSize);

        occupiedSize = occupiedSize - sectorSize + remainSize;
        return occupiedSize;
    }

    /** Get file creation date */
    @Override
    public LocalDate getFileCreateDate(LocalDateTime tm) {
        byte[] date = new byte[data.data().date.length + 1];
        basic.invertMemory(data.data().date, data.data().date.length, date);
        return LocalDate.of(
                ((date[0] & 0xff) <= 0x99 ? ((date[0] & 0xf0) >> 4) * 10 + (date[0] & 0x0f) : -1) + // BCD
                        (tm.getYear() >= 0 && tm.getYear() < 80 ? 100 : 0),    // 2000 - 2079
                ((date[1] & 0xf0) >> 4) - 1,
                (date[2] & 0xff) <= 0x99 ? ((date[2] & 0xf0) >> 4) * 10 + (date[2] & 0x0f) : -1); // BCD
    }

    /** Get file creation time */
    @Override
    public LocalTime getFileCreateTime(LocalDateTime tm) {
        byte[] time = new byte[data.data().time.length + 1];
        basic.invertMemory(data.data().time, data.data().time.length, time);
        return LocalTime.of(
                (time[0] & 0xff) <= 0x99 ? ((time[0] & 0xf0) >> 4) * 10 + (time[0] & 0x0f) : -1, // BCD
                (time[1] & 0xff) <= 0x99 ? ((time[1] & 0xf0) >> 4) * 10 + (time[1] & 0x0f) : -1,// BCD
                0);
    }

    /** Get file creation date string */
    @Override
    public String getFileCreateDateStr() {
        LocalDateTime tm = LocalDateTime.now();
        LocalDate ld = getFileCreateDate(tm);
        return Utils.formatYMDStr(ld);
    }

    /** Get file creation time string */
    @Override
    public String getFileCreateTimeStr() {
        LocalDateTime tm = LocalDateTime.now();
        LocalTime lt = getFileCreateTime(tm);
        return Utils.formatHMStr(lt);
    }

    /** Set file creation date */
    @Override
    public void setFileCreateDate(LocalDateTime tm) {
        if (tm.getYear() < 0 || tm.getMonth().ordinal() < -1 || tm.getDayOfMonth() < 0) return;

        data.data().date[0] = (byte) (((tm.getYear() / 10) % 10) << 4 | (tm.getYear() % 10)); // year BCD
        data.data().date[1] = (byte) (((tm.getMonth().ordinal() + 1) & 0xf) << 4); // month
        data.data().date[2] = (byte) (((tm.getDayOfMonth() / 10) << 4) | (tm.getDayOfMonth() % 10)); // day BCD

        // Calculate day of week from date
        int week = 0;
        LocalDate date;
        String sdate = String.format("%04d-%02d-%02d",
                tm.getYear() + 1900,
                tm.getMonth().ordinal() + 1,
                tm.getDayOfMonth()
        );
        try {
            date = LocalDate.parse(sdate); // TODO check parsable
            week = date.getDayOfWeek().getValue();
        } catch (DateTimeParseException ignore) {
        }
        data.data().date[1] |= (byte) (week & 0xf); // day of week

        if (basic.isDataInverted()) invertMemory(data.data().date, data.data().date.length);
    }

    /** Set file creation time */
    @Override
    public void setFileCreateTime(LocalDateTime tm) {
        if (tm.getHour() < 0 || tm.getMinute() < 0) return;

        data.data().time[0] = (byte) (((tm.getHour() / 10) << 4) | (tm.getHour() % 10)); // hour BCD
        data.data().time[1] = (byte) (((tm.getMinute() / 10) << 4) | (tm.getMinute() % 10)); // minute BCD

        if (basic.isDataInverted()) invertMemory(data.data().time, data.data().time.length);
    }

    /** Has date/time */
    @Override
    public boolean hasCreateDateTime() {
        return true;
    }

    @Override
    public boolean hasCreateDate() {
        return true;
    }

    @Override
    public boolean hasCreateTime() {
        return true;
    }

    /** Can ignore date/time */
    @Override
    public int canIgnoreDateTime() {
        return DATETIME_ALL;
    }

    /** Has address */
    @Override
    public boolean hasAddress() {
        return true;
    }

    /** Get start address */
    @Override
    public int getStartAddress() {
        return basic.invertAndOrderUint16(data.data().loadAddress);
    }

    /** Get execute address */
    @Override
    public int getExecuteAddress() {
        return basic.invertAndOrderUint16(data.data().execAddress);
    }

    /** Set start address */
    @Override
    public void setStartAddress(int val) {
        data.data().loadAddress = basic.invertAndOrderUint16((short) val);
    }

    /** Set execute address */
    @Override
    public void setExecuteAddress(int val) {
        data.data().execAddress = basic.invertAndOrderUint16((short) val);
    }

    /** Directory item size */
    @Override
    public int getDataSize() {
        return data.getDataSize();
    }

    /** Get data structure */
    @Override
    public DirectoryX1Hu getData() {
        return data.data();
    }

    /** Copy data */
    @Override
    public byte[] getRawData() {
        return data.getRawData();
    }

    @Override
    protected void flushData() throws IOException {
        data.flush();
    }

    @Override
    public boolean copyData(byte[] val) {
        return data.copy(val, getDataSize());
    }

    /** Clear data */
    @Override
    public void clearData() {
        data.fill(basic.getDeleteCode(), getDataSize(), basic.isDataInverted(), 0);
    }

    /** Initial data (set unused) */
    @Override
    public void initialData() {
        if (!data.isValid()) return;
        data.fill(basic.getFillCodeOnDir(), getDataSize(), basic.isDataInverted(), 0);
    }

    /** Set start group number */
    @Override
    public void setStartGroup(int fileUnitNum, int val, int size) {
        // X1 Hu-BASIC
        data.data().startGroupH = basic.invertUint8((byte) ((val & 0xff0000) >> 16));
        data.data().startGroupL = basic.invertAndOrderUint16((short) (val & 0xffff));
    }

    /** Get start group number */
    @Override
    public int getStartGroup(int fileUnitNum) {
        // X1 Hu-BASIC
        return basic.invertUint8(data.data().startGroupH) << 16 | basic.invertAndOrderUint16(data.data().startGroupL);
    }

    /** Need check EOF code */
    @Override
    public boolean needCheckEofCode() {
        // EOF code is needed for Asc format
        return ((getFileType1() & FILETYPE_X1HU_ASCII) != 0 && (externalAttr != EXTERNAL_X1_RANDOM));
    }

    /** Get EOF code */
    @Override
    public byte getEofCode() {
        return externalAttr != EXTERNAL_X1_SWORD ? basic.getTextTerminateCode() : 0;
    }

    /** Recalculate file size on save */
    @Override
    public int recalcFileSizeOnSave(InputStream iStream, int fileSize) throws IOException {
        if (needCheckEofCode()) {
            // Check if file ends with termination code
            fileSize = checkEofCode(iStream, fileSize);
        }
        return fileSize;
    }

    /** Determine original type from file name */
    @Override
    public int convOriginalTypeFromFileName(String filename) {
        int t1 = 0;
        // Set attribute by extension
        MyAttribute attr = findUpperCase(basic.getAttributesByExtension(), Utils.getExt(filename));
        if (attr != null) {
            t1 = convToNativeType(attr.getType(), t1);
            t1 |= (externalAttr << 16);
        } else {
            t1 = FILETYPE_X1HU_ASCII;
        }

        // No password
        t1 |= (DATATYPE_X1HU_PASSWORD_NONE << 8);
        return t1;
    }

    //
    // Dialog methods
    //

    /** Get file type 1 position in list */
    public int getFileType1Pos(int nativeType) {
        int val = 0;
        if ((nativeType & FILETYPE_X1HU_BINARY) != 0) {
            val = TYPE_NAME_X1HU_BINARY;         // bin
        } else if ((nativeType & FILETYPE_X1HU_BASIC) != 0) {
            val = TYPE_NAME_X1HU_BASIC;          // bas
        } else if ((nativeType & FILETYPE_X1HU_ASCII) != 0) {
            val = switch (nativeType >> 16) {
                case EXTERNAL_X1_RANDOM -> TYPE_NAME_X1HU_RANDOM; // asc
                case EXTERNAL_X1_SWORD -> TYPE_NAME_X1HU_SWORD;  // asc
                default -> TYPE_NAME_X1HU_ASCII;  // asc
            };
        } else if ((nativeType & FILETYPE_X1HU_DIRECTORY) != 0) {
            val = TYPE_NAME_X1HU_DIRECTORY;     // sub directory
        }
        return val;
    }

    /** Set internal data for property dialog */
    @Override
    public void setInternalDataInAttrDialog(KeyValArray vals) {
        vals.add("inverted", basic.isDataInverted());

        vals.add("TYPE", data.data().type, basic.isDataInverted());
        vals.add("NAME", data.data().name, data.data().name.length, basic.isDataInverted());
        vals.add("EXT", data.data().ext, data.data().ext.length, basic.isDataInverted());
        vals.add("PASSWORD", data.data().password, basic.isDataInverted());
        vals.add("FILE_SIZE", data.data().fileSize, basic.isBigEndian(), basic.isDataInverted());
        vals.add("LOAD_ADDR", data.data().loadAddress, basic.isBigEndian(), basic.isDataInverted());
        vals.add("EXEC_ADDR", data.data().execAddress, basic.isBigEndian(), basic.isDataInverted());
        vals.add("DATE", data.data().date, data.data().date.length, basic.isDataInverted());
        vals.add("TIME", data.data().time, data.data().time.length, basic.isDataInverted());
        vals.add("START_GROUP_H", data.data().startGroupH, basic.isDataInverted());
        vals.add("START_GROUP_L", data.data().startGroupL, basic.isBigEndian(), basic.isDataInverted());
    }
}
