package io.netnotes.logging;

import java.io.File;

import com.google.gson.JsonObject;

import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.utils.LoggingHelpers;
import io.netnotes.gui.fx.display.FxResourceFactory;

public class Log {
    public static File logFile = FxResourceFactory.LOG_FILE;

    public static void write(String scope, String msg){
        LoggingHelpers.writeLogMsg(logFile, scope, msg);
    }

    public static void writeError(String scope, Throwable throwable){
        LoggingHelpers.writeLogMsg(logFile, scope, throwable);
    }

    public static void writeJson(String scope, JsonObject json){
        LoggingHelpers.logJson(logFile, scope, json);
    }

    public static void writeNoteBytesObject(String scope, NoteBytesObject noteBytes){
        LoggingHelpers.writeLogNoteByteObject(logFile, noteBytes);
    }
}
