package stockanalysis;


import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.Map;
import java.util.Set;
import java.lang.StrictMath;

import org.rosuda.JRI.REXP;
import org.rosuda.JRI.RList;
import org.rosuda.JRI.RVector;
import org.rosuda.JRI.Rengine;

public class RController {
	private static RController instanceController = null;
	private static Rengine re = null;
	private static Logger logger = null;
	private static Boolean hasLoaded = false;
	
	// Constructor for new RController
	private RController(){
		logger = Logger.getInstance();
		if (!Rengine.versionCheck()) {
			logger.logError("** Version mismatch - Java files don't match library version.");
		}else{
			logger.log("Creating Rengine");
			this.re = new Rengine(new String[]{}, false, new TextConsole());
			logger.log("Rengine created, waiting for R");
			
			// Wait for REngine new thread to finish loading
			if (!re.waitForR()) {
				logger.logError("Cannot load R");
			}else{
				this.hasLoaded = true;
			}
		}
	}
	
	public Boolean HasLoaded(){
		return this.hasLoaded;
	}
	
	// Get instance for singleton REngine
	public static RController getInstance(){
		if(instanceController == null){
			instanceController = new RController();
		}
		
		return instanceController;
	}
	
	public StockInfo[] flatMapOfData(Map<String, StockInfo[]> dataToProcess){
		SimpleDateFormat dateFormat = new SimpleDateFormat();
		dateFormat.applyPattern("dd/MM/YYYY");
		
		ArrayList<StockInfo> listOfInfo = new ArrayList<StockInfo>();
		
		int numOfIndexesOutOfBound = 0;
		Set<String> keySetOfDataToProcess = dataToProcess.keySet();
		int[] indexOnStockInfo = new int[keySetOfDataToProcess.size()];
		for(int index = 0; index < indexOnStockInfo.length; index++){
			indexOnStockInfo[index] = 0;
		}
		
		// Get the key in an array
		String[] keys = keySetOfDataToProcess.toArray(new String[keySetOfDataToProcess.size()]);
		
		// Get the min start index to process, this index contains the min startTime
		long minStartIndexTime = Long.MAX_VALUE;
		
		for(int index = 0; index < keys.length; index++){
			long tempDate = dataToProcess.get(keys[index])[0].date.getTime();
			minStartIndexTime = minStartIndexTime < tempDate ?  minStartIndexTime : tempDate;
		}
		
		// While any index is still inside of its array boundaries, continue to flat array
		while(numOfIndexesOutOfBound !=  indexOnStockInfo.length){
			float openSum = 0;
			float highSum = 0;
			float lowSum = 0;
			float closeSum = 0;
			long volumeSum = 0;
			
			int count = 0;
			long newMinStartTime = 0;
			
			// For every key in the set of keys
			for(int index = 0; index < keys.length; index++){
				//logger.log("if indexOnStockInfo[index] " + indexOnStockInfo[index] + " < dataToProcess.get(keys[index]).length " + dataToProcess.get(keys[index]).length);
				if(indexOnStockInfo[index] < dataToProcess.get(keys[index]).length){
					StockInfo infoArray = dataToProcess.get(keys[index])[indexOnStockInfo[index]];
					long tempDate = infoArray.date.getTime();
					//logger.log("tempDate = " + dateFormat.format(tempDate));
					if(tempDate == minStartIndexTime){
						openSum += infoArray.open;
						highSum += infoArray.high;
						lowSum += infoArray.low;
						closeSum += infoArray.close;
						volumeSum += infoArray.volume;
						count ++;
						indexOnStockInfo[index] ++;
						newMinStartTime = tempDate;
					}
				}else{
					numOfIndexesOutOfBound++;
				}
			}
			
			minStartIndexTime = Long.MAX_VALUE;
			for(int index = 0; index < keys.length; index++){
				//logger.log("if indexOnStockInfo[index] " + indexOnStockInfo[index] + " < dataToProcess.get(keys[index]).length " + dataToProcess.get(keys[index]).length);
				if(indexOnStockInfo[index] < dataToProcess.get(keys[index]).length){
					long tempDate = dataToProcess.get(keys[index])[indexOnStockInfo[index]].date.getTime();
					minStartIndexTime = minStartIndexTime < tempDate ?  minStartIndexTime : tempDate;
					logger.log("minStartIndexTime = " + minStartIndexTime);
				}
			}
			
			//logger.log("count = " + count);
			
			if(count != 0){
				StockInfo newStockInfo = new StockInfo();
				newStockInfo.open = openSum / count;
				newStockInfo.high = highSum / count;
				newStockInfo.low = lowSum / count;
				newStockInfo.close = closeSum / count;
				newStockInfo.volume = volumeSum / count;
				newStockInfo.date = new Date(newMinStartTime);
				
				logger.log("flat date date is " + dateFormat.format(newStockInfo.date.getTime()));
				
				listOfInfo.add(newStockInfo);
			}else{
				logger.log("count is 0");
			}
		}
		
		return listOfInfo.toArray(new StockInfo[listOfInfo.size()]);
	}
	
	// Run prediction method on data based on file name
	public StockInfo[] runPrediction(Map<String, StockInfo[]> dataToProcess) {
		double[] resultArrayOpen = null;
		double[] resultArrayHigh = null;
		double[] resultArrayLow = null;
		double[] resultArrayClose = null;
		double[] resultArrayVolume = null;
		
		StockInfo[] predictedDataArray = null;
		
		try {
			logger.log("prediction will start");
			// Flat the data to perform prediction on it
			StockInfo[] flatStockInfo = this.flatMapOfData(dataToProcess);
                        Collections.reverse(Arrays.asList(flatStockInfo));
			
			REXP x;
			RVector v;

			// Check for installed packages
			x = re.eval("installed.packages()");
			v = x.asVector();
			String[] packages = x.asStringArray();
			boolean isForecastInstalled = false;
			logger.log("<R> getting installed packages");
			for (int index = 0; index < packages.length
					&& isForecastInstalled == false; index++) {
				logger.log("<R> has installed " + packages[index]);
				if (packages[index] != null && packages[index].compareTo("forecast") == 0) {
					isForecastInstalled = true;
				}
			}

			// If forecast needs to be installed
			if (isForecastInstalled == false) {
				logger.log("<R> will set repos");
				
				// Set CRAN
				re.eval("r <- getOption(\"repos\")");
				re.eval("r[\"CRAN\"] <- \"http://cran.us.r-project.org\"");
				re.eval("options(repos = r)");
				re.eval("rm(r)");

				// Install forecast
				re.eval("install.packages(\"forecast\")");
                                
				logger.log("<R> will install forecast package");
			}
			// Load forecast library
			re.eval("library(\"forecast\")");
			logger.log("<R> loaded forecast");
			
			// Make prediction for Open value -----------------------
			// Load data into R
			logger.log("<R> loading data into R");
			
			StringBuilder builder = new StringBuilder("inputData <- c(");
			for(int index = 0; index < flatStockInfo.length; index++){
				builder.append(flatStockInfo[index].open);
				if(index != flatStockInfo.length-1){
					builder.append(",");
				}else{
					builder.append(")");
				}
			}
			String stringFromBuilder = builder.toString();
			re.eval(stringFromBuilder);
			// Create time series from data
			logger.log("<R> forecasting open values BestFit");
			re.eval("temporalData <- ts(inputData, frequency=365)");
			// Forecast data
			re.eval("forecastData <- forecast(temporalData, h=30)");
                        //re.eval("arimaModel <- auto.arima(temporalData, max.p=5, max.q=5, max.P=5, max.Q=5)");
                        //re.eval("forecastData <- forecast(arimaModel, h=30)");
			x = re.eval("forecastData");
			v = x.asVector();
			x = (REXP) v.elementAt(1);//instead of 3
			resultArrayOpen = x.asDoubleArray();
			
			// Make prediction for High value ------------------------
			builder = new StringBuilder("inputData <- c(");
			for(int index = 0; index < flatStockInfo.length; index++){
				builder.append(flatStockInfo[index].high);
				if(index != flatStockInfo.length-1){
					builder.append(",");
				}else{
					builder.append(")");
				}
			}
			stringFromBuilder = builder.toString();
			re.eval(stringFromBuilder);
			// Create time series from data
			logger.log("<R> forecasting high values BestFit");
			re.eval("temporalData <- ts(inputData, frequency=365)");
			// Forecast data
			re.eval("forecastData <- forecast(temporalData, h=30)");
                        //re.eval("arimaModel <- auto.arima(temporalData, max.p=5, max.q=5, max.P=5, max.Q=5)");
                        //re.eval("forecastData <- forecast(arimaModel, h=30)");
			x = re.eval("forecastData");
			v = x.asVector();
			x = (REXP) v.elementAt(1);
			resultArrayHigh = x.asDoubleArray();
			
			
			// Make prediction for Low value ------------------------
			builder = new StringBuilder("inputData <- c(");
			for(int index = 0; index < flatStockInfo.length; index++){
				builder.append(flatStockInfo[index].low);
				if(index != flatStockInfo.length-1){
					builder.append(",");
				}else{
					builder.append(")");
				}
			}
			stringFromBuilder = builder.toString();
			re.eval(stringFromBuilder);
			// Create time series from data
			logger.log("<R> forecasting low values BestFit");
			re.eval("temporalData <- ts(inputData, frequency=365)");
			// Forecast data
			re.eval("forecastData <- forecast(temporalData, h=30)");
                        //re.eval("arimaModel <- auto.arima(temporalData, max.p=5, max.q=5, max.P=5, max.Q=5)");
                        //re.eval("forecastData <- forecast(arimaModel, h=30)");
			x = re.eval("forecastData");
			v = x.asVector();
			x = (REXP) v.elementAt(1);
			resultArrayLow = x.asDoubleArray();
			
			// Make prediction for Close value ------------------------
			builder = new StringBuilder("inputData <- c(");
			for(int index = 0; index < flatStockInfo.length; index++){
				builder.append(flatStockInfo[index].close);
				if(index != flatStockInfo.length-1){
					builder.append(",");
				}else{
					builder.append(")");
				}
			}
			stringFromBuilder = builder.toString();
			re.eval(stringFromBuilder);
			// Create time series from data
			logger.log("<R> forecasting close values BestFit");
			re.eval("temporalData <- ts(inputData, frequency=365)");
			// Forecast data
			re.eval("forecastData <- forecast(temporalData, h=30)");
                        //re.eval("arimaModel <- auto.arima(temporalData, max.p=5, max.q=5, max.P=5, max.Q=5)");
                        //re.eval("forecastData <- forecast(arimaModel, h=30)");
			x = re.eval("forecastData");
			v = x.asVector();
			x = (REXP) v.elementAt(1);
			resultArrayClose = x.asDoubleArray();
			
			
			// Make prediction for Close value ------------------------
			builder = new StringBuilder("inputData <- c(");
			for(int index = 0; index < flatStockInfo.length; index++){
				builder.append(flatStockInfo[index].volume);
				if(index != flatStockInfo.length-1){
					builder.append(",");
				}else{
					builder.append(")");
				}
			}
			stringFromBuilder = builder.toString();
			re.eval(stringFromBuilder);
			// Create time series from data
			re.eval("temporalData <- ts(inputData, frequency=365)");
			// Forecast data
			re.eval("forecastData <- forecast(temporalData, h=30)");
                        //re.eval("arimaModel <- auto.arima(temporalData, max.p=5, max.q=5, max.P=5, max.Q=5)");
                        //re.eval("forecastData <- forecast(arimaModel, h=30)");
			x = re.eval("forecastData");
			v = x.asVector();
			x = (REXP) v.elementAt(1);
			resultArrayVolume = x.asDoubleArray();
			
			// Create a single StockInfo[] for all data
			StockInfo predictedData;
			predictedDataArray = new StockInfo[30];
			
			Date lastDate = flatStockInfo[flatStockInfo.length-1].date;
			Calendar c = Calendar.getInstance();
			c.setTime(lastDate);
			c.add(Calendar.DATE, 1);
			
			logger.log("<R> values for forecasted data");
			
			SimpleDateFormat dateFormat = new SimpleDateFormat();
			dateFormat.applyPattern("dd/MM/YYYY");
			
			// For all days that were predicted
			for(int index = 0; index < 30; index++){
				predictedData = new StockInfo();
				predictedData.open = (float)resultArrayOpen[index];
                                
                                float maxHigh = (float)StrictMath.max(resultArrayClose[index], StrictMath.max(resultArrayHigh[index], resultArrayOpen[index]));
                                predictedData.high = maxHigh;
                                
                                float minLow = (float)StrictMath.min(resultArrayClose[index], StrictMath.min(resultArrayLow[index], resultArrayOpen[index]));
				predictedData.low = minLow;
				
                                predictedData.close = (float)resultArrayClose[index];
				predictedData.volume = (int)resultArrayVolume[index];
				
				while(c.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY || c.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY){
					c.add(Calendar.DATE, 1);
				}
				predictedData.date = c.getTime();
				
				predictedDataArray[index] = predictedData;
				
				logger.log("<R> stock prediction " + dateFormat.format(predictedData.date.getTime()) + " open: " + predictedData.open + " high: " + predictedData.high + " low: " + predictedData.low + " close: " + predictedData.close);
				c.add(Calendar.DATE, 1);
			}
			
		} catch (Exception e) {
			logger.logException(e);
		}
		
		return predictedDataArray;
	}

}
