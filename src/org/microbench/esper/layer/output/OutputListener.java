/**
 * 
 */
package org.microbench.esper.layer.output;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.EnumSet;
import java.util.Properties;
import java.util.zip.GZIPOutputStream;

import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.naming.NamingException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.microbench.esper.layer.processor.ProcessorListener;

import com.espertech.esper.client.EPRuntime;
import com.espertech.esper.example.servershell.jms.JMSContextFactory;

/**
 * @author miyuru
 *
 */
public class OutputListener implements MessageListener{
    private static Log log = LogFactory.getLog(OutputListener.class);
	private EPRuntime engine;
	private long currentTime = 0;
	private PrintWriter outLogger = null;
	private int dataRate = 0;
	private long expStartTime = 0;
	private int count = 0;
	
    private long tupleCounter = 0;
    private int tupleCountingWindow = 5000;//This is in milliseconds
    private long previousTime = 0; //This is the time that the tuple measurement was taken previously
	
    private final String ENRON_DOMAIN = "@enron.com"; 
    private FileSystem fs;
    private ObjectOutputStream objectOut;
    private ByteArrayOutputStream baos;
    private FileChannel outChannel;
    private Path fp;
	String outFileString; // Valid file pathname
    
    public OutputListener(EPRuntime engine)
    {
        this.engine = engine;
        
		currentTime = System.currentTimeMillis();		

		try {
	        Properties properties = new Properties();
	        InputStream propertiesIS = OutputListener.class.getClassLoader().getResourceAsStream(org.microbench.esper.util.Constants.CONFIG_FILENAME);
	        if (propertiesIS == null)
	        {
	            throw new RuntimeException("Properties file '" + org.microbench.esper.util.Constants.CONFIG_FILENAME + "' not found in classpath");
	        }
			properties.load(propertiesIS);
		
			outFileString = properties.getProperty(org.microbench.esper.util.Constants.TUPLES_OUTPUT_PATH);
			
			outLogger = new PrintWriter(new BufferedWriter(new FileWriter("esper-microbench-output-rate.csv", true)));
            outLogger.println("----- New Session -----<date-time>,<wall-clock-time(s)>,<physical-tuples-in-last-period>,<physical-tuples-data-rate>");
            outLogger.println("java-process-id:" + ManagementFactory.getRuntimeMXBean().getName());
			outLogger.println("Date,Wall clock time (s),TTuples,Data rate (tuples/s)");
					
			File fileTemp = new File(outFileString);
			
			if(fileTemp.exists()){
				fileTemp.delete();
			}	
			
			fileTemp.createNewFile();
		} catch (IOException e) {
			e.printStackTrace();
		}    	
    }
	
	/**
	 * 
	 */
	public OutputListener() {
	}

    public void onMessage(Message message)
    {
		tupleCounter += 1;
		currentTime = System.currentTimeMillis();
		
		if (previousTime == 0){
			previousTime = System.currentTimeMillis();
            expStartTime = previousTime;
		}
		
		if ((currentTime - previousTime) >= tupleCountingWindow){
			dataRate = Math.round((tupleCounter*1000)/(currentTime - previousTime));//need to multiply by thousand to compensate for ms time unit
			Date date = new Date(currentTime);
			DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS");
			outLogger.println(formatter.format(date) + "," + Math.round((currentTime - expStartTime)/1000) + "," + tupleCounter + "," + dataRate);
			outLogger.flush();
			tupleCounter = 0;
			previousTime = currentTime;
		}
    	
        BytesMessage bytesMsg = (BytesMessage) message;
        byte[] body = getBody(bytesMsg);
        
        ByteArrayInputStream b = new ByteArrayInputStream(body);
        ObjectInputStream o;
		try {
			o = new ObjectInputStream(b);
	        Integer intObj = (Integer)o.readObject();
	                
	        persist(intObj);
	        
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}

        count++;
    }
    
    /**
     * This method essentially checks if the email address(es) belong to Enron or not.
     * @param emails
     * @return
     */
    public boolean isEnron(CharSequence ems){
    	if(ems == null){
    		return true; //If the field do not have any email address, then we return "true" to support the cases where all the emailing addresses are from Enron
    	}
    	
    	String emails = ems.toString();
    	
    	if(emails.contains(",")){//This is a list of email addresses
    		String[] strArr = emails.split(",");
    		
    		for(String email : strArr){
    			if (!email.contains(ENRON_DOMAIN)){
    				return false;
    			}
    		}
    		//If we passed the above barrier that means all the email addresses are from Enron
    		return true;
    	}else{
    		return emails.contains(ENRON_DOMAIN);
    	}
    }
    
    public int getCount()
    {
        return count;
    }
    
    private byte[] getBody(BytesMessage bytesMsg)
    {
        try
        {
            long length = bytesMsg.getBodyLength();
            byte[] buf = new byte[(int)length];
            bytesMsg.readBytes(buf);

            return buf;
        }
        catch (JMSException e)
        {
            String text = "Error getting message body";
            log.error(text, e);
            throw new RuntimeException(text, e);
        }
    }
    
    public void persist(Integer intObject){
    	try{	
	    	baos = new ByteArrayOutputStream();
	    	GZIPOutputStream gzipOut = new GZIPOutputStream(baos);
	    	objectOut = new ObjectOutputStream(gzipOut);
    		
	    	objectOut.writeObject(intObject);
	    	objectOut.close();
	    	byte[] bytes = baos.toByteArray();
	    	
	    	Files.write(Paths.get(outFileString), bytes, StandardOpenOption.APPEND);
    	}catch(IOException e){
    		e.printStackTrace();
    	}
    }
}
