package io.netnotes;

import io.netnotes.app.console.ConsoleApplication;
import io.netnotes.engine.utils.LoggingHelpers.Log;

public class Main {

     public static void main(String[] args) {
        ConsoleApplication app = new ConsoleApplication();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            app.shutdown();
        }));
    
        
        try {
            app.start().join();
        } catch (Exception e) {
            Log.logError("[MAIN]", e);
            e.printStackTrace();
        } 
    }

    
    
}


