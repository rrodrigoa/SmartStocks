/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package stockanalysis;

/**
 *
 * @author rodrigoa
 */
import java.awt.BorderLayout;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;

import com.almworks.sqlite4java.SQLiteConnection;
import com.almworks.sqlite4java.SQLiteQueue;
import com.almworks.sqlite4java.SQLiteStatement;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Set;
import static stockanalysis.StockExchangeEnum.SWX;
import static stockanalysis.StockExchangeEnum.LON;
import static stockanalysis.StockExchangeEnum.NASDAQ;
import static stockanalysis.StockExchangeEnum.NYSE;

public class Controller {

    public Logger logger;
    private JPanel currentPanel;
    private JFrame currentFrame;
    private StockExchangeEnum stockExchange;
    private int stockExchangeNumber;
    private String NASDAQ_FILENAME = "./nasdaq_symbols.txt";
    private String NYSE_FILENAME = "./nyse_symbols.txt";
    private String LON_FILENAME = "./lon_symbols.txt";
    private String SWX_FILENAME = "./swx_symbols.txt";
    private String INDEXES_FILENAME = "./indexes_symbols.txt";
    private String DB_NAME = "./DataBase";
    private SQLiteConnection db;
    private String fileName = "";

    public Controller() {
        logger = Logger.getInstance();
    }

    // Start the stock exchange selection panel
    public void startSelectStockExchangePanel() {
        JFrame frame = new JFrame();
        frame.setTitle("SmartStocks");
        frame.setLayout(new BorderLayout());

        SelectStockExchangePanel selectPanel = new SelectStockExchangePanel();
        selectPanel.setController(this);

        frame.add(selectPanel, BorderLayout.CENTER);
        frame.pack();
        frame.setVisible(true);

        this.currentPanel = selectPanel;
        this.currentFrame = frame;
        this.currentFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }

    // Set the stock exchange to work on and open main frame
    public void setStockExchange(StockExchangeEnum stockExchange) {
        this.stockExchange = stockExchange;

        switch (this.stockExchange) {
            case NASDAQ:
                stockExchangeNumber = 1;
                this.fileName = this.NASDAQ_FILENAME;
                this.currentFrame.setTitle("NASDAQ SmartStocks");
                break;
            case NYSE:
                stockExchangeNumber = 2;
                this.fileName = this.NYSE_FILENAME;
                this.currentFrame.setTitle("NYSE SmartStocks");
                break;
            case LON:
                stockExchangeNumber = 3;
                this.fileName = this.LON_FILENAME;
                this.currentFrame.setTitle("LON SmartStocks");
                break;
            case SWX:
                stockExchangeNumber = 4;
                this.fileName = this.SWX_FILENAME;
                this.currentFrame.setTitle("SWX SmartStocks");
                break;
            case INDEXES:
                stockExchangeNumber = 5;
                this.fileName = this.INDEXES_FILENAME;
                this.currentFrame.setTitle("Indexes SmartStocks");
        }

        this.currentPanel.setVisible(false);

        StockAnalysisPanel stockPanel = new StockAnalysisPanel();

        this.currentFrame.remove(this.currentPanel);
        this.currentFrame.add(stockPanel, BorderLayout.CENTER);
        this.currentFrame.pack();
        this.currentFrame.setVisible(true);

        this.currentPanel = stockPanel;
        stockPanel.setController(this);

        Map<String, String> stockSymbols = null;

        // Load symbol list from DB
        stockSymbols = this.loadStockSymbolsFromDB();
        stockPanel.setStockSymbols(stockSymbols, stockExchange);
    }

    public void saveStockSymbolsToFile(Map<String, String> symbolsMap) {
        try {
            // With Map from file, save stock symbols on database on database
            File saveDBFile = new File(this.fileName);
            // If file exists, delete it and start a new one
            if (saveDBFile.exists()) {
                saveDBFile.delete();
            }

            // Create the file
            saveDBFile.createNewFile();

            // Create output stream to write information to file
            OutputStream output = new FileOutputStream(saveDBFile);
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(output, Charset.forName("UTF-8")));

            // Get the key set for symbols map
            Set<String> symbolSet = symbolsMap.keySet();
            String[] symbolArray = symbolSet.toArray(new String[symbolSet.size()]);
            // Write symbol by symbol on the file with stock name
            for (int index = 0; index < symbolArray.length; index++) {
                writer.write(symbolArray[index] + "\t" + symbolsMap.get(symbolArray[index]) + "\n");
            }

            // Close file
            writer.close();
            output.close();

        } catch (Exception e) {
            logger.logException(e);
        }
    }

    public void saveStockSymbolsToDB(Map<String, String> symbolsMap) {
        String command;
        try {
            command = "BEGIN TRANSACTION;";
            logger.logDB(command);
            db.exec(command);

            // Get all the symbols to be added on the database
            Set<String> symbolsSet = symbolsMap.keySet();
            String[] symbolsList = symbolsSet.toArray(new String[symbolsSet.size()]);

            for (int index = 0; index < symbolsList.length; index++) {
                command = "INSERT INTO Symbols(source, symbol, name) VALUES ("
                        + this.stockExchangeNumber + ",\"" + symbolsList[index] + "\",\""
                        + symbolsMap.get(symbolsList[index]) + "\");";

                logger.logDB(command);
                db.exec(command);
            }

            command = "END TRANSACTION;";
            logger.logDB(command);
            db.exec(command);

        } catch (Exception e) {
            logger.logException(e);
        }
    }

    // Connect to database, check if it exists if not, create a new one with tables ready to be populated
    public Boolean connectToDB() {
        try {
            Boolean needToPopulateDBFile = false;

            File dbFile = new File(DB_NAME);
            logger.log("opening database file " + DB_NAME);
            if (dbFile.exists() == false) {
                logger.log("database file not found, will create one");
                dbFile.createNewFile();
                logger.log("database file created");
                needToPopulateDBFile = true;
            }
            db = new SQLiteConnection(dbFile);
            db.open(true);

            // Pre-populate Database with tables if database is not there
            if (needToPopulateDBFile) {
                logger.log("need to populate new database file");
                // Begin transaction
                String command = "BEGIN TRANSACTION;";
                logger.logDB(command);
                db.exec(command);

                command = "CREATE TABLE InfoSource(id INTEGER PRIMARY KEY, source TEXT);";
                logger.logDB(command);
                db.exec(command);

                command = "INSERT INTO InfoSource(id, source) VALUES (1, \"NASDAQ\");";
                logger.logDB(command);
                db.exec(command);

                command = "INSERT INTO InfoSource(id, source) VALUES (2, \"NYSE\");";
                logger.logDB(command);
                db.exec(command);

                command = "INSERT INTO InfoSource(id, source) VALUES (3, \"LON\");";
                logger.logDB(command);
                db.exec(command);

                command = "INSERT INTO InfoSource(id, source) VALUES (4, \"SWX\");";
                logger.logDB(command);
                db.exec(command);

                command = "INSERT INTO InfoSource(id, source) VALUES (5, \"INDEXES\");";
                logger.logDB(command);
                db.exec(command);
                
                command = "CREATE TABLE Symbols(id INTEGER PRIMARY KEY, source, symbol TEXT, name TEXT, UNIQUE(source, symbol) ON CONFLICT IGNORE, FOREIGN KEY(source) REFERENCES InfoSource(id));";
                logger.logDB(command);
                db.exec(command);

                // Pre-populate the stock exchange symbols

                // NASDAQ
                Map<String, String> stockSymbols = this.loadStockSymbolsFromFile();
                this.saveStockSymbolsToDB(stockSymbols);

                command = "END TRANSACTION;";
                logger.logDB(command);
                db.exec(command);
            }

        } catch (Exception e) {
            logger.logException(e);
            logger.logError("returning false on connectToDB");
            return false;
        }
        logger.logSuccess("return true on connectToDB");
        return true;
    }
    
    public String getFileName(){
        return this.fileName;
    }

    public Map<String, String> loadStockSymbolsFromFile() {
        Map<String, String> stockSymbols = new HashMap<String, String>();
        try {
            logger.log("pre populating symbols");

            File nasdaqFile = new File(fileName);
            logger.log("opening file " + fileName);
            if (nasdaqFile.exists() == false) {
                logger.log(fileName + " not found");
                return null;
            } else {
                InputStream fis = new FileInputStream(nasdaqFile);
                BufferedReader reader = new BufferedReader(new InputStreamReader(fis, Charset.forName("UTF-8")));

                String line = null;
                while ((line = reader.readLine()) != null) {
                    String splitList[] = line.split("\t");
                    String name = "";
                    if(splitList.length == 2){
                        name = splitList[1];
                    }
                    stockSymbols.put(splitList[0].trim(), name);
                }

                reader.close();
                fis.close();
            }
        } catch (Exception e) {
            logger.logException(e);
        }

        return stockSymbols;
    }

    public Map<String, String> loadStockSymbolsFromDB() {
        Map<String, String> symbolsMap = new HashMap<String, String>();
        // Read symbols from database
        try {
            String command = "SELECT symbol,name FROM Symbols WHERE source=" + this.stockExchangeNumber + ";";
            String symbolStr;
            String nameStr;

            logger.logDB(command);
            SQLiteStatement st = db.prepare(command);
            while (st.step()) {
                symbolStr = st.columnString(0);
                nameStr = st.columnString(1);
                logger.log(symbolStr);
                symbolsMap.put(symbolStr, nameStr);
            }
        } catch (Exception e) {
            logger.logException(e);
        }
        return symbolsMap;
    }

    public void eraseStockSymbolsFromDB() {
        try {
            String command = "BEGIN TRANSACTION;";
            logger.logDB(command);
            db.exec(command);
            
            command = "DELETE FROM Symbols WHERE source=" + this.stockExchangeNumber + ";";
            logger.logDB(command);
            db.exec(command);
            
            command = "END TRANSACTION;";
            logger.logDB(command);
            db.exec(command);
            
        } catch (Exception e) {
            logger.logException(e);
        }
    }
}
