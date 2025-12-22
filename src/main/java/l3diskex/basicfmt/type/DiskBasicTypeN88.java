/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt.type;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasic.DiskBasicIdentifiedData;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.DiskBasicFat;
import l3diskex.basicfmt.diritem.DiskBasicDirItemN88.DirectoryN88;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;


/**
 * N88-BASIC processing
 * <p>
 * DiskBasicParam
 *
 * <li>ReservedGroups : Group numbers (clusters) to be reserved</li>
 */
public class DiskBasicTypeN88 extends DiskBasicTypeFAT8<DirectoryN88> {

    public static final int FORMAT_TYPE_N88 = 5;

    @Override
    public boolean isSupported(int typeNumber) {
        return typeNumber == FORMAT_TYPE_N88;
    }

    @Override
    public void init(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryN88> dir) {
        super.init(basic, fat, dir);
    }

    @Override
    public int getEmptyGroupNumber() {
        int newNum = INVALID_GROUP_NUMBER;
        // Search from positions close to the management area

        // Number of groups per track
        int grpsPerTrk = basic.getSectorsPerTrackOnBasic() / basic.getSectorsPerGroup();
        // Maximum number of groups
        int maxGroup = basic.getFatEndGroup() - managedStartGroup;
        if (maxGroup < managedStartGroup) maxGroup = managedStartGroup;
        maxGroup = maxGroup * 2 - 1;

        for (int i = 0; i <= maxGroup; i++) {
            int i2 = i / grpsPerTrk;
            int i4 = i / grpsPerTrk / 2;
            int num;
            if ((i2 & 1) == 0) {
                num = managedStartGroup - ((i4 + 1) * grpsPerTrk) + (i % grpsPerTrk);
            } else {
                num = managedStartGroup + ((i4 + 1) * grpsPerTrk) + (i % grpsPerTrk);
            }
            if (basic.getFatEndGroup() < num)
                continue;
            int groupNum = getGroupNumber(num);
            if (groupNum == basic.getGroupUnusedCode()) {
                newNum = num;
                break;
            }
        }
        return newNum;
    }

    @Override
    public double parseParamOnDisk(boolean isFormatting) {
        if (basic.getFatEndGroup() == 0) {
            int endGroup = basic.getTracksPerSideOnBasic() * basic.getSidesPerDiskOnBasic() * basic.getSectorsPerTrackOnBasic();
            endGroup /= basic.getSectorsPerGroup();
            basic.setFatEndGroup(endGroup - 1);
        }
        return 1.0;
    }

    @Override
    public double checkFat(boolean isFormatting) {
        double validRatio = super.checkFat(isFormatting);
        if (validRatio >= 0.0) {
            // Check whether FAT and directory area are system reserved
            List<Integer> groups = basic.getReservedGroups();
            for (int group : groups) {
                int groupNum = getGroupNumber(group);
                if (groupNum != basic.getGroupSystemCode()) {
                    validRatio = -1.0;
                    break;
                }
            }
        }
        return validRatio;
    }

    @Override
    public void fillSector(DiskImageTrack track, DiskImageSector sector) {
        sector.fill(basic.getFillCodeOnFormat());
    }

    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) {
        // FAT, DIR area
        DiskImageTrack track = basic.getTrack(basic.getManagedTrackNumber(), basic.getFatSideNumber());
        if (track == null) return false;
        List<DiskImageSector> sectors = track.getSectors();
        if (sectors == null) return false;
        int idSector = (basic.getDirEndSector() + 1) % basic.getSectorsPerTrackOnBasic();
        for (DiskImageSector sector : sectors) {
            if (sector != null) {
                // Clear file management area. ID area is cleared with 0
                sector.fill(sector.getSectorNumber() != idSector ? basic.getFillCodeOnFAT() : 0);
            }
        }

        // Reserve cluster positions used by the system
        List<Integer> groups = basic.getReservedGroups();
        for (int group : groups) {
            setGroupNumber(group, basic.getGroupSystemCode());
        }

        return true;
    }

    /**
     * Determine the data size of the last sector of the file
     *
     * @param item          Directory item
     * @param iStream       [in,out] Input stream. Used for verify. {@code null} when reading data.
     * @param oStream       [in,out] Output destination. Used when reading data. {@code null} during verify.
     * @param sectorBuffer  Sector buffer
     * @param sectorSize    Buffer size
     * @param remainSize    Remaining size
     * @return Remaining size
     */
    @Override
    public int calcDataSizeOnLastSector(DiskBasicDirItem<DirectoryN88> item,
                                        InputStream iStream,
                                        OutputStream oStream,
                                        byte[] sectorBuffer,
                                        int sectorOffset,
                                        int sectorSize,
                                        int remainSize) throws IOException {
        // File size is at sector size boundary, so calculation is required
        if (item.needCheckEofCode()) {
            // Output up to one byte before the termination code
            byte eofCode = basic.invertUint8(basic.getTextTerminateCode());
            byte nullCode = basic.invertUint8((byte) 0);
            // Except when random access
            int len = sectorSize - 1;
            for (; len >= 0; len--) {
                if (sectorBuffer[len] != eofCode && sectorBuffer[len] != nullCode)
                    break;
            }
            if (len >= 0)
                sectorSize = len + 1;
        } else {
            // No means of calculation, so return the remaining size as is
            if (iStream != null) {
                sectorSize = iStream.available() % sectorSize; // TODO assume available as length
            } else {
                sectorSize = remainSize;
            }
        }
        return sectorSize;
    }

    @Override
    public int writeFile(DiskBasicDirItem<DirectoryN88> item,
                         InputStream iStream,
                         byte[] buffer,
                         int size,
                         int remain,
                         int sectorNum,
                         int groupNum,
                         int nextGroup,
                         int sectorEnd,
                         int seqNum) throws IOException {
        boolean needEofCode = item.needCheckEofCode();

        int len = 0;
        if (remain <= size) {
            // Few left
            if (remain < 0) remain = 0;
            if (remain > 0) {
                if (needEofCode) {
                    int bytesRead = iStream.read(buffer, 0, remain);
                    // Insert termination code at the end
                    // However, if the remaining size matches the sector size, do not insert it
                    if (bytesRead + 1 == remain) {
                        buffer[remain - 1] = basic.getTextTerminateCode();
                    }
                } else {
                    iStream.readNBytes(buffer, 0, remain);
                }
            }
            if (size > remain) {
                // Remaining buffer is zero-suppressed
                Arrays.fill(buffer, remain, size, (byte) 0);
            }
            len = remain;
        } else {
            // Continuous
            iStream.readNBytes(buffer, 0, size);
            len = size;
        }
        // Invert
        basic.invertMemory(buffer, size);

        return len;
    }
}
