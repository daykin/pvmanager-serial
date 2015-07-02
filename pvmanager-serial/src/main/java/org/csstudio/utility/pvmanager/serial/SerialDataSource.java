package org.csstudio.utility.pvmanager.serial;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.epics.pvmanager.ChannelHandler;
import org.epics.pvmanager.DataSource;

import com.google.common.base.Splitter;

/**
 * Extends {@link DataSource}to support serial devices
 * @author daykin
 */
public class SerialDataSource extends DataSource {
	public static Logger log = Logger.getLogger(SerialChannelHandler.class
			.getName());	
	
	public SerialDataSource() {
		super(true);
	}
	
	public static Map<String,String> parseHierarchialURI(String uri) 
			throws URISyntaxException
		{
		Map<String,String> parameters = new HashMap<String,String>();
		URI host = new URI(uri);
		String serialPort = host.getHost()+host.getPath();
		if (serialPort.endsWith("/")){
			serialPort = serialPort.substring(0, serialPort.length()-1);
		}
		parameters.put("serialPort", serialPort);	
		if(host.getQuery()!=null && !(uri.endsWith("?"))){   //if a non-null query is supplied
		String query = uri.split("\\?")[1];
		parameters.putAll(Splitter.on('&').trimResults().
				withKeyValueSeparator("=").split(query));
		}
		return parameters;

	}
	
	/**
	 * parses the given channel name URI
	 * and creates a {@link SerialChannelHandler}
	 * @param channelName
	 */	
	@Override
	protected ChannelHandler createChannel(String channelName)
	{
		int baud = 9600, dataBits=8, stopBits=1;      
		String config = "8n1",com=null;
		final String allowedParity = "neoms";
		char parity = 'n';
		HashMap<String,String> params = new HashMap<String,String>();
		try {
			params.putAll(parseHierarchialURI(channelName));	
		} 
		catch (Exception e) {
			e.printStackTrace();
			log.log(Level.WARNING,e.getMessage(),e);
		}
		com = params.get("serialPort");			
		baud = Integer.parseInt(params.getOrDefault("baud","9600"));		
		config = params.getOrDefault("config","8n1");
		config.toLowerCase();
		dataBits = Character.getNumericValue(config.charAt(0));
		parity =  Character.toLowerCase(config.charAt(1));
		if(allowedParity.indexOf(parity)==-1){ //no parity if invalid spec
			parity = 'n';
		}
		stopBits = Character.getNumericValue(config.charAt(2));
		if(!(stopBits==1 | stopBits==2)){      //1 stop bit if invalid spec
			stopBits = 1;
		}
		return new SerialChannelHandler(this, channelName, com, baud, dataBits, 
				parity, stopBits);		
	}
	
	
}
