/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;
import javax.swing.JOptionPane;


/**
 * 結果保存用のクラス abstract
 */
public abstract class ResultInfo {

    //
    //                         内部クラス (ログ)
    //

    /**
     * シンプルなログクラス (Java版)
     */
    public static class Log {

        /* Log レベル定数 */
        public static final int MyLog_Error = 1;
        public static final int MyLog_Warn = 2;
        public static final int MyLog_Info = 3;
        public static final int MyLog_Debug = 4;

        /** 例: コンソールに出力するだけの実装 */
        public void setMessage(int level, String msg) {
            String prefix;
            switch (level) {
                case MyLog_Error:
                    prefix = "[ERROR] ";
                    break;
                case MyLog_Warn:
                    prefix = "[WARN ] ";
                    break;
                case MyLog_Info:
                    prefix = "[INFO ] ";
                    break;
                case MyLog_Debug:
                    prefix = "[DEBUG] ";
                    break;
                default:
                    prefix = "[LOG  ] ";
                    break;
            }
            System.out.println(prefix + msg);
        }
    }

    /*  */
    /*                         フィールド宣言String
    /*  */
    /** 結果レベル (0:正常, -1:エラー, 1:警告, 2:情報) */
    protected int valid;

    /** すべてのメッセージ */
    protected List<String> msgs;

    /** 取得したメッセージを格納するバッファ */
    protected List<String> bufs;

    /*  */
    /*                         ログオブジェクト (静的)                     */
    /*  */
    protected static Log myLog = new Log();

    /*  */
    /*                         コンストラクタ／コピー                        */
    /*  */

    /** デフォルトコンストラクタ */
    public ResultInfo() {
        msgs = new ArrayList<>();
        bufs = new ArrayList<>();
        clear();
    }

    /** コピーコンストラクタ */
    public ResultInfo(ResultInfo src) {
        this.valid = src.valid;
        this.msgs = new ArrayList<>(src.msgs);
        this.bufs = new ArrayList<>();
    }

    /*  */
    /*                         バリアブルアンダーセット (演算子代入)      */
    /*  */
    public ResultInfo copyFrom(ResultInfo src) {
        this.valid = src.valid;
        this.msgs.clear();
        this.msgs.addAll(src.msgs);
        this.bufs.clear();
        return this;
    }

    /**
     * デストラクタ
     */
    public void destroy() {
        // Java では GC が自動で行われるので何もしない
    }

    /**
     * Clear メソッド
     */
    public void clear() {
        valid = 0;
        msgs.clear();
        bufs.clear();
    }

    /* */
    public void setError(int errorNumber, Object... args) {
        setErrorV(errorNumber, args);
    }

    public void setWarn(int errorNumber, Object... args) {
        setWarnV(errorNumber, args);
    }

    public void setInfo(int errorNumber, Object... args) {
        setInfoV(errorNumber, args);
    }

    /* */
    public void setErrorV(int errorNumber, Object[] args) {
        setMessageV(errorNumber, args);
        valid = -1;
    }

    public void setWarnV(int errorNumber, Object[] args) {
        setMessageV(errorNumber, args);
        if (valid == 0) valid = 1;
    }

    public void setInfoV(int errorNumber, Object[] args) {
        setMessageV(errorNumber, args);
        if (valid == 0) valid = 2;
    }

    /** メッセージ生成を派生クラスに委譲する抽象メソッド */
    public abstract void setMessageV(int errorNumber, Object[] args);

    /* GetMessages（配列版）*/
    public void getMessages(List<String> arr) {
        arr.clear();
        arr.addAll(msgs);
    }

    /* GetMessages（バッファ版）*/
    public List<String> getMessages(int maxrow) {
        bufs.clear();
        int level = (valid < 0) ? Log.MyLog_Error : Log.MyLog_Info;

        if (valid < 2) {
            for (String msg : msgs) {
                myLog.setMessage(level, msg);
            }
        }

        if (maxrow >= 0) {
            int cnt = msgs.size();
            for (int i = 0; i < Math.min(maxrow, cnt); i++) {
                bufs.add(msgs.get(i));
            }
            if (maxrow < cnt) {
                bufs.add(String.format(
                        Locale.getDefault(),
                        "And have more %u messages...",
                        cnt - maxrow));
            }
            return bufs;
        } else {
            return msgs;
        }
    }

    /** 結果ダイアログを表示 */
    public void show() {
        showMessage(getValid(), msgs);
    }

    /** 結果ダイアログを表示 (静的) */
    public static void showMessage(int level, List<String> msgs) {
        StringJoiner sj = new StringJoiner("\n");
        for (String m : msgs) {
            sj.add(m);
        }
        String msg = sj.toString();
        if (msg.isEmpty()) return;

        String caption;
        int icon = JOptionPane.INFORMATION_MESSAGE;

        if (level < 0) {
            caption = "Error";
            icon = JOptionPane.ERROR_MESSAGE;
        } else if (level == 1) {
            caption = "Warning";
            icon = JOptionPane.WARNING_MESSAGE;
        } else {
            caption = "Information";
            icon = JOptionPane.INFORMATION_MESSAGE;
        }

        JOptionPane.showMessageDialog(
                null,
                msg,
                caption,
                icon
        );
    }

    /** メッセージダイアログを表示 (静的) */
    public static int showErrWarnMessage(int code, List<String> msgs) {
        StringJoiner sj = new StringJoiner("\n");
        for (String m : msgs) {
            sj.add(m);
        }
        String msg = sj.toString();
        if (msg.isEmpty()) return 0;

        String caption;
        int optionType = JOptionPane.OK_OPTION;
        int icon = JOptionPane.INFORMATION_MESSAGE;

        if (code < 0) {
            caption = "Error";
            icon = JOptionPane.ERROR_MESSAGE;
        } else if (code > 0) {
            caption = "Warning";
            msg += "\nDo you want to continue?";
            optionType = JOptionPane.YES_NO_OPTION;
            icon = JOptionPane.WARNING_MESSAGE;
        } else {
            caption = "Information";
            icon = JOptionPane.INFORMATION_MESSAGE;
        }

        int ans = JOptionPane.showConfirmDialog(
                null,
                msg,
                caption,
                optionType,
                icon
        );

        return (code == 0 || ans == JOptionPane.YES_OPTION) ? 0 : -1;
    }

    /*  */
    public void setValid(int val) {
        this.valid = val;
    }

    public int getValid() {
        return valid;
    }
}
