package l3diskex.diskimg;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import l3diskex.diskimg.DiskD88.DiskD88DiskHeader;
import l3diskex.diskimg.DiskD88.DiskD88SectorHeader;
import l3diskex.diskimg.DiskImage.DiskImageDisk;
import l3diskex.diskimg.DiskImage.DiskImageFile;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;
import l3diskex.diskimg.DiskWriter.DiskImageWriter;
import vavi.io.SeekableDataOutputStream;
import vavi.util.serdes.Serdes;


public class DiskD88Writer extends DiskImageWriter {

    // Maximum number of tracks that the D88 format can handle.
    // The real value depends on the actual D88 implementation.
    private static final int DISKD88_MAX_TRACKS = 300;

    /**
     * Constructor.  The base class {@link DiskImageWriter} stores the
     * {@link DiskWriter} instance and the {@link DiskResult} instance.
     *
     * @param dw_      Disk writer that owns the low‑level I/O settings
     * @param result_  Result object used for error reporting
     */
    public DiskD88Writer(DiskWriter dw_, DiskResult result_) {
        super(dw_, result_);
    }

    /* ----------------------------------------------------------------
     *  PUBLIC API
     * ---------------------------------------------------------------- */

    /**
     * Validate a disk image.
     *
     * @param image     The disk image to validate.
     * @param diskNumber Disk number (>=0) or -1 for all disks.
     * @param sideNumber Side number (ignored by this class).
     * @return 0 on success, negative value on failure.
     */
    @Override
    public int ValidateDisk(DiskImage image, int diskNumber, int sideNumber) {
        p_result.clear();
        DiskImageFile file = image.getFile();
        if (file == null) {
            p_result.setError(DiskResult.ERR_NO_DATA);
            return p_result.getValid();
        }

        if (diskNumber < 0) {          // Validate all disks
            List<DiskImageDisk> disks = file.getDisks();
            if (disks == null || disks.size() <= 0) {
                p_result.setError(DiskResult.ERR_NO_DISK);
                return p_result.getValid();
            }

            for (int i = 0; i < disks.size(); i++) {
                DiskImageDisk disk = disks.get(i);
                List<DiskImageTrack> tracks = disk.getTracks();
                if (tracks != null && tracks.size() > DISKD88_MAX_TRACKS) {
                    p_result.setWarn(
                            DiskResult.ERRV_TOO_MANY_TRACKS,
                            i, DISKD88_MAX_TRACKS);
                }
            }
        } else {                         // Validate a specific disk
            DiskImageDisk disk = file.getDisk(diskNumber);
            if (disk == null) {
                p_result.setError(DiskResult.ERR_NO_DISK);
                return p_result.getValid();
            }
            List<DiskImageTrack> tracks = disk.getTracks();
            if (tracks != null && tracks.size() > DISKD88_MAX_TRACKS) {
                p_result.setWarn(
                        DiskResult.ERRV_TOO_MANY_TRACKS,
                        diskNumber, DISKD88_MAX_TRACKS);
            }
        }

        return p_result.getValid();
    }

    /**
     * Save a disk image (or all images when <code>diskNumber</code> is
     * negative).  The data is written to the supplied
     * {@link SeekableDataOutputStream}.  The same stream is reused for
     * every disk, so callers usually open the stream once and close it
     * after all calls finish.
     *
     * @param image     Disk image object
     * @param diskNumber Disk number or -1 for all disks
     * @param sideNumber Side number (ignored)
     * @param ostream   Destination stream
     * @return 0 on success, negative value on failure
     */
    @Override
    public int SaveDisk(DiskImage image, int diskNumber, int sideNumber,
                        OutputStream ostream) {
        p_result.clear();
        DiskImageFile file = image.getFile();
        if (file == null) {
            p_result.setError(DiskResult.ERR_NO_DATA);
            return p_result.getValid();
        }

        if (diskNumber < 0) {          // Save all disks
            List<DiskImageDisk> disks = file.getDisks();
            if (disks == null || disks.size() <= 0) {
                p_result.setError(DiskResult.ERR_NO_DISK);
                return p_result.getValid();
            }

            for (int i = 0; i < disks.size(); i++) {
                DiskImageDisk disk = disks.get(i);
                int r = SaveDisk(disk, sideNumber, ostream);
                if (r != 0) return r;
            }
        } else {                         // Save a specific disk
            DiskImageDisk disk = file.getDisk(diskNumber);
            if (disk == null) {
                p_result.setError(DiskResult.ERR_NO_DISK);
                return p_result.getValid();
            }
            int r = SaveDisk(disk, sideNumber, ostream);
            if (r != 0) return r;
        }

        return p_result.getValid();
    }

    /* ----------------------------------------------------------------
     *  PRIVATE HELPERS
     * ---------------------------------------------------------------- */

    /**
     * Core routine that actually writes the D88 image to disk.
     *
     * @param disk      Disk to write
     * @param sideNumber Side number (ignored by this class)
     * @param ostream   Destination stream
     * @return 0 on success, negative on failure
     */
    private int SaveDisk(DiskImageDisk disk, int sideNumber,
                         OutputStream ostream) {
        if (disk == null) {
            p_result.setError(DiskResult.ERR_NO_DISK);
            return p_result.getValid();
        }

        DiskD88DiskHeader d88Header = new DiskD88DiskHeader();
        int newSize = 0;

        /* 1. Prepare the disk header -------------------------------- */
        disk.setOffsetStart(d88Header.getHeaderSize());
        if (sideNumber < 0) {                // Only when saving a whole disk
            newSize = disk.shrinkTracks(p_dw.IsTrimUnusedData());
            disk.setSizeWithoutHeader(newSize);
        }
        d88Header.newHeader(disk.getHeader());

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Serdes.Util.deserialize(d88Header.getHeader(), baos);
            ostream.write(baos.toByteArray(), 0,
                          d88Header.getHeaderSize());
        } catch (IOException e) {
            p_result.setError(DiskResult.ERR_NO_DATA);
            return p_result.getValid();
        }

        /* 2. Walk through all tracks -------------------------------- */
        List<DiskImageTrack> tracks = disk.getTracks();
        if (tracks == null) {
            p_result.setError(DiskResult.ERR_NO_DATA);
            return p_result.getValid();
        }

        d88Header.clearOffsets();
        int trackStart   = sideNumber < 0 ? 0 : sideNumber;
        int trackCount   = tracks.size();
        int trackStep    = sideNumber < 0 ? 1 : 2;
        int trackOffPos  = 0;
        int trackOffset = disk.getOffsetStart();

        for (int t = trackStart; t < trackCount && trackOffPos < DISKD88_MAX_TRACKS;
             t += trackStep) {

            DiskImageTrack track = tracks.get(t);
            if (track == null) continue;

            int trackSize = 0;
            List<DiskImageSector> sectors = track.getSectors();
            int sectorCnt = sectors != null ? sectors.size() : 0;

            /* 2a. Process all sectors in this track --------------- */
            for (int s = 0; s < sectorCnt; s++) {
                DiskImageSector sector = sectors.get(s);
                if (sector == null) continue;

                DiskD88SectorHeader sectHdr = new DiskD88SectorHeader();
                sectHdr.newHeader(sector.getHeader());

                if (sideNumber >= 0) sectHdr.setIDH((byte) 0);

                try {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    Serdes.Util.deserialize(sectHdr.getHeader(), baos);
                    ostream.write(baos.toByteArray(), 0,
                                  sectHdr.getHeaderSize());
                    trackSize += sectHdr.getHeaderSize();

                    byte[] buf = sector.getSectorBuffer();
                    int bufSize = sector.getSectorBufferSize();
                    if (buf != null) {
                        ostream.write(buf, 0, bufSize);
                        trackSize += bufSize;
                    }
                } catch (IOException e) {
                    p_result.setError(DiskResult.ERR_NO_DATA);
                    return p_result.getValid();
                }
            }

            /* 2b. Append data from the side file (if any) ------------ */
            if (!p_dw.IsTrimUnusedData()) {
                byte[] extra = track.getExtraData();
                int extraSize = track.getExtraDataSize();
                if (extra != null && extraSize > 0) {
                    try {
                        ostream.write(extra, 0, extraSize);
                        trackSize += extraSize;
                    } catch (IOException e) {
                        p_result.setError(DiskResult.ERR_NO_DATA);
                        return p_result.getValid();
                    }
                }
            }

            /* 2c. Record the size of this track in the header -------- */
            if (trackSize > 0) {
                d88Header.setOffset(trackOffPos, trackOffset);
                trackOffPos++;
                trackOffset += trackSize;
                d88Header.setDiskSize(trackOffset);
            }
        }

        /* 3. Write back the updated header (if a side was requested)  */
        if (sideNumber >= 0) {
            try {
                ((SeekableDataOutputStream) ostream).position(0);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                Serdes.Util.deserialize(d88Header.getHeader(), baos);
                ostream.write(baos.toByteArray(), 0,
                              d88Header.getHeaderSize());
            } catch (IOException e) {
                p_result.setError(DiskResult.ERR_NO_DATA);
            }
        }

        if (p_result.getValid() >= 0) disk.clearModify();
        return p_result.getValid();
    }
}
