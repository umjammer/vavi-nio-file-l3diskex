/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt.diritem;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
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
import l3diskex.basicfmt.diritem.DiskBasicDirItemHU68K.DirectoryHu68k;
import l3diskex.basicfmt.diritem.DiskBasicDirItemLOSA.DirectoryLosa;
import l3diskex.basicfmt.diritem.DiskBasicDirItemMSDOS.DirectoryMs;
import l3diskex.basicfmt.diritem.DiskBasicDirItemMSDOS.DiskBasicDirItemVFAT.DirectoryMsLfn;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;
import vavi.io.SeekableDataInputStream;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;

import static l3diskex.Parambase.MyAttributes.findUpperCase;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_ARCHIVE_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_ASCII_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_DIRECTORY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_HIDDEN_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_READONLY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_SYSTEM_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_VOLUME_MASK;
import static l3diskex.basicfmt.type.DiskBasicTypeMSDOS.FORMAT_TYPE_CDOS2;
import static l3diskex.basicfmt.type.DiskBasicTypeMSDOS.FORMAT_TYPE_MSDOS;


/** Directory 1 item MS-DOS */
public class DiskBasicDirItemMSDOS extends DiskBasicDirItem<DirectoryMs> {

    private static final Logger logger = System.getLogger(DiskBasicDirItemMSDOS.class.getName());

    private static final ResourceBundle rb = ResourceBundle.getBundle("messages");

    /**
     * Directory entry MS-DOS FAT (32bytes)
     */
    @Serdes(bigEndian = false)
    public static class DirectoryMsDos implements Directory {

        @Element(sequence = 1)
        public byte[] name = new byte[8];
        @Element(sequence = 2)
        public byte[] ext = new byte[3];
        @Element(sequence = 3)
        public byte type;
        @Element(sequence = 4)
        public byte ntRes;
        @Element(sequence = 5)
        public byte cTimeTenth;
        @Element(sequence = 6)
        public short cTime;
        @Element(sequence = 7)
        public short cDate;
        @Element(sequence = 8)
        public short aDate;
        @Element(sequence = 9)
        public short startGroupHi;
        @Element(sequence = 10)
        public short wTime;
        @Element(sequence = 11)
        public short wDate;
        @Element(sequence = 12)
        public short startGroup;
        @Element(sequence = 13)
        public int fileSize;

        public static final int SIZE = 32;
    }

    /**
     * Directory entry MS-DOS compatible (32bytes)
     */
    @Serdes
    public static class DirectoryMs implements Directory {

        // union emulation

        DirectoryMsDos msdos;
        DirectoryMsLfn mslfn;
        DirectoryHu68k hu68k;
        DirectoryLosa losa;

        public DirectoryMsDos msdos() {
            if (msdos == null) {
                try {
                    msdos = new DirectoryMsDos();
                    Serdes.Util.deserialize(new ByteArrayInputStream(raw), msdos);
                } catch (Exception e) {
                    logger.log(Level.ERROR, e.getMessage(), e);
                }
            }
            return msdos;
        }

        public DirectoryMsLfn mslfn() {
            if (mslfn == null) {
                try {
                    mslfn = new DirectoryMsLfn();
                    Serdes.Util.deserialize(new ByteArrayInputStream(raw), mslfn);
                } catch (Exception e) {
                    logger.log(Level.ERROR, e.getMessage(), e);
                }
            }
            return mslfn;
        }

        public DirectoryHu68k hu68k() {
            if (hu68k == null) {
                try {
                    hu68k = new DirectoryHu68k();
                    Serdes.Util.deserialize(new ByteArrayInputStream(raw), hu68k);
                } catch (Exception e) {
                    logger.log(Level.ERROR, e.getMessage(), e);
                }
            }
            return hu68k;
        }

        public DirectoryLosa losa() {
            if (losa == null) {
                try {
                    losa = new DirectoryLosa();
                    Serdes.Util.deserialize(new ByteArrayInputStream(raw), losa);
                } catch (Exception e) {
                    logger.log(Level.ERROR, e.getMessage(), e);
                }
            }
            return losa;
        }

        // real data

        @Element(sequence = 1)
        byte[] raw = new byte[SIZE];

        public static final int SIZE = 32;
    }

    /// MS-DOS attribute names

    public static final int TYPE_NAME_MS_READ_ONLY = 0;
    public static final int TYPE_NAME_MS_HIDDEN = 1;
    public static final int TYPE_NAME_MS_SYSTEM = 2;
    public static final int TYPE_NAME_MS_VOLUME = 3;
    public static final int TYPE_NAME_MS_DIRECTORY = 4;
    public static final int TYPE_NAME_MS_ARCHIVE = 5;
    public static final int TYPE_NAME_MS_LFN = 6;
    public static final int TYPE_NAME_MS_END = 7;

    public static final int FILETYPE_MASK_MS_READ_ONLY = 0x01;
    public static final int FILETYPE_MASK_MS_HIDDEN = 0x02;
    public static final int FILETYPE_MASK_MS_SYSTEM = 0x04;
    public static final int FILETYPE_MASK_MS_VOLUME = 0x08;
    public static final int FILETYPE_MASK_MS_DIRECTORY = 0x10;
    public static final int FILETYPE_MASK_MS_ARCHIVE = 0x20;
    public static final int FILETYPE_MASK_MS_LFN = 0x0f; // long file name

    /** MS-DOS (MSX-DOS) */
    static final Map<String, Object> typeNameMS = new LinkedHashMap<>() {{
        put("Read Only", FILE_TYPE_READONLY_MASK.getValue());
        put("Hidden", FILE_TYPE_HIDDEN_MASK.getValue());
        put("Sys", FILE_TYPE_SYSTEM_MASK.getValue());
        put("<VOL>", FILE_TYPE_VOLUME_MASK.getValue());
        put("<DIR>", FILE_TYPE_DIRECTORY_MASK.getValue());
        put("Arc", FILE_TYPE_ARCHIVE_MASK.getValue());
        put("(LFN)", FILE_TYPE_READONLY_MASK.getValue() | FILE_TYPE_HIDDEN_MASK.getValue() | FILE_TYPE_SYSTEM_MASK.getValue() | FILE_TYPE_VOLUME_MASK.getValue());
    }};

    /** MS-DOS (MSX-DOS) */
    static final Map<String, Object> typeNameMSl = new LinkedHashMap<>() {{
        put("Read Only", FILE_TYPE_READONLY_MASK.getValue());
        put("Hidden", FILE_TYPE_HIDDEN_MASK.getValue());
        put("System", FILE_TYPE_SYSTEM_MASK.getValue());
        put("Volume Label", FILE_TYPE_VOLUME_MASK.getValue());
        put("Directory", FILE_TYPE_DIRECTORY_MASK.getValue());
        put("Archive", FILE_TYPE_ARCHIVE_MASK.getValue());
        put("int File Name", FILE_TYPE_READONLY_MASK.getValue() | FILE_TYPE_HIDDEN_MASK.getValue() | FILE_TYPE_SYSTEM_MASK.getValue() | FILE_TYPE_VOLUME_MASK.getValue());
    }};

    //
    //
    //

    /** Directory data */
    protected final DiskBasicDirData<DirectoryMs> data = new DiskBasicDirData<>();

    @Override
    public boolean isSupported(int formatType) {
        return formatType == FORMAT_TYPE_CDOS2;
    }

    //
    @Override
    public void init(DiskBasic basic) throws IOException {
        super.init(basic);

        data.alloc(DirectoryMs.class);
    }

    //
    @Override
    public void init(DiskBasic basic, DiskImageSector sector, int sectorPos, byte[] data, int dataPos) throws IOException {
        super.init(basic, sector, sectorPos, data, dataPos);

        this.data.attach(DirectoryMs.class, data, dataPos);
    }

    //
    @Override
    public void init(DiskBasic basic, int num, DiskBasicGroupItem groupItem, DiskImageSector sector, int sectorPos,
                     byte[] data, int dataPos, SectorParam next, boolean[] unuse) throws IOException {
        super.init(basic, num, groupItem, sector, sectorPos, data, dataPos, next, unuse);

        // MS-DOS
        this.data.attach(DirectoryMs.class, data, dataPos);
        used(checkUsed(unuse[0]));
        visible((getFileType1() & FILETYPE_MASK_MS_LFN) != FILETYPE_MASK_MS_LFN);
        unuse[0] = (unuse[0] || (this.data.data().msdos().name[0] == 0));

        // Calculate the number of groups
        calcFileSize();

        // Do not display current or parent directory in tree
        String name = getFileNamePlainStr();
        visibleOnTree(!(isDirectory() && (name.equals(".") || name.equals(".."))));
    }

    /** Set pointer to item */
    @Override
    public void setData(int num, DiskBasicGroupItem groupItem, DiskImageSector sector, int sectorPos,
                        byte[] data, int dataPos, SectorParam next) throws IOException {
        super.setData(num, groupItem, sector, sectorPos, data, dataPos, next);

        this.data.attach(DirectoryMs.class, data, dataPos);
    }

    /** Returns position where file name is stored */
    @Override
    protected byte[] getFileNamePos(int num, int[] size, int[] len) {
        // MS-DOS
        int t1 = getFileType1();
        if (num == 0) {
            size[0] = len[0] = data.data().msdos().name.length;
            return data.data().msdos().name;
        } else if (num == 1) {
            if ((t1 & FILETYPE_MASK_MS_VOLUME) != 0) {
                // For volume labels, the extension is also part of the label name
                size[0] = len[0] = data.data().msdos().ext.length;
                return data.data().msdos().ext;
            }
        }
        size[0] = len[0] = 0;
        return null;
    }

    /** Returns position where extension is stored */
    @Override
    protected byte[] getFileExtPos(int[] len) {
        byte[] p = null;
        len[0] = 0;
        // Make volume labels have no extension
        if ((getFileType1() & FILETYPE_MASK_MS_VOLUME) == 0) {
            len[0] = data.data().msdos().ext.length;
            p = data.data().msdos().ext;
        }
        return p;
    }

    /** Returns attribute 1 */
    @Override
    public int getFileType1() {
        return data.data().msdos().type & 0xff;
    }

    /** Set attribute 1 */
    @Override
    public void setFileType1(int val) {
        data.data().msdos().type = (byte) (val & 0xff);
    }

    /** Whether it is a used item */
    @Override
    public boolean checkUsed(boolean unuse) {
        return data.data().msdos().name[0] != 0 && (data.data().msdos().name[0] & 0xff) != 0xe5;
    }

    /** Set file name */
    @Override
    public void setNativeName(byte[] filename, int size, int length) {
        if (length > 0) {
            // 0xe5 is a delete code, so convert to 0x05 (Shift JIS or other 2-byte characters, etc.)
            if ((filename[0] & 0xff) == 0xe5) filename[0] = 0x05;
        }

        byte[] n;
        int nl = 0;
        int[] s = {0}, l = {0};

        int num = 0;
        do {
            s[0] = l[0] = 0;
            n = getFileNamePos(num, s, l);
            if (n == null || s[0] == 0) {
                break;
            }
            if (num > 0) {
                int pl = nl;
                int ps = s[0];
                if (nl < length) {
                    pl = length;
                    ps = s[0] + nl - length;
                }
                if (s[0] + nl > length) {
                    Arrays.fill(filename, pl, pl + ps, (byte) 0);
                }
            }

            if (s[0] > size) s[0] = size;
            System.arraycopy(filename, nl, n, 0, s[0]);

            nl += s[0];
            size -= s[0];
            num++;
        } while (num <= 1);
    }

    /** Get file name */
    public void getNativeName(byte[] filename, int[] size, int[] length) {
        byte[] n;
        int nl = 0;
        int num = 0;
        do {
            int[] s = {0}, l = {0};

            n = getFileNamePos(num, s, l);
            if (n == null || s[0] == 0) {
                break;
            }
            if (s[0] > size[0]) s[0] = size[0];
            System.arraycopy(n, 0, filename, nl, s[0]);

            nl += s[0];
            size[0] -= s[0];
            num++;
        } while (num <= 1);

        if (nl > 0) {
            // Convert 0x05 to 0xe5 (Shift JIS or other 2-byte characters, etc.)
            if (filename[0] == 0x05) filename[0] = (byte) 0xe5;
        }

        length[0] = nl;
    }

    /** Returns attribute string (for file list display) */
    protected void getFileAttrStrSub(int fType, StringBuilder attr) {
        for (int i = 0; i <= TYPE_NAME_MS_ARCHIVE; i++) {
            if ((fType & (int) Utils.valueAt(typeNameMS, i)) != 0) {
                if (!attr.isEmpty()) attr.append(", ");
                attr.append(rb.getString(Utils.keyAt(typeNameMS, i)));
            }
        }
    }

    /** Convert date */
    protected static LocalDate convDateToTm(short date) {
        int yy = ((date & 0xfe00) >> 9) + 80;
        int mm = ((date & 0x01e0) >> 5);
        return LocalDate.of(
                yy,
                mm < 1 || mm > 12 ? 1 : mm, // 1-12 for DateTime month
                (date & 0x001f) + 1);
    }

    /** Convert time */
    protected static LocalTime convTimeToTm(short time) {
        int hh = (time & 0xf800) >> 11;
        int mm = (time & 0x07e0) >> 5;
        int ss = (time & 0x001f) << 1;
        return LocalTime.of(
                hh < 0 || hh > 23 ? 0 : hh,
                mm < 0 || mm > 59 ? 0 : mm,
                ss < 0 || ss > 59 ? 0 : ss);
    }

    /** Convert to date */
    protected static short convTmToDate(LocalDateTime tm) {
        int yy = tm.getYear();
        int month = tm.getMonth().ordinal() - 1; // DateTime month is 0-11
        int date = (
                (((yy - 80) & 0x7f) << 9) |
                        ((month & 0xf) << 5) |
                        ((tm.getDayOfMonth() - 1) & 0x1f)
        ) & 0xffff;
        return (short) date;
    }

    /** Convert to time */
    protected static short convTmToTime(LocalDateTime tm) {
        int time = (
                ((tm.getHour() & 0x1f) << 11) |
                        ((tm.getMinute() & 0x3f) << 5) |
                        ((tm.getSecond() & 0x3f) >> 1)
        ) & 0xffff;
        return (short) time;
    }

    /** Check directory item */
    @Override
    public boolean check(boolean[] last) {
        if (!data.isValid()) return false;

        boolean valid = true;
        // File size exceeds 16MB
        if (checkUsed(false) && data.data().msdos().fileSize > 0xff_ffff) {
            valid = false;
        }
        return valid;
    }

    /** Whether item can be deleted */
    @Override
    public boolean isDeletable() {
        // ".", ".." are not allowed
        boolean valid = true;
        String name = getFileNamePlainStr();
        if (name.equals(".") || name.equals("..")) {
            valid = false;
        }
        return valid;
    }

    /** Delete */
    @Override
    public boolean delete() {
        // Deletion is simply putting a code at the beginning of the entry (0xe5)
        data.fill(basic.invertUint8(basic.getDeleteCode()), 1);
        used(false);
        return true;
    }

    /** Whether file name can be edited */
    @Override
    public boolean isFileNameEditable() {
        // ".", ".." are not allowed
        return isDeletable();
    }

    /** Whether item can be loaded or exported */
    @Override
    public boolean isLoadable() {
        // Volume label is not allowed
        int t1 = getFileType1();
        boolean valid = ((t1 & (FILETYPE_MASK_MS_DIRECTORY | FILETYPE_MASK_MS_VOLUME)) != FILETYPE_MASK_MS_VOLUME);
        // ".", ".." are not allowed
        valid &= isDeletable();
        return valid;
    }

    /** Whether item can be copied (DnD internally) */
    @Override
    public boolean isCopyable() {
        // ".", ".." are not allowed
        return isDeletable();
    }

    /** Whether item can be overwritten */
    @Override
    public boolean isOverWritable() {
        // Directory and volume label are not allowed
        int t1 = getFileType1();
        boolean valid = ((t1 & (FILETYPE_MASK_MS_DIRECTORY | FILETYPE_MASK_MS_VOLUME)) == 0);
        // ".", ".." are not allowed
        valid &= isDeletable();
        return valid;
    }

    /** Set attribute */
    @Override
    public void setFileAttr(DiskBasicFileType fileType) {
        int ftype = fileType.getType();
        if (ftype == -1) return;

        // MS-DOS
        setFileType1((ftype & 0xff00) >> 8);
    }

    /** Returns attribute */
    @Override
    public DiskBasicFileType getFileAttr() {
        int t1 = getFileType1();

        if (isValidDirectory() && (t1 & FILETYPE_MASK_MS_DIRECTORY) == 0) { // TODO ad-hoc if this is a root directory set directory type bit
            t1 |= FILETYPE_MASK_MS_DIRECTORY;
        }

        return new DiskBasicFileType(basic.getFormatTypeNumber(), t1 << 8, t1);
    }

    /** Returns attribute string (for file list screen display) */
    @Override
    public String getFileAttrStr() {
        StringBuilder attr = new StringBuilder();
        int fType = getFileAttr().getType();
        // MS-DOS
        getFileAttrStrSub(fType, attr);
        if (attr.isEmpty()) {
            attr.append("---");
        }
        return attr.toString();
    }

    /** Set file size */
    @Override
    public void setFileSize(int val) {
        groups.setSize(val);
        data.data().msdos().fileSize = val;
    }

    /** Returns file size */
    @Override
    public int getFileSize() {
        int val = data.data().msdos().fileSize;
        return val;
    }

    /**
     * Set directory size
     * MS-DOS directories have a size of 0 in the entry
     */
    @Override
    public void setDirectorySize(int val) {
        setFileSize(0);
    }

    /** Calculate file size and number of groups */
    @Override
    public void calcFileUnitSize(int fileUnitNum) throws IOException {
        if (!isUsed()) return;

        getUnitGroups(fileUnitNum, groups);
    }

    /** Obtain all groups of specified directory */
    @Override
    public void getUnitGroups(int fileUnitNum, DiskBasicGroups groupItems) throws IOException {
        int calcGroups = 0;
        int calcFileSize = getFileSize();

        // 12bit FAT
        boolean rc = true;
        int groupNum = getStartGroup(fileUnitNum);
        boolean working = groupNum >= 2;
        int remain = calcFileSize > 0 ? calcFileSize : 0x7ff_ffff;
        int limit = basic.getFatEndGroup() + 1;
        while (working) {
            int nextGroup = type.getGroupNumber(groupNum);
            if (nextGroup == groupNum) {
                // Error if at the same position
                rc = false;
            } else if (nextGroup >= 0xff8) {
                // Final group
                working = false;
            } else if (nextGroup > basic.getFatEndGroup()) {
                // Invalid group number
                rc = false;
            }
            if (rc) {
                basic.getNumsFromGroup(groupNum, nextGroup, basic.getSectorSize(), remain, groupItems);
                //int fileSize = basic.getSectorSize() * basic.getSectorsPerGroup();
                remain -= basic.getSectorSize() * basic.getSectorsPerGroup();
                calcGroups++;
                groupNum = nextGroup;
                limit--;
            }
            working = working && rc && (limit >= 0);
        }

        groupItems.setNums(calcGroups);
        // If original file size is 0, store size calculated from the number of groups
        groupItems.setSize(calcFileSize > 0 ? calcFileSize : basic.getSectorSize() * basic.getSectorsPerGroup() * calcGroups);
        groupItems.setSizePerGroup(basic.getSectorSize() * basic.getSectorsPerGroup());

        if (limit < 0) {
            // too large or infinite loop
            rc = false;
        }
    }

    /** Set the first group number */
    @Override
    public void setStartGroup(int fileUnitNum, int val, int size) {
        // MS-DOS
        data.data().msdos().startGroup = (short) val;
    }

    /** Returns the first group number */
    @Override
    public int getStartGroup(int fileUnitNum) {
        // MS-DOS
        return data.data().msdos().startGroup & 0xffff;
    }

    /** Whether item has modify date and time */
    @Override
    public boolean hasModifyDateTime() {
        return true;
    }

    @Override
    public boolean hasModifyDate() {
        return true;
    }

    @Override
    public boolean hasModifyTime() {
        return true;
    }

    /**
     * Get modify date
     *
     * @return date
     */
    @Override
    public LocalDate getFileModifyDate(LocalDateTime tm) {
        // MS-DOS
        short wdate = data.data().msdos().wDate;
        return convDateToTm(wdate);
    }

    /**
     * Get modify time
     *
     * @return time
     */
    @Override
    public LocalTime getFileModifyTime(LocalDateTime tm) {
        // MS-DOS
        short wtime = data.data().msdos().wTime;
        return convTimeToTm(wtime);
    }

    /** Returns modify date string */
    @Override
    public String getFileModifyDateStr() {
        LocalDateTime tm = LocalDateTime.now();
        LocalDate ld = getFileModifyDate(tm);
        return Utils.formatYMDStr(ld);
    }

    /** Returns modify time string */
    @Override
    public String getFileModifyTimeStr() {
        LocalDateTime tm = LocalDateTime.now();
        LocalTime lt = getFileModifyTime(tm);
        return Utils.formatHMSStr(lt);
    }

    /** Set modify date */
    @Override
    public void setFileModifyDate(LocalDateTime tm) {
        if (tm.getYear() >= 0 && tm.getMonth().ordinal() >= -1) {
            short wdate = convTmToDate(tm);
            data.data().msdos().wDate = wdate;
        }
    }

    /** Set modify time */
    @Override
    public void setFileModifyTime(LocalDateTime tm) {
        if (tm.getHour() >= 0 && tm.getMinute() >= 0) {
            short wtime = convTmToTime(tm);
            data.data().msdos().wTime = wtime;
        }
    }

    /** Title name of modify date (for dialog) */
    @Override
    public String getFileModifyDateTimeTitle() {
        return "Updated Date";
    }

    /** Returns display order of date and time (for dialog) */
    @Override
    public int getFileDateTimeOrder(int idx) {
        return idx <= 1 ? 1 - idx : idx;
    }

    /** Returns date and time (for file list) */
    @Override
    public String getFileDateTimeStr() {
        return getFileModifyDateTimeStr();
    }

    /** Size of directory item */
    @Override
    public int getDataSize() {
        return data.getDataSize();
    }

    /** Returns item */
    @Override
    public DirectoryMs getData() {
        return data.data();
    }

    /** Copy item */
    @Override
    public boolean copyData(byte[] val) {
        return data.copy(val, getDataSize());
    }

    /** Clear directory: during creation of a new file */
    @Override
    public void clearData() {
        data.fill((byte) 0, getDataSize(), basic.isDataInverted(), 0);
    }

    /** Determine attribute from file name */
    @Override
    public int convFileTypeFromFileName(String filename) {
        return FILE_TYPE_ARCHIVE_MASK.getValue();
    }

    /** Whether EOF code needs to be checked */
    @Override
    public boolean needCheckEofCode() {
        // Determine whether it is a text file by extension
        boolean rc = false;
        MyAttribute attr = findUpperCase(basic.getAttributesByExtension(), getFileExtPlainStr());
        if (attr != null) {
            rc = ((attr.getType() & FILE_TYPE_ASCII_MASK.getValue()) != 0);
        }
        return rc;
    }

    /** Recalculate file size on save: when EOF code is needed, etc. */
    @Override
    public int recalcFileSizeOnSave(InputStream iStream, int fileSize) throws IOException {
        if (needCheckEofCode()) {
            // Whether there is a termination character at the end of the file
            int curr_pos = (int) ((SeekableDataInputStream) iStream).position();
            ((SeekableDataInputStream) iStream).position(iStream.available());
            if (iStream.read() != basic.getTextTerminateCode()) {
                fileSize++;
            }
            ((SeekableDataInputStream) iStream).position(curr_pos);
        }
        return fileSize;
    }

    //
    // For dialog
    //

    /** Set internal data displayed in properties */
    @Override
    public void setInternalDataInAttrDialog(KeyValArray vals) {
        vals.add("NAME", data.data().msdos().name, data.data().msdos().name.length);
        vals.add("EXT", data.data().msdos().ext, data.data().msdos().ext.length);
        vals.add("TYPE", data.data().msdos().type & 0xff);
        vals.add("NTRES", data.data().msdos().ntRes & 0xff);
        vals.add("CTIME_TENTH", data.data().msdos().cTimeTenth & 0xff);
        vals.add("CTIME", data.data().msdos().cTime & 0xffff);
        vals.add("CDATE", data.data().msdos().cDate & 0xffff);
        vals.add("ADATE", data.data().msdos().aDate & 0xffff);
        vals.add("START_GROUP_HI", data.data().msdos().startGroupHi & 0xffff);
        vals.add("WTIME", data.data().msdos().wTime & 0xffff);
        vals.add("WDATE", data.data().msdos().wDate & 0xffff);
        vals.add("START_GROUP", data.data().msdos().startGroup & 0xffff);
        vals.add("FILE_SIZE", data.data().msdos().fileSize);
    }

    ///
    ///
    ///

    /** Directory 1 item MS-DOS VFAT */
    public static class DiskBasicDirItemVFAT extends DiskBasicDirItemMSDOS {

        /**
         * Directory entry MS-DOS LFN (32bytes)
         */
        @Serdes(bigEndian = false)
        public static class DirectoryMsLfn implements Directory {

            @Element(sequence = 1)
            public byte order;
            @Element(sequence = 2)
            public byte[] name = new byte[10];
            @Element(sequence = 3)
            public byte type;
            @Element(sequence = 4)
            public byte type2;
            @Element(sequence = 5)
            public byte checksum;
            @Element(sequence = 6)
            public byte[] name2 = new byte[12];
            @Element(sequence = 7)
            public short dummyGroup;
            @Element(sequence = 8)
            public byte[] name3 = new byte[4];

            public static final int SIZE = 32;
        }

        @Override
        public boolean isSupported(int formatType) {
            return formatType == FORMAT_TYPE_MSDOS;
        }

        @Override
        public void init(DiskBasic basic) throws IOException {
            super.init(basic);
        }

        @Override
        public void init(DiskBasic basic, DiskImageSector sector, int sectorPos,
                         byte[] data, int dataPos) throws IOException {
            super.init(basic, sector, sectorPos, data, dataPos);
        }

        @Override
        public void init(DiskBasic basic, int num, DiskBasicGroupItem groupItem,
                         DiskImageSector sector, int sectorPos,
                         byte[] data, int dataPos,
                         SectorParam next, boolean[] unuse) throws IOException {
            super.init(basic, num, groupItem, sector, sectorPos, data, dataPos, next, unuse);
        }

        /** Returns position where file name is stored */
        @Override
        protected byte[] getFileNamePos(int num, int[] size, int[] len) {
            // MS-DOS
            int t1 = getFileType1();
            DirectoryMs data = this.data.data();

            if (num == 0) {
                if ((t1 & FILETYPE_MASK_MS_LFN) == FILETYPE_MASK_MS_LFN) {
                    // Long file name
                    size[0] = len[0] = data.mslfn().name.length;
                    return data.mslfn().name;
                } else {
                    size[0] = len[0] = data.msdos().name.length;
                    return data.msdos().name;
                }
            } else if (num == 1) {
                if ((t1 & FILETYPE_MASK_MS_LFN) == FILETYPE_MASK_MS_LFN) {
                    // Long file name
                    size[0] = len[0] = data.mslfn().name2.length;
                    return data.mslfn().name2;
                } else if ((t1 & FILETYPE_MASK_MS_VOLUME) != 0) {
                    // For volume labels, the extension is also part of the label name
                    size[0] = len[0] = data.msdos().ext.length;
                    return data.msdos().ext;
                }
            } else if (num == 2) {
                if ((t1 & FILETYPE_MASK_MS_LFN) == FILETYPE_MASK_MS_LFN) {
                    // Long file name
                    size[0] =len[0] =  data.mslfn().name3.length;
                    return data.mslfn().name3;
                }
            }
            size[0] = len[0] = 0;
            return null;
        }

        /** Set file name */
        protected void setNativeName(byte[] filename, int[] size, int length) {
            if (length > 0) {
                // 0xe5 is a delete code, so convert to 0x05 (Shift JIS or other 2-byte characters, etc.)
                if ((filename[0] & 0xff) == 0xe5) filename[0] = 0x05;
            }

            byte[] n;
            int nl = 0;
            int[] s = {0}, l = {0};

            int num = 0;
            do {
                s[0] = l[0] = 0;
                n = getFileNamePos(num, s, l);
                if (n == null || s[0] == 0) {
                    break;
                }
                if (num > 0) {
                    int pl = nl;
                    int ps = s[0];
                    if (nl < length) {
                        pl = length;
                        ps = s[0] + nl - length;
                    }
                    Arrays.fill(filename, pl, pl + ps, (byte) 0);
                }

                if (s[0] > size[0]) s[0] = size[0];
                System.arraycopy(filename, nl, n, 0, s[0]);

                nl += s[0];
                size[0] -= s[0];
                num++;
            } while (num <= 4);
        }

        /** Get file name */
        @Override
        protected void getNativeName(byte[] filename, int size, int[] length) {
            byte[] n = null;
            int nl = 0;
            int num = 0;
            do {
                int[] s = {0}, l = {0};

                n = getFileNamePos(num, s, l);
                if (n == null || s[0] == 0) {
                    break;
                }
                if (s[0] > size) s[0] = size;
                System.arraycopy(n, 0, filename, nl, s[0]);

                nl += s[0];
                size -= s[0];
                num++;
            } while (num <= 4);

            if (nl > 0) {
                // Convert 0x05 to 0xe5 (Shift JIS or other 2-byte characters, etc.)
                if (filename[0] == 0x05) filename[0] = (byte) 0xe5;
            }

            length[0] = nl;
        }

        /** Check directory item */
        @Override
        public boolean check(boolean[] last) {
            if (!data.isValid()) return false;

            boolean valid = true;
            // File size exceeds 16MB
            if (checkUsed(false) && (data.data().msdos().type & FILETYPE_MASK_MS_LFN) != FILETYPE_MASK_MS_LFN && data.data().msdos().fileSize > 0xff_ffff) {
                valid = false;
            }
            return valid;
        }

        /** Whether item can be deleted */
        @Override
        public boolean isDeletable() {
            boolean valid = true;
            int t1 = getFileType1();
            if ((t1 & FILETYPE_MASK_MS_LFN) == FILETYPE_MASK_MS_LFN) {
                valid = false;
            } else if ((t1 & FILETYPE_MASK_MS_DIRECTORY) != 0) {
                String name = getFileNamePlainStr();
                if (name.equals(".") || name.equals("..")) {
                    // Directories ".", ".." cannot be deleted
                    valid = false;
                }
            }
            return valid;
        }

        /** Whether file name can be edited */
        @Override
        public boolean isFileNameEditable() {
            boolean valid = true;
            int t1 = getFileType1();
            if ((t1 & FILETYPE_MASK_MS_LFN) == FILETYPE_MASK_MS_LFN) {
                valid = false;
            } else if ((t1 & FILETYPE_MASK_MS_DIRECTORY) != 0) {
                String name = getFileNamePlainStr();
                if (name.equals(".") || name.equals("..")) {
                    // Directories ".", ".." cannot be edited
                    valid = false;
                }
            }
            return valid;
        }

        /** Set attribute */
        @Override
        public void setFileAttr(DiskBasicFileType fileType) {
            int fType = fileType.getType();
            if (fType == -1) return;

            // MS-DOS
            setFileType1((fType & 0xff00) >> 8);
        }

        /** Returns attribute */
        @Override
        public DiskBasicFileType getFileAttr() {
            int t1 = getFileType1();

            if (isValidDirectory() && (t1 & FILETYPE_MASK_MS_DIRECTORY) == 0) { // TODO ad-hoc if this is a root directory set directory type bit
                t1 |= FILETYPE_MASK_MS_DIRECTORY;
            }

            return new DiskBasicFileType(basic.getFormatTypeNumber(), t1 << 8, t1);
        }

        /** Returns attribute string (for file list display) */
        @Override
        public String getFileAttrStr() {
            StringBuilder attr = new StringBuilder();
            int fType = getFileAttr().getType();
            // MS-DOS
            if ((getFileAttr().getOrigin() & FILETYPE_MASK_MS_LFN) == FILETYPE_MASK_MS_LFN) {
                if (!attr.isEmpty()) attr.append(", ");
                attr.append(Utils.keyAt(typeNameMS, TYPE_NAME_MS_LFN)); // long file name
            } else {
                getFileAttrStrSub(fType, attr);
            }
            if (attr.isEmpty()) {
                attr.append("---");
            }
            return attr.toString();
        }

        /** Set the first group number */
        @Override
        public void setStartGroup(int fileUnitNum, int val, int size) {
            // MS-DOS
            data.data().msdos().startGroup = (short) val;
        }

        /** Returns the first group number */
        @Override
        public int getStartGroup(int fileUnitNum) {
            // MS-DOS
            return data.data().msdos().startGroup & 0xffff;
        }

        /** Whether item has creation date and time */
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

        /** Whether setting of time can be ignored */
        @Override
        public int canIgnoreDateTime() {
            return DATETIME_CREATE_ACCESS;
        }

        /**
         * Returns creation date
         *
         * @return date
         */
        @Override
        public LocalDate getFileCreateDate(LocalDateTime tm) {
            short cDate = data.data().msdos().cDate;
            return convDateToTm(cDate);
        }

        /**
         * Get creation time
         *
         * @return time
         */
        @Override
        public LocalTime getFileCreateTime(LocalDateTime tm) {
            short cTime = data.data().msdos().cTime;
            return convTimeToTm(cTime);
        }

        /** Returns creation date string */
        @Override
        public String getFileCreateDateStr() {
            LocalDateTime tm = LocalDateTime.now();
            LocalDate ld = getFileCreateDate(tm);
            return Utils.formatYMDStr(ld);
        }

        /** Returns creation time string */
        @Override
        public String getFileCreateTimeStr() {
            LocalDateTime tm = LocalDateTime.now();
            LocalTime lt = getFileCreateTime(tm);
            return Utils.formatHMSStr(lt);
        }

        /** Set creation date */
        @Override
        public void setFileCreateDate(LocalDateTime tm) {
            if (tm.getYear() >= 0 && tm.getMonth().ordinal() >= 1) {
                short cdate = convTmToDate(tm);
                data.data().msdos().cDate = cdate;
            }
        }

        /** Set creation time */
        @Override
        public void setFileCreateTime(LocalDateTime tm) {
            if (tm.getHour() >= 1 && tm.getMinute() >= 1) {
                short ctime = convTmToTime(tm);
                data.data().msdos().cTime = ctime;
            }
        }

        /** Whether item has access date and time */
        @Override
        public boolean hasAccessDateTime() {
            return true;
        }

        @Override
        public boolean hasAccessDate() {
            return true;
        }

        @Override
        public boolean hasAccessTime() {
            return false;
        }

        /** Returns access date string */
        @Override
        public LocalDate getFileAccessDate() {
            short aDate = data.data().msdos().aDate;
            return convDateToTm(aDate);
        }

        /** Returns access date string */
        @Override
        public String getFileAccessDateStr() {
            LocalDateTime tm = LocalDateTime.now();
            LocalDate ld = getFileAccessDate();
            return Utils.formatYMDStr(ld);
        }

        /** Set access date */
        @Override
        public void setFileAccessDate(LocalDateTime tm) {
            if (tm.getYear() >= 0 && tm.getMonth().ordinal() >= 1) {
                short adate = convTmToDate(tm);
                data.data().msdos().aDate = adate;
            }
        }

        /** Convert string to byte sequence. Character code is machine dependent */
        @Override
        public int convStringToChars(String src, byte[] dst, int len) {
            if ((getFileType1() & FILETYPE_MASK_MS_LFN) == FILETYPE_MASK_MS_LFN) {
                // Long file names are always UTF-16
                byte[] buf = src.getBytes(StandardCharsets.UTF_16);
                if (buf.length > 0) {
                    int l = Math.min(buf.length, len);
                    System.arraycopy(buf, 0, dst, 0, l);
                    return l;
                } else {
                    return 0;
                }
            } else {
                return basic.getCharCodes().convToChars(src, dst, len);
            }
        }

        /** Convert byte sequence to string. Character code is machine dependent */
        public void convCharsToString(byte[] src, int len, StringBuilder dst) {
            if ((getFileType1() & FILETYPE_MASK_MS_LFN) == FILETYPE_MASK_MS_LFN) {
                // Long file names are always UTF-16
                dst.append(new String(src, 0, len, StandardCharsets.UTF_16));
            } else {
                basic.getCharCodes().convToString(src, 0, len, dst, -1);
            }
        }

        //
        // For dialog
        //

        /** Set other attribute values */
        @Override
        public void setOptionalAttr(DiskBasicDirItemAttr attr) {
        }

        /** Set internal data displayed in properties */
        @Override
        public void setInternalDataInAttrDialog(KeyValArray vals) {
            int t1 = getFileType1();
            if ((t1 & FILETYPE_MASK_MS_LFN) == FILETYPE_MASK_MS_LFN) {
                // int File Name entry
                vals.add("ORDER", data.data().mslfn().order & 0xff);
                vals.add("NAME", data.data().mslfn().name, data.data().mslfn().name.length);
                vals.add("TYPE", data.data().mslfn().type & 0xff);
                vals.add("TYPE2", data.data().mslfn().type2 & 0xff);
                vals.add("CHKSUM", data.data().mslfn().checksum & 0xff);
                vals.add("NAME2", data.data().mslfn().name2, data.data().mslfn().name2.length);
                vals.add("DUMMY_GROUP", data.data().mslfn().dummyGroup & 0xffff);
                vals.add("NAME3", data.data().mslfn().name3, data.data().mslfn().name3.length);
            } else {
                // Short File Name entry
                super.setInternalDataInAttrDialog(vals);
            }
        }
    }
}
