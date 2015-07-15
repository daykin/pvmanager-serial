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
import org.epics.vtype.*;

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
	* @param port serial port to use e.g. COM4 (Windows), dev/tty0(UN*X),
	* @param baudRate the baud rate (default 9600); 
	* recommended: 115200 or less
	* @param dataBits the number of bits per cycle, supports 5,6,7,or 8
	* @param parity type of parity bit, supports N,O,E,M,S
	* @param stopBits the number of stop bits, supports 1,2
	* 
	*/
	SerialChannelHandler(SerialDataSource datasource, String channelName,
			String port, int baudRate, int dataBits, 
			char parity, int stopBits)
		{			
		super(channelName);
		this.baud = baudRate;
		this.dataBits = dataBits;
		this.stopBits = stopBits;
		this.parity = parity;
		try 
			{
			com = CommPortIdentifier.getPortIdentifier(port);
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
		try 
		{
			serialPort.setSerialPortParams(baud, dataBits, stopBits, 
					parityMap.getOrDefault(parity, 0));
			serialPort.addEventListener(this);
			serialPort.notifyOnDataAvailable(true);
			os = serialPort.getOutputStream();
			input = new BufferedReader(new InputStreamReader
					(serialPort.getInputStream()));
			processConnection(serialPort);
		} 
		catch (UnsupportedCommOperationException | 
				TooManyListenersException | IOException e)
		{
			reportExceptionToAllReadersAndWriters(e);
			SerialDataSource.log.log(Level.WARNING,e.getMessage(),e);
		} 	
	}
	
	@Override
	protected boolean isWriteConnected(Object o){
		return true;
	}
	
	
	public static synchronized boolean isInteger(String s){
		try{
			Integer.parseInt(s);
			return true;
		}
		catch(NumberFormatException | NullPointerException e){
			return false;
		}
	}

	public static synchronized boolean isDouble(String s){
		try{
			Double.parseDouble(s);
			return true;
		}
		catch(NumberFormatException | NullPointerException e){
			return false;
		}
	}
	
	/**
	* Processes a serial message.
	*/
	public synchronized void update()
	{
		try {
			if(input.ready()){
				String value = readLineFromSerial();
				System.out.println(value);
				System.out.println(isInteger(value));
				if(isInteger(value)){
					int out = Integer.parseInt(value);
					processMessage(ValueFactory.newVInt(out,ValueFactory.alarmNone(), ValueFactory.timeNow(),ValueFactory.displayNone()));
				}
				else if(isDouble(value)){
					Double doubleOut = Double.parseDouble(value);
					processMessage(ValueFactory.newVDouble(doubleOut,ValueFactory.alarmNone(), ValueFactory.timeNow(),ValueFactory.displayNone()));
				}
				else{
					processMessage(ValueFactory.newVString(value, ValueFactory.alarmNone(), ValueFactory.timeNow()));
				}
			}
		} catch (IOException e) {
			ValueFactory.newVString("INVALID",ValueFactory.newAlarm(AlarmSeverity.INVALID,
					"Empty Response"),ValueFactory.timeNow());
		}
	}
	
	/**
	* retrieve data from serial inputStream
	* @return a line from the Serial input Stream
	*/
	protected synchronized String readLineFromSerial()
	{
		try {			
			return input.readLine();
		} 
		catch (IOException e) {			
			reportExceptionToAllReadersAndWriters(e);
			return "NaN";
		}		
	}

	/**
	* Close listener connection and underlying streams
	*/
	@Override
	protected synchronized void disconnect() 
	{
		try 
		{
			os.close();
			serialPort.close();
			serialPort.removeEventListener();

		} 
		catch (Exception e)
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
	* Event that is active whenever the serial output has data
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
