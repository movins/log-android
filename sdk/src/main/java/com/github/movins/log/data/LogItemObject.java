package com.github.movins.log.data;

public final class LogItemObject {
  public final int kDwFileFlag = 0x00474F4C; // it's "LOG\0"
  public final int kDwFileVersion = 4;
  public final int kDwCookie = 0x19810202;

  public enum E_ITEM_TYPE {
    TYPE_E_CONTENT,
    TYPE_E_CONTENT2
  }

  public final class OLD_LOGBUFFER_INFO {
    int dwSize;
  }

  public final class LOG_FILE_HEAD {
    int dwFlag;
    int dwVer;
  }

  public final class LOG_CRYPT_BLOCK {
    int dwBlockSize;
    byte[] szData;
  }

  public final class LOG_ITEM_HEADER {
    int dwCookie;
    short wType;
    short wReserved;
  }

  public final class FlushRemainedLogContext {
    OLD_LOGBUFFER_INFO pInfo;
    Object log;
  }

  LOG_ITEM_HEADER header;
  int dwProcessId;
  int dwThreadId;
  long stTime; // local time
  short wLevel;
  short wLine;
  int dwUid;

  short cchFilter;
  short cchFunc;
  short cchCppName;
  short cchModule;
  short cchLog;

  byte[] szFilter;
  byte[] szFunc;
  byte[] szCppName;
  byte[] szModule;
  byte[] szLog;
}
