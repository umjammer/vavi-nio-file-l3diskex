///
/// Copyright (c) Sasaji. All rights reserved.
///

package l3diskex.diskimg.writer;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import l3diskex.diskimg.DiskImage;
import l3diskex.diskimg.DiskImage.DiskImageDisk;
import l3diskex.diskimg.DiskImage.DiskImageFile;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;
import l3diskex.diskimg.DiskResult;
import l3diskex.diskimg.DiskWriter;
import l3diskex.diskimg.DiskWriter.DiskImageWriter;


/**
 * Plain disk writer
 */
public class DiskPlainWriter extends DiskImageWriter {

    //
    // Save in plain format
    //

    @Override
    public boolean isSupported(String type) {
        return "plain".equalsIgnoreCase(type);
    }

    @Override
    public void init(DiskWriter writer, DiskResult result) {
        super.init(writer, result);
    }

    /**
     * Save one disk
     *
     * @param disk       Image of one disk
     * @param sideNumber Side number (0-) / If -1, both sides
     * @param oStream    Output destination
     * @return 0 Normal, -1 Error
     */
    private int saveDisk(DiskImageDisk disk, int sideNumber, OutputStream oStream) throws IOException {
        if (disk == null) {
            result.setError(DiskResult.ERR_NO_DISK);
            return result.getValid();
        }

        List<DiskImageTrack> tracks = disk.getTracks();
        if (tracks == null) {
            result.setError(DiskResult.ERR_NO_DATA);
            return result.getValid();
        }

        int trackStart = sideNumber < 0 ? 0 : sideNumber;
        int trackCount = tracks.size();
        int trackStep = sideNumber < 0 ? 1 : 2;

        for (int trackNum = trackStart; trackNum < trackCount; trackNum += trackStep) {
            DiskImageTrack track = tracks.get(trackNum);
            if (track == null) continue;
            List<DiskImageSector> sectors = track.getSectors();
            if (sectors == null) continue;
            // Output in order of sector number
            List<DiskImageSector> sortedSectors = new ArrayList<>(sectors);
            sortedSectors.sort(Comparator.comparingInt(DiskImageSector::getIDR));
            for (DiskImageSector sector : sortedSectors) {
                if (sector == null) continue;

                // write sector body
                byte[] buffer = sector.getSectorBuffer();
                int bufferSize = sector.getSectorBufferSize();
                if (buffer != null && bufferSize > 0) {
                    oStream.write(buffer, 0, bufferSize);
                }
                //sector.clearModify();
            }
        }

        //disk.clearModify();
        return result.getValid();
    }

    /**
     * Save to file as plain image
     *
     * @param image      Disk image
     * @param diskNumber Disk number (0-) / If -1, all
     * @param sideNumber Side number (0-) / If -1, both sides
     * @param oStream    Output destination
     * @return 0 Normal, -1 Error
     */
    @Override
    public int saveDisk(DiskImage image, int diskNumber, int sideNumber, OutputStream oStream) throws IOException {
        result.clear();

        DiskImageFile file = image.getFile();
        if (file == null) {
            result.setError(DiskResult.ERR_NO_DATA);
            return result.getValid();
        }

        if (diskNumber < 0) {
            // Save only the first disk
            List<DiskImageDisk> disks = file.getDisks();
            if (disks == null || disks.size() <= 0) {
                result.setError(DiskResult.ERR_NO_DISK);
                return result.getValid();
            }
            for (int diskNum = 0; diskNum < 1; diskNum++) {
                DiskImageDisk disk = disks.get(diskNum);
                saveDisk(disk, -1, oStream);
            }
        } else {
            // Save specified disk
            DiskImageDisk disk = file.getDisk(diskNumber);
            saveDisk(disk, sideNumber, oStream);
        }

        return result.getValid();
    }
}
