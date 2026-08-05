///
/// Copyright (c) Sasaji. All rights reserved.
///

package l3diskex.basicfmt.diritem;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
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
import l3diskex.basicfmt.diritem.DiskBasicDirItemOS9.DirectoryOs9;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;

import static l3diskex.Config.config;
import static l3diskex.Parambase.MyAttributes.findUpperCase;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BINARY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_DIRECTORY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_NONSHARE_MASK;
import static l3diskex.basicfmt.type.DiskBasicTypeOS9.FORMAT_TYPE_OS9;


/**
 * Directory 1 item OS-9
 */
public class DiskBasicDirItemOS9 extends DiskBasicDirItem<DirectoryOs9> {

    private static final ResourceBundle rb = ResourceBundle.getBundle("messages");

    /**
     * OS-9 LSN
     */
    @Serdes
    public static class Os9Lsn {

        @Element(sequence = 1)
        public byte h;
        @Element(sequence = 2)
        public byte m;
        @Element(sequence = 3)
        public byte l;

        public int getOs9Lsn() {
            return (((h & 0xff) << 16) | ((m & 0xff) << 8) | (l & 0xff));
        }

        public void setOs9Lsn(int val) {
            h = (byte) ((val & 0xff0000) >> 16);
            m = (byte) ((val & 0xff00) >> 8);
            l = (byte) (val & 0xff);
        }
    }

    /**
     * OS-9 Segment
     */
    @Serdes
    public static class Os9Segment {

        @Element(sequence = 1)
        public Os9Lsn lsn = new Os9Lsn();
        @Element(sequence = 2)
        public short siz;
    }

    /**
     * OS-9 Date Format
     */
    @Serdes
    public static class Os9Date {

        @Element(sequence = 1)
        public byte yy;
        @Element(sequence = 2)
        public byte mm;
        @Element(sequence = 3)
        public byte dd;
        @Element(sequence = 4)
        public byte hh;
        @Element(sequence = 5)
        public byte mi;

        public Os9CDate toCDate() {
            Os9CDate cDate = new Os9CDate();
            cDate.yy = this.yy;
            cDate.mm = this.mm;
            cDate.dd = this.dd;
            return cDate;
        }
    }

    /**
     * OS-9 Created Date
     */
    @Serdes
    public static class Os9CDate {

        @Element(sequence = 1)
        public byte yy;
        @Element(sequence = 2)
        public byte mm;
        @Element(sequence = 3)
        public byte dd;
    }

    /**
     * Directory entry OS-9 (32bytes)
     */
    @Serdes
    public static class DirectoryOs9 implements Directory {

        @Element(sequence = 1)
        public byte[] deNam = new byte[28];
        @Element(sequence = 2)
        public byte deReserved;
        @Element(sequence = 3)
        public Os9Lsn deLsn = new Os9Lsn(); // link to FD

        public static final int SIZE = 32;
    }

    /**
     * OS-9 File Descriptor
     */
    @Serdes
    public static class DirectoryOs9Fd implements Directory {

        @Element(sequence = 1)
        public byte attr; // 1 fdAtt
        @Element(sequence = 2)
        public short ownerId; // 2 fdOwn
        @Element(sequence = 3)
        public Os9Date date = new Os9Date(); // 5 fdDat
        @Element(sequence = 4)
        public byte linkCount; // 1 fdLnk
        @Element(sequence = 5)
        public int size; // 4 in bytes fdSiz
        @Element(sequence = 6)
        public Os9CDate cDate = new Os9CDate(); // 3 cDate
        @Element(sequence = 7)
        public Os9Segment[] segments = new Os9Segment[48]; // 5*48=240 fdSeg

        public DirectoryOs9Fd() {
            for (int i = 0; i < 48; i++) {
                segments[i] = new Os9Segment();
            }
        }

        public static final int SIZE = 256;
    }

    private static final int TYPE_NAME_OS9_DIRECTORY = 0;
    private static final int TYPE_NAME_OS9_NONSHARE = 1;

    public static final int FILETYPE_MASK_OS9_DIRECTORY = 0x80;
    public static final int FILETYPE_MASK_OS9_NONSHARE = 0x40;
    public static final int FILETYPE_MASK_OS9_PUBLIC_EXEC = 0x20;
    public static final int FILETYPE_MASK_OS9_PUBLIC_WRITE = 0x10;
    public static final int FILETYPE_MASK_OS9_PUBLIC_READ = 0x08;
    public static final int FILETYPE_MASK_OS9_USER_EXEC = 0x04;
    public static final int FILETYPE_MASK_OS9_USER_WRITE = 0x02;
    public static final int FILETYPE_MASK_OS9_USER_READ = 0x01;

    // OS-9 attribute names
    public static final String[] G_TYPE_NAME_OS9 = {
            "<DIR>",
            "Non-sharable",
    };

    public static final char[] TYPE_NAME_OS9_2 = {
            'X', 'W', 'R', 'x', 'w', 'r'
    };

    public static final String[] TYPE_NAME_OS9_2L = {
            "Execute",
            "Write",
            "Read",
    };

    /**
     * Pointer to OS-9 File Descriptor area
     */
    public static class DiskBasicDirItemOS9FD {

        private DiskBasic basic;
        private DiskImageSector sector;
        private DirectoryOs9Fd fd;
        private int myLsn;
        private final ZeroData zeroData = new ZeroData(); // union replacement

        private static class ZeroData {

            public Os9Date date = new Os9Date();
            public Os9CDate cDate = new Os9CDate();
        }

        public DiskBasicDirItemOS9FD() {
            basic = null;
            sector = null;
            fd = null;
            myLsn = -1;
        }

        /** Set pointer */
        public void set(DiskBasic basic, DiskImageSector sector, int myLsn, DirectoryOs9Fd fd) {
            this.basic = basic;
            this.sector = sector;
            this.myLsn = myLsn;
            this.fd = fd;
        }

        /** Allocate memory for FD */
        public void alloc() {
            fd = null;
            fd = new DirectoryOs9Fd();
        }

        /** Clear FD */
        public void clear() {
            if (fd != null) {
                fd = new DirectoryOs9Fd();
            }
            if (sector != null) {
                sector.fill((byte) 0);
            }
        }

        /** Whether valid */
        public boolean isValid() {
            return (fd != null);
        }

        /** Returns pointer to FD */
        public DirectoryOs9Fd getFD() {
            return fd;
        }

        /** Returns own LSN */
        public int getMyLSN() {
            return myLsn;
        }

        /** Set own LSN */
        public void setMyLSN(int val) {
            myLsn = val;
        }

        /** Returns attribute */
        public short getAttr() {
            return fd != null ? fd.attr : 0;
        }

        /** Set attribute */
        public void setAttr(short val) {
            if (fd != null) {
                fd.attr = (byte) val;
            }
        }

        /** Returns owner ID */
        public int getOwnerId() {
            return fd != null ? fd.ownerId : 0;
        }

        /** Set owner ID */
        public void setOwnerId(int val) {
            if (fd != null) {
                fd.ownerId = (short) val;
            }
        }

        /** Returns LSN of segment */
        public int getLsn(int idx) {
            return fd != null ? fd.segments[idx].lsn.getOs9Lsn() : 0;
        }

        /** Returns number of sectors in segment */
        public int getSize(int idx) {
            return fd != null ? fd.segments[idx].siz : 0;
        }

        /** Set LSN in segment */
        public void setLsn(int idx, int val) {
            if (fd != null) {
                Os9Lsn x = new Os9Lsn();
                x.setOs9Lsn(val);
                fd.segments[idx].lsn = x;
            }
        }

        /** Set number of sectors in segment */
        public void setSize(int idx, int val) {
            if (fd != null) {
                fd.segments[idx].siz = (short) val;
            }
        }

        /** Returns file size */
        public int getSize() {
            return fd != null ? fd.size : 0;
        }

        /** Set file size */
        public void setSize(int val) {
            if (fd != null) {
                fd.size = val;
            }
        }

        /** Returns number of links */
        public short getLinkCount() {
            return fd != null ? fd.linkCount : 0;
        }

        /** Set number of links */
        public void setLinkCount(short val) {
            if (fd != null) {
                fd.linkCount = (byte) val;
            }
        }

        /** Returns modify date */
        public Os9Date getDate() {
            return fd != null ? fd.date : zeroData.date;
        }

        /** Set modify date */
        public void setDate(Os9Date val) {
            if (fd != null) {
                fd.date = val;
            }
        }

        /** Set modify date */
        public void setDate(Os9CDate val) {
            if (fd != null) {
                fd.date.yy = val.yy;
                fd.date.mm = val.mm;
                fd.date.dd = val.dd;
            }
        }

        /** Returns creation date */
        public Os9CDate getCDate() {
            return fd != null ? fd.cDate : zeroData.cDate;
        }

        /** Set creation date */
        public void setCDate(Os9CDate val) {
            if (fd != null) {
                fd.cDate = val;
            }
        }

        /** Write the in memory FD back onto its sector and mark the sector as modified */
        public void setModify() throws IOException {
            if (sector == null || fd == null) return;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Serdes.Util.serialize(fd, baos);
            sector.copy(baos.toByteArray(), baos.size());
            sector.setModify();
        }
    }

    //
    //
    //

    /** Directory data */
    private final DiskBasicDirData<DirectoryOs9> data = new DiskBasicDirData<>();

    /** Pointer to File Descriptor area */
    private final DiskBasicDirItemOS9.DiskBasicDirItemOS9FD fd = new DiskBasicDirItemOS9FD();

    /** Owner ID (for property dialog) */
    public int ownerId;

    /** Group ID (for property dialog) */
    public int groupId;

    @Override
    public boolean isSupported(int formatType) {
        return formatType == FORMAT_TYPE_OS9;
    }

    @Override
    public void init(DiskBasic basic) throws IOException {
        super.init(basic);

        data.alloc(DirectoryOs9.class);
        fd.alloc();
        ownerId = 0;
        groupId = 0;
    }

    @Override
    public void init(DiskBasic basic, DiskImageSector sector, int sectorPos, byte[] data, int dataP) throws IOException {
        super.init(basic, sector, sectorPos, data, dataP);

        this.data.attach(DirectoryOs9.class, data, dataP);
        ownerId = 0;
        groupId = 0;
    }

    @Override
    public void init(DiskBasic basic, int num, DiskBasicGroupItem groupItem, DiskImageSector sector, int sectorPos,
                     byte[] data, int dataP, SectorParam next, boolean[] unuse) throws IOException {
        super.init(basic, num, groupItem, sector, sectorPos, data, dataP, next, unuse);

        this.data.attach(DirectoryOs9.class, data, dataP);
        ownerId = 0;
        groupId = 0;

        used(checkUsed(unuse[0]));

        // Set pointer to FD sector
        if (isUsed()) {
            int lsn = this.data.data().deLsn.getOs9Lsn();
            if (lsn != 0) {
                DiskImageSector targetSector = basic.getSectorFromGroup(lsn);
                if (targetSector != null) {
                    DirectoryOs9Fd fd = new DirectoryOs9Fd();
                    Serdes.Util.deserialize(new ByteArrayInputStream(targetSector.getSectorBuffer()), fd);
                    this.fd.set(basic, targetSector, lsn, fd);
                }
            }
        }

        calcFileSize();

        // Do not display current or parent directory in tree
        String name = getFileNamePlainStr();
        visibleOnTree(!(isDirectory() && (name.equals(".") || name.equals(".."))));
    }

    /**
     * Set pointer to item
     *
     * @param num    Serial number
     * @param groupItem  Data such as track number
     * @param sector Sector
     * @param sectorPos Position of directory entry within sector
     * @param data   Directory item
     * @param next   [out] Next sector
     */
    @Override
    public void setData(int num, DiskBasicGroupItem groupItem, DiskImageSector sector, int sectorPos,
                        byte[] data, int dataPos, SectorParam next) throws IOException {
        super.setData(num, groupItem, sector, sectorPos, data, dataPos, next);

        this.data.attach(DirectoryOs9.class, data, dataPos);
    }

    /**
     * Returns position where file name is stored
     */
    @Override
    protected byte[] getFileNamePos(int num, int[] size, int[] len) {
        if (num == 0) {
            size[0] = len[0] = data.data().deNam.length;
            return data.data().deNam;
        } else {
            size[0] = len[0] = 0;
            return null;
        }
    }

    /**
     * Returns attribute 1
     */
    @Override
    protected int getFileType1() {
        return fd.getAttr() & 0xffff;
    }

    /**
     * Set attribute 1
     */
    @Override
    protected void setFileType1(int val) {
        fd.setAttr((short) (val & 0xff));
    }

    /**
     * Whether it is a used item
     */
    @Override
    public boolean checkUsed(boolean unuse) {
        return (data.data().deNam[0] != 0);
    }

    /**
     * Returns owner ID
     */
    public int getUserID() {
        return fd.getOwnerId();
    }

    /**
     * Set owner ID
     */
    public void setUserID(int val) {
        fd.setOwnerId(val & 0xffff);
    }

    /**
     * Returns position in list from attribute (for property dialog)
     */
    public int getFileType1Pos() {
        return getFileType1();
    }

    /**
     * Set file name
     */
    @Override
    protected void setNativeName(byte[] filename, int size, int length) {
        int[] s = {0}, l = {0};
        byte[] n = getFileNamePos(0, s, l);
        encodeString(n, l[0], new String(filename, 0, length), length);
    }

    /**
     * Get file name
     */
    @Override
    public void getNativeFileName(byte[] name, int[] nLen, byte[] ext, int[] eLen) {
        super.getNativeFileName(name, nLen, ext, eLen);

        // MSB is set on the last character of the string, so clear it
        nLen[0] = decodeString(name, nLen[0], name, nLen[0]);
    }

    /**
     * Convert date
     */
    public LocalDate convDateToTm(Os9CDate date) {
        return LocalDate.of(
                (date.yy % 100) +
                        (date.yy % 100) < 80 ? 100 : 0,
                date.mm - 1,
                date.dd);
    }

    /**
     * Convert time
     */
    public LocalTime convTimeToTm(Os9Date time) {
        return LocalTime.of(
                time.hh,
                time.mi,
                0);
    }

    /**
     * Convert to date
     */
    public void convTmToDate(LocalDateTime tm, Os9CDate date) {
        date.yy = (byte) (tm.getYear() % 100);
        date.mm = (byte) (tm.getMonth().ordinal() + 1);
        date.dd = (byte) tm.getDayOfMonth();
    }

    /**
     * Convert to time
     */
    public void convTmToTime(LocalDateTime tm, Os9Date time) {
        time.hh = (byte) tm.getHour();
        time.mi = (byte) tm.getMinute();
    }

    /**
     * Check directory item
     */
    @Override
    public boolean check(boolean[] last) {
        if (!data.isValid()) return false;

        //if (data.data().DE_Reserved != 0) return false;

        return true;
    }

    /**
     * Delete
     */
    @Override
    public boolean delete() {
        // Deletion is simply putting a code at the beginning of the entry
        data.data().deNam[0] = basic.getDeleteCode();
        used(false);
        return true;
    }

    /**
     * Set attribute
     */
    @Override
    public void setFileAttr(DiskBasicFileType fileType) {
        int fType = fileType.getType();
        if (fType == -1) return;

        int t1 = 0;
        int user_id = -1;
        if (fileType.getFormat() == basic.getFormatTypeNumber()) {
            t1 = fileType.getOrigin(0);
            user_id = fileType.getOrigin(1);
        } else {
            if ((fType & FILE_TYPE_DIRECTORY_MASK.getValue()) != 0) {
                t1 |= FILETYPE_MASK_OS9_DIRECTORY;
            }
            if ((fType & FILE_TYPE_NONSHARE_MASK.getValue()) != 0) {
                t1 |= FILETYPE_MASK_OS9_NONSHARE;
            }
            //t1 |= ((fType & FILETYPE_OS9_PERMISSION_MASK) >> FILETYPE_OS9_PERMISSION_POS);
            if ((fType & FILE_TYPE_BINARY_MASK.getValue()) != 0) {
                t1 |= FILETYPE_MASK_OS9_PUBLIC_EXEC;
                t1 |= FILETYPE_MASK_OS9_USER_EXEC;
            }
            t1 |= FILETYPE_MASK_OS9_PUBLIC_READ;
            t1 |= FILETYPE_MASK_OS9_USER_WRITE;
            t1 |= FILETYPE_MASK_OS9_USER_READ;
        }
        setFileType1(t1);
        if (user_id >= 0) setUserID(user_id);
    }

    /**
     * Returns attribute
     */
    @Override
    public DiskBasicFileType getFileAttr() {
        int val = 0;
        int t1 = getFileType1();
        if ((t1 & FILETYPE_MASK_OS9_DIRECTORY) != 0) {
            val |= FILE_TYPE_DIRECTORY_MASK.getValue();
        }
        if ((t1 & FILETYPE_MASK_OS9_NONSHARE) != 0) {
            val |= FILE_TYPE_NONSHARE_MASK.getValue();
        }
        if ((t1 & (FILETYPE_MASK_OS9_PUBLIC_EXEC | FILETYPE_MASK_OS9_USER_EXEC)) != 0) {
            val |= FILE_TYPE_BINARY_MASK.getValue();
        }
        //val |= ((t1 << FILETYPE_OS9_PERMISSION_POS) & FILETYPE_OS9_PERMISSION_MASK);
        return new DiskBasicFileType(basic.getFormatTypeNumber(), val, t1, getUserID(), 0);
    }

    /**
     * Returns attribute string (for file list display)
     */
    @Override
    public String getFileAttrStr() {
        StringBuilder str = new StringBuilder();
        if (fd.isValid()) {
            if ((fd.getAttr() & FILETYPE_MASK_OS9_DIRECTORY) != 0) {
                if (!str.isEmpty()) str.append(", ");
                str.append(rb.getString(G_TYPE_NAME_OS9[TYPE_NAME_OS9_DIRECTORY]));
            }
            if ((fd.getAttr() & FILETYPE_MASK_OS9_NONSHARE) != 0) {
                if (!str.isEmpty()) str.append(", ");
                str.append(rb.getString(G_TYPE_NAME_OS9[TYPE_NAME_OS9_NONSHARE]));
            }
            if (!str.isEmpty()) str.append(", ");
            for (int i = 0; i < 6; i++) {
                if ((fd.getAttr() & (0x20 >> i)) != 0) {
                    str.append(TYPE_NAME_OS9_2[i]);
                } else {
                    str.append('-');
                }
            }
        }
        return str.toString();
    }

    /**
     * Set file size
     */
    @Override
    public void setFileSize(int val) {
        fd.setSize(val);
        groups.setSize(val);
    }

    /**
     * Returns file size
     */
    @Override
    public int getFileSize() {
        int size = fd.getSize();
        if (size == 0) size = groups.getSize();
        return size;
    }

    /**
     * Calculate file size and number of groups
     */
    @Override
    public void calcFileUnitSize(int fileUnitNum) {
        if (!isUsed() || !fd.isValid()) return;

        getUnitGroups(fileUnitNum, groups);
    }

    /**
     * Get all groups of specified directory
     */
    @Override
    public void getUnitGroups(int fileUnitNum, DiskBasicGroups groupItems) {
        if (!fd.isValid()) return;

        int calcGroups = 0;
        int calcFileSize = getFileSize();

        for (int i = 0; i < 48; i++) {
            int lsn = fd.getLsn(i);
            int size = fd.getSize(i);
            if (size == 0) {
                break;
            }

            if (i != 0) {
                if (groupItems.size() > 0) {
                    DiskBasicGroupItem groupItem = groupItems.last();
                    groupItem.next = lsn;
                }
            }
            for (int n = 0; n < size; n++) {
                int[] trackNum = {0};
                int[] sideNum = {0};
                int[] sectorNum = {1};
                int nextLsn = n + 1 != size ? lsn + n + 1 : 0;
                basic.calcNumFromSectorPosForGroup(lsn + n, trackNum, sideNum, sectorNum, null, null);
                groupItems.add(lsn + n, nextLsn, trackNum[0], sideNum[0], sectorNum[0], sectorNum[0], 0, 1);
                calcGroups++;
                if (calcGroups >= basic.getFatEndGroup()) {
                    // too large block size
                    break;
                }
            }
        }
        groupItems.setNums(calcGroups);
        groupItems.setSize(calcFileSize);
        groupItems.setSizePerGroup(basic.getSectorSize());
    }

    /**
     * Get creation date
     */
    @Override
    public LocalDate getFileCreateDate(LocalDateTime tm) {
        if (fd.isValid()) {
            return convDateToTm(fd.getCDate());
        } else {
            return LocalDate.of(1970, 1, 1);
        }
    }

    /**
     * Returns creation date string
     */
    @Override
    public String getFileCreateDateStr() {
        LocalDateTime tm = LocalDateTime.now();
        LocalDate ld = getFileCreateDate(tm);
        return Utils.formatYMDStr(ld);
    }

    /**
     * Set creation date
     */
    @Override
    public void setFileCreateDate(LocalDateTime tm) {
        if (fd.isValid() && tm.getYear() >= 0 && tm.getMonth().ordinal() >= 0) {
            Os9CDate date = new Os9CDate();
            convTmToDate(tm, date);
            fd.setCDate(date);
        }
    }

    /**
     * Get modify date
     */
    @Override
    public LocalDate getFileModifyDate(LocalDateTime tm) {
        if (fd.isValid()) {
            Os9CDate cDate = fd.getDate().toCDate();
            cDate.yy = fd.getDate().yy;
            cDate.mm = fd.getDate().mm;
            cDate.dd = fd.getDate().dd;
            return convDateToTm(cDate);
        } else {
            return LocalDate.of(1970, 1, 1);
        }
    }

    /**
     * Get modify time
     */
    @Override
    public LocalTime getFileModifyTime(LocalDateTime tm) {
        if (fd.isValid()) {
            return convTimeToTm(fd.getDate());
        } else {
            return LocalTime.of(0, 0, 0);
        }
    }

    /**
     * Returns modify date string
     */
    @Override
    public String getFileModifyDateStr() {
        LocalDateTime tm = LocalDateTime.now();
        LocalDate ld = getFileModifyDate(tm);
        return Utils.formatYMDStr(ld);
    }

    /**
     * Returns modify time string
     */
    @Override
    public String getFileModifyTimeStr() {
        LocalDateTime tm = LocalDateTime.now();
        LocalTime lt = getFileModifyTime(tm);
        return Utils.formatHMStr(lt);
    }

    /**
     * Set modify date
     */
    @Override
    public void setFileModifyDate(LocalDateTime tm) {
        if (fd.isValid() && tm.getYear() >= 0 && tm.getMonth().ordinal() >= -1) {
            Os9CDate date = new Os9CDate();
            convTmToDate(tm, date);
            fd.setDate(date);
        }
    }

    /**
     * Set modify time
     */
    @Override
    public void setFileModifyTime(LocalDateTime tm) {
        if (fd.isValid() && tm.getHour() >= 0 && tm.getMinute() >= -1) {
            Os9Date time = fd.getDate();
            convTmToTime(tm, time);
            fd.setDate(time);
        }
    }

    /**
     * Returns display order of date and time (for dialog)
     */
    @Override
    public int getFileDateTimeOrder(int idx) {
        return idx <= 1 ? 1 - idx : idx;
    }

    /**
     * Returns date and time (for file list)
     */
    @Override
    public String getFileDateTimeStr() {
        return getFileModifyDateTimeStr();
    }

    /**
     * Set the first group number
     */
    @Override
    public void setStartGroup(int fileUnitNum, int val, int size /* = 0 */) {
        data.data().deLsn.setOs9Lsn(val);
    }

    /**
     * Returns the first group number
     */
    @Override
    public int getStartGroup(int fileUnitNum) {
        return data.data().deLsn.getOs9Lsn();
    }

    /**
     * Set extra group number: set LSN to FD sector
     */
    @Override
    public void setExtraGroup(int val) {
        data.data().deLsn.setOs9Lsn(val);
    }

    /**
     * Returns extra group number: returns LSN to FD sector
     */
    @Override
    public int getExtraGroup() {
        return data.data().deLsn.getOs9Lsn();
    }

    /**
     * Get extra group number
     */
    @Override
    public void getExtraGroups(List<Integer> arr) {
        arr.add(getExtraGroup());
    }

    /**
     * Set sector for chain
     */
    @Override
    public void setChainSector(DiskImageSector sector, int lsn, byte[] data, DiskBasicDirItem<DirectoryOs9> pItem) throws IOException {
        DirectoryOs9Fd fd = new DirectoryOs9Fd();
        Serdes.Util.deserialize(new ByteArrayInputStream(sector.getSectorBuffer()), fd);
        this.fd.set(basic, sector, lsn, fd);
        this.fd.clear();

        // Copy attribute
        if (pItem != null) copyItem(pItem);

        // Link count
        this.fd.setLinkCount((short) 1);
    }

    /**
     * Whether item can be deleted
     */
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

    /**
     * Whether file name can be edited
     */
    @Override
    public boolean isFileNameEditable() {
        // ".", ".." are not allowed
        return isDeletable();
    }

    /**
     * Whether item can be loaded or exported
     */
    @Override
    public boolean isLoadable() {
        // ".", ".." are not allowed
        return isDeletable();
    }

    /**
     * Whether item can be copied (DnD internally)
     */
    @Override
    public boolean isCopyable() {
        // ".", ".." are not allowed
        return isDeletable();
    }

    /**
     * Whether item can be overwritten
     */
    @Override
    public boolean isOverWritable() {
        // Directory is not allowed
        int t1 = getFileType1();
        boolean valid = ((t1 & FILETYPE_MASK_OS9_DIRECTORY) == 0);
        // ".", ".." are not allowed
        valid &= isDeletable();
        return valid;
    }

    /**
     * Size of directory item
     */
    @Override
    public int getDataSize() {
        return data.getDataSize();
    }

    /**
     * Returns item
     */
    @Override
    public DirectoryOs9 getData() {
        return data.data();
    }

    /**
     * Copy item
     */
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

    /**
     * Clear directory
     */
    @Override
    public void clearData() {
        data.fill(basic.getDeleteCode(), getDataSize(), basic.isDataInverted(), 0);
    }

    /**
     * Copy item
     */
    @Override
    public void copyItem(DiskBasicDirItem<DirectoryOs9> src) {
        super.copyItem(src);
        DiskBasicDirItemOS9FD srcFd = ((DiskBasicDirItemOS9) src).getFd();
        fd.setAttr(srcFd.getAttr());
        fd.setOwnerId(srcFd.getOwnerId());
        fd.setSize(srcFd.getSize());
        fd.setDate(srcFd.getDate());
        fd.setCDate(srcFd.getCDate());
    }

    /**
     * Returns pointer to FD sector
     */
    public DiskBasicDirItemOS9FD getFd() {
        return fd;
    }

    /**
     * Set sector to which item belongs as modified
     */
    @Override
    public void setModify() throws IOException {
        super.setModify();
        fd.setModify();
    }

    /**
     * Set MSB on the last character of the string
     */
    public static int encodeString(byte[] dst, int dLen, String src, int sLen) {
        Arrays.fill(dst, 0, dLen, (byte) 0);
        int len = dLen > sLen ? sLen : dLen;
        System.arraycopy(src, 0, dst, 0, sLen);

        // Set MSB on the last character of the string
        for (int i = len - 1; i >= 0; i--) {
            if (dst[i] != 0) {
                dst[i] |= (byte) 0x80;
                break;
            }
        }
        return len;
    }

    /**
     * Clear MSB from the last character of the string
     */
    public static int decodeString(byte[] dst, int dLen, byte[] src, int sLen) {
        int len = dLen > sLen ? sLen : dLen;

        // Clear MSB of string
        boolean last = false;
        for (int i = 0; i < len; i++) {
            last = ((src[i] & 0x80) != 0);
            dst[i] = (byte) (src[i] & 0x7f);
            if (last) {
                dLen = i + 1;
                break;
            }
        }
        for (int i = dLen; i < len; i++) {
            dst[i] = 0;
        }
        return dLen;
    }

    /**
     * Processing required before exporting data
     */
    @Override
    public boolean preExportDataFile(String[] filename) {
        if (!config.isAddExtensionExport()) return true;

        if (!isDirectory()) {
            addExtensionByFileAttr(getFileAttr().getType(), 0x3f, filename, false);
        }
        return true;
    }

    /**
     * Processing required before importing data
     */
    @Override
    public boolean preImportDataFile(String[] filename) {
        if (config.isDecideAttrImport()) {
            trimExtensionByExtensionAttr(filename);
        }
        filename[0] = remakeFileNameAndExtStr(filename[0]);
        return true;
    }

    /**
     * Determine attribute from file name
     */
    @Override
    public int convOriginalTypeFromFileName(String filename) {
        final int FILE_TYPE_BINARY_MASK = 0x04;

        int t1 = 0;
        // -- --R -wr
        t1 |= FILETYPE_MASK_OS9_PUBLIC_READ;
        t1 |= FILETYPE_MASK_OS9_USER_WRITE;
        t1 |= FILETYPE_MASK_OS9_USER_READ;
        // Attach execution attribute by extension
        MyAttribute sa = findUpperCase(basic.getAttributesByExtension(), Utils.getExt(filename), FILE_TYPE_BINARY_MASK, FILE_TYPE_BINARY_MASK);
        if (sa != null) {
            // Attach execution attribute
            t1 |= FILETYPE_MASK_OS9_PUBLIC_EXEC;
            t1 |= FILETYPE_MASK_OS9_USER_EXEC;
        }

        return t1;
    }

    /**
     * Set other attribute values
     */
    @Override
    public void setOptionalAttr(DiskBasicDirItemAttr attr) {
        //setCDate(attr.getCreateDateTime());
    }

    /**
     * Set internal data displayed in properties
     */
    @Override
    public void setInternalDataInAttrDialog(KeyValArray vals) throws IOException {

        vals.add("DE_NAM", data.data().deNam, data.data().deNam.length);
        vals.add("DE_Reserved", data.data().deReserved);
        vals.add("DE_LSN", data.data().deLsn.getOs9Lsn());

        DiskBasicDirItemOS9FD cfd = getFd();
        if (!cfd.isValid()) return;

        DirectoryOs9Fd fd_data = cfd.getFD();

        vals.add("FD_ATT", fd_data.attr);
        vals.add("FD_OWN", (byte) fd_data.ownerId, true);
        ByteArrayOutputStream x = new ByteArrayOutputStream();
        Serdes.Util.serialize(fd_data.date, x);
        vals.add("FD_DAT", x.toByteArray(), x.size());
        vals.add("FD_LNK", fd_data.linkCount);
        vals.add("FD_SIZ", (byte) fd_data.size, true);
        x = new ByteArrayOutputStream();
        Serdes.Util.serialize(fd_data.cDate, x);
        vals.add("FD_DCR", x.toByteArray(), x.size());
    }
}
