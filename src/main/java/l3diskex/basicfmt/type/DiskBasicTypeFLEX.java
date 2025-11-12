package l3diskex.basicfmt.type;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import l3diskex.Common;
import l3diskex.Utils;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasic.DiskBasicIdentifiedData;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.DiskBasicFat;
import l3diskex.basicfmt.DiskBasicParam.DiskBasicFormat;
import l3diskex.basicfmt.DiskBasicType;
import l3diskex.basicfmt.diritem.DiskBasicDirItemFLEX.DirectoryFlex;
import l3diskex.basicfmt.diritem.DiskBasicDirItemFLEX.FlexPointer;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskImage.DiskImageTrack;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;

import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_FREE;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_USED_LAST;
import static l3diskex.basicfmt.DiskBasicType.AllocateGroupFlags.ALLOCATE_GROUPS_NEW;


/**
 * FLEXの処理
 * <p>
 * DiskBasicParam
 *
 * <li>DirStartPositionOnSector : ディレクトリエントリの開始位置</li>
 */
public class DiskBasicTypeFLEX extends DiskBasicType<DirectoryFlex> {

    private static final Logger logger = System.getLogger(DiskBasicTypeFLEX.class.getName());

    @Serdes
    public static class FlexSir {

        @Element(sequence = 1)
        public byte[] reserved0 = new byte[16];
        @Element(sequence = 2)
        public byte[] volumeLabel = new byte[8];
        @Element(sequence = 3)
        public byte[] reserved1 = new byte[3];
        @Element(sequence = 4)
        public short volumeNumber;
        @Element(sequence = 5)
        public byte freeStartTrack;
        @Element(sequence = 6)
        public byte freeStartSector;
        @Element(sequence = 7)
        public byte freeLastTrack;
        @Element(sequence = 8)
        public byte freeLastSector;
        @Element(sequence = 9)
        public short numOfFreeSectors;
        @Element(sequence = 10)
        public byte cMonth;
        @Element(sequence = 11)
        public byte cDay;
        @Element(sequence = 12)
        public byte cYear;
        @Element(sequence = 13)
        public byte maxTrack;
        @Element(sequence = 14)
        public byte maxSector;

        static final int SIZE = 16 + 8 + 3 + 2 + 1 + 1 + 1 + 1 + 2 + 1 + 1 + 1 + 1 + 1;
    }

    @Serdes
    static class StFlexFsm {

        @Element(sequence = 1)
        public byte track;
        @Element(sequence = 2)
        public byte sector;
        @Element(sequence = 3)
        public byte count;

        static final int SIZE = 3;
    }

    /** SIRエリア */
    private FlexSir flexSir;

    /** */
    public DiskBasicTypeFLEX(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryFlex> dir) {
        super(basic, fat, dir);

        flexSir = null;

        if (basic.getGroupsPerTrack() <= 0) {
            basic.setGroupsPerTrack(basic.getGroupsPerSector() * basic.getSectorsPerTrackOnBasic());
        }
    }

    /** 論理セクタ番号からセクタ内の位置を得る */
    private int sectorBufferOffset(int sectorNumber) {
        int pos = (sectorNumber - 1) % basic.getGroupsPerSector();
        return pos * basic.getSectorSize() / basic.getGroupsPerSector();
    }

    /** 論理セクタサイズ */
    private int logSecSiz(int sectorSize) {
        return sectorSize / basic.getGroupsPerSector();
    }

    /** FAT位置をセット (seq_numをセット) */
    @Override
    public void setGroupNumber(int num, int val) throws IOException {
        int[] divNum = {0};
        DiskImageSector sector = basic.getSectorFromSectorPos(num, divNum);
        if (sector == null) {
            // why?
            return;
        }
        byte[] b = sector.getSectorBuffer(sectorBufferOffset(divNum[0] + 1));
        if (b == null) {
            // why?
            return;
        }
        FlexPointer p = FlexPointer.serialize(b);
        p.seqNum = (short) val;
        sector.copy(p.deserialize(), sectorBufferOffset(divNum[0] + 1), FlexPointer.SIZE);
    }

    /** FATオフセットを返す (FLEXではグループ番号=セクタ位置) */
    @Override
    public int getGroupNumber(int num) {
        return num;
    }

    /** 使用しているグループ番号か (FLEXでは常にtrue) */
    @Override
    public boolean isUsedGroupNumber(int num) {
        return true;
    }

    /** 次のグループ番号を得る (FLEXではグループはFATでなくチェインでたどるため、通常はINVALID_GROUP_NUMBER) */
    @Override
    public int getNextGroupNumber(int num, int sector_pos) {
        return INVALID_GROUP_NUMBER;
    }

    /** 空きFAT位置を返す */
    @Override
    public int getEmptyGroupNumber() throws IOException {
        DiskImageSector sector = null;
        int groupNum = INVALID_GROUP_NUMBER;
        int staTrackNum = flexSir.freeStartTrack & 0xff;
        int staLSectorNum = flexSir.freeStartSector & 0xff;
        if (staTrackNum == 0 && staLSectorNum == 0) {
            // no free space ?
            return INVALID_GROUP_NUMBER;
        }
        int[] divNum = {0};
        // グループ番号を得る
        groupNum = getSectorPosFromNumS(staTrackNum, staLSectorNum);
        sector = basic.getSectorFromSectorPos(groupNum, divNum);
        if (sector == null) {
            // no free space ?
            return INVALID_GROUP_NUMBER;
        }

        // 次のポインタをフリーセクタポインタに設定
        byte[] b = sector.getSectorBuffer(sectorBufferOffset(divNum[0] + 1));
        if (b == null) {
            // why?
            return INVALID_GROUP_NUMBER;
        }
        FlexPointer p = new FlexPointer();
        Serdes.Util.deserialize(new ByteArrayInputStream(b), p);
        // 空き領域の開始ポインタを更新
        flexSir.freeStartTrack = p.nextTrack;
        flexSir.freeStartSector = p.nextSector;
        int size = flexSir.numOfFreeSectors;
        size--;
        if (size <= 0 || (flexSir.freeStartTrack == 0 && flexSir.freeStartSector == 0)) {
            // 空きがなくなった
            size = 0;
            flexSir.freeStartTrack = 0;
            flexSir.freeStartSector = 0;
            flexSir.freeLastTrack = 0;
            flexSir.freeLastSector = 0;
        }
        flexSir.numOfFreeSectors = (short) size;
        // 予約済みにする
        p.nextTrack = 0;
        p.nextSector = 0;

        return groupNum;
    }

    /** 次の空きFAT位置を返す */
    @Override
    public int getNextEmptyGroupNumber(int currentGroup) throws IOException {
        // 次の空き位置候補
        int nextGroupNum = getEmptyGroupNumber();
        if (nextGroupNum == INVALID_GROUP_NUMBER) {
            return INVALID_GROUP_NUMBER;
        }
        // 現在のセクタに次のセクタへのポインタをセット
        if (chainGroups(currentGroup, nextGroupNum) < 0) {
            return INVALID_GROUP_NUMBER;
        }

        return nextGroupNum;
    }

    /**
     * エリアをチェック
     *
     * @param isFormatting フォーマット中か
     * @return 1.0: 正常, 0.0 - 1.0: 警告あり, <0.0: エラーあり
     */
    @Override
    public double checkFat(boolean isFormatting) throws IOException {
        // SIR area
        DiskImageSector sector = basic.getSectorFromSectorPos(2);
        if (sector == null) {
            return -1.0;
        }
        byte[] b = sector.getSectorBuffer(sectorBufferOffset(2 + 1));
        if (b == null) {
            return -1.0;
        }
        FlexSir flex = new FlexSir();
        Serdes.Util.deserialize(new ByteArrayInputStream(b), flex);

        for (int i = 0; i < flex.reserved0.length; i++) {
            if (flex.reserved0[i] != 0) {
                return -1.0;
            }
        }
        if (flex.maxTrack == 0 || flex.maxSector == 0) {
            return -1.0;
        }

        // 最終グループ番号
        basic.setFatEndGroup(((flex.maxTrack & 0xff) + 1) * (flex.maxSector & 0xff) - 1);

        flexSir = flex;

        double validRatio = 1.0;

        // DIRエリアのチェインをチェック
        int dirCount = 0;
        int dirStartLSector = basic.getDirStartSector(); // logical
        int dirEndLSector = basic.getSectorsPerTrackOnBasic() * basic.getSidesPerDiskOnBasic() * basic.getGroupsPerSector(); // logical

        sector = basic.getSectorFromSectorPos(dirStartLSector - 1);
        int sectorOffset = sectorBufferOffset(dirStartLSector);
        for (int lSectorPos = dirStartLSector; lSectorPos <= dirEndLSector; lSectorPos++) {
            if (sector == null) {
                validRatio = -1.0;
                break;
            }

            FlexPointer p = new FlexPointer();
            b = sector.getSectorBuffer(sectorOffset);
            Serdes.Util.deserialize(new ByteArrayInputStream(b), p);

            dirCount++;

            if (p.nextTrack == 0 && p.nextSector == 0) {
                break;
            }

            sector = basic.getSectorFromSectorPos(getSectorPosFromNumS(p.nextTrack & 0xff, p.nextSector & 0xff));
            //sector = basic.getSector(p.nextTrack, phySecNum(p.nextSector));
            sectorOffset = sectorBufferOffset(p.nextSector & 0xff);
        }
        // DIRエリアの最終セクタ
        int dirEnd = basic.getDirStartSector() + dirCount - 1;
        basic.setDirEndSector(dirEnd); // logical

        return validRatio;
    }

    /**
     * ディスクから各パラメータを取得＆必要なパラメータを計算
     *
     * @param isFormatting フォーマット中か
     * @return 1.0: 正常, 0.0 - 1.0: 警告あり, <0.0: エラーあり
     */
    @Override
    public double parseParamOnDisk(boolean isFormatting) throws IOException {
        if (isFormatting) return 1.0;

        if (flexSir == null) {
            DiskImageSector sector = basic.getSectorFromSectorPos(2);
            if (sector == null) {
                return -1.0;
            }
            FlexSir flex = new FlexSir();
            byte[] bb = sector.getSectorBuffer(sectorBufferOffset(2 + 1));
            Serdes.Util.deserialize(new ByteArrayInputStream(bb), flex);
            flexSir = flex;
        }

        logger.log(Level.TRACE, "FLEX: sir.maxTrack: %d".formatted(flexSir.maxTrack & 0xff));
        if (flexSir.maxTrack > 0) {
            basic.setTracksPerSideOnBasic((flexSir.maxTrack & 0xff) + 1);
        }
        logger.log(Level.TRACE, "FLEX: sir.maxSector: %d".formatted( flexSir.maxSector & 0xff));
        if (flexSir.maxSector > 0) {
            basic.setSectorsPerTrackOnBasic((flexSir.maxSector & 0xff) / basic.getSidesPerDiskOnBasic() / basic.getGroupsPerSector());
        }

        return 1.0;
    }

    /** ルートディレクトリのセクタリストを計算 */
    @Override
    public boolean calcGroupsOnRootDirectory(int startSector, int endSector, DiskBasicGroups groupItems) throws IOException {
        boolean valid = true;

        groupItems.clear();

        // ディレクトリのチェインをたどる
        int dirSize = 0;
        int limit = basic.getFatEndGroup() + 1;
        int[] trackNum = {0};
        int[] sideNum = {0};
        int secNum = 1;
        int[] divNum = {0};
        int[] numOfDivs = {1};
        // 開始セクタ
        DiskImageSector sector = basic.getManagedSector(startSector - 1, trackNum, sideNum, null, divNum, numOfDivs);
        while (limit >= 0) {
            if (sector == null) {
                valid = false;
                break;
            }
            secNum = sector.getSectorNumber();

            byte[] buffer = sector.getSectorBuffer(sectorBufferOffset(divNum[0] + 1));
            if (buffer == null) {
                valid = false;
                break;
            }
            byte[] b = sector.getSectorBuffer(sectorBufferOffset(divNum[0] + 1));

            groupItems.add(0, 0, trackNum[0], sideNum[0], secNum, secNum, divNum[0], numOfDivs[0]);

            dirSize += logSecSiz(sector.getSectorSize());

            FlexPointer p = new FlexPointer();
            Serdes.Util.deserialize(new ByteArrayInputStream(b), p);

            // 次のセクタなし
            if (p.nextTrack == 0 && p.nextSector == 0) {
                break;
            }

            limit--;

            // 次のセクタを得る
            sector = basic.getSectorFromSectorPos(getSectorPosFromNumS(p.nextTrack & 0xff, p.nextSector & 0xff), trackNum, sideNum, divNum, numOfDivs);
        }
        groupItems.setSize(dirSize);

        // 最終セクタ番号を更新
        int secPos = getSectorPosFromNum(trackNum[0], sideNum[0], secNum, 0, 1);
        secPos -= (basic.getManagedTrackNumber() * basic.getSectorsPerTrackOnBasic() * basic.getSidesPerDiskOnBasic());
        basic.setDirEndSector(secPos + 1); // logical

        if (limit < 0) {
            valid = false;
        }
        return valid;
    }

    /** 使用可能なディスクサイズを得る */
    @Override
    public void getUsableDiskSize(int[] diskSize, int[] groupSize) {
        groupSize[0] = basic.getFatEndGroup() + 1;
        diskSize[0] = groupSize[0] * basic.getSectorSize() * basic.getSectorsPerGroup() / basic.getGroupsPerSector();
    }

    /** 残りディスクサイズを計算 */
    @Override
    public void calcDiskFreeSize(boolean wrote) throws IOException {
        int fsize = 0;
        int groups = 0;

        //logger.log(Level.TRACE, "DiskBasicTypeFLEX::CalcDiskFreeSize");

        fatAvailability.empty();

//        fatAvailability.setCount(basic.diskBasicParam.getFatEndGroup() + 1, FAT_AVAIL_USED.getValue());

        // SIR area
        //DiskImageDisk disk = basic.getDisk();
        DiskImageSector sector;
        //FlexSir flex = (FlexSir) sector.getSectorBuffer();

        int trackNum = flexSir.freeStartTrack & 0xff;
        int lSectorNum = flexSir.freeStartSector & 0xff;
        int limit = basic.getFatEndGroup() + 1;
        int[] divNum = {0};
        int[] numOfDivs = {1};
        while ((trackNum != 0 || lSectorNum != 0) && limit >= 0) {
            int sectorPos = getSectorPosFromNumS(trackNum, lSectorNum);
            sector = basic.getSectorFromSectorPos(sectorPos, divNum, numOfDivs);
            //sector = basic.getSector(trackNum, sectorNum);
            if (sector == null) {
                // error
                break;
            }
            if (sectorPos < fatAvailability.count()) {
                if (fatAvailability.get(sectorPos) == FAT_AVAIL_FREE) {
                    // 既に空きエリアにしているのに同じセクタにきている
                    // 無限ループしている？
                    break;
                }
                fatAvailability.set(sectorPos, FAT_AVAIL_FREE);
            }

            // セクタ先頭4バイトは除く
            fsize += (logSecSiz(sector.getSectorSize()) - 4);
            groups++;

            //logger.log(Level.TRACE, "trk:%d sec:%d size:%d".formatted(trackNum, sectorNum, fsize));

            byte[] b = sector.getSectorBuffer(sectorBufferOffset(divNum[0] + 1));
            FlexPointer p = new FlexPointer();
            Serdes.Util.deserialize(new ByteArrayInputStream(b), p);
            trackNum = p.nextTrack & 0xff;
            lSectorNum = p.nextSector & 0xff;
            limit--;
        }

        // ディレクトリエントリのグループ
        List<DiskBasicDirItem<DirectoryFlex>> items = dir.getCurrentItems(null);
        if (items != null) {
            for (DiskBasicDirItem<DirectoryFlex> item : items) {
                if (item == null || !item.isUsed()) continue;

                // 最後のグループ
                int groupCount = item.getGroupCount();
                if (groupCount > 0) {
                    int groupNum = item.getGroup(groupCount - 1).group;
                    if (groupNum <= basic.getFatEndGroup()) {
                        fatAvailability.set(groupNum, FAT_AVAIL_USED_LAST);
                    }
                }
            }
        }

        fatAvailability.setFreeSize(fsize);
        fatAvailability.setFreeGroups(groups);
    }

    /** データサイズ分のグループを確保する */
    @Override
    public int allocateUnitGroups(int fileUnitNum, DiskBasicDirItem<DirectoryFlex> item, int dataSize, AllocateGroupFlags flags, DiskBasicGroups[] groupItems) throws IOException {
        //logger.log(Level.TRACE, "DiskBasicTypeFLEX::AllocateGroups {");

        //int fileSize = dataSize;
        int groups = 0;

        // FAT
        int rc = 0;
        boolean firstGroup = flags == ALLOCATE_GROUPS_NEW;
        int groupNum = flags == ALLOCATE_GROUPS_NEW ? INVALID_GROUP_NUMBER : item.getLastGroup();

        // 1セクタ当たり4バイトはチェイン用のリンクポインタになるので減算
        int bytesPerGroup = basic.getSectorsPerGroup() * (logSecSiz(basic.getSectorSize()) - 4);
        // ランダムアクセスファイルか
        int randomFile = item.getFileAttr().getOrigin();
        if (flags == ALLOCATE_GROUPS_NEW && randomFile > 0) {
            // ランダムアクセスファイル
            // インデックス(FSM)セクタを確保
            for (int idx = 0; idx < randomFile; idx++) {
                groupNum = firstGroup ? getEmptyGroupNumber() : getNextEmptyGroupNumber(groupNum);
                if (groupNum == INVALID_GROUP_NUMBER) {
                    // 空きなし
                    rc = firstGroup ? -1 : -2;
                    break;
                }
                // セクタをクリア
                int[] divNum = {0};
                DiskImageSector sector = basic.getSectorFromGroup(groupNum, divNum, null);
                if (sector != null) {
                    sector.fill((byte) 0, logSecSiz(basic.getSectorSize()), sectorBufferOffset(divNum[0] + 1));
                }
                // グループ番号の書き込み
                if (firstGroup) {
                    item.setStartGroup(fileUnitNum, groupNum);
                    firstGroup = false;
                }
            }
        }

        int sizeRemain = dataSize;
        int limit = basic.getFatEndGroup() + 1;
        while (rc >= 0 && limit >= 0 && sizeRemain > 0) {
            groupNum = firstGroup ? getEmptyGroupNumber() : getNextEmptyGroupNumber(groupNum);
            if (groupNum == INVALID_GROUP_NUMBER) {
                // 空きなし
                rc = firstGroup ? -1 : -2;
                break;
            }
            // グループ番号の書き込み
            if (firstGroup) {
                item.setStartGroup(fileUnitNum, groupNum);
                firstGroup = false;
            }

            //logger.log(Level.TRACE, "  groupNum:0x%03x".formatted(groupNum));

            basic.getNumsFromGroup(groupNum, 0, logSecSiz(basic.getSectorSize()), sizeRemain, groupItems[0]);

            // シーケンス番号設定
            setGroupNumber(groupNum, groups + 1);

            sizeRemain -= bytesPerGroup;
            groups++;

            limit--;
        }
        if (limit < 0) {
            // too large or infinit loop
            rc = firstGroup ? -1 : -2;
        }
        if (rc == 0) {
            // 最終グループ番号
            item.setLastGroup(groupNum);

            // ランダムアクセスファイルの場合は、インデックスを作成する
            if (randomFile > 0) {
                DiskBasicGroups randomGroups = new DiskBasicGroups();
                int prevTrack = -1;
                int prevGroup = 0xfff_ffff;
                int indexCount = 0;
                int indexStart = 0;
                for (int i = 0; i < groupItems[0].size(); i++) {
                    DiskBasicGroupItem groupItem = groupItems[0].get(i);
                    if (prevGroup == groupItem.group) {
                        // 同じならスキップ
                        continue;
                    }
                    if (prevTrack == groupItem.track && prevGroup + 1 == groupItem.group) {
                        // グループ番号が連続している
                        indexCount++;
                    } else {
                        // グループ番号が連続していない
                        if (indexCount > 0) {
                            randomGroups.add(indexStart, indexCount, 0, 0, 0, 0, 0, basic.getGroupsPerSector());
                        }
                        indexStart = groupItem.group;
                        indexCount = 1;
                    }
                    prevTrack = groupItem.track;
                    prevGroup = groupItem.group;
                }
                if (indexCount > 0) {
                    randomGroups.add(indexStart, indexCount, 0, 0, 0, 0, 0, basic.getGroupsPerSector());
                }
                // インデックス(FSM)セクタに書き込む
                DiskImageSector iSector = null;
                int current_idx_start = item.getStartGroup(fileUnitNum);
                int idx = 0;
                boolean finished = false;
                for (int sec = 0; sec < randomFile && !finished; sec++) {
                    int[] divNum = {0};
                    iSector = basic.getSectorFromGroup(current_idx_start, divNum, null);
                    if (iSector == null) break;
                    byte[] buf = iSector.getSectorBuffer(sectorBufferOffset(divNum[0] + 1));
                    if (buf == null) break;
                    for (int pos = 4; pos < logSecSiz(iSector.getSectorSize()); pos += StFlexFsm.SIZE) {
                        DiskBasicGroupItem rItem = randomGroups.get(idx);
                        int[] trackNum = {0}, sectorNum = {0};
                        getNumFromSectorPosS(rItem.group, trackNum, sectorNum);
                        DiskBasicTypeFLEX.StFlexFsm fsm = new StFlexFsm();
                        Serdes.Util.deserialize(new ByteArrayInputStream(buf, pos, buf.length - pos), fsm);
                        fsm.track = (byte) trackNum[0];
                        fsm.sector = (byte) sectorNum[0];
                        fsm.count = (byte) rItem.next;
                        // TODO write back
                        idx++;
                        if (idx >= randomGroups.size()) {
                            finished = true;
                            break;
                        }
                    }
                    FlexPointer p = new FlexPointer();
                    Serdes.Util.deserialize(new ByteArrayInputStream(buf), p);
                    current_idx_start = getSectorPosFromNumS(p.nextTrack & 0xff, p.nextSector & 0xff);
                }
            }
        } else {
            // エラー時
            // 確保した領域を削除
            deleteGroups(groupItems[0]);
            // 空き領域をチェインする
            int last_group = item.getLastGroup();
            if (last_group != 0) {
                int[] divNum = {0};
                DiskImageSector sector = basic.getSectorFromGroup(last_group, divNum, null);
                if (sector != null) {
                    byte[] b = sector.getSectorBuffer(sectorBufferOffset(divNum[0] + 1));
                    if (b != null) {
                        FlexPointer p = new FlexPointer();
                        Serdes.Util.deserialize(new ByteArrayInputStream(b), p);
                        flexSir.freeLastTrack = p.nextTrack;
                        flexSir.freeLastSector = p.nextSector;
                        p.nextTrack = 0;
                        p.nextSector = 0;
                        p.seqNum = 0;

                        remakeChainOnFreeArea();

                        rc = -1;
                    }
                }
            }
        }

        //logger.log(Level.TRACE, "rc: %d }".formatted(rc));
        return rc;
    }

    /** グループをつなげる */
    @Override
    public int chainGroups(int groupNum, int appendGroupNum) throws IOException {
        // 現在のセクタに次のセクタへのポインタをセット
        int[] divNum = {0};
        DiskImageSector sector = basic.getSectorFromSectorPos(groupNum, divNum);
        if (sector == null) {
            // why?
            return -1;
        }
        byte[] b = sector.getSectorBuffer(sectorBufferOffset(divNum[0] + 1));
        if (b == null) {
            // why?
            return -1;
        }
        FlexPointer p = new FlexPointer();
        Serdes.Util.deserialize(new ByteArrayInputStream(b), p);
        int[] nextTrackNum = {0};
        int[] nextSectorNum = {0};
        getNumFromSectorPosS(appendGroupNum, nextTrackNum, nextSectorNum);
        p.nextTrack = (byte) nextTrackNum[0];
        p.nextSector = (byte) nextSectorNum[0];
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Serdes.Util.serialize(p, baos);
        sector.copy(baos.toByteArray(), baos.size(), sectorBufferOffset(divNum[0] + 1)); // TODO is write back?

        return 0;
    }

    /** グループ番号から開始セクタ番号を得る */
    @Override
    public int getStartSectorFromGroup(int groupNum) {
        return groupNum;
    }

    /** グループ番号から最終セクタ番号を得る */
    @Override
    public int getEndSectorFromGroup(int groupNum, int nextGroup, int sectorStart, int sectorSize, int remainSize) {
        return groupNum;
    }

    /** セクタ位置(トラック0,サイド0,セクタ1を0とした通し番号)からトラック、サイド、セクタの各番号を得る */
    @Override
    public void getNumFromSectorPos(int sectorPos, int[] trackNum, int[] sideNum, int[] sectorNum, int[] divNum, int[] divNums) {
        int selectedSide = basic.getSelectedSide();
        int numberingSector = basic.getNumberingSector();
        int sectorsPerTrack = basic.getSectorsPerTrackOnBasic();
        int sidesPerDisk = basic.getSidesPerDiskOnBasic();
        int groupsPerSector = basic.getGroupsPerSector();
        int groupsPerTrack = basic.getGroupsPerTrack();

        int trkSidNum = sectorPos / groupsPerTrack;
        if (selectedSide >= 0) {
            // 1S
            trackNum[0] = trkSidNum;
            sideNum[0] = selectedSide;
        } else {
            // 2D, 2HD
            trackNum[0] = trkSidNum / sidesPerDisk;
            sideNum[0] = trkSidNum % sidesPerDisk;
        }
        sectorNum[0] = ((sectorPos % groupsPerTrack) / groupsPerSector);
        if (divNum != null) divNum[0] = (sectorPos % groupsPerTrack) % groupsPerSector;

        if (numberingSector == 1) {
            // トラックごとに連番の場合
            sectorNum[0] += (sideNum[0] * sectorsPerTrack);
        }

        // サイド番号を逆転するか
        sideNum[0] = basic.getReversedSideNumber(sideNum[0]);

        trackNum[0] += basic.getTrackNumberBaseOnDisk();
        sideNum[0] += basic.getSideNumberBaseOnDisk();
        sectorNum[0] += basic.getSectorNumberBase();

        if (divNums != null) divNums[0] = groupsPerSector;
    }

    /** セクタ位置(トラック0,サイド0,セクタ1を0とした通し番号)からトラック、セクタの各番号を得る */
    @Override
    public void getNumFromSectorPosS(int sectorPos, int[] trackNum, int[] sectorNum) {
        int selectedSide = basic.getSelectedSide();
        int groupsPerTrack = basic.getGroupsPerTrack();
        int sidesPerDisk = basic.getSidesPerDiskOnBasic();

        if (selectedSide >= 0) {
            // 1S
            trackNum[0] = sectorPos / groupsPerTrack;
            sectorNum[0] = (sectorPos % groupsPerTrack) + 1;
        } else {
            // 2D, 2HD
            trackNum[0] = sectorPos / (groupsPerTrack * sidesPerDisk);
            sectorNum[0] = (sectorPos % (groupsPerTrack * sidesPerDisk)) + 1;
        }
    }

    /** トラック、サイド、セクタの各番号からセクタ位置(トラック0,サイド0,セクタ1を0とした通し番号)を得る */
    @Override
    public int getSectorPosFromNum(int trackNum, int sideNum, int sectorNum, int divNum, int numOfDivs) {
        int groupsPerTrack = basic.getGroupsPerTrack();
        int selectedSide = basic.getSelectedSide();
        int numberingSector = basic.getNumberingSector();
        //int sectorsPerTrack = basic.getSectorsPerTrackOnBasic();
        int sidesPerDisk = basic.getSidesPerDiskOnBasic();
        int sectorPos;

        trackNum -= basic.getTrackNumberBaseOnDisk();
        sideNum -= basic.getSideNumberBaseOnDisk();
        sectorNum -= basic.getSectorNumberBaseOnDisk();

        // サイド番号を逆転するか
        sideNum = basic.getReversedSideNumber(sideNum);

        if (selectedSide >= 0) {
            // 1S
            sectorPos = trackNum * groupsPerTrack;
            sectorPos += sectorNum * numOfDivs + divNum;
        } else {
            // 2D, 2HD
            sectorPos = trackNum * sidesPerDisk * groupsPerTrack;
            if (numberingSector == 1) {
                sectorPos += sectorNum * numOfDivs + divNum;
            } else {
                sectorPos += (sideNum % sidesPerDisk) * groupsPerTrack;
                sectorPos += sectorNum * numOfDivs + divNum;
            }
        }

        return sectorPos;
    }

    /** トラック、セクタの各番号からセクタ位置(トラック0,サイド0,セクタ1を0とした通し番号)を得る */
    @Override
    public int getSectorPosFromNumS(int trackNum, int sectorNum) {
        int selectedSide = basic.getSelectedSide();
        int groupsPerTrack = basic.getGroupsPerTrack();
        int sidesPerDisk = basic.getSidesPerDiskOnBasic();
        int sectorPos;

        if (selectedSide >= 0) {
            // 1S
            sectorPos = trackNum * groupsPerTrack + sectorNum - 1;
        } else {
            // 2D, 2HD
            sectorPos = trackNum * groupsPerTrack * sidesPerDisk + sectorNum - 1;
        }
        return sectorPos;
    }

    /** ルートディレクトリか */
    @Override
    public boolean isRootDirectory(int groupNum) {
        return false;
    }

    /** サブディレクトリを作成できるか */
    @Override
    public boolean canMakeDirectory() {
        return false;
    }

    /** フォーマット時セクタデータを指定コードで埋める */
    @Override
    public void fillSector(DiskImageTrack track, DiskImageSector sector) throws IOException {
        for (int divNum = 0; divNum < basic.getGroupsPerSector(); divNum++) {
            sector.fill(basic.getFillCodeOnFormat(), logSecSiz(basic.getSectorSize()), sectorBufferOffset(divNum + 1));

            if (track.getTrackNumber() > 0 && track.getSideNumber() < basic.getSidesPerDiskOnBasic()) {
                // セクタの先頭にリンクを作成
                byte[] b = sector.getSectorBuffer(sectorBufferOffset(divNum + 1));
                if (b != null) {
                    FlexPointer p = new FlexPointer();
                    Serdes.Util.deserialize(new ByteArrayInputStream(b), p);

                    int nextTrack = track.getTrackNumber();
                    int nextSector = (sector.getSectorNumber() - 1) * basic.getGroupsPerSector() + 2 + divNum;
                    if (nextSector >= (basic.getSectorsPerTrackOnBasic() * basic.getSidesPerDiskOnBasic() * basic.getGroupsPerSector() + 1)) {
                        nextTrack++;
                        nextSector = 1;
                    }
                    if (nextTrack < basic.getTracksPerSide()) {
                        p.nextTrack = (byte) nextTrack;
                        p.nextSector = (byte) nextSector;
                    } else {
                        p.nextTrack = 0;
                        p.nextSector = 0;
                    }
                    p.seqNum = 0;

                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    Serdes.Util.serialize(p, baos);
                    sector.copy(baos.toByteArray(), baos.size(), sectorBufferOffset(divNum + 1)); // TODO check is write back
                }
            }
        }
    }

    /** フォーマット時セクタデータを埋めた後の個別処理 */
    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) throws IOException {
        // SIR area
        DiskImageSector sector = basic.getSectorFromSectorPos(2);
        if (sector == null) return false;
        byte[] b = sector.getSectorBuffer(sectorBufferOffset(2 + 1));
        if (b == null) return false;
        FlexSir flex = new FlexSir();
        Serdes.Util.deserialize(new ByteArrayInputStream(b), flex);

        flexSir = flex;

        sector.fill((byte) 0, logSecSiz(basic.getSectorSize()), sectorBufferOffset(2 + 1));

        // SIRエリアを設定

        flexSir.freeStartTrack = 1;
        flexSir.freeStartSector = 1;
        flexSir.freeLastTrack = (byte) (basic.getTracksPerSide() - 1);
        flexSir.freeLastSector = (byte) (basic.getSectorsPerTrackOnBasic() * basic.getSidesPerDiskOnBasic() * basic.getGroupsPerSector());

        int numOfSectors = (basic.getTracksPerSide() - 1) * basic.getSectorsPerTrackOnBasic() * basic.getSidesPerDiskOnBasic() * basic.getGroupsPerSector();

        flexSir.numOfFreeSectors = (short) numOfSectors;

        flexSir.maxTrack = (byte) (basic.getTracksPerSide() - 1);
        flexSir.maxSector = (byte) (basic.getSectorsPerTrackOnBasic() * basic.getSidesPerDiskOnBasic() * basic.getGroupsPerSector());

        LocalDateTime tm = LocalDateTime.now();
        flexSir.cMonth = (byte) (tm.getMonth().ordinal() + 1);
        flexSir.cDay = (byte) tm.getDayOfMonth();
        flexSir.cYear = (byte) (tm.getYear() % 100);

        // volume name and number
        setIdentifiedData(data);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Serdes.Util.serialize(flexSir, baos);
        sector.copy(baos.toByteArray(), sectorBufferOffset(2 + 1), baos.size()); // TODO check is write back

        // DIRエリア

        int dirStartLSector = basic.getDirStartSector() - 1; // logical
        int dirEndLSector = basic.getSectorsPerTrackOnBasic() * basic.getSidesPerDiskOnBasic() * basic.getGroupsPerSector() - 1; // logical
        DiskImageSector prevSector = null;
        int prevLSectorPos = 0;
        int nextTrack = 0;
        int nextSector = 0;
        for (int lSectorPos = dirStartLSector; lSectorPos <= dirEndLSector; lSectorPos++) {
            DiskImageSector currentSector = basic.getSectorFromSectorPos(lSectorPos);
            if (prevSector != null) {
                b = prevSector.getSectorBuffer(sectorBufferOffset(prevLSectorPos + 1));
                if (b != null) {
                    FlexPointer p = new FlexPointer();
                    Serdes.Util.deserialize(new ByteArrayInputStream(b), p);

                    nextSector = lSectorPos + 1;
                    p.nextTrack = (byte) nextTrack;
                    p.nextSector = (byte) nextSector;
                    p.seqNum = 0;

                    baos = new ByteArrayOutputStream();
                    Serdes.Util.deserialize(p, baos);
                    prevSector.copy(baos.toByteArray(), baos.size(), sectorBufferOffset(prevLSectorPos + 1)); // TODO check is write back
                }
            }
            if (currentSector == null) {
                // トラック0のセクタ数はほかのトラックより少ない場合がある
                continue;
            }
            prevSector = currentSector;
            prevLSectorPos = lSectorPos;
            currentSector.fill((byte) 0, logSecSiz(basic.getSectorSize()), sectorBufferOffset(lSectorPos + 1));
        }
        if (prevSector != null) {
            b = prevSector.getSectorBuffer(sectorBufferOffset(prevLSectorPos + 1));
            if (b != null) {
                FlexPointer p = new FlexPointer();
                Serdes.Util.deserialize(new ByteArrayInputStream(b), p);

                p.nextTrack = 0;
                p.nextSector = 0;
                p.seqNum = 0;

                baos = new ByteArrayOutputStream();
                Serdes.Util.deserialize(p, baos);
                prevSector.copy(baos.toByteArray(), baos.size(), sectorBufferOffset(prevLSectorPos + 1));
            }
        }

        return true;
    }

    /** データの読み込み/比較処理 */
    @Override
    public int accessFile(int fileUnitNum, DiskBasicDirItem<DirectoryFlex> item, InputStream iStream, OutputStream oStream, byte[] sectorBuffer, int sectorSize, int remainSize, int sectorNum, int sectorEnd) throws IOException {
        byte[] buf = Arrays.copyOfRange(sectorBuffer, 4, sectorBuffer.length);
        int size = (sectorSize - 4) < remainSize ? (sectorSize - 4) : remainSize;

        byte[] temp;
        if (oStream != null) {
            // 書き出し
            temp = Arrays.copyOfRange(buf, 0, size);
            if (basic.isDataInverted()) Common.invertMemory(temp, temp.length);
            oStream.write(temp, 0, temp.length);
        }
        if (iStream != null) {
            // 読み込んで比較
            temp = new byte[size];
            iStream.readNBytes(temp, 0, temp.length);
            if (basic.isDataInverted()) Common.invertMemory(temp, temp.length);

            if (!Arrays.equals(temp, 0, size, buf, 0, size)) {
                // データが異なる
                return -1;
            }
        }
        return size;
    }

    /** データの書き込み処理 */
    @Override
    public int writeFile(DiskBasicDirItem<DirectoryFlex> item, InputStream iStream, byte[] buffer, int size, int remain, int sectorNum, int groupNum, int nextGroup, int sectorEnd, int seqNum) throws IOException {
        boolean needEofCode = item.needCheckEofCode();

        // セクタの4バイト目から
        buffer = Arrays.copyOfRange(buffer, 4, buffer.length);
        size -= 4;

        int len = 0;
        if (remain <= size) {
            // 残り少ない
            if (remain < 0) remain = 0;
            if (needEofCode) {
                // 最終は終端コード
                if (remain > 1) iStream.readNBytes(buffer, 0, remain - 1);
                if (remain > 0) buffer[remain - 1] = basic.getTextTerminateCode();
            } else {
                if (remain > 0) iStream.readNBytes(buffer, 0, remain);
            }
            if (size > remain) {
                // バッファの余りは0サプレス
                Arrays.fill(buffer, remain, size, (byte) 0);
            }
            len = remain;
        } else {
            // 継続
            iStream.readNBytes(buffer, 0, size);
            len = size;
        }

        // 反転
        basic.invertMemory(buffer, size);

        return len;
    }

    /** 指定したグループ番号のFAT領域を削除する (FLEXでは何もしない) */
    @Override
    public void deleteGroupNumber(int groupNum) {
    }

    /** ファイル削除後の処理 */
    @Override
    public boolean additionalProcessOnDeletedFile(DiskBasicDirItem<DirectoryFlex> item) throws IOException {
        DirectoryFlex d = item.getData();

        DiskImageSector sector;
        FlexPointer p;

        int startTrackNum = d.startTrack & 0xff;
        int startSectorNum = d.startSector & 0xff;
        int lastTrackNum = d.lastTrack & 0xff;
        int lastSectorNum = d.lastSector & 0xff;
        int[] divNum = {0};

        // 削除した領域を空き領域の最後につなげる

        if ((flexSir.freeStartTrack == 0 && flexSir.freeStartSector == 0) ||
                (flexSir.freeLastTrack == 0 && flexSir.freeLastSector == 0)) {
            // 空きがない
            flexSir.freeStartTrack = (byte) startTrackNum;
            flexSir.freeStartSector = (byte) startSectorNum;
            flexSir.freeLastTrack = (byte) lastTrackNum;
            flexSir.freeLastSector = (byte) lastSectorNum;
        } else {
            // チェインする
            sector = basic.getSectorFromSectorPos(getSectorPosFromNumS(flexSir.freeLastTrack & 0xff, flexSir.freeLastSector & 0xff), divNum);
            if (sector == null) return false;
            byte[] b = sector.getSectorBuffer(sectorBufferOffset(divNum[0] + 1));
            if (b == null) return false;

            p = new FlexPointer();
            Serdes.Util.deserialize(new ByteArrayInputStream(b), p);

            p.nextTrack = (byte) startTrackNum;
            p.nextSector = (byte) startSectorNum;
            flexSir.freeLastTrack = (byte) lastTrackNum;
            flexSir.freeLastSector = (byte) lastSectorNum;

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Serdes.Util.serialize(p, baos);
            sector.copy(baos.toByteArray(), baos.size(), sectorBufferOffset(divNum[0] + 1)); // TODO check is this write back?
        }

        // 空き領域のチェインを作り直す

        remakeChainOnFreeArea();

        return true;
    }

    /** 空きエリアのチェインを作り直す */
    public void remakeChainOnFreeArea() throws IOException {
        DiskImageSector sector;
        FlexPointer p;

        // 空き領域のリスト
        int freeTrackNum = flexSir.freeStartTrack & 0xff;
        int freeSectorNum = flexSir.freeStartSector & 0xff;
        DiskBasicGroups groupItems = new DiskBasicGroups();
        while (freeTrackNum != 0 && freeSectorNum != 0) {
            int freeGroupNum = getSectorPosFromNumS(freeTrackNum, freeSectorNum);
            groupItems.add(freeGroupNum, 0, freeTrackNum, 0, freeSectorNum, 0);
            int[] divNum = {0};
            sector = basic.getSectorFromSectorPos(getSectorPosFromNumS(freeTrackNum, freeSectorNum), divNum);
            if (sector == null) break;
            byte[] b = sector.getSectorBuffer(sectorBufferOffset(divNum[0] + 1));
            if (b == null) break;

            p = new FlexPointer();
            Serdes.Util.deserialize(new ByteArrayInputStream(b), p);

            freeTrackNum = p.nextTrack & 0xff;
            freeSectorNum = p.nextSector & 0xff;
        }

        // 空き領域をソートしてチェインを作り直す
        groupItems.sortItems();
        int groupItemsCount = groupItems.size();
        for (int index = 0; index < groupItemsCount; index++) {
            DiskBasicGroupItem gItem = groupItems.get(index);
            int[] divNum ={0};
            sector = basic.getSectorFromSectorPos(getSectorPosFromNumS(gItem.track, gItem.sectorStart), divNum);
            if (sector == null) break;
            byte[] b = sector.getSectorBuffer(sectorBufferOffset(divNum[0] + 1));
            if (b == null) break;

            p = new FlexPointer();
            Serdes.Util.deserialize(new ByteArrayInputStream(b), p);

            if (index == 0) {
                // first
                flexSir.freeStartTrack = (byte) gItem.track;
                flexSir.freeStartSector = (byte) gItem.sectorStart;
            }
            if ((index + 1) != groupItemsCount) {
                DiskBasicGroupItem nextGroupItem = groupItems.get(index + 1);
                p.nextTrack = (byte) nextGroupItem.track;
                p.nextSector = (byte) nextGroupItem.sectorStart;
                //p.seqNum = index + 1;
                p.seqNum = 0;
            } else {
                // last
                p.nextTrack = 0;
                p.nextSector = 0;
                p.seqNum = 0;

                flexSir.freeLastTrack = (byte) gItem.track;
                flexSir.freeLastSector = (byte) gItem.sectorStart;
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Serdes.Util.serialize(p, baos);
            sector.copy(baos.toByteArray(), baos.size(), sectorBufferOffset(divNum[0] + 1)); // TODO check is write back
        }
        flexSir.numOfFreeSectors = (short) groupItemsCount;
    }

    /** IPLや管理エリアの属性を得る */
    @Override
    public void getIdentifiedData(DiskBasicIdentifiedData data) {
        // volume label
        String vol = new String(flexSir.volumeLabel);
        data.setVolumeName(vol);
        data.setVolumeNameMaxLength(flexSir.volumeLabel.length);
        // volume number
        data.setVolumeNumber(flexSir.volumeNumber);
        // volume date
        int y = (flexSir.cYear & 0xff) % 100;
        LocalDate tm = LocalDate.of(y + (y >= 0 && y < 80 ? 100 : 0),
                (flexSir.cMonth & 0xff) - 1,
                flexSir.cDay & 0xff);
        data.setVolumeDate(Utils.formatYMDStr(tm));
    }

    /** IPLや管理エリアの属性をセット */
    @Override
    public void setIdentifiedData(DiskBasicIdentifiedData data) {
        DiskBasicFormat fmt = basic.getFormatType();

        // volume label
        if (fmt.hasVolumeName()) {
            byte[] vol = data.getVolumeName().getBytes();
            System.arraycopy(vol, vol.length, flexSir.volumeLabel, 0, flexSir.volumeLabel.length);
        }
        // volume number
        if (fmt.hasVolumeNumber()) {
            flexSir.volumeNumber = (short) data.getVolumeNumber();
        }
    }
}
