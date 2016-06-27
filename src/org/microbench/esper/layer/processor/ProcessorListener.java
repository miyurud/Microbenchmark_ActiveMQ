/**
 * 
 */
package org.microbench.esper.layer.processor;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.microbench.esper.input.Tuple;
import org.microbench.esper.layer.processor.ProcessorListener;

import com.espertech.esper.client.EPRuntime;

/**
 * @author miyuru
 *
 */
public class ProcessorListener implements MessageListener{
    private static Log log = LogFactory.getLog(ProcessorListener.class);
	private EPRuntime engine;
	private long currentTime = 0;
	private PrintWriter outLogger = null;
	//private PrintWriter outLogger2 = null;
	private int dataRate = 0;
	private int dataRateKBPSec = 0;
	private long expStartTime = 0;
	private int count = 0;
	
    private long tupleCounter = 0;
    private long byteCounter = 0;
    private int tupleCountingWindow = 5000;//This is in milliseconds
    private long previousTime = 0; //This is the time that the tuple measurement was taken previously
	
    private final String ENRON_DOMAIN = "@enron.com"; 
    private int listenerID;
    
    public ProcessorListener(EPRuntime engine, int lid)
    {
    	this.listenerID = lid;
        this.engine = engine;
        
		currentTime = System.currentTimeMillis();		
		try {
			outLogger = new PrintWriter(new BufferedWriter(new FileWriter("esper-microbench-processor-rate.csv", true)));
			//outLogger2 = new PrintWriter(new BufferedWriter(new FileWriter("filter-" + listenerID + ".txt", true)));
			outLogger.println("----- New Session -----<date-time>,<wall-clock-time(s)>,<physical-tuples-in-last-period>,<physical-tuples-data-rate>");
			outLogger.println("java-process-id:" + ManagementFactory.getRuntimeMXBean().getName());
			outLogger.println("Date,Wall clock time (s),TTuples,Data rate (tuples/s)");
		} catch (IOException e1) {
			e1.printStackTrace();
		}
    }
	
	/**
	 * 
	 */
	public ProcessorListener() {
	}

    public void onMessage(Message message)
    {
		//The following is the pure ActiveMQ way of doing this
        BytesMessage bytesMsg = (BytesMessage) message;
        byte[] body = getBody(bytesMsg);
        
        byteCounter+= body.length;
		tupleCounter += 1;
		currentTime = System.currentTimeMillis();
		
		if (previousTime == 0){
			previousTime = System.currentTimeMillis();
            expStartTime = previousTime;
		}
		
		if ((currentTime - previousTime) >= tupleCountingWindow){
			dataRate = Math.round((tupleCounter*1000)/(currentTime - previousTime));//need to multiply by thousand to compensate for ms time unit
			dataRateKBPSec = Math.round((byteCounter*1000)/1024*(currentTime - previousTime)); //Need to divide by 1024 to make it KB
			Date date = new Date(currentTime);
			DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS");
			outLogger.println(formatter.format(date) + "," + Math.round((currentTime - expStartTime)/1000) + "," + tupleCounter + "," + dataRate);
			outLogger.flush();
			tupleCounter = 0;
			previousTime = currentTime;
		}
		
        ByteArrayInputStream b = new ByteArrayInputStream(body);
        ObjectInputStream o;
		try {
			o = new ObjectInputStream(b);
	        Tuple object = (Tuple)o.readObject();
	        
         	engine.sendEvent(object);
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
    	
    	if(emails.contains(",")){//This is a list of eamil addresses
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

}
