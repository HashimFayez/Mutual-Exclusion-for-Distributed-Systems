import java.util.*;
import java.net.*;
import java.nio.*;
import com.sun.nio.sctp.*;
import java.io.*;
import java.lang.Object;
import java.util.Calendar;
import java.util.Date;

class Pair 
{
    String nodeNumber;
    String nodeName;
    SctpChannel TargetChannelvalue;
    String totalNumberOfMessagesToSend;
  
    public Pair(String nodeNumber,String nodeName,SctpChannel TargetChannel, String totalNumberOfMessagesToSend)
    {
    this.nodeNumber=nodeNumber;
    this.nodeName=nodeName;
    this.TargetChannelvalue=TargetChannel;
    this.totalNumberOfMessagesToSend = totalNumberOfMessagesToSend;
    }
}

class ProcessingObject{
	int nodeID;
	int sequenceNumber;
	boolean inquireMessageSent;			//true if sent
	boolean inquireMessageReceived ;	//true if received!
	boolean isFullfilled;				//true if fulfilled!
	//counters
	int requests, locked_failed, inquire, relinquish, release;
	
	long startTime, endTime;
	
	public ProcessingObject(int nodeID,int sequenceNumber, boolean inquireMessageSent, boolean inquireMessageReceived, boolean isFullfilled){
		this.nodeID=nodeID;
		this.sequenceNumber=sequenceNumber;
		this.inquireMessageSent = inquireMessageSent;
		this.inquireMessageReceived = inquireMessageReceived;
		this.isFullfilled = isFullfilled;
		this.requests = this.locked_failed = this.inquire = this.relinquish = this.release = 0;
		this.startTime = this.endTime = 0;
	}
	
	public void changeCounterValue(String counterType){
		//System.out.println("Incrementing the counter value for : "+this.nodeID+ " for request : "+counterType);
		if(counterType.equalsIgnoreCase("REQUEST")){
			this.requests=this.requests+1;
		}
		if(counterType.equalsIgnoreCase("LOCKED") || counterType.equalsIgnoreCase("FAILED")){
			this.locked_failed=this.locked_failed+1;
		}
		if(counterType.equalsIgnoreCase("INQUIRE")){
			this.inquire=this.inquire+1;
		}
		if(counterType.equalsIgnoreCase("RELINQUISH")){
			this.relinquish=this.relinquish+1;
		}
		if(counterType.equalsIgnoreCase("RELEASE")){
			this.release=this.release+1;
		}
	}

	public void setRequestStatus(boolean newStatus){
		this.isFullfilled = newStatus;
	}
	
	public void setStartEndTimeOfRequest(String timeType){
		Calendar lCDateTime = Calendar.getInstance();
		long currentTimeStamp = lCDateTime.getTimeInMillis();
		if(timeType.equalsIgnoreCase("START")){
			startTime=currentTimeStamp;
		}
		else{
			endTime = currentTimeStamp;
		}
	}
}

class QuorumRequestSet{
	int nodeID;
	String requestStatus;
	
	public QuorumRequestSet(int nodeID,String requestStatus){
		this.nodeID = nodeID;
		this.requestStatus = requestStatus;
	}
	
	public String getquorumRequestStatus(int nodeID){
		if(this.nodeID==nodeID){
			return this.requestStatus;
		}
		return null;
	}
	
	public void setquorumRequestStatus(int nodeID, String status){
		if(this.nodeID==nodeID){
			this.requestStatus=status;
		}
	}
}


@SuppressWarnings("serial")
class MessageStructure implements Serializable  ///include MessageId
{
	static int MAX_BUF_SIZE=1000;
	int sequenceNumber;
	String Msg_type;
	int Destination,Source;
	//Counters
	//int locked_failed, inquire, relinquish, release;
	
	public MessageStructure(int sequencenumber,String Msg_type,int Source,int Destination)
		{
		 this.sequenceNumber=sequencenumber;
		 this.Msg_type=Msg_type; 
		 this.Destination=Destination;
		 this.Source=Source;
		}

	public static byte[] serialize(Object obj) throws IOException {
	    ByteArrayOutputStream out = new ByteArrayOutputStream();
	    ObjectOutputStream os = new ObjectOutputStream(out);
	    os.writeObject(obj);
	    return out.toByteArray();
	}
	
	public static Object deserialize(ByteBuffer parabyteBuffer) throws IOException, ClassNotFoundException {
		parabyteBuffer.position(0);
		parabyteBuffer.limit(MAX_BUF_SIZE);
		byte[] bufArr = new byte[parabyteBuffer.remaining()];
		parabyteBuffer.get(bufArr);
		
		ByteArrayInputStream in = new ByteArrayInputStream(bufArr);
	    ObjectInputStream is = new ObjectInputStream(in);
	    return is.readObject();
	}
}


public class Project2 extends Thread{
	int MAX_BUF_SIZE=1000;
	int mynodeId;
	int ServerPort;
	int sequenceNumber;
	ArrayList<Pair> IPAddressNodeMap;
	ArrayList <String> quorumMembers;
	ArrayList <Pair> quorumConnections;
	int totalNodeCount;
	SctpServerChannel ss;
	ArrayList<SctpChannel> Clients;
	SctpChannel client;
	SocketAddress TargetServerSocketAddress;
	ArrayList<ProcessingObject> Received_Request_Q;
	boolean AmILocked; //false if free else true
	ProcessingObject currentProcessingNode;
	ArrayList<ProcessingObject> waitQueue;
	ArrayList<QuorumRequestSet> quorumReqSet;
	
	//This list will store the list of nodes who has sent Inquire messages along with the sequenceNumber!
	ArrayList<int[]> inquireMessageQueue;
	
	//{nodeID, noOfMessagesToBeExpected}
	ArrayList<int[]> quorumMembersRequest;	
	
	public Project2(int nodeId)
	{
		this.mynodeId=nodeId;
		/*if(this.mynodeId == 0){
			this.sequenceNumber=10;
		}
		else 		
			if(this.mynodeId == 2){
				this.sequenceNumber=20;
		}
		else*/
		this.sequenceNumber=0;
		this.quorumConnections=new ArrayList <Pair>();
		this.Received_Request_Q=new ArrayList<ProcessingObject>();
		this.waitQueue = new ArrayList<ProcessingObject>();
		this.quorumReqSet = new ArrayList<QuorumRequestSet>();
		
		this.inquireMessageQueue = new ArrayList<int[]>();
		this.quorumMembersRequest = new ArrayList<int[]>();
		
		//Initialize self with unlocked status
		this.AmILocked=false;
		this.currentProcessingNode = new ProcessingObject(-1,-1, false, false, false);
		this.create_message_counts_file();
	}
	
	public synchronized int totalNoOfCSReq(int nodeID){
		return Integer.parseInt(IPAddressNodeMap.get(nodeID).totalNumberOfMessagesToSend);
	}
	
	public synchronized int numberOfCSReqRemained(int nodeID){
		int loopVariable=0;
		while(loopVariable < quorumMembersRequest.size()){
			if(quorumMembersRequest.get(loopVariable)[0] == nodeID){
				//Found the nodeID
				return quorumMembersRequest.get(loopVariable)[1];
			}
			loopVariable++;
		}
		return 0;
	}
	
	public void printquorumMembersRequest(){
		int loopVariable = 0;
		while(loopVariable < quorumMembersRequest.size()){
			int[] tmpquorumMembersRequest = quorumMembersRequest.get(loopVariable);
			System.out.println("NodeID : "+tmpquorumMembersRequest[0]+", NoOfMessages: "+tmpquorumMembersRequest[1]);
			loopVariable++;
		}
		
	}
	
	public synchronized void changeMessageCounterOfMemberReqSet(int nodeID){
		int loopVariable=0;
		while(loopVariable < quorumMembersRequest.size()){
			if(quorumMembersRequest.get(loopVariable)[0] == nodeID){
				//Found the nodeID
				quorumMembersRequest.get(loopVariable)[1]--;
			}
			loopVariable++;
		}
	}
	
	public synchronized void addNewQuorumMemberReqSet(int nodeID){
		//Look for that node into existing array
		boolean isPresent=false;
		int loopVariable=0;
		while(loopVariable < quorumMembersRequest.size()){
			if(quorumMembersRequest.get(loopVariable)[0] == nodeID){
				isPresent = true;
				break;
			}
			loopVariable++;
		}
		if(!isPresent){
			//Add it to quorumMembersRequest
			int [] newMemberReqSet = {nodeID, 
									  Integer.parseInt(IPAddressNodeMap.get(nodeID).totalNumberOfMessagesToSend)};
			quorumMembersRequest.add(newMemberReqSet);
			}
		}
			
	public synchronized boolean areAllReqMessagesReceived(){
		int loopVariable=0;
		boolean answer = true;
		while(loopVariable < quorumMembersRequest.size()){
			if(quorumMembersRequest.get(loopVariable)[1] > 0){
				answer =false;
				return answer;
			}
			loopVariable++;
		}
		return answer;	
	}
	
	public void Use_Critical_Section(int nodeid,int Seq_number, long startTime, long endTime)
    {
        String file_name = "Critical_Section.txt";    
		File fout=new File("./",file_name);
            try{if (!fout.exists())        if(fout.createNewFile()) System.out.println("file Created");else System.out.println("problem creating file");}catch(IOException ioe){ioe.printStackTrace();System.out.println("Problem creating file");}
            Writer writer;
           // Calendar cal=Calendar.getInstance();
            //Write ENTER
            try{
            FileOutputStream FoutStream=new FileOutputStream(file_name, true);
              try{
                                    writer = new BufferedWriter(new OutputStreamWriter(FoutStream, "UTF-8"));
                                    writer.append(nodeid+","+Seq_number+","+"ENTER"+","+startTime);
                                    writer.append("\n");
                                    writer.close();        
            }catch(IOException ioe){ioe.printStackTrace();}finally {FoutStream.close();}
            }catch(Exception e){}
      //Release file
            try { Thread.sleep(1000);} catch (InterruptedException ie) {}
      //Write EXIT
            try{
            		changeTimeStampOfRequest("END");
            		Calendar lCDateTime = Calendar.getInstance();
            		long currentTimeStamp = lCDateTime.getTimeInMillis();
            		FileOutputStream FoutStream=new FileOutputStream(file_name, true);
                      try{
                                            writer = new BufferedWriter(new OutputStreamWriter(FoutStream, "UTF-8"));
                                            writer.append(nodeid+","+Seq_number+","+"EXIT"+","+currentTimeStamp);
                                            writer.append("\n");
                                            writer.close();        
                    }catch(Exception ioe){ioe.printStackTrace();}finally {FoutStream.close();}
                    }catch(Exception e){}
      //Release File
    }
	
	public void create_message_counts_file()
	{
		String file_name = "Message_Counts_Node_"+this.mynodeId;
		Writer writer;
		File fout=new File("./",file_name);
		System.out.println("Creating file:"+fout.getPath());
		try{if (!fout.exists())	if(fout.createNewFile()) System.out.println("file Created");else System.out.println("problem creating file");}catch(IOException ioe){ioe.printStackTrace();System.out.println("Problem creating file");}
		try{
			FileOutputStream FoutStream=new FileOutputStream(file_name, true);
			java.nio.channels.FileLock lock = FoutStream.getChannel().lock();
			  try{
					synchronized(this)
					{
						writer = new BufferedWriter(new OutputStreamWriter(FoutStream, "UTF-8"));
						//writer.append("Sourceid,Seqno,Req_count,Lock_failed_count,Inquire_count,Relinquish_count,Release_count");
						//writer.append("\n");
						writer.close();	
					}
		        }catch(IOException ioe){ioe.printStackTrace();}finally {lock.release();FoutStream.close();}
			}catch(Exception e){}
	}
	
	public void print_message_counts_to_file(int Sourceid,int Seqno,int Req_count,int Lock_failed_count,int Inquire_count,int Relinquish_count,int Release_count)
	{
		String file_name = "./Message_Counts_Node_"+this.mynodeId;
		Writer writer;
		try{
		FileOutputStream FoutStream=new FileOutputStream(file_name, true);
		java.nio.channels.FileLock lock = FoutStream.getChannel().lock();
		  try{
				synchronized(this)
				{
					writer = new BufferedWriter(new OutputStreamWriter(FoutStream, "UTF-8"));
					writer.append(Sourceid+","+Seqno+","+Req_count+","+Lock_failed_count+","+Inquire_count+","+Relinquish_count+","+Release_count);
					writer.append("\n");
					writer.close();	
				}
	        }catch(IOException ioe){ioe.printStackTrace();}finally {lock.release();FoutStream.close();}
		}catch(Exception e){}
	}
	
	public void writeNodeStatus(String newMessage){
		String file_name = "Node_"+this.mynodeId;
		Writer writer;	
		//File fout=new File("./",file_name);
		//System.out.println("Creating file:"+fout.getPath());
		//try{if (!fout.exists())	if(fout.createNewFile()) System.out.println("file Created");else System.out.println("problem creating file");}catch(IOException ioe){ioe.printStackTrace();System.out.println("Problem creating file");}
		
		try{
		FileOutputStream FoutStream=new FileOutputStream(file_name, true);
		
		java.nio.channels.FileLock lock = FoutStream.getChannel().lock();
		  try{
				synchronized(this)
				{
					writer = new BufferedWriter(new OutputStreamWriter(FoutStream, "UTF-8"));
					writer.append(newMessage);
					writer.append("\n");
					writer.close();	
				}
	        }catch(IOException ioe){ioe.printStackTrace();}finally {lock.release();FoutStream.close();}
		}catch(Exception e){}
	}
	
	public static String Get_IPV4(Set<SocketAddress> Address_List)//OK
	{
		
		SocketAddress [] IPList=null;
		IPList=Address_List.toArray(new SocketAddress[0]);
		String IPV4_REGEX = "/\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d{1,5}";
		for(SocketAddress t : IPList)
		{
			if(t.toString().matches(IPV4_REGEX) & !t.toString().contains("127.0.0.1"))
			return t.toString();	
		}
		return "IPV4 not found";
	}
	
	public void Read_Config()
	{
		//read config.txt
		BufferedReader br = null;
		Pair tmpMap;
		String sCurrentLine;
		totalNodeCount=0;
		IPAddressNodeMap=new ArrayList<Pair>();
		String[] Map;
		String file_name = "./config.txt";
		
		try {
			br = new BufferedReader(new FileReader(file_name));
		    while ((sCurrentLine = br.readLine()) != null) 
			 {Map=sCurrentLine.split(" ");
			  tmpMap=new Pair(Map[0],Map[1],null, Map[2]);
			  //IPAddressNodeMap.add(totalNodeCount, tmpMap);
			  IPAddressNodeMap.add(totalNodeCount,tmpMap);
			  totalNodeCount++;
			 }
		    
		    ServerPort=Integer.parseInt(IPAddressNodeMap.get(mynodeId).nodeName.split(":")[1]);
		    addNewQuorumMemberReqSet(mynodeId);
			}catch (Exception e) {e.printStackTrace();}  finally {	try {if (br != null)br.close();} catch (IOException ex) {ex.printStackTrace();}}
		
		//for (Pair p : IPAddressNodeMap)
			//System.out.println("Node name:"+p.nodeName + " Node id:"+p.nodeNumber);
    }
		
	public void Read_quorum_config()
	{
		BufferedReader br = null;
		String sCurrentLine;
		String []RowSplit=null;
		int myrow=-1,mycolumn=-1;
		int CurrentRow;
		@SuppressWarnings("unused")
		String shape=null;
		String rowCriteria=null,columnCriteria=null;
		quorumMembers=new ArrayList<String>();
		String file_name = "./quorumconfig.txt";
		try {
			br = new BufferedReader(new FileReader(file_name));
		    while ((sCurrentLine = br.readLine()) != null) 
			 { 
		    	if (!sCurrentLine.contains("#"))
		    	{
		    		myrow++;
				    RowSplit=sCurrentLine.split(" ");
				    for (int i=0;i<RowSplit.length;i++){if (RowSplit[i].contains(Integer.toString(this.mynodeId))) {mycolumn=i;break;}}
				    if(mycolumn!=-1) break;
		    	}
			 }
			}catch (Exception e) {System.out.println("Error in first catch!");e.printStackTrace();}  finally {	try {if (br != null)br.close();} catch (IOException ex) {ex.printStackTrace();}}
	
		try {
			br = new BufferedReader(new FileReader(file_name));
			//br = new BufferedReader(new FileReader(System.getProperty("user.home")+"/AOS/Project2Files/quorumconfig"));
			CurrentRow=0;
		    while ((sCurrentLine = br.readLine()) != null) 
			 { 
		    	if (sCurrentLine.contains("#"))
		    	{
		    		if(sCurrentLine.contains("STRUCTURE")) shape=sCurrentLine.split(":")[1];
		    		if(sCurrentLine.contains("ROW")) rowCriteria=sCurrentLine.split(":")[1];
		    		if(sCurrentLine.contains("COLUMN")) columnCriteria=sCurrentLine.split(":")[1];
		    	}
		    	else
		    	{
		    		String [] tmpSplit=sCurrentLine.split(" ");
		    		if(CurrentRow==myrow)
		    		{
		    			if(rowCriteria.equals("COMPLETE")){ quorumMembers.addAll(Arrays.asList(tmpSplit)); }
		    			if(rowCriteria.equals("LHS")){ quorumMembers.addAll(Arrays.asList(Arrays.copyOfRange(tmpSplit, 0, mycolumn+1))); }
		    			if(rowCriteria.equals("RHS")){ quorumMembers.addAll(Arrays.asList(Arrays.copyOfRange(tmpSplit, mycolumn,tmpSplit.length)));}
		    		}
		    		if(columnCriteria.equals("COMPLETE")){if((tmpSplit.length-1)>=mycolumn & CurrentRow!=myrow) {quorumMembers.add(tmpSplit[mycolumn]);}}
		    		if(columnCriteria.equals("UP")){if(((tmpSplit.length-1)>=mycolumn) & myrow>CurrentRow) {quorumMembers.add(tmpSplit[mycolumn]);}}
		    		if(columnCriteria.equals("DOWN")){if(((tmpSplit.length-1)>=mycolumn) & myrow<CurrentRow) {quorumMembers.add(tmpSplit[mycolumn]);}}
		    		
		    		CurrentRow++;
		    	}
			 }
		    }catch (Exception e) {System.out.println("Error in second catch!");e.printStackTrace();}  finally {	try {if (br != null)br.close();} catch (IOException ex) {ex.printStackTrace();}}
	
		//System.out.println("myrow:"+myrow);
		//System.out.println("mycolumn:"+mycolumn);
		for (String s : quorumMembers) {
			//System.out.println(s);
			quorumReqSet.add(new QuorumRequestSet(Integer.parseInt(s),"NULL"));
			//int[] newReqSet = {Integer.parseInt(s), Integer.parseInt(IPAddressNodeMap.get(Integer.parseInt(s)).totalNumberOfMessagesToSend)};
			//quorumMembersRequest.add(newReqSet);
		}
	}
	
	public Pair Get_IP_from_map(String Address)
	{
		for (Pair p : IPAddressNodeMap)
		{
			if (Address.split(":")[0].contains(p.nodeName.split(":")[0]))
				return p;
		}
		return null;
	}
	
	public synchronized void addPairtoQuorumConnections(Pair newPair){
		//if(!Is_connected(newPair))quorumConnections.add(newPair);
		quorumConnections.add(newPair);
	}

	public void StartServer() // ok 
	{
		
		boolean receive = true;
		Clients=new ArrayList<SctpChannel>();
		System.out.println("Server ID: " +this.mynodeId);
		try
		{
			ss=SctpServerChannel.open();
			ss.bind(new InetSocketAddress(this.ServerPort));	
			System.out.println("Server for Node "+this.mynodeId+ " started and listening on Port :"+Get_IPV4(ss.getAllLocalAddresses()));
			
		  while(receive)
		  {
			client=ss.accept();
			client.configureBlocking(false);
			Clients.add(client);
			////Add in qourumConnections
			Pair tmpPair=Get_IP_from_map(Get_IPV4(client.getRemoteAddresses()));
			if (tmpPair!=null)
			{
			Pair addPair=new Pair(tmpPair.nodeNumber,tmpPair.nodeName,client,"NULL");
			addPairtoQuorumConnections(addPair);
			System.out.println("In startServer, Size of quorumConnections: "+quorumConnections.size());
			System.out.println("Server "+Get_IPV4(client.getAllLocalAddresses())+" is connected to client:"+Get_IPV4(client.getRemoteAddresses())+" node id:"+tmpPair.nodeNumber);
			}
			else
			{System.out.println("Problem in retrieving from Map");}
			/*Thread.sleep(1000);
			if(areAllReqMessagesReceived()){
				System.out.println("--------------------------------------------------------------------------");
				System.out.println("| Killing Server for Node "+this.mynodeId+" since all the request have been fulfilled! |");
				System.out.println("--------------------------------------------------------------------------");
				receive = false;
				break;
			}*/			
		  }
		} catch (Exception e) {System.out.println("Exception in startServer!");e.printStackTrace();} //catch(InterruptedException ie) {System.out.println("Exception due to sleep");}
	}
	
	public synchronized boolean Is_connected(int nodeID)
	{
		int loopVariable=0;
		while(loopVariable < getSizeOFQuorumConnections()){
			if(Integer.parseInt(quorumConnections.get(loopVariable).nodeNumber)== nodeID){
					return true;
			}
			loopVariable++;
		}
		return false;
		/*Pair tmpMyPair=null;
	synchronized(quorumConnections.getClass())
	{
		try{
		for(Pair p :quorumConnections)
			if(p.nodeNumber.equals(somePair.nodeNumber) && Integer.parseInt(somePair.nodeNumber)!= this.mynodeId)
				return true;
		
		
		if(somePair.nodeNumber.equals(String.valueOf(this.mynodeId)))
		{for(Pair p :quorumConnections)
			if(Integer.parseInt(p.nodeNumber)==this.mynodeId)
			{tmpMyPair=p; break;}
		
			if(Get_IPV4(tmpMyPair.TargetChannelvalue.getRemoteAddresses()).equals(Get_IPV4(somePair.TargetChannelvalue.getRemoteAddresses())))
				return true;
		}
		}catch(Exception e){}	
		return false;
		
	}*/
	}
	
	public void Connect_to_quorumMembers(int mynodeNumber)
	{
		String ServerIPAddr=null;
		int ServerPortNum=-1;
		Pair tmpConnectionPair=null;
   
		//// Connect to quorum members and store connections				
		 //System.out.println("In Connect to quorummembers function");
	
		 for(String s:quorumMembers)
		 {
		     
			 try { Thread.sleep(2000);} catch (InterruptedException ie) {}
			 int currentNodeId=Integer.parseInt(s);
			 Pair tmpPair=IPAddressNodeMap.get(currentNodeId);
			 ServerIPAddr=tmpPair.nodeName.split(":")[0];
			 ServerPortNum=Integer.parseInt(tmpPair.nodeName.split(":")[1]);
			 if(!Is_connected(currentNodeId) && this.mynodeId!= currentNodeId){
				 
				 TargetServerSocketAddress=new InetSocketAddress(ServerIPAddr,ServerPortNum);
				 int attempts=0;
				 try{
					 client = SctpChannel.open();
					 while(!client.connect(TargetServerSocketAddress))
					 {
						 attempts++;
						 if(attempts>5)
						 {
							 System.out.println("Could not connect to "+TargetServerSocketAddress.toString() +" after "+attempts+" attempts");
							 return;
						 }
					 }
					 if(attempts<=5) 
					 {
		        	//System.out.println("Client "+Get_IPV4(client.getAllLocalAddresses())+" is connected to server:"+Get_IPV4(client.getRemoteAddresses())+" Node id:"+currentNodeId);
						 
						 System.out.println("Adding "+currentNodeId+" to quorumMembers!");
						 tmpConnectionPair=new Pair(tmpPair.nodeNumber,tmpPair.nodeName,client,"NULL");
						 //synchronized(quorumConnections.getClass()){quorumConnections.add(tmpConnectionPair);}
						 addPairtoQuorumConnections(tmpConnectionPair);
					 }
				 }
				 catch(IOException ioe){}
			 }
		 }
	}
	
	public boolean Check_intersection()
	{
		BufferedReader reader;
		String line,myQuorum;
		ArrayList<String> AllQuorums=new ArrayList<String>();
	    int CohortQuorums=0;
	    String file_name = "checkQuorum.txt";
		try{
			FileInputStream FinStream=new FileInputStream(file_name);
			//FileInputStream FinStream=new FileInputStream(System.getProperty("user.home")+"/AOS/Project2Files/CheckQuorum");
			java.nio.channels.FileLock lock = FinStream.getChannel().tryLock(0L, Long.MAX_VALUE, true);
			reader=new BufferedReader(new InputStreamReader(FinStream, "UTF-8"));
			//for every pair of quorums there should be atleast 1 node common.	
			try{
			while((line=reader.readLine())!=null)
				{
					if(line.split("-")[0].equals(this.mynodeId)) myQuorum=line;
					else AllQuorums.add(line);		  
				}
			System.out.println(this.mynodeId+" is in following quorum(s):");
			for(String d : AllQuorums)
				{
				   if(d.split("-")[1].contains(Integer.toString(this.mynodeId)))
				   {CohortQuorums++;
				   String f=d.split("-")[0];
				   System.out.println(" "+f);
				   }
				}
			}catch(Exception e){}finally {lock.release();FinStream.close();}
			}catch(Exception e){e.printStackTrace();}
			if (CohortQuorums>0) return true; return false;
	}

	public void print_quorum_connection_to_file()
	{
		String file_name = "checkQuorum.txt";
		Writer writer;
		File fout=new File("./",file_name);
		//System.out.println("Creating file:"+fout.getPath()+fout.getName());
		try{if (!fout.exists())	if(fout.createNewFile()) System.out.println("file Created");else System.out.println("problem creating file");}catch(IOException ioe){ioe.printStackTrace();System.out.println("Problem creating file");}
	 try{
		 FileOutputStream FoutStream=new FileOutputStream(file_name, true);
		 //FileOutputStream FoutStream=new FileOutputStream(System.getProperty("user.home")+"/AOS/Project2Files/CheckQuorum", true);
		java.nio.channels.FileLock lock = FoutStream.getChannel().lock();
		  try{
				synchronized(this)
				{
					writer = new BufferedWriter(new OutputStreamWriter(FoutStream, "UTF-8"));
					writer.append(this.mynodeId+"-");
//					for (Pair pp :quorumConnections)
//					{//System.out.println("Nodeid:"+pp.nodeNumber+" Node address:"+pp.nodeName+" Remoteaddress:"+Get_IPV4(pp.TargetChannelvalue.getRemoteAddresses()));
//					writer.append(" "+pp.nodeNumber+"|"+Get_IPV4(pp.TargetChannelvalue.getRemoteAddresses()));
//					}
//					writer.append("|");
					for (String pp1 :quorumMembers)
					{writer.append(pp1+" ");}
					writer.append("\n");
					writer.close();	
				}
	        }catch(IOException ioe){ioe.printStackTrace();}finally {lock.release();FoutStream.close();}
		}catch(Exception e){}
	}
	
	public void printQuorumConnections(){
		int loopVariable=0;
		//System.out.println("Printing the quorumConnectios now..!!");
		
		while(loopVariable < quorumConnections.size()){
			try{System.out.println(quorumConnections.get(loopVariable).nodeName+" : " +(Get_IPV4(quorumConnections.get(loopVariable).TargetChannelvalue.getAllLocalAddresses())));}
			catch(Exception e){}
			loopVariable++;
		}
	}
	
	public synchronized void changeTimeStampOfRequest(String timeType){
		int loopVariable =0; 
		while (loopVariable < Received_Request_Q.size()){
			if(Received_Request_Q.get(loopVariable).nodeID == this.mynodeId){
				Received_Request_Q.get(loopVariable).setStartEndTimeOfRequest(timeType);
			}
			loopVariable++;
		}
	}
	
	public synchronized void setcurrentProcessingNode(int nodeid, int sequenceNumber, boolean inquireMessageSent, boolean inquireMessageReceived){
	currentProcessingNode.nodeID= nodeid;
	currentProcessingNode.sequenceNumber= sequenceNumber;
	currentProcessingNode.inquireMessageSent=inquireMessageSent;
	currentProcessingNode.inquireMessageReceived=inquireMessageReceived;
	}
	
	public synchronized ProcessingObject getCurrentProcessingNodeInfo(){
		return currentProcessingNode;
	}
	
	public synchronized boolean getNodeLockStatus(){
		return AmILocked;
	}
	
	public synchronized void setNodeLockStatus(boolean newLockStatus){
		AmILocked=newLockStatus;
	}
	
	public synchronized void addRequestToWaitQueue(int nodeid, int sequenceNumber){
		ProcessingObject newprocessingObject = new ProcessingObject(nodeid, sequenceNumber, false, false, false);
		waitQueue.add(newprocessingObject);
		sortWaitQueue();
	}
	
	public synchronized void sortWaitQueue(){
		boolean swapped = true;
	      int j = 0;
	      ProcessingObject tmp;
	      while (swapped) {
	            swapped = false;
	            j++;
	            for (int i = 0; i < waitQueue.size()-j; i++) {                                       
	                  if ((waitQueue.get(i).nodeID > waitQueue.get(i+1).nodeID) && (waitQueue.get(i).sequenceNumber == waitQueue.get(i+1).sequenceNumber)) {                          
	                        tmp = (waitQueue.get(i));
	                        waitQueue.set(i, waitQueue.get(i+1));
	                        waitQueue.set(i+1, tmp);
	                        swapped = true;
	                  }
	                  if ((waitQueue.get(i).sequenceNumber > waitQueue.get(i+1).sequenceNumber)) {                          
	                        tmp = (waitQueue.get(i));
	                        waitQueue.set(i, waitQueue.get(i+1));
	                        waitQueue.set(i+1, tmp);
	                        swapped = true;
	                  }
	            }                
	      }
	}
	
	public synchronized void setQuorumReqSetStatus(int nodeid, String newStatus){
		QuorumRequestSet tmpquorumRequestSet = new QuorumRequestSet(nodeid, newStatus);
		int loopVariable = 0;
		while (loopVariable < quorumReqSet.size()){
			if(quorumReqSet.get(loopVariable).nodeID == nodeid){
				quorumReqSet.set(loopVariable, tmpquorumRequestSet);
				break;
			}
			loopVariable++;
		}
	}
	
	public synchronized boolean atLeastOneQuorumSetFailed(){
		boolean answer=false;
		Iterator<QuorumRequestSet> iteratorQuorumReqSet = quorumReqSet.iterator();
		while (iteratorQuorumReqSet.hasNext()){
			if(iteratorQuorumReqSet.next().requestStatus.equalsIgnoreCase("FAILED")){
				answer=true;
				return answer;
			}
		}
		return answer;
	}
	
	public synchronized boolean areAllQuorumSetLocked(){
		boolean answer=true;
		int loopVariable=0;
		
		//System.out.println("Size of quorumReqSet: "+quorumReqSet.size());
		while (loopVariable < quorumReqSet.size()){
			if(!(quorumReqSet.get(loopVariable).requestStatus.equalsIgnoreCase("LOCKED"))){
				answer=false;
				//System.out.println("Leaving areAllQuorumSetLocked");
				return answer;
			}
			loopVariable++;
		}
		//System.out.println("Leaving areAllQuorumSetLocked");
		return answer;
	}
	
	public synchronized ProcessingObject popWaitQueue(){
		ProcessingObject tmpprocessingObject;
		//Retrieve 1st object and return it!
		if(waitQueue.size()>0){
			tmpprocessingObject=  waitQueue.get(0);
			waitQueue.remove(0);
			return tmpprocessingObject;	
		}
		return null;
	}

	public synchronized ProcessingObject getWaitQueueFirstReq(){
		ProcessingObject tmpprocessingObject;
		//Retrieve 1st object and return it!
		if(waitQueue.size()>0){
			tmpprocessingObject=  waitQueue.get(0);
			return tmpprocessingObject;
		}
		return null;
	}	
	
	public synchronized int getWaitQueueSize(){
		return waitQueue.size();
	}
	
	public synchronized int getNextSequenceNumber(){
		return (++this.sequenceNumber);
	}
	
	//Method to send the request message
	public synchronized int sendReqOrReleaseMessage(String messageType, int sequenceNumber){
		//Broadcast the request message
		int nextSequenceNumber=0, loopVariable=0, clientID, tmpSequenceNumber=0;
		
		if(messageType.equalsIgnoreCase("REQUEST")){
			nextSequenceNumber = getNextSequenceNumber();
		}
		else{
			nextSequenceNumber = sequenceNumber;		
		}
		//Actual Send of Request and Release message!
		while(loopVariable < quorumReqSet.size()){
			System.out.println("-------------------------------------------------");
			writeNodeStatus("-------------------------------------------------");
			clientID = quorumReqSet.get(loopVariable).nodeID;
			if(messageType.equalsIgnoreCase("RELEASE")){
				setQuorumReqSetStatus(clientID, "null");				
			}		
			if(clientID != this.mynodeId){
				SctpChannel tmpSctpChannel = getSctpChannel(String.valueOf(clientID));
				sendMessage(clientID,nextSequenceNumber, messageType, tmpSctpChannel);
			}
			else{
				if(messageType.equalsIgnoreCase("REQUEST")){
					//Add own request to ReceivedRequestQueue!
					ProcessingObject newRequest = new ProcessingObject(this.mynodeId, nextSequenceNumber, false, false, false);
					addToRequestQ(newRequest);
					//changeCounterValue(newRequest.nodeID, newRequest.sequenceNumber, "REQUEST");
					//Mention StartTime
					changeTimeStampOfRequest("START");
					//System.out.println("Current node status :" +(getNodeLockStatus()));
					if(!getNodeLockStatus()){	//If node is not locked
						//lock self for incoming request and change currentProcessingInfo
						System.out.println("I was not locked so Im locking myself for client: " +newRequest.nodeID);
						writeNodeStatus("I was not locked so Im locking myself for client: " +newRequest.nodeID);
						setNodeLockStatus(true);
						setcurrentProcessingNode(newRequest.nodeID,newRequest.sequenceNumber, false, false);
						setQuorumReqSetStatus(newRequest.nodeID, "LOCKED");
						//System.out.println("Sending a Locked message client: " +newRequest.nodeID);
					}
					else { //If node is already locked
						ProcessingObject tmpCurrentProcessing = getCurrentProcessingNodeInfo();
						//System.out.println("Received a new reqeuest from client: " +newRequest.nodeID);
						System.out.println("I am locked for Node : " +tmpCurrentProcessing.nodeID+" with sequenceNumber: "+tmpCurrentProcessing.sequenceNumber);
						writeNodeStatus("I am locked for Node : " +tmpCurrentProcessing.nodeID+" with sequenceNumber: "+tmpCurrentProcessing.sequenceNumber);
						//Send Failed message to source
						setQuorumReqSetStatus(newRequest.nodeID, "FAILED");
						System.out.println("Sending a failed message to Node : " +newRequest.nodeID);
						writeNodeStatus("Sending a failed message to Node : " +newRequest.nodeID);
						
						//Add new message to wait queue
						addRequestToWaitQueue(newRequest.nodeID,newRequest.sequenceNumber);
						//System.out.println("Adding a request to waitQueue for : " +newRequest.nodeID);	
						
						
						//Now check whether incoming request precedes the existing request
						if(newRequest.sequenceNumber < tmpCurrentProcessing.sequenceNumber && tmpCurrentProcessing.inquireMessageSent==false){
							//Set inquire message_sent flag as yes	
							setcurrentProcessingNode(tmpCurrentProcessing.nodeID,tmpCurrentProcessing.sequenceNumber, true, false);
							//Send inquire message
							//clientID =String.valueOf(tmpCurrentProcessing.nodeID);
							clientID =tmpCurrentProcessing.nodeID;
							SctpChannel tmpSctpChannel = getSctpChannel(String.valueOf(clientID));
							sendMessage(clientID,tmpCurrentProcessing.sequenceNumber,"INQUIRE",tmpSctpChannel);
							System.out.println("Sending an Inquire message to Node : " +tmpCurrentProcessing.nodeID);
							writeNodeStatus("Sending an Inquire message to Node : " +tmpCurrentProcessing.nodeID);
						}
						if( newRequest.sequenceNumber == tmpCurrentProcessing.sequenceNumber && newRequest.nodeID < tmpCurrentProcessing.nodeID && tmpCurrentProcessing.inquireMessageSent==false){
							//Set inquire message_sent flag as yes	
							setcurrentProcessingNode(tmpCurrentProcessing.nodeID,tmpCurrentProcessing.sequenceNumber, true, false);
							//Send inquire message
							//clientID =String.valueOf(tmpCurrentProcessing.nodeID);
							clientID =tmpCurrentProcessing.nodeID;
							SctpChannel tmpSctpChannel = getSctpChannel(String.valueOf(clientID));
							sendMessage(clientID,tmpCurrentProcessing.sequenceNumber,"INQUIRE",tmpSctpChannel);
							System.out.println("Sending an Inquire message to Node : " +tmpCurrentProcessing.nodeID);
							writeNodeStatus("Sending an Inquire message to Node : " +tmpCurrentProcessing.nodeID);
						}
						
					}
				}
				else if(messageType.equalsIgnoreCase("RELEASE")){
					setNodeLockStatus(false);
					changeMessageCounterOfMemberReqSet(this.mynodeId);					
					//changeTimeStampOfRequest("END");							
					ProcessingObject tmpCurrentProcessing = getCurrentProcessingNodeInfo();
					setQuorumReqSetStatus(tmpCurrentProcessing.nodeID, "null");
					ProcessingObject tmpRequestReceivedObj = getRequestQueueObject(tmpCurrentProcessing.nodeID, tmpCurrentProcessing.sequenceNumber);
					//Print all the counters in a file
					print_message_counts_to_file(tmpRequestReceivedObj.nodeID, tmpRequestReceivedObj.sequenceNumber, tmpRequestReceivedObj.requests, tmpRequestReceivedObj.locked_failed, tmpRequestReceivedObj.inquire,tmpRequestReceivedObj.relinquish, tmpRequestReceivedObj.release);
					changeRequestStatus(tmpCurrentProcessing.nodeID,tmpCurrentProcessing.sequenceNumber,true);
					writeNodeStatus("Just finished processing my own Request!");
					System.out.println("Just finished processing my own Request!");	
				}				
			}
			loopVariable++;
			tmpSequenceNumber = getNextSequenceNumber();
		}
		return nextSequenceNumber;
	}
	
	//This method will send Locked/Failed message depending on the node Status
	public synchronized void sendMessage(int clientID, int sequenceNumber, String messageType,SctpChannel sctpChannel){
		//Before Sending a message Increment corresponding counter! of the request in received_request_Q
		
		//This method will be invoked once a node receives a Request from its quorum member!
		MessageStructure newMessage = new MessageStructure(sequenceNumber,messageType,this.mynodeId,clientID);
		//Send block
		try{
			ByteBuffer byteBuffer = ByteBuffer.allocate(MAX_BUF_SIZE);        
			final MessageInfo messageInfo = MessageInfo.createOutgoing(null, 0);
			byteBuffer.put(MessageStructure.serialize(newMessage));
			byteBuffer.flip();
			
	        sctpChannel.send(byteBuffer, messageInfo);
	        sctpChannel.configureBlocking(false);
	        int nextSequenceNumber = getNextSequenceNumber();
	        System.out.println(messageType+ " message sent to Client: "+clientID);
	        writeNodeStatus(messageType+ " message sent to Client: "+clientID);
	        byteBuffer.clear();
	        
	        
		}
		catch(Exception e){
			e.printStackTrace();
			System.out.println("Unable to send message to Client: "+clientID);
		}
	}
			
	public synchronized SctpChannel getSctpChannel(String clientID){
		int loopVariable=0;
		boolean flag =false;
		//System.out.println("Looking SctpChannel for :" +clientID);
		while(loopVariable < quorumConnections.size()){
			//System.out.println("Current quorum id: " +(quorumConnections.get(loopVariable)).nodeNumber);
			if(((quorumConnections.get(loopVariable)).nodeNumber).equalsIgnoreCase(clientID)){
				//System.out.println("Found the node in quorumConnections");
				if(clientID.equalsIgnoreCase(String.valueOf(mynodeId))){
					if(flag){
						return quorumConnections.get(loopVariable).TargetChannelvalue;
					}
					else
						flag=true;
				}
				else
					return quorumConnections.get(loopVariable).TargetChannelvalue;
			}
			loopVariable++;
		}
		return null;
	}
	
	public synchronized void clearInquireQueue(){
		inquireMessageQueue.clear();
	}
	
	public synchronized void sendResponseToInquireQueue(){
		int loopVariable = 0;
		int clientID, nextSequenceNumber;
		SctpChannel newSctpChannel;
		while(loopVariable < inquireMessageQueue.size()){
			clientID = inquireMessageQueue.get(loopVariable)[0];
			newSctpChannel = getSctpChannel(String.valueOf(clientID));
			sendMessage(clientID, inquireMessageQueue.get(loopVariable)[1], "RELINQUISH",newSctpChannel);
			loopVariable++;
			setQuorumReqSetStatus(clientID, "FAILED");
			nextSequenceNumber = getNextSequenceNumber();
		}
		//truncate the list 
		clearInquireQueue();		
	}
	
	public synchronized void addToInquireQueue(int clientID, int sequenceNumber){
		int[] tmpInquireMessage = {clientID,sequenceNumber};
		inquireMessageQueue.add(tmpInquireMessage);
	}
	
	public synchronized boolean getRequestStatus(int clientID, int sequenceNumber){
		int loopVariable = 0;
		ProcessingObject tmpRequestObject;
		while(loopVariable < Received_Request_Q.size()){
			tmpRequestObject = Received_Request_Q.get(loopVariable);
			if(tmpRequestObject.nodeID == clientID && tmpRequestObject.sequenceNumber == sequenceNumber){
				return tmpRequestObject.isFullfilled;
			}
			loopVariable++;
		}
		return false;
	}
	
	public synchronized void changeRequestStatus(int clientID, int sequenceNumber, boolean isFulfilled){
		int loopVariable = 0;
		ProcessingObject tmpRequestObject;
		
		while(loopVariable < Received_Request_Q.size()){
			tmpRequestObject = Received_Request_Q.get(loopVariable);
			if(tmpRequestObject.nodeID == clientID && tmpRequestObject.sequenceNumber == sequenceNumber){
				//System.out.println("Changing request status to fulfilled for client: "+tmpRequestObject.nodeID);
				tmpRequestObject.setRequestStatus(isFulfilled);
			}
			loopVariable++;
		}
	}
	
	public synchronized void changeCounterValue(int clientID, int sequenceNumber, String messageType){
		int loopVariable=0;
		
		//System.out.println("Size of the Received_Request_Q: "+Received_Request_Q.size());
		while(loopVariable < Received_Request_Q.size()){
			if(Received_Request_Q.get(loopVariable).nodeID == clientID && Received_Request_Q.get(loopVariable).sequenceNumber == sequenceNumber){
				//System.out.println("Found the request now incrementing the counters for "+Received_Request_Q.get(loopVariable).nodeID+ " with sequenceNumber: "+Received_Request_Q.get(loopVariable).sequenceNumber);
				Received_Request_Q.get(loopVariable).changeCounterValue(messageType);
				break;
			}
			loopVariable++;
		}
	}
	
	public synchronized int getSizeOFQuorumConnections(){
		return quorumConnections.size();
	}
	
	public synchronized Pair retrieveQuorumConnections(int position){
		if(!(quorumConnections.get(position).nodeNumber.equalsIgnoreCase(String.valueOf(mynodeId)))){
			return quorumConnections.get(position);
		}
		return (new Pair("null","null",null,"NULL"));
		/*int loopVariable=0;
		//This nodes receiverQuorumConnection will precede the current position
		//So if loopVariable has < value than position and quorumConnections.get(position).nodeID =  quorumConnections.get(loopVariable).nodeID
		while(loopVariable < quorumConnections.size()){
			if(quorumConnections.get(loopVariable).nodeNumber.equalsIgnoreCase(String.valueOf(mynodeId))){
				break;
			}
			loopVariable++;
		}
		//loopVariable has an index of receiver Channel
		if(loopVariable == position && quorumConnections.get(position).nodeNumber.equalsIgnoreCase(String.valueOf(mynodeId))){
			System.out.println("Sending SctpChannel of : "+quorumConnections.get(position).nodeNumber);
			return quorumConnections.get(position);
		}
		else if(!(quorumConnections.get(position).nodeNumber.equalsIgnoreCase(String.valueOf(mynodeId)))){
			System.out.println("Sending SctpChannel of : "+quorumConnections.get(position).nodeNumber);
			return quorumConnections.get(position);
		}
		
		return (new Pair("null","null",null));*/
	}
	
	public synchronized ProcessingObject getRequestQueueObject(int nodeID, int sequenceNumber){
		int loopVariable=0;
		while(loopVariable < Received_Request_Q.size()){
			if(Received_Request_Q.get(loopVariable).nodeID == nodeID && Received_Request_Q.get(loopVariable).sequenceNumber == sequenceNumber){
				//System.out.println("Found object in request queue!!!");
				return Received_Request_Q.get(loopVariable);
			}
			loopVariable++;
		}
		return null;
	}
	
	public synchronized void addToRequestQ(ProcessingObject newRequest){
		//System.out.println("Adding new request to Received_Request_Q");
		Received_Request_Q.add(newRequest);
		//System.out.println("Added a request to received_request_queue and Current size of the request queue: " +Received_Request_Q.size());
	}
	
	public void receiverFunction(){
		SctpChannel neighbour;
		Pair iqconnect;
		//ByteBuffer byteBuffer = ByteBuffer.allocate(MAX_BUF_SIZE);
		//MessageInfo messageInfo=null;
		MessageStructure Receive_Message_Object=null;
		String tmpAddr=null;
		int currentNodeid, tmpSequenceNumber, clientID, loopVariable=0;
		ProcessingObject tmpCurrentProcessing;
		boolean receive = true, someFlag=false;
		
		while(receive)
		{
			
			//try{Thread.sleep(2500);}catch(Exception e){}
			if(loopVariable == getSizeOFQuorumConnections()){
				loopVariable=0;
			}
			iqconnect = retrieveQuorumConnections(loopVariable);
			//System.out.println("Retreived the SCTP channel for : "+iqconnect.nodeNumber);
			loopVariable++;
			neighbour=iqconnect.TargetChannelvalue;
			
			try{
				MessageInfo messageInfo;
				ByteBuffer byteBuffer = ByteBuffer.allocate(MAX_BUF_SIZE);	
				byteBuffer.rewind();
				byteBuffer.clear();
				//tmpAddr=Get_IPV4(neighbour.getRemoteAddresses());
				//System.out.println("isBlocking for "+iqconnect.nodeNumber+" : " +neighbour.isBlocking());
				
				messageInfo = neighbour.receive(byteBuffer,null,null);
				neighbour.configureBlocking(false);
				//System.out.println("Received a message!");
				Receive_Message_Object=(MessageStructure)MessageStructure.deserialize(byteBuffer);					
				
				someFlag=true;
				
			}
			catch(Exception e){
				//e.printStackTrace();
				//System.out.println("Exception in receive from :"+tmpAddr);
			}
			//Start of Receive event
			try{
				if(someFlag && !(iqconnect.nodeName.equalsIgnoreCase("null"))){
					synchronized(this){
					someFlag=false;
					System.out.println();
					System.out.println("---------------------------------------------------");
					System.out.println("Received a new message");
					System.out.println("Node          : "+Receive_Message_Object.Source);
					System.out.println("MessageType   : "+Receive_Message_Object.Msg_type);
					System.out.println("SequenceNumber: "+Receive_Message_Object.sequenceNumber);
					
					//Write it to a file
					writeNodeStatus("---------------------------------------------------");
					writeNodeStatus("Received a new message");
					writeNodeStatus("Node          : "+Receive_Message_Object.Source);
					writeNodeStatus("MessageType   : "+Receive_Message_Object.Msg_type);
					writeNodeStatus("SequenceNumber: "+Receive_Message_Object.sequenceNumber);
					
					
					//Retrieve current Node status and next sequence number
					tmpCurrentProcessing = getCurrentProcessingNodeInfo();
					tmpSequenceNumber = getNextSequenceNumber();
					
					//Case 1: if request is received
					if(Receive_Message_Object.Msg_type.equalsIgnoreCase("REQUEST"))
					{
						//try{Thread.sleep(1000);}catch(Exception e){}
						//System.out.println("Received a new request from Node : " +Receive_Message_Object.Source);
						ProcessingObject newRequest = new ProcessingObject(Receive_Message_Object.Source, Receive_Message_Object.sequenceNumber, false, false, false);
						addToRequestQ(newRequest);
						
						//Add nodeID to QuorumMemberReqSet
						addNewQuorumMemberReqSet(Receive_Message_Object.Source);
						//System.out.println("Added new request to Received_Request_Q");
						changeCounterValue(Receive_Message_Object.Source, Receive_Message_Object.sequenceNumber, Receive_Message_Object.Msg_type);
						//System.out.println("Current node status :" +(!getNodeLockStatus()));
						if(!getNodeLockStatus()){	//If node is not locked
							//lock self for incoming request and change currentProcessingInfo
							//System.out.println("I was not locked so Im locking myself for client: " +Receive_Message_Object.Source);
							setNodeLockStatus(true);
							setcurrentProcessingNode(Receive_Message_Object.Source,Receive_Message_Object.sequenceNumber, false, false);
								
							//Send message to source as "locked"
							//clientID =String.valueOf(Receive_Message_Object.Source);
							clientID = Receive_Message_Object.Source;
							sendMessage(clientID,Receive_Message_Object.sequenceNumber,"LOCKED",neighbour);
							System.out.println("Sending a Locked message Node : " +Receive_Message_Object.Source);							
							writeNodeStatus("Sending a Locked message Node : " +Receive_Message_Object.Source);
						}
						else { //If node is already locked
							//System.out.println("Received a new request from Node: " +Receive_Message_Object.Source);
							System.out.println("I am locked for Node : " +tmpCurrentProcessing.nodeID+"  with sequence no: "+tmpCurrentProcessing.sequenceNumber);
							writeNodeStatus("I am locked for Node : " +tmpCurrentProcessing.nodeID+"  with sequence no: "+tmpCurrentProcessing.sequenceNumber);
							//Send Failed message to source
							//clientID =String.valueOf(Receive_Message_Object.Source);
							clientID = Receive_Message_Object.Source;
							sendMessage(clientID,Receive_Message_Object.sequenceNumber,"FAILED",neighbour);
							System.out.println("Sending a failed message to Node : " +Receive_Message_Object.Source);
							writeNodeStatus("Sending a failed message to Node : " +Receive_Message_Object.Source);
							//Add new message to wait queue
							addRequestToWaitQueue(Receive_Message_Object.Source,Receive_Message_Object.sequenceNumber);
							System.out.println("Adding a request to waitQueue for Node : " +Receive_Message_Object.Source+"  with sequence no: "+Receive_Message_Object.sequenceNumber);	
							writeNodeStatus("Adding a request to waitQueue for Node : " +Receive_Message_Object.Source+"  with sequence no: "+Receive_Message_Object.sequenceNumber);
							
							//Now check whether incoming request precedes the existing request
							if(Receive_Message_Object.sequenceNumber < tmpCurrentProcessing.sequenceNumber && !tmpCurrentProcessing.inquireMessageSent){
								//Set inquire message_sent flag as yes	
								setcurrentProcessingNode(tmpCurrentProcessing.nodeID,tmpCurrentProcessing.sequenceNumber, true, false);
								//Send inquire message
								//clientID =String.valueOf(tmpCurrentProcessing.nodeID);
								if(tmpCurrentProcessing.nodeID != this.mynodeId){
									clientID =tmpCurrentProcessing.nodeID;
									SctpChannel tmpSctpChannel = getSctpChannel(String.valueOf(clientID));
									sendMessage(clientID,tmpCurrentProcessing.sequenceNumber,"INQUIRE",tmpSctpChannel);
									System.out.println("Sending an Inquire message to Node : " +tmpCurrentProcessing.nodeID);
									writeNodeStatus("Sending an Inquire message to Node : " +tmpCurrentProcessing.nodeID);
									//change the counter for Inquire message!
									changeCounterValue(Receive_Message_Object.Source, Receive_Message_Object.sequenceNumber, "INQUIRE");
								}
								else{
									//Swap and send locked message!
									//pop that request!
									ProcessingObject waitQueueReq = popWaitQueue();
									//tmpCurrentProcessing
									//Add tmpCurrentProcessing to waitQueue
									addRequestToWaitQueue(tmpCurrentProcessing.nodeID,tmpCurrentProcessing.sequenceNumber);
									setQuorumReqSetStatus(tmpCurrentProcessing.nodeID, "FAILED");
									setcurrentProcessingNode(waitQueueReq.nodeID, waitQueueReq.sequenceNumber,false,false);
									//Send locked message to waitQueueReq.nodeID
									clientID = waitQueueReq.nodeID;
									SctpChannel tmpSctpChannel = getSctpChannel(String.valueOf(clientID));
									sendMessage(clientID,tmpCurrentProcessing.sequenceNumber,"LOCKED",tmpSctpChannel);	
									System.out.println("Sending a LOCKED message to Node : " +clientID);
									writeNodeStatus("Sending a LOCKED message to Node : " +clientID);
								}
							}
							//Where sequence number is equal then check the nodeID
							if((Receive_Message_Object.sequenceNumber == tmpCurrentProcessing.sequenceNumber) && (Receive_Message_Object.Source < tmpCurrentProcessing.nodeID) && !tmpCurrentProcessing.inquireMessageSent){
								//Set inquire message_sent flag as yes	
								setcurrentProcessingNode(tmpCurrentProcessing.nodeID,tmpCurrentProcessing.sequenceNumber, true, false);
								//Send inquire message
								//clientID =String.valueOf(tmpCurrentProcessing.nodeID);
								if(tmpCurrentProcessing.nodeID != this.mynodeId){
									clientID =tmpCurrentProcessing.nodeID;
									SctpChannel tmpSctpChannel = getSctpChannel(String.valueOf(clientID));
									sendMessage(clientID,tmpCurrentProcessing.sequenceNumber,"INQUIRE",tmpSctpChannel);
									System.out.println("Sending an Inquire message to Node : " +tmpCurrentProcessing.nodeID);
									writeNodeStatus("Sending an Inquire message to Node : " +tmpCurrentProcessing.nodeID);
									//Change the InquireCounter!
									changeCounterValue(Receive_Message_Object.Source, Receive_Message_Object.sequenceNumber, "INQUIRE");
								}
								else{
									//Swap and send locked message!
									//pop that request!
									ProcessingObject waitQueueReq = popWaitQueue();
									//tmpCurrentProcessing
									//Add tmpCurrentProcessing to waitQueue
									addRequestToWaitQueue(tmpCurrentProcessing.nodeID,tmpCurrentProcessing.sequenceNumber);
									setQuorumReqSetStatus(tmpCurrentProcessing.nodeID, "FAILED");
									setcurrentProcessingNode(waitQueueReq.nodeID, waitQueueReq.sequenceNumber,false,false);
									//Send locked message to waitQueueReq.nodeID
									clientID = waitQueueReq.nodeID;
									SctpChannel tmpSctpChannel = getSctpChannel(String.valueOf(clientID));
									sendMessage(clientID,tmpCurrentProcessing.sequenceNumber,"LOCKED",tmpSctpChannel);
									System.out.println("Sending a Locked message Node : " +clientID);
									writeNodeStatus("Sending a Locked message Node : " +clientID);
								}
							}
						}			
					}
				
					//Case 2: Received Locked/Failed message from the quorum set
					//Request will be clientID, sequenceNumber
					if(Receive_Message_Object.Msg_type.equalsIgnoreCase("LOCKED")){
						//try{Thread.sleep(1000);}catch(Exception e){}
						//System.out.println("Received a new Locked message from Node : " +Receive_Message_Object.Source);
						
						changeCounterValue(Receive_Message_Object.Destination, Receive_Message_Object.sequenceNumber, Receive_Message_Object.Msg_type);
						//System.out.println("Changing the locked status for : " +Receive_Message_Object.Source);
						//Look for the quorum set list and update the status 
						setQuorumReqSetStatus(Receive_Message_Object.Source, Receive_Message_Object.Msg_type);
						
						//Are all the quorum sets Locked?
						//System.out.println("Are all quorum sets locked?? : "+areAllQuorumSetLocked());
						if(areAllQuorumSetLocked()){
							clearInquireQueue();
							//Enter into critical section.
							
							System.out.println("---------------------------------------------------------");
							System.out.println("| Entering into Critical Section with Sequence Number: "+tmpCurrentProcessing.sequenceNumber+" |");
							System.out.println("---------------------------------------------------------");
							
							
							
							writeNodeStatus("---------------------------------------------------------");
							writeNodeStatus("| Entering into Critical Section with Sequence Number: "+tmpCurrentProcessing.sequenceNumber+" |");
							writeNodeStatus("---------------------------------------------------------");
							
							//This is a blocking call; so no new messages will be read until the node comes out of critical section.
							try{
								//set the endTime for the request!
								changeTimeStampOfRequest("END");
								ProcessingObject requestQueueObjet = getRequestQueueObject(tmpCurrentProcessing.nodeID, tmpCurrentProcessing.sequenceNumber);
								
								Use_Critical_Section(requestQueueObjet.nodeID, requestQueueObjet.sequenceNumber, requestQueueObjet.startTime, requestQueueObjet.endTime);
								Thread.sleep(2000);
							}
							catch(Exception e){
								System.out.println("Received an exception in Locked Message");
								e.printStackTrace();
							}
							//Send a Release broadcast to quorumReqSet
							//System.out.println("Sending Release message to all quorum sets");
							sendReqOrReleaseMessage("RELEASE",tmpCurrentProcessing.sequenceNumber);
							//Generate a new request if the 
							//Pop a new request if any!
							if(getWaitQueueSize() > 0){
								ProcessingObject firstRequestInWaitQueue = popWaitQueue();
								if(firstRequestInWaitQueue!= null){
									setNodeLockStatus(true);
									setcurrentProcessingNode(firstRequestInWaitQueue.nodeID, firstRequestInWaitQueue.sequenceNumber, false, false);
									//check whom to send this message!
									if(firstRequestInWaitQueue.nodeID != this.mynodeId){
										clientID= firstRequestInWaitQueue.nodeID;
										SctpChannel tmpSctpChannel = getSctpChannel(String.valueOf(clientID));
										sendMessage(clientID, firstRequestInWaitQueue.sequenceNumber, "LOCKED", tmpSctpChannel);
										System.out.println("Sending Locked message to Node : " +clientID);
										writeNodeStatus("Sending Locked message to Node : " +clientID);
										if(numberOfCSReqRemained(this.mynodeId) > 0 ){
											//Then generate a new request!
											sendReqOrReleaseMessage("REQUEST",0);
										}
									}
								}
							}
							else if(numberOfCSReqRemained(this.mynodeId) > 0 ){
								//Then generate a new request!
								sendReqOrReleaseMessage("REQUEST",0);
							}
						}
					}
					else if(Receive_Message_Object.Msg_type.equalsIgnoreCase("FAILED")){
						//try{Thread.sleep(1000);}catch(Exception e){}
						//System.out.println("Received a Failed from Node : " +Receive_Message_Object.Source);
						
						changeCounterValue(Receive_Message_Object.Destination, Receive_Message_Object.sequenceNumber, Receive_Message_Object.Msg_type);
						//Look for the quorum set list and update the status 
						setQuorumReqSetStatus(Receive_Message_Object.Source, Receive_Message_Object.Msg_type);
						//System.out.println("Setting failed status of client: " +Receive_Message_Object.Source);
						
						//Check whether Inquire message has come or not
						if(tmpCurrentProcessing.inquireMessageReceived){
							//Respond to all Inquire Requests.
							System.out.println("There are inquire messages in queue! Sending relinquish messages to them!");
							writeNodeStatus("There are inquire messages in queue! Sending relinquish messages to them!");
							sendResponseToInquireQueue();
							//Set the InquireMessageReceived as false!
							setcurrentProcessingNode(tmpCurrentProcessing.nodeID, tmpCurrentProcessing.sequenceNumber, tmpCurrentProcessing.inquireMessageSent, false);
							//System.out.println("Setting the inquireMessagesReceived Status as false for : " +tmpCurrentProcessing.nodeID);
						}
						//Check whether waitQueue has a preceding request
						ProcessingObject tmpWaitQueueFirstReq;
						if(getWaitQueueSize() > 0){
							System.out.println("waitQueue has outstanding requests!");
							
							tmpWaitQueueFirstReq = getWaitQueueFirstReq();
								if((tmpWaitQueueFirstReq.nodeID < tmpCurrentProcessing.nodeID && (tmpWaitQueueFirstReq.sequenceNumber == tmpCurrentProcessing.sequenceNumber)) || (tmpWaitQueueFirstReq.sequenceNumber < tmpCurrentProcessing.sequenceNumber)){
									System.out.println("waitQueue has a preceding request with :"+tmpWaitQueueFirstReq.nodeID+" and seq no: " +tmpWaitQueueFirstReq.sequenceNumber+ "!");
									writeNodeStatus("waitQueue has a preceding request with :"+tmpWaitQueueFirstReq.nodeID+" and seq no: " +tmpWaitQueueFirstReq.sequenceNumber+ "!");
									//Swap!
									tmpWaitQueueFirstReq = popWaitQueue();
									addRequestToWaitQueue(tmpCurrentProcessing.nodeID, tmpCurrentProcessing.sequenceNumber);
									setcurrentProcessingNode(tmpWaitQueueFirstReq.nodeID, tmpWaitQueueFirstReq.sequenceNumber, false, false);
									
									if(tmpWaitQueueFirstReq.nodeID != this.mynodeId){
										//Send locked message
										//clientID =String.valueOf(tmpCurrentProcessing.nodeID);
										clientID =tmpWaitQueueFirstReq.nodeID;
										SctpChannel tmpSctpChannel = getSctpChannel(String.valueOf(clientID));
										sendMessage(clientID,tmpWaitQueueFirstReq.sequenceNumber,"LOCKED", tmpSctpChannel);
										System.out.println("Sending locked message to Node : " +clientID);		
										writeNodeStatus("Sending locked message to Node : " +clientID);		
									}
									else{
										clientID =tmpWaitQueueFirstReq.nodeID;
										System.out.println("Sending locked message to Node : " +clientID);
										setQuorumReqSetStatus(clientID, "LOCKED");
										writeNodeStatus("Sending locked message to Node : " +clientID);
									}

								}
						}					
					}			
				
					//Case 3: Received an Inquire message!
					if(Receive_Message_Object.Msg_type.equalsIgnoreCase("INQUIRE")){
						//try{Thread.sleep(1000);}catch(Exception e){}
						//Request will be clientID, sequenceNumber
						//System.out.println("Received an Inquire request from Node : " +Receive_Message_Object.Source);
						//tmpCurrentProcessingReq = getCurrentProcessingNodeInfo();
						
						if(!getRequestStatus(Receive_Message_Object.Destination, Receive_Message_Object.sequenceNumber)){
							//System.out.println("Setting inquiremessageReceived flag as true!");
							//Set inquireMessageReceived Flag to true
							setcurrentProcessingNode(tmpCurrentProcessing.nodeID,tmpCurrentProcessing.sequenceNumber, false, true);
							//processingObject tmpWaitQueueFirstReq;
							//tmpWaitQueueFirstReq = getWaitQueueFirstReq();
							
							//If it has received a failed message from at least on of the Quorum set member!! 
							if(atLeastOneQuorumSetFailed()){
								System.out.println("Received atleast one Failed message from my quorum");
								writeNodeStatus("Received atleast one Failed message from my quorum");
								setQuorumReqSetStatus(Receive_Message_Object.Source,"FAILED");
								//I was unable to lock all of them so i'm reliving you!
								//Change the status of the incoming quorum req set.
								//Send Relinquish message to Receive_Message_Object.Source
								clientID =(Receive_Message_Object.Source);
								sendMessage(clientID,Receive_Message_Object.sequenceNumber,"RELINQUISH",neighbour);
								System.out.println("Sending Relinquish message to Node : " +clientID);	
								writeNodeStatus("Sending Relinquish message to Node : " +clientID);	
							}
							else{
								//Set the inquireMessageReceived flag to true
								System.out.println("Waiting for at least one failed message and received inquire message before that!");
								writeNodeStatus("Waiting for at least one failed message and received inquire message before that!");
								setcurrentProcessingNode(tmpCurrentProcessing.nodeID, tmpCurrentProcessing.sequenceNumber,tmpCurrentProcessing.inquireMessageSent, true);
								//And add the element into inquireMessageQueue!
								//Since this node doesn't know yet whether it will succeed or or not
								addToInquireQueue(Receive_Message_Object.Source,Receive_Message_Object.sequenceNumber);
							}
						}
					}				

					//Case 4: Received a Relinquish message!
					if(Receive_Message_Object.Msg_type.equalsIgnoreCase("RELINQUISH")){
						//try{Thread.sleep(1000);}catch(Exception e){}
						//changeCounterValue(Receive_Message_Object.Source, Receive_Message_Object.sequenceNumber, Receive_Message_Object.Msg_type);
						ProcessingObject tmpWaitQueueFirstReq = getWaitQueueFirstReq();
						//System.out.println("Received a Relinquish from Node : " +Receive_Message_Object.Source);
						
						
						if(getWaitQueueSize() > 0){
							if(tmpWaitQueueFirstReq.sequenceNumber < tmpCurrentProcessing.sequenceNumber && tmpCurrentProcessing.inquireMessageSent){
								System.out.println("waitQueue has a preceding request with : " +tmpWaitQueueFirstReq.nodeID+" and sequence number : " +tmpWaitQueueFirstReq.sequenceNumber);
								writeNodeStatus("waitQueue has a preceding request with : " +tmpWaitQueueFirstReq.nodeID+" and sequence number : " +tmpWaitQueueFirstReq.sequenceNumber);
								//Pop the first request from the waitQueue 
								ProcessingObject firstRequestInWaitQueue = popWaitQueue();
								// Now swap
								//Add currentprocess to waitQueue
								addRequestToWaitQueue(tmpCurrentProcessing.nodeID, tmpCurrentProcessing.sequenceNumber);
								//Add firstRequestInWaitQueue to currentProcessingNode
								setNodeLockStatus(true);
								setcurrentProcessingNode(firstRequestInWaitQueue.nodeID,firstRequestInWaitQueue.sequenceNumber, false, false);
								//Send Locked message to currentProcessingNode.source
								clientID = firstRequestInWaitQueue.nodeID;
								SctpChannel tmpSctpChannel = getSctpChannel(String.valueOf(clientID));
								sendMessage(clientID,firstRequestInWaitQueue.sequenceNumber, "LOCKED", tmpSctpChannel);
								clearInquireQueue();
								System.out.println("Sending Locked message to Node : " +clientID);
								writeNodeStatus("Sending Locked message to Node : " +clientID);
								changeCounterValue(clientID, firstRequestInWaitQueue.sequenceNumber, "RELINQUISH");
								
							}
							if(tmpWaitQueueFirstReq.sequenceNumber == tmpCurrentProcessing.sequenceNumber && tmpWaitQueueFirstReq.nodeID < tmpCurrentProcessing.nodeID && tmpCurrentProcessing.inquireMessageSent){
								System.out.println("waitQueue has a preceding request with : " +tmpWaitQueueFirstReq.nodeID+" and sequence number : " +tmpWaitQueueFirstReq.sequenceNumber);
								writeNodeStatus("waitQueue has a preceding request with : " +tmpWaitQueueFirstReq.nodeID+" and sequence number : " +tmpWaitQueueFirstReq.sequenceNumber);
								
								//Pop the first request from the waitQueue 
								ProcessingObject firstRequestInWaitQueue = popWaitQueue();
								// Now swap
								//Add firstRequestInWaitQueue to currentProcessingNode
								setNodeLockStatus(true);
								setcurrentProcessingNode(firstRequestInWaitQueue.nodeID,firstRequestInWaitQueue.sequenceNumber, false, false);
								//Add currentprocess to waitQueue
								addRequestToWaitQueue(tmpCurrentProcessing.nodeID, tmpCurrentProcessing.sequenceNumber);
								//Send Locked message to currentProcessingNode.source
								clientID = firstRequestInWaitQueue.nodeID;
								SctpChannel tmpSctpChannel = getSctpChannel(String.valueOf(clientID));
								sendMessage(clientID,firstRequestInWaitQueue.sequenceNumber, "LOCKED", tmpSctpChannel);
								clearInquireQueue();
								System.out.println("Sending locked message to Node : " +clientID);
								writeNodeStatus("Sending locked message to Node : " +clientID);
							}
						}					
					}
					
					//Case 5: Received a Release message!
					if(Receive_Message_Object.Msg_type.equalsIgnoreCase("RELEASE")){
						//try{Thread.sleep(1000);}catch(Exception e){}
						changeCounterValue(Receive_Message_Object.Source, Receive_Message_Object.sequenceNumber, Receive_Message_Object.Msg_type);
						changeMessageCounterOfMemberReqSet(Receive_Message_Object.Source);
						//System.out.println("Received release message from Node : " +Receive_Message_Object.Source);
						//Set the status of the request as fulfilled
						changeRequestStatus(Receive_Message_Object.Source, Receive_Message_Object.sequenceNumber, true);
						ProcessingObject requestQueueObjet = getRequestQueueObject(tmpCurrentProcessing.nodeID, tmpCurrentProcessing.sequenceNumber);
						print_message_counts_to_file(requestQueueObjet.nodeID, requestQueueObjet.sequenceNumber, requestQueueObjet.requests,  requestQueueObjet.locked_failed, requestQueueObjet.inquire,  requestQueueObjet.relinquish,  requestQueueObjet.release);
						clearInquireQueue();
						
						//System.out.println("Chaneged the request status to fulfilled in release!" );
						
						
						if(getWaitQueueSize() > 0){
							System.out.println("waitQueue has a request in release!");
							writeNodeStatus("waitQueue has a request in release!");
							//Pop new request
							ProcessingObject firstRequestInWaitQueue = popWaitQueue();
							if(firstRequestInWaitQueue!= null){
								//System.out.println("First request in waitQueue is for : "+firstRequestInWaitQueue.nodeID);
								setNodeLockStatus(true);
								setcurrentProcessingNode(firstRequestInWaitQueue.nodeID, firstRequestInWaitQueue.sequenceNumber, false, false);
								//check whom to send this message!
								if(firstRequestInWaitQueue.nodeID != this.mynodeId){
									clientID= firstRequestInWaitQueue.nodeID;
									SctpChannel tmpSctpChannel = getSctpChannel(String.valueOf(clientID));
									sendMessage(clientID, firstRequestInWaitQueue.sequenceNumber, "LOCKED", tmpSctpChannel);
									System.out.println("Sending Locked message to Node: " +clientID);
									writeNodeStatus("Sending Locked message to Node: " +clientID);
									//changeCounterValue(clientID, firstRequestInWaitQueue.sequenceNumber, "LOCKED");
								}
								else{
									//Send message to self!
									//System.out.println("Received a new Locked message from Node: " +firstRequestInWaitQueue.nodeID);
									changeCounterValue(firstRequestInWaitQueue.nodeID, firstRequestInWaitQueue.sequenceNumber, "LOCKED");
									//System.out.println("Changing the locked status for : " +firstRequestInWaitQueue.nodeID);
									//Look for the quorum set list and update the status 
									setQuorumReqSetStatus(firstRequestInWaitQueue.nodeID, "LOCKED");
									System.out.println("Sending Locked message to Node: " +firstRequestInWaitQueue.nodeID);
									writeNodeStatus("Sending Locked message to Node: " +firstRequestInWaitQueue.nodeID);
									//If all are locked enter the critical section
									if(areAllQuorumSetLocked()){
										clearInquireQueue();
										//Enter into critical section.
										System.out.println("-----------------------------------");
										System.out.println("| Entering into Critical Section 2 |");
										System.out.println("-----------------------------------");
										
										
										writeNodeStatus("-----------------------------------");
										writeNodeStatus("| Entering into Critical Section(2) with Sequence Number: "+tmpCurrentProcessing.sequenceNumber+" |");
										writeNodeStatus("-----------------------------------");
										
										//This is a blocking call; so no new messages will be read until the node comes out of critical section.
										try{
											changeTimeStampOfRequest("END");
											ProcessingObject requestQueueObject = getRequestQueueObject(tmpCurrentProcessing.nodeID, tmpCurrentProcessing.sequenceNumber);
											Use_Critical_Section(requestQueueObject.nodeID, requestQueueObject.sequenceNumber, requestQueueObject.startTime, requestQueueObject.endTime);
											Thread.sleep(5000);
										}
										catch(Exception e){
											System.out.println("Received an exception in Locked Message");
										}
										//Send a Release broadcast to quorumReqSet
										//System.out.println("Sending Release message to all quorum sets");
										sendReqOrReleaseMessage("RELEASE",tmpCurrentProcessing.sequenceNumber);
										if(getWaitQueueSize() > 0){
											ProcessingObject firstReqInWaitQueue = popWaitQueue();
											if(firstReqInWaitQueue!= null){
												setNodeLockStatus(true);
												setcurrentProcessingNode(firstReqInWaitQueue.nodeID, firstReqInWaitQueue.sequenceNumber, false, false);
												//check whom to send this message!
												if(firstReqInWaitQueue.nodeID != this.mynodeId){
													clientID= firstReqInWaitQueue.nodeID;
													SctpChannel tmpSctpChannel = getSctpChannel(String.valueOf(clientID));
													sendMessage(clientID, firstReqInWaitQueue.sequenceNumber, "LOCKED", tmpSctpChannel);
													System.out.println("Sending Locked message to Node : " +clientID);
													writeNodeStatus("Sending Locked message to Node : " +clientID);
													if(numberOfCSReqRemained(this.mynodeId) > 0 ){
														//Then generate a new request!
														sendReqOrReleaseMessage("REQUEST",0);
													}
												}
											}
										}
										else if(numberOfCSReqRemained(this.mynodeId) > 0 ){
											//Then generate a new request!
											sendReqOrReleaseMessage("REQUEST",0);
										}
									}
								}
							}
						}
						else{
							if(numberOfCSReqRemained(this.mynodeId) > 0 ){
								//Then generate a new request!
								sendReqOrReleaseMessage("REQUEST",0);
							}
							else{
								//Change the status of the node to unlocked!
								//System.out.println("This Node has nothing to do!" );
								//and wait for a new one.
								setNodeLockStatus(false);
								setcurrentProcessingNode(-1, -1, false,false);
							}

						}				
						clearInquireQueue();
					}
					
					//Case 6: Receive a kill message from Analyzed Node
					if(areAllReqMessagesReceived()){
						try{Thread.sleep(3000);}catch(Exception e){}
						System.out.println("----------------------------------------------------------------");
						System.out.println("| Killing Node "+this.mynodeId+" since all the request have been fulfilled! |");
						System.out.println("----------------------------------------------------------------");
						receive = false;
						
						
						writeNodeStatus("----------------------------------------------------------------");
						writeNodeStatus("| Killing Node "+this.mynodeId+" since all the request have been fulfilled! |");
						writeNodeStatus("----------------------------------------------------------------");

						System.out.println("Shutting down the Node in... ");
						writeNodeStatus("Shutting down the Node in... ");
						loopVariable=5;
						while (loopVariable > 0){
							Thread.sleep(1000);
							System.out.print(+loopVariable+ " ");
							writeNodeStatus(+loopVariable+ " ");
							loopVariable--;
						}
						System.out.println();				
						System.exit(0);
					}
				}
				}
			}
			catch(Exception e){
				System.out.println("Exception in receiveing ifs!");
				e.printStackTrace();
			}
			//System.out.println("----------------------------------------------------");
			try{Thread.sleep(1000);}catch(Exception e){}
		}
	}
	
	
	
	public static void main(String [] args)
	{
		//New Main!
		final Project2 p=new Project2(Integer.parseInt(args[0]));
		p.Read_Config();
		p.Read_quorum_config();
		Thread ServerStarter1=new Thread(new Runnable(){public void run(){p.StartServer();}});
		ServerStarter1.start();
		try { Thread.sleep(1000);} catch (InterruptedException ie) {}
		p.Connect_to_quorumMembers(Integer.parseInt(args[0]));
		p.print_quorum_connection_to_file();
		try { Thread.sleep(5000);} catch (InterruptedException ie) {}
		
		if(p.Check_intersection()){
			System.out.println("Intersection property is satisfied");
			p.printQuorumConnections();
			System.out.println("");
			System.out.println("");
			p.printquorumMembersRequest();
			try { Thread.sleep(2000);} catch (InterruptedException ie) {}
			System.out.println("");
			if(p.quorumMembersRequest.get(0)[1] > 0){
				System.out.println("I can send new Request!! ");
				Thread newCSReq = new Thread(new Runnable(){public void run(){try{
					p.sendReqOrReleaseMessage("REQUEST", 0);;}catch(Exception e){System.out.println("Exception in Sender thread!");}}});
			newCSReq.start();
			}
			else{
				System.out.println("I cann't send new Request!! ");
			}
			try { Thread.sleep(2000);} catch (InterruptedException ie) {}
			Thread receiverThread = new Thread(new Runnable(){public void run()
			{try{p.receiverFunction();}catch(Exception e){System.out.println("Exception in receiver thread!");}}});
			receiverThread.start();

		}
		else{
			System.out.println("Intersection property is NOT satisfied");
			System.out.println("So Shutting down the node in... ");
			int loopVariable=5;
			while (loopVariable > 0){
				try { Thread.sleep(1000);} catch (InterruptedException ie) {}
				System.out.print(+loopVariable+ " ");
				loopVariable--;
			}
			System.out.println();				
			System.exit(0);
		}
	}
}