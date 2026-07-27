///
/// Copyright (c) Sasaji. All rights reserved.
///

package l3diskex.basicfmt.diritem;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
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
import l3diskex.basicfmt.diritem.DiskBasicDirItemAppleDOS.AppleDosChain.TrackList;
import l3diskex.basicfmt.diritem.DiskBasicDirItemAppleDOS.DirectoryAppleDos;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;

import static l3diskex.Config.config;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_ASCII_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BASIC_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_BINARY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_DATA_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_INTEGER_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_MACHINE_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_READONLY_MASK;
import static l3diskex.basicfmt.diritem.DiskBasicDirItemAppleDOS.AppleDosChain.APLEDOS_TRACK_LIST_MAX;
import static l3diskex.basicfmt.type.DiskBasicTypeAppleDOS.FORMAT_TYPE_APLEDOS;


/**
 * Directory 1 item Apple DOS 3.x
 */
public class DiskBasicDirItemAppleDOS extends DiskBasicDirItem<DirectoryAppleDos> {

    static final ResourceBundle rb = ResourceBundle.getBundle("messages");

    /**
     * Directory entry Apple DOS (35bytes)
     */
    @Serdes
    public static class DirectoryAppleDos implements Directory {

        @Element(sequence = 1)
        public byte track;
        @Element(sequence = 2)
        public byte sector;
        @Element(sequence = 3)
        public byte type;
        @Element(sequence = 4)
        public byte[] name = new byte[30];
        @Element(sequence = 5)
        public short sectorCount; // size (little endien)

        public static final int SIZE = 35;
    }

    /**
     * Apple DOS top of each sector
     */
    @Serdes
    public static class AppleDosPointer {

        public static final int SIZE = 3;

        @Element(sequence = 1)
        public byte reserved;
        @Element(sequence = 2)
        public byte nextTrack;
        @Element(sequence = 3)
        public byte nextSector;

        public byte[] serialize() {
            return new byte[]{reserved, nextTrack, nextSector};
        }
    }

    /**
     * Directory entry Apple ProDOS (39bytes)
     */
    @Serdes
    public static class DirectoryProDos implements Directory {

        @Element(sequence = 1)
        public byte sTypeAndNLen;
        @Element(sequence = 2)
        public byte[] name = new byte[15];
        @Element(sequence = 3)
        public byte fileType; // file only
        @Element(sequence = 4)
        public short keyPointer; // file only
        @Element(sequence = 5)
        public short blocksUsed; // file only
        @Element(sequence = 6)
        public byte[] eof = new byte[3]; // file only
        @Element(sequence = 7)
        public byte[] cDate = new byte[2];
        @Element(sequence = 8)
        public byte[] cTime = new byte[2];
        @Element(sequence = 9)
        public byte version; // byte
        @Element(sequence = 10)
        public byte minVersion; // byte
        @Element(sequence = 11)
        public byte access; // byte

        // Union for the variant part
        public ProdosAux aux = new ProdosAux();

        public static class ProdosAux {

            public V v = new V();
            public Sv sv = new Sv();
            public F f = new F();
        }

        public static class V {

            public byte entryLen;
            public byte entriesPerBlock;
            public short fileCount;
            public short bitmapPointer;
            public short totalBlocks;
        }

        public static class Sv {

            public byte entryLen;
            public byte entriesPerBlock;
            public short fileCount;
            public short parentPointer;
            public byte parentEntry;
            public byte parentEntryLen;
        }

        public static class F {

            public short auxType; // aux type
            public byte[] mDate = new byte[2];
            public byte[] mTime = new byte[2];
            public short headerPointer;
        }

        public static final int SIZE = 39;
    }

    /// Apple DOS attribute names
    public static final Map<String, Object> typeNameAppleDOS = new LinkedHashMap<>() {{
        put("Text", FILETYPE_MASK_APLEDOS_TEXT);
        put("Integer BASIC", FILETYPE_MASK_APLEDOS_IBASIC);
        put("Applesoft BASIC", FILETYPE_MASK_APLEDOS_ABASIC);
        put("Binary", FILETYPE_MASK_APLEDOS_BINARY);
        put("Read Only", FILETYPE_MASK_APLEDOS_READ_ONLY);
    }};

    /*
     * Apple DOS attribute positions
     */
    static final int TYPE_NAME_APLEDOS_TEXT = 0;
    // Integer BASIC
    static final int TYPE_NAME_APLEDOS_IBASIC = 1;
    // Applesoft BASIC
    static final int TYPE_NAME_APLEDOS_ABASIC = 2;
    static final int TYPE_NAME_APLEDOS_BINARY = 3;
    static final int TYPE_NAME_APLEDOS_READ_ONLY = 4;

    /**
     * Apple DOS attribute values
     */
    static final int FILETYPE_MASK_APLEDOS_TEXT = 0x00;
    static final int FILETYPE_MASK_APLEDOS_IBASIC = 0x01;
    static final int FILETYPE_MASK_APLEDOS_ABASIC = 0x02;
    static final int FILETYPE_MASK_APLEDOS_BINARY = 0x04;
    static final int FILETYPE_MASK_APLEDOS_READ_ONLY = 0x80;

    /**
     * Apple DOS track sector list information 256bytes
     */
    @Serdes
    public static class AppleDosChain {

        private static final int SIZE = 256;

        public static final int APLEDOS_TRACK_LIST_MAX = 122;

        @Element(sequence = 1)
        public AppleDosPointer next;
        @Element(sequence = 2)
        byte[] reserved1 = new byte[2];
        @Element(sequence = 3)
        short number;
        @Element(sequence = 4)
        byte[] reserved2 = new byte[5];
        @Serdes
        public static class TrackList {
            @Element(sequence = 1)
            byte track;
            @Element(sequence = 2)
            byte sector;
        }
        @Element(sequence = 5)
        TrackList[] list = new TrackList[APLEDOS_TRACK_LIST_MAX];
    }

    //
    // Apple DOS track sector list
    //
    static class DiskBasicDirItemAppleDosChain {

        private DiskBasic basic;
        private final List<AppleDosChain> chains;
        private final List<DiskImageSector> sectors;

        public DiskBasicDirItemAppleDosChain() {
            chains = new ArrayList<>();
            sectors = new ArrayList<>();
            basic = null;
        }

        /** Set BASIC */
        public void setBasic(DiskBasic n_basic) {
            basic = n_basic;
        }

        /** Set pointer */
        public void add(AppleDosChain n_chain, DiskImageSector n_sector) {
            chains.add(n_chain);
            sectors.add(n_sector);
        }

        /** Clear */
        public void clear() {
            chains.clear();
            sectors.clear();
        }

        /** Returns number of sectors */
        public int count() {
            return chains.size();
        }

        /** Whether valid */
        public boolean isValid() {
            return !chains.isEmpty();
        }

        /** Returns track & sector */
        public void getTrackAndSector(int idx, int[] track, int[] sector) {
            int max_idx = APLEDOS_TRACK_LIST_MAX;
            for (AppleDosChain item : chains) {
                if (idx < max_idx) {
                    if (item.list[idx] != null) {
                        track[0] = item.list[idx].track & 0xff;
                        sector[0] = item.list[idx].sector & 0xFF;
                    } else {
                        track[0] = 0;
                        sector[0] = 0;
                    }
                    track[0] += basic.getTrackNumberBaseOnDisk();
                    sector[0] += basic.getSectorNumberBase();
                    break;
                }
                idx -= max_idx;
            }
        }

        /** Set track & sector */
        public void setTrackAndSector(int idx, int track, int sector) throws IOException {
            int max_idx = APLEDOS_TRACK_LIST_MAX;
            for (int i = 0; i < chains.size(); i++) {
                AppleDosChain item = chains.get(i);
                if (idx < max_idx) {
                    track -= basic.getTrackNumberBaseOnDisk();
                    sector -= basic.getSectorNumberBase();
                    if (item.list[idx] == null) item.list[idx] = new TrackList();
                    item.list[idx].track = (byte) (track & 0xff);
                    item.list[idx].sector = (byte) (sector & 0xff);
                    writeBack(i);
                    break;
                }
                idx -= max_idx;
            }
        }

        /** Get sector number where next sector exists */
        public int getNext(int idx) {
            AppleDosChain item = chains.get(idx);
            AppleDosPointer next = item.next;
            return (next.nextTrack & 0xFF) * basic.getSectorsPerTrackOnBasic() + (next.nextSector & 0xFF);
        }

        /** Set sector number where next sector exists */
        public void setNext(int idx, int val) throws IOException {
            AppleDosChain item = chains.get(idx);
            AppleDosPointer next = item.next;
            next.nextTrack = (byte) ((val / basic.getSectorsPerTrackOnBasic()) & 0xFF);
            next.nextSector = (byte) ((val % basic.getSectorsPerTrackOnBasic()) & 0xFF);
            item.next = next;
            writeBack(idx);
        }

        private void writeBack(int idx) throws IOException {
            if (idx >= 0 && idx < sectors.size()) {
                DiskImageSector s = sectors.get(idx);
                AppleDosChain item = chains.get(idx);
                if (s != null && item != null) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    Serdes.Util.serialize(item, baos);
                    s.copy(baos.toByteArray(), baos.size(), 0);
                }
            }
        }
    }

    //
    // Directory 1 item Apple DOS 3.x
    //

    /** Directory data */
    private final DiskBasicDirData<DirectoryAppleDos> data = new DiskBasicDirData<>();

    /** Start address held inside file */
    private int startAddress;
    /** Size held inside file */
    private int dataLength;

    /** Track & sector list */
    private final DiskBasicDirItemAppleDosChain chain = new DiskBasicDirItemAppleDosChain();

    @Override
    public boolean isSupported(int formatType) {
        return formatType == FORMAT_TYPE_APLEDOS;
    }

    @Override
    public void init(DiskBasic basic) throws IOException {
        super.init(basic);

        startAddress = -1;
        dataLength = -1;

        data.alloc(DirectoryAppleDos.class);
    }

    @Override
    public void init(DiskBasic basic, DiskImageSector sector, int sectorPos, byte[] data, int dataPos) throws IOException {
        super.init(basic, sector, sectorPos, data, dataPos);

        startAddress = -1;
        dataLength = -1;

        this.data.attach(DirectoryAppleDos.class, data, dataPos);
    }

    @Override
    public void init(DiskBasic basic, int num, DiskBasicGroupItem groupItem, DiskImageSector sector, int sectorPos, byte[] data, int dataPos, SectorParam next, boolean[] unuse) throws IOException {
        super.init(basic, num, groupItem, sector, sectorPos, data, dataPos, next, unuse);

        startAddress = -1;
        dataLength = -1;

        this.data.attach(DirectoryAppleDos.class, data, dataPos);

        used(checkUsed(unuse[0]));

        // Set pointer to chain sector
        if (isUsed()) {
            chain.clear();
            chain.setBasic(basic);
            int gourp = getStartGroup(0);
            while (gourp != 0) {
                DiskImageSector sectorNum = basic.getSectorFromGroup(gourp);
                if (sectorNum == null) break;

                byte[] buf = sectorNum.getSectorBuffer();
                AppleDosChain c = new AppleDosChain();
                Serdes.Util.deserialize(new ByteArrayInputStream(buf), c);
                chain.add(c, sectorNum);
                AppleDosPointer p = new AppleDosPointer();
                Serdes.Util.deserialize(new ByteArrayInputStream(buf), p);
                gourp = type.getSectorPosFromNumS((p.nextTrack & 0xff) + basic.getTrackNumberBaseOnDisk(), (p.nextSector & 0xff) + basic.getSectorNumberBase());
            }
        }

        calcFileSize();
    }

    /**
     * Set pointer to item
     *
     * @param num    Serial number
     * @param gItem  Data such as track number
     * @param sector Sector
     * @param sectorPos Position of directory entry within sector
     * @param data   Directory item
     * @param dataPos  Data pointer
     * @param next   [out] Next sector
     */
    @Override
    public void setData(int num, DiskBasicGroupItem gItem, DiskImageSector sector, int sectorPos, byte[] data, int dataPos, SectorParam next) throws IOException {
        super.setData(num, gItem, sector, sectorPos, data, dataPos, next);

        this.data.attach(DirectoryAppleDos.class, data, dataPos);
    }

    /** Returns position where file name is stored */
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

    /**
     * Set file name
     *
     * filename may have data bits inverted
     * @param filename [in,out] File name
     * @param size     Buffer size
     * @param length   Length
     */
    @Override
    protected void setNativeName(byte[] filename, int size, int length) {
        byte[] n;
        int[] nl = {0};
        int[] ns = {0};
        n = getFileNamePos(0, ns, nl);
        if (n != null && ns[0] > 0) {
            int copySize = ns[0];
            if (copySize > size) copySize = size;
            // File name has MSB set
            for (int i = 0; i < copySize; i++) {
                n[i] = (byte) (filename[i] | 0x80);
            }
        }
    }

    /**
     * Get file name
     *
     * @param filename [in,out] File name
     * @param size     Buffer size
     * @param length   [out] Length
     */
    @Override
    protected void getNativeName(byte[] filename, int size, int[] length) {
        byte[] n = null;
        int[] s = {0};
        int[] l = {0};

        n = getFileNamePos(0, s, l);
        if (n != null && s[0] > 0) {
            if (s[0] > size) s[0] = size;
            // Remove MSB from file name
            for (int i = 0; i < s[0]; i++) {
                filename[i] = (byte) (n[i] & 0x7f);
            }
        }
    }

    /** Returns attribute 1 */
    @Override
    public int getFileType1() {
        return data.data().type & 0xff;
    }

    /** Set attribute 1 */
    @Override
    public void setFileType1(int val) {
        data.data().type = (byte) (val & 0xff);
    }

    /** Whether it is a used item */
    @Override
    public boolean checkUsed(boolean unuse) {
        return !(data.data().track == (byte) 0xff || (data.data().track == 0 && data.data().sector == 0));
    }

    /** Delete */
    @Override
    public boolean delete() {
        // Delete
        used(false);
        // Attribute is not updated here
        return true;
    }

    /**
     * Check directory item
     *
     * @param last [in,out] Whether to end the check
     * @return Check OK
     */
    @Override
    public boolean check(boolean[] last) {
        if (!data.isValid()) return false;

        boolean valid = true;

        if (data.data().track == 0 && data.data().sector == 0) {
            last[0] = true;
            return valid;
        }
        // Attribute bits 3-6 are zero
        if ((data.data().type & 0x78) != 0) {
            valid = false;
        }
        return valid;
    }

    /** Convert common attribute to individual attribute */
    public static int convToFileType1(int ftype) {
        int type1 = 0;
        if ((ftype & (FILE_TYPE_BASIC_MASK.getValue() | FILE_TYPE_BINARY_MASK.getValue() | FILE_TYPE_INTEGER_MASK.getValue())) == (FILE_TYPE_BASIC_MASK.getValue() | FILE_TYPE_BINARY_MASK.getValue())) {
            type1 = FILETYPE_MASK_APLEDOS_ABASIC;
        } else if ((ftype & (FILE_TYPE_BASIC_MASK.getValue() | FILE_TYPE_BINARY_MASK.getValue() | FILE_TYPE_INTEGER_MASK.getValue())) == (FILE_TYPE_BASIC_MASK.getValue() | FILE_TYPE_INTEGER_MASK.getValue())) {
            type1 = FILETYPE_MASK_APLEDOS_IBASIC;
        } else if ((ftype & FILE_TYPE_BINARY_MASK.getValue()) != 0) {
            type1 = FILETYPE_MASK_APLEDOS_BINARY;
        } else {
            type1 = FILETYPE_MASK_APLEDOS_TEXT;
        }
        if ((ftype & FILE_TYPE_READONLY_MASK.getValue()) != 0) {
            type1 |= FILETYPE_MASK_APLEDOS_READ_ONLY;
        }
        return type1;
    }

    /** Convert individual attribute to common attribute */
    public static int convFromFileType1(int type1) {
        int val = 0;
        if ((type1 & FILETYPE_MASK_APLEDOS_ABASIC) != 0) {
            val = (FILE_TYPE_BINARY_MASK.getValue() | FILE_TYPE_BASIC_MASK.getValue());
        } else if ((type1 & FILETYPE_MASK_APLEDOS_IBASIC) != 0) {
            val = (FILE_TYPE_INTEGER_MASK.getValue() | FILE_TYPE_BASIC_MASK.getValue());
        } else if ((type1 & FILETYPE_MASK_APLEDOS_BINARY) != 0) {
            val = (FILE_TYPE_BINARY_MASK.getValue() | FILE_TYPE_MACHINE_MASK.getValue());
        } else {
            val = (FILE_TYPE_ASCII_MASK.getValue() | FILE_TYPE_DATA_MASK.getValue());
        }
        if ((type1 & FILETYPE_MASK_APLEDOS_READ_ONLY) != 0) {
            val |= FILE_TYPE_READONLY_MASK.getValue();
        }
        return val;
    }

    /** Set attribute */
    @Override
    public void setFileAttr(DiskBasicFileType fileType) {
        int fType = fileType.getType();
        if (fType == -1) return;

        int type1 = convToFileType1(fType);

        setFileType1(type1);
    }

    /** Returns attribute */
    @Override
    public DiskBasicFileType getFileAttr() {
        int type1 = getFileType1();
        int val = convFromFileType1(type1);
        return new DiskBasicFileType(basic.getFormatTypeNumber(), val, type1);
    }

    /** Returns attribute string (for file list display) */
    @Override
    public String getFileAttrStr() {
        String str = "";
        int oval = getFileType1();
        if ((oval & FILETYPE_MASK_APLEDOS_IBASIC) != 0) {
            if (!str.isEmpty()) str += ", ";
            str += rb.getString(Utils.keyAt(typeNameAppleDOS, TYPE_NAME_APLEDOS_IBASIC));
        } else if ((oval & FILETYPE_MASK_APLEDOS_ABASIC) != 0) {
            if (!str.isEmpty()) str += ", ";
            str += rb.getString(Utils.keyAt(typeNameAppleDOS, TYPE_NAME_APLEDOS_ABASIC));
        } else if ((oval & FILETYPE_MASK_APLEDOS_BINARY) != 0) {
            if (!str.isEmpty()) str += ", ";
            str += rb.getString(Utils.keyAt(typeNameAppleDOS, TYPE_NAME_APLEDOS_BINARY));
        } else {
            if (!str.isEmpty()) str += ", ";
            str += rb.getString(Utils.keyAt(typeNameAppleDOS, TYPE_NAME_APLEDOS_TEXT));
        }
        if ((oval & FILETYPE_MASK_APLEDOS_READ_ONLY) != 0) {
            if (!str.isEmpty()) str += ", ";
            str += rb.getString(Utils.keyAt(typeNameAppleDOS, TYPE_NAME_APLEDOS_READ_ONLY));
        }
        return str;
    }

    /** Set file size */
    @Override
    public void setFileSize(int val) {
        groups.setSize(val);
        int sectorSize = basic.getSectorSize();
        val = (val + sectorSize - 1) / sectorSize;
        setSectorCount(val + chain.count());
    }

    /** Calculate file size and number of groups */
    @Override
    public void calcFileUnitSize(int fileUnitNum) throws IOException {
        if (!isUsed()) return;

        getUnitGroups(fileUnitNum, groups);
    }

    /**
     * Get all groups of specified directory
     *
     * @param fileUnitNum File number
     * @param groupItems  [out] Group list
     */
    @Override
    public void getUnitGroups(int fileUnitNum, DiskBasicGroups groupItems) throws IOException {
        if (!chain.isValid()) return;

        int calcGroups = 0;
        int calcFileSize = 0;

        for (int i = 0; ; i++) {
            int[] trackNum = {0};
            int[] sectorNum = {0};
            chain.getTrackAndSector(i, trackNum, sectorNum);
            if (trackNum[0] == 0 && sectorNum[0] == 0) {
                break;
            }
            int groupNum = type.getSectorPosFromNumS(trackNum[0], sectorNum[0]);
            int[] sideNum = {0};
            type.getNumFromSectorPos(groupNum, trackNum, sideNum, sectorNum);
            groupItems.add(groupNum, 0, trackNum[0], sideNum[0], sectorNum[0], sectorNum[0]);
            calcGroups++;
            calcFileSize += basic.getSectorSize();
            if (calcGroups >= basic.getFatEndGroup()) {
                // too large block size
                break;
            }
        }
        calcGroups += chain.count();
        if (getSectorCount() != calcGroups) {
            calcGroups = getSectorCount();
            calcFileSize = calcGroups * basic.getSectorSize();
        }
        groupItems.setNums(calcGroups);
        groupItems.setSize(calcFileSize);
        groupItems.setSizePerGroup(basic.getSectorSize());

        // Recalculate last sector
        groupItems.setSize(recalcFileSize(groupItems, (int) groupItems.getSize()));

        // Get addresses inside file
        takeAddressesInFile(groupItems);
    }

    /**
     * Calculate size of the last sector and return file size
     *
     * @param groupItems   Group list
     * @param occupiedSize Occupied size
     * @return Calculated file size
     */
    @Override
    public int recalcFileSize(DiskBasicGroups groupItems, int occupiedSize) throws IOException {
        if (groupItems.size() == 0) return occupiedSize;

        DiskBasicGroupItem lastItem = groupItems.last();
        DiskImageSector sector = basic.getSector(lastItem.track, lastItem.side, lastItem.sectorEnd);
        if (sector == null) return occupiedSize;

        int sectorSize = sector.getSectorSize();
        byte[] buf = sector.getSectorBuffer();
        int remainSize = type.calcDataSizeOnLastSector(this, null, null, buf, 0, sectorSize, sectorSize);

        occupiedSize = occupiedSize - sectorSize + remainSize;

        return occupiedSize;
    }

    /** Extract addresses inside file */
    public void takeAddressesInFile(DiskBasicGroups groupItems) {
        startAddress = -1;
        dataLength = -1;

        if (groupItems.size() == 0) {
            return;
        }

        DiskBasicGroupItem item = groupItems.get(0);
        DiskImageSector sector = basic.getSector(item.track, item.side, item.sectorStart);
        if (sector == null) return;

        int t1 = getFileType1();

        if ((t1 & FILETYPE_MASK_APLEDOS_BINARY) != 0) {
            // Binary
            // Start address
            startAddress = sector.get16(0);
            // Data size
            dataLength = sector.get16(2);
            // Set actual size
            if (dataLength + 5 <= groupItems.getSize()) groupItems.setSize(dataLength + 5);
        } else if ((t1 & (FILETYPE_MASK_APLEDOS_IBASIC | FILETYPE_MASK_APLEDOS_ABASIC)) != 0) {
            // BASIC file size
            // Data size => looks like the last data position
            dataLength = sector.get16(0);
            // Set actual size
            if (dataLength + 3 <= groupItems.getSize()) groupItems.setSize(dataLength + 3);
        }
    }

    /**
     * Set the first group number
     *
     * @param fileUnitNum File number (unused)
     * @param val          Group number
     * @param size         File size (unused)
     */
    @Override
    public void setStartGroup(int fileUnitNum, int val, int size) {
        int[] trackNum = {0};
        int[] sectorNum = {0};
        type.getNumFromSectorPosS(val, trackNum, sectorNum);
        data.data().track = (byte) ((trackNum[0] - basic.getTrackNumberBaseOnDisk()) & 0xff);
        data.data().sector = (byte) ((sectorNum[0] - basic.getSectorNumberBase()) & 0xff);
    }

    /** Returns the first group number */
    @Override
    public int getStartGroup(int fileUnitNum) {
        int val = type.getSectorPosFromNumS((data.data().track & 0xff) + basic.getTrackNumberBaseOnDisk(), (data.data().sector & 0xff) + basic.getSectorNumberBase());
        return val;
    }

    /** Returns extra group number (machine dependent) */
    @Override
    public int getExtraGroup() {
        return getStartGroup(0);
    }

    /** Get extra group numbers (machine dependent) */
    @Override
    public void getExtraGroups(List<Integer> result) {
        int groupNum = getExtraGroup();
        for (int i = 0; i < chain.count(); i++) {
            result.add(groupNum);
            groupNum = chain.getNext(i);
            if (groupNum == 0) break;
        }
    }

    /**
     * Clear sector for chain (machine dependent)
     *
     * @param pItem Source item
     */
    @Override
    public void clearChainSector(DiskBasicDirItem<DirectoryAppleDos> pItem) {
        chain.clear();
        chain.setBasic(basic);
    }

    /**
     * Set sector for chain
     *
     * @param sector   Sector
     * @param groupNum Group number
     * @param data     Buffer in sector
     * @param pItem    Source item
     */
    @Override
    public void setChainSector(DiskImageSector sector, int groupNum, byte[] data, DiskBasicDirItem<DirectoryAppleDos> pItem) throws IOException {
        AppleDosChain c = new AppleDosChain();
        Serdes.Util.deserialize(new ByteArrayInputStream(data), c);
        // Ensure list elements are not null
        for (int i = 0; i < c.list.length; i++) {
            if (c.list[i] == null) c.list[i] = new TrackList();
        }
        chain.add(c, sector);
        if (chain.count() > 1) {
            int i = chain.count() - 2;
            chain.setNext(i, groupNum);
        }
    }

    /**
     * Set group number in sector for chain (machine dependent)
     *
     * @param idx Index
     * @param val Group number
     */
    @Override
    public void addChainGroupNumber(int idx, int val) throws IOException {
        int[] trackNum = {0};
        int[] sectorNum = {0};
        type.getNumFromSectorPosS(val, trackNum, sectorNum);
        chain.setTrackAndSector(idx, trackNum[0], sectorNum[0]);
    }

    /** Set sector count (machine dependent) */
    public void setSectorCount(int val) {
        data.data().sectorCount = (short) val; // be
    }

    /**
     * Returns sector count (machine dependent)
     * <p>
     * Sector count includes the number of sectors occupied by the track sector list
     */
    public int getSectorCount() {
        return data.data().sectorCount & 0xffff; // be
    }

    /** Whether EOF code needs to be checked */
    @Override
    public boolean needCheckEofCode() {
        return ((getFileType1() & 0x7f) == 0);
    }

    /**
     * Recalculate file size on save: when EOF code is needed
     *
     * @param iStream  Input stream
     * @param fileSize File size
     * @return Recalculated file size
     */
    @Override
    public int recalcFileSizeOnSave(InputStream iStream, int fileSize) throws IOException {
        if (needCheckEofCode()) {
            // Check if the end of the file ends with a termination symbol
            // However, if the file size is divisible by the sector size, the termination symbol is not required
            if ((fileSize % basic.getSectorSize()) != 0) {
                fileSize = checkEofCode(iStream, fileSize);
                fileSize--;
            }
        }
        return fileSize;
    }

    /** Size of directory item */
    @Override
    public int getDataSize() {
        return data.getDataSize();
    }

    /** Returns item */
    @Override
    public DirectoryAppleDos getData() {
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

    /** Clear directory */
    @Override
    public void clearData() {
        data.fill(basic.getDeleteCode(), getDataSize());
    }

    /**
     * Processing required before importing data
     *
     * @param filename [in,out] File name
     * @return false: ignore this file
     */
    @Override
    public boolean preImportDataFile(String[] filename) {
        if (config.isDecideAttrImport()) {
            trimExtensionByExtensionAttr(filename);
        }
        filename[0] = remakeFileNameAndExtStr(filename[0]);
        return true;
    }

    /** Whether item can be deleted */
    @Override
    public boolean isDeletable() {
        return true;
    }

    /** Set the first track number */
    public void setStartTrack(byte val) {
        data.data().track = val;
    }

    /** Set the first sector number */
    public void setStartSector(byte val) {
        data.data().sector = val;
    }

    /** Returns the first track number */
    public byte getStartTrack() {
        return data.data().track;
    }

    /** Returns the first sector number */
    public byte getStartSector() {
        return data.data().sector;
    }

    /** Whether the item has address */
    @Override
    public boolean hasAddress() {
        return true;
    }

    /** Whether the item has execution address */
    @Override
    public boolean hasExecuteAddress() {
        return false;
    }

    /** Whether address can be edited */
    @Override
    public boolean isAddressEditable() {
        return false;
    }

    /** Returns start address */
    @Override
    public int getStartAddress() {
        return startAddress;
    }

    /** Returns end address */
    @Override
    public int getEndAddress() {
        return (startAddress >= 0 && dataLength > 0) ? startAddress + dataLength - 1 : -1;
    }

    //
    // For dialog
    //

    /**
     * Set internal data displayed in properties
     *
     * @param vals [in,out] List of name & values
     */
    @Override
    public void setInternalDataInAttrDialog(KeyValArray vals) {
        vals.add("TRACK", data.data().track);
        vals.add("SECTOR", data.data().sector);
        vals.add("TYPE", data.data().type);
        vals.add("NAME", data.data().name, data.data().name.length);
        vals.add("SECTOR_COUNT", data.data().sectorCount);
    }
}
