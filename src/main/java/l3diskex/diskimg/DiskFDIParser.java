/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.diskimg;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import l3diskex.diskimg.DiskImage.DiskImageFile;
import l3diskex.diskimg.FileParam.DiskTypeHint;
import vavi.io.SeekableDataInputStream;


/* ----------------------------------------------------------------
 *  Main parser implementation (FDI format).
 * ----------------------------------------------------------------*/
public class DiskFDIParser extends DiskPlainParser {

    /* ----------------------------------------------------------------
     *  The FDI parser header structure – packed 1‑byte alignment.
     * ----------------------------------------------------------------*/
    static class FdiDskHeader {

        byte[] unknown = new byte[0x10];
        int sectorSize;
        int sectorsPerTrack;
        int sidesPerDisk;
        int tracksPerSide;
        byte[] reserved = new byte[0xfe0];
        byte data = 0;            // placeholder for the first data byte

        /* Read the header from a DataInputStream (big‑endian). */
        public static FdiDskHeader readFrom(DataInputStream dis) throws IOException {
            FdiDskHeader hdr = new FdiDskHeader();
            dis.readFully(hdr.unknown);
            hdr.sectorSize = dis.readInt();
            hdr.sectorsPerTrack = dis.readInt();
            hdr.sidesPerDisk = dis.readInt();
            hdr.tracksPerSide = dis.readInt();
            dis.readFully(hdr.reserved);
            hdr.data = dis.readByte();      // read the first data byte
            return hdr;
        }

        public int getSectorSize() {
            return sectorSize;
        }

        public int getSectorsPerTrack() {
            return sectorsPerTrack;
        }

        public int getSidesPerDisk() {
            return sidesPerDisk;
        }

        public int getTracksPerSide() {
            return tracksPerSide;
        }
    }

    /* ----------------------------------------------------------------
     *  Constructor / destructor
     * ----------------------------------------------------------------*/
    public DiskFDIParser(DiskImageFile file, short modFlags, DiskResult result) {
        super(file, modFlags, result);
    }

    /* ----------------------------------------------------------------
     *  Check
     *  --------------------------------------------------------------*/
    @Override
    public int check(InputStream istream,
                     List<DiskTypeHint> diskHints,
                     DiskParam diskParam,
                     List<DiskParam> diskParams,
                     DiskParam manualParam) throws IOException {

        if (istream == null) return 0;

        /* Seek to beginning */
        if (istream instanceof SeekableDataInputStream) {
            ((SeekableDataInputStream) istream).position(0);
        } else {
            return -1;
        }

        /* Read header */
        DataInputStream dis = new DataInputStream(istream);
        FdiDskHeader header;
        try {
            header = FdiDskHeader.readFrom(dis);
        } catch (IOException e) {
            result.setError(DiskResult.ERRV_DISK_TOO_SMALL, 0);
            return result.getValid();
        }

        /* Validate header fields */
        int sectorSize = header.getSectorSize();
        int sectorsPerTrack = header.getSectorsPerTrack();
        int sidesPerDisk = header.getSidesPerDisk();
        int tracksPerSide = header.getTracksPerSide();

        if (sectorSize <= 0 || sectorSize > 4096) {
            result.setError(DiskResult.ERRV_SECTOR_SIZE_HEADER, 0, sectorSize);
            return result.getValid();
        }
        if (sectorsPerTrack <= 0) {
            result.setError(DiskResult.ERRV_SECTORS_HEADER, 0, sectorsPerTrack);
            return result.getValid();
        }
        if (sidesPerDisk <= 0) {
            result.setError(DiskResult.ERRV_SIDES_HEADER, 0, sidesPerDisk);
            return result.getValid();
        }
        if (tracksPerSide <= 0 || tracksPerSide > 1024) {    // arbitrary upper limit
            result.setError(DiskResult.ERRV_TRACKS_HEADER, 0, tracksPerSide);
            return result.getValid();
        }

        /* Dummy template lookup – always fails in this stub. */
        DiskParam template = null;     // gDiskTemplates.FindStrict(...)

        if (template != null) diskParams.add(template);

        /* In the original code the template would be used to fill
           the DiskParam instance.  Here we simply return 0 if the
           template is unknown. */
        if (template == null) {
            /* No template – ask the caller to fill in the params. */
            manualParam.setDiskParam(sidesPerDisk,
                    tracksPerSide,
                    sectorsPerTrack,
                    sectorSize,
                    0,
                    0,
                    diskParam.getSingles(),
                    diskParam.getParticularTracks());
            return 1;                            // 1 → manual parameter set
        }

        /* Normal parsing continues – return 0 for normal flow. */
        return 0;
    }

    /* ----------------------------------------------------------------
     *  Parse
     *  --------------------------------------------------------------*/
    @Override
    public int parse(InputStream istream, DiskParam diskParam) throws IOException {

        if (diskParam == null) {
            result.setError(DiskResult.ERRV_INVALID_DISK, 0);
            return result.getValid();
        }

        /* Seek to beginning (or skip to start). */
        if (istream instanceof SeekableDataInputStream) {
            ((SeekableDataInputStream) istream).position(0);
        } else {
            return -1;
        }

        /* Read the header */
        DataInputStream dis = new DataInputStream(istream);
        FdiDskHeader header;
        try {
            header = FdiDskHeader.readFrom(dis);
        } catch (IOException e) {
            result.setError(DiskResult.ERRV_DISK_TOO_SMALL, 0);
            return result.getValid();
        }

        /* Skip the reserved block – the header occupies 4097 bytes,
           the actual image data starts at offset 0x1000 (4096). */
        if (istream instanceof SeekableDataInputStream) {
            ((SeekableDataInputStream) istream).position(0x1000);
        } else {
            istream.skip(0x1000);
        }

        /* Delegate the actual data parsing to the base class. */
        return super.parse(istream, diskParam);
    }
}
