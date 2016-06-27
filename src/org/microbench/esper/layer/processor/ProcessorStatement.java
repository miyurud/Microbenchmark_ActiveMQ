/**
 * 
 */
package org.microbench.esper.layer.processor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

import javax.jms.BytesMessage;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.naming.NamingException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.microbench.esper.input.Tuple;
import org.microbench.esper.util.Constants;

import com.espertech.esper.client.EPAdministrator;
import com.espertech.esper.client.EPRuntime;
import com.espertech.esper.client.EPStatement;
import com.espertech.esper.client.EventBean;
import com.espertech.esper.client.UpdateListener;
import com.espertech.esper.event.map.MapEventBean;
import com.espertech.esper.event.bean.BeanEventBean;
import com.espertech.esper.example.servershell.jms.JMSContext;
import com.espertech.esper.example.servershell.jms.JMSContextFactory;

/**
 * @author miyuru
 *
 */
public class ProcessorStatement {
	private static Log log = LogFactory.getLog(ProcessorStatement.class);
    private EPRuntime engine;
    private int count;
    
	private JMSContext jmsCtx_output;
	private MessageProducer producer_output;
	
	private long expStartTime = 0;
	private long currentTime = 0;
    private int tupleCounter = 0;
    private int tupleCountingWindow = 5000;//This is in milliseconds
    private long previousTime = 0; //This is the time that the tuple measurement was taken previously
    
	public ProcessorStatement() {
        Properties properties = new Properties();
        InputStream propertiesIS = ProcessorListener.class.getClassLoader().getResourceAsStream(org.microbench.esper.util.Constants.CONFIG_FILENAME);
        if (propertiesIS == null)
        {
            throw new RuntimeException("Properties file '" + org.microbench.esper.util.Constants.CONFIG_FILENAME + "' not found in classpath");
        }

        try {
			properties.load(propertiesIS);
	
			String destination_modify = properties.getProperty(org.microbench.esper.util.Constants.JMS_INCOMING_DESTINATION_OUTPUT_LAYER);
			String jmsurl_modify = properties.getProperty(org.microbench.esper.util.Constants.JMS_PROVIDER_URL_OUTPUT_LAYER);
			String connFactoryName = properties.getProperty(org.microbench.esper.util.Constants.JMS_CONNECTION_FACTORY_NAME);
	        String user = properties.getProperty(org.microbench.esper.util.Constants.JMS_USERNAME);
	        String password = properties.getProperty(org.microbench.esper.util.Constants.JMS_PASSWORD);
	        boolean isTopic = Boolean.parseBoolean(properties.getProperty(org.microbench.esper.util.Constants.JMS_IS_TOPIC));
	        String factory = properties.getProperty(org.microbench.esper.util.Constants.JMS_CONTEXT_FACTORY);
			jmsCtx_output = JMSContextFactory.createContext(factory, jmsurl_modify, connFactoryName, user, password, destination_modify, isTopic);
			jmsCtx_output.getConnection().start();
			producer_output = jmsCtx_output.getSession().createProducer(jmsCtx_output.getDestination());
		} catch (IOException e) {
			e.printStackTrace();
		} catch (JMSException ec) {
			ec.printStackTrace();
		} catch (NamingException ex) {
			ex.printStackTrace();
		}
	}

    public void createStatement(EPAdministrator admin){
    	EPStatement statement = admin.createEPL("select * from Tuple");
    	
    	statement.addListener(new UpdateListener(){
    		@Override
    		public void update(EventBean[] newEvents, EventBean[] oldEvents){
    			BeanEventBean bean = (BeanEventBean)newEvents[0];
    			Tuple object = (Tuple)bean.getUnderlying();
    			
    			tupleCounter += 1;
    			
    			
    			//Here we just send a tuple for every predefined time window. We do not send tuples for every packet.
    			//Therefore, the output data rate will be very low.
    			currentTime = System.currentTimeMillis();
    			
    			if (previousTime == 0){
    				previousTime = System.currentTimeMillis();
    	            expStartTime = previousTime;
    			}
    			
    			if ((currentTime - previousTime) >= tupleCountingWindow){
    				sendToNextStage(new Integer(tupleCounter));
    				previousTime = currentTime;
    				tupleCounter = 0;
    			}
    		}   		
    	});
    }
    
    public void sendToNextStage(Integer tcounter){    	
    	BytesMessage bytesMessage = null;
		try{
		    bytesMessage = jmsCtx_output.getSession().createBytesMessage();

	        ByteArrayOutputStream b = new ByteArrayOutputStream();
	        ObjectOutputStream oos = new ObjectOutputStream(b);
	        oos.writeObject(tcounter);
	          
	        bytesMessage.writeBytes(b.toByteArray());
		    bytesMessage.setJMSDeliveryMode(DeliveryMode.NON_PERSISTENT);

		    producer_output.send(bytesMessage);
		}catch(JMSException e){
			e.printStackTrace();
		}catch(IOException e){
			e.printStackTrace();
		}
    }
	
}
