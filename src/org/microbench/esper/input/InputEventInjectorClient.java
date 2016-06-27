/**
 * 
 */
package org.microbench.esper.input;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.FileWriter;
import java.io.BufferedWriter;
import java.lang.management.ManagementFactory;
import java.nio.charset.Charset;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.rmi.registry.LocateRegistry;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.Properties;

import javax.management.MBeanServerConnection;
import javax.management.MBeanServerInvocationHandler;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import javax.jms.BytesMessage;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.MessageProducer;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.RegexFileFilter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.microbench.esper.input.InputEventInjectorClient;
import org.microbench.esper.util.Constants;
import org.microbench.esper.util.Utilities;

import com.espertech.esper.example.servershell.jms.JMSContext;
import com.espertech.esper.example.servershell.jms.JMSContextFactory;
import com.espertech.esper.example.servershell.jmx.EPServiceProviderJMXMBean;


/**
 * @author miyuru
 *
 */
public class InputEventInjectorClient {
    private static Log log = LogFactory.getLog(InputEventInjectorClient.class);
    
    private JMSContext jmsCtx_filter;
    
    private MessageProducer producer_filter;
    private long tupleCounter = 0;
    
    private int tupleCountingWindow = 5000;//This is in miliseconds
    private long previousTime = 0; //This is the time that the tuple measurement was taken previously
    private long expStartTime = 0; 
    
	private long currentTime = -1;
	private int dataRate = 0;
	private PrintWriter outLogger;
	
	private long sleepTime = 0;
	private int bigObjectSize = 0;
    //Here we create the new object
    private Tuple object = new Tuple(bigObjectSize);
    
    private int numTupleEmitterThreads = 0;
    
	/**
	 * 
	 */
	public InputEventInjectorClient() {
		
	}

	public void run() throws Exception{
		outLogger = new PrintWriter(new BufferedWriter(new FileWriter("esper-microbench-input-injector-rate.csv", true)));
		
        outLogger.println("----- New Session -----<date-time>,<wall-clock-time(s)>,<physical-tuples-in-last-period>,<physical-tuples-data-rate>");
        outLogger.println("java-process-id:" + ManagementFactory.getRuntimeMXBean().getName());
		outLogger.println("Date,Wall clock time (s),TTuples,Data rate (tuples/s)");
		
		Properties properties = new Properties();
		InputStream propertiesIS = InputEventInjectorClient.class.getClassLoader().getResourceAsStream(org.microbench.esper.util.Constants.CONFIG_FILENAME);
		
        if (propertiesIS == null)
        {
            throw new RuntimeException("Properties file '" + org.microbench.esper.util.Constants.CONFIG_FILENAME + "' not found in classpath");
        }
        properties.load(propertiesIS);
		
        // Start RMI registry
        log.info("Starting RMI registry");
        int port = Integer.parseInt(properties.getProperty(Constants.MGMT_RMI_PORT));
        LocateRegistry.createRegistry(port);
        
        //Sleep time
        sleepTime = Long.parseLong(properties.getProperty(Constants.COMPONENT_SLEEP_TIME));
        bigObjectSize = Integer.parseInt(properties.getProperty(Constants.BIG_MEMORY_SIZE));
        
        //Attached via JMX to running server
        log.info("Attach to server via JMX");
        JMXServiceURL url = new JMXServiceURL(properties.getProperty(org.microbench.esper.util.Constants.MGMT_SERVICE_URL_PROCESSOR_LAYER));
        JMXConnector jmxc = JMXConnectorFactory.connect(url, null);
        MBeanServerConnection mbsc = jmxc.getMBeanServerConnection();
        ObjectName mBeanName = new ObjectName(org.microbench.esper.util.Constants.MGMT_MBEAN_NAME);
        EPServiceProviderJMXMBean proxy = (EPServiceProviderJMXMBean) MBeanServerInvocationHandler.newProxyInstance(
                     mbsc, mBeanName, EPServiceProviderJMXMBean.class, true);

        // Connect to JMS
        log.info("Connecting to JMS server");
        String factory = properties.getProperty(org.microbench.esper.util.Constants.JMS_CONTEXT_FACTORY);
        String connFactoryName = properties.getProperty(org.microbench.esper.util.Constants.JMS_CONNECTION_FACTORY_NAME);
        String user = properties.getProperty(org.microbench.esper.util.Constants.JMS_USERNAME);
        String password = properties.getProperty(org.microbench.esper.util.Constants.JMS_PASSWORD);
        String destination_filter = properties.getProperty(org.microbench.esper.util.Constants.JMS_INCOMING_DESTINATION_PROCESSOR_LAYER);
        boolean isTopic = Boolean.parseBoolean(properties.getProperty(org.microbench.esper.util.Constants.JMS_IS_TOPIC));
        
        String jmsurl_filter = properties.getProperty(org.microbench.esper.util.Constants.JMS_PROVIDER_URL_PROCESSOR_LAYER);
        jmsCtx_filter = JMSContextFactory.createContext(factory, jmsurl_filter, connFactoryName, user, password, destination_filter, isTopic);

        numTupleEmitterThreads = Integer.parseInt(properties.getProperty(org.microbench.esper.util.Constants.TUPLE_EMITTER_THREADS));
        
        // Get producer
        jmsCtx_filter.getConnection().start();
        producer_filter = jmsCtx_filter.getSession().createProducer(jmsCtx_filter.getDestination());
        
        //---- Next we start pumping the emails
        emitTuples();
        
		System.out.println(Utilities.getTimeStamp() + ": Done running Email processing");       

        log.info("Exiting");
        System.exit(-1);
	}
	
	public void emitTuples() {				
		//Next we serialize the object and send it through wire
		BytesMessage bytesMessage = null;
		
		while(true){
			try{
		          bytesMessage = jmsCtx_filter.getSession().createBytesMessage();
		          
		          ByteArrayOutputStream b = new ByteArrayOutputStream();
		          ObjectOutputStream oos = new ObjectOutputStream(b);
		          
		          //We are serializing and injecting the same object again and again.
		          oos.writeObject(object);
		          
		          bytesMessage.writeBytes(b.toByteArray());
		          bytesMessage.setJMSDeliveryMode(DeliveryMode.NON_PERSISTENT);
		          producer_filter.send(bytesMessage);
		          
				  tupleCounter += 1;
				  
					currentTime = System.currentTimeMillis();
					
					if (previousTime == 0){
						previousTime = System.currentTimeMillis();
			            expStartTime = previousTime;
					}
							
					if ((currentTime - previousTime) >= tupleCountingWindow){
						dataRate = Math.round((tupleCounter*1000)/(currentTime - previousTime));//Need to multiple by thousand because the time is in ms
						Date date = new Date(currentTime);
						DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS");
						outLogger.println(formatter.format(date) + "," + Math.round((currentTime - expStartTime)/1000) + "," + tupleCounter + "," + dataRate);
						outLogger.flush();
			            tupleCounter = 0;
			            previousTime = currentTime;
					}
			} catch (JMSException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}

			//We can avoid sleeping by setting a negative value here.
			if(sleepTime > 0){
				try {
					Thread.currentThread().sleep(sleepTime);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try{
			InputEventInjectorClient client = new InputEventInjectorClient();
			client.run();
		}catch(Exception e){
			e.printStackTrace();
		}
	}
}
