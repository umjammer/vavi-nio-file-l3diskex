///
/// Copyright (c) Sasaji. All rights reserved.
///

package l3diskex.diskimg.parser;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import l3diskex.diskimg.DiskImage.DiskImageDisk;
import l3diskex.diskimg.DiskImage.DiskImageFile;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;
import l3diskex.diskimg.DiskParam;
import l3diskex.diskimg.DiskParser.DiskImageParser;
import l3diskex.diskimg.DiskResult;
import l3diskex.diskimg.FileParam.DiskTypeHint;
import vavi.io.SeekableDataInputStream;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;


/**
 * HxC HFE disk parser
 *
 * @see "https://hxc2001.com/floppy_drive_emulator/HFE-file-format.html"
 */
public class DiskHfeParser extends DiskImageParser {

    /**
     * Run-length limited(RLL) parser
     * <p>
     * Analyze one track
     */
    public static abstract class RunLengthLimitedParser {

        protected DiskImageDisk disk;
        protected DiskImageTrack track;
        protected int trackSize;
        protected byte[] data;
        protected int dataLen;
        protected int trackNumber;
        protected int sideNumber;
        protected int numOfSectors;
        protected int d88OffsetPos;
        protected DiskResult result;

        protected static class CurrentIDs {

            public byte c;
            public byte h;
            public byte r;
            public byte n;
            public short crc;
        }

        protected CurrentIDs currentIDs;

        /** Search for GAP */
        protected abstract boolean adjustGap();

        /** Get data */
        protected abstract boolean getData();

        /**
         * Set sector data
         *
         * @param inData  [in,out] Data to be analyzed
         * @param single  single sided?
         * @param deleted Deleted mark?
         * @return Sector size
         */
        protected int setSectorData(byte[] inData, boolean single, boolean deleted) {
            int trackNum = currentIDs.c & 0xff;
            int sideNum = currentIDs.h & 0xff;
            int sectorNum = currentIDs.r & 0xff;
            int sectorSizeCode = currentIDs.n & 0xff;

            if (sectorSizeCode > 7) {
                // Sector size is too large
                result.setError(DiskResult.ERRV_SECTOR_SIZE_SECTOR, 0, trackNum, sideNum, sectorNum, sectorSizeCode, sectorSizeCode);
                return 0;
            }

            int sectorSize = (128 << sectorSizeCode);

            numOfSectors++;
            DiskImageSector sector = track.newImageSector(trackNum, sideNum, sectorNum, sectorSize, 1, false, 0);
            track.add(sector);

            byte[] buf = sector.getSectorBuffer();
            int size = sector.getSectorBufferSize();
            int unit = getDecodeUnit();
            for (int i = 0; i < size; i++) {
                buf[i] = decodeData(Arrays.copyOfRange(inData, i * unit, i * unit + unit));
            }

            sector.setSingleDensity(single);
            sector.setDeletedMark(deleted);
            sector.clearModify();

            // Return size of this sector data
            return sector.getSize();
        }

        /** Decode data */
        protected abstract byte decodeData(byte[] inData);

        /** Run-length limited(RLL) parser */
        public RunLengthLimitedParser() {
            disk = null;
            track = null;
            trackSize = 0;
            data = null;
            dataLen = 0;
            trackNumber = 0;
            sideNumber = 0;
            numOfSectors = 0;
            d88OffsetPos = 0;
            result = null;
            currentIDs = new CurrentIDs();
        }

        /**
         * @param disk         [in,out] Disk
         * @param trackNumber  Track number
         * @param sideNumber   Side number
         * @param d88OffsetPos D88 offset number
         * @param data         [in,out] Data to be analyzed
         * @param dataLen      Data size
         * @param result       [in,out] Analysis error information
         */
        public RunLengthLimitedParser(DiskImageDisk disk, int trackNumber, int sideNumber, int d88OffsetPos, byte[] data, int dataLen, DiskResult result) {
            this.disk = disk;
            track = null;
            trackSize = 0;
            this.data = data;
            this.dataLen = dataLen;
            this.trackNumber = trackNumber;
            this.sideNumber = sideNumber;
            numOfSectors = 0;
            this.d88OffsetPos = d88OffsetPos;
            this.result = result;
            currentIDs = new CurrentIDs();
        }

        /**
         * Analyze data
         *
         * @return Track size in D88 format
         */
        public int parse() {
            track = disk.newImageTrack(trackNumber, sideNumber, d88OffsetPos, 1);
            trackSize = 0;

            while (dataLen > 0) {
                if (!adjustGap()) {
                    break;
                }
                if (!getData()) {
                    break;
                }
            }

            return trackSize;
        }

        public abstract int getDecodeUnit();

        public DiskImageTrack getTrack() {
            return track;
        }

        public int getNumOfSectors() {
            return numOfSectors;
        }

        /**
         * Shift buffer
         *
         * @param data       [in,out] Data
         * @param len        Data length
         * @param shiftCount Number of shifts
         * @return Number of data after shifting
         */
        public static int shiftBytes(byte[] data, int len, int shiftCount) {
            if (shiftCount <= 0) return len;

            int endPos = len - shiftCount;

            for (int i = 0; i < endPos; i++) {
                data[i] = data[i + shiftCount];
            }
            data[endPos] = 0x00;

            len -= shiftCount;

            return len;
        }

        /**
         * Bit shift buffer
         *
         * @param data       [in,out] Data
         * @param len        Data length
         * @param shiftCount Number of shifts
         * @return Number of data after shifting
         */
        public static int shiftBits(byte[] data, int len, int shiftCount) {
            if (shiftCount <= 0) return len;

            int div = (shiftCount >> 3);
            int mod = (shiftCount & 7);

            // lshift bytes
            if (div > 0) {
                len = shiftBytes(data, len, div);
            }

            if (mod == 0) return len;

            // bit shift
            int carry = 0x00;
            for (int i = len - 1; i >= 0; i--) {
                int currentByte = data[i] & 0xff;
                int c = (currentByte << (8 - mod)) & 0xff;
                data[i] = (byte) ((currentByte >>> mod) | carry);
                carry = c;
            }

            return len;
        }
    }

    /**
     * IBM MFM parser
     * <p>
     * Analyze one track
     */
    public static class FormatMFMParser extends RunLengthLimitedParser {

        /**
         * Search for GAP (MFM)
         *
         * <pre>
         * GAP code
         * 4E ->  0 1 0 0  1 1 1 0 \n
         * clk   1 0 0 1  0 0 0 0  \n
         * 10010010 01010100 -> 9245 \n
         * rev   01001001 00101010 -> 492A \n
         *
         * SYNC code
         * 00 ->  0 0 0 0  0 0 0 0 \n
         * clk   1 1 1 1  1 1 1 1  \n
         * 10101010 10101010 \n
         * rev   01010101 01010101 -> 5555 \n
         * </pre>
         *
         * @return Whether GAP and SYNC field exists
         */
        @Override
        protected boolean adjustGap() {
            boolean found = false;
            int maxLen = dataLen;
            byte[] buf = new byte[6];
            int pos = 0;
            // search GAP field
            for (; pos < maxLen; pos++) {
                System.arraycopy(data, pos, buf, 0, 3);
                int count = 0;
                for (; count < 8; count++) {
                    if ((buf[0] & 0xff) == 0x49 && (buf[1] & 0xff) == 0x2a) {
                        found = true;
                        break;
                    }

                    // bit shift left
                    shiftBits(buf, 3, 1);
                }
                if (found) {
                    dataLen = shiftBits(data, dataLen, pos * 8 + count);
                    break;
                }
            }
            if (!found) {
                dataLen = 0;
                return found;
            }
            // search the terminate of SYNC field
            found = false;
            pos = 0;
            for (; pos < maxLen; pos++) {
                System.arraycopy(data, pos, buf, 0, 4);
                int count = 0;
                for (; count < 8; count++) {
                    boolean m1 = (buf[0] & 0xff) == 0x55 && (buf[1] & 0xff) == 0x55 && (buf[2] & 0xff) == 0x25;
                    boolean m2 = (buf[0] & 0xff) == 0x55 && (buf[1] & 0xff) == 0x55 && (buf[2] & 0xff) == 0xa5;
                    if (m1 || m2) {
                        found = true;
                        break;
                    }

                    // bit shift left
                    shiftBits(buf, 4, 1);
                }
                if (found) {
                    if (count >= 4) {
                        count -= 4;
                    } else {
                        pos--;
                        count += 4;
                    }
                    if (pos >= 0) {
                        dataLen = shiftBits(data, dataLen, pos * 8 + count);
                    }
                    break;
                }
            }
            if (!found) {
                dataLen = 0;
            }
            return found;
        }

        private static final byte[] cmpIdx = {(byte) 0x55, (byte) 0x55, (byte) 0x4a, (byte) 0x24, (byte) 0x4a, (byte) 0x24, (byte) 0x4a, (byte) 0x24, (byte) 0xaa, (byte) 0x4a};
        private static final byte[] cmpId = {(byte) 0x55, (byte) 0x55, (byte) 0x22, (byte) 0x91, (byte) 0x22, (byte) 0x91, (byte) 0x22, (byte) 0x91, (byte) 0xaa, (byte) 0x2a};
        private static final byte[] cmpData = {(byte) 0x55, (byte) 0x55, (byte) 0x22, (byte) 0x91, (byte) 0x22, (byte) 0x91, (byte) 0x22, (byte) 0x91, (byte) 0xaa, (byte) 0xa2};
        private static final byte[] cmpDelData = {(byte) 0x55, (byte) 0x55, (byte) 0x22, (byte) 0x91, (byte) 0x22, (byte) 0x91, (byte) 0x22, (byte) 0x91, (byte) 0xaa, (byte) 0x52};

        /**
         * Analyze data (MFM)
         * <pre>
         * PRE AM
         * A1 ->  1 0 1 0  0 0 0 1 \n
         * clk   0 0 0 0  1 x 1 0  \n
         * 01000100 10001001 \n
         * rev   00100010 10010001 -> 2291 \n
         *
         * PRE IDX
         * C2 ->  1 1 0 0  0 0 1 0 \n
         * clk   0 0 0 1  x 1 0 0  \n
         * 01010010 00100100 \n
         * rev   01001010 00100100 -> 4a24 \n
         *
         * INDEX mark
         * FC ->  1 1 1 1  1 1 0 0 \n
         * clk   0 0 0 0  0 0 0 1  \n
         * 01010101 01010010 \n
         * rev   10101010 01001010 -> aa4a \n
         *
         * ID mark
         * FE ->  1 1 1 1  1 1 1 0 \n
         * clk   0 0 0 0  0 0 0 0  \n
         * 01010101 01010100 \n
         * rev   10101010 00101010 -> aa2a \n
         *
         * DATA mark
         * FB ->  1 1 1 1  1 0 1 1 \n
         * clk   0 0 0 0  0 0 0 0  \n
         * 01010101 01000101 \n
         * rev   10101010 10100010 -> aaa2 \n
         *
         *
         * AttributeInfo.Deleted DATA mark
         * F8 ->  1 1 1 1  1 0 0 0 \n
         * clk   0 0 0 0  0 0 1 1  \n
         * 01010101 01001010 \n
         * rev   10101010 01010010 -> aa52 \n
         * </pre>
         *
         * @return Whether AM field exists
         */
        @Override
        protected boolean getData() {
            boolean found = false;
            int maxLen = dataLen;
            int pos = 0;
            for (; pos < maxLen && !found; pos++) {
                if (Arrays.equals(Arrays.copyOfRange(data, pos, pos + 10), cmpIdx)) {
                    // INDEX MARK
                    found = true;
                    dataLen = shiftBytes(data, dataLen, pos + 10);
                    break;
                } else if (Arrays.equals(Arrays.copyOfRange(data, pos, pos + 10), cmpId)) {
                    // ID MARK
                    found = true;
                    // Get C,H,R,N,CRC
                    currentIDs.c = decodeData(Arrays.copyOfRange(data, pos + 10, pos + 12));
                    currentIDs.h = decodeData(Arrays.copyOfRange(data, pos + 12, pos + 14));
                    currentIDs.r = decodeData(Arrays.copyOfRange(data, pos + 14, pos + 16));
                    currentIDs.n = decodeData(Arrays.copyOfRange(data, pos + 16, pos + 18));
                    int crc_h = decodeData(Arrays.copyOfRange(data, pos + 18, pos + 20)) & 0xFF;
                    int crc_l = decodeData(Arrays.copyOfRange(data, pos + 20, pos + 22)) & 0xFF;
                    currentIDs.crc = (short) (crc_h * 256 + crc_l);

                    dataLen = shiftBytes(data, dataLen, pos + 22);
                    break;
                } else if (Arrays.equals(Arrays.copyOfRange(data, pos, pos + 10), cmpData)) {
                    // DATA MARK
                    found = true;
                    // Get Data
                    int size = setSectorData(Arrays.copyOfRange(data, pos + 10, dataLen), false, false);
                    trackSize += size;

                    int unit = getDecodeUnit();
                    dataLen = shiftBytes(data, dataLen, pos + ((size + 2) * unit) + 10);
                    break;
                } else if (Arrays.equals(Arrays.copyOfRange(data, pos, pos + 10), cmpDelData)) {
                    // DELETED DATA MARK
                    found = true;
                    // Get Data
                    int size = setSectorData(Arrays.copyOfRange(data, pos + 10, dataLen), false, true);
                    trackSize += size;

                    int unit = getDecodeUnit();
                    dataLen = shiftBytes(data, dataLen, pos + ((size + 2) * unit) + 10);
                    break;
                }
            }
            if (!found) {
                dataLen = 0;
            }
            return found;
        }

        /**
         * Decode data (MFM)
         *
         * @param inData Data to be analyzed (2 bytes)
         * @return Decoded data
         */
        @Override
        protected byte decodeData(byte[] inData) {
            byte outData;
            outData = (byte) (((inData[0] & 0x80) >> 3) | ((inData[0] & 0x20)) | ((inData[0] & 0x08) << 3) | ((inData[0] & 0x02) << 6));
            outData |= (byte) (((inData[1] & 0x80) >> 7) | ((inData[1] & 0x20) >> 4) | ((inData[1] & 0x08) >> 1) | ((inData[1] & 0x02) << 2));

            return outData;
        }

        public FormatMFMParser(DiskImageDisk disk, int trackNumber, int sideNumber, int d88OffsetPos, byte[] data, int dataLen, DiskResult result) {
            super(disk, trackNumber, sideNumber, d88OffsetPos, data, dataLen, result);
        }

        @Override
        public int getDecodeUnit() {
            return 2;
        }
    }

    /**
     * IBM FM parser
     * <p>
     * Analyze one track
     */
    public static class FormatFMParser extends DiskHfeParser.RunLengthLimitedParser {

        /**
         * Search for GAP (FM)
         * <pre>
         * GAP code
         * FF ->   01  01   01  01   01  01   01  01 \n
         * clk   01  01   01  01   01  01   01  01   \n
         * 01010101 01010101 01010101 01010101 -> 55555555 \n
         * rev   10101010 10101010 10101010 10101010 -> AAAAAAAA \n
         *
         * SYNC code
         * 00 ->   00  00   00  00   00  00   00  00 \n
         * clk   01  01   01  01   01  01   01  01   \n
         * 01000100 01000100 01000100 01000100 \n
         * rev   00100010 00100010 00100010 00100010 -> 22222222 \n
         * </pre>
         *
         * @return Whether GAP and SYNC field exists
         */
        @Override
        protected boolean adjustGap() {
            boolean found = false;
            int maxLen = dataLen;
            byte[] buf = new byte[8];
            int pos = 0;
            // search GAP field
            for (; pos < maxLen; pos++) {
                System.arraycopy(data, pos, buf, 0, 5);
                int count = 0;
                for (; count < 8; count++) {
                    if ((buf[0] & 0xff) == 0xaa && (buf[1] & 0xff) == 0xaa && (buf[2] & 0xff) == 0xaa && (buf[3] & 0xff) == 0xaa) {
                        found = true;
                        break;
                    }

                    // bit shift left
                    shiftBits(buf, 5, 1);
                }
                if (found) {
                    dataLen = shiftBits(data, dataLen, pos * 8 + count);
                    break;
                }
            }
            if (!found) {
                dataLen = 0;
                return found;
            }
            // search the terminate of SYNC field
            found = false;
            pos = 0;
            for (; pos < maxLen; pos++) {
                System.arraycopy(data, pos, buf, 0, 6);
                int count = 0;
                for (; count < 8; count++) {
                    if ((buf[0] & 0xff) == 0x22 && (buf[1] & 0xff) == 0x22 && (buf[2] & 0xff) == 0x22 && (buf[3] & 0xff) == 0x22 && (buf[4] & 0xff) == 0xa2) {
                        found = true;
                        break;
                    }

                    // bit shift left
                    shiftBits(buf, 6, 1);
                }
                if (found) {
                    if (count >= 4) {
                        count -= 4;
                    } else {
                        pos--;
                        count += 4;
                    }
                    if (pos >= 0) {
                        dataLen = shiftBits(data, dataLen, pos * 8 + count);
                    }
                    break;
                }
            }
            if (!found) {
                dataLen = 0;
            }
            return found;
        }

        private static final byte[] cmpIdx = {(byte) 0x22, (byte) 0x22, (byte) 0x22, (byte) 0x22, (byte) 0xaa, (byte) 0xa8, (byte) 0xa8, (byte) 0x22};
        private static final byte[] cmpId = {(byte) 0x22, (byte) 0x22, (byte) 0x22, (byte) 0x22, (byte) 0xaa, (byte) 0x88, (byte) 0xa8, (byte) 0x2a};
        private static final byte[] cmpData = {(byte) 0x22, (byte) 0x22, (byte) 0x22, (byte) 0x22, (byte) 0xaa, (byte) 0x88, (byte) 0x28, (byte) 0xaa};
        private static final byte[] cmpDelData = {(byte) 0x22, (byte) 0x22, (byte) 0x22, (byte) 0x22, (byte) 0xaa, (byte) 0x88, (byte) 0x28, (byte) 0x22};

        /**
         * Analyze data (FM)
         * <pre>
         * INDEX mark
         * FC ->   01  01   01  01   01  01   00  00 \n
         * clk   01  01   0x  01   0x  01   01  01   \n
         * 01010101 00010101 00010101 01000100 \n
         * rev   10101010 10101000 10101000 00100010 -> aaa8a822 \n
         *
         * ID mark
         * FE ->   01  01   01  01   01  01   01  00 \n
         * clk   01  01   0x  0x   0x  01   01  01   \n
         * 01010101 00010001 00010101 01010100 \n
         * rev   10101010 10001000 10101000 00101010 -> aa88a82a \n
         *
         * DATA mark
         * FB ->   01  01   01  01   01  00   01  01 \n
         * clk   01  01   0x  0x   0x  01   01  01   \n
         * 01010101 00010001 00010100 01010101 \n
         * rev   10101010 10001000 00101000 10101010 -> aa8828aa \n
         *
         * Deleted DATA mark
         * F8 ->   01  01   01  01   01  00   00  00 \n
         * clk   01  01   0x  0x   0x  01   01  01   \n
         * 01010101 00010001 00010100 01000100 \n
         * rev   10101010 10001000 00101000 00100010 -> aa882822 \n
         * </pre>
         *
         * @return Whether AM field exists
         */
        @Override
        protected boolean getData() {
            boolean found = false;
            int maxLen = dataLen;
            int pos = 0;
            for (; pos < maxLen && !found; pos++) {
                if (Arrays.equals(Arrays.copyOfRange(data, pos, pos + 8), cmpIdx)) {
                    // INDEX MARK
                    found = true;
                    dataLen = shiftBytes(data, dataLen, pos + 8);
                    break;
                } else if (Arrays.equals(Arrays.copyOfRange(data, pos, pos + 8), cmpId)) {
                    // ID MARK
                    found = true;
                    // Get C,H,R,N,CRC
                    currentIDs.c = decodeData(Arrays.copyOfRange(data, pos + 8, pos + 12));
                    currentIDs.h = decodeData(Arrays.copyOfRange(data, pos + 12, pos + 16));
                    currentIDs.r = decodeData(Arrays.copyOfRange(data, pos + 16, pos + 20));
                    currentIDs.n = decodeData(Arrays.copyOfRange(data, pos + 20, pos + 24));
                    int crcH = decodeData(Arrays.copyOfRange(data, pos + 24, pos + 28)) & 0xFF;
                    int crcL = decodeData(Arrays.copyOfRange(data, pos + 28, pos + 32)) & 0xFF;
                    currentIDs.crc = (short) (crcH * 256 + crcL);

                    dataLen = shiftBytes(data, dataLen, pos + 32);
                    break;
                } else if (Arrays.equals(Arrays.copyOfRange(data, pos, pos + 8), cmpData)) {
                    // DATA MARK
                    found = true;
                    // Get Data
                    int size = setSectorData(Arrays.copyOfRange(data, pos + 8, dataLen), true, false);
                    trackSize += size;

                    int unit = getDecodeUnit();
                    dataLen = shiftBytes(data, dataLen, pos + ((size + 2) * unit) + 8);
                    break;
                } else if (Arrays.equals(Arrays.copyOfRange(data, pos, pos + 8), cmpDelData)) {
                    // DELETED DATA MARK
                    found = true;
                    // Get Data
                    int size = setSectorData(Arrays.copyOfRange(data, pos + 8, dataLen), true, true);
                    trackSize += size;

                    int unit = getDecodeUnit();
                    dataLen = shiftBytes(data, dataLen, pos + ((size + 2) * unit) + 8);
                    break;
                }
            }
            if (!found) {
                dataLen = 0;
            }
            return found;
        }

        /**
         * Decode data (FM)
         *
         * @param inData Data to be analyzed (4 bytes)
         * @return Decoded data
         */
        @Override
        protected byte decodeData(byte[] inData) {
            byte outData;
            outData = (byte) (((inData[0] & 0x80) >> 1) | ((inData[0] & 0x08) << 4));
            outData |= (byte) (((inData[1] & 0x80) >> 3) | ((inData[1] & 0x08) << 2));
            outData |= (byte) (((inData[2] & 0x80) >> 5) | ((inData[2] & 0x08)));
            outData |= (byte) (((inData[3] & 0x80) >> 7) | ((inData[3] & 0x08) >> 2));

            return outData;
        }

        /**
         * IBM FM parser
         * <pre>
         * Bit stream order is:
         * first <- b0 <- b1 <- b2 <- ... <- b7 <- next byte b0 <- b1 ...
         * </pre>
         *
         * @param disk         [in,out] Disk
         * @param trackNumber  Track number
         * @param sideNumber   Side number
         * @param d88OffsetPos D88 offset number
         * @param data         [in,out] Data to be analyzed
         * @param dataLen      Data size
         * @param result       [in,out] Analysis error information
         */
        public FormatFMParser(DiskImageDisk disk, int trackNumber, int sideNumber, int d88OffsetPos, byte[] data, int dataLen, DiskResult result) {
            super(disk, trackNumber, sideNumber, d88OffsetPos, data, dataLen, result);
        }

        @Override
        public int getDecodeUnit() {
            return 4;
        }
    }

    private static final String DISK_HFE_HEADER = "HXCPICFE";
    private static final String DISK_HFE_HEADV3 = "HXCHFEV3";

    private static final byte ISOIBM_MFM_ENCODING = 0x00;
    private static final byte AMIGA_MFM_ENCODING = 0x01;
    private static final byte ISOIBM_FM_ENCODING = 0x02;
    private static final byte EMU_FM_ENCODING = 0x03;
    private static final byte UNKNOWN_ENCODING = (byte) 0xFF;

    // HxC HFE header (512bytes) (LE)
    @Serdes(bigEndian = false)
    public static class HFEHeader {

        @Element(sequence = 1)
        public byte[] signature = new byte[8];
        // always 0
        @Element(sequence = 2)
        public byte revision;
        @Element(sequence = 3)
        public byte tracks;
        @Element(sequence = 4)
        public byte sides;
        @Element(sequence = 5)
        public byte encoding;
        @Element(sequence = 6)
        public short bitRate;
        @Element(sequence = 7)
        public short rpm;

        @Element(sequence = 8)
        public byte interfaceMode;
        @Element(sequence = 9)
        public byte dnu;
        // Offset of the track list LUT in block of 512bytes
        @Element(sequence = 10)
        public short trackListOffset;
        @Element(sequence = 11)
        public byte writeAllowed;
        @Element(sequence = 12)
        public byte singleStep;
        @Element(sequence = 13)
        public byte track0S0EncodeEnable;
        @Element(sequence = 14)
        public byte track0S0Encode;
        @Element(sequence = 15)
        public byte track0S1EncodeEnable;
        @Element(sequence = 16)
        public byte track0S1Encode;

        @Element(sequence = 17)
        public byte[] reserved = new byte[486];

        public static final int SIZE = 512;
    }

    // HxC HFE track offset (LE)
    @Serdes(bigEndian = false)
    public static class HFETrackOffset {

        // Offset of the track data in block of 512bytes
        @Element(sequence = 1)
        public short offset;
        @Element(sequence = 2)
        public short trackLen;

        public static final int SIZE = 4;
    }

    // HxC HFE track offset LUT (up to 1024bytes) (LE)
    @Serdes(bigEndian = false)
    public static class HFETrackOffsetList {

        @Element(sequence = 3)
        public HFETrackOffset[] at = new HFETrackOffset[256];

        public HFETrackOffsetList() {
            for (int i = 0; i < at.length; i++) {
                at[i] = new HFETrackOffset();
            }
        }

        public static final int SIZE = 256 * HFETrackOffset.SIZE;
    }

    private static final Map<Byte, String> HFE_TYPE_MSGS = new LinkedHashMap<>() {{
        put(ISOIBM_MFM_ENCODING, "IBM MFM");
        put(AMIGA_MFM_ENCODING, "Amiga MFM");
        put(ISOIBM_FM_ENCODING, "IBM FM");
        put(EMU_FM_ENCODING, "EMU FM");
        put(UNKNOWN_ENCODING, "unknown");
    }};

    /**
     * Create track data
     *
     * @param istream        [in,out] Data to be analyzed
     * @param track_number   Track number
     * @param sides          Number of sides
     * @param file_offset    File offset
     * @param track_size     Track size
     * @param encoding       Encoding format
     * @param d88_offset_pos [in,out] D88 offset number
     * @param d88_offset     D88 offset
     * @param disk           [in,out] Disk
     * @return D88 offset
     */
    private int parseTracks(InputStream istream, int track_number, int sides, int file_offset, int track_size, byte[] encoding, int[] d88_offset_pos, int d88_offset, DiskImageDisk disk) throws IOException {
        int track_blocks = (track_size / 512);

        byte[][] buffers = new byte[2][];
        buffers[0] = new byte[track_blocks * 256];
        buffers[1] = new byte[track_blocks * 256];

        ((SeekableDataInputStream) istream).position(file_offset);

        boolean working = true;
        for (int block = 0; block < track_blocks && working; block++) {
            for (int side = 0; side < 2 && working; side++) {
                int len = istream.read(buffers[side], block * 256, 256);
                if (len != 256) {
                    result.setError(DiskResult.ERR_NO_TRACK, 0);
                    working = false;
                    break;
                }
            }
        }

        for (int side = 0; side < sides; side++) {
            int d88_track_size = 0;
            DiskImageTrack track = null;
            int sector_nums = 0;
            RunLengthLimitedParser ps = null;

            int side_encoding = encoding[side] & 0xFF;

            switch (side_encoding) {
                case ISOIBM_FM_ENCODING: {
                    // parse FM
                    ps = new FormatFMParser(disk, track_number, side, d88_offset_pos[0], buffers[side], track_blocks * 256, result);
                    d88_track_size = ps.parse();
                    track = ps.getTrack();
                    sector_nums = ps.getNumOfSectors();
                }
                break;
                default: {
                    // parse MFM
                    ps = new FormatMFMParser(disk, track_number, side, d88_offset_pos[0], buffers[side], track_blocks * 256, result);
                    d88_track_size = ps.parse();
                    track = ps.getTrack();
                    sector_nums = ps.getNumOfSectors();
                }
                break;
            }

            if (result.getValid() >= 0 && track != null) {
                // Calculate interleave
                track.calcInterleave();
                // Track size
                track.setSize(d88_track_size);
                // Set number of sectors
                track.setAllSectorsPerTrack(sector_nums);

                // Add to disk
                disk.add(track);
                // Set offset
                disk.setOffset(d88_offset_pos[0], d88_offset);

                d88_offset += d88_track_size;
            }

            d88_offset_pos[0]++;
        }

        return d88_offset;
    }

    /**
     * Analyze disk
     *
     * @param istream Data to be analyzed
     * @return Size
     */
    private int parseDisk(InputStream istream) throws IOException {
        DiskImageDisk disk = file.newImageDisk(0);

        int len = istream.available();
        if (len <= HFEHeader.SIZE) {
            result.setError(DiskResult.ERRV_DISK_TOO_SMALL, 0);
            return 0;
        }
        HFEHeader header = new HFEHeader();
        Serdes.Util.deserialize(istream, header);

        int tracks = header.tracks & 0xff;
        int sides = header.sides & 0xff;

        int d88_offset = disk.getOffsetStart(); // header size
        int[] d88_offset_pos = {0};

        // track list
        int track_list_offset = (header.trackListOffset & 0xffff) * 512;
        ((SeekableDataInputStream) istream).position(track_list_offset);

        len = istream.available();
        if (len < HFETrackOffsetList.SIZE) {
            result.setError(DiskResult.ERRV_DISK_TOO_SMALL, 0);
            return 0;
        }
        HFETrackOffsetList track_offset_list = new HFETrackOffsetList();
        Serdes.Util.deserialize(istream, track_offset_list);

        for (int track_num = 0; track_num < tracks; track_num++) {
            byte[] encoding = new byte[2];
            encoding[0] = header.encoding;
            encoding[1] = header.encoding;
            if (track_num == 0 && (header.track0S0EncodeEnable & 0xff) == 0) encoding[0] = header.track0S0Encode;
            if (track_num == 0 && (header.track0S1EncodeEnable & 0xff) == 0) encoding[1] = header.track0S1Encode;

            d88_offset = parseTracks(istream
                    , track_num, sides
                    , (track_offset_list.at[track_num].offset & 0xffff) * 512
                    , track_offset_list.at[track_num].trackLen & 0xffff
                    , encoding
                    , d88_offset_pos, d88_offset, disk);

            if (d88_offset_pos[0] >= disk.getCreatableTracks()) {
                result.setError(DiskResult.ERRV_OVERFLOW_SIZE, 0, d88_offset);
            }
        }
        // Set maximum track number
        disk.setMaxTrackNumber(tracks);

        disk.setSize(d88_offset);

        if (result.getValid() >= 0) {
            // Add disk
            DiskParam disk_param = disk.calcMajorNumber();
            if (disk_param != null) {
                disk.setDensity(disk_param.getParamDensity());
            }
            disk.setWriteProtect((header.writeAllowed & 0xFF) == 0);
            disk.clearModify();

            file.add(disk, modFlags);
        }

        return d88_offset;
    }

    @Override
    public boolean isSupported(String type) {
        return "hfe".equalsIgnoreCase(type);
    }

    @Override
    public void init(DiskImageFile file, short mod_flags, DiskResult result) {
        super.init(file, mod_flags, result);
    }

    @Override
    public int check(InputStream iStream, List<DiskTypeHint> disk_hints, DiskParam diskParam, List<DiskParam> diskParams, DiskParam manualParam) throws IOException {
        return check(iStream);
    }

    /**
     * Check whether it is HxC HFE file
     *
     * @param iStream Data to be analyzed
     * @return 0: Ok, -1: NG
     */
    @Override
    public int check(InputStream iStream) throws IOException {
        ((SeekableDataInputStream) iStream).position(0);

        int len = iStream.available();
        if (len < HFEHeader.SIZE) {
            // too short
            result.setError(DiskResult.ERRV_DISK_TOO_SMALL, 0);
            return result.getValid();
        }
        HFEHeader header = new HFEHeader();
        Serdes.Util.deserialize(iStream, header);

        // check signature
        String sig = new String(header.signature);
        if ((!sig.startsWith(DISK_HFE_HEADER) && !sig.startsWith(DISK_HFE_HEADV3)) || header.revision != 0) {
            result.setError(DiskResult.ERRV_DISK_HEADER, 0);
            return result.getValid();
        }

        // format
        byte encoding = header.encoding;
        if (encoding != ISOIBM_MFM_ENCODING && encoding != ISOIBM_FM_ENCODING) {
            String msg;
            if (HFE_TYPE_MSGS.containsKey(encoding)) {
                msg = HFE_TYPE_MSGS.get(encoding);
            } else {
                msg = HFE_TYPE_MSGS.get(UNKNOWN_ENCODING);
            }
            result.setError(DiskResult.ERRV_UNSUPPORTED_TYPE, 0, msg);
            return result.getValid();
        }

        // track list
        int track_list_offset = (header.trackListOffset & 0xffff) * 512;

        ((SeekableDataInputStream) iStream).position(track_list_offset);
        for (int track = 0; track < (header.tracks & 0xff); track++) {
            len = iStream.available();
            if (len < HFETrackOffset.SIZE) {
                // too short
                result.setError(DiskResult.ERRV_DISK_TOO_SMALL, 0);
                return result.getValid();
            }
            HFETrackOffset track_offset = new HFETrackOffset();
            Serdes.Util.deserialize(iStream, track_offset);
            if ((track_offset.offset & 0xffff) == 0xffff) {
                // invalid
                result.setError(DiskResult.ERRV_INVALID_DISK, 0);
                return result.getValid();
            }
        }

        return result.getValid();
    }

    /**
     * Analyze HxC HFE file
     */
    @Override
    public int parse(InputStream iStream, DiskParam diskParam) throws IOException {
        parseDisk(iStream);
        return result.getValid();
    }
}
