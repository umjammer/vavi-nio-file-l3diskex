/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.diskimg.parser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.System.Logger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;

import l3diskex.Utils;
import l3diskex.diskimg.DiskImage.DiskImageDisk;
import l3diskex.diskimg.DiskImage.DiskImageFile;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;
import l3diskex.diskimg.DiskParam;
import l3diskex.diskimg.DiskParser.DiskImageParser;
import l3diskex.diskimg.DiskResult;
import l3diskex.diskimg.FileParam.DiskTypeHint;
import vavi.io.SeekableDataInputStream;

import static java.lang.System.getLogger;


/**
 * DSK STR disk parser for X68000/PC9801
 */
public class DiskSTRParser extends DiskImageParser {

    private static final Logger logger = getLogger(DiskSTRParser.class.getName());

    /**
     * DSKSTR secondary compression expanded buffer
     */
    public static class Expand2FIFOBuffer extends Utils.FIFOBuffer {

        private long lastPos;
        private final long[] iStreamPos = new long[9];
        private final long[] eStreamPos = new long[9];

        public Expand2FIFOBuffer() {
            super();
            clear();
        }

        @Override
        public void clear() {
            super.clear();
            lastPos = 0;
            Arrays.fill(iStreamPos, 0);
            Arrays.fill(eStreamPos, 0);
        }

        public void setLastPos(long val) {
            lastPos = val;
        }

        public long getLastPos() {
            return lastPos;
        }

        public void setIStreamPos(int idx, long val) {
            iStreamPos[idx] = val;
        }

        public void setEStreamPos(int idx, long val) {
            eStreamPos[idx] = val;
        }

        public long getIStreamPos(int idx) {
            return iStreamPos[idx];
        }

        public long getEStreamPos(int idx) {
            return eStreamPos[idx];
        }
    }

    private int compressType;
    /** Compression format of input data 0: uncompressed bit0: primary compression bit1: secondary compression */
    private Expand2FIFOBuffer eStream = new Expand2FIFOBuffer();

    /** DSKSTR header */
    private static class StrHeader {

        int dataSize; // BE
        byte[] reserved = new byte[28];

        public static final int SIZE = 4 + 28;

        public void read(InputStream is) throws IOException {
            byte[] buf = new byte[SIZE];
            if (is.read(buf) != SIZE) {
                throw new IOException("Failed to read DSKSTR header");
            }
            ByteBuffer bb = ByteBuffer.wrap(buf).order(ByteOrder.BIG_ENDIAN);
            dataSize = bb.getInt();
            bb.get(reserved);
        }
    }

    /** DSKSTR track header */
    private static class StrTrackHeader {

        byte attr;
        byte secs;
        short off1; // BE
        short off2;
        short offD;
        short dat1;
        short dat2;
        byte[] reserved = new byte[4];

        public static final int SIZE = 1 + 1 + 2 + 2 + 2 + 2 + 2 + 4;

        // TODO Serdes
        public void read(InputStream is) throws IOException {
            byte[] buf = new byte[SIZE];
            if (is.read(buf) != SIZE) {
                throw new IOException("Failed to read DSKSTR track header");
            }
            ByteBuffer bb = ByteBuffer.wrap(buf).order(ByteOrder.BIG_ENDIAN);
            attr = bb.get();
            secs = bb.get();
            off1 = bb.getShort();
            off2 = bb.getShort();
            offD = bb.getShort();
            dat1 = bb.getShort();
            dat2 = bb.getShort();
            bb.get(reserved);
        }

        public short getOff1LE() {
            return Short.reverseBytes(off1);
        }

        public short getOff2LE() {
            return Short.reverseBytes(off2);
        }

        public short getOffdLE() {
            return Short.reverseBytes(offD);
        }
    }

    /** Sector ID */
    private static class StrSectorId {

        byte c;
        byte h;
        byte r;
        byte n;

        public static final int SIZE = 4;

        // TODO Serdes
        public void read(InputStream is) throws IOException {
            byte[] buf = new byte[SIZE];
            if (is.read(buf) != SIZE) {
                throw new IOException("Failed to read DSKSTR sector ID");
            }
            ByteBuffer bb = ByteBuffer.wrap(buf);
            c = bb.get();
            h = bb.get();
            r = bb.get();
            n = bb.get();
        }
    }

    @Override
    public boolean isSupported(String type) {
        return "dskstr".equalsIgnoreCase(type);
    }

    @Override
    public void init(DiskImageFile file, short modFlags, DiskResult result) {
        super.init(file, modFlags, result);
        compressType = 0;
    }

    /**
     * Create sector data
     *
     * @param iStream       Disk image
     * @param diskNumber    Disk number
     * @param trackNumber   Track number
     * @param sideNumber    Side number
     * @param numOfSectors  Number of sectors
     * @param sectorNumber  Sector number
     * @param sectorSize    Sector size
     * @param singleDensity Whether single density
     * @param track         Track
     * @return Sector size including header
     */
    private static int parseSector(InputStream iStream, int diskNumber, int trackNumber, int sideNumber, int numOfSectors,
                                   int sectorNumber, int sectorSize, boolean singleDensity, DiskImageTrack track) throws IOException {
        // Sector creation
        DiskImageSector sector = track.newImageSector(trackNumber, sideNumber, sectorNumber, sectorSize, numOfSectors, false, 0);
        track.add(sector);

        byte[] buffer = sector.getSectorBuffer();

        // plain data
        iStream.readNBytes(buffer, 0, sectorSize);

        sector.setSingleDensity(singleDensity);
        sector.clearModify();

        // Return size of this sector data
        return sector.getSize();
    }

    /**
     * Create track data
     *
     * TODO check seekable
     *
     * @param iStream    Disk image
     * @param diskNumber Disk number
     * @param offsetPos  Offset number
     * @param offset     Offset position
     * @param disk       Disk
     * @return -1: Error or end, >0: Track size
     */
    private int parseTrack(InputStream iStream, int diskNumber, int offsetPos, int offset, DiskImageDisk disk) throws IOException {
        StrTrackHeader trackHeader = new DiskSTRParser.StrTrackHeader();

        // Expand compressed data
        ByteArrayOutputStream oestream = new ByteArrayOutputStream();
        int oeLimit = StrTrackHeader.SIZE;
        int rc = expandFirst(iStream, oestream, oeLimit);

        // Check header
        byte[] trackHeaderBytes = oestream.toByteArray();
        ByteArrayInputStream ieStreamHeader = new ByteArrayInputStream(trackHeaderBytes);
        trackHeader.read(ieStreamHeader);
        int len = trackHeaderBytes.length;

        if (rc < 0 || len < StrTrackHeader.SIZE || trackHeader.attr == 0) {
            // end of file
            return -1;
        }

        int sectorsPerTrack = trackHeader.secs & 0xFF;
        if (sectorsPerTrack <= 0) {
            result.setError(DiskResult.ERRV_DISK_HEADER, diskNumber);
            return -1;
        }

        // Data start position
        oeLimit = trackHeader.getOffdLE() & 0xffff;
        // Continue expanding compressed data
        rc = expandNext(iStream, oestream, oeLimit);

        byte[] attr = new byte[256];
        Arrays.fill(attr, (byte) 0);

        DiskSTRParser.StrSectorId[] id = new StrSectorId[256];
        for (int i = 0; i < id.length; i++) id[i] = new StrSectorId();

        trackHeaderBytes = oestream.toByteArray();
        ByteArrayInputStream ieStream = new ByteArrayInputStream(trackHeaderBytes);

        // Get sector attribute
        ieStream.skipNBytes(trackHeader.getOff1LE() & 0xffff);

        for (int sec = 0; sec < sectorsPerTrack; sec += 4) {
            // 4-byte boundary
            int readLen = ieStream.read(attr, sec, 4);
            if (readLen < 4) {
                result.setError(DiskResult.ERRV_DISK_HEADER, diskNumber);
                return -1;
            }
        }

        // Get sector ID
        ieStream.skipNBytes(trackHeader.getOff2LE() & 0xffff);

        for (int sec = 0; sec < sectorsPerTrack; sec++) {
            // C H R N
            try {
                id[sec].read(ieStream);
                len = StrSectorId.SIZE;
            } catch (IOException e) {
                len = 0;
            }

            if (len < StrSectorId.SIZE) {
                result.setError(DiskResult.ERRV_DISK_HEADER, diskNumber);
                return -1;
            }

            int sectorSize = (128 << (id[sec].n & 0xff));
            if ((id[sec].n & 0xff) > 5) {
                result.setError(DiskResult.ERRV_SECTOR_SIZE_SECTOR, diskNumber, id[sec].c & 0xff, id[sec].h & 0xFF, id[sec].r & 0xff, id[sec].n & 0xff, sectorSize);
                return -1;
            }
            oeLimit += sectorSize;
        }

        // Continue expanding compressed data
        rc = expandNext(iStream, oestream, oeLimit);
        trackHeaderBytes = oestream.toByteArray();

        DiskImageTrack track;
        int trackSize = 0;
        ieStream = new ByteArrayInputStream(trackHeaderBytes);
        ieStream.skipNBytes(trackHeader.getOffdLE() & 0xffff);

        // Create track
        track = disk.newImageTrack(id[0].c & 0xff, id[0].h & 0xff, offsetPos, 1);
        disk.setMaxTrackNumber(id[0].c & 0xff);

        for (int pos = 0; pos < sectorsPerTrack && result.getValid() >= 0; pos++) {
            int sectorSize = (128 << (id[pos].n & 0xff));
            trackSize += parseSector(ieStream, diskNumber, id[pos].c & 0xff, id[pos].h & 0xff,
                    sectorsPerTrack, id[pos].r & 0xff, sectorSize, (attr[pos] & 0x40) == 0, track);
        }

        // Adjust position of input data
        adjustIStream(iStream);

        if (result.getValid() >= 0) {
            // Calculate interleave
            track.calcInterleave();
        }

        if (result.getValid() >= 0) {
            // Set track size
            track.setSize(trackSize);
            // Side number matches ID H of each sector
            track.setSideNumber(track.getMajorIDH());

            // Add to disk
            disk.add(track);
            // Set offset
            disk.setOffset(offsetPos, offset);
        }

        return trackSize;
    }

    /**
     * Analyze file
     *
     * @param iStream    Data to be analyzed
     * @param diskNumber Disk number
     * @return -1: finish parsing, 0: parse next disk
     */
    private int parseDisk(InputStream iStream, int diskNumber) throws IOException {
        // skip header
        if (parseHeader(iStream, diskNumber) < 0) {
            return -1;
        }

        // Create disk
        DiskImageDisk disk = file.newImageDisk(diskNumber);

        // Track analysis
        int d88Offset = disk.getOffsetStart(); // header size
        int d88OffsetPos = 0;
        for (int pos = 0; pos < 204; pos++) {
            int offset = parseTrack(iStream, diskNumber, d88OffsetPos, d88Offset, disk);
            if (offset == -1) {
                break;
            }
            d88Offset += offset;

            d88OffsetPos++;
            if (d88OffsetPos >= disk.getCreatableTracks()) {
                result.setError(DiskResult.ERRV_OVERFLOW_SIZE, diskNumber, d88Offset);
            }
        }
        disk.setSize(d88Offset);

        if (result.getValid() >= 0) {
            // Add disk
            DiskParam diskParam = disk.calcMajorNumber();
            if (diskParam != null) {
                disk.setDensity(diskParam.getParamDensity());
            }
            file.add(disk, modFlags);
        }

        return 0;
    }

    /**
     * Header analysis
     *
     * @param iStream    Data to be analyzed
     * @param diskNumber Disk number
     * @return -1: Error, 0:
     */
    private int parseHeader(InputStream iStream, int diskNumber) throws IOException {
        byte[] buf = new byte[16];
        int len = 1;

        // Skip until 0x1a
        do {
            if (iStream.read(buf, 0, buf.length) != buf.length) {
                // too short
                return -1;
            }
            len = buf.length;
            for (int i = 0; i < len; i++) {
                if (buf[i] == 0x1a) {
                    len = 0;
                    break;
                }
            }
        } while (len > 0);

        if (iStream.read(buf, 0, buf.length) != buf.length) {
            // too short
            return -1;
        }
        if (new String(buf, 0, 10).compareTo("DSKSTR ver") != 0) {
            // not a image
            return -1;
        }

        DiskSTRParser.StrHeader header = new DiskSTRParser.StrHeader();
        try {
            header.read(iStream);
            len = StrHeader.SIZE;
        } catch (IOException e) {
            len = 0;
        }

        if (len < StrHeader.SIZE) {
            // too short
            return -1;
        }

        return 0;
    }

    /**
     * Correct position of input stream
     *
     * @param iStream Original data
     */
    private void adjustIStream(InputStream iStream) throws IOException {
        if ((compressType & 2) != 0) {
            // In the case of secondary compression, the input data may have been read too much, so correct the position
            int match = -1;
            long readPos = eStream.getReadPos();
            for (int i = 0; i < 8; i++) {
                if (eStream.getEStreamPos(i) < readPos && readPos <= eStream.getEStreamPos(i + 1)) {
                    match = i;
                    break;
                }
            }
            if (match >= 0) {
                long targetPos = eStream.getIStreamPos(match + 1);

                ((SeekableDataInputStream) iStream).position(targetPos);
            }
        }
    }

    /**
     * Determine and expand compressed data
     *
     * @param iStream Original data
     * @param oStream Data after expansion
     * @param oLimit  Output buffer size
     * @return -1: no data
     */
    private int expandFirst(InputStream iStream, OutputStream oStream, int oLimit) throws IOException {
        // No data?
        if (iStream.available() == 0) {
            return -1;
        }

        // First data
        compressType = 0;
        int pos = (int) ((SeekableDataInputStream) iStream).position();
        int ch = iStream.read();
        ((SeekableDataInputStream) iStream).position(pos);

        if (ch == 0x08 || ch == 0x0c) {
            compressType = 1;
        } else if (ch == 0xff) { // Using 0xff as a sentinel for 2nd compression in the original code, though -1 might be used for EOF
            compressType = 2;
        }

        eStream.clear();
        if ((compressType & 2) != 0) {
            // Expand secondary compressed data
            expand2(iStream, oStream, oLimit, true);
        } else if ((compressType & 1) != 0) {
            // Expand primary compressed data
            expand1(iStream, oStream, oLimit);
        } else {
            // Uncompressed data
            expand0(iStream, oStream, oLimit);
        }
        return 0;
    }

    /**
     * Continue expanding compressed data
     *
     * @param iStream Original data
     * @param oStream Data after expansion
     * @param oLimit  Output buffer size
     */
    private int expandNext(InputStream iStream, OutputStream oStream, int oLimit) throws IOException {
        if ((compressType & 2) != 0) {
            // Expand secondary compressed data
            expand2(iStream, oStream, oLimit, false);
        } else if ((compressType & 1) != 0) {
            // Regard as primary compressed data
            expand1(iStream, oStream, oLimit);
        } else {
            // Uncompressed data
            expand0(iStream, oStream, oLimit);
        }
        return 0;
    }

    /**
     * Expand secondary compressed data
     *
     * @param iStream Original data
     * @param oStream Data after expansion
     * @param oLimit  Output buffer size
     * @param first   Whether it's the first time
     */
    private void expand2(InputStream iStream, OutputStream oStream, int oLimit, boolean first) throws IOException {
        boolean cont;
        do {
            expand2Element(iStream);
            if (first) {
                // Whether primary compression is used
                int ch = eStream.peekByte();
                if (ch == 0x08 || ch == 0x0c) {
                    compressType |= 1;
                }
                first = false;
            }
            if ((compressType & 1) != 0) {
                // Expand primary compressed data
                cont = expand1Element(oStream, oLimit);
            } else {
                // Uncompressed data
                cont = expand0Element(oStream, oLimit);
            }
        } while (cont);
    }

    /**
     * Expand secondary compressed data
     *
     * @param iStream Original data
     * Use eStream as input stream
     */
    private void expand2Element(InputStream iStream) throws IOException {
        byte[] iBuf = new byte[16];
        int iBufLen;
        byte[] buf = new byte[256];

        int ch = iStream.read();
        if (ch == -1) return; // EOF

        int ipos = (int) ((SeekableDataInputStream) iStream).position();
        eStream.setIStreamPos(0, ipos);
        eStream.setEStreamPos(0, eStream.getWritePos());

        int cmd = (ch & 0xff);

        iBufLen = 0;
        int tempCmd = cmd;
        for (int i = 0; i < 8; i++) {
            if ((tempCmd & 1) != 0) {
                iBufLen++;
                eStream.setLastPos(ipos + iBufLen);
            } else {
                iBufLen += 2;
            }
            eStream.setIStreamPos(i + 1, ipos + iBufLen);
            tempCmd >>= 1;
        }

        Arrays.fill(iBuf, (byte) 0);
        iStream.readNBytes(iBuf, 0, iBufLen);

        tempCmd = cmd;
        iBufLen = 0;
        for (int i = 0; i < 8; i++) {
            if ((tempCmd & 1) != 0) {
                // Output as is
                eStream.appendByte(iBuf[iBufLen++]);
                eStream.setEStreamPos(i + 1, eStream.getWritePos());
            } else {
                int d1 = iBuf[iBufLen++] & 0xFF;
                int d2 = iBuf[iBufLen++] & 0xFF;

                int len = (d2 & 0xf) + 3;
                int index = ((d2 & 0xf0) << 4) | d1;
                index += 18;

                // Calculate data position to be the source of copy
                int eLen = eStream.getWritePos();
                if (eLen >= 0x2000) {
                    index += (eLen & ~0xfff) - 0x1000;
                } else if (eLen < index) {
                    index -= 0x1000;
                }

                // index: Absolute position of expanded data
                if (index >= 0) {
                    // Position correction
                    if (!(eLen <= index + 0x1000 && index < eLen)) {
                        index += 0x1000;
                    }

                    // Get original data
                    byte[] data = eStream.getData();

                    int bLen = Math.min(len, eLen - index);
                    System.arraycopy(data, index, buf, 0, bLen);

                    int bpos = bLen;
                    while (bpos < len) {
                        // Make up data
                        int copyLen = Math.min(len - bpos, bLen);
                        System.arraycopy(buf, 0, buf, bpos, copyLen);
                        bpos += copyLen;
                    }
                } else {
                    // If negative, calculate at a virtual position
                    int nLen = -index;
                    if (nLen > len) nLen = len;
                    if (nLen > 0) {
                        Arrays.fill(buf, 0, nLen, (byte) 0);
                    }
                    int plen = (len + index);
                    if (plen > 0) {
                        byte[] data = eStream.getData();
                        System.arraycopy(data, 0, buf, nLen, plen);
                    }
                }

                // Append to expanded data
                eStream.appendData(buf, len);
                eStream.setEStreamPos(i + 1, eStream.getWritePos());
            }
            tempCmd >>= 1;
        }
    }

    /**
     * Expand primary compressed data
     *
     * @param iStream Original data
     * @param oStream Data after expansion
     * @param oLimit  Output buffer size
     */
    private void expand1(InputStream iStream, OutputStream oStream, int oLimit) throws IOException {
        byte[] buf = new byte[16];

        boolean continuable = (eStream.remain() == 0);
        do {
            if (continuable) {
                int len = iStream.read(buf, 0, buf.length);
                if (len > 0) {
                    eStream.appendData(buf, len);
                }
            }
            continuable = expand1Element(oStream, oLimit);
        } while (continuable);

        if (eStream.remain() > 0) {
            int pos = (int) ((SeekableDataInputStream) iStream).position();
            ((SeekableDataInputStream) iStream).position(pos - eStream.remain());
            eStream.setWritePos(eStream.getReadPos());
        }
    }

    /**
     * Expand primary compressed data
     *
     * Use eStream as input stream
     *
     * @param oStream Data after expansion
     * @param oLimit  Output buffer size
     * @return false if output data size reaches oLimit
     */
    private boolean expand1Element(OutputStream oStream, int oLimit) throws IOException {
        int size;
        int oSize = 0;
        if (oStream instanceof ByteArrayOutputStream) {
            oSize = ((ByteArrayOutputStream) oStream).size();
        }
        byte[] buf = new byte[128];

        do {
            // Check first character
            int ch = eStream.peekByte();
            if (ch == -1) {
                break;
            }
            size = (ch & 0xff);
            if (ch < 0x80) {
                if (size == 0) size = 0x80;
            } else {
                size = 1;
            }
            if (eStream.remain() < (size + 1)) {
                break;
            }

            // Expansion
            ch = eStream.getByte();
            size = (ch & 0xff);
            if (ch < 0x80) {
                if (size == 0) size = 0x80;
                size = eStream.getData(buf, size);
                oStream.write(buf, 0, size);
                oSize += size;
            } else {
                size = (ch & 0x7f);
                ch = eStream.getByte();
                if (size == 0) size = 0x80;
                Arrays.fill(buf, 0, size, (byte) ch);
                oStream.write(buf, 0, size);
                oSize += size;
            }
        } while (oSize < oLimit);

        return oSize < oLimit;
    }

    /**
     * Expand uncompressed data as is
     *
     * @param iStream Original data
     * @param oStream Data after expansion
     * @param oLimit  Output buffer size
     */
    private static void expand0(InputStream iStream, OutputStream oStream, int oLimit) throws IOException {
        int size;
        int osize = 0;
        if (oStream instanceof ByteArrayOutputStream) {
            osize = ((ByteArrayOutputStream) oStream).size();
        }
        byte[] buf = new byte[128];

        while (osize < oLimit) {
            size = Math.min(buf.length, oLimit - osize);

            size = iStream.read(buf, 0, size);
            if (size <= 0) {
                break;
            }
            oStream.write(buf, 0, size);
            osize += size;
        }
    }

    /**
     * Expand uncompressed data as is
     *
     * Use eStream as input stream
     *
     * @param oStream Data after expansion
     * @param oLimit  Output buffer size
     * @return false if output data size reaches oLimit
     */
    private boolean expand0Element(OutputStream oStream, int oLimit) throws IOException {
        int siz;
        int osize = 0;
        if (oStream instanceof ByteArrayOutputStream) {
            osize = ((ByteArrayOutputStream) oStream).size();
        }
        byte[] buf = new byte[128];

        while (osize < oLimit) {
            siz = Math.min(buf.length, oLimit - osize);

            siz = eStream.getData(buf, siz);
            if (siz == 0) {
                break;
            }
            oStream.write(buf, 0, siz);
            osize += siz;
        }
        return (osize < oLimit);
    }

    @Override
    public int check(InputStream iStream, List<DiskTypeHint> diskHints, DiskParam diskParam, List<DiskParam> diskParams, DiskParam manualParam) throws IOException {
        return check(iStream);
    }

    /**
     * Check
     *
     * @param iStream Data to be analyzed
     * @return 1: Display selection dialog, 0: Normal (display selection dialog when there are multiple candidates)
     */
    @Override
    public int check(InputStream iStream) throws IOException {
        ((SeekableDataInputStream) iStream).position(0);

        if (parseHeader(iStream, 0) < 0) {
            return -1;
        }

        return 0;
    }

    /**
     * Analyze file
     *
     * @param iStream   Data to be analyzed
     * @param diskParam Parameters usually unnecessary
     * @return 0: normal, -1: error, 1: warning
     */
    @Override
    public int parse(InputStream iStream, DiskParam diskParam) throws IOException {
        ((SeekableDataInputStream) iStream).position(0);

        for (int diskNumber = 0; diskNumber < 1; diskNumber++) {
            if (parseDisk(iStream, diskNumber) < 0) {
                break;
            }
        }
        return result.getValid();
    }
}
