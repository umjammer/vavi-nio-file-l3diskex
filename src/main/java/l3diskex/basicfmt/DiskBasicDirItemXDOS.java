package l3diskex.basicfmt;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import l3diskex.Utils;
import l3diskex.basicfmt.BasicCommon.DirectoryT;
import l3diskex.basicfmt.BasicCommon.DirectoryXdos;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.BasicCommon.KeyValArray;
import l3diskex.basicfmt.BasicFmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicDirItemXDOS.DiskBasicDirItemXDOSChain;
import l3diskex.basicfmt.DiskBasicDirItemXDOS.XdosChainT;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;
import vavi.util.serdes.Serdes;

import static l3diskex.Config.gConfig;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_ASCII_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BASIC_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BINARY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_DATA_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_DIRECTORY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_HIDDEN_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_MACHINE_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_READONLY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_SYSTEM_MASK;


public class DiskBasicDirItemXDOS extends DiskBasicDirItemXDOSBase<DirectoryXdos> {

    static class XdosSegT {

        byte track;
        byte sector;
        byte size;
    }

    static class XdosChainT {

        XdosSegT[] seg = new XdosSegT[170];

        public XdosChainT() {
            for (int i = 0; i < 170; i++) {
                seg[i] = new XdosSegT();
            }
        }
    }

    enum FileTypeXdos {
        FILETYPE_XDOS_NUL(0x00),
        FILETYPE_XDOS_BIN(0x01),
        FILETYPE_XDOS_BAS(0x02),
        FILETYPE_XDOS_CMD(0x03),
        FILETYPE_XDOS_ASC(0x04),
        FILETYPE_XDOS_SUB(0x05),
        FILETYPE_XDOS_BAT(0x06),
        FILETYPE_XDOS_SYS(0x07),
        FILETYPE_XDOS_DIC(0x08),
        FILETYPE_XDOS_DIR(0x80);

        private final int value;

        FileTypeXdos(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    static class DiskBasicDirItemXDOSChain {

        private DiskBasic basic;
        private DiskImageSector sector;
        private XdosChainT chain;
        private boolean chainOwnmake;

        public DiskBasicDirItemXDOSChain() {
            basic = null;
            sector = null;
            chain = null;
            chainOwnmake = false;
        }

        public DiskBasicDirItemXDOSChain(DiskBasicDirItemXDOSChain src) {
            dup(src);
        }

        public void dup(DiskBasicDirItemXDOSChain src) {
            sector = src.sector;
            if (src.chainOwnmake) {
                chain = new XdosChainT();
                for (int i = 0; i < 170; i++) {
                    chain.seg[i].track = src.chain.seg[i].track;
                    chain.seg[i].sector = src.chain.seg[i].sector;
                    chain.seg[i].size = src.chain.seg[i].size;
                }
            } else {
                chain = src.chain;
            }
            chainOwnmake = src.chainOwnmake;
        }

        public void set(DiskBasic nBasic, DiskImageSector nSector, XdosChainT nChain) {
            basic = nBasic;
            sector = nSector;
            if (chainOwnmake) chain = null;
            chain = nChain;
            chainOwnmake = false;
        }

        public void alloc() {
            if (chainOwnmake) chain = null;
            chain = new XdosChainT();
            chainOwnmake = true;
            for (int i = 0; i < 170; i++) {
                chain.seg[i].track = 0;
                chain.seg[i].sector = 0;
                chain.seg[i].size = 0;
            }
        }

        public void clear() {
            if (sector != null) {
                sector.fill((byte) 0);
            } else if (chain != null) {
                for (int i = 0; i < 170; i++) {
                    chain.seg[i].track = 0;
                    chain.seg[i].sector = 0;
                    chain.seg[i].size = 0;
                }
            }
        }

        public boolean isValid() {
            return chain != null;
        }

        public int getSectorPos(int idx) {
            if (chain == null || idx >= 170) return 0;
            return (chain.seg[idx].track & 0xFF) * basic.diskBasicParam.getSectorsPerTrackOnBasic() + (chain.seg[idx].sector & 0xFF) - 1;
        }

        public int getSectors() {
            if (chain == null) return 0;
            int cnt = 0;
            for (int i = 0; i < 170; i++) {
                if (chain.seg[i].track == 0) {
                    break;
                }
                cnt += (chain.seg[i].size & 0xFF);
            }
            return cnt;
        }

        public int getSectors(int idx) {
            if (chain == null || idx >= 170) return 0;
            return chain.seg[idx].size & 0xFF;
        }

        public void addSectorPos(int idx, int val) {
            if (chain == null || idx >= 170) return;
            if (chain.seg[idx].size == 0) {
                val++;
                chain.seg[idx].track = (byte) (val / basic.diskBasicParam.getSectorsPerTrackOnBasic());
                chain.seg[idx].sector = (byte) (val % basic.diskBasicParam.getSectorsPerTrackOnBasic());
            }
            chain.seg[idx].size++;
        }

        public void setSectors(int idx, int val) {
            if (chain == null || idx >= 170) return;
            chain.seg[idx].size = (byte) val;
        }

        public boolean getSegment(int idx, int[] groupNum, int[] size) {
            if (chain == null) return false;
            groupNum[0] = (chain.seg[idx].track & 0xFF) * basic.diskBasicParam.getSectorsPerTrackOnBasic() + (chain.seg[idx].sector & 0xFF) - 1;
            size[0] = chain.seg[idx].size & 0xFF;
            return chain.seg[idx].track != 0;
        }

        public void setBasic(DiskBasic nBasic) {
            basic = nBasic;
        }
    }

    private DiskBasicDirData<DirectoryXdos> mData;

    protected DiskBasicDirItemXDOS(DiskBasic basic, int nNum, DiskBasicGroupItem nGitem, DiskImageSector nSector, int nSecpos, byte[] nData, SectorParam nNext, boolean[] nUnuse, boolean[] nInherit) {
        super(basic, nNum, nGitem, nSector, nSecpos, nData, nNext, nUnuse);
    }

    public DiskBasicDirItemXDOS(DiskBasic basic) {
        super(basic);
        mData = new DiskBasicDirData<>();
        mData.alloc(DirectoryXdos.class);
    }

    public DiskBasicDirItemXDOS(DiskBasic basic, DiskImageSector nSector, int nSecpos, byte[] nData) {
        super(basic, nSector, nSecpos, nData);
        mData = new DiskBasicDirData<>();
        mData.attach(nData);
    }

    public DiskBasicDirItemXDOS(DiskBasic basic, int nNum, DiskBasicGroupItem nGitem, DiskImageSector nSector, int nSecpos, byte[] nData, SectorParam nNext, boolean[] nUnuse) throws IOException {
        super(basic, nNum, nGitem, nSector, nSecpos, nData, nNext, nUnuse);
        mData = new DiskBasicDirData<>();
        mData.attach(nData);

        used(checkUsed(nUnuse[0]));

        if (isUsed()) {
            attachChain(getStartGroup(0));
        }

        calcFileSize();

        String name = getFileNamePlainStr();
        visibleOnTree(!(isDirectory() && name.equals("!")));
    }

    @Override
    public void setDataPtr(int nNum, DiskBasicGroupItem nGitem, DiskImageSector nSector, int nSecpos, byte[] nData, SectorParam nNext) throws IOException {
        super.setDataPtr(nNum, nGitem, nSector, nSecpos, nData, nNext);
        mData.attach(nData);
    }

    @Override
    protected byte[] getFileNamePos(int num, int[] size, int[] len) {
        if (num == 0) {
            size[0] = len[0] = mData.data().name.length;
            return mData.data().name;
        } else {
            size[0] = len[0] = 0;
            return null;
        }
    }

    @Override
    public int getFileType1() {
        return Short.reverseBytes(mData.data().ftype) & 0xffff;
    }

    @Override
    protected void setFileType1(int val) {
        mData.data().ftype = Short.reverseBytes((short) val);
    }

    public String convFileType1Str(int t1) {
        String str = "";
        if (t1 <= 0x0800) {
            str = Utils.keyAt(gTypeNameXDOS1, t1 >> 8);
        } else if (t1 == 0x8000) {
            str = Utils.keyAt(gTypeNameXDOS1, 9);
        } else if ((t1 & 0x8000) != 0) {
            str = convUserFileTypeToStr(t1);
        }
        return str;
    }

    @Override
    public int getFileType2() {
        return mData.data().attr & 0xFF;
    }

    @Override
    protected void setFileType2(int val) {
        mData.data().attr = (byte) (val & 0xFF);
    }

    @Override
    public boolean checkUsed(boolean unuse) {
        int t1 = (getFileType1() >> 8);
        return (!unuse && t1 != 0 && t1 != 0xff);
    }

    private static String convUserFileTypeToStr(int type1) {
        byte[] ext = new byte[4];
        ext[0] = (byte) (((type1 >> 10) & 0x1f) + 0x40);
        ext[1] = (byte) (((type1 >> 5) & 0x1f) + 0x40);
        ext[2] = (byte) ((type1 & 0x1f) + 0x40);
        ext[3] = 0;
        return new String(ext, 0, 3);
    }

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
        if (!mData.isValid()) return false;
        boolean valid = true;
        if (mData.data().ftype == (short) 0xffff && mData.data().start.track == (byte) 0xff) {
            last[0] = true;
            return valid;
        }
        return valid;
    }

    @Override
    public boolean isDeletable() {
        boolean valid = true;
        String name = getFileNamePlainStr();
        if (name.equals("!")) {
            valid = false;
        }
        return valid;
    }

    @Override
    public boolean delete() {
        mData.fill(basic.invertUint8(basic.diskBasicParam.getDeleteCode()), 1);
        used(false);
        return true;
    }

    @Override
    public void setFileAttr(DiskBasicFileType fileType) {
        int ftype = fileType.getType();
        if (ftype == -1) return;

        int t1 = 0;
        int t2 = 0;
        if (fileType.isDirectory()) {
            t1 = FileTypeXdos.FILETYPE_XDOS_DIR.getValue() << 8;
        } else if (fileType.getFormat() == basic.getFormatTypeNumber()) {
            t1 = fileType.getOrigin();
            t2 = t1 >> 16;
            t1 &= 0xffff;
        } else {
            if ((ftype & FILE_TYPE_DIRECTORY_MASK.getValue()) != 0) {
                t1 = FileTypeXdos.FILETYPE_XDOS_DIR.getValue();
            } else if ((ftype & FILE_TYPE_BASIC_MASK.getValue()) != 0) {
                t1 = FileTypeXdos.FILETYPE_XDOS_BAS.getValue();
            } else if ((ftype & FILE_TYPE_MACHINE_MASK.getValue()) != 0) {
                t1 = FileTypeXdos.FILETYPE_XDOS_BIN.getValue();
            } else if ((ftype & FILE_TYPE_SYSTEM_MASK.getValue()) != 0) {
                t1 = FileTypeXdos.FILETYPE_XDOS_SYS.getValue();
            } else if ((ftype & FILE_TYPE_BINARY_MASK.getValue()) != 0) {
                t1 = FileTypeXdos.FILETYPE_XDOS_CMD.getValue();
            }
            t1 <<= 8;
        }
        setFileType1(t1);
        setFileType2(t2);
    }

    @Override
    public DiskBasicFileType getFileAttr() {
        int val = 0;
        int typ1 = getFileType1();
        switch (typ1 >> 8) {
            case 1:
                val |= FILE_TYPE_MACHINE_MASK.getValue();
                break;
            case 2:
                val |= FILE_TYPE_BASIC_MASK.getValue();
                break;
            case 3:
                val |= FILE_TYPE_BINARY_MASK.getValue();
                break;
            case 4:
                val |= FILE_TYPE_ASCII_MASK.getValue();
                break;
            case 5:
                val |= FILE_TYPE_BASIC_MASK.getValue();
                break;
            case 6:
                val |= FILE_TYPE_BASIC_MASK.getValue();
                break;
            case 7:
                val |= FILE_TYPE_SYSTEM_MASK.getValue();
                break;
            case 8:
                val |= FILE_TYPE_DATA_MASK.getValue();
                break;
            case 0x80:
                val |= FILE_TYPE_DIRECTORY_MASK.getValue();
                break;
            default:
                val |= FILE_TYPE_DATA_MASK.getValue();
                break;
        }

        int typ2 = getFileType2();
        if ((typ2 & 0x80) != 0) {
            val |= FILE_TYPE_HIDDEN_MASK.getValue();
        }
        if ((typ2 & 0x40) != 0) {
            val |= FILE_TYPE_READONLY_MASK.getValue();
        }
        if ((typ2 & 0x20) != 0) {
            val |= FILE_TYPE_SYSTEM_MASK.getValue();
        }
        if ((typ2 & 0x10) != 0) {
            val |= 0x1000000;
        }

        int extended = (typ1 | (typ2 << 16));

        return new DiskBasicFileType(basic.getFormatTypeNumber(), val, extended);
    }

    @Override
    public String getFileAttrStr() {
        String str = convFileType1Str(getFileType1());

        int typ2 = getFileType2();
        if ((typ2 & 0x80) != 0) {
            if (!str.isEmpty()) str += ", ";
            str += "Hidden";
        }
        if ((typ2 & 0x40) != 0) {
            if (!str.isEmpty()) str += ", ";
            str += "Write Protected";
        }
        if ((typ2 & 0x20) != 0) {
            if (!str.isEmpty()) str += ", ";
            str += "System";
        }
        if ((typ2 & 0x10) != 0) {
            if (!str.isEmpty()) str += ", ";
            str += "Kanji";
        }
        return str;
    }

    @Override
    public void setFileSize(int val) {
        mData.data().fileSize = Short.reverseBytes((short) val);
        groups.setSize(val);
    }

    @Override
    public int getFileSize() {
        return Short.reverseBytes(mData.data().fileSize) & 0xFFFF;
    }

    @Override
    public void setStartGroup(int fileunitNum, int val, int size) {
        mData.data().start.track = (byte) (val / basic.diskBasicParam.getSectorsPerTrackOnBasic());
        mData.data().start.sector = (byte) ((val % basic.diskBasicParam.getSectorsPerTrackOnBasic()) + 1);
        mData.data().start.size = (byte) size;
    }

    @Override
    public int getStartGroup(int fileunitNum) {
        return (mData.data().start.track & 0xFF) * basic.diskBasicParam.getSectorsPerTrackOnBasic() + (mData.data().start.sector & 0xFF) - 1;
    }

    @Override
    public void setExtraGroup(int val) {
        mData.data().start.track = (byte) (val / basic.diskBasicParam.getSectorsPerTrackOnBasic());
        mData.data().start.sector = (byte) ((val % basic.diskBasicParam.getSectorsPerTrackOnBasic()) + 1);
    }

    @Override
    public int getExtraGroup() {
        return (mData.data().start.track & 0xFF) * basic.diskBasicParam.getSectorsPerTrackOnBasic() + (mData.data().start.sector & 0xFF) - 1;
    }

    @Override
    public void getExtraGroups(List<Integer> arr) {
        arr.add(getExtraGroup());
    }

    @Override
    public LocalDate getFileCreateDate(LocalDateTime tm) {
        int date = Short.reverseBytes(mData.data().date) & 0xFFFF;
        return convDateToTm(date);
    }

    @Override
    public LocalTime getFileCreateTime(LocalDateTime tm) {
        int time = Short.reverseBytes(mData.data().time) & 0xFFFF;
        return convTimeToTm(time);
    }

    @Override
    public void setFileCreateDate(LocalDateTime tm) {
        if (tm.getYear() >= 0 && tm.getMonth().ordinal() >= 0) {
            int date = convTmToDate(tm);
            mData.data().date = Short.reverseBytes((short) date);
        }
    }

    @Override
    public void setFileCreateTime(LocalDateTime tm) {
        if (tm.getHour() >= 0 && tm.getMinute() >= 0) {
            int time = convTmToTime(tm);
            mData.data().time = Short.reverseBytes((short) time);
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
        return (((tm.getYear() - 80) & 0x7f) << 9)
                | (((tm.getMonth().ordinal() + 1) & 0xf) << 5)
                | (tm.getDayOfMonth() & 0x1f);
    }

    private static int convTmToTime(LocalDateTime tm) {
        return ((tm.getHour() & 0x1f) << 11)
                | ((tm.getMinute() & 0x3f) << 5)
                | ((tm.getSecond() & 0x3f) >> 1);
    }

    @Override
    public int getStartAddress() {
        int addr = mData.data().loadAddr;
        return basic.orderUint16((short) addr);
    }

    @Override
    public int getExecuteAddress() {
        int addr = mData.data().execAddr;
        return basic.orderUint16((short) addr);
    }

    @Override
    public void setStartAddress(int val) {
        mData.data().loadAddr = basic.orderUint16((short) val);
    }

    @Override
    public void setExecuteAddress(int val) {
        mData.data().execAddr = basic.orderUint16((short) val);
    }

    @Override
    public int getDataSize() {
        return mData.getDataSize();
    }

    @Override
    public DirectoryXdos getData() {
        return mData.data();
    }

    @Override
    public boolean copyData(DirectoryXdos val) {
        return mData.copy(val, getDataSize());
    }

    @Override
    public void clearData() {
        mData.fill(basic.diskBasicParam.getDeleteCode(), getDataSize());
    }

    @Override
    public void initialData() {
        mData.fill(basic.diskBasicParam.getFillCodeOnDir(), getDataSize());
    }

    @Override
    public boolean preExportDataFile(String[] filename) {
        if (!gConfig.IsAddExtensionExport()) return true;

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
        if (gConfig.IsDecideAttrImport()) {
            int[] p1 = new int[1];
            isContainAttrByExtension(filename[0], gTypeNameXDOS1, 1, 8, filename, null, p1);
            if (!(1 <= p1[0] && p1[0] <= 8)) {
                String fn = Path.of(filename[0]).getFileName().toString();
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
        if (isContainAttrByExtension(filename, gTypeNameXDOS1, 1, 8, null, typeRef, null)) {
            t1 = (typeRef[0] << 8);
        } else {
            String ext = Utils.getExt(filename).toUpperCase();
            t1 = convStrToUserFileType(ext);
        }
        return t1;
    }

    protected int getFileType1Pos() {
        return getFileType1();
    }

    protected int getFileType2Pos() {
        return getFileType2();
    }

    @Override
    public void setInternalDataInAttrDialog(KeyValArray vals) throws IOException {
        vals.add("self", mData.isSelf());
        vals.add("FTYPE", (byte) mData.data().ftype, true);
        vals.add("NAME", mData.data().name, mData.data().name.length);
        vals.add("LOAD_ADDR", (byte) mData.data().loadAddr, basic.isBigEndian());
        vals.add("FILE_SIZE", (byte) mData.data().fileSize, basic.isBigEndian());
        vals.add("EXEC_ADDR", (byte) mData.data().execAddr, basic.isBigEndian());
        vals.add("DATE", (byte) mData.data().date, true);
        vals.add("TIME", (byte) mData.data().time, true);
        vals.add("ATTR", mData.data().attr);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        Serdes.Util.serialize(mData.data().start, os);
        vals.add("START", os.toByteArray(), os.size());
    }

    public static final Map<String, Object> gTypeNameXDOS1 = new HashMap<>() {{
        put("NUL", 0x00);
        put("BIN", 0x01);
        put("BAS", 0x02);
        put("CMD", 0x03);
        put("ASC", 0x04);
        put("SUB", 0x05);
        put("BAT", 0x06);
        put("SYS", 0x07);
        put("DIC", 0x08);
        put("DIR", 0x80);
    }};

    public static final String[] gTypeNameXDOS2 = {
            "Hidden",
            "Write Protected",
            "System",
            "Kanji",
    };

    private static final XdosSubTypeT[] xdosSubTypes3cmd = {
            new XdosSubTypeT(0x00, 0x00, "Default"),
            new XdosSubTypeT(0x10, 0x10, "SX-BASIC"),
            new XdosSubTypeT(0x11, 0x11, "XASM"),
            new XdosSubTypeT(0x12, 0x12, "XEDIT"),
            new XdosSubTypeT(0x13, 0x13, "SLANG"),
            new XdosSubTypeT(-1, -1, null)
    };

    private static final XdosSubTypeT[] xdosSubTypes5sub = {
            new XdosSubTypeT(0x00, 0x00, "Default"),
            new XdosSubTypeT(0x01, 0x01, "Printer"),
            new XdosSubTypeT(0x10, 0x17, "overley module (turbo/MZ)"),
            new XdosSubTypeT(0x18, 0x1f, "overley module (nomal X1)"),
            new XdosSubTypeT(0x20, 0x2f, "access module"),
            new XdosSubTypeT(-1, -1, null)
    };

    private static final XdosSubTypeT[] xdosSubTypes7sys = {
            new XdosSubTypeT(0x00, 0x00, "X-DOS System (turbo)"),
            new XdosSubTypeT(0x01, 0x01, "X-DOS System (nomal X1)"),
            new XdosSubTypeT(0x02, 0x02, "X-DOS System (MZ-2500)"),
            new XdosSubTypeT(-1, -1, null)
    };

    public static final XdosSubTypeT[][] xdosSubTypes = {
            null,
            null,
            null,
            xdosSubTypes3cmd,
            null,
            xdosSubTypes5sub,
            null,
            xdosSubTypes7sys,
            null,
            null
    };

    private static final int INTNAME_NEW_FILE = 0x01;
    private static final int VERTICAL = 0;
    private static final int EVT_COMBOBOX = 0;

    public static class XdosSubTypeT {

        public int start;
        public int end;
        public String desc;

        XdosSubTypeT(int start, int end, String desc) {
            this.start = start;
            this.end = end;
            this.desc = desc;
        }
    }
}

abstract class DiskBasicDirItemXDOSBase<T extends DirectoryT> extends DiskBasicDirItem<T> {

    protected DiskBasicDirItemXDOSChain chain;

    protected DiskBasicDirItemXDOSBase() {
        super();
    }

    protected DiskBasicDirItemXDOSBase(DiskBasicDirItemXDOSBase src) {
        super(src);
    }

    protected DiskBasicDirItemXDOSBase(DiskBasic basic, int nNum, DiskBasicGroupItem nGitem, DiskImageSector nSector, int nSecpos, byte[] nData, SectorParam nNext, boolean[] nUnuse, boolean[] nInherit) {
        super(basic, nNum, nGitem, nSector, nSecpos, nData, nNext, nUnuse);
    }

    public DiskBasicDirItemXDOSBase(DiskBasic basic) {
        super(basic);
        chain = new DiskBasicDirItemXDOSChain();
        chain.setBasic(basic);
        chain.alloc();
    }

    public DiskBasicDirItemXDOSBase(DiskBasic basic, DiskImageSector nSector, int nSecpos, byte[] nData) {
        super(basic, nSector, nSecpos, nData);
        chain = new DiskBasicDirItemXDOSChain();
        chain.setBasic(basic);
        chain.alloc();
    }

    public DiskBasicDirItemXDOSBase(DiskBasic basic, int nNum, DiskBasicGroupItem nGitem, DiskImageSector nSector, int nSecpos, byte[] nData, SectorParam nNext, boolean[] nUnuse) {
        super(basic, nNum, nGitem, nSector, nSecpos, nData, nNext, nUnuse);
    }

    protected boolean allocateItem() {
        return true;
    }

    protected boolean allocateItem(SectorParam next) {
        return true;
    }

    protected void attachChain(int groupNum) throws IOException {
        if (groupNum != 0) {
            DiskImageSector sector = basic.getSectorFromGroup(groupNum);
            if (sector != null) {
                XdosChainT x = new XdosChainT();
                Serdes.Util.deserialize(sector.getSectorBuffer(), x);
                chain.set(basic, sector, x);
            }
        }
    }

    protected void addGroups(int groupNum, int nextGroup, DiskBasicGroups groupItems) {
        int[] trk = new int[1];
        int[] sid = new int[1];
        int[] sec = new int[1];
        int[] div = new int[1];
        int[] divs = new int[1];
        trk[0] = sid[0] = sec[0] = -1;
        basic.calcNumFromSectorPosForGroup(groupNum, trk, sid, sec, div, divs);
        groupItems.add(groupNum, nextGroup, trk[0], sid[0], sec[0], sec[0], div[0], divs[0]);
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
        boolean valid = !isDirectory();
        valid &= isDeletable();
        return valid;
    }

    @Override
    public void calcFileUnitSize(int fileunitNum) {
        if (!isUsed()) return;
        getUnitGroups(fileunitNum, groups);
    }

    @Override
    public void getUnitGroups(int fileunitNum, DiskBasicGroups groupItems) {
        int calcFileSize = 0;
        int calcGroups = 0;

        if (getFileAttr().isDirectory()) {
            int groupNum = getStartGroup(fileunitNum);
            for (int idx = 0; idx < basic.diskBasicParam.getSubDirGroupSize(); idx++) {
                addGroups(groupNum, idx + 1 != basic.diskBasicParam.getSubDirGroupSize() ? groupNum + 1 : 0, groupItems);
                groupNum++;
                calcGroups++;
                calcFileSize += basic.diskBasicParam.getSectorsPerGroup() * basic.getSectorSize();
            }
        } else {
            calcFileSize += getFileSize();
            if (!chain.isValid()) return;

            for (int idx = 0; idx < 170; idx++) {
                int[] groupNum = new int[1];
                int[] size = new int[1];
                if (!chain.getSegment(idx, groupNum, size)) {
                    break;
                }
                if (idx != 0) {
                    if (groupItems.count() > 0) {
                        DiskBasicGroupItem gitem = groupItems.last();
                        gitem.next = groupNum[0];
                    }
                }
                for (int siz = 0; siz < size[0]; siz++) {
                    int nextGrp = (siz + 1 != size[0] ? groupNum[0] + 1 : 0);
                    addGroups(groupNum[0], nextGrp, groupItems);
                    groupNum[0]++;
                    calcGroups++;
                }
            }
        }
        groupItems.setNums(calcGroups);
        groupItems.setSize(calcFileSize);
        groupItems.setSizePerGroup(basic.getSectorSize() * basic.diskBasicParam.getSectorsPerGroup());
    }

    @Override
    public int recalcFileSize(DiskBasicGroups groupItems, int occupiedSize) throws IOException {
        if (groupItems.count() == 0) return occupiedSize;

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
    public void setChainSector(DiskImageSector sector, byte[] data, DiskBasicDirItem pitem) throws IOException {
        XdosChainT x = new XdosChainT();
        Serdes.Util.deserialize(data, x);
        chain.set(basic, sector, x);
    }

    @Override
    public void addChainGroupNumber(int idx, int val) {
        chain.addSectorPos(idx, val);
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
        getFileCreateDate(tm);
        return Utils.formatYMDStr(tm);
    }

    @Override
    public String getFileCreateTimeStr() {
        LocalDateTime tm = LocalDateTime.now();
        getFileCreateTime(tm);
        return Utils.formatHMSStr(tm);
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
    public int recalcFileSizeOnSave(InputStream istream, int fileSize) {
        if ((fileSize % (basic.getSectorSize() * basic.diskBasicParam.getSectorsPerGroup())) != 0) {
            fileSize = checkEofCode(istream, fileSize);
        }
        return fileSize;
    }
}

