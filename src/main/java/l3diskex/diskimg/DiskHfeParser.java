package l3diskex.diskimg;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import l3diskex.diskimg.DiskImage.DiskImageDisk;
import l3diskex.diskimg.DiskImage.DiskImageFile;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;
import l3diskex.diskimg.DiskParser.DiskImageParser;
import l3diskex.diskimg.FileParam.DiskTypeHint;
import vavi.io.SeekableDataInputStream;


// C++ type conversion: wxUint8 -> byte (unsigned, but used as byte in data operations), wxUint16 -> short, wxUint32 -> int/long.
// Here, byte for data, int for length/size, int for d88_offset (wxUint32 may exceed int max if used as file offset)
// Note: In Java, byte is signed (-128 to 127). Logic involving unsigned comparison or arithmetic must be handled carefully.

/**
 * Run-length limited(RLL)パーサ
 *
 * 1トラック分を解析
 */
abstract class RunLengthLimitedParser {
    protected DiskImageDisk disk;
    protected DiskImageTrack track;
    protected int track_size;
    protected byte[] data;
    protected int data_len;
    protected int track_number;
    protected int side_number;
    protected int sector_nums;
    protected int d88_offset_pos;
    protected DiskResult result;

    protected static class CurrentIDs {
        public byte C;
        public byte H;
        public byte R;
        public byte N;
        public short CRC; // wxUint16
    }
    protected CurrentIDs curr_ids;

    protected abstract boolean adjustGap();
    protected abstract boolean getData();
    protected int setSectorData(byte[] indata, boolean single, boolean deleted) {
        int track_num = curr_ids.C & 0xFF; // Unsigned byte to int
        int side_num = curr_ids.H & 0xFF;
        int sector_num = curr_ids.R & 0xFF;
        int sector_size_code = curr_ids.N & 0xFF;

        if (sector_size_code > 7) {
            // セクタサイズが大きすぎる
            result.setError(DiskResult.ERRV_SECTOR_SIZE_SECTOR, 0, track_num, side_num, sector_num, sector_size_code, sector_size_code);
            return 0;
        }

        int sector_size = (128 << sector_size_code);

        sector_nums++;
        DiskImageSector sector = track.newImageSector(track_num, side_num, sector_num, sector_size, 1, false, 0);
        track.add(sector);

        byte[] buf = sector.getSectorBuffer();
        int siz = sector.getSectorBufferSize();
        int unit = getDecodeUnit();
        for(int i = 0; i < siz; i++) {
            byte[] unitData = Arrays.copyOfRange(indata, i * unit, i * unit + unit);
            buf[i] = decodeData(unitData);
        }

        sector.setSingleDensity(single);
        sector.setDeletedMark(deleted);
        sector.clearModify();

        // このセクタデータのサイズを返す
        return sector.getSize();
    }
    protected abstract byte decodeData(byte[] indata);

    public RunLengthLimitedParser() {
        disk = null;
        track = null;
        track_size = 0;
        data = null;
        data_len = 0;
        track_number = 0;
        side_number = 0;
        sector_nums = 0;
        d88_offset_pos = 0;
        result = null;
        curr_ids = new CurrentIDs();
    }

    public RunLengthLimitedParser(DiskImageDisk n_disk, int n_track_number, int n_side_number, int n_d88_offset_pos, byte[] n_data, int n_data_len, DiskResult n_result) {
        disk = n_disk;
        track = null;
        track_size = 0;
        data = n_data;
        data_len = n_data_len;
        track_number = n_track_number;
        side_number = n_side_number;
        sector_nums = 0;
        d88_offset_pos = n_d88_offset_pos;
        result = n_result;
        curr_ids = new CurrentIDs();
    }
    // virtual ~RunLengthLimitedParser() in C++ is handled by Java garbage collection

    public int parse() {
        track = disk.newImageTrack(track_number, side_number, d88_offset_pos, 1);
        track_size = 0;

        while(data_len > 0) {
            if (!adjustGap()) {
                break;
            }
            if (!getData()) {
                break;
            }
        }

        return track_size;
    }

    public abstract int getDecodeUnit();

    public DiskImageTrack getTrack() { return track; }
    public int getSectorNums() { return sector_nums; }

    /** バッファをシフト */
    public static int shiftBytes(byte[] data, int len, int sftcnt) {
        if (sftcnt <= 0) return len;

        int endpos = len - sftcnt;

        for(int i = 0; i < endpos; i++) {
            data[i] = data[i + sftcnt];
        }
        // Zeroing out the end is not strictly necessary in Java unless the array is reused with a smaller logical length
        // In C++, the assignment data[endpos] = 0x00 is only for one byte, not for all shifted bytes

        len -= sftcnt;

        return len;
    }

    /** バッファをビットシフト */
    public static int shiftBits(byte[] data, int len, int sftcnt) {
        if (sftcnt <= 0) return len;

        int divn = (sftcnt >> 3);
        int modn = (sftcnt & 7);

        // lshift bytes
        if (divn > 0) {
            len = shiftBytes(data, len, divn);
        }

        if (modn == 0) return len;

        // bit shift
        int carry = 0x00;
        for(int i = len - 1; i >= 0; i--) {
            int currentByte = data[i] & 0xFF; // Unsigned value
            int c = (currentByte << (8 - modn)) & 0xFF;
            data[i] = (byte) ((currentByte >>> modn) | carry);
            carry = c;
        }

        return len;
    }
}

/**
 * IBM MFMパーサ
 *
 * 1トラック分を解析
 */
class FormatMFMParser extends RunLengthLimitedParser {
    @Override
    protected boolean adjustGap() {
        boolean found = false;
        int maxlen = data_len;
        byte[] buf = new byte[6]; // C++ used 3, then 4. Allocating 6 for safety based on the max usage later
        int pos = 0;
        // search GAP field
        for(; pos < maxlen; pos++) {
            if (pos + 3 > maxlen) break; // Check bounds

            System.arraycopy(data, pos, buf, 0, 3);
            int cnt = 0;
            for(; cnt < 8; cnt++) {
                // memcmp(buf, "\x49\x2a", 2) == 0
                if ((buf[0] & 0xFF) == 0x49 && (buf[1] & 0xFF) == 0x2A) {
                    found = true;
                    break;
                }

                // bit shift left
                shiftBits(buf, 3, 1);
            }
            if (found) {
                data_len = shiftBits(data, data_len, pos * 8 + cnt);
                break;
            }
        }
        if (!found) {
            data_len = 0;
            return found;
        }

        // search the terminate of SYNC field
        found = false;
        pos = 0;
        for(; pos < maxlen; pos++) {
            if (pos + 4 > maxlen) break; // Check bounds

            System.arraycopy(data, pos, buf, 0, 4);
            int cnt = 0;
            for(; cnt < 8; cnt++) {
                // memcmp(buf, "\x55\x55\x25", 3) == 0
                boolean m1 = (buf[0] & 0xFF) == 0x55 && (buf[1] & 0xFF) == 0x55 && (buf[2] & 0xFF) == 0x25;
                // memcmp(buf, "\x55\x55\xa5", 3) == 0
                boolean m2 = (buf[0] & 0xFF) == 0x55 && (buf[1] & 0xFF) == 0x55 && (buf[2] & 0xFF) == 0xA5;

                if (m1 || m2) {
                    found = true;
                    break;
                }

                // bit shift left
                shiftBits(buf, 4, 1);
            }
            if (found) {
                if (cnt >= 4) {
                    cnt -= 4;
                } else {
                    pos--;
                    cnt += 4;
                }
                if (pos >= 0) {
                    data_len = shiftBits(data, data_len, pos * 8 + cnt);
                }
                break;
            }
        }
        if (!found) {
            data_len = 0;
        }
        return found;
    }

    @Override
    protected boolean getData() {
        boolean found = false;
        int maxlen = data_len;
        int pos = 0;
        byte[] cmpIdx = {(byte)0x55, (byte)0x55, (byte)0x4a, (byte)0x24, (byte)0x4a, (byte)0x24, (byte)0x4a, (byte)0x24, (byte)0xaa, (byte)0x4a};
        byte[] cmpId = {(byte)0x55, (byte)0x55, (byte)0x22, (byte)0x91, (byte)0x22, (byte)0x91, (byte)0x22, (byte)0x91, (byte)0xaa, (byte)0x2a};
        byte[] cmpData = {(byte)0x55, (byte)0x55, (byte)0x22, (byte)0x91, (byte)0x22, (byte)0x91, (byte)0x22, (byte)0x91, (byte)0xaa, (byte)0xa2};
        byte[] cmpDelData = {(byte)0x55, (byte)0x55, (byte)0x22, (byte)0x91, (byte)0x22, (byte)0x91, (byte)0x22, (byte)0x91, (byte)0xaa, (byte)0x52};

        for(; pos < maxlen && !found; pos++) {
            if (pos + 10 > maxlen) break; // Check bounds

            // INDEX MARK
            if (Arrays.equals(Arrays.copyOfRange(data, pos, pos + 10), cmpIdx)) {
                found = true;
                data_len = shiftBytes(data, data_len, pos + 10);
                break;
            }
            // ID MARK (C,H,R,N,CRC - 5 bytes data * 2 unit/byte = 10 bytes)
            else if (Arrays.equals(Arrays.copyOfRange(data, pos, pos + 10), cmpId)) {
                if (pos + 22 > maxlen) break; // Check bounds for ID data and CRC

                found = true;
                // Get C,H,R,N,CRC (10 bytes data, 2 unit/byte)
                curr_ids.C = decodeData(Arrays.copyOfRange(data, pos + 10, pos + 12));
                curr_ids.H = decodeData(Arrays.copyOfRange(data, pos + 12, pos + 14));
                curr_ids.R = decodeData(Arrays.copyOfRange(data, pos + 14, pos + 16));
                curr_ids.N = decodeData(Arrays.copyOfRange(data, pos + 16, pos + 18));

                int crc_h = decodeData(Arrays.copyOfRange(data, pos + 18, pos + 20)) & 0xFF;
                int crc_l = decodeData(Arrays.copyOfRange(data, pos + 20, pos + 22)) & 0xFF;
                curr_ids.CRC = (short) (crc_h * 256 + crc_l);

                data_len = shiftBytes(data, data_len, pos + 22);
                break;
            }
            // DATA MARK
            else if (Arrays.equals(Arrays.copyOfRange(data, pos, pos + 10), cmpData)) {
                // Get Data: Data size + 2 CRC bytes
                // The size is unknown until SetSectorData is called. It relies on curr_ids.N
                // We'll trust that the HxC image is correctly formed and will exit if data_len becomes too small after the setSectorData call.

                // Temporarily check if enough data is left for minimum sector (128 bytes, 64 data * 2 unit/byte + 2 crc * 2 unit/byte = 132 bytes)
                // This parser structure is a bit problematic here as SetSectorData relies on IDs parsed *before* a data mark.
                // Assuming IDs for this sector were parsed by a previous ID MARK found earlier in the track.

                // For simplicity, we assume the IDs are valid and attempt to parse the data block.
                // The size calculation will be done inside SetSectorData based on curr_ids.N

                found = true;

                // Get Data
                // The size SetSectorData returns is the *decoded* size (128, 256, 512...)
                int siz = setSectorData(Arrays.copyOfRange(data, pos + 10, data_len), false, false);

                if (siz > 0) {
                    track_size += siz;
                }

                int unit = getDecodeUnit();
                // Shift data by preamble (10) + decoded data and CRC (siz + 2) * unit
                int shift = pos + ((siz + 2) * unit) + 10;

                if (shift > data_len) {
                    // Data too short, likely truncated HFE file or logic error
                    data_len = 0;
                    // Should set an error here, but following original C++ logic (just break)
                } else {
                    data_len = shiftBytes(data, data_len, shift);
                }
                break;
            }
            // DELETED DATA MARK
            else if (Arrays.equals(Arrays.copyOfRange(data, pos, pos + 10), cmpDelData)) {
                found = true;
                // Get Data
                int siz = setSectorData(Arrays.copyOfRange(data, pos + 10, data_len), false, true);

                if (siz > 0) {
                    track_size += siz;
                }

                int unit = getDecodeUnit();
                int shift = pos + ((siz + 2) * unit) + 10;

                if (shift > data_len) {
                    data_len = 0;
                } else {
                    data_len = shiftBytes(data, data_len, shift);
                }
                break;
            }
        }
        if (!found) {
            data_len = 0;
        }
        return found;
    }

    @Override
    protected byte decodeData(byte[] indata) {
        // indata is 2 bytes (2 * wxUint8)
        if (indata.length < 2) return 0;

        int in0 = indata[0] & 0xFF;
        int in1 = indata[1] & 0xFF;

        int outdata = 0;
        // The MFM decoding logic translates MFM encoded data (2 bits) back to the original data bit (1 bit).
        // 0x80 -> bit 7 of decoded byte
        outdata |= ((in0 & 0x80) >>> 3);
        // 0x20 -> bit 6 of decoded byte
        outdata |= ((in0 & 0x20));
        // 0x08 -> bit 5 of decoded byte
        outdata |= ((in0 & 0x08) << 3);
        // 0x02 -> bit 4 of decoded byte
        outdata |= ((in0 & 0x02) << 6);

        // 0x80 -> bit 3 of decoded byte
        outdata |= ((in1 & 0x80) >>> 7);
        // 0x20 -> bit 2 of decoded byte
        outdata |= ((in1 & 0x20) >>> 4);
        // 0x08 -> bit 1 of decoded byte
        outdata |= ((in1 & 0x08) >>> 1);
        // 0x02 -> bit 0 of decoded byte
        outdata |= ((in1 & 0x02) << 2);

        return (byte) outdata;
    }

    public FormatMFMParser() {
        super();
    }

    public FormatMFMParser(DiskImageDisk n_disk, int n_track_number, int n_side_number, int n_d88_offset_pos, byte[] n_data, int n_data_len, DiskResult n_result) {
        super(n_disk, n_track_number, n_side_number, n_d88_offset_pos, n_data, n_data_len, n_result);
    }

    @Override
    public int getDecodeUnit() { return 2; }
}

/**
 * IBM FMパーサ
 *
 * 1トラック分を解析
 */
class FormatFMParser extends RunLengthLimitedParser {
    @Override
    protected boolean adjustGap() {
        boolean found = false;
        int maxlen = data_len;
        byte[] buf = new byte[8]; // C++ used 5, then 6. Allocating 8 for safety based on the max usage later
        int pos = 0;
        // search GAP field
        for(; pos < maxlen; pos++) {
            if (pos + 5 > maxlen) break; // Check bounds

            System.arraycopy(data, pos, buf, 0, 5);
            int cnt = 0;
            for(; cnt < 8; cnt++) {
                // memcmp(buf, "\xaa\xaa\xaa\xaa", 4) == 0
                if ((buf[0] & 0xFF) == 0xAA && (buf[1] & 0xFF) == 0xAA && (buf[2] & 0xFF) == 0xAA && (buf[3] & 0xFF) == 0xAA) {
                    found = true;
                    break;
                }

                // bit shift left
                shiftBits(buf, 5, 1);
            }
            if (found) {
                data_len = shiftBits(data, data_len, pos * 8 + cnt);
                break;
            }
        }
        if (!found) {
            data_len = 0;
            return found;
        }

        // search the terminate of SYNC field
        found = false;
        pos = 0;
        for(; pos < maxlen; pos++) {
            if (pos + 6 > maxlen) break; // Check bounds

            System.arraycopy(data, pos, buf, 0, 6);
            int cnt = 0;
            for(; cnt < 8; cnt++) {
                // memcmp(buf, "\x22\x22\x22\x22\xa2", 5) == 0
                if ((buf[0] & 0xFF) == 0x22 && (buf[1] & 0xFF) == 0x22 && (buf[2] & 0xFF) == 0x22 && (buf[3] & 0xFF) == 0x22 && (buf[4] & 0xFF) == 0xA2) {
                    found = true;
                    break;
                }

                // bit shift left
                shiftBits(buf, 6, 1);
            }
            if (found) {
                if (cnt >= 4) {
                    cnt -= 4;
                } else {
                    pos--;
                    cnt += 4;
                }
                if (pos >= 0) {
                    data_len = shiftBits(data, data_len, pos * 8 + cnt);
                }
                break;
            }
        }
        if (!found) {
            data_len = 0;
        }
        return found;
    }

    @Override
    protected boolean getData() {
        boolean found = false;
        int maxlen = data_len;
        int pos = 0;
        byte[] cmpIdx = {(byte)0x22, (byte)0x22, (byte)0x22, (byte)0x22, (byte)0xaa, (byte)0xa8, (byte)0xa8, (byte)0x22};
        byte[] cmpId = {(byte)0x22, (byte)0x22, (byte)0x22, (byte)0x22, (byte)0xaa, (byte)0x88, (byte)0xa8, (byte)0x2a};
        byte[] cmpData = {(byte)0x22, (byte)0x22, (byte)0x22, (byte)0x22, (byte)0xaa, (byte)0x88, (byte)0x28, (byte)0xaa};
        byte[] cmpDelData = {(byte)0x22, (byte)0x22, (byte)0x22, (byte)0x22, (byte)0xaa, (byte)0x88, (byte)0x28, (byte)0x22};

        for(; pos < maxlen && !found; pos++) {
            if (pos + 8 > maxlen) break; // Check bounds

            // INDEX MARK
            if (Arrays.equals(Arrays.copyOfRange(data, pos, pos + 8), cmpIdx)) {
                found = true;
                data_len = shiftBytes(data, data_len, pos + 8);
                break;
            }
            // ID MARK (C,H,R,N,CRC - 5 bytes data * 4 unit/byte = 20 bytes)
            else if (Arrays.equals(Arrays.copyOfRange(data, pos, pos + 8), cmpId)) {
                if (pos + 32 > maxlen) break; // Check bounds for ID data and CRC (5 * 4 unit + 2 CRC bytes * 4 unit)

                found = true;
                // Get C,H,R,N,CRC (20 bytes data, 4 unit/byte)
                curr_ids.C = decodeData(Arrays.copyOfRange(data, pos + 8, pos + 12));
                curr_ids.H = decodeData(Arrays.copyOfRange(data, pos + 12, pos + 16));
                curr_ids.R = decodeData(Arrays.copyOfRange(data, pos + 16, pos + 20));
                curr_ids.N = decodeData(Arrays.copyOfRange(data, pos + 20, pos + 24));

                int crc_h = decodeData(Arrays.copyOfRange(data, pos + 24, pos + 28)) & 0xFF;
                int crc_l = decodeData(Arrays.copyOfRange(data, pos + 28, pos + 32)) & 0xFF;
                curr_ids.CRC = (short) (crc_h * 256 + crc_l);

                data_len = shiftBytes(data, data_len, pos + 32);
                break;
            }
            // DATA MARK
            else if (Arrays.equals(Arrays.copyOfRange(data, pos, pos + 8), cmpData)) {
                found = true;
                // Get Data
                int siz = setSectorData(Arrays.copyOfRange(data, pos + 8, data_len), true, false);

                if (siz > 0) {
                    track_size += siz;
                }

                int unit = getDecodeUnit();
                int shift = pos + ((siz + 2) * unit) + 8;

                if (shift > data_len) {
                    data_len = 0;
                } else {
                    data_len = shiftBytes(data, data_len, shift);
                }
                break;
            }
            // DELETED DATA MARK
            else if (Arrays.equals(Arrays.copyOfRange(data, pos, pos + 8), cmpDelData)) {
                found = true;
                // Get Data
                int siz = setSectorData(Arrays.copyOfRange(data, pos + 8, data_len), true, true);

                if (siz > 0) {
                    track_size += siz;
                }

                int unit = getDecodeUnit();
                int shift = pos + ((siz + 2) * unit) + 8;

                if (shift > data_len) {
                    data_len = 0;
                } else {
                    data_len = shiftBytes(data, data_len, shift);
                }
                break;
            }
        }
        if (!found) {
            data_len = 0;
        }
        return found;
    }

    @Override
    protected byte decodeData(byte[] indata) {
        // indata is 4 bytes (4 * wxUint8)
        if (indata.length < 4) return 0;

        int in0 = indata[0] & 0xFF;
        int in1 = indata[1] & 0xFF;
        int in2 = indata[2] & 0xFF;
        int in3 = indata[3] & 0xFF;

        int outdata = 0;
        // The FM decoding logic translates FM encoded data (4 bits) back to the original data bit (1 bit).
        // 0x80 -> bit 7
        outdata |= ((in0 & 0x80) >>> 1);
        // 0x08 -> bit 6
        outdata |= ((in0 & 0x08) << 4);

        // 0x80 -> bit 5
        outdata |= ((in1 & 0x80) >>> 3);
        // 0x08 -> bit 4
        outdata |= ((in1 & 0x08) << 2);

        // 0x80 -> bit 3
        outdata |= ((in2 & 0x80) >>> 5);
        // 0x08 -> bit 2
        outdata |= ((in2 & 0x08));

        // 0x80 -> bit 1
        outdata |= ((in3 & 0x80) >>> 7);
        // 0x08 -> bit 0
        outdata |= ((in3 & 0x08) >>> 2);

        return (byte) outdata;
    }

    public FormatFMParser() {
        super();
    }

    public FormatFMParser(DiskImageDisk n_disk, int n_track_number, int n_side_number, int n_d88_offset_pos, byte[] n_data, int n_data_len, DiskResult n_result) {
        super(n_disk, n_track_number, n_side_number, n_d88_offset_pos, n_data, n_data_len, n_result);
    }

    @Override
    public int getDecodeUnit() { return 4; }
}

/**
 * HxC HFEディスクパーサー
 */
public class DiskHfeParser extends DiskImageParser {
    private static final String DISK_HFE_HEADER = "HXCPICFE";
    private static final String DISK_HFE_HEADV3 = "HXCHFEV3";

    private static final byte ISOIBM_MFM_ENCODING = 0x00;
    private static final byte AMIGA_MFM_ENCODING = 0x01;
    private static final byte ISOIBM_FM_ENCODING = 0x02;
    private static final byte EMU_FM_ENCODING = 0x03;
    private static final byte UNKNOWN_ENCODING = (byte) 0xFF;

    // HxC HFE header (512bytes) (LE)
    private static class HFEHeader {
        public byte[] signature = new byte[8];
        public byte revision;
        public byte tracks;
        public byte sides;
        public byte encoding;
        public short bit_rate; // wxUint16
        public short rpm;      // wxUint16

        public byte interface_mode;
        public byte dnu;
        public short track_list_offset; // wxUint16. Offset of the track list LUT in block of 512bytes
        public byte write_allowed;
        public byte single_step;
        public byte track0s0_encode_enable;
        public byte track0s0_encode;
        public byte track0s1_encode_enable;
        public byte track0s1_encode;

        public byte[] reserved = new byte[486];

        public static final int SIZE = 512;
    }

    // HxC HFE track offset (LE)
    private static class HFETrackOffset {
        public short offset;	// wxUint16. Offset of the track data in block of 512bytes
        public short track_len; // wxUint16.

        public static final int SIZE = 4;
    }

    // HxC HFE track offset LUT (up to 1024bytes) (LE)
    private static class HFETrackOffsetList {
        public HFETrackOffset[] at = new HFETrackOffset[256];

        public HFETrackOffsetList() {
            for(int i = 0; i < at.length; i++) {
                at[i] = new HFETrackOffset();
            }
        }

        public static final int SIZE = 256 * HFETrackOffset.SIZE;
    }

    // Map for wxTRANSLATE/hfe_type_msgs
    private static final Map<Byte, String> HFE_TYPE_MSGS;
    static {
        Map<Byte, String> map = new HashMap<>();
        map.put(ISOIBM_MFM_ENCODING, "IBM MFM");
        map.put(AMIGA_MFM_ENCODING, "Amiga MFM");
        map.put(ISOIBM_FM_ENCODING, "IBM FM");
        map.put(EMU_FM_ENCODING, "EMU FM");
        map.put(UNKNOWN_ENCODING, "unknown");
        HFE_TYPE_MSGS = Collections.unmodifiableMap(map);
    }

    // Utility to read HFEHeader from InputStream
    private HFEHeader readHeader(InputStream istream) throws IOException {
        byte[] buffer = new byte[HFEHeader.SIZE];
        if (istream.read(buffer) < HFEHeader.SIZE) {
            return null;
        }

        HFEHeader header = new HFEHeader();
        ByteBuffer bb = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN);

        bb.get(header.signature);
        header.revision = bb.get();
        header.tracks = bb.get();
        header.sides = bb.get();
        header.encoding = bb.get();
        header.bit_rate = bb.getShort();
        header.rpm = bb.getShort();
        header.interface_mode = bb.get();
        header.dnu = bb.get();
        header.track_list_offset = bb.getShort();
        header.write_allowed = bb.get();
        header.single_step = bb.get();
        header.track0s0_encode_enable = bb.get();
        header.track0s0_encode = bb.get();
        header.track0s1_encode_enable = bb.get();
        header.track0s1_encode = bb.get();
        bb.get(header.reserved);

        return header;
    }

    // Utility to read HFETrackOffsetList from InputStream
    private HFETrackOffsetList readTrackOffsetList(InputStream istream, int tracks) throws IOException {
        HFETrackOffsetList list = new HFETrackOffsetList();
        byte[] buffer = new byte[tracks * HFETrackOffset.SIZE];
        if (istream.read(buffer) < buffer.length) {
            return null;
        }

        ByteBuffer bb = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN);

        for(int i = 0; i < tracks; i++) {
            list.at[i].offset = bb.getShort();
            list.at[i].track_len = bb.getShort();
        }

        return list;
    }

    // Utility to read HFETrackOffset
    private HFETrackOffset readTrackOffset(InputStream istream) throws IOException {
        HFETrackOffset offset = new HFETrackOffset();
        byte[] buffer = new byte[HFETrackOffset.SIZE];
        if (istream.read(buffer) < HFETrackOffset.SIZE) {
            return null;
        }

        ByteBuffer bb = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN);
        offset.offset = bb.getShort();
        offset.track_len = bb.getShort();

        return offset;
    }


    /**
     * トラックデータの作成
     */
    private int parseTracks(InputStream istream, int track_number, int sides, int file_offset, int track_size, byte[] encoding, int[] d88_offset_pos, int d88_offset, DiskImageDisk disk) throws IOException {
        int track_blocks = (track_size / 512);

        byte[][] buffers = new byte[2][];
        buffers[0] = new byte[track_blocks * 256];
        buffers[1] = new byte[track_blocks * 256];

        // Simulating istream.SeekI(file_offset, wxFromStart);
        if (!(istream instanceof SeekableDataInputStream)) {

            // For a better emulation, if istream is RandomAccessFile, we'd use:
            ((SeekableDataInputStream)istream).position(file_offset);
        }

        // C++: istream.SeekI(file_offset, wxFromStart);
        ((SeekableDataInputStream)istream).position(file_offset);

        boolean working = true;
        for(int block = 0; block < track_blocks && working; block++) {
            for(int side = 0; side < 2 && working; side++) {
                int len = istream.read(buffers[side], block * 256, 256);
                if (len != 256) {
                    result.setError(DiskResult.ERR_NO_TRACK, 0);
                    working = false;
                    break;
                }
            }
        }

        for(int side = 0; side < sides; side++) {
            int d88_track_size = 0;
            DiskImageTrack track = null;
            int sector_nums = 0;
            RunLengthLimitedParser ps = null;

            int side_encoding = encoding[side] & 0xFF;

            switch(side_encoding) {
                case ISOIBM_FM_ENCODING:
                {
                    // parse FM
                    ps = new FormatFMParser(disk, track_number, side, d88_offset_pos[0], buffers[side], track_blocks * 256, result);
                }
                break;
                default:
                {
                    // parse MFM (handles ISOIBM_MFM_ENCODING and others as default)
                    ps = new FormatMFMParser(disk, track_number, side, d88_offset_pos[0], buffers[side], track_blocks * 256, result);
                }
                break;
            }

            // Check if parser was created
            if (ps != null) {
                d88_track_size = ps.parse();
                track = ps.getTrack();
                sector_nums = ps.getSectorNums();
            }

            if (result.getValid() >= 0 && track != null) {
                // インターリーブの計算
                track.calcInterleave();
                // トラックサイズ
                track.setSize(d88_track_size);
                // セクタ数設定
                track.setAllSectorsPerTrack(sector_nums);

                // ディスクに追加
                disk.add(track);
                // オフセット設定
                disk.setOffset(d88_offset_pos[0], d88_offset);

                d88_offset += d88_track_size;
            } else if (track != null) {
                // In Java, this would rely on garbage collection if the track wasn't added to the disk.
                // Since C++ explicitly deletes, we leave the object to be garbage collected,
                // assuming the DiskImageDisk.Add() method handles ownership if successful.
            }

            d88_offset_pos[0]++;
        }

        // Deletion of buffers in C++ is not needed in Java due to garbage collection

        return d88_offset;
    }

    /**
     * ディスクの解析
     */
    private int parseDisk(InputStream istream) throws IOException {
        DiskImageDisk disk = file.newImageDisk(0);

        HFEHeader header = readHeader(istream);
        if (header == null) {
            result.setError(DiskResult.ERRV_DISK_TOO_SMALL, 0);
            return 0;
        }

        int tracks = header.tracks & 0xFF;
        int sides = header.sides & 0xFF;

        int d88_offset = disk.getOffsetStart();	// header size
        int[] d88_offset_pos = new int[1]; // Use array to pass by reference
        d88_offset_pos[0] = 0;

        // track list
        int track_list_offset = header.track_list_offset & 0xFFFF; // wxUINT16_SWAP_ON_BE not needed due to little-endian read
        track_list_offset *= 512;

        // Simulating istream.SeekI(track_list_offset, wxFromStart);
        ((SeekableDataInputStream)istream).position(track_list_offset);

        HFETrackOffsetList track_offset_list = readTrackOffsetList(istream, tracks);
        if (track_offset_list == null) {
            result.setError(DiskResult.ERRV_DISK_TOO_SMALL, 0);
            return 0;
        }

        for(int track_num = 0; track_num < tracks; track_num++) {
            byte[] encoding = new byte[2];
            encoding[0] = header.encoding;
            encoding[1] = header.encoding;

            // track 0 special encoding
            if (track_num == 0 && (header.track0s0_encode_enable & 0xFF) == 0) encoding[0] = header.track0s0_encode;
            if (track_num == 0 && (header.track0s1_encode_enable & 0xFF) == 0) encoding[1] = header.track0s1_encode;

            short t_offset_blocks = track_offset_list.at[track_num].offset;
            short t_len = track_offset_list.at[track_num].track_len;

            d88_offset = parseTracks(istream
                    , track_num, sides
                    , (t_offset_blocks & 0xFFFF) * 512
                    , t_len & 0xFFFF
                    , encoding
                    , d88_offset_pos, d88_offset, disk);

            if (d88_offset_pos[0] >= disk.getCreatableTracks()) {
                result.setError(DiskResult.ERRV_OVERFLOW_SIZE, 0, d88_offset);
            }
        }
        // 最大トラック番号設定
        disk.setMaxTrackNumber(tracks);

        disk.setSize(d88_offset);

        if (result.getValid() >= 0) {
            // ディスクを追加
            DiskParam disk_param = disk.calcMajorNumber();
            if (disk_param != null) {
                disk.setDensity(disk_param.getParamDensity());
            }
            disk.setWriteProtect((header.write_allowed & 0xFF) == 0);
            disk.clearModify();

            file.add(disk, modFlags);
        } else {
            // deletion in C++ is not needed in Java
        }

        return d88_offset;
    }

    public DiskHfeParser(DiskImageFile file, short mod_flags, DiskResult result) {
        super(file, mod_flags, result);
    }

    // Java garbage collection handles destruction

    @Override
    public int check(InputStream istream, List<DiskTypeHint> disk_hints, DiskParam disk_param, List<DiskParam> disk_params, DiskParam manual_param) {
        return -1;
    }

    /**
     * HxC HFEファイルかどうかをチェック
     */
    @Override
    public int check(InputStream istream) throws IOException {
        // Simulating istream.SeekI(0);
        ((SeekableDataInputStream)istream).position(0);

        HFEHeader header = readHeader(istream);
        if (header == null) {
            // too short
            result.setError(DiskResult.ERRV_DISK_TOO_SMALL, 0);
            return result.getValid();
        }

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
        int track_list_offset = header.track_list_offset & 0xFFFF;
        track_list_offset *= 512;
        int tracks = header.tracks & 0xFF;

        // Simulating istream.SeekI(track_list_offset, wxFromStart);
        ((SeekableDataInputStream)istream).position(track_list_offset);

        for(int track_num = 0; track_num < tracks; track_num++) {
            HFETrackOffset track_offset = readTrackOffset(istream);

            if (track_offset == null) {
                // too short
                result.setError(DiskResult.ERRV_DISK_TOO_SMALL, 0);
                return result.getValid();
            }

            if ((track_offset.offset & 0xFFFF) == 0xffff) {
                // invalid
                result.setError(DiskResult.ERRV_INVALID_DISK, 0);
                return result.getValid();
            }
        }

        return result.getValid();
    }

    /**
     * HxC HFEファイルを解析
     */
    @Override
    public int parse(InputStream istream, DiskParam disk_param) throws IOException {
        parseDisk(istream);
        return result.getValid();
    }
}
