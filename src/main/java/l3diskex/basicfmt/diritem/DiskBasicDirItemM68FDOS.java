/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt.diritem;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ResourceBundle;

import l3diskex.Utils;
import l3diskex.basicfmt.BasicCommon.Directory;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.KeyValArray;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.DiskBasicError;
import l3diskex.basicfmt.diritem.DiskBasicDirItemM68FDOS.DirectoryM68FDos;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;
import vavi.util.ByteUtil;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;

import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_HIDDEN_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_READONLY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_SYSTEM_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_WRITEONLY_MASK;
import static l3diskex.basicfmt.DiskBasicType.INVALID_GROUP_NUMBER;
import static l3diskex.basicfmt.type.DiskBasicTypeM68FDOS.FORMAT_TYPE_M68FDOS;


/**
 * Directory 1 item Sord M68 FDOS (KDOS)
 */
public class DiskBasicDirItemM68FDOS extends DiskBasicDirItemMZBase<DirectoryM68FDos> {

    static final ResourceBundle rb = ResourceBundle.getBundle("messages");

    /**
     * Directory entry M68 FDOS (31bytes)
     */
    @Serdes
    public static class DirectoryM68FDos implements Directory {

        public static final int SIZE = 4 + 2 + 2 + 2 + 2 + 1 + 2 + 2 + 2 + 2 + 1 + 2 + 2 + 2 + 3;

        @Element(sequence = 1)
        public M68FDosName name = new M68FDosName();
        @Element(sequence = 2)
        public M68FDosExt ext = new M68FDosExt();
        @Element(sequence = 3)
        public short attr1;
        @Element(sequence = 4)
        public short attr2;
        @Element(sequence = 5)
        public short blockSize;
        @Element(sequence = 6)
        public byte eofInSector;
        @Element(sequence = 7)
        public short date;
        @Element(sequence = 8)
        public short time; // unknown
        @Element(sequence = 9)
        public M68fdosRev rev = new M68fdosRev();
        @Element(sequence = 10)
        public short startSector;
        @Element(sequence = 11)
        public byte attr3;
        @Element(sequence = 12)
        public short endSector; // unknown
        @Element(sequence = 13)
        public short loadAddress;
        @Element(sequence = 14)
        public short execAddress;
        @Element(sequence = 15)
        public byte[] unknown2 = new byte[3];

        @Serdes
        public static class M68FDosName {

            static final int SIZE = 4;

            short getW(int index) {
                return ByteUtil.readBeShort(b, index * 2);
            }

            void setW(int index, short value) {
                ByteUtil.writeBeShort(value, b, index * 2);
            }

            @Element(sequence = 1)
            public byte[] b = new byte[4];
        }

        @Serdes
        public static class M68FDosExt {

            static final int SIZE = 2;

            short getW() {
                return ByteUtil.readBeShort(b);
            }

            public void setW(short value) {
                ByteUtil.writeBeShort(value, b);
            }

            @Element(sequence = 1)
            public byte[] b = new byte[2];
        }

        @Serdes
        public static class M68fdosRev {

            static final int SIZE = 2;

            short getW() {
                return ByteUtil.readBeShort(b);
            }

            public void setW(short value) {
                ByteUtil.writeBeShort(value, b);
            }

            @Element(sequence = 1)
            public byte[] b = new byte[2];
        }
    }

    public static final int FILETYPE_M68_FDOS_D = 0x0001;
    public static final int FILETYPE_M68_FDOS_C = 0x0080;
    public static final int FILETYPE_M68_FDOS_S = 0x0100;
    public static final int FILETYPE_M68_FDOS_X = 0x0800;
    public static final int FILETYPE_M68_FDOS_R = 0x1000;
    public static final int FILETYPE_M68_FDOS_W = 0x2000;
    public static final int FILETYPE_M68_FDOS_P = 0x4000;
    public static final int FILETYPE_M68_FDOS_A = 0x8000;

    public static final int EXT_NAME_M68_FDOS_SAV = 0;
    public static final int EXT_NAME_M68_FDOS_END = 1;

    /** M68 FDOS attribute names */
    public static final Map<String, Object> typeNameM68FDos = new HashMap<>() {{
        put("A - Attribute Protected", FILETYPE_M68_FDOS_A);
        put("P - Permanent", FILETYPE_M68_FDOS_P);
        put("W - Write Protected", FILETYPE_M68_FDOS_W);
        put("R - Read Protected", FILETYPE_M68_FDOS_R);
        put("X - Xfer Protected", FILETYPE_M68_FDOS_X);
        put("S - Saved Memory Image", FILETYPE_M68_FDOS_S);
        put("C - Continuous", FILETYPE_M68_FDOS_C);
        put("D - Device", FILETYPE_M68_FDOS_D);
    }};

    /** M68 FDOS attribute names (for list) */
    public static final Map<String, Object> typeNameShortM68FDos = new HashMap<>() {{
        put("A", FILETYPE_M68_FDOS_A);
        put("P", FILETYPE_M68_FDOS_P);
        put("W", FILETYPE_M68_FDOS_W);
        put("R", FILETYPE_M68_FDOS_R);
        put("X", FILETYPE_M68_FDOS_X);
        put("S", FILETYPE_M68_FDOS_S);
        put("C", FILETYPE_M68_FDOS_C);
        put("D", FILETYPE_M68_FDOS_D);
    }};

    /** M68 FDOS file name mapping table */
    public static final char[] m68FDosCharMap = {
            ' ', '?', '0', '1', '2', '3', '4', '5', '6', '7',
            '8', '9', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H',
            'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R',
            'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', '+', '-',
    };

    /** Set attribute from M68 FDOS extension */
    public static final Map<String, Object> extNameM68FDos = new LinkedHashMap<>() {{
        put("SAV", FILETYPE_M68_FDOS_C);
    }};

    /** Directory data */
    private final DiskBasicDirData<DirectoryM68FDos> data = new DiskBasicDirData<>();

    @Override
    public boolean isSupported(int formatType) {
        return formatType == FORMAT_TYPE_M68FDOS;
    }

    @Override
    public void init(DiskBasic basic) throws IOException {
        super.init(basic);

        data.alloc(DirectoryM68FDos.class);
    }

    @Override
    public void init(DiskBasic basic, DiskImageSector sector, int sectorPos, byte[] data, int dataPos) throws IOException {
        super.init(basic, sector, sectorPos, data, dataPos);

        this.data.attach(DirectoryM68FDos.class, data, dataPos);
    }

    @Override
    public void init(DiskBasic basic, int num, DiskBasicGroupItem groupItem, DiskImageSector sector, int sectorPos,
                     byte[] data, int dataPos, SectorParam next, boolean[] unuse) throws IOException {
        super.init(basic, num, groupItem, sector, sectorPos, data, dataPos, next, unuse);

        this.data.attach(DirectoryM68FDos.class, data, dataPos);

        used(checkUsed(unuse[0]));

        calcFileSize();
    }

    /** Returns position where file name is stored */
    @Override
    public byte[] getFileNamePos(int num, int[] size, int[] len) {
        if (num == 0) {
            size[0] = len[0] = 6;
            return data.data().name.b;
        } else {
            size[0] = len[0] = 0;
            return null;
        }
    }

    /** Returns position where extension is stored */
    @Override
    public byte[] getFileExtPos(int[] len) {
        len[0] = 3;
        return data.data().ext.b;
    }

    /** Set file name */
    @Override
    public void setNativeName(byte[] filename, int size, int length) {
        int[] nl = {0};
        int[] ns = {0};
        byte[] n = getFileNamePos(0, ns, nl);
        if (n != null && ns[0] > 0) {
            data.data().name.setW(0, basic.orderUint16(encodeName(Arrays.copyOfRange(filename, 0, size), 0, size)));
            data.data().name.setW(1, basic.orderUint16(encodeName(Arrays.copyOfRange(filename, 3, size), 0, size - 3)));
        }
    }

    /** Set extension */
    @Override
    public void setNativeExt(byte[] fileExt, int size, int length) {
        int[] el = {0};
        byte[] e = getFileExtPos(el);
        if (e != null && el[0] > 0) {
            data.data().ext.setW(basic.orderUint16(encodeName(fileExt, 0, size)));
        }
    }

    /** Get file name */
    @Override
    public void getNativeName(byte[] filename, int size, int[] length) {
        int[] s = {0};
        int[] l = {0};

        byte[] n = getFileNamePos(0, s, l);
        if (n != null && s[0] > 0) {
            int copySize = s[0];
            if (copySize > size) copySize = size;
            decodeName(basic.orderUint16(data.data().name.getW(0)), filename, 0, size);
            decodeName(basic.orderUint16(data.data().name.getW(1)), filename, 3, size - 3);
        }

        length[0] = l[0];
    }

    /** Get extension */
    @Override
    public void getNativeExt(byte[] fileExt, int size, int[] length) {
        int[] l = {0};

        byte[] e = getFileExtPos(l);
        if (e != null && l[0] > 0) {
            decodeName(basic.orderUint16(data.data().ext.getW()), fileExt, 0, size);
        }

        length[0] = l[0];
    }

    /** Returns attribute 1 */
    @Override
    public int getFileType1() {
        return basic.orderUint16(data.data().attr1) & 0xffff;
    }

    /** Returns attribute 2 */
    @Override
    public int getFileType2() {
        return basic.orderUint16(data.data().attr2) & 0xffff;
    }

    /** Returns attribute 3 */
    @Override
    public int getFileType3() {
        return data.data().attr3 & 0xff;
    }

    /** Set attribute 1 */
    @Override
    public void setFileType1(int val) {
        data.data().attr1 = basic.orderUint16((short) val);
    }

    /** Set attribute 2 */
    @Override
    public void setFileType2(int val) {
        data.data().attr2 = basic.orderUint16((short) val);
    }

    /** Set attribute 3 */
    @Override
    public void setFileType3(int val) {
        data.data().attr3 = (byte) (val & 0xff);
    }

    /** Whether it is a used item */
    @Override
    public boolean checkUsed(boolean unuse) {
        return !(data.data().name.getW(0) == 0 && data.data().name.getW(1) == 0 && data.data().ext.getW() == 0);
    }

    /** Returns revision string */
    private String getRevisionStr() {
        String str = "";
        short revision = getRevision();
        if (revision != 0) {
            byte[] buf = new byte[4];
            for (int i = 0; i < 4; i++) buf[i] = 0;
            decodeName(revision, buf, 0, 3);
            str = new String(buf);
        }
        return str;
    }

    /** Returns revision */
    public short getRevision() {
        return basic.orderUint16(data.data().rev.getW());
    }

    /** Set revision */
    private void setRevision(short val) {
        data.data().rev.setW(basic.orderUint16(val));
    }

    /** Convert attribute */
    private int convToNativeType(int fileType) {
        int val = 0;
        if ((fileType & FILE_TYPE_SYSTEM_MASK.getValue()) != 0) {
            val = FILETYPE_M68_FDOS_S;
        }
        if ((fileType & FILE_TYPE_HIDDEN_MASK.getValue()) != 0) {
            val |= FILETYPE_M68_FDOS_P;
        }
        if ((fileType & FILE_TYPE_READONLY_MASK.getValue()) != 0) {
            val |= FILETYPE_M68_FDOS_W;
        }
        if ((fileType & FILE_TYPE_WRITEONLY_MASK.getValue()) != 0) {
            val |= FILETYPE_M68_FDOS_R;
        }
        return val;
    }

    /** Set file size in data */
    @Override
    public void setFileSizeBase(int val) {
        int sectorSize = basic.getSectorSize();
        if (needChainInData()) {
            sectorSize -= 2;
        }
        int sector = val / sectorSize;
        int eof = val % sectorSize;
        if (eof > 0) {
            sector++;
        }
        data.data().blockSize = basic.orderUint16((short) sector);
        data.data().eofInSector = (byte) (eof & 0xff);
    }

    /** Returns file size in data */
    @Override
    public int getFileSizeBase() {
        int sectorSize = basic.getSectorSize();

        int val = basic.orderUint16(data.data().blockSize) & 0xffff;
        int eof = data.data().eofInSector & 0xff;
        val *= sectorSize;
        if (eof > 0) {
            val += eof;
            val -= sectorSize;
        }
        return val;
    }

    /** Mid-processing for group acquisition calculation */
    private void calcAllGroups(int calcFlags, int[] groupNum, int[] remain, int[] secSize, int[] endSec, Object[] userData) {
        if (needChainInData()) {
            // Get the next sector number at the end of the sector
            DiskImageSector sector = basic.getSectorFromGroup(groupNum[0]);
            if (sector == null) {
                // Why?
                groupNum[0] = INVALID_GROUP_NUMBER;
                return;
            }
            short nextGroupNum = sector.get16(-2, basic.isBigEndian());
            if (nextGroupNum == 0 || (nextGroupNum & 0xffff) > basic.getFatEndGroup()) {
                groupNum[0] = INVALID_GROUP_NUMBER;
            } else {
                groupNum[0] = nextGroupNum & 0xffff;
            }
        } else {
            // Contiguous
            groupNum[0]++;
        }
    }

    /** Decode file name */
    private static void decodeName(short codeVal, byte[] name, int offset, int size) {
        int code = codeVal & 0xffff;
        int sta = size > 2 ? 2 : size;
        for (int i = sta; i >= 0; i--) {
            int c = (code % 40);
            name[offset + i] = (byte) m68FDosCharMap[c];
            code /= 40;
        }
    }

    /** Encode file name */
    private static short encodeName(byte[] name, int offset, int size) {
        short code = 0;
        int fin = size < 3 ? size : 3;
        for (int i = 0; i < fin; i++) {
            code *= 40;

            int match = -1;
            for (int c = 0; c < 40; c++) {
                if ((name[offset + i] & 0xff) == (m68FDosCharMap[c] & 0xff)) {
                    match = c;
                    break;
                }
            }
            if (match >= 0) {
                code += (short) (match % 40);
            }
        }
        return code;
    }

    /** Set pointer to item */
    @Override
    public void setData(int num, DiskBasicGroupItem gIgroupItemem, DiskImageSector sector, int sectorPos,
                        byte[] data, int dataPos, SectorParam next) throws IOException {
        super.setData(num, gIgroupItemem, sector, sectorPos, data, dataPos, next);

        this.data.attach(DirectoryM68FDos.class, data, dataPos);
    }

    /** Check directory item */
    @Override
    public boolean check(boolean[] last) {
        if (!data.isValid()) return false;

        short val = basic.orderUint16(data.data().name.getW(0));
        if ((val & 0xffff) >= 0xed80) {
            return false;
        }
        return true;
    }

    /** Delete */
    @Override
    public boolean delete() throws IOException {
        // Clear file name
        data.data().name.setW(0, (short) 0);
        data.data().name.setW(1, (short) 0);
        data.data().ext.setW((short) 0);
        used(false);
        // Set start group to unused
        type.setGroupNumber(getStartGroup(0), 0);
        return true;
    }

    /** Set attribute */
    @Override
    public void setFileAttr(DiskBasicFileType fileType) {
        int ftype = fileType.getType();
        if (ftype == -1) return;

        int t1 = 0;
        int t2 = 0;
        int t3 = 0;
        short rev = 0;
        if (fileType.getFormat() == basic.getFormatTypeNumber()) {
            t1 = fileType.getOrigin(0);
            t2 = fileType.getOrigin(1);
            int t3_rev = fileType.getOrigin(2);
            rev = (short) (t3_rev & 0xffff);
            t3 = t3_rev >> 16;
        } else {
            t1 = convToNativeType(ftype);
        }
        setFileType1(t1);
        setFileType2(t2);
        setFileType3(t3);
        setRevision(rev);
    }

    /** Returns attribute */
    @Override
    public DiskBasicFileType getFileAttr() {
        int t1 = getFileType1();
        int val = 0;
        if ((t1 & FILETYPE_M68_FDOS_S) != 0) {
            val = FILE_TYPE_SYSTEM_MASK.getValue();
        }
        if ((t1 & FILETYPE_M68_FDOS_P) != 0) {
            val |= FILE_TYPE_HIDDEN_MASK.getValue();
        }
        if ((t1 & FILETYPE_M68_FDOS_R) != 0) {
            val |= FILE_TYPE_WRITEONLY_MASK.getValue();
        }
        if ((t1 & FILETYPE_M68_FDOS_W) != 0) {
            val |= FILE_TYPE_READONLY_MASK.getValue();
        }
        int t2 = getFileType2();
        int t3 = getFileType3();
        short rev = getRevision();

        return new DiskBasicFileType(basic.getFormatTypeNumber(), val, t1, t2, (t3 << 16) | (rev & 0xffff));
    }

    /** Returns attribute string (for file list display) */
    @Override
    public String getFileAttrStr() {
        StringBuilder attr = new StringBuilder();
        int fType = getFileType1();
        for (Map.Entry<String, Object> e : typeNameShortM68FDos.entrySet()) {
            if ((fType & (int) e.getValue()) != 0) {
                if (!attr.isEmpty()) attr.append(", ");
                attr.append(rb.getString(e.getKey()));
            }
        }

        return attr.toString();
    }

    /** Returns file size */
    @Override
    public int getFileSize() {
        int sectorSize = basic.getSectorSize();
        if (needChainInData()) {
            sectorSize -= 2;
        }
        int val = basic.orderUint16(data.data().blockSize) & 0xffff;
        int eof = data.data().eofInSector & 0xff;
        val *= sectorSize;
        if (eof > 0) {
            val += eof;
            val -= sectorSize;
        }
        return val;
    }

    /** Calculate file size and number of groups */
    @Override
    public void calcFileUnitSize(int fileUnitNum) {
        if (!isUsed()) return;

        getUnitGroups(fileUnitNum, groups);
    }

    /** Set the first group number */
    @Override
    public void setStartGroup(int fileUnitNum, int val, int size) {
        short startSector = (short) (val & 0xffff);
        data.data().startSector = basic.orderUint16(startSector);
    }

    /** Returns the first group number */
    @Override
    public int getStartGroup(int fileUnitNum) {
        return basic.orderUint16(data.data().startSector) & 0xffff;
    }

    /** Set extra group number */
    @Override
    public void setExtraGroup(int val) {
        short endSector = (short) (val & 0xffff);
        data.data().endSector = basic.orderUint16(endSector);
    }

    /** Returns extra group number */
    @Override
    public int getExtraGroup() {
        return basic.orderUint16(data.data().endSector) & 0xffff;
    }

    /** Get extra group number */
    public void getExtraGroups(int[] arr) {
    }

    /** Whether item has date and time */
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
        return false;
    }

    /** Whether item time setting can be ignored */
    @Override
    public int canIgnoreDateTime() {
        return DiskBasicDirItem.DATETIME_ALL;
    }

    /** Returns date */
    @Override
    public LocalDate getFileCreateDate(LocalDateTime tm) {
        int date = basic.orderUint16(data.data().date) & 0xffff;

        int yy = (date >> 9) & 0x7f;
        if (yy < 70) yy += 100;
        return LocalDate.of(
                yy,
                ((date & 0x01e0) >> 5) - 1,
                date & 0x001f);
    }

    /** Returns date */
    @Override
    public String getFileCreateDateStr() {
        LocalDateTime tm = LocalDateTime.now();
        LocalDate ld = getFileCreateDate(tm);
        return Utils.formatYMDStr(ld);
    }

    /** Set date */
    @Override
    public void setFileCreateDate(LocalDateTime tm) {
        int yy = (tm.getYear() & 0x007f);
        int mm = ((tm.getMonth().ordinal() + 1) & 0x000f);
        int dd = (tm.getDayOfMonth() & 0x001f);

        short date = (short) ((yy << 9) | (mm << 5) | dd);
        data.data().date = basic.orderUint16(date);
    }

    /** Whether item has address */
    @Override
    public boolean hasAddress() {
        return true;
    }

    /** Returns start address */
    @Override
    public int getStartAddress() {
        return basic.orderUint16(data.data().loadAddress) & 0xffff;
    }

    /** Returns end address */
    @Override
    public int getEndAddress() {
        return getStartAddress() + getFileSize() - 1;
    }

    /** Returns execution address */
    @Override
    public int getExecuteAddress() {
        return basic.orderUint16(data.data().execAddress) & 0xffff;
    }

    /** Set start address */
    @Override
    public void setStartAddress(int val) {
        data.data().loadAddress = basic.orderUint16((short) val);
    }

    /** Set execution address */
    @Override
    public void setExecuteAddress(int val) {
        data.data().execAddress = basic.orderUint16((short) val);
    }

    /** Size of directory item */
    @Override
    public int getDataSize() {
        return data.getDataSize();
    }

    /** Returns item */
    @Override
    public DirectoryM68FDos getData() {
        return data.data();
    }

    /** Copy item */
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

    /** Clear directory: during creation of a new file */
    @Override
    public void clearData() {
        if (!data.isValid()) return;

        data.fill(0);
    }

    /** Whether data needs to be chained (is non-contiguous data) */
    @Override
    public boolean needChainInData() {
        return ((getFileType1() & FILETYPE_M68_FDOS_C) == 0);
    }

    /** Processing required before exporting data */
    public boolean preExportDataFile(String filename) {
        return true;
    }

    /** Generate internal file name from file path before displaying import dialog */
    public boolean preImportDataFile(String filename) {
        return true;
    }

    /** Determine attribute from file name */
    @Override
    public int convOriginalTypeFromFileName(String filename) {
        int[] t1 = {0};

        isContainAttrByExtension(filename, extNameM68FDos, 0, EXT_NAME_M68_FDOS_END - 1, null, t1, null);

        return t1[0];
    }

    /** Process attribute values */
    @Override
    public boolean processAttr(DiskBasicDirItemAttr attr, DiskBasicError errinfo) {
        return true;
    }

    /** Set internal data displayed in properties */
    @Override
    public void setInternalDataInAttrDialog(KeyValArray vals) {
        vals.add("NAME", data.data().name.b, data.data().name.b.length);
        vals.add("EXT", data.data().ext.b, data.data().ext.b.length);
        vals.add("ATTR1", (byte) data.data().attr1, basic.isBigEndian());
        vals.add("ATTR2", (byte) data.data().attr2, basic.isBigEndian());
        vals.add("BLOCK_SIZE", (byte) data.data().blockSize, basic.isBigEndian());
        vals.add("EOF_IN_SEC", data.data().eofInSector);
        vals.add("DATE", (byte) data.data().date, basic.isBigEndian());
        vals.add("TIME?", (byte) data.data().time, basic.isBigEndian());
        vals.add("REV", data.data().rev.b, data.data().rev.b.length);
        vals.add("START_SECTOR", (byte) data.data().startSector, basic.isBigEndian());
        vals.add("ATTR3", data.data().attr3);
        vals.add("END_SECTOR?", (byte) data.data().endSector, basic.isBigEndian());
        vals.add("LOAD_ADDR", (byte) data.data().loadAddress, basic.isBigEndian());
        vals.add("EXEC_ADDR", (byte) data.data().execAddress, basic.isBigEndian());
        vals.add("UNKNOWN2", data.data().unknown2, data.data().unknown2.length);
    }
}
