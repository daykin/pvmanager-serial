package org.csstudio.utility.pvmanager.serial;

import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;
import gnu.io.UnsupportedCommOperationException;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.TooManyListenersException;
import java.util.logging.Level;

import org.epics.pvmanager.ChannelWriteCallback;
import org.epics.pvmanager.MultiplexedChannelHandler;
import org.epics.vtype.VByte;
import org.epics.vtype.VDouble;
import org.epics.vtype.VFloat;
import org.epics.vtype.VInt;
import org.epics.vtype.VLong;
import org.epics.vtype.VShort;
import org.epics.vtype.VString;
import org.epics.vtype.VType;
import org.epics.vtype.ValueFactory;

/**
 * Handles logic and events for SerialDataSource
 * @author daykin
 */
public class SerialChannelHandler extends 
MultiplexedChannelHandler<Object,Object> implements SerialPortEventListener
{
	/**used to convert given parity char to its 
	 * respective numerical value accepted by RXTX*/
	public static final Map<Character,Integer> parityMap = 
			new HashMap<Character,Integer>()
	{	
		private static final long serialVersionUID = 1L;
		{
			put('n',0);
			put('o',1);
			put('e',2);
			put('m',3);
			put('s',4);
		};
	};
		
	public CommPortIdentifier com;
	protected int baud, dataBits, stopBits;
	protected char parity;
	
	public SerialPort serialPort;
	public BufferedReader input;
	public OutputStream os;

	
	/**
	* Sets parameters for serial connection. normally parsed from 
	* hierarchial uri parameter in {@link SerialDataSource}.createChannel()
	* @param datasource an instance of SerialDataSource
	* @param channelName the channel name
	* @param comPort serial port to use e.g. COM4 (Windows), dev/tty0(UN*X),
	* @param baudRate the baud rate (default 9600); 
	* recommended: 115200 or less
	* @param dataBits the number of bits per cycle, supports 5,6,7,or 8
	* @param parity type of parity bit, supports N,O,E,M,S
	* @param stopBits the number of stop bits, supports 1,2
	* 
	*/
	SerialChannelHandler(SerialDataSource datasource, String channelName,
			String comPort, int baudRate, int dataBits, 
			char parity, int stopBits)
		{			
		super(channelName);
		this.baud = baudRate;
		this.dataBits = dataBits;
		this.stopBits = stopBits;
		this.parity = parity;
		try 
			{
			com = CommPortIdentifier.getPortIdentifier(comPort);
			serialPort = (SerialPort) com.open(channelName,5000);
		
			}			
		catch (Exception e) 
			{
				reportExceptionToAllReadersAndWriters(e);
			}			
		}
	/**
	* Opens a serial connection.
	*/
	@Override
	protected synchronized void connect() 
	{
		processConnection(serialPort);		
		try 
		{
			serialPort.setSerialPortParams(baud, dataBits, stopBits, 
					parityMap.getOrDefault(this.parity,0));
			serialPort.addEventListener(this);
			serialPort.notifyOnDataAvailable(true);
			os = serialPort.getOutputStream();
			input = new BufferedReader(new InputStreamReader
					(serialPort.getInputStream()));	
		} 
		catch (UnsupportedCommOperationException | 
				TooManyListenersException | IOException e)
		{
			reportExceptionToAllReadersAndWriters(e);
			SerialDataSource.log.log(Level.WARNING,e.getMessage(),e);
		} 	
	}
	
	/**
	* Processes a serial message.
	*/
	public synchronized void update()
	{
		Object value = readLineFromSerial();
		processMessage(value);
	}
	
	/**
	* retrieve data from serial inputStream
	* @return a line from the Serial input Stream
	*/
	protected synchronized Object readLineFromSerial()
	{
		Object serialIn = "NaN";
		try
		{
			if(input.ready())
			{
			serialIn=input.readLine();
			}
		} 
		catch (IOException e) 
		{
			reportExceptionToAllReadersAndWriters(e);
		}
		return serialIn;
	}

	/**
	* Close listener connection and underlying streams
	*/
	@Override
	protected synchronized void disconnect() 
	{
		serialPort.close();
		serialPort.removeEventListener();
		try 
		{
			os.close();
			input.close();
		} 
		catch (IOException e)
		{
			reportExceptionToAllReadersAndWriters(e);
		}
	}

	/**
	* Send an object to the serial device
	* @param newValue an object for conversion to {@link VType}
	*/
	@Override
	protected synchronized void write(Object newValue, ChannelWriteCallback callback) 
	{
		if (!(newValue instanceof VType))
		{
			Object convertedValue = 
					ValueFactory.toVTypeChecked(newValue);
			if(convertedValue != null){
				newValue = convertedValue;
			}
		}
		DataOutputStream dataOut = new DataOutputStream(os);
		try
		{
			Thread.sleep(500);
			if(newValue instanceof VString)
			{			
				dataOut.writeUTF(((VString)newValue).getValue());
			}
			else if(newValue instanceof VDouble)
			{
				dataOut.writeDouble(((VDouble)newValue).getValue());
			}
			else if(newValue instanceof VFloat)
			{
				dataOut.writeFloat(((VFloat)newValue).getValue());
			}
			else if(newValue instanceof VLong)
			{
				dataOut.writeLong(((VLong)newValue).getValue());
			}
			else if(newValue instanceof VInt)
			{
				dataOut.writeDouble(((VInt)newValue).getValue());
			}
			else if(newValue instanceof VShort)
			{					
				dataOut.writeDouble(((VShort)newValue).getValue());
			}
			else if(newValue instanceof VByte)
			{
				dataOut.writeByte(((VByte)newValue).getValue());
			}
			else{
				throw new UnsupportedOperationException("Unsupported VType,"
						+ " must be instance of VNumber or VString");					
			}
			processMessage(newValue);
			callback.channelWritten(null);
		}
		catch(IOException | InterruptedException e)
		{
			e.printStackTrace();
			callback.channelWritten(e);
			reportExceptionToAllReadersAndWriters(e);
		}
	}
	
	/**
	* Event that is active whenever the serial output has data for us
	* Implements rxtx SerialPortEventListener
	* @param event- an event on the serial connection
	*/
	@Override
	public synchronized void serialEvent(SerialPortEvent event) {
				if (event.getEventType() == SerialPortEvent.DATA_AVAILABLE)
				{
					update();
				}		
			}
}
