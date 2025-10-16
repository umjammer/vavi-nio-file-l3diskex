/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.diskimg;


import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import l3diskex.diskimg.DiskImage.DiskImageFile;
import l3diskex.diskimg.DiskParam.DiskParticular;
import l3diskex.diskimg.DiskParam.DiskParticulars;
import l3diskex.diskimg.FileParam.DiskTypeHint;

import static l3diskex.diskimg.DiskParam.gDiskTemplates;


// --
//  DiskADCParser (public class)
// --
public class DiskADCParser extends DiskPlainParser {

    /* -----------------------------------------------------------------
     *  Constants & helpers
     * -----------------------------------------------------------------*/
    private static final int HEADER_SIZE = 84;          // 84 bytes
    private static final int LABEL_MAX = 63;            // maximum label length

    /* -----------------------------------------------------------------
     *  The struct st_adc_header (84 bytes)
     * -----------------------------------------------------------------*/
    private static class AdcHeader {
        int  labelLength;      // 1 byte
        byte[] label = new byte[LABEL_MAX];   // 63 bytes
        int  dataSize;         // 4 bytes, big‑endian
        int  resourceSize;     // 4 bytes, big‑endian
        int  createDate;       // 4 bytes
        int  modifyDate;       // 4 bytes
        byte[] unknown = new byte[4];   // 4 bytes

        /* read header from a byte buffer */
        static AdcHeader readFrom(byte[] buf, int pos) {
            AdcHeader h = new AdcHeader();
            h.labelLength = buf[pos] & 0xFF;
            System.arraycopy(buf, pos + 1, h.label, 0, LABEL_MAX);
            // dataSize & resourceSize are stored big‑endian in the file
            h.dataSize =   ((buf[pos + 64] & 0xFF) << 24) |
                           ((buf[pos + 65] & 0xFF) << 16) |
                           ((buf[pos + 66] & 0xFF) << 8)  |
                           (buf[pos + 67] & 0xFF);
            h.resourceSize = ((buf[pos + 68] & 0xFF) << 24) |
                             ((buf[pos + 69] & 0xFF) << 16) |
                             ((buf[pos + 70] & 0xFF) << 8)  |
                             (buf[pos + 71] & 0xFF);
            h.createDate = ((buf[pos + 72] & 0xFF) << 24) |
                           ((buf[pos + 73] & 0xFF) << 16) |
                           ((buf[pos + 74] & 0xFF) << 8)  |
                           (buf[pos + 75] & 0xFF);
            h.modifyDate = ((buf[pos + 76] & 0xFF) << 24) |
                           ((buf[pos + 77] & 0xFF) << 16) |
                           ((buf[pos + 78] & 0xFF) << 8)  |
                           (buf[pos + 79] & 0xFF);
            System.arraycopy(buf, pos + 80, h.unknown, 0, 4);
            return h;
        }
    }

    /* -----------------------------------------------------------------
     *  Constructor / Destructor
     * -----------------------------------------------------------------*/
    public DiskADCParser(DiskImageFile file, short modFlags, DiskResult result) {
        super(file, modFlags, result);
    }

    /** Destructor – nothing to do in Java, but provided for API parity */
    public void destroy() {
        // No resource to free in the stub
    }

    /* -----------------------------------------------------------------
     *  Parse method (file → stream → parse)
     * -----------------------------------------------------------------*/
    @Override
    public int parse(InputStream istream, DiskParam diskParam) {
        if (diskParam == null) {
            result.setError(DiskResult.ERRV_INVALID_DISK, 0);
            return result.getValid();
        }

        try {
            /* read the header first */
            istream.mark(HEADER_SIZE);                 // allow resetting
            byte[] buf = new byte[HEADER_SIZE];
            int len = istream.read(buf, 0, HEADER_SIZE);
            if (len < HEADER_SIZE) {
                // file too short to contain a valid header
                result.setError(DiskResult.ERRV_DISK_TOO_SMALL, 0);
                return result.getValid();
            }

            /* rewind the stream so that the plain parser sees the whole file */
            istream.reset();

            /* delegate to the plain parser */
            return super.parse(istream, diskParam);
        } catch (IOException e) {
            result.setError(DiskResult.ERRV_INVALID_DISK, 0);
            return result.getValid();
        }
    }

    /* -----------------------------------------------------------------
     *  Check method – verifies that the file is a valid ADC image
     * -----------------------------------------------------------------*/
    @Override
    public int check(InputStream istream,
                     List<DiskTypeHint> diskHints,
                     DiskParam      diskParam,
                     List<DiskParam> diskParams,
                     DiskParam      manualParam) {
        try {
            /* read header */
            istream.mark(HEADER_SIZE);
            byte[] buf = new byte[HEADER_SIZE];
            int len = istream.read(buf, 0, HEADER_SIZE);
            if (len < HEADER_SIZE) {
                result.setError(DiskResult.ERRV_DISK_TOO_SMALL, 0);
                return result.getValid();
            }
            AdcHeader header = AdcHeader.readFrom(buf, 0);

            /* 1) check label length */
            if (header.labelLength > LABEL_MAX ||
                header.label[header.labelLength] != 0) {
                result.setError(DiskResult.ERRV_INVALID_DISK, 0);
                return result.getValid();
            }

            /* 2) compute expected file size */
            int dataSize = Integer.reverseBytes(header.dataSize);     // BE → native LE
            int resSize  = Integer.reverseBytes(header.resourceSize);
            int fileSize = HEADER_SIZE + dataSize + resSize;
            int streamLen = getStreamLength(istream);
            if (fileSize != streamLen) {
                result.setError(DiskResult.ERRV_INVALID_DISK, 0);
                return result.getValid();
            }

            /* 3) infer disk parameters from data size */
            int sidesPerDisk       = 1;
            int tracksPerSide      = 1;
            int sectorsPerTrack    = 1;
            int sectorSize         = 256;
            DiskParticulars sd = new DiskParticulars();
            DiskParticulars pt = new DiskParticulars();

            if (dataSize <= 143360) {                           // 140K
                sidesPerDisk    = 1;
                tracksPerSide   = 35;
                sectorsPerTrack = 16;
                sectorSize      = 256;
            } else if (dataSize <= 819200) {                     // 800K
                sidesPerDisk    = 2;
                tracksPerSide   = 80;
                sectorsPerTrack = 12;
                sectorSize      = 512;
                for (int i = 16, n = sectorsPerTrack - 1; i < tracksPerSide; i += 16, n--) {
                    pt.add(new DiskParticular(i, -1, -1, 16, n, 512));
                }
            } else if (dataSize <= 1474560) {                    // 1440K
                sidesPerDisk    = 2;
                tracksPerSide   = 80;
                sectorsPerTrack = 18;
                sectorSize      = 512;
            }

            /* 4) look up a matching template */
            DiskParam param = gDiskTemplates.findStrict(
                    sidesPerDisk, tracksPerSide, sectorsPerTrack, sectorSize,
                    1, 0, 0, 0, 0,
                    sd, pt);
            if (param != null) {
                diskParams.add(param);
            }

            /* 5) no candidates → manual entry */
            if (diskParams.size() == 0) {
                manualParam.setDiskParam(
                        sidesPerDisk,
                        tracksPerSide,
                        sectorsPerTrack,
                        sectorSize,
                        0,
                        1,
                        sd,
                        pt);
                return 1;            // dialog must be shown
            }
            return 0;                    // parsing OK
        } catch (IOException e) {
            result.setError(DiskResult.ERRV_INVALID_DISK, 0);
            return result.getValid();
        }
    }

    /* -----------------------------------------------------------------
     *  Helper – return the length of an InputStream
     * -----------------------------------------------------------------*/
    private int getStreamLength(InputStream stream) {
        if (stream instanceof FileInputStream) {
            try {
                return (int) ((FileInputStream) stream).getChannel().size();
            } catch (IOException e) {
                return -1;
            }
        }
        /* for other stream types we cannot determine length – stub */
        return -1;
    }
}
