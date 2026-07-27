///
/// Copyright (c) Sasaji. All rights reserved.
///

package l3diskex.basicfmt.diritem;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import l3diskex.Common;
import l3diskex.Utils;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.BasicCommon.KeyValArray;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.diritem.DiskBasicDirItemAppleDOS.DirectoryProDos;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;

import static l3diskex.Config.config;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BASIC_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_DIRECTORY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_HIDDEN_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_READONLY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_SYSTEM_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_UNDELETE_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_VOLUME_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_WRITEONLY_MASK;
import static l3diskex.basicfmt.DiskBasicType.INVALID_GROUP_NUMBER;
import static l3diskex.basicfmt.type.DiskBasicTypeProDOS.FORMAT_TYPE_PRODOS;


/// Array of Apple ProDOS index ProDOSOneIndex
public class DiskBasicDirItemProDOS extends DiskBasicDirItem<DirectoryProDos> {

    // wxTRANSLATE("File"), wxTRANSLATE("<DIR>"), wxTRANSLATE("<VOL>"),
    public static final String[] TYPE_NAME_PRODOS1 = {
            "File",
            "<DIR>",
            "<VOL>",
    };

    // Apple ProDOS attribute position STORAGE_TYPE
    public static final int TYPE_NAME_PRODOS_FILE = 0;
    public static final int TYPE_NAME_PRODOS_SUBDIR = 1;
    public static final int TYPE_NAME_PRODOS_VOLUME = 2;

    // Apple ProDOS attribute value STORAGE_TYPE
    public static final int FILETYPE_MASK_PRODOS_DELETED = 0x0;
    public static final int FILETYPE_MASK_PRODOS_SEEDING = 0x1;
    public static final int FILETYPE_MASK_PRODOS_SAPLING = 0x2;
    public static final int FILETYPE_MASK_PRODOS_TREE = 0x3;
    public static final int FILETYPE_MASK_PRODOS_SUBDIR = 0xd;
    public static final int FILETYPE_MASK_PRODOS_SUBVOL = 0xe;
    public static final int FILETYPE_MASK_PRODOS_VOLUME = 0xf;

    // Apple ProDOS attribute position FILE_TYPE
    public static final int TYPE_NAME_PRODOS_NOT = 0;
    public static final int TYPE_NAME_PRODOS_BAD = 1;
    public static final int TYPE_NAME_PRODOS_TXT = 2;
    public static final int TYPE_NAME_PRODOS_BIN = 3;
    public static final int TYPE_NAME_PRODOS_DIR = 4;
    public static final int TYPE_NAME_PRODOS_ADB = 5;
    public static final int TYPE_NAME_PRODOS_AWP = 6;
    public static final int TYPE_NAME_PRODOS_ASP = 7;
    public static final int TYPE_NAME_PRODOS_PAS = 8;
    public static final int TYPE_NAME_PRODOS_CMD = 9;
    public static final int TYPE_NAME_PRODOS_INT = 10;
    public static final int TYPE_NAME_PRODOS_IVR = 11;
    public static final int TYPE_NAME_PRODOS_BAS = 12;
    public static final int TYPE_NAME_PRODOS_VAR = 13;
    public static final int TYPE_NAME_PRODOS_REL = 14;
    public static final int TYPE_NAME_PRODOS_SYS = 15;
    public static final int TYPE_NAME_PRODOS_END = 16;

    // Apple ProDOS attribute value FILE_TYPE
    public static final int FILETYPE_MASK_PRODOS_NOT = 0x00;
    public static final int FILETYPE_MASK_PRODOS_BAD = 0x01;
    public static final int FILETYPE_MASK_PRODOS_TXT = 0x04;
    public static final int FILETYPE_MASK_PRODOS_BIN = 0x06;
    public static final int FILETYPE_MASK_PRODOS_DIR = 0x0f;
    public static final int FILETYPE_MASK_PRODOS_ADB = 0x19;
    public static final int FILETYPE_MASK_PRODOS_AWP = 0x1a;
    public static final int FILETYPE_MASK_PRODOS_ASP = 0x1b;
    public static final int FILETYPE_MASK_PRODOS_PAS = 0xef;
    public static final int FILETYPE_MASK_PRODOS_CMD = 0xf0;
    public static final int FILETYPE_MASK_PRODOS_INT = 0xfa;
    public static final int FILETYPE_MASK_PRODOS_IVR = 0xfb;
    public static final int FILETYPE_MASK_PRODOS_BAS = 0xfc;
    public static final int FILETYPE_MASK_PRODOS_VAR = 0xfd;
    public static final int FILETYPE_MASK_PRODOS_REL = 0xfe;
    public static final int FILETYPE_MASK_PRODOS_SYS = 0xff;

    // gTypeNameProDOS2 from basicdiritem_prodos.cpp
    public static final Map<String, Object> TYPE_NAME_PRODOS2 = new LinkedHashMap<>() {{
        put("<no type>", FILETYPE_MASK_PRODOS_NOT);
        put("BAD", FILETYPE_MASK_PRODOS_BAD);
        put("TXT", FILETYPE_MASK_PRODOS_TXT);
        put("BIN", FILETYPE_MASK_PRODOS_BIN);
        put("DIR", FILETYPE_MASK_PRODOS_DIR);
        put("ADB", FILETYPE_MASK_PRODOS_ADB);
        put("AWB", FILETYPE_MASK_PRODOS_AWP);
        put("ASP", FILETYPE_MASK_PRODOS_ASP);
        put("PAS", FILETYPE_MASK_PRODOS_PAS);
        put("CMD", FILETYPE_MASK_PRODOS_CMD);
        put("INT", FILETYPE_MASK_PRODOS_INT);
        put("IVR", FILETYPE_MASK_PRODOS_IVR);
        put("BAS", FILETYPE_MASK_PRODOS_BAS);
        put("VAR", FILETYPE_MASK_PRODOS_VAR);
        put("REL", FILETYPE_MASK_PRODOS_REL);
        put("SYS", FILETYPE_MASK_PRODOS_SYS);
    }};

    // Apple ProDOS attribute value ACCESS
    public static final int FILETYPE_MASK_PRODOS_DESTROY = 0x80;
    public static final int FILETYPE_MASK_PRODOS_RENAME = 0x40;
    public static final int FILETYPE_MASK_PRODOS_CHANGE = 0x20;
    public static final int FILETYPE_MASK_PRODOS_WRITE = 0x02;
    public static final int FILETYPE_MASK_PRODOS_READ = 0x01;
    public static final int FILETYPE_MASK_PRODOS_ACCESS_ALL = 0xe3;

    // gTypeNameProDOS3 from basicdiritem_prodos.cpp
    public static final Map<String, Object> TYPE_NAME_PRODOS3 = new LinkedHashMap<>() {{
        put("Readable", FILETYPE_MASK_PRODOS_READ);
        put("Writable", FILETYPE_MASK_PRODOS_WRITE);
        put("Changed", FILETYPE_MASK_PRODOS_CHANGE);
        put("Can Rename", FILETYPE_MASK_PRODOS_RENAME);
        put("Can Destroy", FILETYPE_MASK_PRODOS_DESTROY);
    }};

    public static final String TYPE_NAME_PRODOS3S = "rwcnd";

    /** Apple ProDOS chain of directory blocks */
    @Serdes
    public static class ProDOSDirPointer {
        @Element(sequence = 1)
        public short prevBlock;
        @Element(sequence = 2)
        public short nextBlock;
    }

    // One Apple ProDOS index
    static class ProDosOneIndex {

        private final byte[][] buf = new byte[2][];
        private int size;
        private final int groupNum;

        public ProDosOneIndex() {
            size = 0;
            groupNum = -1;
        }

        /** Allocate sector buffer */
        public void attachBuffer(DiskBasic basic, int groupNum, int startSectorPos) {
            DiskImageSector sector = null;
            int bufIndex = 0;
            for(int i=0; i < basic.getSectorsPerGroup() && bufIndex < 2; i++) {
                sector = basic.getSectorFromSectorPos(startSectorPos + i);
                if (sector == null) break;
                size = sector.getSectorSize();
                for(int bufPos = 0; bufPos < size && bufIndex < 2; bufPos += 256) {
                    setBuffer(bufIndex, sector.getSectorBuffer(bufPos));
                    bufIndex++;
                }
            }
        }

        public void setBuffer(int index, byte[] buffer) {
            buf[index] = buffer;
        }

        public short getGroupNumber(int pos) {
            return (short) (((buf[1][pos] & 0xffff) << 8) | (buf[0][pos] & 0xffff));
        }

        public void setGroupNumber(int pos, short val) {
            buf[1][pos] = (byte) (val >>> 8);
            buf[0][pos] = (byte) (val & 0xff);
        }

        public int getSize() {
            return size;
        }

        public int getMyGroupNumber() {
            return groupNum;
        }

        //
        // ProDosIndex
        //

        /**
         * Get the block number at the specified position
         * @param pos Position. Serial number across all indices
         * @return Block number
         */
        public static short getGroupNumber(List<ProDosOneIndex> list, int pos) {
            short groupNum = (short) 0xffff;

            for (ProDosOneIndex item : list) {
                int size = item.getSize();
                if (pos < size) {
                    groupNum = item.getGroupNumber(pos);
                    break;
                }
                pos -= size;
            }
            return groupNum;
        }

        /**
         * Set the block number at the specified position
         * @param pos Position. Serial number across all indices
         * @param val Block number
         */
        public static void setGroupNumber(List<ProDosOneIndex> list, int pos, short val) {
            for (ProDosOneIndex item : list) {
                int size = item.getSize();
                if (pos < size) {
                    item.setGroupNumber(pos, val);
                    break;
                }
                pos -= size;
            }
        }
    }

    //
    // Directory 1 item Apple ProDOS 8 / 16
    //

    /** Directory data */
    DiskBasicDirData<DirectoryProDos> data = new DiskBasicDirData<>();

    /** Pointer inside sector */
    DirItemSectorBoundary sData;

    /** Group number to which this item belongs */
    public int mDirGroupNum;

    private final List<ProDosOneIndex> index = new ArrayList<>();

    @Override
    public boolean isSupported(int formatType) {
        return formatType == FORMAT_TYPE_PRODOS;
    }

    @Override
    public void init(DiskBasic basic) throws IOException {
        super.init(basic);

        data.alloc(DirectoryProDos.class);
        allocateItem(null);

        mDirGroupNum = 0;
    }

    @Override
    public void init(DiskBasic basic, DiskImageSector sector, int sectorPos, byte[] data, int dataP) throws IOException {
        super.init(basic, sector, sectorPos, data, dataP);

        this.data.attach(DirectoryProDos.class, data, dataP);
        allocateItem(null);

        mDirGroupNum = 0;
    }

    @Override
    public void init(DiskBasic basic, int num, DiskBasicGroupItem groupItem, DiskImageSector sector, int sectorPos,
                     byte[] data, int dataP, SectorParam next, boolean[] unuse) throws IOException {
        super.init(basic, num, groupItem, sector, sectorPos, data, dataP, next, unuse);

        this.data.attach(DirectoryProDos.class, data, dataP);
        allocateItem(next);

        mDirGroupNum = type.getSectorPosFromNum(groupItem.track, groupItem.side, sector.getSectorNumber());
        mDirGroupNum = mDirGroupNum / basic.getSectorsPerGroup();

        used(checkUsed(unuse[0]));

        // Set pointer to chain sector
        if (isUsed()) {
            index.clear();
            //index.setBasic(basic);
            int sType = getFileType1();
            switch(sType) {
                case FILETYPE_MASK_PRODOS_SAPLING:
                case FILETYPE_MASK_PRODOS_TREE:
                    int groupNum = getStartGroup(0);

			        //int sectorSize = basic.getSectorSize();
                    int startPos = type.getStartSectorFromGroup(groupNum);

                    ProDosOneIndex item = new ProDosOneIndex();
                    item.attachBuffer(basic, groupNum, startPos);
                    index.add(item);

                    if (sType == FILETYPE_MASK_PRODOS_TREE) {
                        // tree
                        int size = 256;
                        for(int i = 0; i < size; i++) {
                            groupNum = ProDosOneIndex.getGroupNumber(index, i);
                            if (groupNum == 0) {
                                break;
                            }
                            startPos = type.getStartSectorFromGroup(groupNum);

                            item = new ProDosOneIndex();
                            item.attachBuffer(basic, groupNum, startPos);
                            index.add(item);
                        }
                    }
                    break;
            }
        }

        calcFileSize();
    }

    /**
     * Allocate directory entry
     * {@link data} is memory allocated internally
     * {@link #sData} becomes pointer to inside sector
     */
    private boolean allocateItem(SectorParam next) throws IOException {
        sData.clear();
        boolean bound = sData.set(basic, sector, position, data.getRawData(), getDataSize(), next);

        if (bound) {
            // If spanning across sectors, data is allocated internally
            data.alloc(DirectoryProDos.class);
            data.fill(0);
        }

        // Copy
        sData.copyTo(data.getRawData());

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
    protected void setNativeName(byte[] filename, int size, int length) {
        byte[] n;
        int[] nl = {0};
        int[] ns = {0};
        n = getFileNamePos(0, ns, nl);
        if (n != null && ns[0] > 0) {
            System.arraycopy(n, 0, filename, 0, ns[0]);
        }

        nl[0] = Common.trimRight(n, length, basic.getDirTerminateCode());

        // File name length
        data.data().sTypeAndNLen = (byte) ((nl[0] & 0xf) | (data.data().sTypeAndNLen & 0xf0));
    }

    @Override
    protected void getNativeName(byte[] filename, int size, int[] length) {
        byte[] n;
        int[] s = {0};
        int[] l = {0};

        n = getFileNamePos(0, s, l);
        if (n != null && s[0] > 0) {
            if (s[0] > size) s[0] = size;
            System.arraycopy(n, 0, filename, 0, s[0]);
            int nLen = data.data().sTypeAndNLen & 0xf;
            if (nLen < size) filename[nLen] = 0;
        }
        length[0] = l[0];
    }

    @Override
    public int getFileNameStrSize() {
        int[] s = new int[1];
        int[] l = new int[1];
        getFileNamePos(0, s, l);

        return s[0];
    }

    /** Returns attribute 1 STORAGE_TYPE */
    @Override
    public int getFileType1() {
        return (data.data().sTypeAndNLen >> 4) & 0xf;
    }

    /** Set attribute 1 STORAGE_TYPE */
    @Override
    public void setFileType1(int val) {
        data.data().sTypeAndNLen = (byte) ((val << 4) | (data.data().sTypeAndNLen & 0x0f));
    }

    /** Returns attribute 2 FILE_TYPE */
    @Override
    public int getFileType2() {
        return data.data().fileType & 0xff;
    }

    /** Set attribute 2 FILE_TYPE */
    @Override
    public void setFileType2(int val) {
        data.data().fileType = (byte) (val & 0xff);
    }

    /** Returns attribute 3 ACCESS */
    @Override
    public int getFileType3() {
        return data.data().access & 0xff;
    }

    /** Set attribute 3 ACCESS */
    @Override
    public void setFileType3(int val) {
        data.data().access = (byte) (val & 0xff);
    }

    /** Returns AUX_TYPE */
    public int getAuxType() {
        return data.data().aux.f.auxType & 0xffff;
    }

    /** Set AUX_TYPE */
    public void setAuxType(int val) {
        data.data().aux.f.auxType = (short) (val & 0xffff);
    }

    @Override
    public boolean checkUsed(boolean unuse) {
        return getFileType1() != 0;
    }

    /** Returns version (VERSION, MIN_VERSION) */
    public int getVersion() {
        return (data.data().version & 0xff) << 8 | (data.data().minVersion & 0xff);
    }

    /** Set version (VERSION, MIN_VERSION) */
    public void setVersion(int val) {
        data.data().minVersion = (byte) (val & 0xff);
        val >>= 8;
        data.data().version = (byte) (val & 0xff);
    }

    /** Returns number of blocks used */
    public int getBlocksUsed() {
        return data.data().blocksUsed & 0xffff;
    }

    /** Set number of blocks used */
    public void setBlocksUsed(int val) {
        data.data().blocksUsed = (short) (val & 0xffff);
    }

    @Override
    public boolean delete() {
        // Delete
        used(false);
        data.data().sTypeAndNLen = 0;
        return true;
    }

    @Override
    public boolean check(boolean[] last) {
        if (!data.isValid()) return false;

        boolean valid = true;

        int sType = getFileType1();
        if (0x3 < sType && sType < 0xd) {
            valid = false;
        }
        return valid;
    }

    /** Meaning of unique attributes: STORAGE_TYPE, FILE_TYPE, ACCESS */
    @Override
    public void setFileAttr(DiskBasicFileType fileType) {
        int fType = fileType.getType();
        if (fType == -1) return;

        if (fileType.getFormat() == basic.getFormatTypeNumber()) {
            // From same OS
            int t3 = fileType.getOrigin();
            int t2 = t3 >> 8;
            int t1 = t2 >> 8;
            t2 &= 0xff;
            t1 &= 0xff;

            setFileType1(t1);
            if (t1 != FILETYPE_MASK_PRODOS_VOLUME) {
                setFileType2(t2);
            }
            setFileType3(t3);

            int type4 = fileType.getOrigin(1);
            int version = fileType.getOrigin(2);
            setAuxType(type4);
            setVersion(version);

        } else {
            // From different OS
            int t1 = 0;
            int t2 = FILETYPE_MASK_PRODOS_NOT;
            int t3 = FILETYPE_MASK_PRODOS_ACCESS_ALL;
            if ((fType & FILE_TYPE_BASIC_MASK.getValue()) != 0) {
                t2 = FILETYPE_MASK_PRODOS_BAS;
            } else if ((fType & FILE_TYPE_DIRECTORY_MASK.getValue()) != 0) {
                t1 = FILETYPE_MASK_PRODOS_SUBDIR;
                t2 = FILETYPE_MASK_PRODOS_DIR;
            }
            if ((fType & FILE_TYPE_READONLY_MASK.getValue()) != 0) {
                t3 &= ~FILETYPE_MASK_PRODOS_WRITE;
            }
            if ((fType & FILE_TYPE_UNDELETE_MASK.getValue()) != 0) {
                t3 &= ~FILETYPE_MASK_PRODOS_DESTROY;
            }
            if ((fType & FILE_TYPE_WRITEONLY_MASK.getValue()) != 0) {
                t3 &= ~FILETYPE_MASK_PRODOS_READ;
            }
            if ((fType & (FILE_TYPE_SYSTEM_MASK.getValue() | FILE_TYPE_HIDDEN_MASK.getValue())) != 0) {
                t3 &= ~(FILETYPE_MASK_PRODOS_WRITE | FILETYPE_MASK_PRODOS_RENAME | FILETYPE_MASK_PRODOS_DESTROY);
            }

            if (t1 > 0) setFileType1(t1);
            setFileType2(t2);
            setFileType3(t3);
        }
    }

    /**
     * Returns attribute
     * Meaning of unique attributes: STORAGE_TYPE, FILE_TYPE, ACCESS
     */
    @Override
    public DiskBasicFileType getFileAttr() {
        int val = 0;
        int sType = getFileType1();
        int fType = getFileType2();
        int access = getFileType3();

        switch (sType) {
            case FILETYPE_MASK_PRODOS_SUBDIR:
                val |= FILE_TYPE_DIRECTORY_MASK.getValue();
                break;
            case FILETYPE_MASK_PRODOS_VOLUME:
            case FILETYPE_MASK_PRODOS_SUBVOL:
                val |= FILE_TYPE_VOLUME_MASK.getValue();
                fType = 0;
                break;
            default:
                break;
        }

        int type4 = getAuxType();
        int version = getVersion();

        return new DiskBasicFileType(basic.getFormatTypeNumber(), val, (sType << 16) | (fType << 8) | access, type4, version);
    }

    @Override
    public String getFileAttrStr() {
        String str = "";
        int sPos = convFileType1Pos(getFileType1());

        if (sPos != TYPE_NAME_PRODOS_FILE) {
            // DIR or VOL
            str = TYPE_NAME_PRODOS1[sPos];
        } else {
            // FILE TYPE
            int type2 = getFileType2();
            int fpos = convFileType2Pos(type2);
            if (fpos >= 0) {
                str = Utils.keyAt(TYPE_NAME_PRODOS2, fpos);
            } else {
                str = String.format("0x%02x", type2);
            }
        }

        // ACCESS
        str += " ,";

        int access = getFileType3();
        for (int i = 0; i < TYPE_NAME_PRODOS3.size(); i++) {
            if ((access & (int) Utils.valueAt(TYPE_NAME_PRODOS3, i)) != 0) {
                str += Utils.keyAt(TYPE_NAME_PRODOS3, i);
            } else {
                str += "-";
            }
        }

        return str;
    }

    /** Returns position in list from attribute */
    private int convFileType1Pos(int type1) {
        int pos = switch (type1) {
            case FILETYPE_MASK_PRODOS_SUBDIR -> TYPE_NAME_PRODOS_SUBDIR;
            case FILETYPE_MASK_PRODOS_SUBVOL, FILETYPE_MASK_PRODOS_VOLUME -> TYPE_NAME_PRODOS_VOLUME;
            default -> TYPE_NAME_PRODOS_FILE;
        };
        return pos;
    }

    /** Returns position in list from attribute */
    private int convFileType2Pos(int type2) {
        return Utils.indexOf(TYPE_NAME_PRODOS2, type2 & 0xff);
    }

    @Override
    public void setFileSize(int val) {
        groups.setSize(val);
        int block = basic.getSectorSize() * basic.getSectorsPerGroup();
        int groups = (val + block - 1) / block;
        groups += index.size();

        data.data().eof[0] = (byte) (val & 0xff);
        val >>= 8;
        data.data().eof[1] = (byte) (val & 0xff);
        val >>= 8;
        data.data().eof[2] = (byte) (val & 0xff);

        setBlocksUsed(groups);
    }

    @Override
    public int getFileSize() {
        int val = (data.data().eof[0] & 0xff) +
                ((data.data().eof[1] & 0xff) << 8) +
                ((data.data().eof[2] & 0xff) << 16);

        int sType = getFileType1();
        val = switch (sType) {
            case FILETYPE_MASK_PRODOS_SUBVOL, FILETYPE_MASK_PRODOS_VOLUME -> 0;
            default -> val;
        };
        return val;
    }

    @Override
    public void calcFileUnitSize(int fileUnitNum) throws IOException {
        if (!checkUsed(false)) return;

        getUnitGroups(fileUnitNum, groups);
    }

    @Override
    public void getUnitGroups(int fileUnitNum, DiskBasicGroups groupItems) throws IOException {
        //if (!chain.isValid()) return;

        int calcGroups = 0;
        int calcFileSize = 0;

        int[] trackNum = {0};
        int[] sideNum = {0};
        int sectorNum = 0;

        int sectorSize = basic.getSectorSize();
        int blockSize = sectorSize * basic.getSectorsPerGroup();

        int remainSize = getFileSize();
        int groupNum = getStartGroup(fileUnitNum);
        int sectorPos = groupNum * basic.getSectorsPerGroup();

        int sType = getFileType1();
        if (sType == FILETYPE_MASK_PRODOS_SEEDING) {
            // File that fits in 1 block
            basic.getNumsFromGroup(groupNum, 0, sectorSize, remainSize, groupItems);
            calcGroups++;
            calcFileSize += getFileSize();
        } else if (sType == FILETYPE_MASK_PRODOS_SAPLING) {
            // File referencing index
            for (int i = 0; i < getBlocksUsed(); i++) {
                groupNum = ProDosOneIndex.getGroupNumber(index, i);
                if (groupNum == 0) {
                    break;
                }
                basic.getNumsFromGroup(groupNum, 0, sectorSize, remainSize, groupItems);
                calcGroups++;
            }
            calcFileSize += getFileSize();
        } else if (sType == FILETYPE_MASK_PRODOS_TREE) {
            // File referencing index - Tree format
            for (int i = 0; i < getBlocksUsed(); i++) {
                groupNum = ProDosOneIndex.getGroupNumber(index, i + sectorSize);
                if (groupNum == 0) {
                    break;
                }
                basic.getNumsFromGroup(groupNum, 0, sectorSize, remainSize, groupItems);
                calcGroups++;
            }
            calcFileSize += getFileSize();
        } else if (sType == FILETYPE_MASK_PRODOS_SUBDIR) {
            // Subdirectory
            for (int i = 0; i < getBlocksUsed(); i++) {
                ProDOSDirPointer next = new ProDOSDirPointer();
                next.nextBlock = 0;

                for (int ss = 0; ss < basic.getSectorsPerGroup(); ss++) {
                    DiskImageSector sector = basic.getSectorFromSectorPos(sectorPos, trackNum, sideNum);
                    if (sector == null) {
                        break;
                    }
                    byte[] buffer = sector.getSectorBuffer();
                    if (buffer == null) {
                        break;
                    }
                    if (ss == 0) {
                        // Hold pointer to next block
                        Serdes.Util.deserialize(new ByteArrayInputStream(buffer), next);
                    }

                    sectorNum = sector.getSectorNumber();

                    groupItems.add(groupNum, 0, trackNum[0], sideNum[0], sectorNum, sectorNum);

                    sectorPos++;
                }

                calcGroups++;

                // No next sector
                if (next.nextBlock == 0) {
                    break;
                }

                groupNum = next.nextBlock & 0xffff;
                sectorPos = groupNum * basic.getSectorsPerGroup();
            }
            calcFileSize += getFileSize();
        } else {

        }
        groupItems.setNums(calcGroups);
        groupItems.setSize(calcFileSize);
        groupItems.setSizePerGroup(blockSize);

        // Recalculation of the last sector
        //groupItems.setSize(recalcFileSize(groupItems, groupItems.getSize()));

        // Get addresses inside file
        takeAddressesInFile(groupItems);
    }

    @Override
    public int recalcFileSize(DiskBasicGroups groupItems, int occupiedSize) {
        return occupiedSize;
    }

    // Extract addresses inside file
    private void takeAddressesInFile(DiskBasicGroups groupItems) {
        if (groupItems.size() == 0) {
            return;
        }

        DiskBasicGroupItem item = groupItems.get(0);
        DiskImageSector sector = basic.getSector(item.track, item.side, item.sectorStart);
        if (sector == null) return;
    }

    @Override
    public int getDataSize() {
        return data.getDataSize();
    }

    @Override
    public DirectoryProDos getData() {
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
        return data.copy(val);
    }

    @Override
    public void clearData() {
        data.fill(0);
    }

    @Override
    public void setStartGroup(int fileUnitNum, int val, int size) {
        data.data().keyPointer = (short) val;
    }

    @Override
    public int getStartGroup(int fileUnitNum) {
        int val = data.data().keyPointer & 0xffff;

        int sType = getFileType1();
        val = switch (sType) {
            case FILETYPE_MASK_PRODOS_SUBVOL, FILETYPE_MASK_PRODOS_VOLUME -> mDirGroupNum;
            default -> val;
        };
        return val;
    }

    @Override
    public void setParentGroup(int val) {
        int sType = getFileType1();
        switch (sType) {
            case FILETYPE_MASK_PRODOS_SUBVOL:
            case FILETYPE_MASK_PRODOS_VOLUME:
                break;
            default:
                data.data().aux.f.headerPointer = (short) val;
                break;
        }
    }

    @Override
    public int getParentGroup() {
        int val;
        int sType = getFileType1();
        val = switch (sType) {
            case FILETYPE_MASK_PRODOS_SUBVOL, FILETYPE_MASK_PRODOS_VOLUME -> INVALID_GROUP_NUMBER;
            default -> data.data().aux.f.headerPointer & 0xffff;
        };
        return val;
    }

    @Override
    public int getExtraGroup() {
        int sType = getFileType1();
        return switch (sType) {
            case FILETYPE_MASK_PRODOS_SAPLING, FILETYPE_MASK_PRODOS_TREE -> getStartGroup(0);
            default -> INVALID_GROUP_NUMBER;
        };
    }

    @Override
    public void getExtraGroups(java.util.List<Integer> arr) {
        int sType = getFileType1();
        switch (sType) {
            case FILETYPE_MASK_PRODOS_SAPLING:
            case FILETYPE_MASK_PRODOS_TREE:
                arr.add(getStartGroup(0));
                break;
        }
    }

    @Override
    public void clearChainSector(DiskBasicDirItem<DirectoryProDos> pItem) throws IOException {
        for (ProDosOneIndex item : index) {
            int gnum = item.getMyGroupNumber();
            type.deleteGroupNumber(gnum);
        }
        index.clear();
        //index.setBasic(basic);
    }

    @Override
    public void setChainSector(int num, int pos, byte[] data, DiskBasicDirItem<DirectoryProDos> pitem) {
        ProDosOneIndex item = new ProDosOneIndex();
        item.attachBuffer(basic, num, pos);
        index.add(item);
    }

    @Override
    public void addChainGroupNumber(int index, int val) {
        ProDosOneIndex.setGroupNumber(this.index, index, (short) val);
    }

    @Override
    public boolean needCheckEofCode() {
        return false;
    }

    @Override
    public int recalcFileSizeOnSave(InputStream iStream, int fileSize) {
        int sType;
        if (fileSize < 0x200) {
            // No index if less than 512 bytes
            sType = FILETYPE_MASK_PRODOS_SEEDING;
        } else if (fileSize < 0x20000) {
            // One index if less than 131K bytes
            sType = FILETYPE_MASK_PRODOS_SAPLING;
        } else {
            // Tree if 131K bytes or more
            sType = FILETYPE_MASK_PRODOS_TREE;
        }
        setFileType1(sType);

        return fileSize;
    }

    @Override
    public boolean preExportDataFile(String[] filename) {
        if (!config.isAddExtensionExport()) return true;

        // Attach extension from attributes
        if (!isDirectory()) {
            String[] ext = new String[1];
            if (getFileAttrName(convFileType2Pos(getFileType2()), TYPE_NAME_PRODOS2, ext)) {
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

    /**
     * Processing required before importing data
     *
     * @param filename [in,out] File name
     * @return false if this file is excluded
     */
    @Override
    public boolean preImportDataFile(String[] filename) {
        if (config.isDecideAttrImport()) {
            isContainAttrByExtension(filename[0], TYPE_NAME_PRODOS2, TYPE_NAME_PRODOS_NOT, TYPE_NAME_PRODOS_SYS, filename, null, null);
        }
        // Remove extension
        filename[0] = remakeFileNameAndExtStr(filename[0]);
        return true;
    }

    @Override
    public int convOriginalTypeFromFileName(String filename) {
        int[] t2 = {0};
        // Set attribute by extension
        if (!isContainAttrByExtension(filename, TYPE_NAME_PRODOS2, TYPE_NAME_PRODOS_NOT, TYPE_NAME_PRODOS_SYS, null, t2, null)) {
            t2[0] = FILETYPE_MASK_PRODOS_TXT;
        }

        return t2[0];
    }

    @Override
    public boolean isDeletable() {
        int sType = getFileType1();
        return (sType != FILETYPE_MASK_PRODOS_SUBVOL && sType != FILETYPE_MASK_PRODOS_VOLUME);
    }

    @Override
    public boolean hasModifyDateTime() {
        int stype = getFileType1();
        return (stype != FILETYPE_MASK_PRODOS_SUBVOL && stype != FILETYPE_MASK_PRODOS_VOLUME);
    }

    @Override
    public LocalDate getFileCreateDate(LocalDateTime tm) {
        return convDateToTm(data.data().cDate);
    }

    @Override
    public LocalTime getFileCreateTime(LocalDateTime tm) {
        return convTimeToTm(data.data().cTime);
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
        convDateFromTm(tm, data.data().cDate);
    }

    @Override
    public void setFileCreateTime(LocalDateTime tm) {
        convTimeFromTm(tm, data.data().cTime);
    }

    @Override
    public LocalDate getFileModifyDate(LocalDateTime tm) {
        int sType = getFileType1();
        return switch (sType) {
            case FILETYPE_MASK_PRODOS_SUBVOL, FILETYPE_MASK_PRODOS_VOLUME -> tm.toLocalDate();
            default -> convDateToTm(data.data().cDate);
        };
    }

    @Override
    public LocalTime getFileModifyTime(LocalDateTime tm) {
        int sType = getFileType1();
        return switch (sType) {
            case FILETYPE_MASK_PRODOS_SUBVOL, FILETYPE_MASK_PRODOS_VOLUME -> tm.toLocalTime();
            default -> convTimeToTm(data.data().cTime);
        };
    }

    @Override
    public String getFileModifyDateStr() {
        LocalDateTime tm = LocalDateTime.now();
        LocalDate ld = getFileModifyDate(tm);
        return Utils.formatYMDStr(ld);
    }

    @Override
    public String getFileModifyTimeStr() {
        LocalDateTime tm = LocalDateTime.now();
        LocalTime lt = getFileModifyTime(tm);
        return Utils.formatHMStr(lt);
    }

    @Override
    public void setFileModifyDate(LocalDateTime tm) {
        convDateFromTm(tm, data.data().cDate);
    }

    @Override
    public void setFileModifyTime(LocalDateTime tm) {
        convTimeFromTm(tm, data.data().cTime);
    }

    // Static conversion methods
    public static LocalDate convDateToTm(byte[] data) {
        int ymd = (data[1] & 0xff) << 8 | (data[0] & 0xff);
        return LocalDate.of(
                (ymd >> 9) +
                        ((ymd >> 9) < 70 ? 100 : 0),
                ((ymd >> 5) & 0xf) - 1,
                ymd & 0x1f);
    }

    public static LocalTime convTimeToTm(byte[] data) {
        return LocalTime.of(
                data[1] & 0x1f,
                data[0] & 0x3f,
                0);
    }

    public static void convDateFromTm(LocalDateTime tm, byte[] data) {
        if (tm.getYear() >= 0 && tm.getMonth().ordinal() >= -1) {
            data[1] = (byte) (((tm.getYear() & 0x7f) % 100) << 1 | (((tm.getMonth().ordinal() + 1) & 0x8) >> 3));
            data[0] = (byte) ((((tm.getMonth().ordinal() + 1) & 0x7) << 5) | (tm.getDayOfMonth() & 0x1f));
        }
    }

    public static void convTimeFromTm(LocalDateTime tm, byte[] data) {
        if (tm.getHour() >= 0 && tm.getMinute() >= 0) {
            data[1] = (byte) (tm.getHour() & 0x1f);
            data[0] = (byte) (tm.getMinute() & 0x3f);
        }
    }

    @Override
    public int getStartAddress() {
        int fType = getFileType2();
        return switch (fType) {
            case FILETYPE_MASK_PRODOS_BIN, FILETYPE_MASK_PRODOS_BAS, FILETYPE_MASK_PRODOS_SYS -> getAuxType();
            default -> -1;
        };
    }

    @Override
    public int getEndAddress() {
        int val = -1;
        int fType = getFileType2();
        val = switch (fType) {
            case FILETYPE_MASK_PRODOS_BIN, FILETYPE_MASK_PRODOS_BAS, FILETYPE_MASK_PRODOS_SYS ->
                    getAuxType() + getFileSize() - 1;
            default -> val;
        };
        return val;
    }

    @Override
    public void setStartAddress(int val) {
        int fType = getFileType2();
        switch (fType) {
            case FILETYPE_MASK_PRODOS_BIN:
            case FILETYPE_MASK_PRODOS_BAS:
            case FILETYPE_MASK_PRODOS_SYS:
                setAuxType(val);
                break;
        }
    }

    public void increaseFileCount() {
         short val = 0;
         int sType = getFileType1();
         switch (sType) {
         case FILETYPE_MASK_PRODOS_SUBVOL:
         case FILETYPE_MASK_PRODOS_VOLUME:
             val = data.data().aux.v.fileCount;
             val++;
             data.data().aux.v.fileCount = val;
         default:
             break;
         }
    }

    public void decreaseFileCount() {
         short val = 0;
         int sType = getFileType1();
         switch (sType) {
         case FILETYPE_MASK_PRODOS_SUBVOL:
         case FILETYPE_MASK_PRODOS_VOLUME:
             val = data.data().aux.v.fileCount;
             val--;
             data.data().aux.v.fileCount = val;
             break;
         default:
             break;
         }
    }

    /** Set sector to which item belongs as modified */
    @Override
    public void setModify() {
        sData.copyFrom(data.getRawData());
    }

    @Override
    public void setInternalDataInAttrDialog(KeyValArray vals) {
        int stype = getFileType1();
        vals.add("STORAGE_TYPE", data.data().sTypeAndNLen);
        vals.add("FILE_NAME", data.data().name, data.data().name.length);
        vals.add("FILE_TYPE", data.data().fileType);
        vals.add("KEY_POINTER", data.data().keyPointer);
        vals.add("BLOCKS_USED", data.data().blocksUsed);
        vals.add("EOF", data.data().eof, data.data().eof.length);
        vals.add("CREATION_DATE", data.data().cDate, data.data().cDate.length);
        vals.add("CREATION_TIME", data.data().cTime, data.data().cTime.length);
        vals.add("VERSION", data.data().version);
        vals.add("MIN_VERSION", data.data().minVersion);
        vals.add("ACCESS", data.data().access);
        switch (stype) {
            case FILETYPE_MASK_PRODOS_VOLUME:
                vals.add("ENTRY_LENGTH", data.data().aux.v.entryLen);
                vals.add("ENTRIES_PER_BLOCK", data.data().aux.v.entriesPerBlock);
                vals.add("FILE_COUNT", data.data().aux.v.fileCount);
                vals.add("BITMAP_POINTER", data.data().aux.v.bitmapPointer);
                vals.add("TOTAL_BLOCKS", data.data().aux.v.totalBlocks);
                break;
            case FILETYPE_MASK_PRODOS_SUBVOL:
                vals.add("ENTRY_LENGTH", data.data().aux.sv.entryLen);
                vals.add("ENTRIES_PER_BLOCK", data.data().aux.sv.entriesPerBlock);
                vals.add("FILE_COUNT", data.data().aux.sv.fileCount);
                vals.add("PARENT_POINTER", data.data().aux.sv.parentPointer);
                vals.add("PARENT_ENTRY", data.data().aux.sv.parentEntry);
                vals.add("PARENT_ENTRY_LENGTH", data.data().aux.sv.parentEntryLen);
                break;
            default:
                vals.add("AUX_TYPE", data.data().aux.f.auxType);
                vals.add("LASTMOD_DATE", data.data().aux.f.mDate, data.data().aux.f.mDate.length);
                vals.add("LASTMOD_TIME", data.data().aux.f.mTime, data.data().aux.f.mTime.length);
                vals.add("HEADER_POINTER", data.data().aux.f.headerPointer);
                break;
        }
    }
}
