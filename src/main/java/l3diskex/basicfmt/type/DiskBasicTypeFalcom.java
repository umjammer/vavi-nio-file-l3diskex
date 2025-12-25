///
/// @author Copyright (c) Sasaji. All rights reserved.
///

package l3diskex.basicfmt.type;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasic.DiskBasicIdentifiedData;
import l3diskex.basicfmt.DiskBasicDir;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.DiskBasicFat;
import l3diskex.basicfmt.DiskBasicType;
import l3diskex.basicfmt.diritem.DiskBasicDirItemFalcom.DirectoryFalcom;

import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_FREE;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_SYSTEM;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_USED;
import static l3diskex.basicfmt.DiskBasicFat.DiskBasicAvailability.FatAvailability.FAT_AVAIL_USED_LAST;


/**
 * Falcom DOS processing
 */
public class DiskBasicTypeFalcom extends DiskBasicType<DirectoryFalcom> {

    public static final int FORMAT_TYPE_FALCOM = 91;

    @Override
    public boolean isSupported(int typeNumber) {
        return typeNumber == FORMAT_TYPE_FALCOM;
    }

    @Override
    public void init(DiskBasic basic, DiskBasicFat fat, DiskBasicDir<DirectoryFalcom> dir) {
        super.init(basic, fat, dir);
    }

    /** Check FAT area */
    @Override
    public double checkFat(boolean isFormatting) {
        return 1.0;
    }

    /** Get each parameter from disk and calculate necessary parameters */
    @Override
    public double parseParamOnDisk(boolean isFormatting) {
        // Number of groups
        if (basic.getFatEndGroup() == 0) {
            int end_group = basic.getTracksPerSideOnBasic() * basic.getSidesPerDiskOnBasic() * basic.getSectorsPerTrackOnBasic();
            basic.setFatEndGroup(end_group - 1);
        }
        return 1.0;
    }

    /** Calculate remaining disk size */
    @Override
    public void calcDiskFreeSize(boolean wrote) {
        fatAvailability.empty();
//        fatAvailability.setCount(basic.getFatEndGroup() + 1, FAT_AVAIL_FREE);

        List<DiskBasicDirItem<DirectoryFalcom>> items = dir.getCurrentItems(null);
        for (int index = 0; items != null && index < items.size(); index++) {
            DiskBasicDirItem<DirectoryFalcom> item = items.get(index);
            if (item == null || !item.isUsed()) continue;

            // Examine map of group numbers
            DiskBasicGroups groups = item.getGroups();
            int count = groups.size();
            for (int n = 0; n < count; n++) {
                DiskBasicGroupItem group = groups.get(n);
                int groupNum = group.group;
                if (groupNum <= basic.getFatEndGroup()) {
                    if (n + 1 == count) {
                        fatAvailability.set(groupNum, FAT_AVAIL_USED_LAST);
                    } else {
                        fatAvailability.set(groupNum, FAT_AVAIL_USED);
                    }
                }
            }
        }

        // Check free space
        int groups = 0;
        int dirArea = (basic.getDirEndSector() / basic.getSectorsPerGroup());
        for (int pos = 0; pos <= basic.getFatEndGroup(); pos++) {
            if (pos < dirArea) {
                // Directory area is in use
                fatAvailability.set(pos, FAT_AVAIL_SYSTEM);
            } else if (fatAvailability.get(pos) == FAT_AVAIL_FREE) {
//				fatAvailability.Item(pos).set(FAT_AVAIL_FREE);
                groups++;
            }
        }

        int fSize = groups * basic.getSectorSize() * basic.getSectorsPerGroup();

        fatAvailability.setFreeSize(fSize);
        fatAvailability.setFreeGroups(groups);
    }

    /** Whether formatting is supported */
    @Override
    public boolean supportFormatting() {
        return false;
    }

    /** Individual processing after filling sector data */
    @Override
    public boolean additionalProcessOnFormatted(DiskBasicIdentifiedData data) {
        return true;
    }

    /** Determine data size of the last sector of file */
    @Override
    public int calcDataSizeOnLastSector(DiskBasicDirItem<DirectoryFalcom> item, InputStream iStream, OutputStream oStream, byte[] sectorBuffer, int sectorOffset, int sectorSize, int remainSize) {
        return remainSize;
    }

    /** Whether writing is supported */
    @Override
    public boolean supportWriting() {
        return false;
    }

    /** Whether deleting is supported */
    @Override
    public boolean supportDeleting() {
        return false;
    }

    /** Delete FAT area for the specified group number */
    @Override
    public void deleteGroupNumber(int groupNum) throws IOException {
        // Mark as unused
        setGroupNumber(groupNum, 0);
    }
}
