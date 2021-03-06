package com.cloudcomputing.samza.pitt_cabs;

import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.io.IOException;
import java.util.HashMap;

import org.apache.samza.config.Config;
import org.apache.samza.storage.kv.Entry;
import org.apache.samza.storage.kv.KeyValueIterator;
import org.apache.samza.storage.kv.KeyValueStore;
import org.apache.samza.system.IncomingMessageEnvelope;
import org.apache.samza.system.OutgoingMessageEnvelope;
import org.apache.samza.task.InitableTask;
import org.apache.samza.task.MessageCollector;
import org.apache.samza.task.StreamTask;
import org.apache.samza.task.TaskContext;
import org.apache.samza.task.TaskCoordinator;
import org.apache.samza.task.WindowableTask;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;

import com.cloudcomputing.samza.pitt_cabs.DriverMatchConfig;

/**
 * Consumes the stream of driver location updates and rider cab requests.
 * Outputs a stream which joins these 2 streams and gives a stream of rider
 * to driver matches.
 * @author Adithya Balasubramanian
 */
public class DriverMatchTask implements StreamTask, InitableTask, WindowableTask {

  /* Define per task state here. (kv stores etc) */
	private KeyValueStore<String, Map<String, Object>> driverLocation;


  @Override
  @SuppressWarnings("unchecked")
  public void init(Config config, TaskContext context) throws Exception {
	//Initialize stuff (maybe the kv stores?)
	  driverLocation = (KeyValueStore<String, Map<String, Object>>) context.getStore("driver-loc");
	  
  }

  @Override
  @SuppressWarnings("unchecked")
  public void process(IncomingMessageEnvelope envelope, MessageCollector collector, TaskCoordinator coordinator) {
	// All the messages for a particular partition
	// come here (somewhat like MapReduce). Messages for a blockId will arrive
	// at one task only, thereby enabling you to do stateful stream processing.
	  //get JSON message by calling getMessage();
	  String incomingStream = envelope.getSystemStreamPartition().getStream();
	  
	  HashMap<String, Object> message = new HashMap<String, Object>();
	  message = (HashMap<String, Object>)envelope.getMessage();
	
	  if (message != null){
		  if (incomingStream.equals(DriverMatchConfig.DRIVER_LOC_STREAM.getStream())){
			  String driverId = String.valueOf(message.get("driverId"));  //unique identifier of the driver
			  if(message!=null)
			  driverLocation.put(driverId, message);
			 
		  } else if(incomingStream!=null && incomingStream.equals(DriverMatchConfig.EVENT_STREAM.getStream())){
			  String blockId = String.valueOf(message.get("blockId"));   //the block where the driver is currently moving.
			    //unique identifier of the driver
			  String type = String.valueOf(message.get("type"));  //
			  String latitude = String.valueOf(message.get("latitude")); //lat-long of the driver
			  String longitude = String.valueOf(message.get("longitude"));
			  String status = String.valueOf(message.get("status"));
			  	  
			  if(type!=null && type.equals("ENTERING_BLOCK") && status.equals("AVAILABLE")){
				  String driverId = String.valueOf(message.get("driverId"));
				  driverLocation.put(driverId, message);
			  }else if(type!=null && (type.equals("LEAVING_BLOCK") || type.equals("UNAVAILABLE"))){
				  String driverId = String.valueOf(message.get("driverId"));
				  driverLocation.delete(driverId);
			  }else if(type!=null && type.equalsIgnoreCase("RIDE_REQUEST")){
				  String riderID = String.valueOf(message.get("riderId"));
				  KeyValueIterator<String, Map<String, Object>> iter = driverLocation.all();
				  double minDistance = Double.MAX_VALUE;
				  double distance;
				  String nearestDriverID = new String();
				  while(iter.hasNext()){
					 Entry<String, Map<String, Object>> loc = iter.next();
					  if(loc!=null){
				      //String driverId = String.valueOf(message.get("driverId"));
					  Map<String,Object> value = (Map<String, Object>)loc.getValue();
					  String currentDriverLat = String.valueOf(value.get("latitude"));
					  String currentDriverLong = String.valueOf(value.get("longitude"));
					  String currentDriverBlockid = String.valueOf(value.get("blockId"));
					  String currentDriverID = String.valueOf(value.get("driverId"));
					  distance = Math.sqrt(Math.pow((Double.parseDouble(currentDriverLat)-Double.parseDouble(latitude)), 2) + 
							  Math.pow((Double.parseDouble(currentDriverLong)-Double.parseDouble(longitude)), 2) );
					  if(currentDriverBlockid.equals(blockId)){
								minDistance = Math.min(minDistance, distance);
								if(minDistance == distance){
									nearestDriverID = currentDriverID;
							}
					  }
					  }
				  }
				  //remove the driver who is closest
				  driverLocation.delete(nearestDriverID); 
				  message.clear();
				  message.put("riderId", riderID);
				  message.put("driverId", nearestDriverID);
				  message.put("priceFactor", 1.0+"");
				  collector.send(new OutgoingMessageEnvelope(DriverMatchConfig.MATCH_STREAM, message));
			  }else if(type!=null && type.equals("RIDE_COMPLETE")){
				  String driverId = String.valueOf(message.get("driverId"));
				  driverLocation.put(driverId, message);
			  }
		  }  
	  }
  }


  @Override
  public void window(MessageCollector collector, TaskCoordinator coordinator) {
	//this function is called at regular intervals, not required for this project
  }
}

