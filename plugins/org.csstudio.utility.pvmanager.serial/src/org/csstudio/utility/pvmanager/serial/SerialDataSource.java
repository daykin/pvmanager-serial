package org.csstudio.utility.pvmanager.serial;


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
	
	public static Map<String,String> parseArgs(String channelName) 
		{
		Map<String,String> parameters = new HashMap<String,String>();
		
		if (channelName.endsWith("/")){
			channelName = channelName.substring(0, channelName.length()-1);
		}
		parameters.put("serialPort", channelName.split("\\?")[0]);
		if(!(channelName.endsWith("?"))&&channelName.contains("?")){   //if a non-null query is supplied
		String query = channelName.split("\\?")[1];
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
		String config = "8n1";
		String com = "";
		final String allowedParity = "neoms";
		char parity = 'n';
		HashMap<String,String> params = new HashMap<String,String>();
		try {
			params.putAll(parseArgs(channelName));	
		} 
		catch (Exception e) {
			e.printStackTrace();
			log.log(Level.WARNING,e.getMessage(),e);
		}
		com = params.get("serialPort");
		baud = Integer.parseInt(params.getOrDefault("baud","9600"));		
		dataBits = Integer.parseInt(params.getOrDefault("databits","8"));	
		parity =  Character.toLowerCase(params.getOrDefault("parity", "n").charAt(0));
		stopBits = Integer.parseInt(params.getOrDefault("stopbits","1"));
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
