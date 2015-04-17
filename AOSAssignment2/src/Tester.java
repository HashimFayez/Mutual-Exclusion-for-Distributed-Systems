//Tester node achieves the following
// 1) Check whether only  one node enter CS
// 2) calculate message complexity
//package Tester;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.nio.channels.FileLock;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class Tester
{
	 public static boolean isentered=false;
	 public static int nodeid;
	 public static String line;
	 
	 
	public void calcMsgComplexity() throws IOException
	 {
		 
		 int noofnodes=Node_count()-3;
		 //we can add total no of requests here in the system
		 //ArrayList<String> list=new ArrayList<>();
		 HashMap<String,Integer> hm = new HashMap<String,Integer>(); 
		 String[] names = new String[noofnodes];
		 String file_name = "Message_Counts_Node_";
		 for(int i=0;i<noofnodes;i++)
		 {
			 names[i] = file_name+i;
			 //names[i]="C:\\Users\\Gopal\\Workspaces\\AOS\\AOS\\src\\tester\\Message_Counts_Node_"+i+".txt";
		 }
		 
		 for(String fname:names)
		 {
			 BufferedReader reader = new BufferedReader(new FileReader(fname));
			 
			 String linerd=null;
			 try
			 {
				 while ((linerd = reader.readLine()) != null)
				 {
					 String[] partrd=linerd.split(",");
					 String reqid=partrd[0]+"_"+partrd[1];
					 int a=Integer.parseInt(partrd[2]);
					 int b=Integer.parseInt(partrd[3]);
					 int c=Integer.parseInt(partrd[4]);
					 int d=Integer.parseInt(partrd[5]);
					 int e=Integer.parseInt(partrd[6]);
					 int counters=a+b+c+d+e;
					 //if ((list.contains(reqid))==false)
					 //{
					 //System.out.println("value added");
					 //list.add(reqid);
					 //}
					 //System.out.println("enter"+counters);
					 if(hm.containsKey(reqid))
					 {
					  int exist=hm.get(reqid);
					  int update=exist+counters;
					  for(Entry<String, Integer> entry : hm.entrySet())
					  {
						  if(entry.getKey().equals(reqid))
						  {
							  entry.setValue(update);
						  }
					  }
					  
					 }
					 else
					 {
					 hm.put(reqid, counters);
					 }
					  			 
				 }
				
				 
			 }
			 catch(Exception e)
			 {
				 
			 }
			 reader.close();
			 
		 }
		 System.out.println("Message Complexity");
		 System.out.println("Total Number of messages exchanged per request");
		 Integer lowerBound= Integer.MAX_VALUE, upperBound=Integer.MIN_VALUE,temp,total=0;
		 for (Map.Entry<String,Integer> entry : hm.entrySet()) 
		 {
			    System.out.println(entry.getKey() + ", " + entry.getValue());
			    temp=(Integer)entry.getValue();
			    total+=temp;
                if(temp < lowerBound){
                    lowerBound=temp;
                    }
                if(temp > upperBound){
                    upperBound=temp;
                }
		 }
     System.out.println("Lower Bound of Messages exchanged is : "+lowerBound);
     System.out.println("Upper Bound of Messages exchanged is : "+upperBound);
     System.out.println("Total messages exchanged are : "+total);
     System.out.println("Total number of requests is : "+hm.size());
     System.out.println("Average message exchanged are : "+(double)total/hm.size());
}
		 
	
	
	
	
	 public void testCSfile() throws IOException
	 {
		 //format used 
		 //String TimeStamp = new java.util.Date().toString();
		 //System.out.println(TimeStamp);
		 String file_name = "Critical_Section.txt";
		 FileInputStream file=new FileInputStream(file_name);
		 //FileInputStream file=new FileInputStream("C:\\Users\\Gopal\\Workspaces\\AOS\\AOS\\src\\tester\\cs_entered.txt");
		 
		 FileLock lock=file.getChannel().tryLock(0L,Long.MAX_VALUE,true);	
		 BufferedReader br = new BufferedReader(new InputStreamReader(file, "UTF-8"));
		 String[] parts=new String[4];		 
		 try
		 {
			 
			 System.out.println("\nVerifying if only one node is entering CS at a time");
			 while ((line = br.readLine()) != null)
			 {
				 
			    parts=line.split(",");
			    
			    if(parts[2].equals("ENTER")==true)
			    {
			    	isentered=true;
			    	nodeid=Integer.parseInt(parts[0]);
			    			    	
			    }
			    if(parts[2].equals("EXIT")==true)
			    {
			    				    	
			    	isentered=false;
			    	if(nodeid==Integer.parseInt(parts[0]))
			    	{
			    		System.out.println("node "+Integer.parseInt(parts[0])+" entered and exited CS successfully");
			    	}
			    	else
			    	{
			    		System.out.println("Error: node "+nodeid+" and node "+Integer.parseInt(parts[0])+" entered concurrently");
			    	}
			    	nodeid=0;
			    	
			    }
			    
			 }//reading lines end
			 
			 
			 
		 }
		 catch(Exception e)
		 {
			e.printStackTrace();
		 }
		 finally
		 {
			 
			 lock.release();
			 file.close();
			 
		 }
		 

	 }
	 	public int Node_count()
	 	{
		 BufferedReader reader;
		 //String line,myQuorum;
		 //ArrayList<String> AllQuorums=new ArrayList<String>();
		    int NodeCount=0;
		    String file_name = "quorumconfig.txt";
		 try{
			 FileInputStream FinStream=new FileInputStream(file_name);
		 //FileInputStream FinStream=new FileInputStream("C:\\Users\\Gopal\\Workspaces\\AOS\\AOS\\src\\tester\\quorumconfig.txt");
		 java.nio.channels.FileLock lock = FinStream.getChannel().tryLock(0L, Long.MAX_VALUE, true);
		 reader=new BufferedReader(new InputStreamReader(FinStream, "UTF-8"));
		 	
		 try{
		 while((line=reader.readLine())!=null)
		 NodeCount+=line.split(" ").length;
		 }catch(Exception e){}finally {lock.release();FinStream.close();}
		 }catch(Exception e){e.printStackTrace();}
		 return NodeCount;
		 }
	 	 public void calcSystemThroughput() throws IOException
	 	 {
	 		 String file_name = "Critical_Section.txt";
	 		 System.out.println("\nCalculating system throughput");
	 		 
	 		FileInputStream file=new FileInputStream(file_name);
	 		//FileInputStream file=new FileInputStream("C:\\Users\\Gopal\\Workspaces\\AOS\\AOS\\src\\tester\\cs_entered.txt");
	 		 
	 		FileInputStream file1=new FileInputStream(file_name);
	 		//FileInputStream file1=new FileInputStream("C:\\Users\\Gopal\\Workspaces\\AOS\\AOS\\src\\tester\\cs_entered.txt");
	 		 
	 		FileReader fr=new FileReader(file_name);
	 		//FileReader fr=new FileReader("C:\\Users\\Gopal\\Workspaces\\AOS\\AOS\\src\\tester\\cs_entered.txt");
	 		 
	 		 FileLock lock=file.getChannel().tryLock(0L,Long.MAX_VALUE,true);	
			 BufferedReader br2 = new BufferedReader(new InputStreamReader(file, "UTF-8"));
			 BufferedReader br3 = new BufferedReader(new InputStreamReader(file1, "UTF-8"));
			 String[] parts=new String[4];	
			 int total_lines=0;
			 int linenumber = 0;
			 //String firstreq = Long.MAX_VALUE ,lastreq = "0" ,firstres =Long.MAX_VALUE ,lastres="0";
			Long firstreq = Long.MAX_VALUE ,lastreq = Long.MIN_VALUE ,firstres =Long.MAX_VALUE ,lastres=Long.MIN_VALUE;
			 try
			 {
				 LineNumberReader lnr = new LineNumberReader(fr);
				 
				 //System.out.println("total lines"+linenumber);
				 while((br3.readLine())!=null)
				 {
					 linenumber++;
				 }
				 total_lines=linenumber;
				 
				 br3.close();
				 
				 while ((line = br2.readLine()) != null )
				 {
					 lnr.readLine();
					 parts=line.split(",");
				    
				    if(parts[2].equals("ENTER")==true)
				    {
				    	if((firstreq) > Long.parseLong(parts[3]))
				    	{
				    		firstreq=Long.parseLong(parts[3]);
				    		
				    	}
				    	if((lastreq) < Long.parseLong(parts[3]))
				    	{
				    		lastreq=Long.parseLong(parts[3]);
				    	}
				    }
				    if(parts[2].equals("EXIT")==true)
				    {
				    	if((firstres) > Long.parseLong(parts[3]))
				    	{
				    		firstres=Long.parseLong(parts[3]);
				    		
				    	}
				    	if((lastres) < Long.parseLong(parts[3]))
				    	{
				    		lastres=Long.parseLong(parts[3]);
				    	}
				    }
				 }

                                 long Req_count=total_lines/2;
                                 long firstreql=(firstreq);
                                 long lastreql=(lastreq);
                                 long firstresl=(firstres);
                                 long lastresl=(lastres);
                                 long req_diff=lastreql-firstreql;
                                 long res_diff=lastresl-firstresl;
                                 long processing_time=lastresl-firstreql;
                              //   System.out.println("Difference between last and first request "+(double)req_diff/1000+"  seconds");
                               //  System.out.println("Difference between last and first response  "+(double)res_diff/1000+" seconds");
                                 double resp_time=(double)res_diff/(Req_count*1000);
                                 System.out.println("Response time is "+(resp_time)+" request per second");
                                 System.out.println("System throughput is "+(double)Req_count/processing_time*1000+" requests/sec ");

			 }
			 catch(Exception e)
			 {
				 e.printStackTrace();
			 }
			 finally
			 {
				 lock.release();
				 file.close();
			 }
				 
	 	 }
		 public static void main(String[] args) throws IOException
		 {
			Tester t=new Tester();
			t.calcMsgComplexity();
			t.testCSfile();
			t.calcSystemThroughput();
		
	 }
	 
}
