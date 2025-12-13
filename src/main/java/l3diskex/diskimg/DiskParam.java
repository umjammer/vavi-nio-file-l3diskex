package l3diskex.diskimg;

import java.io.File;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import l3diskex.Parambase.TemplatesBase;
import l3diskex.Utils;
import org.xml.sax.SAXException;


public class DiskParam {

    private static final Logger logger = System.getLogger(DiskParam.class.getName());

    public static DiskTemplates diskTemplates = new DiskTemplates();

    /** トラック＆サイド＆セクタ番号を保持 */
    public static class SectorParam {

        protected int trackNum;
        protected int sideNum;
        protected int sectorNum;
        protected int sectorSize;

        /** トラック＆サイド＆セクタ番号を保持 */
        public SectorParam() {
            this.trackNum = -1;
            this.sideNum = -1;
            this.sectorNum = -1;
            this.sectorSize = -1;
        }

        /**
         * 番号を保持
         *
         * @param trackNum   トラック番号
         * @param sideNum    サイド番号
         * @param sectorNum  セクタ番号
         * @param sectorSize セクタサイズ
         */
        public SectorParam(int trackNum, int sideNum, int sectorNum, int sectorSize) {
            this.trackNum = trackNum;
            this.sideNum = sideNum;
            this.sectorNum = sectorNum;
            this.sectorSize = sectorSize;
        }

        /** 比較 */
        public boolean equals(SectorParam dst) {
            return (trackNum == dst.trackNum
                    && sideNum == dst.sideNum
                    && sectorNum == dst.sectorNum
                    && sectorSize == dst.sectorSize);
        }

        /**
         * 指定したトラック、サイドが特殊なセクタ（単密度など）か
         *
         * @param trackNum   トラック番号
         * @param sideNum    サイド番号
         * @param sectorNum  セクタ番号
         * @param sectorSize セクタサイズ、セクタサイズが <0 の時、条件から除外する
         */
        public boolean match(int trackNum, int sideNum, int sectorNum, int sectorSize) {
            return ((this.trackNum < 0 || (this.trackNum == trackNum))
                    && (this.sideNum < 0 || (this.sideNum == sideNum))
                    && (this.sectorNum < 0 || (this.sectorNum == sectorNum))
                    && (this.sectorSize < 0 || sectorSize < 0 || (this.sectorSize == sectorSize)));
        }

        public void setTrackNumber(int val) {
            trackNum = val;
        }

        public void setSideNumber(int val) {
            sideNum = val;
        }

        public void setSectorNumber(int val) {
            sectorNum = val;
        }

        public int getTrackNumber() {
            return trackNum;
        }

        public int getSideNumber() {
            return sideNum;
        }

        public int getSectorNumber() {
            return sectorNum;
        }
    }

    /** トラック＆サイド＆セクタ番号を保持 */
    public static class TrackParam extends SectorParam {

        public static final int ID_IS_VALID = 0x8000;

        protected int numOfTracks;
        protected int sectorsPerTrack;
        protected int[] id;

        /**
         * 単密度など特殊なトラックやセクタ情報を保持する
         */
        public TrackParam() {
            this.numOfTracks = 1;
            this.sectorsPerTrack = -1;
            this.id = new int[4];
        }

        /**
         * 特殊なトラックやセクタを登録
         *
         * @param trackNum        トラック番号 (-1: ALL)
         * @param sideNum         サイド番号 (-1: ALL)
         * @param sectorNum       セクタ番号 (特殊セクタ指定時のみ)
         * @param numOfTracks     トラック数 (特殊トラック指定時のみ)
         * @param sectorsPerTrack セクタ数 (特殊トラック指定時のみ)
         * @param sectorSize      セクタサイズ
         */
        public TrackParam(int trackNum, int sideNum, int sectorNum, int numOfTracks, int sectorsPerTrack, int sectorSize) {
            super(trackNum, sideNum, sectorNum, sectorSize);
            this.numOfTracks = numOfTracks;
            this.sectorsPerTrack = sectorsPerTrack;
            this.id = new int[4];
        }

        /** 比較 */
        public boolean equals(TrackParam dst) {
            return (trackNum == dst.trackNum
                    && sideNum == dst.sideNum
                    && sectorNum == dst.sectorNum
                    && numOfTracks == dst.numOfTracks
                    && (sectorsPerTrack < 0 || dst.sectorsPerTrack < 0 || sectorsPerTrack == dst.sectorsPerTrack)
                    && sectorSize == dst.sectorSize);
        }

        /** IDをセット */
        public void setID(int idx, int val) {
            if (idx >= 0 && idx < 4) {
                id[idx] = val;
            }
        }

        /** IDを返す */
        public int getID(int idx) {
            if (idx >= 0 && idx < 4) {
                return id[idx];
            } else {
                return 0;
            }
        }

        /** トラックの数を設定 */
        public void setNumberOfTracks(int val) {
            numOfTracks = val;
        }

        /** セクタの数を設定 */
        public void setSectorsPerTrack(int val) {
            sectorsPerTrack = val;
        }

        /** セクタサイズを設定 */
        public void setSectorSize(int val) {
            sectorSize = val;
        }

        /** トラックの数を返す */
        public int getNumberOfTracks() {
            return numOfTracks;
        }

        /** セクタの数を返す */
        public int getSectorsPerTrack() {
            return sectorsPerTrack;
        }

        /** セクタサイズを返す */
        public int getSectorSize() {
            return sectorSize;
        }

        /** IDを返す */
        public int[] getID() {
            return id;
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", TrackParam.class.getSimpleName() + "[", "]")
                    .add("numOfTracks=" + numOfTracks)
                    .add("sectorsPerTrack=" + sectorsPerTrack)
                    .add("id=" + Arrays.toString(id))
                    .toString();
        }
    }

    /** 単密度など特殊なトラックやセクタ情報を保持する */
    public static class DiskParticular extends TrackParam {

        private final List<TrackParam> excludes;

        /** 単密度など特殊なトラックやセクタ情報を保持する */
        public DiskParticular() {
            this.excludes = new ArrayList<>();
        }

        /**
         * 特殊なトラックやセクタを登録
         *
         * @param trackNum        トラック番号 (-1: ALL)
         * @param sideNum         サイド番号 (-1: ALL)
         * @param sectorNum       セクタ番号 (特殊セクタ指定時のみ)
         * @param numOfTracks     トラック数 (特殊トラック指定時のみ)
         * @param sectorsPerTrack セクタ数 (特殊トラック指定時のみ)
         * @param sectorSize      セクタサイズ
         */
        public DiskParticular(int trackNum, int sideNum, int sectorNum, int numOfTracks, int sectorsPerTrack, int sectorSize) {
            super(trackNum, sideNum, sectorNum, numOfTracks, sectorsPerTrack, sectorSize);
            this.excludes = new ArrayList<>();
        }

        /**
         * 除外するトラックをリストに追加
         *
         * @param param パラメータ
         */
        public void addExclude(TrackParam param) {
            excludes.add(param);
        }

        /**
         * トラックが除外リストに含まれるか
         *
         * @param trackNum トラック番号
         * @param sideNum  サイド番号
         */
        public boolean findExclude(int trackNum, int sideNum) {
            TrackParam match = null;
            for (TrackParam item : excludes) {
                if (trackNum != item.getTrackNumber()) continue;
                if (sideNum != item.getSideNumber()) continue;
                match = item;
                break;
            }
            return match != null;
        }

        /**
         * 同じセクタのものをまとめる
         *
         * @param sectors セクタ数
         * @param result  [in,out] リスト
         */
        public static void uniqueSectors(int sectors, List<DiskParticular> result) {
            int count = result.size();
            if (count <= 1) return;

            if (sectors == count) {
                List<DiskParticular> newSd = new ArrayList<>();
                DiskParticular sd = result.getFirst();
                newSd.add(new DiskParticular(sd.getTrackNumber(), sd.getSideNumber(), -1, sd.getNumberOfTracks(), sd.getSectorsPerTrack(), sd.getSectorSize()));
                result.clear();
                result.addAll(newSd);
            }
        }

        /**
         * 同じトラックやサイドのものをまとめる
         *
         * @param tracks    トラック数
         * @param sides     サイド数
         * @param bothSides 両面タイプか
         * @param result    [in,out] リスト
         */
        public static void uniqueTracks(int tracks, int sides, boolean bothSides, List<DiskParticular> result) {
            int count = result.size();
            if (count <= 1) return;

            List<DiskParticular> newSd = new ArrayList<>();
            DiskParticular prevSd;

            prevSd = result.getFirst();
            // 同じトラック番号で全サイドが同じパラメータ(単密度)であればまとめる
            int sideCount = 1;
            boolean allSides = true;
            for (int idx = 1; idx <= count; idx++) {
                DiskParticular sd = idx < count ? result.get(idx) : null;
                if (prevSd.getSectorNumber() >= 0) {
                    newSd.add(prevSd);
                    allSides = false;
                    sideCount = 0;
                } else if (sd == null || prevSd.getTrackNumber() != sd.getTrackNumber()) {
                    if (sideCount >= sides || bothSides) {
                        newSd.add(new DiskParticular(prevSd.getTrackNumber(), -1, -1, prevSd.getNumberOfTracks(), prevSd.getSectorsPerTrack(), prevSd.getSectorSize()));
                    } else {
                        newSd.add(new DiskParticular(prevSd.getTrackNumber(), prevSd.getSideNumber(), -1, prevSd.getNumberOfTracks(), prevSd.getSectorsPerTrack(), prevSd.getSectorSize()));
                        allSides = false;
                    }
                    sideCount = 0;
                }
                sideCount++;
                prevSd = sd;
            }
            result.clear();
            result.addAll(newSd);

            count = result.size();
            newSd.clear();
            // 全トラックが同じパラメータ(単密度)であればまとめる
            if (allSides && count >= tracks) {
                DiskParticular sd = result.getFirst();
                newSd.add(new DiskParticular(-1, -1, -1, sd.getNumberOfTracks(), sd.getSectorsPerTrack(), sd.getSectorSize()));
                result.clear();
                result.addAll(newSd);
            }
        }

        //
        // DiskParticulars
        //

        /** セクタ/トラックのリスト内で最小値を返す */
        public static int getMinSectorsPerTrack(List<DiskParticular> list, int defaultNumber) {
            int val = 0xffff;
            for (DiskParticular diskParticular : list) {
                if (diskParticular.getSectorsPerTrack() < val) {
                    val = diskParticular.getSectorsPerTrack();
                }
            }
            if (defaultNumber < val) {
                val = defaultNumber;
            }
            return val;
        }

        /** セクタ/トラックのリスト内で最大値を返す */
        public static int getMaxSectorsPerTrack(List<DiskParticular> list, int defaultNumber) {
            int val = 0;
            for (DiskParticular diskParticular : list) {
                if (diskParticular.getSectorsPerTrack() > val) {
                    val = diskParticular.getSectorsPerTrack();
                }
            }
            if (defaultNumber > val) {
                val = defaultNumber;
            }
            return val;
        }

        /** 全ての値が一致するか */
        public static boolean equals(List<DiskParticular> list, List<DiskParticular> dst) {
            boolean match = true;
            if (list.size() != dst.size()) {
                match = false;
            } else {
                for (DiskParticular ds : dst) {
                    boolean sm = false;
                    for (DiskParticular ss : list) {
                        if (ss.equals(ds)) {
                            sm = true;
                            break;
                        }
                    }
                    if (!sm) {
                        match = false;
                        break;
                    }
                }
            }
            return match;
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", DiskParticular.class.getSimpleName() + "[", "]")
                    .add("excludes=" + excludes)
                    .toString();
        }
    }

    /** 各トラックのセクタ数を保持 */
    public static class NumSectorsParam {

        protected int startTrackNum;
        protected int numOfTracks;
        protected int sectorsPerTrack;

        /**
         * // 各トラックのセクタ数を保持
         */
        public NumSectorsParam() {
            this.startTrackNum = 0;
            this.numOfTracks = 0;
            this.sectorsPerTrack = 1;
        }

        /**
         * @param startTrackNum   開始トラック番号
         * @param numOfTracks     トラック数
         * @param sectorsPerTrack セクタ数/トラック
         */
        public NumSectorsParam(int startTrackNum, int numOfTracks, int sectorsPerTrack) {
            this.startTrackNum = startTrackNum;
            this.numOfTracks = numOfTracks;
            this.sectorsPerTrack = sectorsPerTrack;
        }

        /** 開始トラック番号を設定 */
        public void setStartTrackNumber(int val) {
            startTrackNum = val;
        }

        /** トラック数を設定 */
        public void setNumberOfTracks(int val) {
            numOfTracks = val;
        }

        /** セクタ数/トラックを設定 */
        public void setSectorsPerTrack(int val) {
            sectorsPerTrack = val;
        }

        /** 開始トラック番号を返す */
        public int getStartTrackNumber() {
            return startTrackNum;
        }

        /** トラック数を返す */
        public int getNumberOfTracks() {
            return numOfTracks;
        }

        /** セクタ数/トラックを返す */
        public int getSectorsPerTrack() {
            return sectorsPerTrack;
        }

        //
        // NumSectorsParams
        //

        /** セクタ/トラックのリスト内の最小値を返す */
        public static int getMinSectorOfTracks(List<NumSectorsParam> list) {
            int val = 0xffff;
            for (NumSectorsParam numSectorsParam : list) {
                if (numSectorsParam.getSectorsPerTrack() < val) {
                    val = numSectorsParam.getSectorsPerTrack();
                }
            }
            return val;
        }

        /** リスト内でセクタ数の最大値を返す */
        public static int getMaxSectorOfTracks(List<NumSectorsParam> list) {
            int val = 0;
            for (NumSectorsParam numSectorsParam : list) {
                if (numSectorsParam.getSectorsPerTrack() > val) {
                    val = numSectorsParam.getSectorsPerTrack();
                }
            }
            return val;
        }
    }

    /** DISK BASIC 名前リストを保存 */
    public static class DiskParamName {

        private String name;
        private int flags;

        public DiskParamName() {
            this.flags = 0;
        }

        /** 名前を設定 */
        public void setName(String val) {
            name = val;
        }

        /** 名前を返す */
        public String getName() {
            return name;
        }

        /** フラグを設定 */
        public void setFlags(int val) {
            flags = val;
        }

        /** フラグを返す */
        public int getFlags() {
            return flags;
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", DiskParamName.class.getSimpleName() + "[", "]")
                    .add("name='" + name + "'")
                    .add("flags=" + flags)
                    .toString();
        }
    }

    /**
     * インターリーブ/セクタスキュー
     *
     * マップがないときは、間隔から計算する。
     */
    public static class SectorInterleave {

        /** 固有のマップを使用する場合 true */
        private boolean hasMap;
        /** 変換後のセクタ番号 */
        private List<Integer> secs;

        public SectorInterleave() {
            this.hasMap = false;
            this.secs = new ArrayList<>();
            this.secs.add(0);
        }

        /** インターリーブ間隔を返す */
        public int get() {
            return secs.getFirst();
        }

        /** インターリーブマップを返す */
        public int get(int idx) {
            if (idx < secs.size()) {
                return secs.get(idx);
            } else {
                return 0;
            }
        }

        /** インターリーブ間隔を設定 */
        public void set(int val) {
            hasMap = false;
            secs.set(0, val);
        }

        /** インターリーブマップを設定 */
        public void set(List<Integer> val) {
            hasMap = true;
            secs = new ArrayList<>(val);
        }

        /** 固有のマップを持っているか */
        public boolean hasMap() {
            return hasMap;
        }
    }

    /** ディスク種類名 "2D", "2HD" など */
    protected String diskTypeName;
    /** BASIC種類（DiskBasicParamとのマッチングにも使用） */
    protected List<DiskParamName> basicTypes;
    /** 裏返し可能 AB面あり（L3用3インチFDなど） */
    protected boolean reversible;
    /** サイド数 */
    protected int sidesPerDisk;
    /** トラック数 */
    protected int tracksPerSide;
    /** セクタ数 */
    protected int sectorsPerTrack;
    /** セクタサイズ */
    protected int sectorSize;
    /** セクタ番号の付番方法 (0: サイド毎, 1: トラック毎) */
    protected int numberingSector;
    /** 0x00: 2D, 0x10: 2DD, 0x20: 2HD */
    protected int diskDensity;
    /** セクタの間隔 */
    protected int interleave;
    /** 開始トラック番号 */
    protected int trackNumberBase;
    /** 開始サイド番号 */
    protected int sideNumberBase;
    /** 開始セクタ番号 */
    protected int sectorNumberBase;
    /** セクタ数がトラックごとに異なる */
    protected boolean variableSecsPerTrack;
    /** 単密度にするトラック */
    protected List<DiskParticular> singles;
    /** 特殊なトラックを定義 */
    protected List<DiskParticular> pTracks;
    /** 特殊なセクタを定義 */
    protected List<DiskParticular> pSectors;
    /** 密度情報（説明用） */
    protected String densityName;
    /** 説明 */
    protected String description;

    public DiskParam() {
        this.clearDiskParam();
    }

    public DiskParam(DiskParam src) {
        this.setDiskParam(src);
    }

    public void setDiskParam(DiskParam src) {
        this.diskTypeName = src.diskTypeName;
        this.basicTypes = new ArrayList<>(src.basicTypes);
        this.reversible = src.reversible;
        this.sidesPerDisk = src.sidesPerDisk;
        this.tracksPerSide = src.tracksPerSide;
        this.sectorsPerTrack = src.sectorsPerTrack;
        this.sectorSize = src.sectorSize;
if (sectorSize == 0) {
 new Exception("sectorSize: " + sectorSize).printStackTrace();
}
        this.numberingSector = src.numberingSector;
        this.diskDensity = src.diskDensity;
        this.interleave = src.interleave;
        this.trackNumberBase = src.trackNumberBase;
        this.sideNumberBase = src.sideNumberBase;
        this.sectorNumberBase = src.sectorNumberBase;
        this.variableSecsPerTrack = src.variableSecsPerTrack;
        this.singles = new ArrayList<>();
        this.singles.addAll(src.singles);
        this.pTracks = new ArrayList<>();
        this.pTracks.addAll(src.pTracks);
        this.pSectors = new ArrayList<>();
        this.pSectors.addAll(src.pSectors);
        this.densityName = src.densityName;
        this.description = src.description;
    }

    /**
     * 全パラメータを設定
     *
     * @param typeName             ディスク種類名 "2D" "2HD" など
     * @param basicTypes           BASIC種類（DiskBasicParamとのマッチングにも使用）
     * @param reversible           裏返し可能 AB面あり（L3用3インチFDなど）
     * @param sidesPerDisk         サイド数
     * @param tracksPerSide        トラック数
     * @param sectorsPerTrack      セクタ数
     * @param sector_size          セクタサイズ
     * @param numberingSector      セクタ番号の付番方法(0:サイド毎、1:トラック毎)
     * @param disk_density         0x00:2D 0x10:2DD 0x20:2HD
     * @param interleave           セクタの間隔
     * @param trackNumberBase      開始トラック番号
     * @param sideNumberBase       開始サイド番号
     * @param sectorNumberBase     開始セクタ番号
     * @param variableSecsPerTrack セクタ数がトラックごとに異なるか
     * @param singles              単密度にするトラック
     * @param pTracks              特殊なトラックを定義 セクタ数がトラックごとに異なる場合
     * @param pSectors             特殊なセクタを定義
     * @param densityName          密度情報（説明用）
     * @param desc                 説明
     */
    public void setDiskParam(String typeName, List<DiskParamName> basicTypes, boolean reversible,
                             int sidesPerDisk, int tracksPerSide, int sectorsPerTrack, int sector_size,
                             int numberingSector, int disk_density, int interleave, int trackNumberBase,
                             int sideNumberBase, int sectorNumberBase, boolean variableSecsPerTrack,
                             List<DiskParticular> singles, List<DiskParticular> pTracks, List<DiskParticular> pSectors,
                             String densityName, String desc) {
        this.diskTypeName = typeName;
        this.basicTypes = new ArrayList<>(basicTypes);
        this.reversible = reversible;
        this.sidesPerDisk = sidesPerDisk;
        this.tracksPerSide = tracksPerSide;
        this.sectorsPerTrack = sectorsPerTrack;
        this.sectorSize = sector_size;
        this.numberingSector = numberingSector;
        this.diskDensity = disk_density;
        this.interleave = interleave;
        this.trackNumberBase = trackNumberBase;
        this.sideNumberBase = sideNumberBase;
        this.sectorNumberBase = sectorNumberBase;
        this.variableSecsPerTrack = variableSecsPerTrack;
        this.singles = new ArrayList<>();
        this.singles.addAll(singles);
        this.pTracks = new ArrayList<>();
        this.pTracks.addAll(pTracks);
        this.pSectors = new ArrayList<>();
        this.pSectors.addAll(pSectors);
        this.densityName = densityName;
        this.description = desc;
    }

    /**
     * 主要パラメータだけ設定
     *
     * @param sidesPerDisk    サイド数
     * @param tracksPerSide   トラック数
     * @param sectorsPerTrack セクタ数
     * @param sectorSize      セクタサイズ
     * @param diskDensity     0x00: 2D, 0x10: 2DD, 0x20: 2HD
     * @param interleave      セクタの間隔
     * @param singles         単密度にするトラック
     * @param pTracks         特殊なトラックを定義 セクタ数がトラックごとに異なる場合
     */
    public void setDiskParam(int sidesPerDisk, int tracksPerSide, int sectorsPerTrack,
                             int sectorSize, int diskDensity, int interleave,
                             List<DiskParticular> singles, List<DiskParticular> pTracks) {
        this.sidesPerDisk = sidesPerDisk;
        this.tracksPerSide = tracksPerSide;
        this.sectorsPerTrack = sectorsPerTrack;
        this.sectorSize = sectorSize;
        this.diskDensity = diskDensity;
        this.interleave = interleave;
        this.singles = new ArrayList<>();
        this.singles.addAll(singles);
        this.pTracks = new ArrayList<>();
        this.pTracks.addAll(pTracks);
    }

    /** 初期化 */
    public void clearDiskParam() {
        this.diskTypeName = "";
        this.basicTypes = new ArrayList<>();
        this.reversible = false;
        this.sidesPerDisk = 0;
        this.tracksPerSide = 0;
        this.sectorsPerTrack = 0;
        this.sectorSize = 0;
        this.numberingSector = 0;
        this.diskDensity = 0;
        this.interleave = 1;
        this.trackNumberBase = 0;
        this.sideNumberBase = 0;
        this.sectorNumberBase = 1;
        this.variableSecsPerTrack = false;
        this.singles = new ArrayList<>();
        this.pTracks = new ArrayList<>();
        this.pSectors = new ArrayList<>();
        this.densityName = "";
        this.description = "";
    }

    /**
     * 指定したパラメータで一致するものがあるか
     *
     * @param sidesPerDisk     サイド/ディスク
     * @param tracksPerSide    トラック/サイド
     * @param sectorsPerTrack  セクタ/トラック
     * @param sectorSize       セクタサイズ
     * @param interleave       インターリーブ
     * @param trackNumberBase  開始トラック番号
     * @param sideNumberBase   開始サイド番号
     * @param sectorNumberBase 開始セクタ番号
     * @param numberingSector  連番セクタか
     * @param singles          単密度
     * @param pTracks          特殊なトラック
     * @return true: 一致する
     */
    public boolean match(int sidesPerDisk, int tracksPerSide, int sectorsPerTrack, int sectorSize,
                         int interleave, int trackNumberBase, int sideNumberBase, int sectorNumberBase,
                         int numberingSector, List<DiskParticular> singles, List<DiskParticular> pTracks) {
        boolean match = (this.sidesPerDisk == sidesPerDisk)
                && (this.tracksPerSide == tracksPerSide)
                && (this.sectorsPerTrack == sectorsPerTrack)
                && (this.sectorSize == sectorSize)
                && (this.interleave == interleave)
                && (this.trackNumberBase == trackNumberBase)
                && (this.sideNumberBase == sideNumberBase)
                && (this.sectorNumberBase == sectorNumberBase)
                && (this.numberingSector == numberingSector)
                && (this.singles.equals(singles))
                && (this.pTracks.equals(pTracks));
        return match;
    }

    /**
     * 指定したパラメータで一致するものがあるか
     *
     * @param sidesPerDisk    サイド/ディスク
     * @param tracksPerSide   トラック/サイド
     * @param sectorsPerTrack セクタ/トラック
     * @param sectorSize      セクタサイズ
     * @return true: 一致する
     */
    public boolean match(int sidesPerDisk, int tracksPerSide, int sectorsPerTrack, int sectorSize) {
        boolean match = (this.sidesPerDisk == sidesPerDisk)
                && (this.tracksPerSide == tracksPerSide)
                && (this.sectorsPerTrack == sectorsPerTrack)
                && (this.sectorSize == sectorSize);
        return match;
    }

    /**
     * 指定したパラメータで一致するものがあるか
     *
     * @param param パラメータ
     * @return true: 一致する
     */
    public boolean match(DiskParam param) {
        boolean match = (diskTypeName.equals(param.diskTypeName))
                && (reversible == param.reversible)
                && (sidesPerDisk == param.sidesPerDisk)
                && (tracksPerSide == param.tracksPerSide)
                && (sectorsPerTrack == param.sectorsPerTrack)
                && (sectorSize == param.sectorSize)
                && (interleave == param.interleave)
                && (trackNumberBase == param.trackNumberBase)
                && (sideNumberBase == param.sideNumberBase)
                && (sectorNumberBase == param.sectorNumberBase)
                && (numberingSector == param.numberingSector)
                && (singles.equals(param.singles));
        return match;
    }

    /**
     * 指定したパラメータで一致するものがあるか
     *
     * @param param パラメータ
     * @return true: 一致する
     */
    public boolean matchExceptName(DiskParam param) {
        boolean match = (reversible == param.reversible)
                && (sidesPerDisk == param.sidesPerDisk)
                && (tracksPerSide == param.tracksPerSide)
                && (sectorsPerTrack == param.sectorsPerTrack)
                && (sectorSize == param.sectorSize)
                && (interleave == param.interleave)
                && (trackNumberBase == param.trackNumberBase)
                && (sideNumberBase == param.sideNumberBase)
                && (sectorNumberBase == param.sectorNumberBase)
                && (numberingSector == param.numberingSector)
                && (singles.equals(param.singles))
                && (pTracks.equals(param.pTracks));
        return match;
    }

    /**
     * 指定したパラメータに近い値で一致するものがあるか
     *
     * @param num             フェーズ番号
     * @param sidesPerDisk    サイド/ディスク
     * @param tracksPerSide   トラック/サイド
     * @param sectorsPerTrack セクタ/トラック
     * @param sectorSize      セクタサイズ
     * @param interleave      インターリーブ
     * @param numberingSector 連番セクタか
     * @param singles         単密度
     * @param last            [out] 検索終わり
     * @return true: 一致する
     */
    public boolean matchNear(int num, int sidesPerDisk, int tracksPerSide, int sectorsPerTrack,
                             int sectorSize, int interleave, int numberingSector,
                             List<DiskParticular> singles, boolean[] last) {
        boolean match = false;
        switch (num) {
            case 0:
                // 特殊なトラックを除いて比較
                match = (this.sidesPerDisk == sidesPerDisk) // サイド数は一致
                        && (this.tracksPerSide == tracksPerSide)
                        && (this.sectorsPerTrack == sectorsPerTrack)
                        && (this.sectorSize == sectorSize)
                        && (this.interleave == interleave)
                        && (this.numberingSector == numberingSector)
                        && (this.singles.equals(singles));
                break;
            case 1:
                // compare without interleave
                match = (this.sidesPerDisk == sidesPerDisk) // サイド数は一致
                        && (this.tracksPerSide == tracksPerSide) // トラック数は一致
                        && (this.sectorsPerTrack == sectorsPerTrack) // セクタ数は一致
                        && (this.sectorSize == sectorSize) // セクタサイズは一致
                        && (this.numberingSector == numberingSector)    // セクタ番号の付番方法は一致
                        && (this.singles.equals(singles));    // 単密度のトラックは一致
                break;
            case 2:
                // インターリーブを入れて
                // トラック数が指定範囲内で比較
                match = (this.sidesPerDisk == sidesPerDisk) // サイド数は一致
                        && (this.sectorsPerTrack == sectorsPerTrack) // セクタ数は一致
                        && (this.sectorSize == sectorSize) // セクタサイズは一致
                        && (this.numberingSector == numberingSector)    // セクタ番号の付番方法は一致
                        && (this.singles.equals(singles))    // 単密度のトラックは一致
                        && (this.interleave == interleave) // インターリーブは一致
                        && ((tracksPerSide - 5) <= this.tracksPerSide && this.tracksPerSide <= tracksPerSide); // トラック数は-5 - 0の範囲
                break;
            case 3:
                // インターリーブを除いて、
                // トラック数が指定範囲内で比較
                match = (this.sidesPerDisk == sidesPerDisk) // サイド数は一致
                        && (this.sectorsPerTrack == sectorsPerTrack) // セクタ数は一致
                        && (this.sectorSize == sectorSize) // セクタサイズは一致
                        && (this.numberingSector == numberingSector) // セクタ番号の付番方法は一致
                        && (this.singles.equals(singles)) // 単密度のトラックは一致
                        && ((tracksPerSide - 5) <= this.tracksPerSide && this.tracksPerSide <= tracksPerSide); // トラック数は-5 - 0の範囲
                break;
            case 4:
                match = (this.sidesPerDisk == sidesPerDisk) // サイド数は一致
                        && (this.sectorsPerTrack == sectorsPerTrack) // セクタ数は一致
                        && (this.sectorSize == sectorSize) // セクタサイズは一致
                        && (this.numberingSector == numberingSector) // セクタ番号の付番方法は一致
                        && ((tracksPerSide - 5) <= this.tracksPerSide && this.tracksPerSide <= tracksPerSide); // トラック数は-5 - 0の範囲
                break;
            case 5:
                match = (this.sidesPerDisk == sidesPerDisk) // サイド数は一致
                        && (this.sectorSize == sectorSize) // セクタサイズは一致
                        && (this.numberingSector == numberingSector) // セクタ番号の付番方法は一致
                        && ((tracksPerSide - 5) <= this.tracksPerSide && this.tracksPerSide <= tracksPerSide) // トラック数は-5 - 0の範囲
                        && (this.sectorsPerTrack <= sectorsPerTrack); // セクタ数は小さければよし
                break;
            default:
                last[0] = true;
                break;
        }
        return match;
    }

    /**
     * 指定したトラック、サイドが単密度か
     *
     * @param trackNum        トラック番号
     * @param sideNum         サイド番号
     * @param sectorsPerTrack [out] セクタ数
     * @param sectorSize      [out] セクタサイズ
     * @return true, false
     */
    public boolean findSingleDensity(int trackNum, int sideNum, int[] sectorsPerTrack, int[] sectorSize) {
        boolean match = false;
        for (DiskParticular sd : singles) {
            if (sd.match(trackNum, sideNum, -1, -1)) {
                match = true;
                if (sectorsPerTrack != null && sd.getSectorsPerTrack() > 0) {
                    sectorsPerTrack[0] = sd.getSectorsPerTrack();
                }
                if (sectorSize != null && sd.getSectorSize() > 0) {
                    sectorSize[0] = sd.getSectorSize();
                }
                break;
            }
        }
        return match;
    }

    /**
     * 指定したトラック、サイド、セクタが単密度か
     *
     * @param trackNum   トラック番号
     * @param sideNum    サイド番号
     * @param sectorNum  セクタ番号
     * @param sectorSize セクタサイズ
     * @return true, false
     */
    public boolean findSingleDensity(int trackNum, int sideNum, int sectorNum, int sectorSize) {
        boolean match = false;
        for (DiskParticular sd : singles) {
            if (sd.match(trackNum, sideNum, sectorNum, sectorSize)) {
                match = true;
                break;
            }
        }
        return match;
    }

    /**
     * 単密度を持っているか
     *
     * @param sectorsPerTrack [out] セクタ数
     * @param sectorSize      [out] セクタサイズ
     * @return 0: なし, 1: 全トラック, 2: トラック0,サイド0, 3: トラック0,両面
     */
    public int hasSingleDensity(int[] sectorsPerTrack /* = null */, int[] sectorSize /* = null */) {
        int val = 0;
        DiskParticular sd = null;
        for (DiskParticular single : singles) {
            if (single.getTrackNumber() < 0 && single.getSideNumber() < 0) {
                sd = single;
                val = 1;
                break;
            } else if (single.getTrackNumber() == 0 && single.getSideNumber() < 0) {
                sd = single;
                val = 3;
                break;
            } else if (single.getTrackNumber() == 0 && single.getSideNumber() == 0) {
                sd = single;
                val = 2;
                break;
            }
        }
        int maxTracks = getTracksPerSide() * getSidesPerDisk();
        if (maxTracks > 0 && maxTracks == singles.size()) {
            sd = singles.getFirst();
            val = 1;
        }
        if (val > 0) {
            if (sectorsPerTrack != null) {
                sectorsPerTrack[0] = sd.getSectorsPerTrack();
                if (sectorsPerTrack[0] < 0) {
                    sectorsPerTrack[0] = getSectorsPerTrack();
                }
            }
            if (sectorSize != null) {
                sectorSize[0] = sd.getSectorSize();
            }
        }
        return val;
    }

    /** ディスクサイズを計算する（ベタディスク用） */
    public int calcDiskSize() {
        int diskSize = 0;
        int track = getTrackNumberBaseOnDisk();
        int tracks = getTracksPerSide() + track;
        for (; track < tracks; track++) {
            for (int sid = 0; sid < getSidesPerDisk(); sid++) {
                int[] secNums = {getSectorsPerTrack()};
                int[] secSize = {getSectorSize()};
                findParticularTrack(track, sid, secNums, secSize);
                findSingleDensity(track, sid, secNums, secSize);

                for (int sec = 0; sec < secNums[0]; sec++) {
                    findParticularSector(track, sid, sec, secSize, null);
                    diskSize += secSize[0];
                }
            }
        }
        return diskSize;
    }

    /**
     * 特殊なトラックか
     *
     * @param trackNum        トラック番号
     * @param sideNum         サイド番号
     * @param sectorsPerTrack セクタ/トラック
     * @param sectorSize      セクタサイズ
     * @return true, false
     */
    public boolean findParticularTrack(int trackNum, int sideNum, int[] sectorsPerTrack, int[] sectorSize) {
        DiskParticular match = null;
        for (DiskParam.DiskParticular pt : pTracks) {
            if (pt.getSectorsPerTrack() <= 0) continue;
            if (pt.getSectorNumber() >= 0) continue;
            if (trackNum < pt.getTrackNumber() || (pt.getTrackNumber() + pt.getNumberOfTracks()) <= trackNum)
                continue;
            if (pt.getSideNumber() >= 0 && pt.getSideNumber() != sideNum) continue;
            if (pt.findExclude(trackNum, sideNum)) continue;

            match = pt;
            sectorsPerTrack[0] = pt.getSectorsPerTrack();
            if (pt.getSectorSize() > 0) {
                sectorSize[0] = pt.getSectorSize();
            }
            break;
        }
        return (match != null);
    }

    /**
     * 特殊なセクタか
     *
     * @param trackNum   トラック番号
     * @param sideNum    サイド番号
     * @param sectorNum  セクタ番号
     * @param sectorSize セクタサイズ
     * @param sectorId   [out] C,H,R,Nの入った配列を返す
     * @return true, false
     */
    public boolean findParticularSector(int trackNum, int sideNum, int sectorNum, int[] sectorSize, int[][] sectorId) {
        DiskParticular match = null;
        for (DiskParticular ps : pSectors) {
            if (ps.getSectorSize() <= 0) continue;
            if (ps.getSectorsPerTrack() >= 0) continue;
            if (ps.getSectorNumber() >= 0 && ps.getSectorNumber() != sectorNum) continue;
            if (ps.getSideNumber() >= 0 && ps.getSideNumber() != sideNum) continue;
            if (ps.getTrackNumber() >= 0 && ps.getTrackNumber() != trackNum) continue;
            if (ps.findExclude(trackNum, sideNum)) continue;

            match = ps;
            sectorSize[0] = ps.getSectorSize();
            if (sectorId != null) {
                sectorId[0] = ps.getID();
            }
            break;
        }
        return match != null;
    }

    /**
     * DISK BASICをさがす
     *
     * @param typeName タイプ名
     * @param flags    フラグ
     * @return 名前
     */
    public DiskParamName findBasicType(String typeName, int flags) {
        DiskParamName match = null;
        for (DiskParamName item : basicTypes) {
            if (item.getName().equals(typeName) && (flags < 0 || item.getFlags() == flags)) {
                match = item;
                break;
            }
        }
        return match;
    }

    /**
     * ディスクパラメータを文字列にフォーマットして返す
     *
     * @return 文字列
     */
    public String getDiskDescription() {
        StringBuilder str = new StringBuilder();

        if (!densityName.isEmpty()) {
            str.append(densityName);
            str.append("  ");
        }
        String format = "%d";
        format += tracksPerSide <= 1 ? "track" : "tracks";
        format += " ";
        format += "%d";
        format += sidesPerDisk <= 1 ? "side" : "sides";
        format += " ";
        if (variableSecsPerTrack) {
            format += "%d-%d";
            format += "sectors";
        } else {
            format += "%d";
            format += sectorsPerTrack <= 1 ? "sector" : "sectors";
        }
        format += " ";
        format += "%d";
        format += "bytes/sector";
        format += " ";
        format += "Interleave:";
        format += "%d";

        if (variableSecsPerTrack) {
            int minSectors = DiskParticular.getMinSectorsPerTrack(pTracks, sectorsPerTrack);
            int maxSectors = DiskParticular.getMaxSectorsPerTrack(pTracks, sectorsPerTrack);
            str.append(String.format(format, tracksPerSide, sidesPerDisk, minSectors, maxSectors, sectorSize, interleave));
        } else {
            str.append(String.format(format, tracksPerSide, sidesPerDisk, sectorsPerTrack, sectorSize, interleave));
        }

        if (!description.isEmpty()) {
            str.append(" ");
            str.append(description);
        }
        return str.toString();
    }

    /** ディスク種類名を設定 "2D" "2HD" など */
    public void setDiskTypeName(String str) {
        diskTypeName = str;
    }

    /** BASIC種類を設定 */
    public void setBasicTypes(List<DiskParamName> arr) {
        basicTypes = new ArrayList<>(arr);
    }

    /** 裏返し可能 AB面ありかどうかを設定 */
    public void setReversible(boolean val) {
        reversible = val;
    }

    /** サイド数を設定 */
    public void setSidesPerDisk(int val) {
        sidesPerDisk = val;
    }

    /** トラック数を設定 */
    public void setTracksPerSide(int val) {
        tracksPerSide = val;
    }

    /** セクタ数を設定 */
    public void setSectorsPerTrack(int val) {
        sectorsPerTrack = val;
    }

    /** セクタサイズを設定 */
    public void setSectorSize(int val) {
        if (val == 0) {
            logger.log(Level.WARNING, "sectorSize = 0", new Exception("sectorSize = 0"));
        }
        sectorSize = val;
    }

    /** セクタ番号の付番方法 (0: サイド毎, 1: トラック毎) を設定 */
    public void setNumberingSector(int val) {
        numberingSector = val;
    }

    /** 密度 (0x00: 2D, 0x10: 2DD, 0x20: 2HD) を設定 */
    public void setParamDensity(int val) {
        diskDensity = val;
    }

    /** セクタの間隔を設定 */
    public void setInterleave(int val) {
        interleave = val;
    }

    /** 開始トラック番号を設定 */
    public void setTrackNumberBaseOnDisk(int val) {
        trackNumberBase = val;
    }

    /** 開始サイド番号を設定 */
    public void setSideNumberBaseOnDisk(int val) {
        sideNumberBase = val;
    }

    /** 開始セクタ番号を設定 */
    public void setSectorNumberBaseOnDisk(int val) {
        sectorNumberBase = val;
    }

    /** セクタ数がトラックごとに異なる */
    public void setVariableSectorsPerTrack(boolean val) {
        variableSecsPerTrack = val;
    }

    /** 単密度にするトラックを設定 */
    public void setSingles(List<DiskParticular> arr) {
        singles = new ArrayList<>();
        singles.addAll(arr);
    }

    /** 特殊なトラックを設定 */
    public void setParticularTracks(List<DiskParticular> arr) {
        pTracks = new ArrayList<>();
        pTracks.addAll(arr);
    }

    /** 特殊なセクタを設定 */
    public void setParticularSectors(List<DiskParticular> arr) {
        pSectors = new ArrayList<>();
        pSectors.addAll(arr);
    }

    /** 密度情報（説明用）を設定 */
    public void setDensityName(String str) {
        densityName = str;
    }

    /** 説明を設定 */
    public void setDescription(String str) {
        description = str;
    }

    /** 単密度にするトラックを追加 */
    public void addSingleDensity(DiskParticular val) {
        singles.add(val);
    }

    /** 特殊なトラックを追加 */
    public void addParticularTrack(DiskParticular val) {
        pTracks.add(val);
    }

    /** 特殊なセクタを追加 */
    public void addParticularSector(DiskParticular val) {
        pSectors.add(val);
    }

    /** ディスク種類名を返す "2D" "2HD" など */
    public String getDiskTypeName() {
        return diskTypeName;
    }

    /** BASIC種類を返す */
    public List<DiskParamName> getBasicTypes() {
        return basicTypes;
    }

    /** 裏返し可能 AB面ありかどうかを返す */
    public boolean isReversible() {
        return reversible;
    }

    /** サイド数を返す */
    public int getSidesPerDisk() {
        return sidesPerDisk;
    }

    /** トラック数を返す */
    public int getTracksPerSide() {
        return tracksPerSide;
    }

    /** セクタ数を返す */
    public int getSectorsPerTrack() {
        return sectorsPerTrack;
    }

    /** セクタサイズを返す */
    public int getSectorSize() {
//logger.log(Level.INFO, "sectorSize: " + sectorSize);
        return sectorSize;
    }

    /** セクタ番号の付番方法(0:サイド毎、1:トラック毎)を返す */
    public int getNumberingSector() {
        return numberingSector;
    }

    /** 密度(0x00:2D 0x10:2DD 0x20:2HD)を返す */
    public int getParamDensity() {
        return diskDensity;
    }

    /** セクタの間隔を返す */
    public int getInterleave() {
        return interleave;
    }

    /** 開始トラック番号を返す */
    public int getTrackNumberBaseOnDisk() {
        return trackNumberBase;
    }

    /** 開始サイド番号を返す */
    public int getSideNumberBaseOnDisk() {
        return sideNumberBase;
    }

    /** 開始セクタ番号を返す */
    public int getSectorNumberBaseOnDisk() {
        return sectorNumberBase;
    }

    /** セクタ数がトラックごとに異なる */
    public boolean isVariableSectorsPerTrack() {
        return variableSecsPerTrack;
    }

    /** 単密度にするトラックを返す */
    public List<DiskParticular> getSingles() {
        return singles;
    }

    /** 特殊なトラックを返す */
    public List<DiskParticular> getParticularTracks() {
        return pTracks;
    }

    /** 特殊なセクタを返す */
    public List<DiskParticular> getParticularSectors() {
        return pSectors;
    }

    /** 密度情報（説明用）を返す */
    public String getDensityName() {
        return densityName;
    }

    /** 説明を返す */
    public String getDescription() {
        return description;
    }

    /** ディスクパラメータのテンプレートを提供する */
    public static class DiskTemplates extends TemplatesBase {

        private final List<DiskParam> params;

        /**
         * ディスクパラメータのテンプレートを提供する
         */
        public DiskTemplates() {
            this.params = new ArrayList<>();
        }

        /**
         * XMLファイルから読み込み
         *
         * @param dataPath    入力ファイルのあるパス
         * @param localeName  ローケル名
         * @param errMessages [out] エラーメッセージ
         * @return true / false
         * @see ""disk_types.xml"
         */
        public boolean load(String dataPath, String localeName, StringBuilder errMessages) {
            params.clear();

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            Document doc;
            try {
                DocumentBuilder builder = factory.newDocumentBuilder();
                doc = builder.parse(new File(dataPath + "disk_types.xml"));
            } catch (ParserConfigurationException | SAXException | IOException e) {
logger.log(Level.ERROR, e.getMessage(), e);
                return false;
            }

            // start processing the XML file
            if (!doc.getDocumentElement().getNodeName().equals("DiskTypes")) {
logger.log(Level.ERROR, "no DiskTypes element found");
                return false;
            }

            Node item = doc.getDocumentElement().getFirstChild();
            while (item != null) {
                if (item.getNodeName().equals("DiskType")) {
                    DiskParam p = new DiskParam();

                    String type_name = ((Element) item).getAttribute("name");
                    p.setDiskTypeName(type_name);

                    Node itemnode = item.getFirstChild();
                    String[] den_name = {""}, den_name_locale = {""};
                    String[] desc = {""}, desc_locale = {""};
                    while (itemnode != null) {
                        String str = itemnode.getTextContent();
                        switch (itemnode.getNodeName()) {
                            case "Reversible" -> p.setReversible(Utils.toBool(str));
                            case "SidesPerDisk" -> p.setSidesPerDisk(Utils.toInt(str));
                            case "TracksPerSide" -> p.setTracksPerSide(Utils.toInt(str));
                            case "SectorsPerTrack" -> p.setSectorsPerTrack(Utils.toInt(str));
                            case "SectorSize" -> p.setSectorSize(Utils.toInt(str));
                            case "NumberingSector" -> {
                                str = str.toLowerCase();
                                if (str.equals("track")) {
                                    p.setNumberingSector(1);
                                }
                            }
                            case "Density" -> p.setParamDensity(Utils.toInt(str));
                            case "Interleave" -> p.setInterleave(Utils.toInt(str));
                            case "TrackNumberBase" -> p.setTrackNumberBaseOnDisk(Utils.toInt(str));
                            case "SideNumberBase" -> p.setSideNumberBaseOnDisk(Utils.toInt(str));
                            case "SectorNumberBase" -> p.setSectorNumberBaseOnDisk(Utils.toInt(str));
                            case "VariableSectorsPerTrack" -> p.setVariableSectorsPerTrack(Utils.toBool(str));
                            case "DiskBasicTypes" -> {
                                List<DiskParamName> basic_types = new ArrayList<>();
                                if (!loadDiskBasicTypes(itemnode, basic_types, errMessages)) {
                                    return false;
                                }
                                p.setBasicTypes(basic_types);
                            }
                            case "SingleDensity" -> {
                                DiskParticular s = new DiskParticular();
                                if (!loadSingleDensity(itemnode, s, errMessages)) {
                                    return false;
                                }
                                p.addSingleDensity(s);
                            }
                            case "ParticularTrack" -> {
                                DiskParticular d = new DiskParticular();
                                if (!loadParticularTrack(itemnode, d, errMessages)) {
                                    return false;
                                }
                                p.addParticularTrack(d);
                            }
                            case "ParticularSector" -> {
                                DiskParticular d = new DiskParticular();
                                if (!loadParticularSector(itemnode, d, errMessages)) {
                                    return false;
                                }
                                p.addParticularSector(d);
                            }
                            case "DensityName" -> loadDescription(itemnode, localeName, den_name, den_name_locale);
                            case "Description" -> loadDescription(itemnode, localeName, desc, desc_locale);
                        }
                        itemnode = itemnode.getNextSibling();
                    }
                    if (!den_name_locale[0].isEmpty()) {
                        den_name = den_name_locale;
                    }
                    p.setDensityName(den_name[0]);
                    if (!desc_locale[0].isEmpty()) {
                        desc[0] = desc_locale[0];
                    }
                    p.setDescription(desc[0]);

                    if (find(type_name) == null) {
                        params.add(p);
                    } else {
                        errMessages.append("\n");
                        errMessages.append("Duplicate type name in DiskType : ");
                        errMessages.append(type_name);
                        return false;
                    }
                }
                item = item.getNextSibling();
            }
            assert !params.isEmpty();
logger.log(Level.INFO, "params: " + params.size());
            return true;
        }

        /**
         * DiskBasicTypesエレメントをロード
         *
         * @param node        子ノード
         * @param basicTypes  [out] ロードしたデータ
         * @param errMessages [out] エラーメッセージ
         * @return true
         */
        public boolean loadDiskBasicTypes(Node node, List<DiskParamName> basicTypes, StringBuilder errMessages) {
            Node citemnode = node.getFirstChild();
            while (citemnode.getNodeType() != Node.ELEMENT_NODE) {
                citemnode = citemnode.getNextSibling();
            }
            while (citemnode != null) {
                String str = citemnode.getTextContent();
                if (citemnode.getNodeName().equals("Type")) {
                    DiskParamName p = new DiskParamName();
                    str = str.trim();
                    if (!str.isEmpty()) {
                        p.setName(str);
                    }
                    str = ((Element) citemnode).getAttribute("p");
                    if (str.equalsIgnoreCase("major")) {
                        p.setFlags(1);
                    } else if (str.equalsIgnoreCase("minor")) {
                        p.setFlags(2);
                    }
                    basicTypes.add(p);
                }
                citemnode = citemnode.getNextSibling();
            }
            return true;
        }

        /**
         * SingleDensityエレメントをロード
         *
         * @param node        子ノード
         * @param s           [out] ロードしたデータ
         * @param errMessages [out] エラーメッセージ
         * @return true
         */
        public boolean loadSingleDensity(Node node, DiskParticular s, StringBuilder errMessages) {
            String str;
            str = ((Element) node).getAttribute("track");
            if (!str.isEmpty() && !str.equalsIgnoreCase("ALL")) {
                s.setTrackNumber(Utils.toInt(str));
            }
            str = ((Element) node).getAttribute("side");
            if (!str.isEmpty() && !str.equalsIgnoreCase("ALL")) {
                s.setSideNumber(Utils.toInt(str));
            }
            str = ((Element) node).getAttribute("sector");
            if (!str.isEmpty() && !str.equalsIgnoreCase("ALL")) {
                s.setSectorNumber(Utils.toInt(str));
            }
            str = ((Element) node).getAttribute("sectors");
            if (!str.isEmpty() && !str.equalsIgnoreCase("ALL")) {
                s.setSectorsPerTrack(Utils.toInt(str));
            }
            str = ((Element) node).getAttribute("size");
            if (!str.isEmpty()) {
                s.setSectorSize(Utils.toInt(str));
            } else {
                // default size
                s.setSectorSize(128);
            }
            return true;
        }

        /**
         * ParticularTrack エレメントをロード
         *
         * @param node        子ノード
         * @param d           [out] ロードしたデータ
         * @param errMessages [out] エラーメッセージ
         * @return true
         */
        public boolean loadParticularTrack(Node node, DiskParticular d, StringBuilder errMessages) {
            String str;
            str = ((Element) node).getAttribute("track");
            if (!str.isEmpty() && !str.equalsIgnoreCase("ALL")) {
                d.setTrackNumber(Utils.toInt(str));
            }
            str = ((Element) node).getAttribute("tracks");
            if (str.equalsIgnoreCase("ALL")) {
                errMessages.append("\n");
                errMessages.append("Cannot set \"ALL\" on tracks attribute in ParticularTrack element.");
                return false;
            }
            if (!str.isEmpty() && !str.equalsIgnoreCase("ALL")) {
                d.setNumberOfTracks(Utils.toInt(str));
            }
            str = ((Element) node).getAttribute("side");
            if (!str.isEmpty() && !str.equalsIgnoreCase("ALL")) {
                d.setSideNumber(Utils.toInt(str));
            }
            str = ((Element) node).getAttribute("sectors");
            if (!str.isEmpty() && !str.equalsIgnoreCase("ALL")) {
                d.setSectorsPerTrack(Utils.toInt(str));
            }
            str = ((Element) node).getAttribute("size");
            if (!str.isEmpty()) {
                d.setSectorSize(Utils.toInt(str));
            }
            Node cnode = node.getFirstChild();
            while (cnode != null) {
                if (cnode.getNodeName().equals("Exclude")) {
                    DiskParam.TrackParam e = new DiskParam.TrackParam();
                    str = ((Element) cnode).getAttribute("track");
                    if (!str.isEmpty()) {
                        e.setTrackNumber(Utils.toInt(str));
                    }
                    str = ((Element) cnode).getAttribute("side");
                    if (!str.isEmpty()) {
                        e.setSideNumber(Utils.toInt(str));
                    }
                    d.addExclude(e);
                }
                cnode = cnode.getNextSibling();
            }
            return true;
        }

        /**
         * ParticularSectorエレメントをロード
         *
         * @param node        子ノード
         * @param d           [out] ロードしたデータ
         * @param errMessages [out] エラーメッセージ
         * @return true
         */
        public boolean loadParticularSector(Node node, DiskParticular d, StringBuilder errMessages) {
            String str;
            str = ((Element) node).getAttribute("track");
            if (!str.isEmpty() && !str.equalsIgnoreCase("ALL")) {
                d.setTrackNumber(Utils.toInt(str));
            }
            str = ((Element) node).getAttribute("side");
            if (!str.isEmpty() && !str.equalsIgnoreCase("ALL")) {
                d.setSideNumber(Utils.toInt(str));
            }
            str = ((Element) node).getAttribute("sector");
            if (!str.isEmpty() && !str.equalsIgnoreCase("ALL")) {
                d.setSectorNumber(Utils.toInt(str));
            }
            str = ((Element) node).getAttribute("size");
            if (!str.isEmpty()) {
                d.setSectorSize(Utils.toInt(str));
            }
            str = ((Element) node).getAttribute("id_h");
            if (!str.isEmpty()) {
                d.setID(1, (short) Utils.toInt(str) | TrackParam.ID_IS_VALID);
            }
            str = ((Element) node).getAttribute("id_r");
            if (!str.isEmpty()) {
                d.setID(2, (short) Utils.toInt(str) | TrackParam.ID_IS_VALID);
            }
            Node cnode = node.getFirstChild();
            while (cnode != null) {
                if (cnode.getNodeName().equals("Exclude")) {
                    TrackParam e = new TrackParam();
                    str = ((Element) cnode).getAttribute("track");
                    if (!str.isEmpty()) {
                        e.setTrackNumber(Utils.toInt(str));
                    }
                    str = ((Element) cnode).getAttribute("side");
                    if (!str.isEmpty()) {
                        e.setSideNumber(Utils.toInt(str));
                    }
                    d.addExclude(e);
                }
                cnode = cnode.getNextSibling();
            }
            return true;
        }

        /**
         * タイプ名に一致するテンプレートの番号を返す
         *
         * @param typeName タイプ名
         * @return ディスクテンプレートの位置 / ないとき-1
         */
        public int indexOf(String typeName) {
            int match = -1;
            for (int i = 0; i < params.size(); i++) {
                DiskParam item = params.get(i);
                if (typeName.equals(item.getDiskTypeName())) {
                    match = i;
                    break;
                }
            }
            return match;
        }

        /** 一致するテンプレートを返す */
        public DiskParam find(DiskParam n_param) {
            DiskParam match = null;
            for (DiskParam item : params) {
                if (item.matchExceptName(n_param)) {
                    match = item;
                    break;
                }
            }
            return match;
        }

        /** タイプ名に一致するテンプレートを返す */
        public DiskParam find(String n_type_name) {
            DiskParam match = null;
            for (DiskParam item : params) {
                if (n_type_name.equals(item.getDiskTypeName())) {
                    match = item;
                    break;
                }
            }
            return match;
        }

        /**
         * パラメータに一致するテンプレートを返す
         *
         * @param sidesPerDisk     サイド数
         * @param tracksPerSide    トラック数
         * @param sectorsPerTrack  セクタ数
         * @param sectorSize       セクタサイズ
         * @param interleave       インターリーブ
         * @param trackNumberBase  開始トラック番号
         * @param sideNumberBase   開始サイド番号
         * @param sectorNumberBase 開始セクタ番号
         * @param numberingSector  セクタ採番方法
         * @param singles          単密度情報
         * @param pTracks          特殊トラック
         * @return ディスクパラメータ or null
         */
        public DiskParam findStrict(int sidesPerDisk, int tracksPerSide, int sectorsPerTrack, int sectorSize,
                                    int interleave, int trackNumberBase, int sideNumberBase, int sectorNumberBase,
                                    int numberingSector, List<DiskParticular> singles, List<DiskParticular> pTracks) {
            DiskParam matchItem = null;
            boolean m = false;
            for (DiskParam item : params) {
                m = item.match(sidesPerDisk, tracksPerSide, sectorsPerTrack, sectorSize, interleave,
                        trackNumberBase, sideNumberBase, sectorNumberBase, numberingSector, singles, pTracks);
                if (m) {
                    matchItem = item;
                    break;
                }
            }
            return matchItem;
        }

        /**
         * パラメータに一致するあるいは近い物のテンプレートを返す
         *
         * @param sidesPerDisk     サイド数
         * @param tracksPerSide    トラック数
         * @param sectorsPerTrack  セクタ数
         * @param sectorSize       セクタサイズ
         * @param interleave       インターリーブ
         * @param trackNumberBase  開始トラック番号
         * @param sideNumberBase   開始サイド番号
         * @param sectorNumberBase 開始セクタ番号
         * @param numberingSector  セクタ採番方法
         * @param singles          単密度情報
         * @param pTracks          特殊トラック
         * @return ディスクパラメータ or null
         */
        public DiskParam find(int sidesPerDisk, int tracksPerSide, int sectorsPerTrack, int sectorSize,
                              int interleave, int trackNumberBase, int sideNumberBase, int sectorNumberBase,
                              int numberingSector, List<DiskParticular> singles, List<DiskParticular> pTracks) {
logger.log(Level.TRACE, new StringJoiner(", ", "", "")
//                .add(diskTypeName)
//                .add(reversible + "")
                .add(sidesPerDisk + "")
                .add(tracksPerSide + "")
                .add(sectorsPerTrack + "")
                .add(sectorSize + "")
                .add(numberingSector + "")
//                .add(diskDensity + "")
                .add(interleave + "")
                .add(trackNumberBase + "")
                .add(sideNumberBase + "")
                .add(sectorNumberBase + "")
//                .add(variableSecsPerTrack + "")
                .add("\"" + singles + "\"")
                .add("\"" + pTracks + "\"")
//                .add("\"" + pSectors + "\"")
//                .add("\"" + densityName + "\"")
//                .add("\"" + description + "\"")
//                .add("\"" + basicTypes + "\"")
                .toString());
            DiskParam matchItem = findStrict(sidesPerDisk, tracksPerSide, sectorsPerTrack, sectorSize,
                    interleave, trackNumberBase, sideNumberBase, sectorNumberBase, numberingSector, singles, pTracks);
            if (matchItem == null) {
logger.log(Level.TRACE, "no strict match");
                boolean[] last = {false};
                boolean m = false;
                for (int num = 0; !last[0] && !m; num++) {
                    // パラメータが一致しないときは、引数に近いパラメータ
                    for (DiskParam item : params) {
                        m = item.matchNear(num, sidesPerDisk, tracksPerSide, sectorsPerTrack, sectorSize,
                                interleave, numberingSector, singles, last);
                        if (last[0]) {
                            break;
                        }
                        if (m) {
                            matchItem = item;
                            break;
                        }
                    }
                }
            }
            return matchItem;
        }

        /**
         * パラメータに一致するテンプレートのリストを返す
         *
         * @param sidesPerDisk    サイド数
         * @param tracksPerSide   トラック数
         * @param sectorsPerTrack セクタ数
         * @param sector_size     セクタサイズ
         * @param list            [out] 候補リスト
         * @param separator       リストの最初にセパレータ(null)を追加するか
         * @return リスト内のアイテム数
         */
        public int find(int sidesPerDisk, int tracksPerSide, int sectorsPerTrack, int sector_size,
                        List<DiskParam> list, boolean separator) {
            for (DiskParam item : params) {
                if (item.match(sidesPerDisk, tracksPerSide, sectorsPerTrack, sector_size)) {
                    // 重複してなければ追加
                    if (!list.contains(item)) {
                        if (separator) {
                            // 最初の候補の前にセパレータを追加
                            list.add(null);
                            separator = false;
                        }
                        list.add(item);
                    }
                }
            }
            return list.size();
        }

        public DiskParam get(int index) {
            return params.get(index);
        }

        public int size() {
            return params.size();
        }
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", DiskParam.class.getSimpleName() + "[", "]")
                .add("diskTypeName='" + diskTypeName + "'")
                .add("reversible=" + reversible)
                .add("sidesPerDisk=" + sidesPerDisk)
                .add("tracksPerSide=" + tracksPerSide)
                .add("sectorsPerTrack=" + sectorsPerTrack)
                .add("sectorSize=" + sectorSize)
                .add("numberingSector=" + numberingSector)
                .add("diskDensity=" + diskDensity)
                .add("interleave=" + interleave)
                .add("trackNumberBase=" + trackNumberBase)
                .add("sideNumberBase=" + sideNumberBase)
                .add("sectorNumberBase=" + sectorNumberBase)
                .add("variableSecsPerTrack=" + variableSecsPerTrack)
                .add("singles=" + singles)
                .add("pTracks=" + pTracks)
                .add("pSectors=" + pSectors)
                .add("densityName='" + densityName + "'")
                .add("description='" + description + "'")
                .add("basicTypes=" + basicTypes)
                .toString();
        // csv
//        return new StringJoiner(", ", "", "")
//                .add(diskTypeName)
//                .add(reversible + "")
//                .add(sidesPerDisk + "")
//                .add(tracksPerSide + "")
//                .add(sectorsPerTrack + "")
//                .add(sectorSize + "")
//                .add(numberingSector + "")
//                .add(diskDensity + "")
//                .add(interleave + "")
//                .add(trackNumberBase + "")
//                .add(sideNumberBase + "")
//                .add(sectorNumberBase + "")
//                .add(variableSecsPerTrack + "")
//                .add("\"" + singles + "\"")
//                .add("\"" + pTracks + "\"")
//                .add("\"" + pSectors + "\"")
//                .add("\"" + densityName + "\"")
//                .add("\"" + description + "\"")
//                .add("\"" + basicTypes + "\"")
//                .toString();
    }
}
