//
// @author Copyright (c) Sasaji. All rights reserved.
//

package l3diskex.ui;

import java.awt.Dimension;
import java.awt.Point;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JWindow;

import l3diskex.Utils;
import l3diskex.basicfmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.DiskBasicDirItem.DiskBasicDirItemAttr;
import l3diskex.basicfmt.DiskBasicDirItem.DiskBasicDirItems;
import l3diskex.basicfmt.DiskBasicError;
import l3diskex.ui.IntNameValidatorFile.IntNameValidator;
import l3diskex.ui.UIBinDump.UiDiskBinDumpFrame;


/** Disk & File Operations */
class UiDiskProcess extends JFrame {

    protected int m_unique_number;

    // Constructor
    public UiDiskProcess(JWindow parent, String id, String title, Point pos, Dimension size) {
        super(parent, id, title, pos, size);
        m_unique_number = 0;
    }

    /**
     * Import specified files
     *
     * @param paths     List of file paths
     * @param dir_basic [in,out] OS of the destination
     * @param dir_item  [in,out] Destination directory item
     * @param confirm   Whether to show confirmation dialog when including directory
     * @param start_msg Start message
     * @param end_msg   End message
     * @return true:OK false:Error
     */
    public boolean ImportDataFiles(List<String> paths, DiskBasic dir_basic, DiskBasicDirItem dir_item, boolean confirm, String start_msg, String end_msg) {
        if (dir_basic == null) {
            return false;
        }

        if (confirm) {
            // Show confirmation dialog
            String name = dir_item.getParent() != null ? dir_item.getFileNameStr() : "root directory"; // _("root directory")
            if (name.isEmpty()) name = "this directory"; // _("this directory")
            String msg = String.format("Are you sure to import to %s?", name); // _("Are you sure to import to %s?")
            if (JOptionPane.showConfirmDialog(msg, start_msg, wxDialogReturn.wxYES | wxDialogReturn.wxNO) != wxDialogReturn.wxYES) {
                return true;
            }
        }

        StartImportCounter(0, start_msg);

        int sts = 0;
        for (int n = 0; n < paths.size() && sts >= 0; n++) {
            File file_path = new File(paths.get(n));
            String data_dir = file_path.getParent();
            List<String> names = new ArrayList<>();
            names.add(file_path.getName());

            // Simulate file_path.RemoveLastDir() and file_path.AppendDir("Attrs")
            // This is a rough simulation, as path manipulation can be complex.
            String parent_dir = file_path.getParentFile() != null ? file_path.getParentFile().getParent() : null;
            String attr_dir = (parent_dir != null ? parent_dir + File.separator + "Attrs" : "Attrs");

            sts |= ImportDataFiles(data_dir, attr_dir, names, dir_basic, dir_item, 0);
        }

        FinishImportCounter(end_msg);

        // Directory display needs update
        dir_item.ValidDirectory(false);
        // Update list in right panel
        UiDiskFileList file_list = GetFileListPanel();
        if (file_list != null) ((UiDiskFileList) file_list).RefreshFiles(); // Placeholder method
        // Update tree in left panel
        UiDiskList disk_list = GetDiskListPanel();
        if (disk_list != null)
            ((UiDiskList) disk_list).RefreshAllDirectoryNodes(dir_basic.GetDisk(), dir_basic.GetSelectedSide(), dir_item); // Placeholder method
        if (sts != 0) {
            dir_basic.ShowErrorMessage();
        }
        return (sts >= 0);
    }

    /// Import specified files
    ///
    /// @param data_dir  Data folder
    /// @param attr_dir  Attribute folder
    /// @param names     List of file names
    /// @param dir_basic [in,out] OS of the destination
    /// @param dir_item  [in,out] Destination directory item
    /// @param depth     Depth
    /// @return 1 warning
    /// @return 0 normal
    /// @return -1 error
    /// @attention This function is called recursively.
    protected int ImportDataFiles(String data_dir, String attr_dir, List<String> names, DiskBasic dir_basic, DiskBasicDirItem dir_item, int depth) {
        if (depth > gConfig.GetDirDepth()) {
            dir_basic.GetErrinfo().SetError(DiskBasicError.ERR_PATH_TOO_DEEP);
            return -1;
        }

        int sts = 0;
        int count = names.size();
        AppendImportCounter(count);

        for (int n = 0; n < count && sts >= 0; n++) {
            BeginBusyCursorImportCounterIfNeed();
            IncreaseImportCounter();

            String name = names.get(n);
            Path full_data_path = Paths.get(data_dir, name);
            Path full_attr_path = Paths.get(attr_dir, name + ".xml"); // "xml"
            // Convert file name
            name = Utils.decodeFileName(name);

            if (Files.isDirectory(full_data_path)) {
                // If folder

                // Create new directory
                DiskBasicDirItem[] new_dir_item = {null};
                sts = MakeDirectory(dir_basic, dir_item, name, "Import Directory", new_dir_item); // _("Import Directory")
                if (sts < 0) {
                    break;
                }
                DiskBasicDirItem new_item = new_dir_item[0];

                // List of files in folder
                List<String> sub_names = new ArrayList<>();
                File dir = full_data_path.toFile();
                File[] sub_files = dir.listFiles();
                if (sub_files != null) {
                    for (File sub_file : sub_files) {
                        sub_names.add(sub_file.getName());
                    }
                }
                if (sub_names.isEmpty()) {
                    continue;
                }

                // Temporarily move to new directory and perform initialization etc.
                // TODO
                DiskBasicDirItem cur_item = dir_basic.GetCurrentDirectory();
                if (!dir_basic.ChangeDirectory(new_item)) {
                    sts = -1;
                    break;
                }
                dir_basic.ChangeDirectory(cur_item);

                // Import recursively
                sts |= ImportDataFiles(full_data_path.toString(), full_attr_path.toString(), sub_names, dir_basic, new_item, depth + 1);

            } else {
                // If file
                sts |= ImportDataFile(full_data_path.toString(), full_attr_path.toString(), name, dir_basic, dir_item);

            }
        }

        return sts;
    }

    /// Import specified files
    ///
    /// @param full_data_path Data file path
    /// @param full_attr_path Attribute file path
    /// @param file_name      File name
    /// @param dir_basic      [in,out] OS of the destination
    /// @param dir_item       [in,out] Destination directory item
    /// @return 1 warning exists, continue processing
    /// @return 0 normal
    /// @return -1 error exists, cannot continue
    protected int ImportDataFile(String full_data_path, String full_attr_path, String file_name, DiskBasic dir_basic, DiskBasicDirItem dir_item) {
        if (dir_basic == null) {
            return -1;
        }

        if (!dir_basic.IsFormatted()) {
            return -1;
        }

        // Check remaining disk size and get input file size
        int[] file_size = {0};
        if (!dir_basic.CheckFile(full_data_path, file_size)) {
            return -1;
        }

        // Style for importing from external
        int style = IntNameBoxFlags.INTNAME_NEW_FILE | IntNameBoxFlags.INTNAME_SHOW_TEXT | IntNameBoxFlags.INTNAME_SHOW_ATTR | IntNameBoxFlags.INTNAME_SPECIFY_FILE_NAME | IntNameBoxFlags.INTNAME_SHOW_SKIP_DIALOG;

        int sts = 0;
        DiskBasicDirItem temp_item = dir_basic.CreateDirItem();

        // Read file information if it exists
        String filename = file_name;
        DiskBasicDirItemAttr[] date_time_array = {new DiskBasicDirItemAttr()};
        DiskBasicDirItemAttr date_time = date_time_array[0];
        if (temp_item.ReadFileAttrFromXml(full_attr_path, date_time_array)) {
            // File name
            filename = temp_item.GetFileNameStr();
            // Change to internal import
            style = IntNameBoxFlags.INTNAME_IMPORT_INTERNAL | IntNameBoxFlags.INTNAME_SHOW_TEXT | IntNameBoxFlags.INTNAME_SHOW_ATTR
                    | IntNameBoxFlags.INTNAME_SPECIFY_CDATE_TIME | IntNameBoxFlags.INTNAME_SPECIFY_MDATE_TIME | IntNameBoxFlags.INTNAME_SPECIFY_ADATE_TIME
                    | IntNameBoxFlags.INTNAME_SHOW_SKIP_DIALOG;
            // Whether copyable
            if (!temp_item.IsCopyable()) {
                // Don't treat as error
                sts = 1;
            }
        } else {
            // Get date from file
            temp_item.ReadFileDateTime(full_data_path, date_time);
            style |= IntNameBoxFlags.INTNAME_SPECIFY_CDATE_TIME | IntNameBoxFlags.INTNAME_SPECIFY_MDATE_TIME | IntNameBoxFlags.INTNAME_SPECIFY_ADATE_TIME;
        }
        if (sts == 0) {
            // Show dialog
            int ans = ShowIntNameBoxAndCheckSameFile(dir_basic, dir_item, temp_item, filename, file_size[0], date_time, style);
            if (ans == wxDialogReturn.wxYES) {
                // Save to disk
                DiskBasicDirItem[] madeitem = {null};
                boolean valid = dir_basic.SaveFile(full_data_path, dir_item, temp_item, madeitem);
                if (!valid) {
                    sts = -1;
                }
            } else {
                sts = -1;
            }
        }
        // delete temp_item (Java garbage collection handles this)
        return sts;
    }

    /// Import specified file by overwriting
    ///
    /// @param item      Directory item of file to save (has attributes etc.)
    /// @param path      Path of data file to save
    /// @param dir_basic [in,out] OS of the destination
    /// @param dir_item  [in,out] Destination directory item
    /// @param start_msg Start message
    /// @param end_msg   End message
    /// @return true:OK false:Error
    public boolean ImportDataFile(DiskBasicDirItem item, String path, DiskBasic dir_basic, DiskBasicDirItem dir_item, String start_msg, String end_msg) {
        if (dir_basic == null) {
            return false;
        }

        // Create temporary directory item
        DiskBasicDirItem pitem = dir_basic.CreateDirItem();
        pitem.CopyItem(item);

        StartImportCounter(1, start_msg);

        boolean valid = dir_basic.SaveFile(path, dir_item, pitem);

        IncreaseImportCounter();
        FinishImportCounter(end_msg);

        // Directory display needs update
        dir_item.ValidDirectory(false);
        // Update list in right panel
        UiDiskFileList file_list = GetFileListPanel();
        if (file_list != null) ((UiDiskFileList) file_list).RefreshFiles(); // Placeholder method
        // Update tree in left panel
        UiDiskList disk_list = GetDiskListPanel();
        if (disk_list != null)
            ((UiDiskList) disk_list).RefreshAllDirectoryNodes(dir_basic.GetDisk(), dir_basic.GetSelectedSide(), dir_item); // Placeholder method

        // delete pitem (Java garbage collection handles this)
        return valid;
    }

    /// Export to specified file
    ///
    /// @return true:OK false:Error
    /// @param dir_basic    Source OS
    /// @param item         Directory item to extract
    /// @param path         File path
    /// @param start_msg    Start message
    /// @param end_msg      End message
    public boolean ExportDataFile(DiskBasic dir_basic, DiskBasicDirItem item, String path, String start_msg, String end_msg) {
        if (dir_basic == null) return false;

        StartExportCounter(1, start_msg);

        // Load
        boolean valid = dir_basic.LoadFile(item, path);
        // Reflect date
        if (valid) {
            item.WriteFileDateTime(path);
        }

        IncreaseExportCounter();
        FinishExportCounter(end_msg);

        if (!valid) {
            dir_basic.ShowErrorMessage();
        }
        return valid;
    }

    /// Export to specified folder
    ///
    /// @param dir_basic   Source OS
    /// @param dir_items   Selected list
    /// @param data_dir    Folder to output data files
    /// @param attr_dir    Folder to output attribute files
    /// @param file_object [in,out] File object
    /// @param depth       Depth
    /// @return 1 warning
    /// @return 0 normal
    /// @return -1 error
    /// @attention This function is called recursively.
    public int ExportDataFiles(DiskBasic dir_basic, List<DiskBasicDirItem> dir_items, String data_dir, String attr_dir, Path file_object, int depth) {
        if (dir_items == null) return 0;

        if (depth > gConfig.GetDirDepth()) {
            return -1;
        }

        int sts = 0;
        // Select exportable files
        int count = dir_items.size();
        List<DiskBasicDirItem> valid_items = new ArrayList<>();
        for (int n = 0; n < count && sts >= 0; n++) {
            DiskBasicDirItem item = dir_items.get(n);
            if (item == null) {
                continue;
            }
            // Unused is not allowed
            if (!item.IsUsed()) {
                continue;
            }
            // Cannot load
            if (!item.IsLoadable()) {
                continue;
            }
            valid_items.add(item);
        }

        count = valid_items.size();

        AppendExportCounter(count);

        boolean attr_exists = !attr_dir.isEmpty();
        for (int n = 0; n < count && sts >= 0; n++) {
            IncreaseExportCounter();
            BeginBusyCursorExportCounterIfNeed();

            DiskBasicDirItem item = valid_items.get(n);

            String native_name = item.GetFileNameStrForExport();
            // Process before exporting (whether to change file name)
            String[] native_name_arr = {native_name};
            if (!item.PreExportDataFile(native_name_arr)) {
                sts = -1;
                break;
            }
            native_name = native_name_arr[0];

            if (native_name.isEmpty()) {
                continue;
            }
            // Escape characters that cannot be set in file name
            String file_name = Utils.encodeFileName(native_name);
            // Create full path
            Path full_data_name_path = Paths.get(data_dir, file_name);
            String full_data_name = full_data_name_path.toString();
            String full_attr_name = attr_exists ? Paths.get(attr_dir, file_name + ".xml").toString() : attr_dir; // "xml"

            if (full_data_name.length() > 255 || full_attr_name.length() > 255) {
                // Path too long
                sts = 1;
                dir_basic.GetErrinfo().SetError(DiskBasicError.ERRV_CANNOT_EXPORT, native_name);
                dir_basic.GetErrinfo().SetError(DiskBasicError.ERR_PATH_TOO_DEEP);
                continue;
            }

            if (item.IsDirectory()) {
                // If directory
                // Assign directory
                boolean valid = dir_basic.AssignDirectory(item);
                if (!valid) {
                    sts = 1;
                    dir_basic.GetErrinfo().SetError(DiskBasicError.ERRV_CANNOT_EXPORT, native_name);
                    continue;
                }
                // Create data subfolder
                File data_file = new File(full_data_name);
                if (data_file.exists()) {
                    // Already exists (Assuming File.exists() covers both FileExists and DirExists from Path)
                    sts = 1;
                    dir_basic.GetErrinfo().SetError(DiskBasicError.ERRV_CANNOT_EXPORT, native_name);
                    dir_basic.GetErrinfo().SetError(DiskBasicError.ERR_FILE_ALREADY_EXIST);
                    continue;
                }
                if (!data_file.mkdir()) {
                    sts = 1;
                    dir_basic.GetErrinfo().SetError(DiskBasicError.ERRV_CANNOT_EXPORT, native_name);
                    continue;
                }
                if (attr_exists) {
                    // Create attribute subfolder
                    File attr_file = new File(full_attr_name);
                    if (!attr_file.mkdir()) {
                        sts = 1;
                        dir_basic.GetErrinfo().SetError(DiskBasicError.ERRV_CANNOT_EXPORT, native_name);
                        continue;
                    }
                }
                // Export recursively
                sts |= ExportDataFiles(dir_basic, item.GetChildren(), full_data_name, full_attr_name, file_object, depth + 1);
            } else {
                // If file
                boolean rc = dir_basic.LoadFile(item, full_data_name);
                sts |= (rc ? 0 : -1);
                // Reflect date
                if (rc) {
                    item.WriteFileDateTime(full_data_name);
                }
                // Output attribute info in XML
                if (attr_exists) {
                    item.WriteFileAttrToXml(full_attr_name);
                }
            }

            // Add file object (for DnD)
            // Add only top level
            if (depth == 0 && file_object != null && sts >= 0) {
                file_object.AddFile(full_data_name);
            }
        }
        return sts;
    }

    /// Delete specified file
    ///
    /// @return 0:OK >0:Warning <0:Error
    /// @param dir_basic BASIC
    /// @param[in,out] dst_item  Item to delete
    public int DeleteDataFile(DiskBasic dir_basic, DiskBasicDirItem dst_item) {
        if (dst_item == null) return -1;

        int sts = dir_basic.IsDeletableFile(dst_item);
        if (sts == 0) {
            boolean is_directory = dst_item.IsDirectory();
            String filename = dst_item.GetFileNameStr();
            String msg = String.format("Do you really want to delete '%s'?", filename); // _("Do you really want to delete '%s'?")
            String title = is_directory ? "Delete a directory" : "Delete a file"; // _("Delete a directory") : _("Delete a file")
            int ans = wxMessageBox.Show(msg, title, wxDialogReturn.wxYES | wxDialogReturn.wxNO);
            if (ans != wxDialogReturn.wxYES) {
                return -1;
            }
            DiskBasicDirItems dst_items = new DiskBasicDirItems();
            dst_items.Add(dst_item);
            sts = DeleteDataFiles(dir_basic, dst_items, 0, null);

            // Update list
            UiDiskList disk_list = GetDiskListPanel();
            if (disk_list != null)
                ((UiDiskList) disk_list).DeleteDirectoryNode(dir_basic.GetDisk(), dst_item); // Placeholder method
            UiDiskFileList file_list = GetFileListPanel();
            if (file_list != null) ((UiDiskFileList) file_list).RefreshFiles(); // Placeholder method
        }
        if (sts != 0) {
            dir_basic.ShowErrorMessage();
        }
        return sts;
    }

    /// Delete specified files in bulk (recursive)
    ///
    /// @return 0:OK >0:Warning <0:Error
    /// @attention This function is called recursively.
    /// @param dir_basic       BASIC
    /// @param[in,out] items           List of items to delete
    /// @param depth           Depth
    /// @param[in,out] dir_items       List of sub-directory items
    public int DeleteDataFiles(DiskBasic dir_basic, List<DiskBasicDirItem> items, int depth, List<DiskBasicDirItem> dir_items) {
        if (depth > gConfig.GetDirDepth()) {
            return 1;
        }

        int sts = 0;
        // Some models delete items from list, so copy list in advance
        List<DiskBasicDirItem> tmp_items = new ArrayList<>();
        tmp_items.addAll(items);

        int tmp_count = tmp_items.size();
        for (int n = 0; n < tmp_count && sts >= 0; n++) {
            DiskBasicDirItem item = tmp_items.get(n);
            if (item == null) {
                continue;
            }
            if (!item.isUsed()) {
                continue;
            }
            // Can be deleted?
            if (!item.isDeletable()) {
                continue;
            }
            boolean is_directory = item.isDirectory();
            if (is_directory) {
                // Assign
                dir_basic.AssignDirectory(item);
                // If directory, delete files in directory first
                List<DiskBasicDirItem> sitems = item.getChildren();
                if (sitems != null) {
                    int ssts = DeleteDataFiles(dir_basic, sitems, depth + 1, null); // dir_items should be null for recursive call's sub-items
                    if (ssts == 0 && dir_items != null) {
                        dir_items.add(item);
                    }
                    sts |= (ssts != 0 ? -1 : 0);
                }
            }
            if (sts >= 0) {
                // Delete
                boolean ssts = dir_basic.DeleteFile(item, false);
                sts |= (ssts ? 0 : -1);
            }
        }
        return sts;
    }

    /// Whether directory can be created
    ///
    /// @return true: possible, false: impossible
    public boolean CanMakeDirectory(DiskBasic dir_basic) {
        return dir_basic != null ? dir_basic.CanMakeDirectory() : false;
    }

    /// Create directory
    /// Show dialog when directory name is duplicated
    ///
    /// @return 1: name already exists, -1: other error
    /// @param[in,out] dir_basic Destination OS
    /// @param[in,out] dir_item  Destination directory
    /// @param name      Directory name
    /// @param title     Dialog title
    /// @param[out] nitem     Created directory item
    public int MakeDirectory(DiskBasic dir_basic, DiskBasicDirItem dir_item, String name, String title, DiskBasicDirItem[] nitem) {
        int sts = 1;
        String dir_name = name;
        {
            // Change name if necessary
            DiskBasicDirItem pre_item = dir_basic.CreateDirItem();
            String[] dir_name_arr = {dir_name};
            if (!pre_item.PreExportDataFile(dir_name_arr)) { // PreImportDataFile is used in C++, but it takes String& (mutable), using PreExportDataFile as a mutable placeholder
                sts = -1;
            }
            dir_name = dir_name_arr[0];
            // delete pre_item (GC)
        }
        while (sts > 0) {
            sts = dir_basic.MakeDirectory(dir_item, dir_name, gConfig.DoesIgnoreDateTime(), nitem);
            if (sts == 1) {
                // Show dialog as same name exists
                dir_basic.ClearErrorMessage();
                String msgs = "The same file name or directory already exists."; // _("The same file name or directory already exists.")
                msgs += "\n";
                msgs += "Please rename this."; // _("Please rename this.")
                DiskBasicDirItem temp_item = dir_basic.CreateDirItem();

                // IntNameBox dlg is a placeholder
                // IntNameBox dlg = new IntNameBox(this, this, wxID_ANY, title, msgs, dir_basic, temp_item, dir_name, dir_name, 0, null, IntNameBoxFlags.INTNAME_NEW_FILE | IntNameBoxFlags.INTNAME_SHOW_TEXT | IntNameBoxFlags.INTNAME_SPECIFY_FILE_NAME);

                // Simulate dialog logic: Assume user enters a new name or cancels
                int ans = wxDialogReturn.wxCANCEL; // Placeholder for dlg.ShowModal()

                if (ans != wxDialogReturn.wxID_OK) {
                    sts = -1;
                } else {
                    // dlg.GetInternalName(dir_name); // Placeholder
                    // if (temp_item.CanIgnoreDateTime()) { // Placeholder
                    //     config.IgnoreDateTime(dlg.DoesIgnoreDateTime(config.DoesIgnoreDateTime())); // Placeholder
                    // }
                    // Simulate a new name for the loop to continue or exit
                    dir_name = "new_name_placeholder";
                }
                // delete temp_item (GC)
            }
        }

        return sts;
    }

    /// Assign directory
    ///
    /// @param dir_basic Current OS
    /// @param dir_item  Directory item
    /// @return true:OK false:Error
    public boolean AssignDirectory(DiskBasic dir_basic, DiskBasicDirItem dir_item) {
        if (dir_basic == null) return false;

        boolean sts = dir_basic.AssignDirectory(dir_item);
        if (sts) {
            // Update list
            UiDiskList disk_list = GetDiskListPanel();
            if (disk_list != null)
                ((UiDiskList) disk_list).SelectDirectoryNode(dir_basic.GetDisk(), dir_item); // Placeholder method
        }
        return sts;
    }

    /// Change directory
    ///
    /// @param dir_basic    Current OS
    /// @param dir_item     Destination directory item
    /// @param refresh_list Whether to refresh file list
    /// @return true:OK false:Error
    public boolean ChangeDirectory(DiskBasic dir_basic, DiskBasicDirItem dir_item, boolean refresh_list) {
        if (dir_basic == null) return false;

        boolean sts = dir_basic.ChangeDirectory(dir_item);
        if (sts) {
            // Update list
            if (refresh_list) {
                UiDiskFileList file_list = GetFileListPanel();
                if (file_list != null) ((UiDiskFileList) file_list).SetFiles(); // Placeholder method
            }
            UiDiskList disk_list = GetDiskListPanel();
            if (disk_list != null)
                ((UiDiskList) disk_list).SelectDirectoryNode(dir_basic.GetDisk(), dir_item); // Placeholder method
        }
        return sts;
    }

    /// Delete directory
    ///
    /// @param dir_basic Current OS
    /// @param dir_item  Directory item to delete
    /// @return true:OK false:Error
    public boolean DeleteDirectory(DiskBasic dir_basic, DiskBasicDirItem dir_item) {
        if (dir_basic == null || dir_item == null) return false;

        DiskBasicDirItem parent = dir_item.GetParent();
        if (parent != null) {
            // Parent directory display needs update
            parent.ValidDirectory(false);
        }
        int sts = DeleteDataFile(dir_basic, dir_item);

        return (sts != 0);
    }

    /// Message dialog display when same file name exists when displaying file name dialog
    ///
    /// @param dir_basic Current OS
    /// @param dir_item  Current directory
    /// @param temp_item Directory item
    /// @param file_name File path
    /// @param file_size File size
    /// @param date_time Date and time
    /// @param style     Style (IntNameBoxShowFlags)
    /// @return wxYES
    /// @return wxCANCEL
    protected int ShowIntNameBoxAndCheckSameFile(DiskBasic dir_basic, DiskBasicDirItem dir_item, DiskBasicDirItem temp_item, String file_name, int file_size, DiskBasicDirItemAttr date_time, int style) {
        int ans = wxDialogReturn.wxNO;
        boolean skip_dlg = gConfig.IsSkipImportDialog();
        IntNameBox dlg = null; // Placeholder
        String int_name = file_name;

        // Generate file name from file path
        if ((style & IntNameBoxFlags.INTNAME_NEW_FILE) != 0) {
            // When importing from external
            String[] int_name_arr = {int_name};
            if (!temp_item.PreExportDataFile(int_name_arr)) { // PreImportDataFile takes String& (mutable)
                // Error
                ans = wxDialogReturn.wxCANCEL;
            }
            int_name = int_name_arr[0];
        }
        while (ans != wxDialogReturn.wxYES && ans != wxDialogReturn.wxCANCEL) {
            if (!skip_dlg) {
                // Show file name dialog
                if (dlg == null) {
                    // dlg = new IntNameBox(this, this, wxID_ANY, _("Import File"), "", dir_basic, temp_item, file_name, int_name, file_size, date_time, style); // Placeholder
                }
                int dlgsts = wxDialogReturn.wxID_OK; // Placeholder for dlg.ShowModal()

                RestartImportCounter();

                if (dlgsts == wxDialogReturn.wxID_OK) {
                    // dlg.GetInternalName(int_name); // Placeholder: assuming int_name is set by dialog logic

                    // Reflect file name and attribute values specified in dialog to item
                    if (!SetDirItemFromIntNameDialog(temp_item, dlg, dir_basic, true)) { // Placeholder: dlg is null
                        ans = wxDialogReturn.wxCANCEL;
                        break;
                    }

                    // Check file size
                    int[] limit = {0};
                    if (!temp_item.IsFileValidSize(dlg, file_size, limit)) {
                        String msg = String.format("File size is larger than %d bytes, do you want to continue?", limit[0]); // _("File size is larger than %d bytes, do you want to continue?")
                        ans = wxMessageBox.Show(msg, "File is too large", wxDialogReturn.wxYES | wxDialogReturn.wxNO | wxDialogReturn.wxCANCEL); // _("File is too large")
                        if (ans == wxDialogReturn.wxNO) continue;
                        else if (ans == wxDialogReturn.wxCANCEL) break;
                    } else {
                        ans = wxDialogReturn.wxYES;
                    }
                } else {
                    ans = wxDialogReturn.wxCANCEL;
                    break;
                }
            } else {
                // When not displaying dialog
                if ((style & IntNameBoxFlags.INTNAME_NEW_FILE) != 0) {
                    // Importing from external and no dialog
                    // Is file name valid?
                    IntNameValidator vali = new IntNameValidator(temp_item, "file name", dir_basic.GetValidFileName()); // _("file name")
                    if (!vali.Validate(this, int_name)) {
                        // File name is inappropriate
                        skip_dlg = false;
                        ans = wxDialogReturn.wxNO;
                        continue;
                    }
                    // Determine attribute from file name and reflect to item
                    if (!SetDirItemFromIntNameParam(temp_item, file_name, int_name, date_time, dir_basic, true)) {
                        ans = wxDialogReturn.wxCANCEL;
                        break;
                    }
                } else {
                    // When importing from internal
                    boolean ignore_datetime = gConfig.DoesIgnoreDateTime();
                    int ignore_type = temp_item.CanIgnoreDateTime().getValue();
                    if (!(ignore_datetime && (ignore_type & DiskBasicDirItem.enDateTime.DATETIME_CREATE.getValue()) != 0)) {
                        temp_item.SetFileCreateDateTime(date_time.GetCreateDateTime());
                    }
                    if (!(ignore_datetime && (ignore_type & DiskBasicDirItem.enDateTime.DATETIME_MODIFY.getValue()) != 0)) {
                        temp_item.SetFileModifyDateTime(date_time.GetModifyDateTime());
                    }
                    if (!(ignore_datetime && (ignore_type & DiskBasicDirItem.enDateTime.DATETIME_ACCESS.getValue()) != 0)) {
                        temp_item.SetFileAccessDateTime(date_time.GetAccessDateTime());
                    }
                }
                ans = wxDialogReturn.wxYES;
            }

            if (ans == wxDialogReturn.wxYES) {
                // Check for duplicate file name
                int sts = dir_basic.IsFileNameDuplicated(dir_item, temp_item);
                if (sts < 0) {
                    // Already exists, cannot overwrite
                    skip_dlg = false;
                    String msg = String.format("File '%s' already exists and cannot overwrite, please rename it.", temp_item.GetFileNameStr()); // _("File '%s' already exists and cannot overwrite, please rename it.")
                    ans = wxMessageBox.Show(msg, "File exists", wxDialogReturn.wxOK | wxDialogReturn.wxCANCEL); // _("File exists")
                    if (ans == wxDialogReturn.wxOK) continue;
                    else break;
                } else if (sts == 1) {
                    // Overwrite confirmation dialog
                    skip_dlg = false;
                    String msg = String.format("File '%s' already exists, do you really want to overwrite it?", temp_item.GetFileNameStr()); // _("File '%s' already exists, do you really want to overwrite it?")
                    ans = wxMessageBox.Show(msg, "File exists", wxDialogReturn.wxYES | wxDialogReturn.wxNO | wxDialogReturn.wxCANCEL); // _("File exists")
                    if (ans == wxDialogReturn.wxNO) continue;
                    else if (ans == wxDialogReturn.wxCANCEL) break;
                } else {
                    ans = wxDialogReturn.wxYES;
                }
            } else {
                ans = wxDialogReturn.wxCANCEL;
                break;
            }
        }

        // delete dlg (GC)
        return ans;
    }

    /// Reflect contents of file name dialog
    ///
    /// @param item   Directory item
    /// @param dlg    File name dialog
    /// @param basic  BASIC
    /// @param rename Whether file name can be changed
    /// @return true:OK false:Error
    public boolean SetDirItemFromIntNameDialog(DiskBasicDirItem item, IntNameBox dlg, DiskBasic basic, boolean rename) {
        DiskBasicDirItemAttr attr = new DiskBasicDirItemAttr();

        // Reflect parameters to configuration
        // config.SkipImportDialog(dlg.IsSkipDialog(config.IsSkipImportDialog())); // Placeholder
        // if (item.CanIgnoreDateTime()) { // Placeholder
        //     config.IgnoreDateTime(dlg.DoesIgnoreDateTime(config.DoesIgnoreDateTime())); // Placeholder
        // }

        // Reflect attributes to item
        String newname = "";
        // dlg.GetInternalName(newname); // Placeholder

        attr.Renameable(rename);
        attr.SetFileName(newname, item.GetOptionalNameInAttrDialog(dlg)); // Placeholder dlg

        attr.IgnoreDateTime(gConfig.DoesIgnoreDateTime());

        // attr.SetCreateDateTime(dlg.GetCreateDateTime()); // Placeholder dlg
        // attr.SetModifyDateTime(dlg.GetModifyDateTime()); // Placeholder dlg
        // attr.SetAccessDateTime(dlg.GetAccessDateTime()); // Placeholder dlg

        // attr.SetStartAddress(dlg.GetStartAddress()); // Placeholder dlg
        // attr.SetEndAddress(dlg.GetEndAddress()); // Placeholder dlg
        // attr.SetExecuteAddress(dlg.GetExecuteAddress()); // Placeholder dlg

        // Reflect model-dependent attributes to item
        boolean sts = item.SetAttrInAttrDialog(dlg, attr, basic.GetErrinfo()); // Placeholder dlg

        if (sts) {
            // Process attribute values if necessary
            sts = item.ProcessAttr(attr, basic.GetErrinfo());
        }
        if (sts) {
            // Update attributes
            sts = basic.ChangeAttr(item, attr);
        }

        return sts;
    }

    /// Reflect file name
    ///
    /// @param item      Directory item
    /// @param file_path File path
    /// @param intname   Internal file name
    /// @param date_time Date and time
    /// @param basic     BASIC
    /// @param rename    Whether file name can be changed
    /// @return true:OK false:Error
    public boolean SetDirItemFromIntNameParam(DiskBasicDirItem item, String file_path, String intname, DiskBasicDirItemAttr date_time, DiskBasic basic, boolean rename) {
        DiskBasicDirItemAttr attr = new DiskBasicDirItemAttr();

        // Reflect attributes to item
        attr.Renameable(rename);
        attr.SetFileName(intname, item.ConvOptionalNameFromFileName(file_path));

        attr.IgnoreDateTime(gConfig.DoesIgnoreDateTime());
        attr.SetCreateDateTime(date_time.GetCreateDateTime());
        attr.SetModifyDateTime(date_time.GetModifyDateTime());
        attr.SetAccessDateTime(date_time.GetAccessDateTime());

        // Set attributes from file name
        attr.SetFileAttr(basic.GetFormatTypeNumber(), item.convFileTypeFromFileName(file_path), item.convOriginalTypeFromFileName(file_path));

        boolean sts = true;
        if (sts) {
            // Process attribute values if necessary
            sts = item.ProcessAttr(attr, basic.GetErrinfo());
        }
        if (sts) {
            // Update attributes
            sts = basic.ChangeAttr(item, attr);
        }
        return sts;
    }

    // --- Virtual methods from uimainprocess.h ---

    /** Get default font for list window */
    public void GetDefaultListFont(Object font) {
    } // wxFont is replaced by Object, as it's just a placeholder

    /// @name Disk tree in left panel
    //@{

    /** Return disk tree in left panel */
    public UiDiskList GetDiskListPanel() {
        return null;
    }
    //@}

    /// @name File list in right bottom panel
    //@{

    /** Return file list panel in right bottom panel */
    public UiDiskFileList GetFileListPanel(boolean inst) {
        return null;
    }

    public UiDiskFileList GetFileListPanel() {
        return GetFileListPanel(false);
    }
    //@}

    /** Return dump window */
    public UiDiskBinDumpFrame GetBinDumpFrame() {
        return null;
    }

    /// @name Status counter
    //@{
    public void StartExportCounter(int count, String message) {
    }

    public void AppendExportCounter(int count) {
    }

    public void IncreaseExportCounter() {
    }

    public void BeginBusyCursorExportCounterIfNeed() {
    }

    public void FinishExportCounter(String message) {
    }

    public void StartImportCounter(int count, String message) {
    }

    public void AppendImportCounter(int count) {
    }

    public void IncreaseImportCounter() {
    }

    public void BeginBusyCursorImportCounterIfNeed() {
    }

    public void FinishImportCounter(String message) {
    }

    public void RestartImportCounter() {
    }
    //@}

    /// @name Property
    //@{

    /** Unique number */
    public int GetUniqueNumber() {
        return m_unique_number;
    }

    /** Increment unique number by 1 */
    public void IncreaseUniqueNumber() {
        m_unique_number++;
    }
    //@}
}