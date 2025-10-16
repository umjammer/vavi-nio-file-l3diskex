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
import l3diskex.basicfmt.BasicFmt.DiskBasic;
import l3diskex.basicfmt.DiskBasicDirItem;
import l3diskex.basicfmt.DiskBasicDirItem.DiskBasicDirItemAttr;
import l3diskex.basicfmt.DiskBasicDirItem.DiskBasicDirItems;
import l3diskex.basicfmt.DiskBasicError;
import l3diskex.ui.IntNameValidatorFile.IntNameValidator;
import l3diskex.ui.UIBinDump.UiDiskBinDumpFrame;


/// ディスク＆ファイル操作
class UiDiskProcess extends JFrame {
    protected int m_unique_number;

    // Constructor
    public UiDiskProcess(JWindow parent, String id, String title, Point pos, Dimension size) {
        super(parent, id, title, pos, size);
        m_unique_number = 0;
    }

    /// 指定したファイルをインポート
    /// @param     paths     ファイルパスのリスト
    /// @param [in,out] dir_basic 保存先のOS
    /// @param [in,out] dir_item  保存先ディレクトリアイテム
    /// @param     confirm   ディレクトリを含む場合に確認ダイアログを表示するか
    /// @param     start_msg 開始メッセージ
    /// @param     end_msg   終了メッセージ
    /// @return true:OK false:Error
    public boolean ImportDataFiles(List<String> paths, DiskBasic dir_basic, DiskBasicDirItem dir_item, boolean confirm, String start_msg, String end_msg) {
        if (dir_basic == null) {
            return false;
        }

        if (confirm) {
            // 確認ダイアログを表示する
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

        // ディレクトリの表示は更新が必要
        dir_item.ValidDirectory(false);
        // 右パネルのリストを更新
        UiDiskFileList file_list = GetFileListPanel();
        if (file_list != null) ((UiDiskFileList) file_list).RefreshFiles(); // Placeholder method
        // 左パネルのツリーを更新
        UiDiskList disk_list = GetDiskListPanel();
        if (disk_list != null) ((UiDiskList) disk_list).RefreshAllDirectoryNodes(dir_basic.GetDisk(), dir_basic.GetSelectedSide(), dir_item); // Placeholder method
        if (sts != 0) {
            dir_basic.ShowErrorMessage();
        }
        return (sts >= 0);
    }

    /// 指定したファイルをインポート
    /// @attention 再帰的に呼ばれる。 This function is called recursively.
    /// @param data_dir      データフォルダ
    /// @param attr_dir      属性フォルダ
    /// @param names         ファイル名のリスト
    /// @param [in,out] dir_basic 保存先のOS
    /// @param [in,out] dir_item  保存先ディレクトリアイテム
    /// @param depth         深さ
    /// @return  1 警告あり
    /// @return  0 正常
    /// @return -1 エラー
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
            // ファイル名を変換
            name = Utils.decodeFileName(name);

            if (Files.isDirectory(full_data_path)) {
                // フォルダの場合

                // 新規ディレクトリ作成
                DiskBasicDirItem[] new_dir_item = {null};
                sts = MakeDirectory(dir_basic, dir_item, name, "Import Directory", new_dir_item); // _("Import Directory")
                if (sts < 0) {
                    break;
                }
                DiskBasicDirItem new_item = new_dir_item[0];

                // フォルダ内のファイルリスト
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

                // 新規ディレクトリに一時的に移動して初期化などを行う
                // TODO
                DiskBasicDirItem cur_item = dir_basic.GetCurrentDirectory();
                if (!dir_basic.ChangeDirectory(new_item)) {
                    sts = -1;
                    break;
                }
                dir_basic.ChangeDirectory(cur_item);

                // 再帰的にインポート
                sts |= ImportDataFiles(full_data_path.toString(), full_attr_path.toString(), sub_names, dir_basic, new_item, depth + 1);

            } else {
                // ファイルの場合
                sts |= ImportDataFile(full_data_path.toString(), full_attr_path.toString(), name, dir_basic, dir_item);

            }
        }

        return sts;
    }

    /// 指定したファイルをインポート
    /// @param     full_data_path データファイルパス
    /// @param     full_attr_path 属性ファイルパス
    /// @param     file_name      ファイル名
    /// @param [in,out] dir_basic      保存先のOS
    /// @param [in,out] dir_item       保存先ディレクトリアイテム
    /// @return  1 警告あり処理継続
    /// @return  0 正常
    /// @return -1 エラー継続不可
    protected int ImportDataFile(String full_data_path, String full_attr_path, String file_name, DiskBasic dir_basic, DiskBasicDirItem dir_item) {
        if (dir_basic == null) {
            return -1;
        }

        if (!dir_basic.IsFormatted()) {
            return -1;
        }

        // ディスクの残りサイズのチェックと入力ファイルのサイズを得る
        int[] file_size = {0};
        if (!dir_basic.CheckFile(full_data_path, file_size)) {
            return -1;
        }

        // 外部からインポートのスタイル
        int style = IntNameBoxFlags.INTNAME_NEW_FILE | IntNameBoxFlags.INTNAME_SHOW_TEXT | IntNameBoxFlags.INTNAME_SHOW_ATTR | IntNameBoxFlags.INTNAME_SPECIFY_FILE_NAME | IntNameBoxFlags.INTNAME_SHOW_SKIP_DIALOG;

        int sts = 0;
        DiskBasicDirItem temp_item = dir_basic.CreateDirItem();

        // ファイル情報があれば読み込む
        String filename = file_name;
        DiskBasicDirItemAttr[] date_time_array = {new DiskBasicDirItemAttr()};
        DiskBasicDirItemAttr date_time = date_time_array[0];
        if (temp_item.ReadFileAttrFromXml(full_attr_path, date_time_array)) {
            // ファイル名
            filename = temp_item.GetFileNameStr();
            // 内部からインポートに変更
            style = IntNameBoxFlags.INTNAME_IMPORT_INTERNAL | IntNameBoxFlags.INTNAME_SHOW_TEXT | IntNameBoxFlags.INTNAME_SHOW_ATTR
                    | IntNameBoxFlags.INTNAME_SPECIFY_CDATE_TIME | IntNameBoxFlags.INTNAME_SPECIFY_MDATE_TIME | IntNameBoxFlags.INTNAME_SPECIFY_ADATE_TIME
                    | IntNameBoxFlags.INTNAME_SHOW_SKIP_DIALOG;
            // コピーできるか
            if (!temp_item.IsCopyable()) {
                // エラーにはしない
                sts = 1;
            }
        } else {
            // ファイルから日付を得る
            temp_item.ReadFileDateTime(full_data_path, date_time);
            style |= IntNameBoxFlags.INTNAME_SPECIFY_CDATE_TIME | IntNameBoxFlags.INTNAME_SPECIFY_MDATE_TIME | IntNameBoxFlags.INTNAME_SPECIFY_ADATE_TIME;
        }
        if (sts == 0) {
            // ダイアログ表示
            int ans = ShowIntNameBoxAndCheckSameFile(dir_basic, dir_item, temp_item, filename, file_size[0], date_time, style);
            if (ans == wxDialogReturn.wxYES) {
                // ディスク内にセーブする
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

    /// 指定したファイルを上書きでインポート
    /// @param item          保存するファイルのディレクトリアイテム（属性などを持っている）
    /// @param path          保存するデータファイルパス
    /// @param [in,out] dir_basic 保存先のOS
    /// @param [in,out] dir_item  保存先ディレクトリアイテム
    /// @param start_msg     開始メッセージ
    /// @param end_msg       終了メッセージ
    /// @return true:OK false:Error
    public boolean ImportDataFile(DiskBasicDirItem item, String path, DiskBasic dir_basic, DiskBasicDirItem dir_item, String start_msg, String end_msg) {
        if (dir_basic == null) {
            return false;
        }

        // 仮ディレクトリアイテムを作成
        DiskBasicDirItem pitem = dir_basic.CreateDirItem();
        pitem.CopyItem(item);

        StartImportCounter(1, start_msg);

        boolean valid = dir_basic.SaveFile(path, dir_item, pitem);

        IncreaseImportCounter();
        FinishImportCounter(end_msg);

        // ディレクトリの表示は更新が必要
        dir_item.ValidDirectory(false);
        // 右パネルのリストを更新
        UiDiskFileList file_list = GetFileListPanel();
        if (file_list != null) ((UiDiskFileList) file_list).RefreshFiles(); // Placeholder method
        // 左パネルのツリーを更新
        UiDiskList disk_list = GetDiskListPanel();
        if (disk_list != null) ((UiDiskList) disk_list).RefreshAllDirectoryNodes(dir_basic.GetDisk(), dir_basic.GetSelectedSide(), dir_item); // Placeholder method

        // delete pitem (Java garbage collection handles this)
        return valid;
    }

    /// 指定したファイルにエクスポート
    /// @param[in] dir_basic    抽出元のOS
    /// @param[in] item         抽出したいディレクトリアイテム
    /// @param[in] path         ファイルパス
    /// @param[in] start_msg    開始メッセージ
    /// @param[in] end_msg      終了メッセージ
    /// @return true:OK false:Error
    public boolean ExportDataFile(DiskBasic dir_basic, DiskBasicDirItem item, String path, String start_msg, String end_msg) {
        if (dir_basic == null) return false;

        StartExportCounter(1, start_msg);

        // ロード
        boolean valid = dir_basic.LoadFile(item, path);
        // 日付を反映
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

    /// 指定したフォルダにエクスポート
    /// @attention 再帰的に呼ばれる。 This function is called recursively.
    /// @param     dir_basic   抽出元のOS
    /// @param     dir_items   選択したリスト
    /// @param     data_dir    データファイル出力先フォルダ
    /// @param     attr_dir    属性ファイル出力先フォルダ
    /// @param [in,out] file_object ファイルオブジェクト
    /// @param     depth       深さ
    /// @return  1 警告あり
    /// @return  0 正常
    /// @return -1 エラー
    public int ExportDataFiles(DiskBasic dir_basic, List<DiskBasicDirItem> dir_items, String data_dir, String attr_dir, Path file_object, int depth) {
        if (dir_items == null) return 0;

        if (depth > gConfig.GetDirDepth()) {
            return -1;
        }

        int sts = 0;
        // エクスポート可能なファイルを選択
        int count = dir_items.size();
        List<DiskBasicDirItem> valid_items = new ArrayList<>();
        for (int n = 0; n < count && sts >= 0; n++) {
            DiskBasicDirItem item = dir_items.get(n);
            if (item == null) {
                continue;
            }
            // 未使用は不可
            if (!item.IsUsed()) {
                continue;
            }
            // ロード不可
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
            // エクスポートする前の処理（ファイル名を変更するか）
            String[] native_name_arr = {native_name};
            if (!item.PreExportDataFile(native_name_arr)) {
                sts = -1;
                break;
            }
            native_name = native_name_arr[0];

            if (native_name.isEmpty()) {
                continue;
            }
            // ファイル名に設定できない文字をエスケープ
            String file_name = Utils.encodeFileName(native_name);
            // フルパスを作成
            Path full_data_name_path = Paths.get(data_dir, file_name);
            String full_data_name = full_data_name_path.toString();
            String full_attr_name = attr_exists ? Paths.get(attr_dir, file_name + ".xml").toString() : attr_dir; // "xml"

            if (full_data_name.length() > 255 || full_attr_name.length() > 255) {
                // パスが長すぎる
                sts = 1;
                dir_basic.GetErrinfo().SetError(DiskBasicError.ERRV_CANNOT_EXPORT, native_name);
                dir_basic.GetErrinfo().SetError(DiskBasicError.ERR_PATH_TOO_DEEP);
                continue;
            }

            if (item.IsDirectory()) {
                // ディレクトリの場合
                // ディレクトリをアサイン
                boolean valid = dir_basic.AssignDirectory(item);
                if (!valid) {
                    sts = 1;
                    dir_basic.GetErrinfo().SetError(DiskBasicError.ERRV_CANNOT_EXPORT, native_name);
                    continue;
                }
                // データサブフォルダを作成
                File data_file = new File(full_data_name);
                if (data_file.exists()) {
                    // 既にある (Assuming File.exists() covers both FileExists and DirExists from Path)
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
                    // 属性サブフォルダを作成
                    File attr_file = new File(full_attr_name);
                    if (!attr_file.mkdir()) {
                        sts = 1;
                        dir_basic.GetErrinfo().SetError(DiskBasicError.ERRV_CANNOT_EXPORT, native_name);
                        continue;
                    }
                }
                // 再帰的にエクスポート
                sts |= ExportDataFiles(dir_basic, item.GetChildren(), full_data_name, full_attr_name, file_object, depth + 1);
            } else {
                // ファイルの場合
                boolean rc = dir_basic.LoadFile(item, full_data_name);
                sts |= (rc ? 0 : -1);
                // 日付を反映
                if (rc) {
                    item.WriteFileDateTime(full_data_name);
                }
                // 属性情報をXMLで出力
                if (attr_exists) {
                    item.WriteFileAttrToXml(full_attr_name);
                }
            }

            // ファイルオブジェクトを追加(DnD用)
            // トップレベルのみ追加
            if (depth == 0 && file_object != null && sts >= 0) {
                file_object.AddFile(full_data_name);
            }
        }
        return sts;
    }

    /// 指定したファイルを削除
    /// @param[in]     dir_basic BASIC
    /// @param[in,out] dst_item  削除対象アイテム
    /// @return 0:OK >0:Warning <0:Error
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

            // リスト更新
            UiDiskList disk_list = GetDiskListPanel();
            if (disk_list != null) ((UiDiskList) disk_list).DeleteDirectoryNode(dir_basic.GetDisk(), dst_item); // Placeholder method
            UiDiskFileList file_list = GetFileListPanel();
            if (file_list != null) ((UiDiskFileList) file_list).RefreshFiles(); // Placeholder method
        }
        if (sts != 0) {
            dir_basic.ShowErrorMessage();
        }
        return sts;
    }

    /// 指定したファイルを一括削除（再帰的）
    /// @attention 再帰的に呼ばれる。 This function is called recursively.
    /// @param[in]     dir_basic       BASIC
    /// @param[in,out] items           削除対象アイテムリスト
    /// @param[in]     depth           深さ
    /// @param[in,out] dir_items       サブディレクトリアイテムリスト
    /// @return 0:OK >0:Warning <0:Error
    public int DeleteDataFiles(DiskBasic dir_basic, List<DiskBasicDirItem> items, int depth, List<DiskBasicDirItem> dir_items) {
        if (depth > gConfig.GetDirDepth()) {
            return 1;
        }

        int sts = 0;
        // 機種によってはアイテムをリストから削除するので予めリストをコピー
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
            // 削除できるか
            if (!item.isDeletable()) {
                continue;
            }
            boolean is_directory = item.isDirectory();
            if (is_directory) {
                // アサイン
                dir_basic.AssignDirectory(item);
                // ディレクトリのときは先にディレクトリ内ファイルを削除
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
                // 削除
                boolean ssts = dir_basic.DeleteFile(item, false);
                sts |= (ssts ? 0 : -1);
            }
        }
        return sts;
    }

    /// ディレクトリを作成できるか
    /// @return true:できる false:できない
    public boolean CanMakeDirectory(DiskBasic dir_basic) {
        return dir_basic != null ? dir_basic.CanMakeDirectory() : false;
    }

    /// ディレクトリ作成
    /// ディレクトリ名が重複する時にダイアログを表示
    /// @param[in,out] dir_basic 作成先のOS
    /// @param[in,out] dir_item  作成先のディレクトリ
    /// @param[in]     name      ディレクトリ名
    /// @param[in]     title     ダイアログのタイトル
    /// @param[out]    nitem     作成したディレクトリアイテム
    /// @return 1:同じ名前がある -1:その他エラー
    public int MakeDirectory(DiskBasic dir_basic, DiskBasicDirItem dir_item, String name, String title, DiskBasicDirItem[] nitem) {
        int sts = 1;
        String dir_name = name;
        {
            // 必要なら名前を変更
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
                // 同じ名前があるのでダイアログ表示
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
                    //     gConfig.IgnoreDateTime(dlg.DoesIgnoreDateTime(gConfig.DoesIgnoreDateTime())); // Placeholder
                    // }
                    // Simulate a new name for the loop to continue or exit
                    dir_name = "new_name_placeholder";
                }
                // delete temp_item (GC)
            }
        }

        return sts;
    }

    /// ディレクトリをアサインする
    /// @param dir_basic 現在のOS
    /// @param dir_item ディレクトリのアイテム
    /// @return true:OK false:Error
    public boolean AssignDirectory(DiskBasic dir_basic, DiskBasicDirItem dir_item) {
        if (dir_basic == null) return false;

        boolean sts = dir_basic.AssignDirectory(dir_item);
        if (sts) {
            // リスト更新
            UiDiskList disk_list = GetDiskListPanel();
            if (disk_list != null) ((UiDiskList) disk_list).SelectDirectoryNode(dir_basic.GetDisk(), dir_item); // Placeholder method
        }
        return sts;
    }

    /// ディレクトリを移動する
    /// @param dir_basic 現在のOS
    /// @param dir_item 移動先ディレクトリのアイテム
    /// @param refresh_list ファイルリストを更新するか
    /// @return true:OK false:Error
    public boolean ChangeDirectory(DiskBasic dir_basic, DiskBasicDirItem dir_item, boolean refresh_list) {
        if (dir_basic == null) return false;

        boolean sts = dir_basic.ChangeDirectory(dir_item);
        if (sts) {
            // リスト更新
            if (refresh_list) {
                UiDiskFileList file_list = GetFileListPanel();
                if (file_list != null) ((UiDiskFileList) file_list).SetFiles(); // Placeholder method
            }
            UiDiskList disk_list = GetDiskListPanel();
            if (disk_list != null) ((UiDiskList) disk_list).SelectDirectoryNode(dir_basic.GetDisk(), dir_item); // Placeholder method
        }
        return sts;
    }

    /// ディレクトリを削除する
    /// @param dir_basic 現在のOS
    /// @param dir_item  削除するディレクトリのアイテム
    /// @return true:OK false:Error
    public boolean DeleteDirectory(DiskBasic dir_basic, DiskBasicDirItem dir_item) {
        if (dir_basic == null || dir_item == null) return false;

        DiskBasicDirItem parent = dir_item.GetParent();
        if (parent != null) {
            // 親ディレクトリの表示は更新が必要
            parent.ValidDirectory(false);
        }
        int sts = DeleteDataFile(dir_basic, dir_item);

        return (sts != 0);
    }

    /// ファイル名ダイアログ表示と同じファイル名が存在する際のメッセージダイアログ表示
    /// @param dir_basic 現在のOS
    /// @param dir_item  現在のディレクトリ
    /// @param temp_item ディレクトリアイテム
    /// @param file_name ファイルパス
    /// @param file_size ファイルサイズ
    /// @param date_time 日時
    /// @param style     スタイル(IntNameBoxShowFlags)
    /// @return wxYES
    /// @return wxCANCEL
    protected int ShowIntNameBoxAndCheckSameFile(DiskBasic dir_basic, DiskBasicDirItem dir_item, DiskBasicDirItem temp_item, String file_name, int file_size, DiskBasicDirItemAttr date_time, int style) {
        int ans = wxDialogReturn.wxNO;
        boolean skip_dlg = gConfig.IsSkipImportDialog();
        IntNameBox dlg = null; // Placeholder
        String int_name = file_name;

        // ファイルパスからファイル名を生成
        if ((style & IntNameBoxFlags.INTNAME_NEW_FILE) != 0) {
            // 外部からインポート時
            String[] int_name_arr = {int_name};
            if (!temp_item.PreExportDataFile(int_name_arr)) { // PreImportDataFile takes String& (mutable)
                // エラー
                ans = wxDialogReturn.wxCANCEL;
            }
            int_name = int_name_arr[0];
        }
        while (ans != wxDialogReturn.wxYES && ans != wxDialogReturn.wxCANCEL) {
            if (!skip_dlg) {
                // ファイル名ダイアログを表示
                if (dlg == null) {
                    // dlg = new IntNameBox(this, this, wxID_ANY, _("Import File"), "", dir_basic, temp_item, file_name, int_name, file_size, date_time, style); // Placeholder
                }
                int dlgsts = wxDialogReturn.wxID_OK; // Placeholder for dlg.ShowModal()

                RestartImportCounter();

                if (dlgsts == wxDialogReturn.wxID_OK) {
                    // dlg.GetInternalName(int_name); // Placeholder: assuming int_name is set by dialog logic

                    // ダイアログで指定したファイル名や属性値をアイテムに反映
                    if (!SetDirItemFromIntNameDialog(temp_item, dlg, dir_basic, true)) { // Placeholder: dlg is null
                        ans = wxDialogReturn.wxCANCEL;
                        break;
                    }

                    // ファイルサイズのチェック
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
                // ダイアログを表示しないとき
                if ((style & IntNameBoxFlags.INTNAME_NEW_FILE) != 0) {
                    // 外部からインポート時でダイアログなし
                    // ファイル名が適正か
                    IntNameValidator vali = new IntNameValidator(temp_item, "file name", dir_basic.GetValidFileName()); // _("file name")
                    if (!vali.Validate(this, int_name)) {
                        // ファイル名が不適切
                        skip_dlg = false;
                        ans = wxDialogReturn.wxNO;
                        continue;
                    }
                    // 属性をファイル名から判定してアイテムに反映
                    if (!SetDirItemFromIntNameParam(temp_item, file_name, int_name, date_time, dir_basic, true)) {
                        ans = wxDialogReturn.wxCANCEL;
                        break;
                    }
                } else {
                    // 内部からインポート時
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
                // ファイル名重複チェック
                int sts = dir_basic.IsFileNameDuplicated(dir_item, temp_item);
                if (sts < 0) {
                    // 既に存在します 上書き不可
                    skip_dlg = false;
                    String msg = String.format("File '%s' already exists and cannot overwrite, please rename it.", temp_item.GetFileNameStr()); // _("File '%s' already exists and cannot overwrite, please rename it.")
                    ans = wxMessageBox.Show(msg, "File exists", wxDialogReturn.wxOK | wxDialogReturn.wxCANCEL); // _("File exists")
                    if (ans == wxDialogReturn.wxOK) continue;
                    else break;
                } else if (sts == 1) {
                    // 上書き確認ダイアログ
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

    /// ファイル名ダイアログの内容を反映させる
    /// @param item   ディレクトリアイテム
    /// @param dlg    ファイル名ダイアログ
    /// @param basic  BASIC
    /// @param rename ファイル名を変更できるか
    /// @return true:OK false:Error
    public boolean SetDirItemFromIntNameDialog(DiskBasicDirItem item, IntNameBox dlg, DiskBasic basic, boolean rename) {
        DiskBasicDirItemAttr attr = new DiskBasicDirItemAttr();

        // パラメータを設定に反映
        // gConfig.SkipImportDialog(dlg.IsSkipDialog(gConfig.IsSkipImportDialog())); // Placeholder
        // if (item.CanIgnoreDateTime()) { // Placeholder
        //     gConfig.IgnoreDateTime(dlg.DoesIgnoreDateTime(gConfig.DoesIgnoreDateTime())); // Placeholder
        // }

        // 属性をアイテムに反映
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

        // 機種依存の属性をアイテムに反映
        boolean sts = item.SetAttrInAttrDialog(dlg, attr, basic.GetErrinfo()); // Placeholder dlg

        if (sts) {
            // 必要なら属性値を加工する
            sts = item.ProcessAttr(attr, basic.GetErrinfo());
        }
        if (sts) {
            // 属性を更新
            sts = basic.ChangeAttr(item, attr);
        }

        return sts;
    }

    /// ファイル名を反映させる
    /// @param item      ディレクトリアイテム
    /// @param file_path ファイルパス
    /// @param intname   内部ファイル名
    /// @param date_time 日時
    /// @param basic     BASIC
    /// @param rename    ファイル名を変更できるか
    /// @return true:OK false:Error
    public boolean SetDirItemFromIntNameParam(DiskBasicDirItem item, String file_path, String intname, DiskBasicDirItemAttr date_time, DiskBasic basic, boolean rename) {
        DiskBasicDirItemAttr attr = new DiskBasicDirItemAttr();

        // 属性をアイテムに反映
        attr.Renameable(rename);
        attr.SetFileName(intname, item.ConvOptionalNameFromFileName(file_path));

        attr.IgnoreDateTime(gConfig.DoesIgnoreDateTime());
        attr.SetCreateDateTime(date_time.GetCreateDateTime());
        attr.SetModifyDateTime(date_time.GetModifyDateTime());
        attr.SetAccessDateTime(date_time.GetAccessDateTime());

        // ファイル名から属性を設定
        attr.SetFileAttr(basic.GetFormatTypeNumber(), item.convFileTypeFromFileName(file_path), item.convOriginalTypeFromFileName(file_path));

        boolean sts = true;
        if (sts) {
            // 必要なら属性値を加工する
            sts = item.ProcessAttr(attr, basic.GetErrinfo());
        }
        if (sts) {
            // 属性を更新
            sts = basic.ChangeAttr(item, attr);
        }
        return sts;
    }

    // --- Virtual methods from uimainprocess.h ---

    /// リストウィンドウのデフォルトフォントを得る
    public void GetDefaultListFont(Object font) {} // wxFont is replaced by Object, as it's just a placeholder

    /// @name 左パネルのディスクツリー
    //@{
    /// 左パネルのディスクツリーを返す
    public UiDiskList GetDiskListPanel() {
        return null;
    }
    //@}

    /// @name 右下パネルのファイルリスト
    //@{
    /// 右下パネルのファイルリストパネルを返す
    public UiDiskFileList GetFileListPanel(boolean inst) {
        return null;
    }
    public UiDiskFileList GetFileListPanel() {
        return GetFileListPanel(false);
    }
    //@}

    /// ダンプウィンドウを返す
    public UiDiskBinDumpFrame GetBinDumpFrame() {
        return null;
    }

    /// @name ステータスカウンター
    //@{
    public void StartExportCounter(int count, String message) {}
    public void AppendExportCounter(int count) {}
    public void IncreaseExportCounter() {}
    public void BeginBusyCursorExportCounterIfNeed() {}
    public void FinishExportCounter(String message) {}

    public void StartImportCounter(int count, String message) {}
    public void AppendImportCounter(int count) {}
    public void IncreaseImportCounter() {}
    public void BeginBusyCursorImportCounterIfNeed() {}
    public void FinishImportCounter(String message) {}
    public void RestartImportCounter() {}
    //@}

    /// @name プロパティ
    //@{
    /// ユニーク番号
    public int GetUniqueNumber() {
        return m_unique_number;
    }
    /// ユニーク番号を＋１
    public void IncreaseUniqueNumber() {
        m_unique_number++;
    }
    //@}
}