/**************************************************************************************
 * Copyright (C) 2008 EsperTech, Inc. All rights reserved.                            *
 * http://esper.codehaus.org                                                          *
 * http://www.espertech.com                                                           *
 * ---------------------------------------------------------------------------------- *
 * The software in this package is published under the terms of the GPL license       *
 * a copy of which has been included with this distribution in the license.txt file.  *
 **************************************************************************************/
package org.microbench.esper.util;


public class Constants
{
    public static final String CONFIG_FILENAME = "servershell_config.properties";

    public static final String JMS_CONTEXT_FACTORY = "jms-context-factory";
    public static final String JMS_PROVIDER_URL_PROCESSOR_LAYER = "jms-provider-url-processor";
    public static final String JMS_PROVIDER_URL_OUTPUT_LAYER = "jms-provider-url-output";
    
    public static final String JMS_CONNECTION_FACTORY_NAME = "jms-connection-factory-name";
    public static final String JMS_USERNAME = "jms-user";
    public static final String JMS_PASSWORD = "jms-password";
    
    public static final String JMS_INCOMING_DESTINATION_PROCESSOR_LAYER = "jms-incoming-destination-processor";
    public static final String JMS_INCOMING_DESTINATION_OUTPUT_LAYER = "jms-incoming-destination-output";
    
    public static final String JMS_IS_TOPIC = "jms-is-topic";
    public static final String JMS_NUM_LISTENERS_PROCESSOR = "jms-num-listeners-processor";
    public static final String JMS_NUM_LISTENERS_OUTPUT = "jms-num-listeners-output";

    public static final String MGMT_RMI_PORT = "rmi-port";
    public static final String MGMT_RMI_PORT_PROCESSOR_LAYER = "rmi-port-processor";
    public static final String MGMT_RMI_PORT_OUTPUT_LAYER = "rmi-port-output";
    
    public static final String MGMT_SERVICE_URL = "jmx-service-url";
    public static final String MGMT_SERVICE_URL_PROCESSOR_LAYER = "jmx-service-url-processor";
    public static final String MGMT_SERVICE_URL_OUTPUT_LAYER = "jmx-service-url-output";
    public static final String MGMT_MBEAN_NAME = "com.espertech.esper.mbean:type=EPServiceProviderJMXMBean";
    
    public static final String EMAILS_FOLDER = "email-directory";
    public static final String PERSON_NAMES = "person-names";
    
    public static final String TUPLES_OUTPUT_PATH = "out-file-path";
    
    public static final String BIG_MEMORY_SIZE="big-memory-size";
    public static final String COMPONENT_SLEEP_TIME="comp-sleep-time";
    
    public static final String TUPLE_EMITTER_THREADS="number-of-tuple-emitter-threads";
}
