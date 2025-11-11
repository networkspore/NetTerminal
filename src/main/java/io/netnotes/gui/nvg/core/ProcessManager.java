package io.netnotes.gui.nvg.core;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.netnotes.engine.state.BitFlagStateMachine;
import io.netnotes.utils.Execs;


class ProcessManager {
    private final ProcessContainer container;
    private final BitFlagStateMachine stateMachine;
    
    private final Map<Integer, Process> processes = new ConcurrentHashMap<>();
    private final AtomicInteger nextProcessId = new AtomicInteger(1);
    
    private volatile Process foregroundProcess;
    private final List<Process> backgroundProcesses = new CopyOnWriteArrayList<>();
    
    private final ExecutorService executor = Execs.getVirtualExecutor();

    public ProcessManager(ProcessContainer container, BitFlagStateMachine stateMachine) {
        this.container = container;
        this.stateMachine = stateMachine;
    }
    
    public int startProcess(Process process) {
        int pid = nextProcessId.getAndIncrement();
        process.setPid(pid);
        processes.put(pid, process);
        
        // Set as foreground if nothing else is
        if (foregroundProcess == null) {
            setForegroundProcess(process);
        } else {
            backgroundProcesses.add(process);
        }
        
        // Execute in background thread
        executor.submit(() -> {
            try {
                process.execute();
            } catch (Exception e) {
                System.err.println("Process " + pid + " error: " + e.getMessage());
            } finally {
                process.complete();
                processCompleted(process);
            }
        });
        
        return pid;
    }
    
    public void setForegroundProcess(Process process) {
        if (foregroundProcess != null && foregroundProcess != process) {
            backgroundProcesses.add(foregroundProcess);
        }
        
        foregroundProcess = process;
    }
    
    public Process getForegroundProcess() {
        return foregroundProcess;
    }
    
    public boolean killProcess(int pid) {
        Process process = processes.remove(pid);
        if (process != null) {
            process.kill();
            
            if (process == foregroundProcess) {
                foregroundProcess = null;
                if (!backgroundProcesses.isEmpty()) {
                    setForegroundProcess(backgroundProcesses.remove(0));
                }
            } else {
                backgroundProcesses.remove(process);
            }
            
            return true;
        }
        return false;
    }
    
    private void processCompleted(Process process) {
        processes.remove(process.getPid());
        
        if (process == foregroundProcess) {
            foregroundProcess = null;
            if (!backgroundProcesses.isEmpty()) {
                setForegroundProcess(backgroundProcesses.remove(0));
            }
        } else {
            backgroundProcesses.remove(process);
        }
    }
    
    public void shutdownAll() {
        for (Process process : processes.values()) {
            process.kill();
        }
        processes.clear();
        backgroundProcesses.clear();
        foregroundProcess = null;
        
        executor.shutdownNow();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
