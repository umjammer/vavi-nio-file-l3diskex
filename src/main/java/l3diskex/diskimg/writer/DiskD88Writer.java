///
/// Copyright (c) Sasaji. All rights reserved.
///

package l3diskex.diskimg.writer;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import l3diskex.diskimg.DiskD88.DiskD88DiskHeader;
import l3diskex.diskimg.DiskD88.DiskD88SectorHeader;
import l3diskex.diskimg.DiskImage;
import l3diskex.diskimg.DiskImage.DiskImageDisk;
import l3diskex.diskimg.DiskImage.DiskImageFile;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;
import l3diskex.diskimg.DiskResult;
import l3diskex.diskimg.DiskWriter;
import l3diskex.diskimg.DiskWriter.DiskImageWriter;
import vavi.io.SeekableDataOutputStream;
import vavi.util.serdes.Serdes;


/** D88形式ディスクライター */
public class DiskD88Writer extends DiskImageWriter {

    /**
     * Maximum number of tracks that the D88 format can handle.
     * The real value depends on the actual D88 implementation.
     */
    private static final int DISKD88_MAX_TRACKS = 300;

    @Override
    public boolean isSupported(String type) {
        return "d88".equalsIgnoreCase(type);
    }

    @Override
    public void init(DiskWriter writer, DiskResult result) {
        super.init(writer, result);
    }

    /**
     * Validate a disk image.
     *
     * @param image      The disk image to validate.
     * @param diskNumber Disk number (>=0) or -1 for all disks.
     * @param sideNumber Side number (ignored by this class).
     * @return 0 on success, negative value on failure.
     */
    @Override
    public int validateDisk(DiskImage image, int diskNumber, int sideNumber) {
        result.clear();

        DiskImageFile file = image.getFile();
        if (file == null) {
            result.setError(DiskResult.ERR_NO_DATA);
            return result.getValid();
        }

        if (diskNumber < 0) {
            // Validate all disks
            List<DiskImageDisk> disks = file.getDisks();
            if (disks == null || disks.size() <= 0) {
                result.setError(DiskResult.ERR_NO_DISK);
                return result.getValid();
            }
            for (int i = 0; i < disks.size(); i++) {
                DiskImageDisk disk = disks.get(i);
                List<DiskImageTrack> tracks = disk.getTracks();
                if (tracks != null && tracks.size() > DISKD88_MAX_TRACKS) {
                    result.setWarn(DiskResult.ERRV_TOO_MANY_TRACKS, i, DISKD88_MAX_TRACKS);
                }
            }
        } else {
            // Validate a specific disk
            DiskImageDisk disk = file.getDisk(diskNumber);
            if (disk == null) {
                result.setError(DiskResult.ERR_NO_DISK);
                return result.getValid();
            }
            List<DiskImageTrack> tracks = disk.getTracks();
            if (tracks != null && tracks.size() > DISKD88_MAX_TRACKS) {
                result.setWarn(DiskResult.ERRV_TOO_MANY_TRACKS, diskNumber, DISKD88_MAX_TRACKS);
            }
        }

        return result.getValid();
    }

    /**
     * Save a disk image (or all images when <code>diskNumber</code> is
     * negative).  The data is written to the supplied
     * {@link SeekableDataOutputStream}.  The same stream is reused for
     * every disk, so callers usually open the stream once and close it
     * after all calls finish.
     *
     * @param image      Disk image object
     * @param diskNumber Disk number or -1 for all disks
     * @param sideNumber Side number (ignored)
     * @param oStream    Destination stream
     * @return 0 on success, negative value on failure
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
            // Save all disks
            List<DiskImageDisk> disks = file.getDisks();
            if (disks == null || disks.size() <= 0) {
                result.setError(DiskResult.ERR_NO_DISK);
                return result.getValid();
            }
            for (DiskImageDisk disk : disks) {
                saveDisk(disk, sideNumber, oStream);
            }
        } else {
            // Save a specific disk
            DiskImageDisk disk = file.getDisk(diskNumber);
            saveDisk(disk, sideNumber, oStream);
        }

        return result.getValid();
    }

    /**
     * Core routine that actually writes the D88 image to disk.
     *
     * @param disk       Disk to write
     * @param sideNumber Side number (ignored by this class)
     * @param oStream    Destination stream
     * @return 0 on success, negative on failure
     */
    private int saveDisk(DiskImageDisk disk, int sideNumber, OutputStream oStream) throws IOException {
        if (disk == null) {
            result.setError(DiskResult.ERR_NO_DISK);
            return result.getValid();
        }

        DiskD88DiskHeader d88Header = new DiskD88DiskHeader();

        // Prepare the disk header
        int newSize = 0;
        disk.setOffsetStart(d88Header.getHeaderSize());
        if (sideNumber < 0) {
            newSize = disk.shrinkTracks(writer.isTrimUnusedData());
            disk.setSizeWithoutHeader(newSize);
        }

        // ディスクヘッダ
        d88Header.newHeader(disk.getHeader());

        // write disk header
        Serdes.Util.serialize(d88Header.getHeader(), oStream);

        List<DiskImageTrack> tracks = disk.getTracks();
        if (tracks == null) {
            result.setError(DiskResult.ERR_NO_DATA);
            return result.getValid();
        }

        // オフセットクリア
        d88Header.clearOffsets();

        int trackStart = sideNumber < 0 ? 0 : sideNumber;
        int trackCount = tracks.size();
        int trackStep = sideNumber < 0 ? 1 : 2;

        int trackOffPos = 0;
        int trackOffset = disk.getOffsetStart();
        for (int t = trackStart; t < trackCount && trackOffPos < DISKD88_MAX_TRACKS; t += trackStep) {
            DiskImageTrack track = tracks.get(t);
            if (track == null) continue;
            int trackSize = 0;
            List<DiskImageSector> sectors = track.getSectors();
            int sectorCnt = sectors != null ? sectors.size() : 0;
            for (int sector_num = 0; sector_num < sectorCnt; sector_num++) {
                DiskImageSector sector = sectors.get(sector_num);
                if (sector == null) continue;

                // セクタヘッダ
                DiskD88SectorHeader sectHdr = new DiskD88SectorHeader();
                sectHdr.newHeader(sector.getHeader());

                if (sideNumber >= 0) {
                    // 片面だけ保存のときはID Hを0にする
                    sectHdr.setIDH((byte) 0);
                }

                // write sector header
                Serdes.Util.deserialize(sectHdr.getHeader(), oStream);
                trackSize += sectHdr.getHeaderSize();

                // write sector body
                byte[] buf = sector.getSectorBuffer();
                int bufSize = sector.getSectorBufferSize();
                if (buf != null) {
                    oStream.write(buf, 0, bufSize);
                    trackSize += bufSize;
                }
            }
            //
            if (!writer.isTrimUnusedData()) {
                // 余分なデータ
                byte[] extra = track.getExtraData();
                int extraSize = track.getExtraDataSize();
                if (extra != null && extraSize > 0) {
                    oStream.write(extra, 0, extraSize);
                    trackSize += extraSize;
                }
            }
            if (trackSize > 0) {
                // オフセットをセット
                d88Header.setOffset(trackOffPos, trackOffset);
                trackOffPos++;
                trackOffset += trackSize;
                d88Header.setDiskSize(trackOffset);
            }
        }
        if (sideNumber >= 0) {
            // 片面だけ保存のときはディスクヘッダを更新
            ((SeekableDataOutputStream) oStream).position(0);
            Serdes.Util.deserialize(d88Header.getHeader(), oStream);
        }

        if (result.getValid() >= 0) {
            disk.clearModify();
        }
        return result.getValid();
    }
}
