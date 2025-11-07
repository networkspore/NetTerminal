package io.netnotes.utils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Execs {
    public static final ExecutorService VirtualExec  = Executors.newVirtualThreadPerTaskExecutor();
    public static final ExecutorService SchedualedVirtual  = Executors.newScheduledThreadPool(0, Thread.ofVirtual().factory());

    public static ExecutorService getVirtualExecutor(){
        return VirtualExec;
    }

     public static ExecutorService getSchedualedExecutor(){
        return SchedualedVirtual;
    }
}
