///
/// @author Copyright (c) Sasaji. All rights reserved.
///

package l3diskex.basicfmt.diritem;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

import l3diskex.Utils;
import l3diskex.basicfmt.BasicCommon.Directory;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.BasicCommon.KeyValArray;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.diritem.DiskBasicDirItemXDOS.DirectoryXDos;
import l3diskex.basicfmt.diritem.DiskBasicDirItemXDOS.DiskBasicDirItemXDOSChain;
import l3diskex.basicfmt.diritem.DiskBasicDirItemXDOS.XDosChain;
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
import static l3diskex.basicfmt.type.DiskBasicTypeXDOS.FORMAT_TYPE_XDOS;


/** Directory 1 item X-DOS Base */
public class DiskBasicDirItemXDOS extends DiskBasicDirItemXDOSBase<DirectoryXDos> {

    private static final ResourceBundle rb = ResourceBundle.getBundle("messages");

    /**
     * X-DOS segment information
     */
    @Serdes(bigEndian = false)
    public static class XDosSeg {

        @Element(sequence = 1)
        public byte track;
        @Element(sequence = 2)
        public byte sector;
        @Element(sequence = 3)
        public byte size;
    }

    /**
     * Directory entry X-DOS X1 (32bytes)
     */
    @Serdes(bigEndian = false)
    public static class DirectoryXDos implements Directory {

        @Element(sequence = 1)
        public short fType; // big endien
        @Element(sequence = 2)
        public byte[] name = new byte[16];
        @Element(sequence = 3)
        public short loadAddr;
        @Element(sequence = 4)
        public short fileSize;
        @Element(sequence = 5)
        public short execAddr;
        @Element(sequence = 6)
        public short date;
        @Element(sequence = 7)
        public short time;
        @Element(sequence = 8)
        public byte attr; // attribute
        @Element(sequence = 9)
        public XDosSeg start = new XDosSeg();

        public static final int SIZE = 32;
    }


    public static class XDosSubType {

        public int start;
        public int end;
        public String desc;

        XDosSubType(int start, int end, String desc) {
            this.start = start;
            this.end = end;
            this.desc = desc;
        }
    }

    private static final XDosSubType[] xDosSubTypes3cmd = {
            new XDosSubType(0x00, 0x00, "Default"),
            new XDosSubType(0x10, 0x10, "SX-BASIC"),
            new XDosSubType(0x11, 0x11, "XASM"),
            new XDosSubType(0x12, 0x12, "XEDIT"),
            new XDosSubType(0x13, 0x13, "SLANG"),
            new XDosSubType(-1, -1, null)
    };

    private static final XDosSubType[] xDosSubTypes5sub = {
            new XDosSubType(0x00, 0x00, "Default"),
            new XDosSubType(0x01, 0x01, "Printer"),
            new XDosSubType(0x10, 0x17, "overley module (turbo/MZ)"),
            new XDosSubType(0x18, 0x1f, "overley module (nomal X1)"),
            new XDosSubType(0x20, 0x2f, "access module"),
            new XDosSubType(-1, -1, null)
    };

    private static final XDosSubType[] xDosSubTypes7sys = {
            new XDosSubType(0x00, 0x00, "X-DOS System (turbo)"),
            new XDosSubType(0x01, 0x01, "X-DOS System (nomal X1)"),
            new XDosSubType(0x02, 0x02, "X-DOS System (MZ-2500)"),
            new XDosSubType(-1, -1, null)
    };

    public static final XDosSubType[][] xDosSubTypes = {
            null,
            null,
            null,
            xDosSubTypes3cmd,
            null,
            xDosSubTypes5sub,
            null,
            xDosSubTypes7sys,
            null,
            null
    };

    /** X-DOS chain information (FAM) */
    @Serdes(bigEndian = false)
    static class XDosChain {

        @Element(sequence = 1)
        XDosSeg[] seg = new XDosSeg[170];

        public XDosChain() {
            for (int i = 0; i < 170; i++) {
                seg[i] = new XDosSeg();
            }
        }
    }

    /// X-DOS attribute values
    static final int FILETYPE_XDOS_NUL = 0x00;
    static final int FILETYPE_XDOS_BIN = 0x01;
    static final int FILETYPE_XDOS_BAS = 0x02;
    static final int FILETYPE_XDOS_CMD = 0x03;
    static final int FILETYPE_XDOS_ASC = 0x04;
    static final int FILETYPE_XDOS_SUB = 0x05;
    static final int FILETYPE_XDOS_BAT = 0x06;
    static final int FILETYPE_XDOS_SYS = 0x07;
    static final int FILETYPE_XDOS_DIC = 0x08;
    static final int FILETYPE_XDOS_DIR = 0x80;

    static final int FILETYPE_XDOS2_HIDDEN = 0x80;
    static final int FILETYPE_XDOS2_READONLY = 0x40;
    static final int FILETYPE_XDOS2_SYSTEM = 0x20;
    static final int FILETYPE_XDOS2_KANJI = 0x10;

    static final int FILE_TYPE_KANJI_MASK = 0x1000000;

    public static final Map<String, Object> typeNameXDOS1 = new LinkedHashMap<>() {{
            put("NUL", FILETYPE_XDOS_NUL); // 0x00
            put("BIN", FILETYPE_XDOS_BIN); // 0x01
            put("BAS", FILETYPE_XDOS_BAS); // 0x02
            put("CMD", FILETYPE_XDOS_CMD); // 0x03
            put("ASC", FILETYPE_XDOS_ASC); // 0x04
            put("SUB", FILETYPE_XDOS_SUB); // 0x05
            put("BAT", FILETYPE_XDOS_BAT); // 0x06
            put("SYS", FILETYPE_XDOS_SYS); // 0x07
            put("DIC", FILETYPE_XDOS_DIC); // 0x08
            put("DIR", FILETYPE_XDOS_DIR); // 0x80
    }};

    static final int TYPE_NAME_XDOS_NUL = 0;
    static final int TYPE_NAME_XDOS_BIN = 1;
    static final int TYPE_NAME_XDOS_BAS = 2;
    static final int TYPE_NAME_XDOS_CMD = 3;
    static final int TYPE_NAME_XDOS_ASC = 4;
    static final int TYPE_NAME_XDOS_SUB = 5;
    static final int TYPE_NAME_XDOS_BAT = 6;
    static final int TYPE_NAME_XDOS_SYS = 7;
    static final int TYPE_NAME_XDOS_DIC = 8;
    static final int TYPE_NAME_XDOS_DIR = 9;

    public static final String[] typeNameXDOS2 = {
        "Hidden",          // 0x80
        "Write Protected", // 0x40
        "System",          // 0x20
        "Kanji",           // 0x10
    };

    static final int TYPE_NAME_XDOS2_HIDDEN = 0;
    static final int TYPE_NAME_XDOS2_READONLY = 1;
    static final int TYPE_NAME_XDOS2_SYSTEM = 2;
    static final int TYPE_NAME_XDOS2_KANJI = 3;

    /** X-DOS chain information access */
    static class DiskBasicDirItemXDOSChain {

        private DiskBasic basic;
        private DiskImageSector sector;
        private XDosChain chain;

        public DiskBasicDirItemXDOSChain() {
            basic = null;
            sector = null;
            chain = null;
        }

        public DiskBasicDirItemXDOSChain(DiskBasicDirItemXDOSChain src) {
            dup(src);
        }

        public void dup(DiskBasicDirItemXDOSChain src) {
            sector = src.sector;
            chain = new XDosChain();
            for (int i = 0; i < 170; i++) {
                chain.seg[i].track = src.chain.seg[i].track;
                chain.seg[i].sector = src.chain.seg[i].sector;
                chain.seg[i].size = src.chain.seg[i].size;
            }
        }

        public void set(DiskBasic basic, DiskImageSector sector, XDosChain chain) {
            this.basic = basic;
            this.sector = sector;
            this.chain = chain;
        }

        public void alloc() {
            chain = new XDosChain();
            for (int i = 0; i < 170; i++) {
                chain.seg[i].track = 0;
                chain.seg[i].sector = 0;
                chain.seg[i].size = 0;
            }
        }

        public void clear() {
            if (chain != null) {
                for (int i = 0; i < 170; i++) {
                    chain.seg[i].track = 0;
                    chain.seg[i].sector = 0;
                    chain.seg[i].size = 0;
                }
            }
            if (sector != null) {
                sector.fill((byte) 0);
            }
        }

        /** Write the in memory chain back onto the sector it came from */
        private void writeBack() {
            if (sector == null || chain == null) return;
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                Serdes.Util.serialize(chain, baos);
                sector.copy(baos.toByteArray(), baos.size());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        public boolean isValid() {
            return chain != null;
        }

        public int getSectorPos(int index) {
            if (chain == null || index >= 170) return 0;
            return (chain.seg[index].track & 0xff) * basic.getSectorsPerTrackOnBasic() + (chain.seg[index].sector & 0xff) - 1;
        }

        public int getSectors() {
            if (chain == null) return 0;
            int count = 0;
            for (int i = 0; i < 170; i++) {
                if (chain.seg[i].track == 0) {
                    break;
                }
                count += (chain.seg[i].size & 0xFF);
            }
            return count;
        }

        public int getSectors(int index) {
            if (chain == null || index >= 170) return 0;
            return chain.seg[index].size & 0xFF;
        }

        public void addSectorPos(int index, int val) {
            if (chain == null || index >= 170) return;
            if (chain.seg[index].size == 0) {
                val++;
                chain.seg[index].track = (byte) (val / basic.getSectorsPerTrackOnBasic());
                chain.seg[index].sector = (byte) (val % basic.getSectorsPerTrackOnBasic());
            }
            chain.seg[index].size++;
            writeBack();
        }

        public void setSectors(int index, int val) {
            if (chain == null || index >= 170) return;
            chain.seg[index].size = (byte) val;
            writeBack();
        }

        public boolean getSegment(int index, int[] groupNum, int[] size) {
            if (chain == null) return false;
            groupNum[0] = (chain.seg[index].track & 0xff) * basic.getSectorsPerTrackOnBasic() + (chain.seg[index].sector & 0xff) - 1;
            size[0] = chain.seg[index].size & 0xff;
            return chain.seg[index].track != 0;
        }

        public void setBasic(DiskBasic basic) {
            this.basic = basic;
        }
    }

    /** Directory data */
    private final DiskBasicDirData<DirectoryXDos> data = new DiskBasicDirData<>();

    protected void init(DiskBasic basic, int num, DiskBasicGroupItem groupItem, DiskImageSector sector, int secPos, byte[] data, int dataP, SectorParam next, boolean[] unuse, boolean[] inherit) throws IOException {
        super.init(basic, num, groupItem, sector, secPos, data, dataP, next, unuse);
    }

    @Override
    public boolean isSupported(int formatType) {
        return formatType == FORMAT_TYPE_XDOS;
    }

    @Override
    public void init(DiskBasic basic) throws IOException {
        super.init(basic);

        data.alloc(DirectoryXDos.class);
    }

    @Override
    public void init(DiskBasic basic, DiskImageSector sector, int sectorPos, byte[] data, int dataP) throws IOException {
        super.init(basic, sector, sectorPos, data, dataP);

        this.data.attach(DirectoryXDos.class, data, dataP);
    }

    @Override
    public void init(DiskBasic basic, int num, DiskBasicGroupItem groupItem, DiskImageSector sector, int sectorPos, byte[] data, int dataP, SectorParam next, boolean[] unuse) throws IOException {
        super.init(basic, num, groupItem, sector, sectorPos, data, dataP, next, unuse);

        this.data.attach(DirectoryXDos.class, data, dataP);

        used(checkUsed(unuse[0]));

        // Set pointer to chain sector
        if (isUsed()) {
            attachChain(getStartGroup(0));
        }

        // Calculate file size and number of groups
        calcFileSize();

        // Do not display parent directory in tree
        String name = getFileNamePlainStr();
        visibleOnTree(!(isDirectory() && name.equals("!")));
    }

    @Override
    public void setData(int num, DiskBasicGroupItem groupItem, DiskImageSector sector, int sectorPos, byte[] data, int dataPos, SectorParam next) throws IOException {
        super.setData(num, groupItem, sector, sectorPos, data, dataPos, next);

        this.data.attach(DirectoryXDos.class, data, dataPos);
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
        return data.data().fType & 0xffff;
    }

    @Override
    protected void setFileType1(int val) {
        data.data().fType = (short) val;
    }

    /** Attribute 1 string */
    public String convFileType1Str(int t1) {
        String str = "";
        if (t1 <= 0x0800) {
            str = rb.getString(Utils.keyAt(typeNameXDOS1, t1 >> 8));
        } else if (t1 == 0x8000) {
            str = rb.getString(Utils.keyAt(typeNameXDOS1, 9));
        } else if ((t1 & 0x8000) != 0) {
            // User file type
            str = convUserFileTypeToStr(t1);
        }
        return str;
    }

    @Override
    public int getFileType2() {
        return data.data().attr & 0xff;
    }

    @Override
    protected void setFileType2(int val) {
        data.data().attr = (byte) (val & 0xff);
    }

    @Override
    public boolean checkUsed(boolean unuse) {
        int t1 = (getFileType1() >> 8);
        return (!unuse && t1 != 0 && t1 != 0xff);
    }

    /** Get user file type name */
    private static String convUserFileTypeToStr(int type1) {
        byte[] ext = new byte[4];
        ext[0] = (byte) (((type1 >> 10) & 0x1f) + 0x40);
        ext[1] = (byte) (((type1 >> 5) & 0x1f) + 0x40);
        ext[2] = (byte) ((type1 & 0x1f) + 0x40);
        ext[3] = 0;
        return new String(ext, 0, 3);
    }

    /** Convert to user file type */
    public static int convStrToUserFileType(String str) {
        int type1 = 0x8000;
        for (int i = 0; i < str.length() && i < 3; i++) {
            char c = str.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                type1 |= (((c - 0x40) & 0x1f) << ((2 - i) * 5));
            }
        }
        return type1;
    }

    @Override
    public boolean check(boolean[] last) {
        if (!data.isValid()) return false;

        boolean valid = true;
        if (data.data().fType == (short) 0xffff && data.data().start.track == (byte) 0xff) {
            last[0] = true;
            return valid;
        }
        return valid;
    }

    @Override
    public boolean isDeletable() {
        // "!" is not allowed
        boolean valid = true;
        String name = getFileNamePlainStr();
        if (name.equals("!")) {
            valid = false;
        }
        return valid;
    }

    @Override
    public boolean delete() {
        // Deletion is simply putting a code at the beginning of the entry
        data.fill(basic.invertUint8(basic.getDeleteCode()), 1);
        used(false);
        return true;
    }

    @Override
    public void setFileAttr(DiskBasicFileType fileType) {
        int fType = fileType.getType();
        if (fType == -1) return;

        int t1 = 0;
        int t2 = 0;
        if (fileType.isDirectory()) {
            // Case of directory
            t1 = FILETYPE_XDOS_DIR << 8;
        } else if (fileType.getFormat() == basic.getFormatTypeNumber()) {
            // Case of same OS
            t1 = fileType.getOrigin();
            t2 = t1 >> 16;
            t1 &= 0xffff;
        } else {
            // Case of different OS
            if ((fType & FILE_TYPE_DIRECTORY_MASK.getValue()) != 0) {
                t1 = FILETYPE_XDOS_DIR;
            } else if ((fType & FILE_TYPE_BASIC_MASK.getValue()) != 0) {
                t1 = FILETYPE_XDOS_BAS;
            } else if ((fType & FILE_TYPE_MACHINE_MASK.getValue()) != 0) {
                t1 = FILETYPE_XDOS_BIN;
            } else if ((fType & FILE_TYPE_SYSTEM_MASK.getValue()) != 0) {
                t1 = FILETYPE_XDOS_SYS;
            } else if ((fType & FILE_TYPE_BINARY_MASK.getValue()) != 0) {
                t1 = FILETYPE_XDOS_CMD;
            }
            t1 <<= 8;
        }
        setFileType1(t1);
        setFileType2(t2);
    }

    @Override
    public DiskBasicFileType getFileAttr() {
        int val = 0;
        int type1 = getFileType1();
        switch (type1 >> 8) {
            case 1:	// OBJ
                val |= FILE_TYPE_MACHINE_MASK.getValue();
                break;
            case 2:	// BAS
                val |= FILE_TYPE_BASIC_MASK.getValue();
                break;
            case 3:	// CMD
                val |= FILE_TYPE_BINARY_MASK.getValue();
                break;
            case 4:	// ASC
                val |= FILE_TYPE_ASCII_MASK.getValue();
                break;
            case 5:	// SUB
                val |= FILE_TYPE_BASIC_MASK.getValue();
                break;
            case 6:	// BAT
                val |= FILE_TYPE_BASIC_MASK.getValue();
                break;
            case 7:	// SYS
                val |= FILE_TYPE_SYSTEM_MASK.getValue();
                break;
            case 8:	// DIC
                val |= FILE_TYPE_DATA_MASK.getValue();
                break;
            case 0x80: // DIR
                val |= FILE_TYPE_DIRECTORY_MASK.getValue();
                break;
            default:
                val |= FILE_TYPE_DATA_MASK.getValue();
                break;
        }

        int type2 = getFileType2();
        if ((type2 & FILETYPE_XDOS2_HIDDEN) != 0) {
            val |= FILE_TYPE_HIDDEN_MASK.getValue();
        }
        if ((type2 & FILETYPE_XDOS2_READONLY) != 0) {
            val |= FILE_TYPE_READONLY_MASK.getValue();
        }
        if ((type2 & FILETYPE_XDOS2_SYSTEM) != 0) {
            val |= FILE_TYPE_SYSTEM_MASK.getValue();
        }
        if ((type2 & FILETYPE_XDOS2_KANJI) != 0) {
            val |= FILE_TYPE_KANJI_MASK;
        }

        // Put file type as is in unique attribute
        int extended = (type1 | (type2 << 16));

        return new DiskBasicFileType(basic.getFormatTypeNumber(), val, extended);
    }

    @Override
    public String getFileAttrStr() {
        String str = convFileType1Str(getFileType1());

        int typ2 = getFileType2();
        if ((typ2 & FILETYPE_XDOS2_HIDDEN) != 0) {
            if (!str.isEmpty()) str += ", ";
            str += rb.getString(typeNameXDOS2[TYPE_NAME_XDOS2_HIDDEN]);
        }
        if ((typ2 & FILETYPE_XDOS2_READONLY) != 0) {
            if (!str.isEmpty()) str += ", ";
            str += rb.getString(typeNameXDOS2[TYPE_NAME_XDOS2_READONLY]);
        }
        if ((typ2 & FILETYPE_XDOS2_SYSTEM) != 0) {
            if (!str.isEmpty()) str += ", ";
            str += rb.getString(typeNameXDOS2[TYPE_NAME_XDOS2_SYSTEM]);
        }
        if ((typ2 & FILETYPE_XDOS2_KANJI) != 0) {
            if (!str.isEmpty()) str += ", ";
            str += rb.getString(typeNameXDOS2[TYPE_NAME_XDOS2_KANJI]);
        }
        return str;
    }

    @Override
    public void setFileSize(int val) {
        data.data().fileSize = (short) val;
        groups.setSize(val);
    }

    @Override
    public int getFileSize() {
        return data.data().fileSize & 0xffff;
    }

    @Override
    public void setStartGroup(int fileUnitNum, int val, int size) {
        data.data().start.track = (byte) (val / basic.getSectorsPerTrackOnBasic());
        data.data().start.sector = (byte) ((val % basic.getSectorsPerTrackOnBasic()) + 1);
        data.data().start.size = (byte) size;
    }

    @Override
    public int getStartGroup(int fileUnitNum) {
        return (data.data().start.track & 0xff) * basic.getSectorsPerTrackOnBasic() + (data.data().start.sector & 0xff) - 1;
    }

    @Override
    public void setExtraGroup(int val) {
        data.data().start.track = (byte) (val / basic.getSectorsPerTrackOnBasic());
        data.data().start.sector = (byte) ((val % basic.getSectorsPerTrackOnBasic()) + 1);
    }

    @Override
    public int getExtraGroup() {
        return (data.data().start.track & 0xff) * basic.getSectorsPerTrackOnBasic() + (data.data().start.sector & 0xff) - 1;
    }

    @Override
    public void getExtraGroups(List<Integer> arr) {
        arr.add(getExtraGroup());
    }

    @Override
    public LocalDate getFileCreateDate(LocalDateTime tm) {
        int date = data.data().date & 0xffff;
        return convDateToTm(date);
    }

    @Override
    public LocalTime getFileCreateTime(LocalDateTime tm) {
        int time = data.data().time & 0xffff;
        return convTimeToTm(time);
    }

    @Override
    public void setFileCreateDate(LocalDateTime tm) {
        if (tm.getYear() >= 0 && tm.getMonth().ordinal() >= 0) {
            int date = convTmToDate(tm);
            data.data().date = (short) date;
        }
    }

    @Override
    public void setFileCreateTime(LocalDateTime tm) {
        if (tm.getHour() >= 0 && tm.getMinute() >= 0) {
            int time = convTmToTime(tm);
            data.data().time = (short) time;
        }
    }

    @Override
    public String getFileCreateDateTimeTitle() {
        return "Created Date";
    }

    private static LocalDate convDateToTm(int date) {
        return LocalDate.of(
                ((date & 0xfe00) >> 9) + 80,
                ((date & 0x01e0) >> 5) - 1,
                date & 0x001f);
    }

    private static LocalTime convTimeToTm(int time) {
        return LocalTime.of(
                (time & 0xf800) >> 11,
                (time & 0x07e0) >> 5,
                (time & 0x001f) << 1);
    }

    private static int convTmToDate(LocalDateTime tm) {
        return (((tm.getYear() - 80) & 0x7f) << 9) |
                (((tm.getMonth().ordinal() + 1) & 0xf) << 5) |
                (tm.getDayOfMonth() & 0x1f);
    }

    private static int convTmToTime(LocalDateTime tm) {
        return ((tm.getHour() & 0x1f) << 11) |
                ((tm.getMinute() & 0x3f) << 5) |
                ((tm.getSecond() & 0x3f) >> 1);
    }

    @Override
    public int getStartAddress() {
        int addr = data.data().loadAddr;
        return basic.orderUint16((short) addr);
    }

    @Override
    public int getExecuteAddress() {
        int addr = data.data().execAddr;
        return basic.orderUint16((short) addr);
    }

    @Override
    public void setStartAddress(int val) {
        data.data().loadAddr = basic.orderUint16((short) val);
    }

    @Override
    public void setExecuteAddress(int val) {
        data.data().execAddr = basic.orderUint16((short) val);
    }

    @Override
    public int getDataSize() {
        return data.getDataSize();
    }

    @Override
    public DirectoryXDos getData() {
        return data.data();
    }

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

    @Override
    public void clearData() {
        data.fill(basic.getDeleteCode(), getDataSize());
    }

    @Override
    public void initialData() {
        data.fill(basic.getFillCodeOnDir(), getDataSize());
    }

    @Override
    public boolean preExportDataFile(String[] filename) {
        if (!config.isAddExtensionExport()) return true;

        // Attach extension
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
            int[] p1 = new int[1];
            isContainAttrByExtension(filename[0], typeNameXDOS1, TYPE_NAME_XDOS_BIN, TYPE_NAME_XDOS_DIC, filename, null, p1);
            if (!(TYPE_NAME_XDOS_BIN <= p1[0] && p1[0] <= TYPE_NAME_XDOS_DIC)) {
                String fn = Path.of(filename[0]).getFileName().toString();
                // Extension is excluded
                filename[0] = fn;
            }
        }
        filename[0] = remakeFileNameAndExtStr(filename[0]);
        return true;
    }

    @Override
    public int convOriginalTypeFromFileName(String filename) {
        int t1 = 0;
        int[] typeRef = new int[1];
        if (isContainAttrByExtension(filename, typeNameXDOS1, TYPE_NAME_XDOS_BIN, TYPE_NAME_XDOS_DIC, null, typeRef, null)) {
            t1 = (typeRef[0] << 8);
        } else {
            String ext = Utils.getExt(filename).toUpperCase();
            t1 = convStrToUserFileType(ext);
        }
        return t1;
    }

    /** Returns position in list from attribute (for property dialog) */
    protected int getFileType1Pos() {
        return getFileType1();
    }

    /** Returns position in list from attribute (for property dialog) */
    protected int getFileType2Pos() {
        return getFileType2();
    }

    @Override
    public void setInternalDataInAttrDialog(KeyValArray vals) throws IOException {
        vals.add("FTYPE", (byte) data.data().fType, true);
        vals.add("NAME", data.data().name, data.data().name.length);
        vals.add("LOAD_ADDR", (byte) data.data().loadAddr, basic.isBigEndian());
        vals.add("FILE_SIZE", (byte) data.data().fileSize, basic.isBigEndian());
        vals.add("EXEC_ADDR", (byte) data.data().execAddr, basic.isBigEndian());
        vals.add("DATE", (byte) data.data().date, true);
        vals.add("TIME", (byte) data.data().time, true);
        vals.add("ATTR", data.data().attr);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        Serdes.Util.serialize(data.data().start, os);
        vals.add("START", os.toByteArray(), os.size());
    }
}

/** Directory 1 item X-DOS Base */
abstract class DiskBasicDirItemXDOSBase<T extends Directory> extends DiskBasicDirItem<T> {

    static final int XDOS_CHAIN_SEGMENTS = 170;

    /** Chain information */
    protected DiskBasicDirItemXDOSChain chain = new DiskBasicDirItemXDOSChain();

    protected void init(DiskBasic basic, int num, DiskBasicGroupItem groupItem, DiskImageSector sector, int secPos, byte[] data, int dataP, SectorParam next, boolean[] unuse, boolean inherit) throws IOException {
        super.init(basic, num, groupItem, sector, secPos, data, dataP, next, unuse);
    }

    @Override
    public void init(DiskBasic basic) throws IOException {
        super.init(basic);

        chain.setBasic(basic);
        chain.alloc();
    }

    @Override
    public void init(DiskBasic basic, DiskImageSector sector, int sectorPos, byte[] data, int dataP) throws IOException {
        super.init(basic, sector, sectorPos, data, dataP);

        chain.setBasic(basic);
        chain.alloc();
    }

    @Override
    public void init(DiskBasic basic, int num, DiskBasicGroupItem groupItem, DiskImageSector sector, int sectorPos, byte[] data, int dataP, SectorParam next, boolean[] unuse) throws IOException {
        super.init(basic, num, groupItem, sector, sectorPos, data, dataP, next, unuse);
    }

    protected boolean allocateItem() {
        return true;
    }

    protected boolean allocateItem(SectorParam next) throws IOException {
        return true;
    }

    /** Set chain information */
    protected void attachChain(int groupNum) throws IOException {
        if (groupNum != 0) {
            DiskImageSector sector = basic.getSectorFromGroup(groupNum);
            if (sector != null) {
                XDosChain x = new XDosChain();
                Serdes.Util.deserialize(new ByteArrayInputStream(sector.getSectorBuffer()), x);
                chain.set(basic, sector, x);
            }
        }
    }

    /** Add groups */
    protected void addGroups(int groupNum, int nextGroup, DiskBasicGroups groupItems) {
        int[] track = new int[1], side = new int[1], sector = new int[1], div = new int[1], divs = new int[1];
        track[0] = side[0] = sector[0] = -1;
        basic.calcNumFromSectorPosForGroup(groupNum, track, side, sector, div, divs);
        groupItems.add(groupNum, nextGroup, track[0], side[0], sector[0], sector[0], div[0], divs[0]);
    }

    @Override
    public boolean isFileNameEditable() {
        return isDeletable();
    }

    @Override
    public boolean isLoadable() {
        return isDeletable();
    }

    @Override
    public boolean isCopyable() {
        return isDeletable();
    }

    @Override
    public boolean isOverWritable() {
        // Directory is not allowed
        boolean valid = !isDirectory();
        // "!" is not allowed
        valid &= isDeletable();
        return valid;
    }

    @Override
    public void calcFileUnitSize(int fileUnitNum) {
        if (!isUsed()) return;

        getUnitGroups(fileUnitNum, groups);
    }

    @Override
    public void getUnitGroups(int fileUnitNum, DiskBasicGroups groupItems) {
        int calcFileSize = 0;
        int calcGroups = 0;

        if (getFileAttr().isDirectory()) {
            // Case of directory
            int groupNum = getStartGroup(fileUnitNum);
            for (int idx = 0; idx < basic.getSubDirGroupSize(); idx++) {
                addGroups(groupNum, idx + 1 != basic.getSubDirGroupSize() ? groupNum + 1 : 0, groupItems);
                groupNum++;
                calcGroups++;
                calcFileSize += basic.getSectorsPerGroup() * basic.getSectorSize();
            }
        } else {
            // Case of file
            // File size
            calcFileSize += getFileSize();

            if (!chain.isValid()) return;

            for (int index = 0; index < XDOS_CHAIN_SEGMENTS; index++) {
                int[] groupNum = new int[1];
                int[] sgoupSize = new int[1];
                if (!chain.getSegment(index, groupNum, sgoupSize)) {
                    break;
                }
                if (index != 0) {
                    if (groupItems.size() > 0) {
                        DiskBasicGroupItem gitem = groupItems.last();
                        gitem.next = groupNum[0];
                    }
                }
                for (int size = 0; size < sgoupSize[0]; size++) {
                    int nextGrp = (size + 1 != sgoupSize[0] ? groupNum[0] + 1 : 0);
                    addGroups(groupNum[0], nextGrp, groupItems);
                    groupNum[0]++;
                    calcGroups++;
                }
            }
        }
        groupItems.setNums(calcGroups);
        groupItems.setSize(calcFileSize);
        groupItems.setSizePerGroup(basic.getSectorSize() * basic.getSectorsPerGroup());
    }

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

    @Override
    public void setChainSector(DiskImageSector sector, byte[] data, DiskBasicDirItem<T> pItem) throws IOException {
        XDosChain x = new XDosChain();
        Serdes.Util.deserialize(data, x);
        chain.set(basic, sector, x);
    }

    @Override
    public void addChainGroupNumber(int index, int val) {
        chain.addSectorPos(index, val);
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
    public boolean hasCreateTime() {
        return true;
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
        return Utils.formatHMSStr(lt);
    }

    @Override
    public boolean hasAddress() {
        return true;
    }

    @Override
    public boolean needCheckEofCode() {
        return getFileAttr().isAscii();
    }

    @Override
    public int recalcFileSizeOnSave(InputStream iStream, int fileSize) throws IOException {
        // Check if the end of the file ends with a termination symbol
        // However, if the file size matches the cluster size, the termination symbol is not required
        if ((fileSize % (basic.getSectorSize() * basic.getSectorsPerGroup())) != 0) {
            fileSize = checkEofCode(iStream, fileSize);
        }
        return fileSize;
    }
}
