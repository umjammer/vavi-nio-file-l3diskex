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
 * HxC HFEディスクパーサー
 */
public class DiskHfeParser extends DiskImageParser {

    /**
     * Run-length limited(RLL)パーサ
     * <p>
     * 1トラック分を解析
     */
    public static abstract class RunLengthLimitedParser {

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

        /// GAPをさがす
        protected abstract boolean adjustGap();

        /** データを得る */
        protected abstract boolean getData();

        /**
         * セクタデータをセット
         *
         * @param indata  [in,out] 解析対象データ
         * @param single  single sided?
         * @param deleted Deleted mark?
         * @return セクタサイズ
         */
        protected int setSectorData(byte[] indata, boolean single, boolean deleted) {
            int track_num = curr_ids.C & 0xff;
            int side_num = curr_ids.H & 0xff;
            int sector_num = curr_ids.R & 0xff;
            int sector_size_code = curr_ids.N & 0xff;

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
            for (int i = 0; i < siz; i++) {
                buf[i] = decodeData(Arrays.copyOfRange(indata, i * unit, i * unit + unit));
            }

            sector.setSingleDensity(single);
            sector.setDeletedMark(deleted);
            sector.clearModify();

            // このセクタデータのサイズを返す
            return sector.getSize();
        }

        /** データをデコード */
        protected abstract byte decodeData(byte[] indata);

        //
        // Run-length limited(RLL)パーサ
        //
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

        /**
         * @param n_disk           [in,out] ディスク
         * @param n_track_number   トラック番号
         * @param n_side_number    サイド番号
         * @param n_d88_offset_pos D88オフセット番号
         * @param n_data           [in,out] 解析対象データ
         * @param n_data_len       データサイズ
         * @param n_result         [in,out] 解析エラー情報
         */
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

        /// データの解析
        ///
        /// @return D88形式でのトラックサイズ
        public int parse() {
            track = disk.newImageTrack(track_number, side_number, d88_offset_pos, 1);
            track_size = 0;

            while (data_len > 0) {
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

        public DiskImageTrack getTrack() {
            return track;
        }

        public int getSectorNums() {
            return sector_nums;
        }

        /**
         * バッファをシフト
         *
         * @param data   [in,out] データ
         * @param len    データ長さ
         * @param sftcnt シフト数
         * @return シフトした後のデータ数
         */
        public static int shiftBytes(byte[] data, int len, int sftcnt) {
            if (sftcnt <= 0) return len;

            int endpos = len - sftcnt;

            for (int i = 0; i < endpos; i++) {
                data[i] = data[i + sftcnt];
            }
            data[endpos] = 0x00;

            len -= sftcnt;

            return len;
        }

        /**
         * バッファをビットシフト
         *
         * @param data   [in,out] データ
         * @param len    データ長さ
         * @param sftcnt シフト数
         * @return シフトした後のデータ数
         */
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
            for (int i = len - 1; i >= 0; i--) {
                int currentByte = data[i] & 0xff;
                int c = (currentByte << (8 - modn)) & 0xff;
                data[i] = (byte) ((currentByte >>> modn) | carry);
                carry = c;
            }

            return len;
        }
    }

    /**
     * IBM MFMパーサ
     * <p>
     * 1トラック分を解析
     */
    public static class FormatMFMParser extends RunLengthLimitedParser {

        /**
         * GAPをさがす(MFM)
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
         * @return GAP and SYNCフィールドあり
         */
        @Override
        protected boolean adjustGap() {
            boolean found = false;
            int maxlen = data_len;
            byte[] buf = new byte[6];
            int pos = 0;
            // search GAP field
            for (; pos < maxlen; pos++) {
                System.arraycopy(data, pos, buf, 0, 3);
                int cnt = 0;
                for (; cnt < 8; cnt++) {
                    if ((buf[0] & 0xff) == 0x49 && (buf[1] & 0xff) == 0x2a) {
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
            for (; pos < maxlen; pos++) {
                System.arraycopy(data, pos, buf, 0, 4);
                int cnt = 0;
                for (; cnt < 8; cnt++) {
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

        private static final byte[] cmpIdx = {(byte) 0x55, (byte) 0x55, (byte) 0x4a, (byte) 0x24, (byte) 0x4a, (byte) 0x24, (byte) 0x4a, (byte) 0x24, (byte) 0xaa, (byte) 0x4a};
        private static final byte[] cmpId = {(byte) 0x55, (byte) 0x55, (byte) 0x22, (byte) 0x91, (byte) 0x22, (byte) 0x91, (byte) 0x22, (byte) 0x91, (byte) 0xaa, (byte) 0x2a};
        private static final byte[] cmpData = {(byte) 0x55, (byte) 0x55, (byte) 0x22, (byte) 0x91, (byte) 0x22, (byte) 0x91, (byte) 0x22, (byte) 0x91, (byte) 0xaa, (byte) 0xa2};
        private static final byte[] cmpDelData = {(byte) 0x55, (byte) 0x55, (byte) 0x22, (byte) 0x91, (byte) 0x22, (byte) 0x91, (byte) 0x22, (byte) 0x91, (byte) 0xaa, (byte) 0x52};

        /**
         * データの解析(MFM)
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
         * @return AMフィールドあり
         */
        @Override
        protected boolean getData() {
            boolean found = false;
            int maxlen = data_len;
            int pos = 0;
            for (; pos < maxlen && !found; pos++) {
                if (Arrays.equals(Arrays.copyOfRange(data, pos, pos + 10), cmpIdx)) {
                    // INDEX MARK
                    found = true;
                    data_len = shiftBytes(data, data_len, pos + 10);
                    break;
                } else if (Arrays.equals(Arrays.copyOfRange(data, pos, pos + 10), cmpId)) {
                    // ID MARK
                    found = true;
                    // Get C,H,R,N,CRC
                    curr_ids.C = decodeData(Arrays.copyOfRange(data, pos + 10, pos + 12));
                    curr_ids.H = decodeData(Arrays.copyOfRange(data, pos + 12, pos + 14));
                    curr_ids.R = decodeData(Arrays.copyOfRange(data, pos + 14, pos + 16));
                    curr_ids.N = decodeData(Arrays.copyOfRange(data, pos + 16, pos + 18));
                    int crc_h = decodeData(Arrays.copyOfRange(data, pos + 18, pos + 20)) & 0xFF;
                    int crc_l = decodeData(Arrays.copyOfRange(data, pos + 20, pos + 22)) & 0xFF;
                    curr_ids.CRC = (short) (crc_h * 256 + crc_l);

                    data_len = shiftBytes(data, data_len, pos + 22);
                    break;
                } else if (Arrays.equals(Arrays.copyOfRange(data, pos, pos + 10), cmpData)) {
                    // DATA MARK
                    found = true;
                    // Get Data
                    int siz = setSectorData(Arrays.copyOfRange(data, pos + 10, data_len), false, false);
                    track_size += siz;

                    int unit = getDecodeUnit();
                    data_len = shiftBytes(data, data_len, pos + ((siz + 2) * unit) + 10);
                    break;
                } else if (Arrays.equals(Arrays.copyOfRange(data, pos, pos + 10), cmpDelData)) {
                    // DELETED DATA MARK
                    found = true;
                    // Get Data
                    int siz = setSectorData(Arrays.copyOfRange(data, pos + 10, data_len), false, true);
                    track_size += siz;

                    int unit = getDecodeUnit();
                    data_len = shiftBytes(data, data_len, pos + ((siz + 2) * unit) + 10);
                    break;
                }
            }
            if (!found) {
                data_len = 0;
            }
            return found;
        }

        /// データをデコード(MFM)
        ///
        /// @param indata 解析対象データ(2bytes)
        /// @return デコード後のデータ
        @Override
        protected byte decodeData(byte[] indata) {
            byte outdata;
            outdata = (byte) (((indata[0] & 0x80) >> 3) | ((indata[0] & 0x20)) | ((indata[0] & 0x08) << 3) | ((indata[0] & 0x02) << 6));
            outdata |= (byte) (((indata[1] & 0x80) >> 7) | ((indata[1] & 0x20) >> 4) | ((indata[1] & 0x08) >> 1) | ((indata[1] & 0x02) << 2));

            return outdata;
        }

        public FormatMFMParser(DiskImageDisk n_disk, int n_track_number, int n_side_number, int n_d88_offset_pos, byte[] n_data, int n_data_len, DiskResult n_result) {
            super(n_disk, n_track_number, n_side_number, n_d88_offset_pos, n_data, n_data_len, n_result);
        }

        @Override
        public int getDecodeUnit() {
            return 2;
        }
    }

    /**
     * IBM FMパーサ
     * <p>
     * 1トラック分を解析
     */
    public static class FormatFMParser extends DiskHfeParser.RunLengthLimitedParser {

        /**
         * GAPをさがす(FM)
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
         * @return GAP and SYNCフィールドあり
         */
        @Override
        protected boolean adjustGap() {
            boolean found = false;
            int maxlen = data_len;
            byte[] buf = new byte[8];
            int pos = 0;
            // search GAP field
            for (; pos < maxlen; pos++) {
                System.arraycopy(data, pos, buf, 0, 5);
                int cnt = 0;
                for (; cnt < 8; cnt++) {
                    if ((buf[0] & 0xff) == 0xaa && (buf[1] & 0xff) == 0xaa && (buf[2] & 0xff) == 0xaa && (buf[3] & 0xff) == 0xaa) {
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
            for (; pos < maxlen; pos++) {
                System.arraycopy(data, pos, buf, 0, 6);
                int cnt = 0;
                for (; cnt < 8; cnt++) {
                    if ((buf[0] & 0xff) == 0x22 && (buf[1] & 0xff) == 0x22 && (buf[2] & 0xff) == 0x22 && (buf[3] & 0xff) == 0x22 && (buf[4] & 0xff) == 0xa2) {
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

        private static final byte[] cmpIdx = {(byte) 0x22, (byte) 0x22, (byte) 0x22, (byte) 0x22, (byte) 0xaa, (byte) 0xa8, (byte) 0xa8, (byte) 0x22};
        private static final byte[] cmpId = {(byte) 0x22, (byte) 0x22, (byte) 0x22, (byte) 0x22, (byte) 0xaa, (byte) 0x88, (byte) 0xa8, (byte) 0x2a};
        private static final byte[] cmpData = {(byte) 0x22, (byte) 0x22, (byte) 0x22, (byte) 0x22, (byte) 0xaa, (byte) 0x88, (byte) 0x28, (byte) 0xaa};
        private static final byte[] cmpDelData = {(byte) 0x22, (byte) 0x22, (byte) 0x22, (byte) 0x22, (byte) 0xaa, (byte) 0x88, (byte) 0x28, (byte) 0x22};

        /**
         * データの解析(FM)
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
         * @return AMフィールドあり
         */
        @Override
        protected boolean getData() {
            boolean found = false;
            int maxlen = data_len;
            int pos = 0;
            for (; pos < maxlen && !found; pos++) {
                if (Arrays.equals(Arrays.copyOfRange(data, pos, pos + 8), cmpIdx)) {
                    // INDEX MARK
                    found = true;
                    data_len = shiftBytes(data, data_len, pos + 8);
                    break;
                } else if (Arrays.equals(Arrays.copyOfRange(data, pos, pos + 8), cmpId)) {
                    // ID MARK
                    found = true;
                    // Get C,H,R,N,CRC
                    curr_ids.C = decodeData(Arrays.copyOfRange(data, pos + 8, pos + 12));
                    curr_ids.H = decodeData(Arrays.copyOfRange(data, pos + 12, pos + 16));
                    curr_ids.R = decodeData(Arrays.copyOfRange(data, pos + 16, pos + 20));
                    curr_ids.N = decodeData(Arrays.copyOfRange(data, pos + 20, pos + 24));
                    int crc_h = decodeData(Arrays.copyOfRange(data, pos + 24, pos + 28)) & 0xFF;
                    int crc_l = decodeData(Arrays.copyOfRange(data, pos + 28, pos + 32)) & 0xFF;
                    curr_ids.CRC = (short) (crc_h * 256 + crc_l);

                    data_len = shiftBytes(data, data_len, pos + 32);
                    break;
                } else if (Arrays.equals(Arrays.copyOfRange(data, pos, pos + 8), cmpData)) {
                    // DATA MARK
                    found = true;
                    // Get Data
                    int siz = setSectorData(Arrays.copyOfRange(data, pos + 8, data_len), true, false);
                    track_size += siz;

                    int unit = getDecodeUnit();
                    data_len = shiftBytes(data, data_len, pos + ((siz + 2) * unit) + 8);
                    break;
                } else if (Arrays.equals(Arrays.copyOfRange(data, pos, pos + 8), cmpDelData)) {
                    // DELETED DATA MARK
                    found = true;
                    // Get Data
                    int siz = setSectorData(Arrays.copyOfRange(data, pos + 8, data_len), true, true);
                    track_size += siz;

                    int unit = getDecodeUnit();
                    data_len = shiftBytes(data, data_len, pos + ((siz + 2) * unit) + 8);
                    break;
                }
            }
            if (!found) {
                data_len = 0;
            }
            return found;
        }

        /// データをデコード(FM)
        ///
        /// @param indata 解析対象データ(4bytes)
        /// @return デコード後のデータ
        @Override
        protected byte decodeData(byte[] indata) {
            byte outdata;
            outdata = (byte) (((indata[0] & 0x80) >> 1) | ((indata[0] & 0x08) << 4));
            outdata |= (byte) (((indata[1] & 0x80) >> 3) | ((indata[1] & 0x08) << 2));
            outdata |= (byte) (((indata[2] & 0x80) >> 5) | ((indata[2] & 0x08)));
            outdata |= (byte) (((indata[3] & 0x80) >> 7) | ((indata[3] & 0x08) >> 2));

            return outdata;
        }

        /**
         * IBM FMパーサ
         * <pre>
         * Bit stream order is:
         * first <- b0 <- b1 <- b2 <- ... <- b7 <- next byte b0 <- b1 ...
         * </pre>
         *
         * @param n_disk           [in,out] ディスク
         * @param n_track_number   トラック番号
         * @param n_side_number    サイド番号
         * @param n_d88_offset_pos D88オフセット番号
         * @param n_data           [in,out] 解析対象データ
         * @param n_data_len       データサイズ
         * @param n_result         [in,out] 解析エラー情報
         */
        public FormatFMParser(DiskImageDisk n_disk, int n_track_number, int n_side_number, int n_d88_offset_pos, byte[] n_data, int n_data_len, DiskResult n_result) {
            super(n_disk, n_track_number, n_side_number, n_d88_offset_pos, n_data, n_data_len, n_result);
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
        public short bit_rate;
        @Element(sequence = 7)
        public short rpm;

        @Element(sequence = 8)
        public byte interface_mode;
        @Element(sequence = 9)
        public byte dnu;
        // Offset of the track list LUT in block of 512bytes
        @Element(sequence = 10)
        public short track_list_offset;
        @Element(sequence = 11)
        public byte write_allowed;
        @Element(sequence = 12)
        public byte single_step;
        @Element(sequence = 13)
        public byte track0s0_encode_enable;
        @Element(sequence = 14)
        public byte track0s0_encode;
        @Element(sequence = 15)
        public byte track0s1_encode_enable;
        @Element(sequence = 16)
        public byte track0s1_encode;

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
        public short track_len;

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
     * トラックデータの作成
     *
     * @param istream        [in,out] 解析対象データ
     * @param track_number   トラック番号
     * @param sides          サイド数
     * @param file_offset    ファイルオフセット
     * @param track_size     トラックサイズ
     * @param encoding       エンコード形式
     * @param d88_offset_pos [in,out] D88オフセット番号
     * @param d88_offset     D88オフセット
     * @param disk           [in,out] ディスク
     * @return D88オフセット
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
                    sector_nums = ps.getSectorNums();
                }
                break;
                default: {
                    // parse MFM
                    ps = new FormatMFMParser(disk, track_number, side, d88_offset_pos[0], buffers[side], track_blocks * 256, result);
                    d88_track_size = ps.parse();
                    track = ps.getTrack();
                    sector_nums = ps.getSectorNums();
                }
                break;
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
            }

            d88_offset_pos[0]++;
        }

        return d88_offset;
    }

    /**
     * ディスクの解析
     *
     * @param istream 解析対象データ
     * @return サイズ
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
        int track_list_offset = (header.track_list_offset & 0xffff) * 512;
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
            if (track_num == 0 && (header.track0s0_encode_enable & 0xff) == 0) encoding[0] = header.track0s0_encode;
            if (track_num == 0 && (header.track0s1_encode_enable & 0xff) == 0) encoding[1] = header.track0s1_encode;

            d88_offset = parseTracks(istream
                    , track_num, sides
                    , (track_offset_list.at[track_num].offset & 0xffff) * 512
                    , track_offset_list.at[track_num].track_len & 0xffff
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
        }

        return d88_offset;
    }

    public DiskHfeParser(DiskImageFile file, short mod_flags, DiskResult result) {
        super(file, mod_flags, result);
    }

    @Override
    public int check(InputStream istream, List<DiskTypeHint> disk_hints, DiskParam disk_param, List<DiskParam> disk_params, DiskParam manual_param) {
        return -1;
    }

    /**
     * HxC HFEファイルかどうかをチェック
     *
     * @param istream 解析対象データ
     * @return 0: Ok, -1: NG
     */
    @Override
    public int check(InputStream istream) throws IOException {
        ((SeekableDataInputStream) istream).position(0);

        int len = istream.available();
        if (len < HFEHeader.SIZE) {
            // too short
            result.setError(DiskResult.ERRV_DISK_TOO_SMALL, 0);
            return result.getValid();
        }
        HFEHeader header = new HFEHeader();
        Serdes.Util.deserialize(istream, header);

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
        int track_list_offset = (header.track_list_offset & 0xffff) * 512;

        ((SeekableDataInputStream) istream).position(track_list_offset);
        for (int track = 0; track < (header.tracks & 0xff); track++) {
            len = istream.available();
            if (len < HFETrackOffset.SIZE) {
                // too short
                result.setError(DiskResult.ERRV_DISK_TOO_SMALL, 0);
                return result.getValid();
            }
            HFETrackOffset track_offset = new HFETrackOffset();
            Serdes.Util.deserialize(istream, track_offset);
            if ((track_offset.offset & 0xffff) == 0xffff) {
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
