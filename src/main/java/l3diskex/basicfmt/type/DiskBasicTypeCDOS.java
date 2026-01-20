///
/// Copyright (c) Sasaji. All rights reserved.
///

package l3diskex.basicfmt.type;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalDate;
import java.util.Arrays;

import l3diskex.Common;
import l3diskex.Utils;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasic.DiskBasicIdentifiedData;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.DiskBasicFat;
import l3diskex.basicfmt.DiskBasicFat.DiskBasicFatBuffer;
import l3diskex.basicfmt.DiskBasicParam.DiskBasicFormat;
import l3diskex.basicfmt.diritem.DiskBasicDirItemCDOS.DirectoryCDos;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import vavi.io.SeekableDataInputStream;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;

import static l3diskex.Common.copyMemory;
import static l3diskex.Utils.TEMP_DATA_SIZE;


/**
 * C-DOS processing
 * <p>
 * DiskBasicParam
 * <li>IDString     ID in FAT area</li>
 * <li>IPLString    IPL in sector 1</li>
 * <li>VolumeString volume name</li>
 * <li>Endian       byte order of 16-bit values</li>
 *
 * @see "http://fukui.s17.xrea.com/retro/cdos/index.html"
 */
public class DiskBasicTypeCDOS extends DiskBasicTypeMZBase<DirectoryCDos> {

    /** CDos usage sector */
    @Serdes
    static class FatCDos {
        /** Usage status */
        @Element(sequence = 1)
        public byte[] bits = new byte[0xae];
        /** Extended directory (not supported) */
        @Element(sequence = 2)
        public short exDir;
        /** Volume number */
        @Element(sequence = 3)
        public short volumeNum;
        /** Year */
        @Element(sequence = 4)
        public byte yy;
        /** Month */
        @Element(sequence = 5)
        public byte mm;
        /** Day */
        @Element(sequence = 6)
        public byte dd;
        /** Volume name */
        @Element(sequence = 7)
        public byte[] volumeName = new byte[27];
        /** ID */
        @Element(sequence = 8)
        public byte[] id = new byte[16];
        @Element(sequence = 9)
        public byte[] reserved1 = new byte[0x20];
    }

    public static final int FORMAT_TYPE_CDOS = 72;

    @Override
    public boolean isSupported(int typeNumber) {
        return typeNumber == FORMAT_TYPE_CDOS;
    }

    @Override
    public void init(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryCDos> dir) {
        super.init(basic, fat, dir);
    }

    /**
     * Get the position of used groups
     */
    @Override
    public void calcUsedGroupPos(int num, int[] pos, int[] mask) {
        mask[0] = 1 << (pos[0] & 7);
        pos[0] = (pos[0] >> 3);
    }

    /**
     * Check FAT area
     */
    @Override
    public double checkFat(boolean isFormatting) throws IOException {
        double valid_ratio = 1.0;

        // FAT area
        DiskBasicFatBuffer fatbuf = fat.getDiskBasicFatBuffer(0, 0);
        if (fatbuf == null) {
            return -1.0;
        }
        FatCDos f = new FatCDos();
        Serdes.Util.deserialize(new ByteArrayInputStream(fatbuf.getBuffer()), f);
        if (basic.invertUint8(f.bits[0]) != (byte) 0xff) {
            valid_ratio = 0.1;
        }
        byte[] d_id = basic.getVariousStringParam("IDString").getBytes(); // To8BitData
        if (d_id.length > 0) {
            // For FM, there is "FM" in the ID part
            byte[] s_id = new byte[f.id.length];
            basic.invertMemory(f.id, f.id.length, s_id);
            if (!Arrays.equals(s_id, d_id)) {
                valid_ratio = 0.1;
            }
        }

        // Final group number
        basic.setFatEndGroup(basic.getTracksPerSide() * basic.getSidesPerDiskOnBasic() * basic.getSectorsPerTrackOnBasic() - 1);

        return valid_ratio;
    }

    /**
     * Allocate groups for data size
     */
    @Override
    public int allocateUnitGroups(int fileUnitNum, DiskBasicDirItem<DirectoryCDos> item, int dataSize, AllocateGroupFlags flags, DiskBasicGroups[] groupItems) throws IOException {
        int[] fileSize = {0};
        int[] groups = {0};

        int rc = 0;
        //int groupNum = 0;
        int remain = dataSize;
        int sectorSize = basic.getSectorSize();

        // Number of required groups
        int groupSize = ((dataSize - 1) / sectorSize / basic.getSectorsPerGroup()) + 1;

        // Find a position where unused are continuous
        int[] groupStart = new int[1];
        int cnt = findContinuousArea(groupSize, groupStart);

        if (cnt < groupSize) {
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
     * Whether a subdirectory can be created
     */
    @Override
    public boolean canMakeDirectory() {
        return false;
    }

    /**
     * Individual processing after filling sector data
     */
    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) throws IOException {
        //
        // IPL
        //
        DiskImageSector sector = basic.getSectorFromSectorPos(0);
        if (sector != null) {
            sector.fill(basic.invertUint8(basic.getFillCodeOnFAT()));    // invert
            byte[] buf = sector.getSectorBuffer();
            if (buf != null) {
                byte[] ipl = basic.getVariousStringParam("IPLString").getBytes(); // To8BitData
                int len = ipl.length;
                if (len > 0) {
                    if (len > 32) len = 32;
                    basic.invertMemory(ipl, len, buf);
                }
            }
        }

        //
        // FAT area
        //
        DiskBasicFatBuffer fatbuf = fat.getDiskBasicFatBuffer(0, 0);
        if (fatbuf == null) {
            return false;
        }
        fatbuf.fill(basic.invertUint8(basic.getFillCodeOnFAT()));
        //memset(fatbuf.getBuffer(), basic.getFillCodeOnFAT(), fatbuf.getSize());
        //basic.invertMem(fatbuf.getBuffer(), fatbuf.getSize());

        byte[] b = fatbuf.getBuffer();
        if (b == null) {
            return false;
        }
        FatCDos f = new FatCDos();
        Serdes.Util.deserialize(new ByteArrayInputStream(b), f);

        // Mark system area as used
        basic.invertMemory(f.bits, f.bits.length);

        int groupNumStart = 0;
        int groupNumEnd = (basic.getManagedTrackNumber() + 1) * basic.getSectorsPerTrackOnBasic() * basic.getSidesPerDiskOnBasic();
        for (int groupNum = groupNumStart; groupNum < groupNumEnd; groupNum++) {
            int[] pos = {groupNum};
            int[] mask = {0};
            calcUsedGroupPos(groupNum, pos, mask);
            f.bits[pos[0]] |= (byte) mask[0];
        }
        // Mark overtrack part as used
        groupNumStart = basic.getFatEndGroup() + 1;
        groupNumEnd = (0xb0 << 3);
        for (int groupNum = groupNumStart; groupNum < groupNumEnd; groupNum++) {
            int[] pos = {groupNum};
            int[] mask = {0};
            calcUsedGroupPos(groupNum, pos, mask);
            f.bits[pos[0]] |= (byte) mask[0];
        }

        basic.invertMemory(f.bits, f.bits.length);

        // Extended directory
        f.exDir = basic.invertUint16((short) 0xffff);

        // ID
        byte[] id = basic.getVariousStringParam("IDString").getBytes(); // To8BitData
        if (id.length > 0) {
            basic.invertMemory(id, id.length, f.id);
        }

        // Set volume number
        int vol_num = data.getVolumeNumber();
        f.volumeNum = basic.invertAndOrderUint16((short) vol_num);
        // Set volume name
        byte[] volumeName;
        if (!data.getVolumeName().isEmpty()) {
            volumeName = data.getVolumeName().getBytes();
        } else {
            volumeName = basic.getVariousStringParam("VolumeString").getBytes(); // To8BitData
        }
        Common.copyMemory(volumeName, volumeName.length, (byte) 0, f.volumeName, f.volumeName.length);
        basic.invertMemory(f.volumeName, f.volumeName.length);
        // Volume date
        LocalDate tm = Utils.convDateStrToTm(data.getVolumeDate());
        if (tm == null) {
            tm = LocalDate.now();
        }
        byte[] yy = new byte[1], mm = new byte[1], dd = new byte[1];
        Utils.convTmToYYMMDD(tm.atStartOfDay(), yy, mm, dd);
        f.yy = basic.invertUint8(yy[0]);
        f.mm = basic.invertUint8(mm[0]);
        f.dd = basic.invertUint8(dd[0]);

        //
        // Auto Start
        //
        sector = basic.getSectorFromSectorPos(basic.getFatStartSector());
        if (sector != null) {
            sector.fill(basic.invertUint8(basic.getFillCodeOnFAT()));
        }

        //
        // DIR area
        //
        int[] trackNumber = new int[1], sideNumber = new int[1], sectorNumber = new int[1];
        for (int sectorPos = basic.getDirStartSector(); sectorPos <= basic.getDirEndSector(); sectorPos++) {
            getNumFromSectorPos(sectorPos - 1, trackNumber, sideNumber, sectorNumber);
            sector = basic.getSector(trackNumber[0], sideNumber[0], sectorNumber[0]);
            if (sector != null) {
                sector.fill(basic.invertUint8(basic.getFillCodeOnDir()));
            }
        }

        return true;
    }

    /**
     * Data read/comparison processing
     */
    @Override
    public int accessFile(int fileUnitNum, DiskBasicDirItem<DirectoryCDos> item, InputStream iStream, OutputStream oStream, byte[] sectorBuffer, int sectorSize, int remainSize, int sectorNum, int sectorEnd) throws IOException {
        int size = remainSize < sectorSize ? remainSize : sectorSize;

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

            if (Arrays.compare(temp, sectorBuffer) != 0) {
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
    public boolean convertDataForLoad(DiskBasicDirItem<DirectoryCDos> item, InputStream iStream, OutputStream oStream) throws IOException {
        int oSize = iStream.available(); // TODO assume available as GetLength()

        if (item.getFileAttr().isAscii()) {
            // Check if the final byte is 0
            ((SeekableDataInputStream) iStream).position(oSize - 1);
            if (iStream.read() == 0) {
                oSize--;
            }
            ((SeekableDataInputStream) iStream).position(0);
        }

        byte[] temp = new byte[TEMP_DATA_SIZE];
        while (oSize > 0) {
            int len = iStream.readNBytes(temp, 0, temp.length);
            oStream.write(temp, 0, len > oSize ? oSize : len);
            oSize -= len;
        }

        return true;
    }

    /**
     * Convert contents when verifying an exported file
     */
    @Override
    public boolean convertDataForVerify(DiskBasicDirItem<DirectoryCDos> item, InputStream iStream, OutputStream oStream) throws IOException {
        int osize = iStream.available(); // TODO assume available as GetLength()

        boolean needNullCode = false;
        if (item.getFileAttr().isAscii()) {
            // Check if the final byte is 0
            ((SeekableDataInputStream) iStream).position(osize - 1);
            int c = iStream.read();
            if (c > 0 && c != 0xff) {
                needNullCode = true;
            }
            ((SeekableDataInputStream) iStream).position(0);
        }

        byte[] temp = new byte[TEMP_DATA_SIZE];
        int len;
        while ((len = iStream.read(temp, 0, temp.length)) > 0) {
            oStream.write(temp, 0, len);
        }

        if (needNullCode) {
            // Append $00 at the end and output
            oStream.write(0);
        }
        return true;
    }

    /**
     * Convert data before saving file
     */
    @Override
    public boolean convertDataForSave(DiskBasicDirItem<DirectoryCDos> item, InputStream iStream, OutputStream oStream) throws IOException {
        // Processing is same as verify
        return convertDataForVerify(item, iStream, oStream);
    }

    /**
     * Data write process
     */
    @Override
    public int writeFile(DiskBasicDirItem<DirectoryCDos> item, InputStream iStream, byte[] buffer, int size, int remain, int sectorNum, int groupNum, int nextGroup, int sectorEnd, int seqNum) throws IOException {
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
        if (fatBuf == null) {
            return;
        }
        FatCDos f = new FatCDos();
        Serdes.Util.deserialize(new ByteArrayInputStream(fatBuf.getBuffer()), f);

        // volume number
        data.setVolumeNumber(basic.invertAndOrderUint16(f.volumeNum));
        // volume label
        byte[] volumeName = new byte[f.volumeName.length];
        basic.invertMemory(f.volumeName, f.volumeName.length, volumeName);
        data.setVolumeName(new String(volumeName));
        data.setVolumeNameMaxLength(f.volumeName.length);
        // volume date
        LocalDate tm = Utils.convYYMMDDToTm(
                basic.invertUint8(f.yy),
                basic.invertUint8(f.mm),
                basic.invertUint8(f.dd)
        );
        data.setVolumeDate(l3diskex.Utils.formatYMDStr(tm));
    }

    /**
     * Set attributes of IPL and managed area
     */
    @Override
    public void setIdentifiedData(DiskBasicIdentifiedData data) throws IOException {
        // FAT area
        DiskBasicFatBuffer fatBuf = fat.getDiskBasicFatBuffer(0, 0);
        if (fatBuf == null) {
            return;
        }
        FatCDos f = new FatCDos();
        Serdes.Util.deserialize(new ByteArrayInputStream(fatBuf.getBuffer()), f);

        DiskBasicFormat format = basic.getFormatType();

        // volume number
        if (format.hasVolumeNumber()) {
            f.volumeNum = basic.invertAndOrderUint16((short) data.getVolumeNumber());
        }
        // volume label
        if (format.hasVolumeName()) {
            byte[] volumeName = data.getVolumeName().getBytes();
            copyMemory(volumeName, volumeName.length, (byte) 0, f.volumeName, f.volumeName.length);
            basic.invertMemory(f.volumeName, f.volumeName.length);
        }
        // volume date
        if (format.hasVolumeDate()) {
            LocalDate tm = Utils.convDateStrToTm(data.getVolumeDate());
            if (tm != null) {
                byte[] yy = new byte[1], mm = new byte[1], dd = new byte[1];
                Utils.convTmToYYMMDD(tm.atStartOfDay(), yy, mm, dd);
                f.yy = basic.invertUint8(yy[0]);
                f.mm = basic.invertUint8(mm[0]);
                f.dd = basic.invertUint8(dd[0]);
            }
        }
    }
}
