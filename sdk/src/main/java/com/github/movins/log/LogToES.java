package com.github.movins.log;

import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 日志输出到文件操作
 *
 */
public class LogToES {
  private static final String BAK_EXT = ".bak";
  private static final String LOG_TIME_FORMAT_STR = "yyyy-MM-dd kk:mm:ss.SSS";

  private static final String BAK_DATE_FORMAT_STR = "-yyyyMMdd-kkmmss.SSS";
  private static String PATTERN_STR = "-[0-9]{8}-[0-9]{6}.[0-9]{3}";
  private static Pattern PATTERN = Pattern.compile(PATTERN_STR);

  private static volatile boolean initied = false;
  private static File currentFile = null;
  private static File currentDir = null;
  private static BufferedWriter currentWriter;
  private static String currentName;

  private static final FastDateFormat LOG_FORMAT = FastDateFormat
      .getInstance(LOG_TIME_FORMAT_STR);

  private static FastDateFormat simpleDateFormat = FastDateFormat
      .getInstance(BAK_DATE_FORMAT_STR);

  private static ThreadLocal<Calendar> logCalendar = new ThreadLocal() {
    @Override
    protected Calendar initialValue() {
      return Calendar.getInstance();
    }
  };

  /** In MB. */
  public static final int MAX_FILE_SIZE = 100;// 修改日志最大文件为100M

  public static final int DEFAULT_BAK_FILE_NUM_LIMIT = 5;// release版 日志备份文件个数
  public static final int DEBUG_BAK_FILE_NUM_LIMIT = 10;// debug版 日志备份文件个数

  /** Back file num limit, when this is exceeded, will delete older logs. */
  private static int mBackFileNumLimit = DEFAULT_BAK_FILE_NUM_LIMIT;

  public static final int DEFAULT_BUFF_SIZE = 32 * 1024;

  /** Buffer size , threshold for flush/close. */
  private static int BUFF_SIZE = DEFAULT_BUFF_SIZE;

  private static Object mLock = new Object();

  /** To flush by interval. */
  private static long mLastMillis = 0;
  private static final long FLUSH_INTERVAL = 5000;

  private volatile static String mLogPath;

  private static HashMap<String, SimpleDateFormat> mSimpleDateFormatCache = new HashMap<String, SimpleDateFormat>();

  public static SimpleDateFormat getSimpleDateFormat(String format) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
      SimpleDateFormat sdf = mSimpleDateFormatCache.get(format);
      if (sdf == null) {
        sdf = new SimpleDateFormat(format);
        mSimpleDateFormatCache.put(format, sdf);
      }

      return sdf;
    } else {
      return new SimpleDateFormat(format);
    }
  }

  public static void setBackupLogLimitInMB(int logCapacityInMB) {
    if (logCapacityInMB > 0) {
      mBackFileNumLimit = logCapacityInMB / MAX_FILE_SIZE;
    }
  }

  public static boolean initialize(String logDir) {
    boolean result = false;
    do {
      if (initied) {
        result = true;
        break;
      }
      if (TextUtils.isEmpty(logDir)) {
        break;
      }
      currentDir = new File(logDir);
      currentDir.mkdirs();
      if (!currentDir.isDirectory()) {
        currentDir = null;
        break;
      }
      mLogPath = logDir;
      initied = true;

      result = true;
    } while (false);

    return result;
  }

  public static boolean isSizeLimited() {
    if (currentFile == null) {
      return false;
    }
    long fileSize = (currentFile.length() >>> 20);// convert to M bytes
    return fileSize >= MAX_FILE_SIZE;
  }

  public static boolean ready(String fileName) throws IOException {
    boolean result = false;
    do {
      if (!initied) {
        break;
      }
      if (TextUtils.isEmpty(fileName)) {
        break;
      }
      synchronized (mLock) {
        if (!fileName.equals(currentName)) {
          if (currentWriter != null) {
            currentWriter.close();
            currentWriter = null;
          }
          File logFile = new File(currentDir, fileName);
          if (!logFile.exists()) {
            try {
              logFile.createNewFile();
            } catch (IOException e) {
              e.printStackTrace();
              break;
            }
          }
          currentName = fileName;
          currentFile = logFile;
          FileWriter fileWriter = new FileWriter(logFile, true);
          currentWriter = new BufferedWriter(fileWriter, BUFF_SIZE);
        }
      }

      result = true;
    } while (false);

    return result;
  }

  public static String getLogPath() {
    return mLogPath;
  }

  public static void setBuffSize(int bytes) {
    BUFF_SIZE = bytes;
  }

  public static void writeLogToFile(String msg, boolean immediateClose, long timeMillis) throws IOException {
    writeLog(msg, immediateClose, timeMillis);
  }

  public static void writeLog(String msg, boolean immediateClose, long timeMillis) throws IOException {
    if (!initied) {
      return;
    }
    String strLog = convertLog(msg, timeMillis);
    synchronized (mLock) {
      if (currentWriter == null) {
        return;
      }
      // we can make FileWriter static, but when to close it
      currentWriter.write(strLog);

      // It doesn't matter there are multiple files gets mixed.
      final long curMillis = SystemClock.elapsedRealtime();
      if (curMillis - mLastMillis >= FLUSH_INTERVAL) {
        currentWriter.flush();
        mLastMillis = curMillis;
      }

      if (immediateClose) {
        currentWriter.close();
        currentName = null;
        currentFile = null;
        currentWriter = null;
      }
    }
  }

  private static String convertLog(String msg, long timeMillis) {
    Calendar logCal = logCalendar.get();
    logCal.setTimeInMillis(timeMillis);
    String strLog = LOG_FORMAT.format(logCal);
    StringBuffer sb = new StringBuffer(strLog.length() + msg.length() + 4);
    sb.append(strLog);
    sb.append(' ');
    sb.append(msg);
    sb.append('\n');

    return sb.toString();
  }

  private static File createFile(String path, String fileName) {
    StringBuilder sb = new StringBuilder();
    if (path.endsWith(File.separator)) {
      sb.append(path).append(fileName);
    } else {
      sb.append(path).append(File.separator).append(fileName);
    }
    return new File(sb.toString());
  }

  private static boolean equal(String s1, String s2) {
    if (s1 != null && s2 != null) {
      return s1.equals(s2);
    } else {
      return s1 == null && s2 == null;
    }
  }

  public static boolean getLogOutputPaths(MLog.LogOutputPaths out, String fileName) {
    String dir = LogToES.getLogPath();
    if (dir == null || fileName == null) {
      return false;
    }
    out.dir = dir;
    String current = null;
    synchronized (mLock) {
      current = currentName;
    }
    if (current == null) {
      current = createFile(dir, fileName).getAbsolutePath();
    }
    out.currentLogFile = current;

    // get latest.
    File folder = new File(dir);
    File[] files = folder.listFiles();
    if (files != null) {
      long maxBackupTime = 0;
      long tempTime;
      String dest = null;
      for (File e : files) {
        tempTime = getLogFileBackupTime(e);
        if (tempTime > maxBackupTime) {
          maxBackupTime = tempTime;
          dest = e.getAbsolutePath();
        }
      }
      out.latestBackupFile = dest;
    }

    return true;
  }

  /**
   * 获取日志备份创建时间，通过文件名判断，如果是备份文件就解析，解析不到就返回文件最后修改时间
   *
   * @param file file
   * @return 0 or backup millis or lastModified millis
   */
  private static long getLogFileBackupTime(File file) {
    if (file == null || !file.exists() || !isBakFile(file.getAbsolutePath())) {
      return 0;
    }
    long time = 0;
    try {
      String filename = file.getName();
      Matcher matcher = PATTERN.matcher(filename);
      if (matcher.find()) {
        String dateStr = filename.substring(matcher.start(), matcher.end());
        time = getSimpleDateFormat(BAK_DATE_FORMAT_STR).parse(dateStr).getTime();
        Log.i("LogToES", ".bak name:" + dateStr + ", time" + time + ", str:" + simpleDateFormat.format(time));
      } else {
        time = file.lastModified();
        Log.i("LogToES", ".bak find time format wrong, filename:" + filename + ", lastModified:" + time);
      }
    } catch (Throwable throwable) {
      Log.e("LogToES", "getLogFileBackupTime error" + throwable);
      time = file.lastModified();
      Log.i("LogToES", ".bak lastModified:" + time);
    }
    return time;
  }

  private static boolean isBakFile(String file) {
    return file.endsWith(BAK_EXT);
  }

  private static void limitVolume() {
    String dir = getLogPath();
    File dirFile = new File(dir);
    if (!dirFile.exists()) {
      return;
    }

    final File files[] = dirFile.listFiles();
    if (files == null || files.length <= Math.max(0, mBackFileNumLimit)) {
      return;
    }

    int numOfDeletable = 0;
    for (int i = 0, N = files.length; i < N; i++) {
      File file = files[i];
      if (isBakFile(file.getName())) {
        ++numOfDeletable;
      }
    }

    if (numOfDeletable <= 0) {
      // really weird, the naming rule have been changed!
      // this function won't work anymore.
      return;
    }

    // the logs.txt and uncaught_exception.txt may be missing,
    // so just allocate same size as the old.
    File[] deletables = new File[numOfDeletable];
    int i = 0;
    for (File e : files) {
      if (i >= numOfDeletable) {
        // unexpected case.
        break;
      }

      if (isBakFile(e.getName())) {
        deletables[i++] = e;
      }
    }

    deleteIfOutOfBound(deletables);
  }

  private static void deleteIfOutOfBound(File[] files) {
    if (files.length <= mBackFileNumLimit) {
      return;
    }

    // sort files by create time(time is on the file name) DESC.
    Comparator<? super File> comparator = new Comparator<File>() {

      @Override
      public int compare(File lhs, File rhs) {
        return rhs.getName().compareTo(lhs.getName());
      }

    };

    Arrays.sort(files, comparator);

    final int filesNum = files.length;

    // delete files from index to size.
    for (int i = mBackFileNumLimit; i < filesNum; ++i) {
      File file = files[i];
      if (!file.delete()) {
        // NOTE here we cannot call MLog, we are to be depended by MLog.
        Log.e("LogToES", "LogToES failed to delete file " + file);
      }
    }
  }

  public static void flush() {
    synchronized (mLock) {
      BufferedWriter writer = currentWriter;
      if (writer != null) {
        try {
          writer.flush();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
  }

  public static void close() {
    synchronized (mLock) {
      if (currentWriter != null) {
        try {
          currentWriter.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
      currentName = null;
      currentFile = null;
      currentWriter = null;
    }
  }

  public static boolean isOpen() {
    synchronized (mLock) {
      return currentWriter != null;
    }
  }

  public static Calendar getThreadCalendar() {
    return logCalendar.get();
  }

}
