/*
 * Copyright (c) Sasaji. All rights reserved.
 */

package l3diskex;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;
import javax.swing.JOptionPane;


/**
 * An abstract class for storing results.
 */
public abstract class ResultInfo {

    private static final Logger logger = System.getLogger(ResultInfo.class.getName());

    /** result level (0:ok, -1:error, 1:warning, 2:info) */
    protected int valid;

    /** all messages */
    protected List<String> msgs;

    /** buffer to store fetched messages */
    protected List<String> bufs;

    /** default constructor */
    public ResultInfo() {
        msgs = new ArrayList<>();
        bufs = new ArrayList<>();
        clear();
    }

    /** copy constructor */
    public ResultInfo(ResultInfo src) {
        this.valid = src.valid;
        this.msgs = new ArrayList<>(src.msgs);
        this.bufs = new ArrayList<>();
    }

    /**
     * variable under set (assignment operator)
     */
    public ResultInfo copyFrom(ResultInfo src) {
        this.valid = src.valid;
        this.msgs.clear();
        this.msgs.addAll(src.msgs);
        this.bufs.clear();
        return this;
    }

    /**
     * Clear method
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

    /** An abstract method that delegates message generation to a derived class. */
    public abstract void setMessageV(int errorNumber, Object[] args);

    /* GetMessages (for array) */
    public void getMessages(List<String> arr) {
        arr.clear();
        arr.addAll(msgs);
    }

    /* GetMessages (for buffer) */
    public List<String> getMessages(int maxrow) {
        bufs.clear();
        Level level = (valid < 0) ? Level.ERROR : Level.INFO;

        if (valid < 2) {
            for (String msg : msgs) {
                logger.log(level, msg);
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
                        "And have more %d messages...",
                        cnt - maxrow));
            }
            return bufs;
        } else {
            return msgs;
        }
    }

    /** Show result dialog */
    public void show() {
        showMessage(getValid(), msgs);
    }

    /** Show result dialog (static) */
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

    /** Show message dialog (static) */
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

    /** */
    public void setValid(int val) {
        this.valid = val;
    }

    public int getValid() {
        return valid;
    }
}
