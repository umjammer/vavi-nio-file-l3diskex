///
/// @author Copyright (c) Sasaji. All rights reserved.
///

package l3diskex.basicfmt.diritem;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
import l3diskex.basicfmt.BasicCommon.DirectoryT;
import l3diskex.basicfmt.BasicCommon.DirectoryXdos;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.BasicCommon.KeyValArray;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.diritem.DiskBasicDirItemXDOS.DiskBasicDirItemXDOSChain;
import l3diskex.basicfmt.diritem.DiskBasicDirItemXDOS.XdosChainT;
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


/// ディレクトリ１アイテム X-DOS Base
public class DiskBasicDirItemXDOS extends DiskBasicDirItemXDOSBase<DirectoryXdos> {

    private static final ResourceBundle rb = ResourceBundle.getBundle("messages");

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

    static class XdosSegT {

        byte track;
        byte sector;
        byte size;
    }

    /// X-DOSチェイン情報 (FAM)
    static class XdosChainT {

        XdosSegT[] seg = new XdosSegT[170];

        public XdosChainT() {
            for (int i = 0; i < 170; i++) {
                seg[i] = new XdosSegT();
            }
        }
    }

    /// X-DOS属性値
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

    public static final Map<String, Object> gTypeNameXDOS1 = new LinkedHashMap<>() {{
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

    public static final String[] gTypeNameXDOS2 = {
        ("Hidden"),				// 0x80
        ("Write Protected"),	// 0x40
        ("System"),				// 0x20
        ("Kanji"),				// 0x10
    };

    static final int TYPE_NAME_XDOS2_HIDDEN = 0;
    static final int TYPE_NAME_XDOS2_READONLY = 1;
    static final int TYPE_NAME_XDOS2_SYSTEM = 2;
    static final int TYPE_NAME_XDOS2_KANJI = 3;

    /// X-DOSチェイン情報アクセス
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

    /** ディレクトリデータ */
    private DiskBasicDirData<DirectoryXdos> mData = new DiskBasicDirData<>();

    protected DiskBasicDirItemXDOS(DiskBasic basic, int nNum, DiskBasicGroupItem nGitem, DiskImageSector nSector, int nSecpos, byte[] nData, int dataP, SectorParam nNext, boolean[] nUnuse, boolean[] nInherit) {
        super(basic, nNum, nGitem, nSector, nSecpos, nData, dataP, nNext, nUnuse);
    }

    public DiskBasicDirItemXDOS(DiskBasic basic) {
        super(basic);

        mData.alloc(DirectoryXdos.class);
    }

    public DiskBasicDirItemXDOS(DiskBasic basic, DiskImageSector nSector, int nSecpos, byte[] nData, int dataP) {
        super(basic, nSector, nSecpos, nData, dataP);

        mData.attach(DirectoryXdos.class, nData, dataP);
    }

    public DiskBasicDirItemXDOS(DiskBasic basic, int nNum, DiskBasicGroupItem nGitem, DiskImageSector nSector, int nSecpos, byte[] nData, int dataP, SectorParam nNext, boolean[] nUnuse) throws IOException {
        super(basic, nNum, nGitem, nSector, nSecpos, nData, dataP, nNext, nUnuse);

        mData.attach(DirectoryXdos.class, nData, dataP);

        used(checkUsed(nUnuse[0]));

        // チェインセクタへのポインタをセット
        if (isUsed()) {
            attachChain(getStartGroup(0));
        }

        // ファイルサイズとグループ数を計算
        calcFileSize();

        // 親ディレクトリはツリーに表示しない
        String name = getFileNamePlainStr();
        visibleOnTree(!(isDirectory() && name.equals("!")));
    }

    @Override
    public void setDataPtr(int nNum, DiskBasicGroupItem nGitem, DiskImageSector nSector, int nSecpos, byte[] nData, int dataP, SectorParam nNext) throws IOException {
        super.setDataPtr(nNum, nGitem, nSector, nSecpos, nData, dataP, nNext);

        mData.attach(DirectoryXdos.class, nData, dataP);
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
        return mData.data().ftype & 0xffff;
    }

    @Override
    protected void setFileType1(int val) {
        mData.data().ftype = (short) val;
    }

    /** 属性１の文字列 */
    public String convFileType1Str(int t1) {
        String str = "";
        if (t1 <= 0x0800) {
            str = rb.getString(Utils.keyAt(gTypeNameXDOS1, t1 >> 8));
        } else if (t1 == 0x8000) {
            str = rb.getString(Utils.keyAt(gTypeNameXDOS1, 9));
        } else if ((t1 & 0x8000) != 0) {
            // ユーザファイルタイプ
            str = convUserFileTypeToStr(t1);
        }
        return str;
    }

    @Override
    public int getFileType2() {
        return mData.data().attr & 0xff;
    }

    @Override
    protected void setFileType2(int val) {
        mData.data().attr = (byte) (val & 0xff);
    }

    @Override
    public boolean checkUsed(boolean unuse) {
        int t1 = (getFileType1() >> 8);
        return (!unuse && t1 != 0 && t1 != 0xff);
    }

    /** ユーザーファイルタイプ名を得る */
    private static String convUserFileTypeToStr(int type1) {
        byte[] ext = new byte[4];
        ext[0] = (byte) (((type1 >> 10) & 0x1f) + 0x40);
        ext[1] = (byte) (((type1 >> 5) & 0x1f) + 0x40);
        ext[2] = (byte) ((type1 & 0x1f) + 0x40);
        ext[3] = 0;
        return new String(ext, 0, 3);
    }

    /** ユーザーファイルタイプに変換 */
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
        // "!"は不可
        boolean valid = true;
        String name = getFileNamePlainStr();
        if (name.equals("!")) {
            valid = false;
        }
        return valid;
    }

    @Override
    public boolean delete() {
        // 削除はエントリの先頭にコードを入れるだけ
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
            // ディレクトリの場合
            t1 = FILETYPE_XDOS_DIR << 8;
        } else if (fileType.getFormat() == basic.getFormatTypeNumber()) {
            // 同じOSの場合
            t1 = fileType.getOrigin();
            t2 = t1 >> 16;
            t1 &= 0xffff;
        } else {
            // 違うOSの場合
            if ((ftype & FILE_TYPE_DIRECTORY_MASK.getValue()) != 0) {
                t1 = FILETYPE_XDOS_DIR;
            } else if ((ftype & FILE_TYPE_BASIC_MASK.getValue()) != 0) {
                t1 = FILETYPE_XDOS_BAS;
            } else if ((ftype & FILE_TYPE_MACHINE_MASK.getValue()) != 0) {
                t1 = FILETYPE_XDOS_BIN;
            } else if ((ftype & FILE_TYPE_SYSTEM_MASK.getValue()) != 0) {
                t1 = FILETYPE_XDOS_SYS;
            } else if ((ftype & FILE_TYPE_BINARY_MASK.getValue()) != 0) {
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
        int typ1 = getFileType1();
        switch (typ1 >> 8) {
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

        int typ2 = getFileType2();
        if ((typ2 & FILETYPE_XDOS2_HIDDEN) != 0) {
            val |= FILE_TYPE_HIDDEN_MASK.getValue();
        }
        if ((typ2 & FILETYPE_XDOS2_READONLY) != 0) {
            val |= FILE_TYPE_READONLY_MASK.getValue();
        }
        if ((typ2 & FILETYPE_XDOS2_SYSTEM) != 0) {
            val |= FILE_TYPE_SYSTEM_MASK.getValue();
        }
        if ((typ2 & FILETYPE_XDOS2_KANJI) != 0) {
            val |= FILE_TYPE_KANJI_MASK;
        }

        // 独自属性にはファイル種類そのまま入れる
        int extended = (typ1 | (typ2 << 16));

        return new DiskBasicFileType(basic.getFormatTypeNumber(), val, extended);
    }

    @Override
    public String getFileAttrStr() {
        String str = convFileType1Str(getFileType1());

        int typ2 = getFileType2();
        if ((typ2 & FILETYPE_XDOS2_HIDDEN) != 0) {
            if (!str.isEmpty()) str += ", ";
            str += rb.getString(gTypeNameXDOS2[TYPE_NAME_XDOS2_HIDDEN]);
        }
        if ((typ2 & FILETYPE_XDOS2_READONLY) != 0) {
            if (!str.isEmpty()) str += ", ";
            str += rb.getString(gTypeNameXDOS2[TYPE_NAME_XDOS2_READONLY]);
        }
        if ((typ2 & FILETYPE_XDOS2_SYSTEM) != 0) {
            if (!str.isEmpty()) str += ", ";
            str += rb.getString(gTypeNameXDOS2[TYPE_NAME_XDOS2_SYSTEM]);
        }
        if ((typ2 & FILETYPE_XDOS2_KANJI) != 0) {
            if (!str.isEmpty()) str += ", ";
            str += rb.getString(gTypeNameXDOS2[TYPE_NAME_XDOS2_KANJI]);
        }
        return str;
    }

    @Override
    public void setFileSize(int val) {
        mData.data().fileSize = (short) val;
        groups.setSize(val);
    }

    @Override
    public int getFileSize() {
        return mData.data().fileSize & 0xffff;
    }

    @Override
    public void setStartGroup(int fileunitNum, int val, int size) {
        mData.data().start.track = (byte) (val / basic.diskBasicParam.getSectorsPerTrackOnBasic());
        mData.data().start.sector = (byte) ((val % basic.diskBasicParam.getSectorsPerTrackOnBasic()) + 1);
        mData.data().start.size = (byte) size;
    }

    @Override
    public int getStartGroup(int fileunitNum) {
        return (mData.data().start.track & 0xff) * basic.diskBasicParam.getSectorsPerTrackOnBasic() + (mData.data().start.sector & 0xff) - 1;
    }

    @Override
    public void setExtraGroup(int val) {
        mData.data().start.track = (byte) (val / basic.diskBasicParam.getSectorsPerTrackOnBasic());
        mData.data().start.sector = (byte) ((val % basic.diskBasicParam.getSectorsPerTrackOnBasic()) + 1);
    }

    @Override
    public int getExtraGroup() {
        return (mData.data().start.track & 0xff) * basic.diskBasicParam.getSectorsPerTrackOnBasic() + (mData.data().start.sector & 0xff) - 1;
    }

    @Override
    public void getExtraGroups(List<Integer> arr) {
        arr.add(getExtraGroup());
    }

    @Override
    public LocalDate getFileCreateDate(LocalDateTime tm) {
        int date = mData.data().date & 0xffff;
        return convDateToTm(date);
    }

    @Override
    public LocalTime getFileCreateTime(LocalDateTime tm) {
        int time = mData.data().time & 0xffff;
        return convTimeToTm(time);
    }

    @Override
    public void setFileCreateDate(LocalDateTime tm) {
        if (tm.getYear() >= 0 && tm.getMonth().ordinal() >= 0) {
            int date = convTmToDate(tm);
            mData.data().date = (short) date;
        }
    }

    @Override
    public void setFileCreateTime(LocalDateTime tm) {
        if (tm.getHour() >= 0 && tm.getMinute() >= 0) {
            int time = convTmToTime(tm);
            mData.data().time = (short) time;
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
    public boolean copyData(byte[] val) {
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
        if (!gConfig.isAddExtensionExport()) return true;

        // 拡張子を付加する
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
        if (gConfig.isDecideAttrImport()) {
            int[] p1 = new int[1];
            isContainAttrByExtension(filename[0], gTypeNameXDOS1, TYPE_NAME_XDOS_BIN, TYPE_NAME_XDOS_DIC, filename, null, p1);
            if (!(TYPE_NAME_XDOS_BIN <= p1[0] && p1[0] <= TYPE_NAME_XDOS_DIC)) {
                String fn = Path.of(filename[0]).getFileName().toString();
                // 拡張子は除く
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
        if (isContainAttrByExtension(filename, gTypeNameXDOS1, TYPE_NAME_XDOS_BIN, TYPE_NAME_XDOS_DIC, null, typeRef, null)) {
            t1 = (typeRef[0] << 8);
        } else {
            String ext = Utils.getExt(filename).toUpperCase();
            t1 = convStrToUserFileType(ext);
        }
        return t1;
    }

    /** 属性からリストの位置を返す(プロパティダイアログ用) */
    protected int getFileType1Pos() {
        return getFileType1();
    }

    /** 属性からリストの位置を返す(プロパティダイアログ用) */
    protected int getFileType2Pos() {
        return getFileType2();
    }

    @Override
    public void setInternalDataInAttrDialog(KeyValArray vals) throws IOException {
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
}

/// ディレクトリ１アイテム X-DOS Base
abstract class DiskBasicDirItemXDOSBase<T extends DirectoryT> extends DiskBasicDirItem<T> {

    static final int XDOS_CHAIN_SEGMENTS = 170;

    /** チェイン情報 */
    protected DiskBasicDirItemXDOSChain chain = new DiskBasicDirItemXDOSChain();

    protected DiskBasicDirItemXDOSBase(DiskBasic basic, int nNum, DiskBasicGroupItem nGitem, DiskImageSector nSector, int nSecpos, byte[] nData, int dataP, SectorParam nNext, boolean[] nUnuse, boolean nInherit) {
        super(basic, nNum, nGitem, nSector, nSecpos, nData, dataP, nNext, nUnuse);
    }

    public DiskBasicDirItemXDOSBase(DiskBasic basic) {
        super(basic);

        chain.setBasic(basic);
        chain.alloc();
    }

    public DiskBasicDirItemXDOSBase(DiskBasic basic, DiskImageSector nSector, int nSecpos, byte[] nData, int dataP) {
        super(basic, nSector, nSecpos, nData, dataP);

        chain.setBasic(basic);
        chain.alloc();
    }

    public DiskBasicDirItemXDOSBase(DiskBasic basic, int nNum, DiskBasicGroupItem nGitem, DiskImageSector nSector, int nSecpos, byte[] nData, int dataP, SectorParam nNext, boolean[] nUnuse) {
        super(basic, nNum, nGitem, nSector, nSecpos, nData, dataP, nNext, nUnuse);
    }

    protected boolean allocateItem() {
        return true;
    }

    protected boolean allocateItem(SectorParam next) throws IOException {
        return true;
    }

    /** チェイン情報を設定 */
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

    /** グループを追加する */
    protected void addGroups(int groupNum, int nextGroup, DiskBasicGroups groupItems) {
        int[] trk = new int[1], sid = new int[1], sec = new int[1], div = new int[1], divs = new int[1];
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
        // ディレクトリは不可
        boolean valid = !isDirectory();
        // "!"は不可
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
            // ディレクトリの場合
            int groupNum = getStartGroup(fileunitNum);
            for (int idx = 0; idx < basic.diskBasicParam.getSubDirGroupSize(); idx++) {
                addGroups(groupNum, idx + 1 != basic.diskBasicParam.getSubDirGroupSize() ? groupNum + 1 : 0, groupItems);
                groupNum++;
                calcGroups++;
                calcFileSize += basic.diskBasicParam.getSectorsPerGroup() * basic.getSectorSize();
            }
        } else {
            // ファイルの場合
            // ファイルサイズ
            calcFileSize += getFileSize();

            if (!chain.isValid()) return;

            for (int idx = 0; idx < XDOS_CHAIN_SEGMENTS; idx++) {
                int[] groupNum = new int[1];
                int[] size = new int[1];
                if (!chain.getSegment(idx, groupNum, size)) {
                    break;
                }
                if (idx != 0) {
                    if (groupItems.size() > 0) {
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
    public void setChainSector(DiskImageSector sector, byte[] data, DiskBasicDirItem<T> pitem) throws IOException {
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
    public int recalcFileSizeOnSave(InputStream istream, int fileSize) {
        // ファイルの最終が終端記号で終わっているかを調べる
        // ただし、ファイルサイズがクラスタサイズと合うなら終端記号は不要
        if ((fileSize % (basic.getSectorSize() * basic.diskBasicParam.getSectorsPerGroup())) != 0) {
            fileSize = checkEofCode(istream, fileSize);
        }
        return fileSize;
    }
}
