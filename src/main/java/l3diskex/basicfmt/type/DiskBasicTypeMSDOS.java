/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt.type;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;

import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasic.DiskBasicIdentifiedData;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.DiskBasicFat;
import l3diskex.basicfmt.DiskBasicFat.DiskBasicFatArea;
import l3diskex.basicfmt.DiskBasicParam;
import l3diskex.basicfmt.DiskBasicParam.DiskBasicFormat;
import l3diskex.basicfmt.diritem.DiskBasicDirItemMSDOS.DirectoryMsDos;
import l3diskex.diskimg.DiskImage.DiskImageDisk;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;

import static l3diskex.basicfmt.BasicCommon.DiskBasicFormatType.FORMAT_TYPE_UNKNOWN;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_DIRECTORY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_VOLUME_MASK;
import static l3diskex.basicfmt.DiskBasicTemplates.gDiskBasicTemplates;


/**
 * MS-DOSの処理
 * <p>
 * DiskBasicParam 固有のパラメータ
 *
 * @li MediaID  メディアID
 * @li IgnoreParameter  セクタ1のパラメータを無視するか
 */
public class DiskBasicTypeMSDOS extends DiskBasicTypeFAT12<DirectoryMsDos> {

    /** FAT BPB */
    @Serdes(bigEndian = false)
    public static final class fat_bpb_t {

        @Element(sequence = 1)
        public byte[] bs_JmpBoot = new byte[3];
        @Element(sequence = 2)
        public byte[] bs_OEMName = new byte[8];

        @Element(sequence = 3, value = "unsigned short")
        public int bpb_BytsPerSec;
        @Element(sequence = 4, value = "unsigned byte")
        public int bpb_SecPerClus;
        @Element(sequence = 5, value = "unsigned short")
        public int bpb_RsvdSecCnt;
        @Element(sequence = 6, value = "unsigned byte")
        public int bpb_NumFATs;
        @Element(sequence = 7, value = "unsigned short")
        public int bpb_RootEntCnt;
        @Element(sequence = 8, value = "unsigned short")
        public int bpb_TotSec16;
        @Element(sequence = 9, value = "unsigned byte")
        public int bpb_Media;
        @Element(sequence = 10, value = "unsigned short")
        public int bpb_FATSz16;
        @Element(sequence = 11, value = "unsigned short")
        public int bpb_SecPerTrk;
        @Element(sequence = 12, value = "unsigned short")
        public int bpb_NumHeads;
        @Element(sequence = 13)
        public int bpb_HiddSec;
    }

    public DiskBasicTypeMSDOS(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryMsDos> dir) {
        super(basic, fat, dir);
    }

    /**
     * FAT area checks
     */
    @Override
    public double checkFat(boolean isFormatting) throws IOException {
        // 重複チェック
        double valid_ratio = super.checkFat(isFormatting);

        if (valid_ratio < 0.0) return valid_ratio;

        // FATエリアの先頭がメディアIDであること
        int match = 0;
        DiskBasicFatArea bufs = fat.getDiskBasicFatArea();
        match = bufs.matchData8(0, basic.diskBasicParam.getMediaId());
        if (match != (basic.diskBasicParam.getValidNumberOfFats() > 0 ? basic.diskBasicParam.getValidNumberOfFats() : basic.diskBasicParam.getNumberOfFats())) {
            valid_ratio -= 0.5;
        }

        // 最終グループ番号を計算
        int max_grp_on_fat = basic.diskBasicParam.getSectorsPerFat() * basic.getSectorSize() * 2 / 3;
        int max_grp_on_prm = (basic.getSidesPerDiskOnBasic() * basic.getTracksPerSide() * basic.getSectorsPerTrackOnBasic() - basic.diskBasicParam.getDirEndSector()) / basic.getSectorsPerGroup() + 1;

        basic.diskBasicParam.setFatEndGroup(max_grp_on_fat > max_grp_on_prm ? max_grp_on_prm : max_grp_on_fat);

        return valid_ratio;
    }

    @Override
    public double parseParamOnDisk(boolean isFormatting) throws IOException {
        double valid_ratio = 1.0;

        if (!basic.diskBasicParam.getVariousBoolParam("IgnoreParameter")) {
            valid_ratio = parseMSDOSParamOnDisk(basic.getDisk(), isFormatting);
        } else {
            if (basic.getFatEndGroup() == 0) {
                int max_grp_on_prm = (basic.getSidesPerDiskOnBasic() * basic.getTracksPerSide() * basic.getSectorsPerTrackOnBasic() - basic.diskBasicParam.getDirEndSector()) / basic.getSectorsPerGroup() + 1;
                basic.diskBasicParam.setFatEndGroup(max_grp_on_prm);
            }
        }

        byte[] ipl = basic.diskBasicParam.getVariousStringParam("IPLCompareString").getBytes();
        if (ipl.length > 0) {
            DiskImageSector sector = basic.getSector(0, 0, 1);
            if (sector == null) return -1.0;
            if (sector.find(ipl, ipl.length) < 0) {
                valid_ratio = 0.0;
            }
        }

        return valid_ratio;
    }

    public double parseMSDOSParamOnDisk(DiskImageDisk disk, boolean isFormatting) throws IOException {
        if (isFormatting) return 1.0;

        int nums = 0;
        int valids = 0;

        // MS-DOS ディスク上のパラメータを読む
        DiskImageSector sector = basic.getSector(0, 0, 1);
        if (sector == null) return -1.0;
        byte[] datas = sector.getSectorBuffer();
        if (datas == null) return -1.0;
        fat_bpb_t bpb = new fat_bpb_t();
        Serdes.Util.deserialize(new ByteArrayInputStream(datas), bpb);

        nums++;
        if (bpb.bpb_SecPerClus != 0) {
            // cluster size
            valids++;
        }
        nums++;
        if (disk.getSectorSize() == bpb.bpb_BytsPerSec) {
            // sector size
            valids++;
        }
        nums++;
        if (disk.getSidesPerDisk() >= bpb.bpb_NumHeads) {
            // side count (disk)
            valids++;
        }
        nums++;
        if (basic.diskBasicParam.getSidesPerDiskOnBasic() == bpb.bpb_NumHeads) {
            // side count (BASIC)
            valids++;
        }
        nums++;
        if (basic.diskBasicParam.getSectorsPerTrackOnBasic() == bpb.bpb_SecPerTrk) {
            // sector per track (BASIC)
            valids++;
        }

        // FATエリアの先頭メディアIDが一致するか
        DiskBasicFatArea bufs = fat.getDiskBasicFatArea();
        nums++;
        if (bufs.matchData8(0, 0, basic.diskBasicParam.getMediaId())) {
            valids++;
        }

        // ディスク内のパラメータで更新する
        if (nums == valids) {
            basic.diskBasicParam.setSidesPerDiskOnBasic(bpb.bpb_NumHeads);
            basic.diskBasicParam.setSectorsPerGroup(bpb.bpb_SecPerClus);
            basic.diskBasicParam.setReservedSectors(bpb.bpb_RsvdSecCnt);
            basic.diskBasicParam.setNumberOfFats(bpb.bpb_NumFATs);
            basic.diskBasicParam.setSectorsPerFat(bpb.bpb_FATSz16);
            basic.diskBasicParam.setDirEntryCount(bpb.bpb_RootEntCnt);

            basic.diskBasicParam.setDirStartSector(-1);
            basic.diskBasicParam.setDirEndSector(-1);
            basic.diskBasicParam.calcDirStartEndSector(bpb.bpb_BytsPerSec);

            basic.diskBasicParam.setSectorsPerTrackOnBasic(bpb.bpb_SecPerTrk);

            basic.diskBasicParam.setMediaId((byte) bpb.bpb_Media);

            // トラック数
            int tracks = bpb.bpb_TotSec16;
            if (tracks > 0) {
                tracks = tracks / basic.diskBasicParam.getSidesPerDiskOnBasic() / basic.diskBasicParam.getSectorsPerTrackOnBasic();
                basic.diskBasicParam.setTracksPerSideOnBasic(tracks);
            }
        }

        // さらにJmpBootとOEMNameをチェック
        byte[] jmp = basic.diskBasicParam.getVariousStringParam("JumpBoot").getBytes();
        int len = jmp.length < bpb.bs_JmpBoot.length ? jmp.length : bpb.bs_JmpBoot.length;
        nums++;
        if (len > 0 && Arrays.equals(Arrays.copyOfRange(bpb.bs_JmpBoot, 0, len), Arrays.copyOf(jmp, len))) {
            valids++;
        }

        byte[] oem = basic.diskBasicParam.getVariousStringParam("OEMName").getBytes();
        len = oem.length < bpb.bs_OEMName.length ? oem.length : bpb.bs_OEMName.length;
        nums += 5;
        if (len > 0 && Arrays.equals(Arrays.copyOfRange(bpb.bs_OEMName, 0, len), Arrays.copyOf(oem, len))) {
            valids += 5;
        }

        // 最終グループ番号を計算
        int maxGrpOnFat = basic.diskBasicParam.getSectorsPerFat() * basic.getSectorSize() * 2 / 3;
        int maxGrpOnPrm = (basic.diskBasicParam.getSidesPerDiskOnBasic() * basic.getTracksPerSide() * basic.diskBasicParam.getSectorsPerTrackOnBasic() - basic.diskBasicParam.getDirEndSector()) / basic.diskBasicParam.getSectorsPerGroup() + 1;

        basic.diskBasicParam.setFatEndGroup(Math.min(maxGrpOnFat, maxGrpOnPrm));

        // テンプレートに一致するものがあるか
        DiskBasicParam param = gDiskBasicTemplates.findType(basic.diskBasicParam.getBasicCategoryName(), basic.diskBasicParam.getBasicTypeName(), basic.diskBasicParam.getSidesPerDiskOnBasic(), basic.diskBasicParam.getSectorsPerTrackOnBasic());
        if (param != null) {
            basic.diskBasicParam.setBasicDescription(param.getBasicDescription());
        }

        double validRatio = 0.0;
        if (nums > 0) {
            validRatio = (double) valids / nums;
        }
        return validRatio;
    }

    @Override
    public boolean isRootDirectory(int groupNum) {
        // オフセット未満だったらルート
        return groupNum <= 1;
    }

    @Override
    public boolean renameOnMakingDirectory(String[] dirName) {
        // 空や"."で始まるディレクトリは作成不可
        if (dirName[0].isEmpty() || dirName[0].startsWith(".")) {
            return false;
        }
        return true;
    }

    /** サブディレクトリを作成した後の個別処理 */
    @Override
    public void additionalProcessOnMadeDirectory(DiskBasicDirItem<DirectoryMsDos> item,
                                                 DiskBasicGroups groupItems,
                                                 DiskBasicDirItem<DirectoryMsDos> parentItem) throws IOException {
        if (groupItems.size() <= 0) return;

        // カレントと親ディレクトリのエントリを作成する
        DiskBasicGroupItem gitem = groupItems.get(0);

        DiskImageSector sector = basic.getDisk().getSector(gitem.track, gitem.side, gitem.sectorStart);

        byte[] buf = sector.getSectorBuffer();
        int bufOffset = 0;
        DiskBasicDirItem<DirectoryMsDos> newItem = basic.createDirItem(sector, 0, buf, bufOffset);

        // current entry
        newItem.copyData(item.getRawData());
        newItem.setFileNamePlain(".");
        newItem.setFileAttr(FORMAT_TYPE_UNKNOWN, FILE_TYPE_DIRECTORY_MASK.getValue(), 0);

        // parent entry
        bufOffset += newItem.getDataSize();
        newItem.setDataPtr(0, null, sector, 0, buf, bufOffset, null);
        if (parentItem != null) {
            // 親がサブディレクトリ
            newItem.copyData(parentItem.getRawData());
        } else {
            // 親がルート
            newItem.copyData(item.getRawData());
            newItem.setStartGroup(0, 0);
        }
        newItem.setFileCreateDateTime(item.getFileCreateDateTime());
        newItem.setFileModifyDateTime(item.getFileModifyDateTime());
        newItem.setFileAccessDateTime(item.getFileAccessDateTime());
        newItem.setFileNamePlain("..");
        newItem.setFileAttr(FORMAT_TYPE_UNKNOWN, FILE_TYPE_DIRECTORY_MASK.getValue(), 0);
    }

    /// セクタデータを埋めた後の個別処理
    /// フォーマット IPLの書き込み
    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) throws IOException {
        if (!createBiosParameterBlock("\u00eb\u003c\u0090", "FAT12", null)) {
            return false;
        }

        // volume label
        DiskBasicFormat fmt = basic.getFormatType();
        if (fmt.hasVolumeName()) {
            int dirStart = basic.diskBasicParam.getReservedSectors() + basic.diskBasicParam.getNumberOfFats() * basic.diskBasicParam.getSectorsPerFat();
            DiskImageSector sec = basic.getSectorFromSectorPos(dirStart);
            DiskBasicDirItem<DirectoryMsDos> ditem = dir.newItem(sec, 0, sec.getSectorBuffer(), 0);

            ditem.setFileNamePlain(data.getVolumeName());
            ditem.setFileAttr(FORMAT_TYPE_UNKNOWN, FILE_TYPE_VOLUME_MASK.getValue(), 0);
            LocalDateTime tm = LocalDateTime.now();
            ditem.setFileModifyDateTime(tm);
        }

        return true;
    }

    /// BIOS Parameter Block を作成
    public boolean createBiosParameterBlock(String jmp, String name, byte[][] secBuf) throws IOException {
        DiskImageSector sec = basic.getSector(0, 0, 1);
        if (sec == null) return false;
        byte[] buf = sec.getSectorBuffer();
        if (buf == null) return false;

        if (secBuf != null) secBuf[0] = buf;

        sec.fill((byte) 0);

        DiskBasicTypeMSDOS.fat_bpb_t hed = new fat_bpb_t();
        Serdes.Util.deserialize(new ByteArrayInputStream(buf), hed);

        byte[] s_jmp = basic.diskBasicParam.getVariousStringParam("JumpBoot").getBytes();
        if (s_jmp.length > 0) {
            jmp = new String(s_jmp);
        }
        int len = Math.min(jmp.length(), hed.bs_JmpBoot.length);
        System.arraycopy(jmp.getBytes(), 0, hed.bs_JmpBoot, 0, len);

        hed.bpb_BytsPerSec = basic.getSectorSize();
        hed.bpb_SecPerClus = basic.diskBasicParam.getSectorsPerGroup();
        hed.bpb_RsvdSecCnt = basic.diskBasicParam.getReservedSectors();
        hed.bpb_NumFATs = basic.diskBasicParam.getNumberOfFats();
        hed.bpb_RootEntCnt = basic.diskBasicParam.getDirEntryCount();

        byte[] s_name = basic.diskBasicParam.getVariousStringParam("OEMName").getBytes();
        if (s_name.length > 0) name = new String(s_name);
        // 上記パラメータ領域をまたがって設定可能にする
        len = Math.min(name.length(), 16);
        Arrays.fill(hed.bs_OEMName, (byte) 0x20);
        System.arraycopy(name.getBytes(), 0, hed.bs_OEMName, 0, len);

        len = basic.getTracksPerSide() * basic.diskBasicParam.getSectorsPerTrackOnBasic() * basic.diskBasicParam.getSidesPerDiskOnBasic();
        hed.bpb_TotSec16 = len;
        hed.bpb_Media = basic.diskBasicParam.getMediaId();
        hed.bpb_FATSz16 = basic.diskBasicParam.getSectorsPerFat();
        hed.bpb_SecPerTrk = basic.diskBasicParam.getSectorsPerTrackOnBasic();
        hed.bpb_NumHeads = basic.diskBasicParam.getSidesPerDiskOnBasic();

        // set the media ID in the first FAT entry
        setGroupNumber(0, 0xffff_ff00 | basic.diskBasicParam.getMediaId());
        setGroupNumber(1, 0xffff_ffff);

        return true;
    }

    @Override
    public void getIdentifiedData(DiskBasicIdentifiedData data) {
        // volume label
        DiskBasicDirItem<DirectoryMsDos> ditem = dir.findFileByAttrOnRoot(FILE_TYPE_VOLUME_MASK.getValue(), FILE_TYPE_VOLUME_MASK.getValue() | FILE_TYPE_DIRECTORY_MASK.getValue(), null);
        if (ditem != null) {
            data.setVolumeName(((DiskBasicDirItem<DirectoryMsDos>) ditem).getFileNamePlainStr());
            data.setVolumeNameMaxLength(ditem.getFileNameStrSize());
        }
    }

    @Override
    public void setIdentifiedData(DiskBasicIdentifiedData data) throws IOException {
        DiskBasicFormat fmt = basic.getFormatType();

        // volume label
        if (fmt.hasVolumeName()) {
            modifyOrMakeVolumeLabel(data.getVolumeName());
        }
    }

    /** ボリュームラベルを更新 なければ作成 */
    protected boolean modifyOrMakeVolumeLabel(String filename) throws IOException {
        DiskBasicDirItem<DirectoryMsDos>[] nextItem = new DiskBasicDirItem[1];
        // ボリュームラベルがあるか
        DiskBasicDirItem<DirectoryMsDos> item = dir.findFileByAttrOnRoot(FILE_TYPE_VOLUME_MASK.getValue(),
                FILE_TYPE_VOLUME_MASK.getValue() | FILE_TYPE_DIRECTORY_MASK.getValue(), null);
        if (item == null) {
            // try to allocate a new item
            item = dir.getEmptyItemOnRoot(null, nextItem);
            if (item == null) {
                // allocation failed
                return false;
            } else {
                item.setEndMark(nextItem[0]);
            }
            item.clearData();
            item.setFileAttr(FORMAT_TYPE_UNKNOWN, FILE_TYPE_VOLUME_MASK.getValue(), 0);
        }
        item.setFileNameStr(filename);
        item.used(true);

        return true;
    }
}
