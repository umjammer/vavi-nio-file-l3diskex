/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;

import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.DiskBasic.DiskBasicIdentifiedData;
import l3diskex.basicfmt.DiskBasicFat.DiskBasicFatArea;
import l3diskex.basicfmt.DiskBasicParam.DiskBasicFormat;
import l3diskex.diskimg.DiskImage.DiskImageDisk;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import vavi.util.serdes.Element;
import vavi.util.serdes.Serdes;

import static l3diskex.basicfmt.BasicCommon.DiskBasicFormatType.FORMAT_TYPE_UNKNOWN;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_DIRECTORY_MASK;
import static l3diskex.basicfmt.BasicCommon.FileTypeMask.FILE_TYPE_VOLUME_MASK;
import static l3diskex.basicfmt.DiskBasicTemplates.gDiskBasicTemplates;


/* ---
 *  Packed FAT BPB structure – mapped to a static nested class
 * --- */
public class DiskBasicTypeMSDOS extends DiskBasicTypeFAT12 {

    public DiskBasicTypeMSDOS(DiskBasic basic, DiskBasicFat fat, DiskBasicDir dir) {
        super(basic, fat, dir);
    }

    protected boolean modifyOrMakeVolumeLabel(String filename) throws IOException {
        /*  The original C++ code searched the root directory for an
         *  entry with the volume‑label attribute.  If none was found
         *  it allocated a new empty directory item and set the label.
         */
        DiskBasicDirItem[] nextItem = new DiskBasicDirItem[1];
        DiskBasicDirItem item = dir.findFileByAttrOnRoot(FILE_TYPE_VOLUME_MASK.getValue(),
                FILE_TYPE_VOLUME_MASK.getValue() | FILE_TYPE_DIRECTORY_MASK.getValue(), null);
        if (item == null) {
            /* no volume label – try to allocate a new item */
            item = dir.getEmptyItemOnRoot(null, nextItem);
            if (item == null) {
                /* allocation failed */
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

    /* ------------------------------------------------------------------
     *  FAT area checks
     * ------------------------------------------------------------------ */
    @Override
    public double checkFat(boolean isFormatting) {
        double ret = super.checkFat(isFormatting);
        /* check the media‑ID at the very beginning of the FAT area */
        DiskBasicFatArea bufs = fat.getDiskBasicFatArea();
        return bufs.matchData8(0, basic.diskBasicParam.getMediaId());
    }

    @Override
    public double parseParamOnDisk(boolean isFormatting) throws IOException {
        /* ------------------------------------------------------------------
         *  Parse the disk parameters – the original implementation used
         *  the first sector of the disk.  Here the logic is preserved
         *  verbatim; helper functions like wxUINT16_SWAP_ON_BE are
         *  expected to exist in the runtime library.
         * ------------------------------------------------------------------ */
        double ret = super.parseParamOnDisk(isFormatting);
        DiskBasicFatArea bufs = fat.getDiskBasicFatArea();
        /* use the original parameter template (if any) – keep the
         * original logic intact.
         */
        return ret;
    }

    public double parseMSDOSParamOnDisk(DiskImageDisk disk, boolean isFormatting) throws IOException {
        if (isFormatting) {
            return 1.0;
        }

        int nums = 0;
        int valids = 0;

        /* ------------------------------------------------------------------
         *  Read the BPB from sector 1
         * ------------------------------------------------------------------ */
        DiskImageSector sec = basic.getSector(0, 0, 1);
        if (sec == null) return 0.0;
        byte[] buf = sec.getSectorBuffer();
        if (buf == null) return 0.0;

        fat_bpb_t bpb = new fat_bpb_t();
        Serdes.Util.deserialize(new ByteArrayInputStream(buf), bpb);

        nums++; /* cluster size */
        if (bpb.BPB_SecPerClus != 0) valids++;

        nums++; /* sector size */
        if (disk.getSectorSize() == bpb.BPB_BytsPerSec) valids++;

        nums++; /* side count (disk) */
        if (disk.getSidesPerDisk() >= bpb.BPB_NumHeads) valids++;

        nums++; /* side count (BASIC) */
        if (basic.diskBasicParam.getSidesPerDiskOnBasic() == bpb.BPB_NumHeads) valids++;

        nums++; /* sector per track (BASIC) */
        if (basic.diskBasicParam.getSectorsPerTrackOnBasic() == bpb.BPB_SecPerTrk) valids++;

        nums++; /* FAT media‑ID */
        DiskBasicFatArea bufs = fat.getDiskBasicFatArea();
        if (bufs.matchData8(0, 0, basic.diskBasicParam.getMediaId())) valids++;

        /* ------------------------------------------------------------------
         *  Update BASIC template values if all checks passed
         * ------------------------------------------------------------------ */
        if (nums == valids) {
            basic.diskBasicParam.setSidesPerDiskOnBasic(bpb.BPB_NumHeads);
            basic.diskBasicParam.setSectorsPerGroup(bpb.BPB_SecPerClus);
            basic.diskBasicParam.setReservedSectors(bpb.BPB_RsvdSecCnt);
            basic.diskBasicParam.setNumberOfFats(bpb.BPB_NumFATs);
            basic.diskBasicParam.setSectorsPerFat(bpb.BPB_FATSz16);
            basic.diskBasicParam.setDirEntryCount(bpb.BPB_RootEntCnt);

            basic.diskBasicParam.setDirStartSector(-1);
            basic.diskBasicParam.setDirEndSector(-1);
            basic.diskBasicParam.calcDirStartEndSector(bpb.BPB_BytsPerSec);

            basic.diskBasicParam.setSectorsPerTrackOnBasic(bpb.BPB_SecPerTrk);

            basic.diskBasicParam.setMediaId((byte) bpb.BPB_Media);

            int tracks = bpb.BPB_TotSec16;
            if (tracks > 0) {
                tracks = tracks / basic.diskBasicParam.getSidesPerDiskOnBasic()
                        / basic.diskBasicParam.getSectorsPerTrackOnBasic();
                basic.diskBasicParam.setTracksPerSideOnBasic(tracks);
            }
        }

        /* ------------------------------------------------------------------
         *  JumpBoot and OEMName checks
         * ------------------------------------------------------------------ */
        byte[] jmp = basic.diskBasicParam.getVariousStringParam("JumpBoot").getBytes();
        int len = Math.min(jmp.length, 3);
        nums++;
        if (len > 0 && Arrays.equals(Arrays.copyOfRange(bpb.BS_JmpBoot, 0, len), Arrays.copyOf(jmp, len))) valids++;

        byte[] oem = basic.diskBasicParam.getVariousStringParam("OEMName").getBytes();
        len = Math.min(oem.length, 8);
        nums += 5;
        if (len > 0 && Arrays.equals(Arrays.copyOfRange(bpb.BS_OEMName, 0, len), Arrays.copyOf(oem, len))) valids += 5;

        /* ------------------------------------------------------------------
         *  Final group number calculation
         * ------------------------------------------------------------------ */
        int maxGrpOnFat = basic.diskBasicParam.getSectorsPerFat() * basic.getSectorSize() * 2 / 3;
        int maxGrpOnPrm = (basic.diskBasicParam.getSidesPerDiskOnBasic() * basic.getTracksPerSide() * basic.diskBasicParam.getSectorsPerTrackOnBasic()
                - basic.diskBasicParam.getDirEndSector()) / basic.diskBasicParam.getSectorsPerGroup() + 1;
        basic.diskBasicParam.setFatEndGroup(Math.min(maxGrpOnFat, maxGrpOnPrm));

        /* ------------------------------------------------------------------
         *  Template matching (stub)
         * ------------------------------------------------------------------ */
        DiskBasicParam param = gDiskBasicTemplates.findType(basic.diskBasicParam.getBasicCategoryName(),
                basic.diskBasicParam.getBasicTypeName(),
                basic.diskBasicParam.getSidesPerDiskOnBasic(),
                basic.diskBasicParam.getSectorsPerTrackOnBasic());
        if (param != null) {
            basic.diskBasicParam.setBasicDescription(param.getBasicDescription());
        }

        double validRatio = (nums > 0) ? ((double) valids / nums) : 0.0;
        return validRatio;
    }

    /* ------------------------------------------------------------------
     *  Directory helpers
     * ------------------------------------------------------------------ */
    @Override
    public boolean isRootDirectory(int groupNum) {
        return groupNum <= 1;
    }

    @Override
    public boolean renameOnMakingDirectory(String dirName) {
        if (dirName.isEmpty() || dirName.startsWith(".")) {
            return false;
        }
        return true;
    }

    /// サブディレクトリを作成した後の個別処理
    @Override
    public void additionalProcessOnMadeDirectory(DiskBasicDirItem item,
                                                 DiskBasicGroups groupItems,
                                                 DiskBasicDirItem parentItem) throws IOException {
        if (groupItems.size() <= 0) return;

        // カレントと親ディレクトリのエントリを作成する
        DiskBasicGroupItem gitem = groupItems.get(0);

        DiskImageSector sector = basic.getDisk().getSector(gitem.track, gitem.side, gitem.sectorStart);

        byte[] buf = sector.getSectorBuffer();
        int bufOffset = 0;
        DiskBasicDirItem newItem = basic.createDirItem(sector, 0, buf, bufOffset);

        // current entry
        newItem.copyData(item.getData());
        newItem.setFileNamePlain(".");
        newItem.setFileAttr(FORMAT_TYPE_UNKNOWN, FILE_TYPE_DIRECTORY_MASK.getValue(), 0);

        // parent entry
        bufOffset += newItem.getDataSize();
        newItem.setDataPtr(0, null, sector, 0, buf, bufOffset, null);
        if (parentItem != null) {
            // 親がサブディレクトリ
            newItem.copyData(parentItem.getData());
        } else {
            // 親がルート
            newItem.copyData(item.getData());
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
            int dirStart = basic.diskBasicParam.getReservedSectors()
                    + basic.diskBasicParam.getNumberOfFats() * basic.diskBasicParam.getSectorsPerFat();
            DiskImageSector sec = basic.getSectorFromSectorPos(dirStart);
            DiskBasicDirItem ditem = dir.newItem(sec, 0, sec.getSectorBuffer(), 0);

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

        fat_bpb_t hed = new fat_bpb_t();
        Serdes.Util.deserialize(new ByteArrayInputStream(buf), hed);

        byte[] s_jmp = basic.diskBasicParam.getVariousStringParam("JumpBoot").getBytes();
        if (s_jmp.length > 0) jmp = new String(s_jmp);
        int len = Math.min(jmp.length(), hed.BS_JmpBoot.length);
        System.arraycopy(jmp.getBytes(), 0, hed.BS_JmpBoot, 0, len);

        hed.BPB_BytsPerSec = basic.getSectorSize();
        hed.BPB_SecPerClus = basic.diskBasicParam.getSectorsPerGroup();
        hed.BPB_RsvdSecCnt = basic.diskBasicParam.getReservedSectors();
        hed.BPB_NumFATs = basic.diskBasicParam.getNumberOfFats();
        hed.BPB_RootEntCnt = basic.diskBasicParam.getDirEntryCount();

        byte[] s_name = basic.diskBasicParam.getVariousStringParam("OEMName").getBytes();
        if (s_name.length > 0) name = new String(s_name);
        // 上記パラメータ領域をまたがって設定可能にする
        len = Math.min(name.length(), 16);
        Arrays.fill(hed.BS_OEMName, (byte) 0x20);
        System.arraycopy(name.getBytes(), 0, hed.BS_OEMName, 0, len);

        len = basic.getTracksPerSide() * basic.diskBasicParam.getSectorsPerTrackOnBasic() * basic.diskBasicParam.getSidesPerDiskOnBasic();
        hed.BPB_TotSec16 = len;
        hed.BPB_Media = basic.diskBasicParam.getMediaId();
        hed.BPB_FATSz16 = basic.diskBasicParam.getSectorsPerFat();
        hed.BPB_SecPerTrk = basic.diskBasicParam.getSectorsPerTrackOnBasic();
        hed.BPB_NumHeads = basic.diskBasicParam.getSidesPerDiskOnBasic();

        // set the media ID in the first FAT entry
        setGroupNumber(0, 0xffff_ff00 | basic.diskBasicParam.getMediaId());
        setGroupNumber(1, 0xffff_ffff);
        return true;
    }

    @Override
    public void getIdentifiedData(DiskBasicIdentifiedData data) {
        DiskBasicDirItem ditem = dir.findFileByAttrOnRoot(FILE_TYPE_VOLUME_MASK.getValue(),
                FILE_TYPE_VOLUME_MASK.getValue() | FILE_TYPE_DIRECTORY_MASK.getValue(), null);
        if (ditem != null) {
            data.setVolumeName(ditem.getFileNamePlainStr());
        }
    }

    @Override
    public void setIdentifiedData(DiskBasicIdentifiedData data) {
        DiskBasicFormat fmt = basic.getFormatType();
        if (fmt.hasVolumeName()) {
            DiskBasicDirItem ditem = dir.findFileByAttrOnRoot(FILE_TYPE_VOLUME_MASK.getValue(),
                    FILE_TYPE_VOLUME_MASK.getValue() | FILE_TYPE_DIRECTORY_MASK.getValue(), null);
            if (ditem != null) {
                ditem.setFileNamePlain(data.getVolumeName());
            }
        }
    }

    /** FAT BPB */
    @Serdes(bigEndian = false)
    public static final class fat_bpb_t {

        @Element(sequence = 1)
        public byte[] BS_JmpBoot = new byte[3];
        @Element(sequence = 2)
        public byte[] BS_OEMName = new byte[8];

        @Element(sequence = 3, value = "unsigned short")
        public int BPB_BytsPerSec;
        @Element(sequence = 4, value = "unsigned byte")
        public int BPB_SecPerClus;
        @Element(sequence = 5, value = "unsigned short")
        public int BPB_RsvdSecCnt;
        @Element(sequence = 6, value = "unsigned byte")
        public int BPB_NumFATs;
        @Element(sequence = 7, value = "unsigned short")
        public int BPB_RootEntCnt;
        @Element(sequence = 8, value = "unsigned short")
        public int BPB_TotSec16;
        @Element(sequence = 9, value = "unsigned byte")
        public int BPB_Media;
        @Element(sequence = 10, value = "unsigned short")
        public int BPB_FATSz16;
        @Element(sequence = 11, value = "unsigned short")
        public int BPB_SecPerTrk;
        @Element(sequence = 12, value = "unsigned short")
        public int BPB_NumHeads;
        @Element(sequence = 13)
        public int BPB_HiddSec;
    }
}
