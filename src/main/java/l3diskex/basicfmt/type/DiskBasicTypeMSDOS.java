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

import static l3diskex.basicfmt.BasicCommon.FORMAT_TYPE_UNKNOWN;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_DIRECTORY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_VOLUME_MASK;
import static l3diskex.basicfmt.DiskBasicTemplates.gDiskBasicTemplates;


/**
 * MS-DOSの処理
 * <p>
 * DiskBasicParam 固有のパラメータ
 *
 * <li>MediaID  メディアID</li>
 * <li>IgnoreParameter  セクタ1のパラメータを無視するか</li>
 */
public class DiskBasicTypeMSDOS extends DiskBasicTypeFAT12<DirectoryMsDos> {

    /** FAT BPB */
    @Serdes(bigEndian = false)
    public static final class FatBpb {

        @Element(sequence = 1)
        public byte[] jumpBoot = new byte[3]; // bs_JmpBoot
        @Element(sequence = 2)
        public byte[] oemName = new byte[8]; // bs_OEMName

        @Element(sequence = 3, value = "unsigned short")
        public int bytesPerSec; // bpb_BytsPerSec
        @Element(sequence = 4, value = "unsigned byte")
        public int sectorPerCluster; // bpb_SecPerClus
        @Element(sequence = 5, value = "unsigned short")
        public int reservedSectorCount; // bpb_RsvdSecCnt
        @Element(sequence = 6, value = "unsigned byte")
        public int numberOfFats; // bpb_NumFATs
        @Element(sequence = 7, value = "unsigned short")
        public int rootEntryCount; // bpb_RootEntCnt
        @Element(sequence = 8, value = "unsigned short")
        public int totalSectors16; // bpb_TotSec16
        @Element(sequence = 9, value = "unsigned byte")
        public int media; // bpb_Media
        @Element(sequence = 10, value = "unsigned short")
        public int fatSize16; // bpb_FATSz16
        @Element(sequence = 11, value = "unsigned short")
        public int sectorsPerTrack; // bpb_SecPerTrk
        @Element(sequence = 12, value = "unsigned short")
        public int numberOfHeads; // bpb_NumHeads
        @Element(sequence = 13)
        public int hiddenSectors; // bpb_HiddSec
    }

    public static final int FORMAT_TYPE_MSDOS = 3;
    public static final int FORMAT_TYPE_CDOS2 = 32;
    public static final int FORMAT_TYPE_LOSA = 31;

    @Override
    public boolean isSupported(int typeNumber) {
        return typeNumber == FORMAT_TYPE_MSDOS ||
                typeNumber == FORMAT_TYPE_LOSA ||
                typeNumber == FORMAT_TYPE_CDOS2;
    }

    @Override
    public void init(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryMsDos> dir) {
        super.init(basic, fat, dir);
    }

    /**
     * FAT area checks
     */
    @Override
    public double checkFat(boolean isFormatting) throws IOException {
        // 重複チェック
        double validRatio = super.checkFat(isFormatting);

        if (validRatio < 0.0) return validRatio;

        // FATエリアの先頭がメディアIDであること
        int match = 0;
        DiskBasicFatArea bufs = fat.getDiskBasicFatArea();
        match = bufs.matchData8(0, basic.getMediaId());
        if (match != (basic.getValidNumberOfFats() > 0 ? basic.getValidNumberOfFats() : basic.getNumberOfFats())) {
            validRatio -= 0.5;
        }

        // 最終グループ番号を計算
        int maxGroupOnFat = basic.getSectorsPerFat() * basic.getSectorSize() * 2 / 3;
        int maxGroupOnParam = (basic.getSidesPerDiskOnBasic() * basic.getTracksPerSide() * basic.getSectorsPerTrackOnBasic() - basic.getDirEndSector()) / basic.getSectorsPerGroup() + 1;

        basic.setFatEndGroup(maxGroupOnFat > maxGroupOnParam ? maxGroupOnParam : maxGroupOnFat);

        return validRatio;
    }

    @Override
    public double parseParamOnDisk(boolean isFormatting) throws IOException {
        double validRatio = 1.0;

        if (!basic.getVariousBoolParam("IgnoreParameter")) {
            validRatio = parseMSDOSParamOnDisk(basic.getDisk(), isFormatting);
        } else {
            if (basic.getFatEndGroup() == 0) {
                int maxGroupOnParam = (basic.getSidesPerDiskOnBasic() * basic.getTracksPerSide() * basic.getSectorsPerTrackOnBasic() - basic.getDirEndSector()) / basic.getSectorsPerGroup() + 1;
                basic.setFatEndGroup(maxGroupOnParam);
            }
        }

        byte[] ipl = basic.getVariousStringParam("IPLCompareString").getBytes();
        if (ipl.length > 0) {
            DiskImageSector sector = basic.getSector(0, 0, 1);
            if (sector == null) return -1.0;
            if (sector.find(ipl, ipl.length) < 0) {
                validRatio = 0.0;
            }
        }

        return validRatio;
    }

    public double parseMSDOSParamOnDisk(DiskImageDisk disk, boolean isFormatting) throws IOException {
        if (isFormatting) return 1.0;

        int nums = 0;
        int valids = 0;

        // MS-DOS ディスク上のパラメータを読む
        DiskImageSector sector = basic.getSector(0, 0, 1);
        if (sector == null) return -1.0;
        byte[] data = sector.getSectorBuffer();
        if (data == null) return -1.0;
        FatBpb bpb = new FatBpb();
        Serdes.Util.deserialize(new ByteArrayInputStream(data), bpb);

        nums++;
        if (bpb.sectorPerCluster != 0) {
            // cluster size
            valids++;
        }
        nums++;
        if (disk.getSectorSize() == bpb.bytesPerSec) {
            // sector size
            valids++;
        }
        nums++;
        if (disk.getSidesPerDisk() >= bpb.numberOfHeads) {
            // side count (disk)
            valids++;
        }
        nums++;
        if (basic.getSidesPerDiskOnBasic() == bpb.numberOfHeads) {
            // side count (BASIC)
            valids++;
        }
        nums++;
        if (basic.getSectorsPerTrackOnBasic() == bpb.sectorsPerTrack) {
            // sector per track (BASIC)
            valids++;
        }

        // FATエリアの先頭メディアIDが一致するか
        DiskBasicFatArea bufs = fat.getDiskBasicFatArea();
        nums++;
        if (bufs.matchData8(0, 0, basic.getMediaId())) {
            valids++;
        }

        // ディスク内のパラメータで更新する
        if (nums == valids) {
            basic.setSidesPerDiskOnBasic(bpb.numberOfHeads);
            basic.setSectorsPerGroup(bpb.sectorPerCluster);
            basic.setReservedSectors(bpb.reservedSectorCount);
            basic.setNumberOfFats(bpb.numberOfFats);
            basic.setSectorsPerFat(bpb.fatSize16);
            basic.setDirEntryCount(bpb.rootEntryCount);

            basic.setDirStartSector(-1);
            basic.setDirEndSector(-1);
            basic.calcDirStartEndSector(bpb.bytesPerSec);

            basic.setSectorsPerTrackOnBasic(bpb.sectorsPerTrack);

            basic.setMediaId((byte) bpb.media);

            // トラック数
            int tracks = bpb.totalSectors16;
            if (tracks > 0) {
                tracks = tracks / basic.getSidesPerDiskOnBasic() / basic.getSectorsPerTrackOnBasic();
                basic.setTracksPerSideOnBasic(tracks);
            }
        }

        // さらにJmpBootとOEMNameをチェック
        byte[] jump = basic.getVariousStringParam("JumpBoot").getBytes();
        int len = jump.length < bpb.jumpBoot.length ? jump.length : bpb.jumpBoot.length;
        nums++;
        if (len > 0 && Arrays.equals(Arrays.copyOfRange(bpb.jumpBoot, 0, len), Arrays.copyOf(jump, len))) {
            valids++;
        }

        byte[] oem = basic.getVariousStringParam("OEMName").getBytes();
        len = oem.length < bpb.oemName.length ? oem.length : bpb.oemName.length;
        nums += 5;
        if (len > 0 && Arrays.equals(Arrays.copyOfRange(bpb.oemName, 0, len), Arrays.copyOf(oem, len))) {
            valids += 5;
        }

        // 最終グループ番号を計算
        int maxGroupOnFat = basic.getSectorsPerFat() * basic.getSectorSize() * 2 / 3;
        int maxGroupOnParam = (basic.getSidesPerDiskOnBasic() * basic.getTracksPerSide() * basic.getSectorsPerTrackOnBasic() - basic.getDirEndSector()) / basic.getSectorsPerGroup() + 1;

        basic.setFatEndGroup(Math.min(maxGroupOnFat, maxGroupOnParam));

        // テンプレートに一致するものがあるか
        DiskBasicParam param = gDiskBasicTemplates.findType(basic.getBasicCategoryName(), basic.getBasicTypeName(), basic.getSidesPerDiskOnBasic(), basic.getSectorsPerTrackOnBasic());
        if (param != null) {
            basic.setBasicDescription(param.getBasicDescription());
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
        DiskBasicGroupItem gItem = groupItems.get(0);

        DiskImageSector sector = basic.getDisk().getSector(gItem.track, gItem.side, gItem.sectorStart);

        byte[] buf = sector.getSectorBuffer();
        int bufOffset = 0;
        DiskBasicDirItem<DirectoryMsDos> newItem = basic.createDirItem(sector, 0, buf, bufOffset);

        // current entry
        newItem.copyData(item.getRawData());
        newItem.setFileNamePlain(".");
        newItem.setFileAttr(FORMAT_TYPE_UNKNOWN, FILE_TYPE_DIRECTORY_MASK.getValue(), 0);

        // parent entry
        bufOffset += newItem.getDataSize();
        newItem.setData(0, null, sector, 0, buf, bufOffset, null);
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

    /**
     * セクタデータを埋めた後の個別処理
     * フォーマット IPLの書き込み
     */
    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) throws IOException {
        if (!createBiosParameterBlock("\u00eb\u003c\u0090", "FAT12", null)) {
            return false;
        }

        // volume label
        DiskBasicFormat format = basic.getFormatType();
        if (format.hasVolumeName()) {
            int dirStart = basic.getReservedSectors() + basic.getNumberOfFats() * basic.getSectorsPerFat();
            DiskImageSector sector = basic.getSectorFromSectorPos(dirStart);
            DiskBasicDirItem<DirectoryMsDos> dItem = dir.newItem(sector, 0, sector.getSectorBuffer(), 0);

            dItem.setFileNamePlain(data.getVolumeName());
            dItem.setFileAttr(FORMAT_TYPE_UNKNOWN, FILE_TYPE_VOLUME_MASK.getValue(), 0);
            LocalDateTime tm = LocalDateTime.now();
            dItem.setFileModifyDateTime(tm);
        }

        return true;
    }

    /// BIOS Parameter Block を作成
    public boolean createBiosParameterBlock(String jump, String name, byte[][] sectorBuffer) throws IOException {
        DiskImageSector sec = basic.getSector(0, 0, 1);
        if (sec == null) return false;
        byte[] buf = sec.getSectorBuffer();
        if (buf == null) return false;

        if (sectorBuffer != null) sectorBuffer[0] = buf;

        sec.fill((byte) 0);

        FatBpb hed = new FatBpb();
        Serdes.Util.deserialize(new ByteArrayInputStream(buf), hed);

        byte[] jumpBytes = basic.getVariousStringParam("JumpBoot").getBytes();
        if (jumpBytes.length > 0) {
            jump = new String(jumpBytes);
        }
        int len = Math.min(jump.length(), hed.jumpBoot.length);
        System.arraycopy(jump.getBytes(), 0, hed.jumpBoot, 0, len);

        hed.bytesPerSec = basic.getSectorSize();
        hed.sectorPerCluster = basic.getSectorsPerGroup();
        hed.reservedSectorCount = basic.getReservedSectors();
        hed.numberOfFats = basic.getNumberOfFats();
        hed.rootEntryCount = basic.getDirEntryCount();

        byte[] nameBytes = basic.getVariousStringParam("OEMName").getBytes();
        if (nameBytes.length > 0) name = new String(nameBytes);
        // 上記パラメータ領域をまたがって設定可能にする
        len = Math.min(name.length(), 16);
        Arrays.fill(hed.oemName, (byte) 0x20);
        System.arraycopy(name.getBytes(), 0, hed.oemName, 0, len);

        len = basic.getTracksPerSide() * basic.getSectorsPerTrackOnBasic() * basic.getSidesPerDiskOnBasic();
        hed.totalSectors16 = len;
        hed.media = basic.getMediaId();
        hed.fatSize16 = basic.getSectorsPerFat();
        hed.sectorsPerTrack = basic.getSectorsPerTrackOnBasic();
        hed.numberOfHeads = basic.getSidesPerDiskOnBasic();

        // set the media ID in the first FAT entry
        setGroupNumber(0, 0xffff_ff00 | basic.getMediaId());
        setGroupNumber(1, 0xffff_ffff);

        return true;
    }

    @Override
    public void getIdentifiedData(DiskBasicIdentifiedData data) {
        // volume label
        DiskBasicDirItem<DirectoryMsDos> dItem = dir.findFileByAttrOnRoot(FILE_TYPE_VOLUME_MASK.getValue(), FILE_TYPE_VOLUME_MASK.getValue() | FILE_TYPE_DIRECTORY_MASK.getValue(), null);
        if (dItem != null) {
            data.setVolumeName(dItem.getFileNamePlainStr());
            data.setVolumeNameMaxLength(dItem.getFileNameStrSize());
        }
    }

    @Override
    public void setIdentifiedData(DiskBasicIdentifiedData data) throws IOException {
        DiskBasicFormat format = basic.getFormatType();

        // volume label
        if (format.hasVolumeName()) {
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
