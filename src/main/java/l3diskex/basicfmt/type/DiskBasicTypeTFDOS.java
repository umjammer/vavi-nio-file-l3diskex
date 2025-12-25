/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt.type;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

import l3diskex.Common;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasic.DiskBasicIdentifiedData;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.DiskBasicFat;
import l3diskex.basicfmt.DiskBasicFat.DiskBasicFatBuffer;
import l3diskex.basicfmt.DiskBasicParam.DiskBasicFormat;
import l3diskex.basicfmt.diritem.DiskBasicDirItemTFDOS.DirectoryTfDos;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import vavi.io.SeekableDataInputStream;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;

import static l3diskex.Utils.TEMP_DATA_SIZE;


/**
 * TF-DOS processing
 * <p>
 * DiskBasicParam Specific parameters
 *
 * <li>IDString  IPL in sector 1</li>
 * <li>VolumeString volume name in FAT area</li>
 * <li>ReservedGroups tracks to be marked as used</li>
 *
 * @see "http://fukui.s17.xrea.com/retro/tfdos/index.html"
 */
public class DiskBasicTypeTFDOS extends DiskBasicTypeMZBase<DirectoryTfDos> {

    /** TF-DOS IPL sector */
    @Serdes
    static class TfDosIpl {

        @Element(sequence = 1)
        public byte[] ipl = new byte[0xf0];
        // Automatic execution command at boot
        // When it is other than 0D, TF-DOS treats the 0D-terminated string stored at +$00F0 as a command
        // and executes it automatically.
        @Element(sequence = 2)
        public byte[] autoStart = new byte[0x10];

        public static int SIZE = 0xf0 + 0x10;
    }

    /** TF-DOS FAT sector */
    @Serdes
    static class TfDosFat {

        @Element(sequence = 1)
        public byte[] fat = new byte[0xc0];
        // Volume number (when 0, Master)
        @Element(sequence = 2)
        public byte volumeNum;
        @Element(sequence = 3)
        public byte reserved1;
        // File management number (always 1 in TF-DOS V2.x)
        @Element(sequence = 4)
        public byte identNumber;
        // DOS system version in the disk (always 2 in TF-DOS V2.x)
        @Element(sequence = 5)
        public byte versionNumber;
        // The master disk is described as "TF-DOS MASTER".
        @Element(sequence = 6)
        public byte[] volumeName = new byte[12];
        @Element(sequence = 7)
        public byte[] reserved2 = new byte[0x2a];
        @Element(sequence = 8)
        public short x1SectorSize;
        @Element(sequence = 9)
        public byte[] reserved3 = new byte[4];
    }

    /** Whether it is a BASE compatible file */
    private boolean isBaseCompatible;

    public static final int FORMAT_TYPE_TFDOS = 71;

    @Override
    public boolean isSupported(int typeNumber) {
        return typeNumber == FORMAT_TYPE_TFDOS;
    }

    @Override
    public void init(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryTfDos> dir) {
        super.init(basic, fat, dir);

        this.isBaseCompatible = false;
    }

    /**
     * Check FAT area
     */
    @Override
    public double checkFat(boolean isFormatting) throws IOException {
        double validRatio = 1.0;

        // Adjust group size per track
        basic.setSectorsPerGroup(basic.getSectorsPerTrack());

        // Final group number
        int maxGroup = basic.getTracksPerSide() * basic.getSidesPerDiskOnBasic() - 1;
        basic.setFatEndGroup(maxGroup);

        // FAT area
        DiskBasicFatBuffer fatBuf = fat.getDiskBasicFatBuffer(0, 0);
        if (fatBuf == null) {
            return -1.0;
        }

        // File management number
        TfDosFat f = new TfDosFat();
        byte[] b = fatBuf.getBuffer();
        Serdes.Util.deserialize(new ByteArrayInputStream(b), f);
        if (basic.invertUint8((byte) (f.identNumber & 0xff)) != 1) {
            return -1.0;
        }
        // Volume name
        byte[] volumeName = new byte[12];
        basic.invertMemory(f.volumeName, f.volumeName.length, volumeName);
        if (!new String(volumeName, 0, 6).equals("TF-DOS")) {
            return -1.0;
        }

        // Values other than 0 or 0xff are invalid
        for (int groupNum = 0; groupNum <= basic.getFatEndGroup() && groupNum < fatBuf.getSize(); groupNum++) {
            int value = basic.invertUint8((byte) fatBuf.get(groupNum));
            if (value != basic.getGroupUnusedCode() && value != basic.getGroupSystemCode()) {
                validRatio = -1.0;
                break;
            }
        }

        return validRatio;
    }

    /**
     * Set FAT position
     */
    @Override
    public void setGroupNumber(int num, int val) {
        if (num > basic.getFatEndGroup()) {
            return;
        }

        DiskBasicFatBuffer fatBuf = fat.getDiskBasicFatBuffer(0, 0);
        if (fatBuf == null) {
            return;
        }

        if (num < fatBuf.getSize()) {
            int value = val != 0 ? basic.getGroupSystemCode() : basic.getGroupUnusedCode();
            fatBuf.set(num, basic.invertUint8((byte) value));
        }
    }

    @Override
    public int getGroupNumber(int num) {
        return num;
    }

    /**
     * Whether FAT position is used
     */
    @Override
    public boolean isUsedGroupNumber(int num) {
        boolean exist = false;

        if (num > basic.getFatEndGroup()) {
            return false;
        }

        DiskBasicFatBuffer fatBuf = fat.getDiskBasicFatBuffer(0, 0);
        if (fatBuf == null) {
            return true;
        }

        // FAT has an unused/used table
        if (num < fatBuf.getSize()) {
            if (basic.invertUint8((byte) fatBuf.get(num)) != basic.getGroupUnusedCode()) {
                exist = true;
            }
        }
        return exist;
    }

    /**
     * Get next group number
     */
    @Override
    public int getNextGroupNumber(int num, int sector_pos) {
        return INVALID_GROUP_NUMBER;
    }

    /**
     * Returns a free position
     */
    @Override
    public int getEmptyGroupNumber() {
        return INVALID_GROUP_NUMBER;
    }

    /**
     * Allocate groups for data size
     */
    @Override
    public int allocateUnitGroups(int fileUnitNum, DiskBasicDirItem<DirectoryTfDos> item, int dataSize, AllocateGroupFlags flags, DiskBasicGroups[] groupItems) throws IOException {
        int[] fileSize = {0};
        int[] groups = {0};

        int rc = 0;
        int remain = dataSize;
        int sectorSize = basic.getSectorSize();

        // Required number of groups
        int groupSize = ((dataSize - 1) / sectorSize / basic.getSectorsPerGroup()) + 1;

        // Find a position where unused are continuous
        int[] groupStart = new int[1]; // Simulate pass-by-reference
        int count = findContinuousArea(groupSize, groupStart);
        if (count < groupSize) {
            // Not enough free space
            rc = -1;
            return rc;
        }

        // Start group decided
        item.setStartGroup(fileUnitNum, groupStart[0]);

        // Allocate area
        rc = allocateGroupsSub(item, groupStart[0], remain, sectorSize, groupItems[0], fileSize, groups);

        return rc;
    }

    /**
     * Data read/comparison processing
     */
    @Override
    public int accessFile(int fileUnitNum, DiskBasicDirItem<DirectoryTfDos> item, InputStream iStream, OutputStream oStream,
                          byte[] sectorBuffer, int sectorSize, int remainSize, int sectorNum, int sectorEnd) throws IOException {
        int size = (remainSize < sectorSize ? remainSize : sectorSize);

        byte[] temp;
        if (oStream != null) {
            // Writing out
            temp = Arrays.copyOfRange(sectorBuffer, 0, size);
            if (basic.isDataInverted()) Common.invertMemory(temp, temp.length);

            oStream.write(temp, 0, temp.length);
        }
        if (iStream != null) {
            // Read and compare
            temp = new byte[size];
            iStream.readNBytes(temp, 0, temp.length);
            if (basic.isDataInverted()) Common.invertMemory(temp, temp.length);

            if (!Arrays.equals(temp, 0, temp.length, sectorBuffer, 0, size)) {
                // Data is different
                return -1;
            }
        }
        return size;
    }

    /**
     * Convert contents when exporting an internal file
     */
    @Override
    public boolean convertDataForLoad(DiskBasicDirItem<DirectoryTfDos> item, InputStream iStream, OutputStream oStream) throws IOException {
        // BASE compatible file
        isBaseCompatible = (item.getFileAttr().isAscii() && item.getExternalAttr() == 1);

        int oSize = iStream.available();

        if (item.getFileAttr().isAscii() && item.getExternalAttr() > 0) {
            // BASE compatible file. Check if the final byte is 0.
            ((SeekableDataInputStream) iStream).position(oSize - 1);
            if (iStream.read() == 0) {
                isBaseCompatible = true;
                oSize--;	// Do not output the last data
            }
            ((SeekableDataInputStream) iStream).position(0);
        }
        if (isBaseCompatible) {
            // In case of BASE compatible, convert TAB code ($14 -> $09)
            byte[] temp = new byte[TEMP_DATA_SIZE];
            while (oSize > 0) {
                int len = iStream.readNBytes(temp, 0, temp.length);
                if (len <= 0) break;
                // TAB code ($14 -> $09) conversion
                for (int pos = 0; pos < temp.length; pos++) {
                    if (temp[pos] == 0x14) temp[pos] = 0x09;
                }
                oStream.write(temp, 0,  len > oSize ? oSize : len);
                oSize -= len;
            }
        } else {
            // Do not convert
            byte[] buffer = new byte[4096];
            int len;
            while ((len = iStream.read(buffer)) > 0) {
                oStream.write(buffer, 0, len);
            }
        }
        return true;
    }

    /**
     * Convert contents when verifying an exported file
     */
    @Override
    public boolean convertDataForVerify(DiskBasicDirItem<DirectoryTfDos> item, InputStream iStream, OutputStream oStream) throws IOException {
        int oSize = iStream.available();

        if (isBaseCompatible) {
            // In case of BASE compatible, convert TAB code ($09 -> $14)
            byte[] temp = new byte[TEMP_DATA_SIZE];
            while (oSize > 0) {
                int len = iStream.read(temp, 0, temp.length);
                if (len <= 0) break;
                // TAB code ($09 -> $14) conversion
                for (int pos = 0; pos < temp.length; pos++) {
                    if (temp[pos] == 0x09) temp[pos] = 0x14;
                }
                oStream.write(temp, 0,  len > oSize ? oSize : len);
                oSize -= len;
            }
            // Finally output $00
            oStream.write(0);
        } else {
            // Do not convert
            byte[] buffer = new byte[4096];
            int len;
            while ((len = iStream.read(buffer)) > 0) {
                oStream.write(buffer, 0, len);
            }
        }
        return true;
    }

    /**
     * Get final sector number from group number
     */
    @Override
    public int getEndSectorFromGroup(int groupNum, int nextGroup, int sectorStart, int sectorSize, int remainSize) {
        int endSector = sectorStart;
        int groupSize = basic.getSectorsPerGroup() * sectorSize;
        if (remainSize < groupSize) {
            endSector += ((remainSize + sectorSize - 1) / sectorSize) - 1;
        } else {
            endSector += basic.getSectorsPerGroup() - 1;
        }
        return endSector;
    }

    /**
     * Whether it is the root directory
     */
    @Override
    public boolean isRootDirectory(int groupNum) {
        // If it is less than the offset, it is root
        return (basic.invertUint8((byte) fat.get(1)) & 0xff) > groupNum;	// invert
    }

    /**
     * Individual processing after filling sector data
     */
    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) throws IOException {
        // IPL
        DiskImageSector sector = basic.getSectorFromSectorPos(0);
        if (sector != null) {
            sector.fill(basic.invertUint8(basic.getFillCodeOnFAT()));

            TfDosIpl ipl = new TfDosIpl();
            byte[] b = sector.getSectorBuffer();
            Serdes.Util.deserialize(new ByteArrayInputStream(b), ipl);
            if (TfDosIpl.SIZE >= 0x100) {
                // Set IPL string
                byte[] iplBytes = basic.getVariousStringParam("IDString").getBytes();
                int len = iplBytes.length;
                if (len > 0) {
                    if (len > TfDosIpl.SIZE) len = TfDosIpl.SIZE;
                    basic.invertMemory(iplBytes, len, ipl.ipl); // Copy to 0x00
                }
                // No automatic execution (autoStart starts at 0xf0)
                for (int i = 0; i < 0x10; i++) {
                    ipl.autoStart[0xf0 + i] = basic.invertUint8((byte) 0x0d);
                }
            }
        }

        // FAT area
        DiskBasicFatBuffer fatBuf = fat.getDiskBasicFatBuffer(0, 0);
        fatBuf.fill(basic.invertUint8(basic.getFillCodeOnFAT()));

        TfDosFat f = new TfDosFat();
        byte[] b = fatBuf.getBuffer();
        Serdes.Util.deserialize(new ByteArrayInputStream(b), f);

        // Mark system area as used
        List<Integer> groups = basic.getReservedGroups();
        for (int group : groups) {
            if (group >= 0 && group < 0xc0) {
                f.fat[group] = basic.invertUint8((byte) basic.getGroupSystemCode());
            }
        }
        // Mark overtrack portions as used
        for (int pos = basic.getFatEndGroup() + 1; pos < 0xc0; pos++) {
            f.fat[pos] = basic.invertUint8((byte) basic.getGroupSystemCode());
        }

        // Set volume number
        int volumeNumber = data.getVolumeNumber();
        f.volumeNum = basic.invertUint8((byte) volumeNumber);
        // Set version number
        f.identNumber = basic.invertUint8((byte) 1);
        f.versionNumber = basic.invertUint8((byte) 2);
        // Set volume name
        byte[] volumeName;
        if (!data.getVolumeName().isEmpty()) {
            volumeName = data.getVolumeName().getBytes();
        } else {
            volumeName = basic.getVariousStringParam("VolumeString").getBytes();
        }
        System.arraycopy(volumeName, 0, f.volumeName, 0, f.volumeName.length);
        basic.invertMemory(f.volumeName, f.volumeName.length);

        // DIR area
        int[] trackNum = new int[1], sideNum = new int[1], sectorNum = new int[1];
        for (int sectorPos = basic.getDirStartSector(); sectorPos <= basic.getDirEndSector(); sectorPos++) {
            getNumFromSectorPos(sectorPos - 1, trackNum, sideNum, sectorNum);
            sector = basic.getSector(trackNum[0], sideNum[0], sectorNum[0]);
            if (sector != null) {
                sector.fill(basic.invertUint8(basic.getFillCodeOnDir()));
            }
        }

        return true;
    }

    /**
     * Convert data before saving file
     */
    @Override
    public boolean convertDataForSave(DiskBasicDirItem<DirectoryTfDos> item, InputStream iStream, OutputStream oStream) throws IOException {
        // BASE compatible file
        isBaseCompatible = item.getFileAttr().isAscii() && item.getExternalAttr() == 1;

        // Processing is same as verify
        return convertDataForVerify(item, iStream, oStream);
    }

    /**
     * Data write process
     */
    @Override
    public int writeFile(DiskBasicDirItem<DirectoryTfDos> item, InputStream iStream, byte[] buffer, int size, int remain,
                         int sectorNum, int groupNum, int nextGroup, int sectorEnd, int seqNum) throws IOException {
        int len = 0;

        if (remain <= size) {
            // Few left
            if (remain < 0) remain = 0;
            if (remain > 0) {
                byte[] temp = new byte[remain];
                iStream.readNBytes(temp, 0, temp.length);

                System.arraycopy(temp, 0, buffer, 0, temp.length);
            }
            if (size > remain) {
                // Remaining buffer is zero-suppressed
                Arrays.fill(buffer, remain, size, (byte) 0);
            }
            len = remain;
        } else {
            // Continuous
            byte[] temp = new byte[size];
            iStream.readNBytes(temp, 0, temp.length);

            System.arraycopy(temp, 0, buffer, 0, temp.length);

            len = size;
        }

        // Invert
        basic.invertMemory(buffer, size);

        return len;
    }

    /**
     * Get attributes of IPL and managed area
     */
    @Override
    public void getIdentifiedData(DiskBasicIdentifiedData data) throws IOException {
        // FAT area
        DiskBasicFatBuffer fatBuf = fat.getDiskBasicFatBuffer(0, 0);
        DiskBasicTypeTFDOS.TfDosFat f = new TfDosFat();
        Serdes.Util.deserialize(new ByteArrayInputStream(fatBuf.getBuffer()), f);

        // volume label
        byte[] volumeName = new byte[12 + 1];
        basic.invertMemory(f.volumeName, volumeName.length, volumeName);
        StringBuilder sb = new StringBuilder();
        basic.getCharCodes().convToString(volumeName, 0, 12, sb, -1);
        data.setVolumeName(sb.toString());
        // volume number
        data.setVolumeNumber(basic.invertUint8(f.volumeNum) & 0xff);
    }

    /**
     * Set attributes of IPL and managed area
     */
    @Override
    public void setIdentifiedData(DiskBasicIdentifiedData data) throws IOException {
        // FAT area
        DiskBasicFatBuffer fatBuf = fat.getDiskBasicFatBuffer(0, 0);
        TfDosFat f = new TfDosFat();
        Serdes.Util.deserialize(new ByteArrayInputStream(fatBuf.getBuffer()), f);

        DiskBasicFormat format = basic.getFormatType();

        // volume label
        if (format.hasVolumeName()) {
            byte[] dst = new byte[f.volumeName.length + 1];
            int l = basic.getCharCodes().convToChars(data.getVolumeName(), dst, dst.length);
            if (l > 0) {
                System.arraycopy(dst, 0, f.volumeName, 0, f.volumeName.length);
                basic.invertMemory(f.volumeName, f.volumeName.length);
            }
        }
        // volume number
        if (format.hasVolumeNumber()) {
            f.volumeNum = basic.invertUint8((byte) data.getVolumeNumber());
        }
    }
}
