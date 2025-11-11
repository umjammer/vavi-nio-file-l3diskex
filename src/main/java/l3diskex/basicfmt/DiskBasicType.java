package l3diskex.basicfmt;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.System.Logger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import l3diskex.Common;
import l3diskex.Utils;
import l3diskex.basicfmt.BasicCommon.DirectoryT;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.DiskBasic.DiskBasicIdentifiedData;
import l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability;
import l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;
import l3diskex.diskimg.DiskParam.NumSectorsParam;
import l3diskex.diskimg.DiskParam.SectorParam;

import static l3diskex.basicfmt.DiskBasicError.ERRV_NO_SECTOR;
import static l3diskex.basicfmt.DiskBasicError.ERRV_NO_TRACK;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_FREE;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_SYSTEM;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_USED;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_USED_LAST;


/**
 * DISK BASIC 機種依存 個別の処理テンプレート
 * <p>
 * 抽象クラス
 */
public abstract class DiskBasicType<T extends DirectoryT> {

    private static final Logger logger = System.getLogger(DiskBasicType.class.getName());

    public static final int INVALID_GROUP_NUMBER = -1;

    /** セクタを確保する時のフラグ */
    public enum AllocateGroupFlags {
        ALLOCATE_GROUPS_NEW,
        ALLOCATE_GROUPS_APPEND
    }

    /**
     * セクタスキューマップ基底
     *
     * @see SectorsPerTrack
     * @see DiskBasicSectorPosTrans
     */
    public static class SectorSkewBase implements Cloneable {

        /** セクタ数 */
        protected int numSecs;

        public SectorSkewBase() {
            numSecs = 0;
        }

        public SectorSkewBase(SectorSkewBase src) {
            numSecs = src.numSecs;
        }

        /** クローン コピー時に使用する */
        @Override
        public SectorSkewBase clone() {
            try {
                return (SectorSkewBase) super.clone();
            } catch (CloneNotSupportedException e) {
                throw new AssertionError();
            }
        }

        /** スキューマップを削除 */
        public void delete() {
        }

        /** スキューマップを作成 */
        public void create(DiskBasic basic, int numSecs) {
            this.numSecs = numSecs;
        }

        /** スキューマップをマッピング */
        public void mapping(DiskBasic basic) {
        }

        /** 物理番号に変換 */
        public int toPhysical(int val) {
            return 0;
        }

        /** 論理番号に変換 */
        public int toLogical(int val) {
            return 0;
        }
    }

    /**
     * セクタスキューマップ
     *
     * @see SectorsPerTrack
     * @see DiskBasicSectorPosTrans
     */
    public static class DiskBasicSectorSkew extends SectorSkewBase implements Cloneable {

        /** 論理セクタ番号 から 物理セクタ番号 */
        protected int[] ltopMap;
        /** 物理セクタ番号 から 論理セクタ番号 */
        protected int[] ptolMap;

        public DiskBasicSectorSkew() {
            super();
            ltopMap = null;
            ptolMap = null;
        }

        public DiskBasicSectorSkew(DiskBasicSectorSkew src) {
            super(src);
            if (src.ltopMap != null) {
                ltopMap = new int[src.numSecs];
                System.arraycopy(src.ltopMap, 0, ltopMap, 0, src.numSecs);
            }
            if (src.ptolMap != null) {
                ptolMap = new int[src.numSecs];
                System.arraycopy(src.ptolMap, 0, ptolMap, 0, src.numSecs);
            }
        }

        /** クローン コピー時に使用する */
        @Override
        public SectorSkewBase clone() {
            return new DiskBasicSectorSkew(this);
        }

        /** スキューマップを削除 */
        @Override
        public void delete() {
            ltopMap = null;
            ptolMap = null;
        }

        /** スキューマップを作成 */
        @Override
        public void create(DiskBasic basic, int numSecs) {
            super.create(basic, numSecs);

            ltopMap = new int[this.numSecs];
            ptolMap = new int[this.numSecs];

            for (int i = 0; i < this.numSecs; i++) {
                ltopMap[i] = -1;
                ptolMap[i] = -1;
            }

            mapping(basic);
        }

        protected void mappingFromParam(DiskBasic basic) {
            int psec = 0;
            for (int lsec = 0; lsec < numSecs; lsec++) {
                psec = basic.diskBasicParam.getSectorSkewMap(lsec);
                ltopMap[lsec] = psec;
                if (psec < numSecs) ptolMap[psec] = lsec;
            }
        }

        protected void mappingFromCalc(DiskBasic basic, int skew) {
            if (skew < 1) skew = 1;

            int psec = 0;
            for (int lsec = 0; lsec < numSecs; lsec++) {
                ltopMap[lsec] = psec;
                ptolMap[psec] = lsec;
                psec += skew;
                if (psec >= numSecs) {
                    psec -= numSecs;
                    for (int limit = numSecs; psec < numSecs && ptolMap[psec] >= 0 && limit > 0; limit--) { // TODO vavi adds "psec < numSecs"
                        psec++;
                    }
                }
            }
        }

        /** スキューマップをマッピング */
        @Override
        public void mapping(DiskBasic basic) {
            if (basic.diskBasicParam.hasSectorSkewMap()) {
                mappingFromParam(basic);
            } else {
                // システムはソフトセクタスキュー(仮想的インターリーブ)を持っている
                mappingFromCalc(basic, basic.diskBasicParam.getSectorSkew());
            }
        }

        /** 物理番号に変換 */
        @Override
        public int toPhysical(int val) {
            if (ltopMap != null) val = ltopMap[val];
            return val;
        }

        /** 論理番号に変換 */
        @Override
        public int toLogical(int val) {
            if (ptolMap != null) val = ptolMap[val];
            return val;
        }
    }

    /** セクタスキューマップ インポート時の空きセクタの割り当て方 */
    public static class DiskBasicSectorSkewForSave extends DiskBasicSectorSkew implements Cloneable {

        public DiskBasicSectorSkewForSave() {
            super();
        }

        public DiskBasicSectorSkewForSave(DiskBasicSectorSkewForSave src) {
            super(src);
        }

        /** クローン コピー時に使用する */
        @Override
        public SectorSkewBase clone() {
            return new DiskBasicSectorSkewForSave(this);
        }

        /** スキューマップをマッピング */
        @Override
        public void mapping(DiskBasic basic) {
            mappingFromCalc(basic, basic.diskBasicParam.getVariousIntegerParam("SectorSkewForSave"));
        }
    }

    /**
     * トラックごとのセクタ数を保持
     *
     * @see DiskBasicSectorPosTrans
     */
    public static class SectorsPerTrack {

        /** トラック数 */
        private final int numOfTracks;
        /** セクタ数(両サイド通して) */
        private final int numOfSectors;
        /** 合計セクタ数 */
        private final int totalSectors;
        /** セクタスキュー */
        private SectorSkewBase ssmap;

        public SectorsPerTrack() {
            numOfTracks = 0;
            numOfSectors = 0;
            totalSectors = 0;
            ssmap = null;
        }

        public SectorsPerTrack(SectorsPerTrack src) {
            numOfTracks = src.numOfTracks;
            numOfSectors = src.numOfSectors;
            totalSectors = src.totalSectors;
            if (src.ssmap != null) {
                ssmap = src.ssmap.clone();
            } else {
                ssmap = null;
            }
        }

        public SectorsPerTrack(int numOfTracks, int numOfSectors, int totalSectors) {
            this.numOfTracks = numOfTracks;
            this.numOfSectors = numOfSectors;
            this.totalSectors = totalSectors;
            this.ssmap = null;
        }

        /** トラック数を返す */
        public int getNumOfTracks() {
            return numOfTracks;
        }

        /** セクタ数を返す */
        public int getNumOfSectors() {
            return numOfSectors;
        }

        /** 合計セクタ数を返す */
        public int getTotalSectors() {
            return totalSectors;
        }

        /** スキューマップを設定 */
        public void setSectorSkewMap(SectorSkewBase map) {
            ssmap = map;
        }

        /** スキューマップを返す */
        public SectorSkewBase getSectorSkewMap() {
            return ssmap;
        }
    }

    /**
     * セクタ位置変換マップリスト SectorsPerTrack の配列
     * <p>
     * トラック毎にセクタ数が異なる場合、このセクタ数リストを使って
     * セクタ位置を求める。
     */
    public static class DiskBasicSectorPosTrans {

        protected List<SectorsPerTrack> list = new ArrayList<>();

        public void create(DiskBasic basic) {
            list.clear();
            List<NumSectorsParam> sp = basic.diskBasicParam.sectorsPerTrackOnBasicList();
            if (!sp.isEmpty()) {
                for (NumSectorsParam p : sp) {
                    list.add(new SectorsPerTrack(p.getNumberOfTracks(), p.getSectorsPerTrack() * basic.getSidesPerDiskOnBasic(), p.getNumberOfTracks() * p.getSectorsPerTrack() * basic.getSidesPerDiskOnBasic()));
                }
            } else if (basic.isVariableSectorsPerTrack()) {
                int numOfTracks = 0;
                int prevNumOfSectors = 0;
                int totalSectors = 0;
                int trk = basic.getTrackNumberBaseOnDisk();
                int trks = basic.diskBasicParam.getTracksPerSideOnBasic() + trk;
                for (; trk < trks; trk++) {
                    for (int sid = 0; sid < basic.diskBasicParam.getSidesPerDiskOnBasic(); sid++) {
                        DiskImageTrack track = basic.getTrack(trk, sid);
                        if (track == null) {
                            continue;
                        }
                        int numOfSectors = track.getSectorsPerTrack() * basic.diskBasicParam.getSidesPerDiskOnBasic();
                        if (prevNumOfSectors == 0) {
                            totalSectors = 0;
                            prevNumOfSectors = numOfSectors;
                            numOfTracks = 0;
                        } else if (prevNumOfSectors != numOfSectors) {
                            list.add(new SectorsPerTrack(numOfTracks, prevNumOfSectors, totalSectors));
                            totalSectors = 0;
                            prevNumOfSectors = numOfSectors;
                            numOfTracks = 0;
                        }
                        totalSectors += numOfSectors;
                    }
                    numOfTracks++;
                }
                list.add(new SectorsPerTrack(numOfTracks, prevNumOfSectors, totalSectors));
            } else {
                list.add(new SectorsPerTrack(basic.diskBasicParam.getTracksPerSideOnBasic(), basic.diskBasicParam.getSectorsPerTrackOnBasic() * basic.getSidesPerDiskOnBasic(), basic.getTracksPerSideOnBasic() * basic.getSectorsPerTrackOnBasic() * basic.getSidesPerDiskOnBasic()));
            }
        }

        /**
         * セクタスキューマップを作る
         * <p>
         * 使用する場合は派生クラスを作る
         */
        public void createSectorSkewMap(DiskBasic basic) {
        }

        /** 指定トラックのアイテムを返す */
        public SectorsPerTrack findByTrackNum(int trackNum) {
            SectorsPerTrack match = null;
            for (SectorsPerTrack item : list) {
                if (trackNum < item.getNumOfTracks()) {
                    match = item;
                    break;
                }
                trackNum -= item.getNumOfTracks();
            }
            return match;
        }

        /** 全トラックのセクタ数を返す */
        public int getTotalSectors() {
            int val = 0;
            for (SectorsPerTrack item : list) {
                val += item.getTotalSectors();
            }
            return val;
        }

        /** 論理セクタ位置(最初のトラック＆セクタを0とした通し番号)からトラック、セクタの各番号を得る サイド番号はセクタ番号の通し番号に変換 */
        public void getNumFromSectorPos(int sectorPos, int[] trackNum, int[] sectorNum, int[] numOfSectors) {
            trackNum[0] = 0;
            for (SectorsPerTrack item : list) {
                if (sectorPos < item.getTotalSectors()) {
                    numOfSectors[0] = item.getNumOfSectors();
                    trackNum[0] += (sectorPos / numOfSectors[0]);
                    sectorNum[0] = sectorPos % numOfSectors[0];
                    break;
                }
                sectorPos -= item.getTotalSectors();
                trackNum[0] += item.getNumOfTracks();
            }
        }

        /** トラック、セクタの各番号からセクタ位置(最初のトラック＆セクタを0とした通し番号)を得る サイド番号はセクタ番号の通し番号に変換 */
        public int getSectorPosFromNum(int trackNum, int sectorNum, int[] numOfSectors) {
            int sectorPos = 0;
            for (SectorsPerTrack item : list) {
                if (trackNum < item.getNumOfTracks()) {
                    numOfSectors[0] = item.getNumOfSectors();
                    sectorPos += trackNum * numOfSectors[0] + sectorNum;
                    break;
                }
                trackNum -= item.getNumOfTracks();
                sectorPos += item.getTotalSectors();
            }
            return sectorPos;
        }

        public SectorsPerTrack get(int i) {
            return list.get(i);
        }

        public int size() {
            return list.size();
        }
    }

    protected DiskBasic basic;
    protected DiskBasicFat fat;
    protected DiskBasicDir<T> dir;

    /** 管理エリアの開始グループ番号 */
    protected int managedStartGroup;
    /** データ開始グループ番号 */
    protected int dataStartGroup;

    /** 使用状況(FAT,グループ単位) */
    protected DiskBasicAvailability fatAvailability = new DiskBasicAvailability();

    protected DiskBasicType() {
    }

    public DiskBasicType(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<T> dir) {
        this.basic = basic;
        this.fat = fat;
        this.dir = dir;
        this.dataStartGroup = 0;
    }

    /** FAT位置をセット */
    public void setGroupNumber(int num, int val) throws IOException {
    }

    /** FAT位置を返す */
    public int getGroupNumber(int num) throws IOException {
        return 0;
    }

    /** 使用しているグループ番号か */
    public boolean isUsedGroupNumber(int num) {
        return true;
    }

    /** 次のグループ番号を得る */
    public int getNextGroupNumber(int num, int sectorPos) {
        return 0;
    }

    /** 空きFAT位置を返す */
    public int getEmptyGroupNumber() throws IOException {
        int newNum = INVALID_GROUP_NUMBER;
        for (int num = 0; num <= basic.diskBasicParam.getFatEndGroup(); num++) {
            int gnum = getGroupNumber(num);
            if (gnum == basic.diskBasicParam.getGroupUnusedCode()) {
                newNum = num;
                break;
            }
        }
        return newNum;
    }

    /** 次の空きFAT位置を返す */
    public int getNextEmptyGroupNumber(int currGroup) throws IOException {
        int newNum = INVALID_GROUP_NUMBER;
        int secsPerGrp = basic.diskBasicParam.getSectorsPerGroup();
        int secsPerTrk = basic.diskBasicParam.getSectorsPerTrackOnBasic();
        int sides = basic.diskBasicParam.getSidesPerDiskOnBasic();

        int sed = secsPerTrk * sides / secsPerGrp;
        if (sed == 0) return INVALID_GROUP_NUMBER;
        int groupMax = (basic.diskBasicParam.getFatEndGroup() / sed) + 1;
        int groupManage = managedStartGroup / sed;
        int groupStart = currGroup / sed;
        int groupEnd;
        int dir;
        int sst = 0;
        boolean found = false;

        dir = (groupStart >= groupManage ? 1 : -1);

        for (int i = 0; i < 2; i++) {
            groupEnd = (dir > 0 ? groupMax : -1);
            for (int g = groupStart; g != groupEnd; g += dir) {
                for (int s = sst; s < sed; s++) {
                    int num = g * sed + s;
                    if (num > basic.diskBasicParam.getFatEndGroup()) {
                        break;
                    }
                    int gnum = getGroupNumber(num);
                    if (gnum == basic.diskBasicParam.getGroupUnusedCode()) {
                        newNum = num;
                        found = true;
                        break;
                    }
                }
                if (found) break;
                sst = 0;
            }
            if (found) break;
            dir = -dir;
            groupStart = groupManage;
        }
        return newNum;
    }

    /**
     * ディスクから各パラメータを取得＆必要なパラメータを計算
     *
     * @param isFormatting フォーマット中か
     */
    public double parseParamOnDisk(boolean isFormatting) throws IOException {
        return 1.0;
    }

    /**
     * FATエリアをチェック
     *
     * @param isFormatting フォーマット中か
     */
    public double checkFat(boolean isFormatting) throws IOException {
        return 1.0;
    }

    /** FATの開始位置を得る（ダイアログ用） */
    public void getStartNumOnFat(int[] trackNum, int[] sideNum, int[] sectorNum) {
        int secPos = basic.diskBasicParam.getFatStartSector() - 1;
        int secFat = basic.diskBasicParam.getSectorsPerFat();
        DiskImageTrack track = null;
        if (secPos >= 0 && secFat > 0) {
            if (basic.diskBasicParam.getFatSideNumber() >= 0)
                secPos += basic.diskBasicParam.getFatSideNumber() * basic.diskBasicParam.getSectorsPerTrackOnBasic();
            track = basic.getManagedTrack(secPos, sideNum, sectorNum);
        }
        if (track != null) {
            trackNum[0] = track.getTrackNumber();
        } else {
            trackNum[0] = -1;
        }
    }

    /** FATの終了位置を得る（ダイアログ用） */
    public void getEndNumOnFat(int[] trackNum, int[] sideNum, int[] sectorNum) {
        int secSta = basic.diskBasicParam.getFatStartSector() - 1;
        int secPos = secSta + basic.diskBasicParam.getSectorsPerFat() * basic.diskBasicParam.getNumberOfFats() - 1;
        int secFat = basic.diskBasicParam.getSectorsPerFat();
        DiskImageTrack track = null;
        if (secSta >= 0 && secFat > 0) {
            if (basic.diskBasicParam.getFatSideNumber() >= 0)
                secPos += basic.diskBasicParam.getFatSideNumber() * basic.diskBasicParam.getSectorsPerTrackOnBasic();
            track = basic.getManagedTrack(secPos, sideNum, sectorNum);
        }
        if (track != null) {
            trackNum[0] = track.getTrackNumber();
        } else {
            trackNum[0] = -1;
        }
    }

    /** "FAT"などのタイトル名（ダイアログ用） */
    public String getTitleForFat() {
        return "FAT";
    }

    /** 管理エリアのトラック番号からグループ番号を計算 */
    public int calcManagedStartGroup() {
        int trk = basic.getManagedTrackNumber();
        int sid = basic.diskBasicParam.getFatSideNumber();
        if (sid < 0) sid = 0;
        int sides = basic.diskBasicParam.getSidesPerDiskOnBasic();
        int secsPerGrp = basic.diskBasicParam.getSectorsPerGroup();
        int secsPerTrk = basic.diskBasicParam.getSectorsPerTrackOnBasic();
        managedStartGroup = (trk * sides + sid) * secsPerTrk / secsPerGrp;
        return managedStartGroup;
    }

    /** ルートディレクトリのセクタリストを計算 */
    public boolean calcGroupsOnRootDirectory(int startSector, int endSector, DiskBasicGroups groupItems) throws IOException {
        groupItems.clear();
        int dirSize = 0;
        int sectorBase = basic.getSectorNumberBase();
        for (int secPos = startSector - sectorBase; secPos <= endSector - sectorBase; secPos++) {
            int[] trkNum = new int[1], sidNum = new int[1], secNum = new int[1], divNum = new int[1], divNums = new int[1];
            secNum[0] = 1;
            divNums[0] = 1;
            DiskImageSector sector = basic.getManagedSector(secPos, trkNum, sidNum, secNum, divNum, divNums);
            if (sector == null) continue;
            groupItems.add(secPos, 0, trkNum[0], sidNum[0], secNum[0], secNum[0], divNum[0], divNums[0]);
            dirSize += (sector.getSectorSize() / divNums[0]);
        }
        groupItems.setSize(dirSize);
        return true;
    }

    /** ルートディレクトリのチェック */
    public double checkRootDirectory(int startSector, int endSector, DiskBasicGroups groupItems, boolean isFormatting) throws IOException {
        if (isFormatting) return 1.0;
        double validRatio = -1.0;
        if (calcGroupsOnRootDirectory(startSector, endSector, groupItems)) {
            validRatio = checkDirectory(true, groupItems);
        }
        return validRatio;
    }

    /** ルートディレクトリをアサイン */
    public boolean assignRootDirectory(int startSector, int endSector, DiskBasicGroups groupItems, DiskBasicDirItem<T> dirItem) throws IOException {
        calcGroupsOnRootDirectory(startSector, endSector, groupItems);
        return assignDirectory(true, groupItems, dirItem);
    }

    /** ディレクトリのチェック */
    public double checkDirectory(boolean isRoot, DiskBasicGroups groupItems) throws IOException {
        boolean valid = true;
        boolean[] last = {false};
        int nUsedItems = 0;
        double nNormals = 0.0;

        int indexNumber = 0;
        int[] pos = {0};
        int[] sizeRemain = {groupItems.getSize()};
        int finish = 0;
        int prevGrpNum = -1;
        DiskBasicDirItem<T> nitem = dir.newItem(null, 0, null, 0);
        for (int idx = 0; idx < groupItems.size() && finish >= -1; idx++) {
            DiskBasicGroupItem gitem = groupItems.get(idx);
            int grpNum = gitem.group;
            int trkNum = gitem.track;
            int sidNum = gitem.side;
            int divNum = gitem.divNum; // 分割番号
            int divNums = gitem.divNums; // 分割数
            DiskImageTrack track = basic.getTrack(trkNum, sidNum);
            if (track == null) {
                valid = false;
                break;
            }
            DiskBasicGroupItem nextGitem = idx + 1 < groupItems.size() ? groupItems.get(idx + 1) : null;

            for (int secNum = gitem.sectorStart; secNum <= gitem.sectorEnd && finish >= -1; secNum++) {
                DiskImageSector sector = track.getSector(secNum);
                if (sector == null) {
                    valid = false;
                    break;
                }
                byte[] buffer = sector.getSectorBuffer();
                int bufferOffset = 0;
                if (buffer == null) {
                    valid = false;
                    break;
                }

                int[] size = {sector.getSectorSize() / divNums};

                SectorParam nextSec = new SectorParam(trkNum, sidNum, secNum < gitem.sectorEnd ? secNum + 1 : (nextGitem != null ? nextGitem.sectorStart : -1), -1);

                // オフセットを足す
                bufferOffset += (size[0] * divNum);
                bufferOffset += pos[0];

                if (grpNum != prevGrpNum) {
                    // グループ番号が変わるときにスキップする位置
                    bufferOffset += basic.diskBasicParam.getDirStartPosOnGroup();
                    pos[0] += basic.diskBasicParam.getDirStartPosOnGroup();
                    sizeRemain[0] -= basic.diskBasicParam.getDirStartPosOnGroup();
                    prevGrpNum = grpNum;
                }

                if (idx == 0 && secNum == gitem.sectorStart) {
                    // ディレクトリエリア先頭をスキップする位置
                    int skip = isRoot ? basic.diskBasicParam.getDirStartPosOnRoot() : basic.diskBasicParam.getDirStartPos();
                    bufferOffset += skip;
                    pos[0] += skip;
                    sizeRemain[0] -= skip;
                }

                // ディレクトリエリア各セクタの先頭をスキップする位置
                bufferOffset += basic.diskBasicParam.getDirStartPosOnSector();
                pos[0] += basic.diskBasicParam.getDirStartPosOnSector();
                sizeRemain[0] -= basic.diskBasicParam.getDirStartPosOnSector();

                while (valid && !last[0] && pos[0] < size[0]) {
                    finish = finishAssigningDirectory(pos, size, sizeRemain);
                    if (finish < 0) {
                        // 終了 ポジションは次グループの先頭に
                        sizeRemain[0] = size[0] - pos[0];
                        pos[0] = size[0];
                        break;
                    }
//logger.log(Level.DEBUG, "sector buffer: " + bufferOffset + " / " + buffer.length);
                    nitem.setDataPtr(indexNumber, gitem, sector, pos[0], buffer, bufferOffset, nextSec);
                    valid = nitem.check(last);
                    if (valid) {
                        if (nitem.checkUsed(false)) {
                            nNormals += nitem.normalCodesInFileName();
                            nUsedItems++;
                        }
                    }
                    pos[0] += nitem.getDataSize();
                    bufferOffset += nitem.getDataSize();
                    sizeRemain[0] -= nitem.getDataSize();
                    indexNumber++;
                }

                pos[0] -= size[0];
                pos[0] = adjustPositionAssigningDirectory(pos[0]);
            }
        }

        double validRatio = 0.0;
        if (!valid) {
            validRatio = -1.0;
        } else if (nUsedItems > 0) {
            validRatio = nNormals / (double) nUsedItems;
        }

        return validRatio;
    }

    /** ディレクトリが空か */
    public boolean isEmptyDirectory(boolean isRoot, DiskBasicGroups groupItems) throws IOException {
        boolean valid = true;
        boolean last = false;
        int indexNumber = 0;
        int[] pos = {0};
        int[] sizeRemain = {groupItems.getSize()};
        int finish = 0;
        int prevGrpNum = -1;
        DiskBasicDirItem<T> nitem = dir.newItem(null, 0, null, 0);

        for (int idx = 0; idx < groupItems.size() && finish >= -1; idx++) {
            DiskBasicGroupItem gitem = groupItems.get(idx);
            int grpNum = gitem.group;
            int trkNum = gitem.track;
            int sidNum = gitem.side;
            int divNum = gitem.divNum;
            int divNums = gitem.divNums;
            DiskImageTrack track = basic.getTrack(trkNum, sidNum);
            if (track == null) {
                valid = false;
                break;
            }
            DiskBasicGroupItem nextGitem;
            nextGitem = idx + 1 < groupItems.size() ? groupItems.get(idx + 1) : null;

            for (int secNum = gitem.sectorStart; secNum <= gitem.sectorEnd && valid && !last && finish >= -1; secNum++) {
                DiskImageSector sector = track.getSector(secNum);
                if (sector == null) {
                    valid = false;
                    break;
                }
                byte[] buffer = sector.getSectorBuffer();
                if (buffer == null) {
                    valid = false;
                    break;
                }
                int[] size = {sector.getSectorSize() / divNums};
                SectorParam nextSec = new SectorParam(trkNum, sidNum, secNum < gitem.sectorEnd ? secNum + 1 : (nextGitem != null ? nextGitem.sectorStart : -1), -1);

                int bufferOffset = (size[0] * divNum) + pos[0];
                if (grpNum != prevGrpNum) {
                    int skip = basic.diskBasicParam.getDirStartPosOnGroup();
                    bufferOffset += skip;
                    pos[0] += skip;
                    sizeRemain[0] -= skip;
                    prevGrpNum = grpNum;
                }
                if (idx == 0 && secNum == gitem.sectorStart) {
                    int skip = isRoot ? basic.diskBasicParam.getDirStartPosOnRoot() : basic.diskBasicParam.getDirStartPos();
                    bufferOffset += skip;
                    pos[0] += skip;
                    sizeRemain[0] -= skip;
                }
                int skip = basic.diskBasicParam.getDirStartPosOnSector();
                bufferOffset += skip;
                pos[0] += skip;
                sizeRemain[0] -= skip;

                while (valid && !last && pos[0] < size[0]) {
                    finish = finishAssigningDirectory(pos, size, sizeRemain);
                    if (finish < 0) {
                        sizeRemain[0] = size[0] - pos[0];
                        pos = size;
                        break;
                    }
                    nitem.setDataPtr(indexNumber, gitem, sector, pos[0], buffer, bufferOffset, nextSec);
                    if (nitem.isNormalFile()) {
                        valid = !nitem.checkUsed(last);
                    }
                    pos[0] += nitem.getDataSize();
                    bufferOffset += nitem.getDataSize();
                    sizeRemain[0] -= nitem.getDataSize();
                    indexNumber++;
                }
                pos[0] -= size[0];
                pos[0] = adjustPositionAssigningDirectory(pos[0]);
            }
        }
        return valid;
    }

    /** ディレクトリをアサイン */
    public boolean assignDirectory(boolean isRoot, DiskBasicGroups groupItems, DiskBasicDirItem<T> dirItem) throws IOException {
        int indexNumber = 0;
        int[] pos = {0};
        boolean[] unuse = {false};
        int[] sizeRemain = {groupItems.getSize()};
        int finish = 0;
        int prevGrpNum = -1;
        for (int idx = 0; idx < groupItems.size() && finish >= -1; idx++) {
            DiskBasicGroupItem gitem = groupItems.get(idx);
            int grpNum = gitem.group;
            int trkNum = gitem.track;
            int sidNum = gitem.side;
            int divNum = gitem.divNum; // 分割番号
            int divNums = gitem.divNums; // 分割数
            DiskImageTrack track = basic.getTrack(trkNum, sidNum);
            if (track == null) {
                continue;
            }
            DiskBasicGroupItem nextGitem = idx + 1 < groupItems.size() ? groupItems.get(idx + 1) : null;

            for (int secNum = gitem.sectorStart; secNum <= gitem.sectorEnd && finish >= -1; secNum++) {
                DiskImageSector sector = track.getSector(secNum);
                if (sector == null) continue;

                byte[] buffer = sector.getSectorBuffer();
                int bufferOffset = 0;
                if (buffer == null) continue;

                int[] size = {sector.getSectorSize() / divNums};
                SectorParam nextSec = new SectorParam(trkNum, sidNum,
                        secNum < gitem.sectorEnd ? secNum + 1 : (nextGitem != null ? nextGitem.sectorStart : -1),
                        -1);

                // オフセットを足す
                bufferOffset += (size[0] * divNum);
                bufferOffset += pos[0];

                if (grpNum != prevGrpNum) {
                    // グループ番号が変わるときにスキップする位置
                    int skip = basic.diskBasicParam.getDirStartPosOnGroup();
                    bufferOffset += skip;
                    pos[0] += skip;
                    sizeRemain[0] -= skip;
                    prevGrpNum = grpNum;
                }

                if (idx == 0 && secNum == gitem.sectorStart) {
                    // ディレクトリエリア先頭をスキップする位置
                    int skip = isRoot ? basic.diskBasicParam.getDirStartPosOnRoot() : basic.diskBasicParam.getDirStartPos();
                    bufferOffset += skip;
                    pos[0] += skip;
                    sizeRemain[0] -= skip;
                }

                // ディレクトリエリア各セクタの先頭をスキップする位置
                int skip = basic.diskBasicParam.getDirStartPosOnSector();
                bufferOffset += skip;
                pos[0] += skip;
                sizeRemain[0] -= skip;

                while (pos[0] < size[0]) {
                    finish = finishAssigningDirectory(pos, size, sizeRemain);
                    if (finish < 0) {
                        // 終了 ポジションは次グループの先頭に
                        sizeRemain[0] -= (size[0] - pos[0]);
                        pos[0] = size[0];
                        break;
                    }
                    DiskBasicDirItem<T> nitem = dir.newItem(indexNumber, gitem, sector, pos[0], buffer, bufferOffset, nextSec, unuse);
                    if (finish > 0) {
                        nitem.used(false);
                    }
                    // 親ディレクトリを設定
                    nitem.setParent(dirItem);
                    // 子ディレクトリに追加
                    dirItem.addChild(nitem);

                    pos[0] += nitem.getDataSize();
                    bufferOffset += nitem.getDataSize();
                    sizeRemain[0] -= nitem.getDataSize();
                    indexNumber++;
                }

                pos[0] -= size[0];
                pos[0] = adjustPositionAssigningDirectory(pos[0]);
            }
        }

        return true;
    }

    /**
     * セクタをディレクトリとして初期化
     *
     * @param groupItems 確保したセクタリスト
     * @param fileSize   [in,out] サイズ ディレクトリを拡張した時は既存サイズに加算
     * @param sizeRemain [in,out] 残りサイズ
     * @param errinfo    [in,out] エラー情報
     * @return 0:正常, <0:エラー
     */
    public int initializeSectorsAsDirectory(DiskBasicGroups groupItems, int[] fileSize, int[] sizeRemain, DiskBasicError errinfo) throws IOException {
        int rc = 0;

        DiskBasicDirItem<?> newitem = basic.createDirItem(null, 0, null, 0);
        int dirSize = newitem.getDataSize();
        int indexNumber = 0;
        int[] pos = new int[1];
        int finish = 0;
        int prevGrpNum = -1;
        for (int idx = 0; idx < groupItems.size() && finish >= -1 && rc >= 0; idx++) {
            DiskBasicGroupItem gitem = groupItems.get(idx);
            int grpNum = gitem.group;
            int trkNum = gitem.track;
            int sidNum = gitem.side;
            int divNum = gitem.divNum; // 分割番号
            int divNums = gitem.divNums; // 分割数
            DiskImageTrack track = basic.getTrack(trkNum, sidNum);
            if (track == null) {
                // トラックがない！
                errinfo.setError(ERRV_NO_TRACK, grpNum, trkNum, sidNum);
                rc = -2;
                break;
            }

            DiskBasicGroupItem nextGitem = idx + 1 < groupItems.size() ? groupItems.get(idx + 1) : null;

            for (int secNum = gitem.sectorStart; secNum <= gitem.sectorEnd && finish >= -1 && rc >= 0; secNum++) {
                DiskImageSector sector = basic.getSector(trkNum, sidNum, secNum);
                if (sector == null) {
                    errinfo.setError(ERRV_NO_SECTOR, grpNum, trkNum, sidNum, secNum);
                    // セクタがない！
                    rc = -2;
                    break;
                }
                byte[] buffer = sector.getSectorBuffer();
                int bufferOffset = 0;
                if (buffer == null) {
                    errinfo.setError(ERRV_NO_SECTOR, grpNum, trkNum, sidNum, secNum);
                    // セクタがない！
                    rc = -2;
                    break;
                }

                int[] size = new int[] {sector.getSectorSize() / divNums};

                SectorParam nextSec = new SectorParam(trkNum, sidNum, secNum < gitem.sectorEnd ? secNum + 1 : (nextGitem != null ? nextGitem.sectorStart : -1), -1);
                // オフセットを足す
                bufferOffset += (size[0] * divNum);
                bufferOffset += pos[0];

                if (grpNum != prevGrpNum) {
                    // グループ番号が変わるときにスキップする位置
                    int skip = basic.diskBasicParam.getDirStartPosOnGroup();
                    bufferOffset += skip;
                    pos[0] += skip;
                    sizeRemain[0] -= skip;
                    prevGrpNum = grpNum;
                }

                if (idx == 0 && secNum == gitem.sectorStart) {
                    // ディレクトリエリア先頭をスキップする位置
                    int skip = basic.diskBasicParam.getDirStartPos();
                    bufferOffset += skip;
                    pos[0] += skip;
                    sizeRemain[0] -= skip;
                }

                // ディレクトリエリア各セクタの先頭をスキップする位置
                int skip = basic.diskBasicParam.getDirStartPosOnSector();
                bufferOffset += skip;
                pos[0] += skip;
                sizeRemain[0] -= skip;

                while (pos[0] < size[0]) {
                    finish = finishAssigningDirectory(pos, size, sizeRemain);
                    if (finish < 0) {
                        // 終了 ポジションは次グループの先頭に
                        fileSize[0] += (size[0] - pos[0]);
                        sizeRemain[0] -= (size[0] - pos[0]);
                        pos = size;
                        break;
                    }
                    // ディスク内に書き込む
                    newitem.setDataPtr(indexNumber, gitem, sector, pos[0], buffer, bufferOffset, nextSec);
                    // 初期値を入れる
                    newitem.initialData();

                    pos[0] += dirSize;
                    bufferOffset += dirSize;
                    fileSize[0] += dirSize;
                    sizeRemain[0] -= dirSize;
                    indexNumber++;
                }

                pos[0] -= size[0];
                pos[0] = adjustPositionAssigningDirectory(pos[0]);
            }
        }

        return rc;
    }

    /**
     * ディレクトリエリアのサイズに達したらアサイン終了するか
     *
     * @param pos        [in,out] ディレクトリの位置
     * @param size       [in,out] ディレクトリのセクタサイズ
     * @param sizeRemain [in,out] ディレクトリの残りサイズ
     * @return 0: 終了しない, 1: 強制的に未使用とする アサインは継続, -1: 現グループでアサイン終了。次のグループから継続, -2: 強制的にアサイン終了する
     */
    public int finishAssigningDirectory(int[] pos, int[] size, int[] sizeRemain) {
        return 0;
    }

    /**
     * ディレクトリアサインでセクタ毎に位置を調整する
     *
     * @param pos ディレクトリの位置
     * @return 調整後のディレクトリの位置
     */
    public int adjustPositionAssigningDirectory(int pos) {
        return pos;
    }

    /** ルートディレクトリの開始位置を得る */
    public void getStartNumOnRootDirectory(int[] trackNum, int[] sideNum, int[] sectorNum) {
        DiskImageTrack track = basic.getManagedTrack(basic.diskBasicParam.getDirStartSector() - basic.getSectorNumberBase(), sideNum, sectorNum);
        if (track != null) {
            trackNum[0] = track.getTrackNumber();
        }
    }

    /** ルートディレクトリの終了位置を得る */
    public void getEndNumOnRootDirectory(int[] trackNum, int[] sideNum, int[] sectorNum) {
        DiskImageTrack track = basic.getManagedTrack(basic.diskBasicParam.getDirEndSector() - basic.getSectorNumberBase(), sideNum, sectorNum);
        if (track != null) {
            trackNum[0] = track.getTrackNumber();
        }
    }

    /** 使用可能なディスクサイズを得る */
    public void getUsableDiskSize(int[] diskSize, int[] groupSize) throws IOException {
        groupSize[0] = 0;
        for (int pos = 0; pos <= basic.diskBasicParam.getFatEndGroup(); pos++) {
            int gnum = getGroupNumber(pos);
            if (gnum != basic.diskBasicParam.getGroupSystemCode()) groupSize[0]++;
        }
        diskSize[0] = groupSize[0] * basic.getSectorSize() * basic.diskBasicParam.getSectorsPerGroup();
    }

    /** 残りディスクサイズを計算 */
    public void calcDiskFreeSize(boolean wrote) throws IOException {
        fatAvailability.empty();
        for (int pos = 0; pos <= basic.diskBasicParam.getFatEndGroup(); pos++) {
            int fsize = 0;
            int grps = 0;
            int gnum = getGroupNumber(pos);
            FatAvailability fsts = FAT_AVAIL_USED;
            if (gnum == basic.diskBasicParam.getGroupUnusedCode()) {
                fsize = (basic.getSectorSize() * basic.diskBasicParam.getSectorsPerGroup());
                grps = 1;
                fsts = FAT_AVAIL_FREE;
            } else if (gnum == basic.diskBasicParam.getGroupSystemCode()) {
                fsts = FAT_AVAIL_SYSTEM;
            } else if (gnum >= basic.diskBasicParam.getGroupFinalCode()) {
                fsts = FAT_AVAIL_USED_LAST;
            }
            fatAvailability.add(fsts, fsize, grps);
        }
    }

    /** 残りディスクサイズをクリア */
    public void clearDiskFreeSize() {
        fatAvailability.emptyInit();
    }

    /** 残りディスクサイズを得る(CalcDiskFreeSize()で計算した結果) */
    public void getFreeDiskSize(int[] diskSize, int[] groupSize) {
        diskSize[0] = fatAvailability.getFreeSize();
        groupSize[0] = fatAvailability.getFreeGroups();
    }

    /** 残りディスクサイズを得る(CalcDiskFreeSize()で計算した結果) */
    public int getFreeDiskSize() {
        return fatAvailability.getFreeSize();
    }

    /** 残りグループ数を得る(CalcDiskFreeSize()で計算した結果) */
    public int getFreeGroupSize() {
        return fatAvailability.getFreeGroups();
    }

    /** FATの空き状況を配列で返す */
    public void getFatAvailability(int[] offset, List<FatAvailability>[] arr) {
        offset[0] = 0;
        arr[0] = fatAvailability.list;
    }

    /** データサイズ分のグループを確保する */
    public int allocateUnitGroups(int fileunitNum, DiskBasicDirItem<T> item, int dataSize, AllocateGroupFlags flags, DiskBasicGroups[] groupItems) throws IOException {
        int groups = 0;
        int rc = 0;
        boolean firstGroup = flags == AllocateGroupFlags.ALLOCATE_GROUPS_NEW;
        int[] sizeremain = {dataSize};
        int bytesPerGroup = basic.getSectorsPerGroup() * basic.getSectorSize();
        int groupNum = getEmptyGroupNumber();
        int limit = basic.getFatEndGroup() + 1;
        while (rc >= 0 && limit >= 0 && sizeremain[0] > 0) {
            if (groupNum == INVALID_GROUP_NUMBER) {
                rc = firstGroup ? -1 : -2;
                break;
            }
            setGroupNumber(groupNum, basic.diskBasicParam.getGroupFinalCode());
            if (firstGroup) {
                item.setStartGroup(fileunitNum, groupNum);
                firstGroup = false;
            }
            int nextGroupNum = getNextEmptyGroupNumber(groupNum);
            if (nextGroupNum == INVALID_GROUP_NUMBER || sizeremain[0] <= bytesPerGroup) {
                nextGroupNum = calcLastGroupNumber(nextGroupNum, sizeremain);
            }
            basic.getNumsFromGroup(groupNum, nextGroupNum, basic.getSectorSize(), sizeremain[0], groupItems[0]);
            setGroupNumber(groupNum, nextGroupNum);
            groupNum = nextGroupNum;
            sizeremain[0] -= bytesPerGroup;
            groups++;
            limit--;
        }
        if (limit < 0) {
            rc = firstGroup ? -1 : -2;
        }
        if (rc >= 0) {
            if (flags == AllocateGroupFlags.ALLOCATE_GROUPS_APPEND) {
                if (groupItems[0].size() > 0) {
                    rc = chainGroups(item.getStartGroup(0), groupItems[0].get(0).group);
                }
            }
        } else {
            deleteGroups(groupItems[0]);
            rc = -1;
        }
        return rc;
    }

    /** データサイズ分のグループを確保する */
    public int allocateGroups(DiskBasicDirItem<T> item, int dataSize, AllocateGroupFlags flags, DiskBasicGroups[] groupItems) throws IOException {
        return allocateUnitGroups(0, item, dataSize, flags, groupItems);
    }

    /** グループをつなげる */
    public int chainGroups(int groupNum, int appendGroupNum) throws IOException {
        int limit = basic.getFatEndGroup() + 1;
        while (limit >= 0) {
            int nextGroupNum = getGroupNumber(groupNum);
            if (nextGroupNum >= basic.diskBasicParam.getGroupFinalCode()) {
                setGroupNumber(groupNum, appendGroupNum);
                break;
            }
            groupNum = nextGroupNum;
            limit--;
        }
        return (limit >= 0 ? 0 : -1);
    }

    /** グループ番号から開始セクタ番号を得る */
    public int getStartSectorFromGroup(int groupNum) {
        return groupNum * basic.getSectorsPerGroup();
    }

    /** グループ番号から最終セクタ番号を得る */
    public int getEndSectorFromGroup(int groupNum, int nextGroup, int sectorStart, int sectorSize, int remainSize) {
        int sectorEnd = sectorStart + basic.getSectorsPerGroup() - 1;
        if (nextGroup >= basic.diskBasicParam.getGroupFinalCode()) {
            sectorEnd = sectorStart + (nextGroup - basic.diskBasicParam.getGroupFinalCode());
        }
        return sectorEnd;
    }

    /** データ領域の開始セクタを計算 */
    public int calcDataStartSectorPos() {
        return 0;
    }

    /** スキップするトラック番号 */
    public int calcSkippedTrack() {
        return 0x7fff;
    }

    public void getNumFromSectorPos(int sectorPos, int[] trackNum, int[] sideNum, int[] sectorNum) {
        getNumFromSectorPos(sectorPos, trackNum, sideNum, sectorNum, null, null);
    }

    /** セクタ位置(トラック0,サイド0,セクタ1を0とした通し番号)からトラック、サイド、セクタの各番号を得る */
    public void getNumFromSectorPos(int sectorPos, int[] trackNum, int[] sideNum, int[] sectorNum, int[] divNum, int[] divNums) {
        int selectedSide = basic.getSelectedSide();
        int numberingSector = basic.getNumberingSector();
        int sectorsPerTrack = basic.getSectorsPerTrackOnBasic();
        int sidesPerDisk = basic.getSidesPerDiskOnBasic();

        if (selectedSide >= 0) {
            trackNum[0] = sectorPos / sectorsPerTrack;
            sideNum[0] = selectedSide;
        } else {
            trackNum[0] = sectorPos / sectorsPerTrack / sidesPerDisk;
            sideNum[0] = (sectorPos / sectorsPerTrack) % sidesPerDisk;
        }
        sectorNum[0] = (sectorPos % sectorsPerTrack);

        if (numberingSector == 1) {
            sectorNum[0] += (sideNum[0] * sectorsPerTrack);
        }

        sideNum[0] = basic.getReversedSideNumber(sideNum[0]);
        trackNum[0] += basic.getTrackNumberBaseOnDisk();
        sideNum[0] += basic.getSideNumberBaseOnDisk();
        sectorNum[0] += basic.getSectorNumberBase();

        if (divNum != null) divNum[0] = 0;
        if (divNums != null) divNums[0] = 1;
    }

    /** セクタ位置(トラック0,サイド0,セクタ1を0とした通し番号)からトラック、セクタの各番号を得る */
    public void getNumFromSectorPosS(int sectorPos, int[] trackNum, int[] sectorNum) {
        int selectedSide = basic.getSelectedSide();
        int sectorsPerTrack = basic.getSectorsPerTrackOnBasic();
        int sidesPerDisk = basic.getSidesPerDiskOnBasic();
        if (selectedSide >= 0) {
            trackNum[0] = sectorPos / sectorsPerTrack;
            sectorNum[0] = (sectorPos % sectorsPerTrack);
        } else {
            trackNum[0] = sectorPos / (sectorsPerTrack * sidesPerDisk);
            sectorNum[0] = (sectorPos % (sectorsPerTrack * sidesPerDisk));
        }
        trackNum[0] += basic.getTrackNumberBaseOnDisk();
        sectorNum[0] += basic.getSectorNumberBase();
    }

    /** セクタ位置(トラック0,サイド0,セクタ1を0とした通し番号)からトラック、セクタの各番号を得る */
    public void getNumFromSectorPosT(int sectorPos, int[] trackNum, int[] sectorNum) {
        int sectorsPerTrack = basic.getSectorsPerTrackOnBasic();
        trackNum[0] = sectorPos / sectorsPerTrack;
        sectorNum[0] = (sectorPos % sectorsPerTrack);
        trackNum[0] += basic.getTrackNumberBaseOnDisk();
        sectorNum[0] += basic.getSectorNumberBase();
    }

    public int getSectorPosFromNum(int trackNum, int sideNum, int sectorNum) {
        return getSectorPosFromNum(trackNum, sideNum, sectorNum, 0, 0);
    }

    /** トラック、サイド、セクタの各番号からセクタ位置(トラック0,サイド0,セクタ1を0とした通し番号)を得る */
    public int getSectorPosFromNum(int trackNum, int sideNum, int sectorNum, int divNum, int divNums) {
        int selectedSide = basic.getSelectedSide();
        int numberingSector = basic.getNumberingSector();
        int sectorsPerTrack = basic.getSectorsPerTrackOnBasic();
        int sidesPerDisk = basic.getSidesPerDiskOnBasic();
        int sectorPos;

        trackNum -= basic.getTrackNumberBaseOnDisk();
        sideNum -= basic.getSideNumberBaseOnDisk();
        sectorNum -= basic.getSectorNumberBase();

        sideNum = basic.getReversedSideNumber(sideNum);

        if (selectedSide >= 0) {
            sectorPos = trackNum * sectorsPerTrack + sectorNum;
        } else {
            sectorPos = trackNum * sectorsPerTrack * sidesPerDisk;
            sectorPos += (sideNum % sidesPerDisk) * sectorsPerTrack;
            if (numberingSector == 1) {
                sectorPos += (sectorNum % sectorsPerTrack);
            } else {
                sectorPos += sectorNum;
            }
        }
        return sectorPos;
    }

    /** トラック、セクタの各番号からセクタ位置(トラック0,サイド0,セクタ1を0とした通し番号)を得る */
    public int getSectorPosFromNumS(int trackNum, int sectorNum) {
        int selectedSide = basic.getSelectedSide();
        int sectorsPerTrack = basic.getSectorsPerTrackOnBasic();
        int sidesPerDisk = basic.getSidesPerDiskOnBasic();
        int sectorPos;

        trackNum -= basic.getTrackNumberBaseOnDisk();
        sectorNum -= basic.getSectorNumberBase();

        if (selectedSide >= 0) {
            sectorPos = trackNum * sectorsPerTrack + sectorNum;
        } else {
            sectorPos = trackNum * sectorsPerTrack * sidesPerDisk + sectorNum;
        }
        return sectorPos;
    }

    /** トラック、セクタの各番号からセクタ位置(トラック0,サイド0,セクタ1を0とした通し番号)を得る */
    public int getSectorPosFromNumT(int trackNum, int sectorNum) {
        int sectorsPerTrack = basic.getSectorsPerTrackOnBasic();
        trackNum -= basic.getTrackNumberBaseOnDisk();
        sectorNum -= basic.getSectorNumberBase();
        return trackNum * sectorsPerTrack + sectorNum;
    }

    /** ルートディレクトリか */
    public boolean isRootDirectory(int groupNum) {
        return true;
    }

    /** サブディレクトリを作成できるか */
    public boolean canMakeDirectory() {
        return false;
    }

    /** ルートディレクトリのサイズを拡張できるか */
    public boolean canExpandRootDirectory() {
        return false;
    }

    /** サブディレクトリのサイズを拡張できるか */
    public boolean canExpandDirectory() {
        return false;
    }

    /** サブディレクトリを作成する前にディレクトリ名を編集する */
    public boolean renameOnMakingDirectory(String[] dirName) {
        return true;
    }

    /** サブディレクトリを作成する前の準備を行う */
    public boolean prepareToMakeDirectory(DiskBasicDirItem<T> item) throws IOException {
        return true;
    }

    /** 未使用のディレクトリアイテムを返す */
    public DiskBasicDirItem<T> getEmptyDirectoryItem(DiskBasicDirItem<T> parent, List<DiskBasicDirItem<T>> items, DiskBasicDirItem<T> pitem, DiskBasicDirItem<T>[] nextItem) throws IOException {
        DiskBasicDirItem<T> matchItem = null;
        if (items != null) {
            for (int i = 0; i < items.size(); i++) {
                DiskBasicDirItem<T> item = items.get(i);
                if (!item.isUsed()) {
                    matchItem = item;
                    if (nextItem != null) {
                        i++;
                        if (i < items.size() && !items.get(i).isUsed()) {
                            nextItem[0] = items.get(i);
                        } else {
                            nextItem[0] = null;
                        }
                    }
                    break;
                }
            }
        }
        return matchItem;
    }

    /** ディレクトリアイテムをリリース（機種依存） */
    public void releaseDirectoryItem(DiskBasicDirItem<T> item) {
    }

    /** サブディレクトリを作成した後の個別処理 */
    public void additionalProcessOnMadeDirectory(DiskBasicDirItem<T> item, DiskBasicGroups groupItems, DiskBasicDirItem<T> parentItem) throws IOException {
    }

    /** ディレクトリ拡張後の個別処理 */
    public boolean additionalProcessOnExpandedDirectory(DiskBasicDirItem<T> item, DiskBasicGroups groupItems, DiskBasicDirItem<T> parentItem) {
        return true;
    }

    /** フォーマットできるか */
    public boolean supportFormatting() {
        return true;
    }

    /** セクタデータを指定コードで埋める */
    public void fillSector(DiskImageTrack track, DiskImageSector sector) throws IOException {
        sector.fill(basic.diskBasicParam.getFillCodeOnFormat());
    }

    /** セクタデータを埋めた後の個別処理 */
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) throws IOException {
        return true;
    }

    /** ファイルの最終セクタのデータサイズを求める */
    public int calcDataSizeOnLastSector(DiskBasicDirItem<T> item, InputStream istream, OutputStream ostream, byte[] sectorBuffer, int sectorOffset, int sectorSize, int remainSize) throws IOException {
        return remainSize;
    }

    /** データの読み込み/比較の前処理 */
    public boolean prepareToAccessFile(int fileunitNum, DiskBasicDirItem<T> item, InputStream istream, OutputStream ostream, int[] fileSize, DiskBasicGroups groupItems, DiskBasicError errinfo) {
        return true;
    }

    /** データの読み込み/比較処理 */
    public int accessFile(int fileunitNum, DiskBasicDirItem<T> item, InputStream istream, OutputStream ostream, byte[] sectorBuffer, int sectorSize, int remainSize, int sectorNum, int sectorEnd) throws IOException {
        int modifiedSize = sectorSize;
        if (remainSize <= sectorSize) {
            // ファイルの最終セクタ
            modifiedSize = calcDataSizeOnLastSector(item, istream, ostream, sectorBuffer, 0, sectorSize, remainSize);
        }
        if (modifiedSize < 0) {
            // セクタなし
            return -2;
        }

        if (modifiedSize > 0) {
            byte[] temp;
            if (ostream != null) {
                // 書き出し
                temp = Arrays.copyOfRange(sectorBuffer, 0, modifiedSize);
                if (basic.isDataInverted()) Common.mem_invert(temp, temp.length);
                ostream.write(temp, 0, temp.length);
            }
            if (istream != null) {
                // 読み込んで比較
                temp = new byte[modifiedSize];
                istream.readNBytes(temp, 0, temp.length);
                if (basic.isDataInverted()) Common.mem_invert(temp, temp.length);

                if (!Arrays.equals(Arrays.copyOf(temp, temp.length), Arrays.copyOf(sectorBuffer, temp.length))) {
                    // データが異なる
                    return -1;
                }
            }
        }

        return sectorSize;
    }

    /** 内部ファイルをエクスポートする際に内容を変換 */
    public boolean convertDataForLoad(DiskBasicDirItem<T> item, InputStream istream, OutputStream ostream) throws IOException {
        byte[] buffer = new byte[1024];
        int len;
        while ((len = istream.read(buffer)) != -1) {
            ostream.write(buffer, 0, len);
        }
        return true;
    }

    /** エクスポートしたファイルをベリファイする際に内容を変換 */
    public boolean convertDataForVerify(DiskBasicDirItem<T> item, InputStream istream, OutputStream ostream) throws IOException {
        return convertDataForLoad(item, istream, ostream);
    }

    /** 書き込み可能か */
    public boolean supportWriting() {
        return true;
    }

    /** 指定したサイズが十分書き込めるか */
    public boolean isEnoughFileSize(int size) {
        return true;
    }

    /** ファイルをセーブする前にデータを変換 */
    public boolean convertDataForSave(DiskBasicDirItem<T> item, InputStream istream, OutputStream ostream) throws IOException {
        return convertDataForLoad(item, istream, ostream);
    }

    /** グループ確保時に最後のグループ番号を計算する */
    public int calcLastGroupNumber(int groupNum, int[] sizeRemain) {
        return groupNum;
    }

    /** ファイルをセーブする前の準備を行う */
    public boolean prepareToSaveFile(InputStream istream, int[] fileSize, DiskBasicDirItem<T> pitem, DiskBasicDirItem<T> nitem, DiskBasicError errinfo) throws IOException {
        return true;
    }

    /** データの書き込み処理 */
    public int writeFile(DiskBasicDirItem<T> item, InputStream istream, byte[] buffer, int size, int remain, int sectorNum, int groupNum, int nextGroup, int sectorEnd, int seqNum) throws IOException {
        boolean needEofCode = item.needCheckEofCode();
        int len = 0;
        if (remain <= size) {
            // 残り少ない
            if (remain < 0) remain = 0;
            if (needEofCode) {
                // 最終は終端コード
                if (remain > 1) istream.read(buffer, 0, remain - 1);
                if (remain > 0) buffer[remain - 1] = item.getEofCode();
            } else {
                if (remain > 0) istream.read(buffer, 0, remain);
            }
            if (size > remain) {
                // バッファの余りは0サプレス
                Arrays.fill(buffer, remain, size, (byte) 0);
            }
            len = remain;
        } else {
            // 継続
            istream.read(buffer, 0, size);
            len = size;
        }

        // 反転
        basic.invertMem(buffer, size);

        return len;
    }

    /** データの書き込み終了後の処理 */
    public void additionalProcessOnSavedFile(DiskBasicDirItem<T> item) throws IOException {
    }

    /** ファイル名変更後の処理 */
    public void additionalProcessOnRenamedFile(DiskBasicDirItem<T> item) throws IOException {
    }

    /** 属性変更後の処理 */
    public void additionalProcessOnChangedAttr(DiskBasicDirItem<T> item) {
    }

    /** ファイルを削除できるか */
    public boolean supportDeleting() {
        return true;
    }

    /** 指定したグループ番号のFAT領域を削除する */
    public void deleteGroups(DiskBasicGroups groupItems) throws IOException {
        for (DiskBasicGroupItem item : groupItems.getItems()) {
            // FATエントリを削除
            deleteGroupNumber(item.group);
        }
    }

    /** 指定したグループ番号のFAT領域を削除する */
    public void deleteGroupNumber(int groupNum) throws IOException {
        // FATに未使用コードを設定
        setGroupNumber(groupNum, basic.diskBasicParam.getGroupUnusedCode());
    }

    /** ファイル削除後の処理 */
    public boolean additionalProcessOnDeletedFile(DiskBasicDirItem<T> item) throws IOException {
        return true;
    }

    /** IPLや管理エリアの属性を得る */
    public void getIdentifiedData(DiskBasicIdentifiedData data) throws IOException {
    }

    /** IPLや管理エリアの属性をセット */
    public void setIdentifiedData(DiskBasicIdentifiedData data) throws IOException {
    }

    /** 管理エリアの開始グループをセット */
    public void setManagedStartGroup(int val) {
        managedStartGroup = val;
    }
}
