package l3diskex.basicfmt;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;

import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_FREE;
import static l3diskex.basicfmt.DiskBasicType.INVALID_GROUP_NUMBER;


/**
 * FATアクセス
 */
public class DiskBasicFat {

    /**
     * 使用状況テーブル
     */
    public static class DiskBasicAvailability {

        /**
         * 使用状況テーブル enum
         */
        public enum FatAvailability {
            FAT_AVAIL_FREE,
            FAT_AVAIL_SYSTEM,
            FAT_AVAIL_USED,
            FAT_AVAIL_USED_FIRST,
            FAT_AVAIL_USED_LAST,
            FAT_AVAIL_MISSING,
            FAT_AVAIL_LEAK,
            FAT_AVAIL_NULLEND
        }

        List<FatAvailability> list = new ArrayList<>();

        /** 空きサイズ */
        private int freeSize;
        /** 空きグループ数 */
        private int freeGroups;

        public DiskBasicAvailability() {
            super();
            freeSize = -1;
            freeGroups = -1;
        }

        /**
         * 初期化 空きサイズを 0 にする
         */
        public void clear() {
            list.clear();
            freeSize = 0;
            freeGroups = 0;
        }

        /**
         * 初期化 空きサイズを 0 にする
         */
        public void empty() {
            list.clear();
            freeSize = 0;
            freeGroups = 0;
        }

        /**
         * 初期化 空きサイズを -1 にする
         */
        public void emptyInit() {
            list.clear();
            freeSize = -1;
            freeGroups = -1;
        }

        /**
         * @param val   値
         * @param size  空きサイズ
         * @param group 空きグループ数
         *              追加
         */
        public void add(FatAvailability val, int size, int group) {
            list.add(val);
            freeSize += size;
            freeGroups += group;
        }

        /**
         * @param idx 位置
         * @param val 値
         *            セット (Safety)
         */
        public void set(int idx, FatAvailability val) {
            if (idx < list.size()) {
                list.set(idx, val);
            }
        }

        /**
         * @param index 位置
         * @return 値
         * ゲット (Safety)
         */
        public final FatAvailability get(int index) {
            FatAvailability val = FAT_AVAIL_FREE;
            if (index < list.size()) {
                val = list.get(index);
            }
            return val;
        }

        // Size is int in Java, but for internal use, we map Count() to size()
        public int count() {
            return list.size();
        }

        /**
         * 空きサイズを返す
         */
        public int getFreeSize() {
            return freeSize;
        }

        /**
         * 空きグループ数を返す
         */
        public int getFreeGroups() {
            return freeGroups;
        }

        /**
         * 空きサイズをセット
         */
        public void setFreeSize(int val) {
            freeSize = val;
        }

        /**
         * 空きグループ数をセット
         */
        public void setFreeGroups(int val) {
            freeGroups = val;
        }

        public int size() {
            return list.size();
        }
    }

    /**
     * ビット ON/OFF バッファ １つ
     *
     * @see DiskBasicBitMLMap
     */
    public static class BitMLBuffer {

        protected byte[] buffer;
        protected int size;

        public BitMLBuffer() {
            buffer = null;
            size = 0;
        }

        public BitMLBuffer(byte[] buffer, int size) {
            this.buffer = buffer;
            this.size = size;
        }

        /**
         * 指定位置のビットを変更する
         *
         * @param num ビット位置
         * @param val true:セット / false:リセット
         */
        public void modify(int num, boolean val) {
            int pos = num >> 3;
            int bit = num & 7;
            if (buffer != null && pos < size) {
                if (val) {
                    buffer[pos] |= (byte) (0x80 >> bit);
                } else {
                    buffer[pos] &= (byte) ~(0x80 >> bit);
                }
            }
        }

        /**
         * 指定位置のビットがセットされているか
         *
         * @param num ビット位置
         * @return true:セット / false:リセット
         */
        public boolean isSet(int num) {
            int pos = num >> 3;
            int bit = num & 7;
            if (buffer != null && pos < size) {
                return ((buffer[pos] & (0x80 >> bit)) != 0);
            }
            return false;
        }

        /**
         * 指定位置のビット位置を計算
         *
         * @param num ビット位置
         * @param pos バッファ位置
         * @param bit ビット
         */
        public void getPos(int num, int[] pos, int[] bit) {
            pos[0] = num >> 3;
            bit[0] = num & 7;
        }

        /**
         * バッファを返す
         */
        public byte[] getBuffer() {
            return buffer;
        }

        /**
         * サイズ(ビット数)を返す
         */
        public int getBitSize() {
            return size << 3;
        }

        /**
         * バッファサイズを返す
         */
        public int getSize() {
            return size;
        }
    }

    /**
     * ビット ON/OFF マップ BitMLBuffer の配列
     */
    public static class DiskBasicBitMLMap {

        public List<BitMLBuffer> list = new ArrayList<>();

        public DiskBasicBitMLMap() {
        }

        /**
         * ポインタをセット
         */
        public void addBuffer(byte[] buffer, int size) {
            list.add(new BitMLBuffer(buffer, size));
        }

        /**
         * @param group_num 位置
         * @param val       true:セット / false:リセット
         *                  指定位置のビットを変更する
         */
        public void modify(int group_num, boolean val) {
            for (int idx = 0; idx < size(); idx++) {
                BitMLBuffer item = get(idx);
                int size = item.getBitSize();
                if (group_num < size) {
                    item.modify(group_num, val);
                    break;
                }
                group_num -= size;
            }
        }

        /**
         * 指定位置が空いているか
         *
         * @param group_num 位置
         * @return true:セット / false:リセット
         */
        public boolean isSet(int group_num) {
            boolean val = false;
            for (int idx = 0; idx < size(); idx++) {
                BitMLBuffer item = get(idx);
                int size = item.getBitSize();
                if (group_num < size) {
                    val = item.isSet(group_num);
                    break;
                }
                group_num -= size;
            }
            return val;
        }

        /**
         * 指定位置からバッファ内の位置を計算
         *
         * @param group_num 位置
         * @param idx       MAPの位置
         * @param pos       MAP内のバッファ位置(byte)
         * @param bit       ビット位置
         * @return true / false オーバフロー
         */
        public boolean getPosInMap(int group_num, int[] idx, int[] pos, int[] bit) {
            boolean valid = false;
            for (idx[0] = 0; idx[0] < size(); idx[0]++) {
                BitMLBuffer item = get(idx[0]);
                int size = item.getBitSize();
                if (group_num < size) {
                    item.getPos(group_num, pos, bit);
                    valid = true;
                    break;
                }
                group_num -= size;
            }
            return valid;
        }

        public BitMLBuffer get(int i) {
            return list.get(i);

        }

        public int size() {
            return list.size();
        }
    }

    /**
     * FATエリア（セクタ）へのポインタを保持
     */
    public static class DiskBasicFatBuffer {

        /** バッファサイズ */
        private final int size;
        /** バッファポインタ（セクタ内の開始ポインタ） */
        private final byte[] buffer;

        public DiskBasicFatBuffer() {
            size = 0;
            buffer = null;
        }

        public DiskBasicFatBuffer(byte[] newBuf, int newSize) {
            size = newSize;
            this.buffer = newBuf;
        }

        /**
         * バッファポインタを返す
         */
        public byte[] getBuffer() {
            return buffer;
        }

        /**
         * バッファサイズを返す
         */
        public int getSize() {
            return size;
        }

        /**
         * バッファを指定コードで埋める
         *
         * @param code コード
         */
        public void fill(byte code) {
            if (buffer != null) {
                Arrays.fill(buffer, 0, size, code);
            }
        }

        /**
         * バッファにコピー
         *
         * @param buf バッファ
         * @param len サイズ
         */
        public void copy(byte[] buf, int len) {
            if (buffer != null) {
                int copyLen = Math.min(len, size);
                System.arraycopy(buf, 0, buffer, 0, copyLen);
            }
        }

        /**
         * 指定位置のデータを返す(8ビット)
         *
         * @param pos 位置(8ビット1単位)
         * @return 値
         */
        public int get(int pos) {
            return buffer != null ? (buffer[pos] & 0xff) : INVALID_GROUP_NUMBER;
        }

        /**
         * 指定位置にデータをセット(8ビット)
         *
         * @param pos 位置(8ビット1単位)
         * @param val 値
         */
        public void set(int pos, int val) {
            if (buffer != null) {
                buffer[pos] = (byte) (val & 0xff);
            }
        }

        /**
         * 指定位置のビットをセット/リセット
         *
         * @param pos    位置(8ビット1単位)
         * @param mask   対象のビット
         * @param val    セット/リセット
         * @param invert 反転するか
         * @return 処理したか
         */
        public boolean bit(int pos, int mask, boolean val, boolean invert) {
            if (pos >= size) return false;

            int bit = get(pos); // Get returns 0-255
            if (invert) bit ^= 0xff;
            bit = (val ? (bit | (mask & 0xff)) : (bit & ~(mask & 0xff)));
            if (invert) bit ^= 0xff;
            set(pos, bit);
            return true;
        }

        /**
         * 指定位置のビットがONか
         *
         * @param pos    位置(8ビット1単位)
         * @param mask   対象のビット
         * @param invert 反転するか
         * @return ビットがON
         */
        public boolean bitTest(int pos, byte mask, boolean invert) {
            if (pos >= size) return false;

            int bit = get(pos); // Get returns 0-255
            if (invert) bit ^= 0xff;
            return (bit & (mask & 0xff)) != 0;
        }

        /**
         * 指定位置のデータを返す(16ビット、リトルエンディアン)
         *
         * @param pos 位置(8ビット1単位)
         * @return 値
         */
        public int get16LE(int pos) {
            if (buffer == null || pos + 1 >= size) return INVALID_GROUP_NUMBER;
            int b0 = buffer[pos] & 0xff;
            int b1 = buffer[pos + 1] & 0xff;
            return (b1 << 8) | b0;
        }

        /**
         * 指定位置にデータをセット(16ビット、リトルエンディアン)
         *
         * @param pos 位置(8ビット1単位)
         * @param val 値
         */
        public void set16LE(int pos, int val) {
            if (buffer != null && pos + 1 < size) {
                buffer[pos] = (byte) (val & 0xff);
                buffer[pos + 1] = (byte) ((val >> 8) & 0xff);
            }
        }

        /**
         * 指定位置のデータを返す(16ビット、ビッグエンディアン)
         *
         * @param pos 位置(8ビット1単位)
         * @return 値
         */
        public int get16BE(int pos) {
            if (buffer == null || pos + 1 >= size) return INVALID_GROUP_NUMBER;
            int b0 = buffer[pos] & 0xff;
            int b1 = buffer[pos + 1] & 0xff;
            return (b0 << 8) | b1;
        }

        /**
         * 指定位置にデータをセット(16ビット、ビッグエンディアン)
         *
         * @param pos 位置(8ビット1単位)
         * @param val 値
         */
        public void Set16BE(int pos, int val) {
            if (buffer != null && pos + 1 < size) {
                buffer[pos] = (byte) ((val >> 8) & 0xff);
                buffer[pos + 1] = (byte) (val & 0xff);
            }
        }

        //
        // DiskBasicFatBuffers
        //

        /**
         * 8ビットデータを返す
         *
         * @param pos 位置(8ビット1単位)
         * @return 値
         */
        public static int getData8(List<DiskBasicFatBuffer> list, int pos) {
            int val = INVALID_GROUP_NUMBER;
            for (DiskBasicFatBuffer buf : list) {
                if (pos < buf.getSize()) {
                    val = buf.get(pos);
                    break;
                }
                pos -= buf.getSize();
            }
            return val;
        }

        /**
         * 8ビットデータをセット
         *
         * @param pos 位置(8ビット1単位)
         * @param val 値
         */
        public static void setData8(List<DiskBasicFatBuffer> list, int pos, int val) {
            for (DiskBasicFatBuffer buf : list) {
                if (pos < buf.getSize()) {
                    buf.set(pos, val);
                    break;
                }
                pos -= buf.getSize();
            }
        }

        /**
         * 8ビットデータが一致するか
         *
         * @param pos 位置(8ビット1単位)
         * @param val 値
         * @return 一致する
         */
        public static boolean matchData8(List<DiskBasicFatBuffer> list, int pos, int val) {
            boolean match = false;
            for (DiskBasicFatBuffer buf : list) {
                if (pos < buf.getSize()) {
                    match = (buf.get(pos) == val);
                    break;
                }
                pos -= buf.getSize();
            }
            return match;
        }

        /**
         * 8ビットデータのビットをセット/リセット
         *
         * @param pos    位置(8ビット1単位)
         * @param mask   対象のビット
         * @param val    セット/リセット
         * @param invert 反転するか
         * @return 処理したか
         */
        public static boolean bitData8(List<DiskBasicFatBuffer> list, int pos, byte mask, boolean val, boolean invert) {
            boolean processed = false;
            for (DiskBasicFatBuffer buf : list) {
                processed = buf.bit(pos, mask, val, invert);
                if (processed) break;
                pos -= buf.getSize();
            }
            return processed;
        }

        /**
         * 12ビットデータ(リトルエンディアン)を返す
         *
         * @param pos 位置(12ビット1単位)
         * @return 値
         */
        public static int getData12LE(List<DiskBasicFatBuffer> list, int pos) {
            int val = INVALID_GROUP_NUMBER;
            boolean odd = (pos & 1) != 0;
            pos = pos * 3 / 2;
            int cnt = 0;
            for (int i = 0; i < list.size() && cnt < 2; i++) {
                DiskBasicFatBuffer buf = list.get(i);
                while (pos < buf.getSize() && cnt < 2) {
                    int tmp = buf.get(pos);
                    if (cnt == 0) {
                        val = odd ? tmp >> 4 : tmp;
                    } else {
                        val |= odd ? tmp << 4 : (tmp & 0x0f) << 8;
                    }
                    pos++;
                    cnt++;
                }
                pos -= buf.getSize();
            }
            if (cnt != 2) val = INVALID_GROUP_NUMBER;
            return val & 0xfff;
        }

        /**
         * 12ビットデータ(リトルエンディアン)をセット
         *
         * @param pos 位置(12ビット1単位)
         * @param val 値
         */
        public static void setData12LE(List<DiskBasicFatBuffer> list, int pos, int val) {
            boolean odd = ((pos & 1) != 0);
            pos = pos * 3 / 2;
            int cnt = 0;
            for (int i = 0; i < list.size() && cnt < 2; i++) {
                DiskBasicFatBuffer buf = list.get(i);
                while (pos < buf.getSize() && cnt < 2) {
                    int tmp = buf.get(pos);
                    if (cnt == 0) {
                        tmp = odd ? ((val & 0x0f) << 4) | (tmp & 0x0f) : (val & 0xff);
                    } else {
                        tmp = odd ? (val >> 4) & 0xff : ((val >> 8) & 0x0f) | (tmp & 0xf0);
                    }
                    buf.set(pos, tmp);
                    pos++;
                    cnt++;
                }
                pos -= buf.getSize();
            }
        }

        /**
         * 16ビットデータ(リトルエンディアン)を返す
         *
         * @param pos 位置(16ビット1単位)
         * @return 値
         */
        public static int getData16LE(List<DiskBasicFatBuffer> list, int pos) {
            int val = INVALID_GROUP_NUMBER;
            pos *= 2;
            for (DiskBasicFatBuffer buf : list) {
                if (pos < buf.getSize()) {
                    val = buf.get16LE(pos);
                    break;
                }
                pos -= buf.getSize();
            }
            return val;
        }

        /**
         * 16ビットデータ(リトルエンディアン)をセット
         *
         * @param pos 位置(16ビット1単位)
         * @param val 値
         */
        public static void setData16LE(List<DiskBasicFatBuffer> list, int pos, int val) {
            pos *= 2;
            for (DiskBasicFatBuffer buf : list) {
                if (pos < buf.getSize()) {
                    buf.set16LE(pos, val);
                    break;
                }
                pos -= buf.getSize();
            }
        }

        /**
         * 16ビットデータ(ビッグエンディアン)を返す
         *
         * @param pos 位置(16ビット1単位)
         * @return 値
         */
        public static int getData16BE(List<DiskBasicFatBuffer> list, int pos) {
            int val = INVALID_GROUP_NUMBER;
            pos *= 2;
            for (DiskBasicFatBuffer buf : list) {
                if (pos < buf.getSize()) {
                    val = buf.get16BE(pos);
                    break;
                }
                pos -= buf.getSize();
            }
            return val;
        }

        /**
         * 16ビットデータ(ビッグエンディアン)をセット
         *
         * @param pos 位置(16ビット1単位)
         * @param val 値
         */
        public static void setData16BE(List<DiskBasicFatBuffer> list, int pos, int val) {
            pos *= 2;
            for (DiskBasicFatBuffer buf : list) {
                if (pos < buf.getSize()) {
                    buf.Set16BE(pos, val);
                    break;
                }
                pos -= buf.getSize();
            }
        }
    }

    /**
     * FATバッファ（ミラーリング含む） DiskBasicFatBuffers の配列
     */
    public static class DiskBasicFatArea {

        List<List<DiskBasicFat.DiskBasicFatBuffer>> list = new ArrayList<>();

        /** 有効なバッファ数 */
        private int validCount;

        public DiskBasicFatArea() {
            validCount = list.size();
        }

        /**
         * 代入
         */
        public DiskBasicFatArea operatorAssign(DiskBasicFatArea src) {
            list.clear();
            list.addAll(src.list);
            validCount = src.validCount;
            return this;
        }

        /**
         * クリア
         */
        public void empty() {
            list.clear();
            validCount = 0;
        }

        /**
         * 追加
         *
         * @param lItem   追加するアイテム
         * @param nInsert 追加する数
         */
        public void add(List<DiskBasicFatBuffer> lItem, int nInsert) {
            for (int i = 0; i < nInsert; i++) {
                list.add(lItem);
            }
            validCount = list.size();
        }

        public void add(List<DiskBasicFatBuffer> lItem) {
            add(lItem, 1);
        }

        /**
         * 有効なバッファ数をセット
         */
        public void setValidCount(int val) {
            validCount = val;
        }

        /**
         * 有効なバッファ数を返す
         */
        public int getValidCount() {
            return validCount;
        }

        /**
         * 8ビットデータを返す
         *
         * @param idx ミラーリング位置
         * @param pos 位置(8ビット1単位)
         * @return 値
         */
        public int getData8(int idx, int pos) {
            int val = INVALID_GROUP_NUMBER;
            if (idx >= list.size()) return val;

            List<DiskBasicFatBuffer> bufs = list.get(idx);
            val = DiskBasicFatBuffer.getData8(bufs, pos);
            return val;
        }

        /**
         * 8ビットデータをセット
         *
         * @param pos 位置(8ビット1単位)
         * @param val 値
         */
        public void setData8(int pos, int val) {
            for (int n = 0; n < getValidCount(); n++) {
                setData8(n, pos, val);
            }
        }

        /**
         * 8ビットデータをセット
         *
         * @param idx ミラーリング位置
         * @param pos 位置(8ビット1単位)
         * @param val 値
         */
        public void setData8(int idx, int pos, int val) {
            if (idx >= list.size()) return;

            List<DiskBasicFatBuffer> bufs = list.get(idx);
            DiskBasicFatBuffer.setData8(bufs, pos, val);
        }

        /**
         * 8ビットデータが一致するか
         *
         * @param pos 位置(8ビット1単位)
         * @param val 値
         * @return 一致した数（多重分）
         */
        public int matchData8(int pos, int val) {
            int matchCount = 0;
            for (int n = 0; n < getValidCount(); n++) {
                if (matchData8(n, pos, val)) matchCount++;
            }
            return matchCount;
        }

        /**
         * 8ビットデータが一致するか
         *
         * @param idx ミラーリング位置
         * @param pos 位置(8ビット1単位)
         * @param val 値
         * @return 一致した
         */
        public boolean matchData8(int idx, int pos, int val) {
            if (idx >= list.size()) return false;

            List<DiskBasicFatBuffer> bufs = list.get(idx);
            return DiskBasicFatBuffer.matchData8(bufs, pos, val);
        }

        /**
         * 8ビットデータのビットをセット/リセット
         *
         * @param idx    ミラーリング位置
         * @param pos    位置(8ビット1単位)
         * @param mask   対象のビット
         * @param val    セット/リセット
         * @param invert 反転するか
         */
        public void bitData8(int idx, int pos, byte mask, boolean val, boolean invert) {
            if (idx >= list.size()) return;

            List<DiskBasicFatBuffer> bufs = list.get(idx);
            DiskBasicFatBuffer.bitData8(bufs, pos, mask, val, invert);
        }

        /**
         * 12ビットデータ(リトルエンディアン)を返す
         *
         * @param idx ミラーリング位置
         * @param pos 位置(12ビット1単位)
         * @return 値
         */
        public int getData12LE(int idx, int pos) {
            int val = INVALID_GROUP_NUMBER;
            if (idx >= list.size()) return val;

            List<DiskBasicFatBuffer> bufs = list.get(idx);
            val = DiskBasicFatBuffer.getData12LE(bufs, pos);
            return val;
        }

        /**
         * 12ビットデータ(リトルエンディアン)をセット
         *
         * @param pos 位置(12ビット1単位)
         * @param val 値
         */
        public void setData12LE(int pos, int val) {
            for (int n = 0; n < getValidCount(); n++) {
                setData12LE(n, pos, val);
            }
        }

        /**
         * 12ビットデータ(リトルエンディアン)をセット
         *
         * @param idx ミラーリング位置
         * @param pos 位置(12ビット1単位)
         * @param val 値
         */
        public void setData12LE(int idx, int pos, int val) {
            if (idx >= list.size()) return;

            List<DiskBasicFatBuffer> bufs = list.get(idx);
            DiskBasicFatBuffer.setData12LE(bufs, pos, val);
        }

        /**
         * 16ビットデータ(リトルエンディアン)を返す
         *
         * @param idx ミラーリング位置
         * @param pos 位置(16ビット1単位)
         * @return 値
         */
        public int getData16LE(int idx, int pos) {
            int val = INVALID_GROUP_NUMBER;
            if (idx >= list.size()) return val;

            List<DiskBasicFatBuffer> bufs = list.get(idx);
            val = DiskBasicFatBuffer.getData16LE(bufs, pos);
            return val;
        }

        /**
         * 16ビットデータ(リトルエンディアン)をセット
         *
         * @param pos 位置(16ビット1単位)
         * @param val 値
         */
        public void setData16LE(int pos, int val) {
            for (int n = 0; n < getValidCount(); n++) {
                setData16LE(n, pos, val);
            }
        }

        /**
         * 16ビットデータ(リトルエンディアン)をセット
         *
         * @param idx ミラーリング位置
         * @param pos 位置(16ビット1単位)
         * @param val 値
         */
        public void setData16LE(int idx, int pos, int val) {
            if (idx >= list.size()) return;

            List<DiskBasicFatBuffer> bufs = list.get(idx);
            DiskBasicFatBuffer.setData16LE(bufs, pos, val);
        }

        /**
         * 16ビットデータ(ビッグエンディアン)を返す
         *
         * @param idx ミラーリング位置
         * @param pos 位置(16ビット1単位)
         * @return 値
         */
        public int getData16BE(int idx, int pos) {
            int val = INVALID_GROUP_NUMBER;
            if (idx >= list.size()) return val;

            List<DiskBasicFatBuffer> bufs = list.get(idx);
            val = DiskBasicFatBuffer.getData16BE(bufs, pos);
            return val;
        }

        /**
         * 16ビットデータ(ビッグエンディアン)をセット
         *
         * @param pos 位置(16ビット1単位)
         * @param val 値
         */
        public void setData16BE(int pos, int val) {
            for (int n = 0; n < getValidCount(); n++) {
                setData16BE(n, pos, val);
            }
        }

        /**
         * 16ビットデータ(ビッグエンディアン)をセット
         *
         * @param idx ミラーリング位置
         * @param pos 位置(16ビット1単位)
         * @param val 値
         */
        public void setData16BE(int idx, int pos, int val) {
            if (idx >= list.size()) return;

            List<DiskBasicFatBuffer> bufs = list.get(idx);
            DiskBasicFatBuffer.setData16BE(bufs, pos, val);
        }

        public int size() {
            return list.size();
        }

        public List<DiskBasicFatBuffer> get(int i) {
            return list.get(i);
        }
    }

    private DiskBasic basic;
    private DiskBasicType type;
    /** FATの数 */
    private int count;
    /** 使用しているFATの数 */
    private int vCount;
    /** FATサイズ(セクタ数) */
    private int size;
    /** 開始セクタ番号 */
    private int start;
    /** 開始位置 */
    private int startPos;

    private final DiskBasicFatArea bufs = new DiskBasicFatArea();

    public DiskBasicFat(DiskBasic basic) {
        this.basic = basic;
        type = null;
        clear();
    }

    /**
     * FATエリアをアサイン
     *
     * @param isFormatting フォーマット中か
     * @return 1.0: 正常, <1.0: 警告あり, <0.0: エラーあり
     */
    public double assign(boolean isFormatting) throws IOException {
        double validRatio = 1.0;

        int sectorNum = basic.getFatStartSector();
        int sideNum = basic.getReversedSideNumber(basic.getFatSideNumber());

        bufs.empty();

        type = basic.getType();

        if (sectorNum >= 0) {
            DiskImageTrack managed_track;
            int[] sideNums = new int[] {sideNum};
            int[] sectorNums = new int[] {sectorNum};

            if (sideNum >= 0) {
                // トラック、サイド番号から計算
                managed_track = basic.getTrack(basic.getManagedTrackNumber(), sideNum);
            } else {
                // セクタ番号の通し番号で計算
                managed_track = basic.getManagedTrack(basic.getReservedSectors(), sideNums, sectorNums);
            }
            if (managed_track == null) {
                return -1.0;
            }

            sideNum = sideNums[0];
            sectorNum = sectorNums[0];

            // セクタ位置を得る
            start = type.getSectorPosFromNum(basic.getManagedTrackNumber(), sideNum, sectorNum, 0, 1);

            count = basic.getNumberOfFats();
            size = basic.getSectorsPerFat();
            startPos = basic.getFatStartPos();
            vCount = basic.getValidNumberOfFats();
            if (vCount < 0) {
                vCount = count;
            }

            type.calcManagedStartGroup();

            // set buffer pointer for useful accessing
            int startSector = start;
            int endSector = start + size - 1;
            for (int fatNum = 0; fatNum < count && validRatio >= 0.0; fatNum++) {
                List<DiskBasicFatBuffer> fatBufs = new ArrayList<>();
                for (int secNum = startSector; secNum <= endSector; secNum++) {
                    int[] divNum = new int[] {0};
                    int[] divNums = new int[] {1};
                    DiskImageSector sector = basic.getSectorFromSectorPos(secNum, divNum, divNums);
                    if (sector == null) {
                        validRatio = -1.0;
                        break;
                    }

                    int sSize = sector.getSectorSize();
                    sSize /= divNums[0];
                    byte[] buf = sector.getSectorBuffer();
                    int offset = sSize * divNum[0];

//                    int sectorSize = sSize;
                    if (secNum == startSector) {
                        // 最初のセクタだけ開始位置がずれる
                        offset += startPos;
                        sSize -= startPos;
                    }
                    DiskBasicFatBuffer fatbuf = new DiskBasicFatBuffer(Arrays.copyOfRange(buf, offset, offset + sSize), sSize);
                    fatBufs.add(fatbuf);
                }
                bufs.add(fatBufs);

                startSector += size;
                endSector += size;
            }

            bufs.setValidCount(vCount);
        }

        if (validRatio >= 0.0) {
            validRatio = type.checkFat(isFormatting);
        }

        return validRatio;
    }

    /** FATエリアのアサインを解除 */
    public void clear() {
        count = 0;
        vCount = 0;
        size = 0;
        start = 0;
        startPos = 0;

        bufs.list.clear();
    }

    /** FATエリアのアサインを解除 */
    public void empty() {
        clear();
    }

    /**
     * FAT領域の最初のセクタの指定位置のデータを取得
     *
     * @param pos 位置
     */
    public int get(int pos) {
        int code = 0;
        DiskImageSector sector = basic.getSectorFromSectorPos(start);
        if (sector != null) {
            byte[] buf = sector.getSectorBuffer();
            int size = sector.getSectorBufferSize();
            if (buf != null && pos < size) {
                code = buf[pos] & 0xFF;
            }
        }
        return code;
    }

    /**
     * FAT領域の最初のセクタにデータを書く
     *
     * @param pos  位置
     * @param code コード
     */
    public void set(int pos, byte code) {
        int start_sector = start;
        for (int fat_num = 0; fat_num < vCount; fat_num++) {
            DiskImageSector sector = basic.getSectorFromSectorPos(start_sector);
            if (sector != null) {
                byte[] buf = sector.getSectorBuffer();
                int size = sector.getSectorBufferSize();
                if (buf != null && pos < size) {
                    buf[pos] = code;
                }
            }
            start_sector += size;
        }
    }

    /**
     * FAT領域の最初のセクタにデータを書く
     *
     * @param buf バッファ
     * @param len サイズ
     */
    public void copy(byte[] buf, int len) {
        int startSector = start;
        for (int fatNum = 0; fatNum < vCount; fatNum++) {
            DiskImageSector sector = basic.getSectorFromSectorPos(startSector);
            if (sector != null) {
                sector.copy(buf, len);
            }
            startSector += size;
        }
    }

    /**
     * FAT領域を指定コードで埋める
     *
     * @param code コード
     */
    public void fill(byte code) {
        int startSector = start;
        int endSector = start + size - 1;
        for (int fatNum = 0; fatNum < count; fatNum++) {
            for (int secNum = startSector; secNum <= endSector; secNum++) {
                DiskImageSector sector = basic.getSectorFromSectorPos(secNum);
                if (sector != null) {
                    sector.fill(code);
                }
            }
            startSector += size;
            endSector += size;
        }
    }

    /**
     * FAT領域を返す
     */
    public DiskBasicFatArea getDiskBasicFatArea() {
        return bufs;
    }

    /**
     * FATバッファを返す
     *
     * @param idx ミラーリングしているときのインデックス
     */
    public List<DiskBasicFatBuffer> getDiskBasicFatBuffers(int idx) {
        if (idx >= bufs.list.size()) {
            return null;
        }
        return bufs.list.get(idx);
    }

    /**
     * FATバッファ（セクタ）を返す
     *
     * @param idx    ミラーリングしているときのインデックス
     * @param subidx バッファ位置
     */
    public DiskBasicFatBuffer getDiskBasicFatBuffer(int idx, int subidx) {
        List<DiskBasicFatBuffer> fatBufs = getDiskBasicFatBuffers(idx);
        if (fatBufs == null || subidx >= fatBufs.size()) {
            return null;
        }
        return fatBufs.get(subidx);
    }
}
