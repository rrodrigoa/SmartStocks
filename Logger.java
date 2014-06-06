/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package stockanalysis;

/**
 *
 * @author rodrigoa
 */
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;

public class Logger implements AutoCloseable {

    private static Logger loggerInstance = null;
    private static File loggerFile = null;
    private static String loggerFileLocation = "./logFile.txt";
    private static FileWriter fw = null;
    private static BufferedWriter bw = null;
    private static Boolean isInitialized = false;
    private static Boolean isDebug = false;

    protected Logger() {
        try {
            // Check to see if debug file is necessary
            File debugOnFile = new File("./debug.xml");
            if(debugOnFile.exists() == true){
                // Debug file exists, so save info in the log
                isDebug = true;
                
                loggerFile = new File(loggerFileLocation);
                if (loggerFile.exists() == false) {
                    loggerFile.createNewFile();
                }

                fw = new FileWriter(loggerFile.getAbsoluteFile(), true);
                bw = new BufferedWriter(fw);
            }else{
                isDebug = false;
            }


        } catch (IOException e) {
            isInitialized = false;
            e.printStackTrace();
        }
        isInitialized = true;
    }

    public static Logger getInstance() {
        if (loggerInstance == null) {
            loggerInstance = new Logger();
        }
        return loggerInstance;
    }

    public Boolean isInitialized() {
        return isInitialized;
    }

    public void log(String message) {
        Date date = new Date();
        try {
            String logMessage = "log - " + date.toString() + " - " + message + "\r\n";
            if(isDebug){
                bw.write(logMessage);
            }
            System.out.println(logMessage);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void logError(String errorMessage) {
        Date date = new Date();
        try {
            if(isDebug){
                bw.write("error - " + date.toString() + " - " + errorMessage + "\r\n");
                bw.flush();
            }
            
            System.out.println(errorMessage);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void logDB(String command) {
        Date date = new Date();
        try {
            String message = "DB_COMMAND - " + date.toString() + " - " + command + "\r\n";
            if(isDebug){
                bw.write(message);
                bw.flush();
            }
            
            System.out.println(message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void logSuccess(String successMessage) {
        Date date = new Date();
        try {
            if(isDebug){
                bw.write("success - " + date.toString() + " - " + successMessage + "\r\n");
                bw.flush();
            }
            System.out.println(successMessage);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void logException(Exception e) {
        Date date = new Date();
        try {
            String message = "EXCEPTION - " + date.toString() + " - " + e.toString() + "\r\n";
            if(isDebug){
                bw.write(message);
            }
            System.out.println(message);
            StackTraceElement[] elements = e.getStackTrace();
            for (int i = 0; i < elements.length; i++) {
                if(isDebug){
                    bw.write("\t" + elements[i].toString() + "\r\n");
                }
                message = "\t" + elements[i].toString() + "\r\n";
                System.out.println(message);
            }
            if(isDebug){
                bw.flush();
            }
        } catch (IOException exc) {
            exc.printStackTrace();
        }
    }

    public void close() {
        try {
            if(isDebug){
                bw.write("CLOSING");
                bw.flush();
                fw.flush();
                bw.close();
                fw.close();
            }
            System.out.println("CLOSING");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
