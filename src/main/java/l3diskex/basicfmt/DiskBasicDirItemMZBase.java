/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex.basicfmt;

import java.io.IOException;

import l3diskex.basicfmt.BasicCommon.DirectoryT;
import l3diskex.basicfmt.BasicCommon.DiskBasicFileType;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroupItem;
import l3diskex.basicfmt.BasicCommon.DiskBasicGroups;
import l3diskex.basicfmt.BasicFmt.DiskBasic;
import l3diskex.diskimg.DiskImage.DiskImageSector;
import l3diskex.diskimg.DiskParam.SectorParam;


//
//  DiskBasicDirItemMZBase
//
public abstract class DiskBasicDirItemMZBase<T extends DirectoryT> extends DiskBasicDirItem<T> {

    /** Constructor – only a reference to the disk. */
    public DiskBasicDirItemMZBase(DiskBasic basic) {
        super(basic);
    }

    /** Constructor – initialize from sector data. */
    public DiskBasicDirItemMZBase(DiskBasic basic,
                                 DiskImageSector n_sector,
                                 int n_secpos,
                                 byte[] n_data) {
        super(basic, n_sector, n_secpos, n_data);
    }

    /** Full constructor – all parameters. */
    public DiskBasicDirItemMZBase(DiskBasic basic,
                                 int n_num,
                                 DiskBasicGroupItem n_gitem,
                                 DiskImageSector n_sector,
                                 int n_secpos,
                                 byte[] n_data,
                                 SectorParam n_next,
                                 boolean[] n_unuse) {
        super(basic, n_num, n_gitem, n_sector, n_secpos, n_data, n_next, n_unuse);
    }

    /*--------------------------------------------------------------
     *  Virtual overrides (mostly empty implementations)
     *--------------------------------------------------------------*/
    protected void setFileSizeBase(int val) {
        /* The actual storage of the file size in the disk image
         * is handled by the derived class.  This method is left
         * empty to keep the original behaviour. */
    }

    protected int getFileSizeBase() {
        /* The original C++ version returns 0.  Derived classes
         * should provide the correct implementation. */
        return 0;
    }

    protected void preCalcFileSize() {
        /* Empty – no special preprocessing required before
         * the file size calculation. */
    }

    protected void calcAllGroups(int calcFlags,
                                 int[] groupNum,
                                 int[] remain,
                                 int[] secSize,
                                 int[] endSec,
                                 Object userData) {
        /* Increment the group number as in the original C++. */
        groupNum[0]++;          // group_num++ in C++
    }

    protected void postCalcAllGroups(Object userData) {
        /* No post‑processing required. */
    }

    /*--------------------------------------------------------------
     *  Delete handling
     *--------------------------------------------------------------*/
    @Override
    public boolean delete() throws IOException {
        /* Set the delete flag for the file type and mark the
         * item as unused.  The disk image sector containing
         * the file’s metadata will have its first file type
         * byte replaced by the disk’s delete code. */
        setFileType1(basic.diskBasicParam.getDeleteCode());   // Set the type byte
        used(false);                           // Mark as unused
        if (type != null) {
            type.setGroupNumber(getStartGroup(0), 0);  // Free the block
        }
        return true;
    }

    /*--------------------------------------------------------------
     *  Directory initialisation
     *--------------------------------------------------------------*/
    @Override
    public void initialData() {
        /* Reset the item so that it is marked as unused.  The
         * actual implementation resides in the base class. */
        clearData();          // Equivalent to ClearData() in C++
    }

    /*--------------------------------------------------------------
     *  File size handling
     *--------------------------------------------------------------*/
    @Override
    public void setFileSize(int val) {
        groups.setSize(val);      // Size of the group vector
        setFileSizeBase(val);       // Store the size in the disk image
    }

    @Override
    public void calcFileUnitSize(int fileunitNum) {
        if (!isUsed()) {
            return;
        }
        getUnitGroups(fileunitNum, groups);
    }

    /*--------------------------------------------------------------
     *  Group calculation helpers
     *--------------------------------------------------------------*/
    protected void preCalcAllGroups(int[] calcFlags,
                                    int[] groupNum,
                                    int[] remain,
                                    int[] secSize,
                                    Object[] userData) {
        /* No special preprocessing required. */
    }

    /*--------------------------------------------------------------
     *  Retrieve all groups of the specified directory
     *--------------------------------------------------------------*/
    @Override
    public void getUnitGroups(int fileunitNum, DiskBasicGroups groupItems) {
        int calcFlags  = 0;
        int calcGroups = 0;
        int calcGroupsLocal = 0;           // Counter used inside the loop
        int[] userData = new int[1];    // Emulates void **user_data

        // The original C++ code uses a reference to an unsigned 32‑bit
        // integer for group_num.  In Java we use a single‑element int[]
        // array to emulate a reference.
        int[] groupNum = new int[]{getStartGroup(fileunitNum)};

        // The file size in the data stream (retrieved by GetFileSizeBase)
        int fileSizeInData = getFileSizeBase();
        int remain = fileSizeInData;
        int secSize = basic.getSectorSize();

        // Pre‑calculation hook – does nothing in this class
        preCalcAllGroups(new int[]{calcFlags},
                         groupNum,
                         new int[]{remain},
                         new int[]{secSize},
                         new Object[] {userData[0]});

        // Main loop – collect all the groups used by this file
        int limit = basic.diskBasicParam.getFatEndGroup() + 1;
        while (remain > 0 && limit >= 0) {
            boolean usedGroup = (type != null && type.isUsedGroupNumber(groupNum[0]));
            if (usedGroup) {
                int endSec = -1;
                basic.getNumsFromGroup(groupNum[0], 0, secSize, remain, groupItems, userData);
                calcAllGroups(calcFlags, groupNum, new int[]{remain}, new int[]{secSize}, new int[]{endSec}, userData);
                calcGroupsLocal++;
                remain -= (secSize * basic.getSectorsPerGroup());
            } else {
                limit = 0;
            }
            limit--;
        }

        // Store the totals into the caller’s DiskBasicGroups object
        groupItems.setNums(calcGroupsLocal);
        groupItems.setSize(fileSizeInData);
        groupItems.setSizePerGroup(basic.getSectorSize() * basic.getSectorsPerGroup());

        // Post‑calculation hook – does nothing in this class
        postCalcAllGroups(userData[0]);
    }

    /*--------------------------------------------------------------
     *  Deletion and editing checks
     *--------------------------------------------------------------*/
    @Override
    public boolean isDeletable() {
        boolean valid = true;
        DiskBasicFileType attr = getFileAttr();

        if (attr.isVolume()) {
            /* Volume numbers cannot be deleted. */
            valid = false;
        } else if (attr.isDirectory()) {
            String name = getFileNamePlainStr();
            if (".".equals(name) || "..".equals(name)) {
                /* Directories "." and ".." cannot be deleted. */
                valid = false;
            }
        }
        return valid;
    }

    @Override
    public boolean isFileNameEditable() {
        boolean valid = true;
        DiskBasicFileType attr = getFileAttr();

        if (attr.isVolume()) {
            /* Volume numbers cannot be edited. */
            valid = false;
        } else if (attr.isDirectory()) {
            String name = getFileNamePlainStr();
            if (".".equals(name) || "..".equals(name)) {
                /* Directories "." and ".." cannot be edited. */
                valid = false;
            }
        }
        return valid;
    }
}
