import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;


public class FileManager {
	
	public int nodeNumber;
	public HashMap<Integer,String> nodeMap = new HashMap<Integer,String>();
	
	public int N;
	public int VN;
	public int RU;
	public int DS;
	
	public int numberFiles=0;
	public Object syncLock = new Object();
	public volatile boolean[] fileWriteLock;
	public volatile boolean[] fileReadLock;
	public volatile boolean gotReplies = false;
	public int serverPort;
	
	public ArrayList<FileDetails> listOfFDs= new ArrayList<FileDetails>();
	public ArrayList<Integer>[] listReadRequests;
	public FileDetails[] files;
	int count = 0;
	//write to file
	//write(File f);
	     //wait to obtain lock on atleast w machines
	     //if(  )
	
	
	//read from file
	//read(File f);
	
	public FileManager(int nodeno){
		parseFile("config.txt");
		this.nodeNumber = nodeno;
		files = new FileDetails[numberFiles];
		fileWriteLock = new boolean[numberFiles];
		fileReadLock = new boolean[numberFiles];
		listReadRequests = new ArrayList[numberFiles];
		initializeDS();
		String add = nodeMap.get(nodeNumber);
		String[] ips = add.split(":");
		Server serv = new Server(this,Integer.parseInt(ips[1]));
		Thread serverThread = new Thread(serv);
		serverThread.start();
		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void parseFile(String fileName){
		//parse config file and set values
				BufferedReader br;
				int totalNumber = -1;
			//	E = SD = CSRequests = -1;
				int linecount = 0;
				try {
					br = new BufferedReader(new FileReader(fileName));
					String line;
					while((line = br.readLine()) != null){
						if(line.length() == 0);
						else if(line.charAt(0) == '#');
						else if(totalNumber == -1){
							totalNumber = Integer.parseInt(line.trim());
							linecount = totalNumber;
							//System.out.println(linecount);
						}
						else if(linecount>0){
							line = line.trim().replaceAll("(\t)+", ",");
							//System.out.println(line);
							//Initializing the Hashmap with the node configuration
							nodeMap.put(Integer.parseInt(line.split(",")[0]), line.split(",")[1] + ":" + line.split(",")[2]);
							linecount--;
						}
//						else if(CSRequests == -1)
//							CSRequests = Integer.parseInt(line.trim());
//						else if(SD == -1)
//							SD = Integer.parseInt(line.trim());
//						else if(E == -1)
//							E = Integer.parseInt(line.trim());
						N=totalNumber;
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
					
	}
	
	public void initializeDS(){
		for(int i=0;i<numberFiles;i++){
			files[i]= new FileDetails(i,0,0,nodeNumber,null,nodeNumber);
			listReadRequests[i] = new ArrayList<Integer>();
		}
	}
	
	public boolean write_enter(int fileNo, String addend) throws IOException{
		if(askWritePermissions(fileNo)){
			write(addend,fileNo);
			return true;
		}
		
		return false;
		
	}
	
	public String read_enter(int fileNo) throws IOException{
		if(askReadPermissions(fileNo)){
			return read(fileNo);
		}
		return null;
	}
	
	public boolean askWritePermissions(int fileNumber) throws IOException{
		do{
			
			if(acquireWriteLock(fileNumber)){
				listOfFDs.add(files[fileNumber]);
				for(int i=0; i<N; i++){
					if(i!=nodeNumber)
					    sendWriteRequest(i);
				}
				//wait untill it got all replies
				while(!gotReplies){
					
				}	
				//check for necessary conditions
				if(checkForPermissions()){
					return true;
				}else{
					synchronized(syncLock){
					    fileWriteLock[fileNumber]=false;
					}
					releaseLocks();
					//waitForExpo();
				}
			}
		}while(true);
		
	}
	
	public boolean askReadPermissions(int fileNumb) throws IOException{
		do{
			
			if(acquireReadLock(fileNumb)){	
				listReadRequests[fileNumb].add(nodeNumber);
				listOfFDs.add(files[fileNumb]);
				for(int i=0; i<N; i++){
					if(i!=nodeNumber)
					    sendReadRequest(i);
				}
				//wait untill it got all replies
				while(!gotReplies){
					
				}	
				//check for necessary conditions
				if(checkForPermissions()){
					return true;
				}else{
					synchronized(syncLock){
					    listReadRequests[fileNumb].remove(nodeNumber);
					    if(listReadRequests[fileNumb].isEmpty())
					    	fileReadLock[fileNumb] = false;
					}
					releaseLocks();
					//waitForExpo();
				}
			}
		}while(true);
		
	}	
	
	public boolean checkForPermissions(){
		int maxVN=0,maxRU=0, maxVNCount=0;
		
			for(FileDetails fd:listOfFDs){
				if(fd.VN>maxVN){
					maxVN=fd.VN;
					maxRU=fd.RU;
				}
					
			
			for(FileDetails f:listOfFDs){
			    if(f.VN==maxVN)
			    	maxVNCount++;
			    
			}
			
			if(maxVNCount>maxRU/2){
				
				return true;
			}
		}
		
		return false;
	}
	
	public void write(String addend,int fileNo) throws IOException{
		int maxVN=0;
		FileDetails maxFD=null;
		for(FileDetails fd:listOfFDs){
			if(fd.VN>=maxVN){
				maxFD = fd;
			}
		}
		
		byte[] fileArray = maxFD.byteFile;
		BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream("Machine"+(nodeNumber+1)+"/file"+fileNo+".txt"));
		//bos.write(fileArray);
		byte[] strBytes = addend.getBytes();
		byte[] bytesToCopy = new byte[fileArray.length+strBytes.length];
		System.arraycopy(fileArray, 0, bytesToCopy, 0, fileArray.length);
		System.arraycopy(strBytes, 0, bytesToCopy, fileArray.length, strBytes.length);
		
		bos.write(bytesToCopy);
		bos.close();
		
		byte[] fileBytes = formFile(fileNo);
		FileDetails updated = new FileDetails(fileNo,maxVN+1,listOfFDs.size(),nodeNumber,fileBytes,nodeNumber);
		files[fileNo]=updated;
		for(FileDetails fd:listOfFDs){
			if(fd.nodeNo!=nodeNumber)
			    sendWriteLockRelease(updated, fd.nodeNo);
		}
		
		synchronized(syncLock){
		    fileWriteLock[fileNo]=false;
		}
		listOfFDs.clear();
		gotReplies=false;
		count=0;
	}
	
	public String read(int fileNumb) throws IOException{
		int maxVN=0;
		FileDetails maxFD=null;
		for(FileDetails fd:listOfFDs){
			if(fd.VN>maxVN){
				maxFD = fd;
			}
		}
		
		byte[] fileArray = maxFD.byteFile;
        String readString = new String(fileArray);
        
		for(FileDetails fd:listOfFDs){
			if(fd.nodeNo!=nodeNumber)
			    sendReadRelease(fd.nodeNo);
		}
		listReadRequests[fileNumb].remove(nodeNumber);
		if(listReadRequests[fileNumb].isEmpty())
			fileReadLock[fileNumb] = false;
		listOfFDs.clear();
		gotReplies=false;
		count=0;       
        
        return readString;
	}
	
	public byte[] formFile(int fileNumb) throws IOException{
		File myFile = new File ("Machine"+(nodeNumber+1)+"/file"+fileNumb+".txt");
        byte [] mybytearray  = new byte [(int)myFile.length()];
        FileInputStream fis = new FileInputStream(myFile);
        BufferedInputStream bis = new BufferedInputStream(fis);
        bis.read(mybytearray,0,mybytearray.length);
        bis.close();
        
        return mybytearray;
	}
	
	public void updateFile(byte[] fileBytes, int fileNumb) throws IOException{
		BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream("Machine"+(nodeNumber+1)+"/file"+fileNumb+".txt"));
		bos.write(fileBytes);
		bos.close();
	}

	public void releaseLocks(){
		for(FileDetails fd : listOfFDs){
			sendReleaseLocks(fd.nodeNo);
		}
		listOfFDs.clear();
		gotReplies=false;
		count=0;
	}
	
	public void sendReply(FileDetails fd, boolean renderLock, int destination){
		try
		{
			String address = nodeMap.get(destination);
			String[] ips = address.split(":");
			//Create a client socket and connect to server at 127.0.0.1 port 5000
			Socket clientSocket = new Socket(ips[0],Integer.parseInt(ips[1]));
			
           // System.out.println("sent request to "+ destination+" from "+ nodeNo);
			ObjectOutputStream oos = new ObjectOutputStream(clientSocket.getOutputStream());
			Message reqMsg = new Message("REP",nodeNumber,fd,renderLock);
			oos.writeObject(reqMsg);
			//writer.close();
			oos.close();
			clientSocket.close();
		}
		catch(IOException ex)
		{
			ex.printStackTrace();
		}		
	}

	public void sendWriteReply(FileDetails fd, boolean renderLock, int destination){
		try
		{
			String address = nodeMap.get(destination);
			String[] ips = address.split(":");
			//Create a client socket and connect to server at 127.0.0.1 port 5000
			Socket clientSocket = new Socket(ips[0],Integer.parseInt(ips[1]));
			
           // System.out.println("sent request to "+ destination+" from "+ nodeNo);
			ObjectOutputStream oos = new ObjectOutputStream(clientSocket.getOutputStream());
			Message repMsg = new Message("WGRANT",nodeNumber,fd,renderLock);
			oos.writeObject(repMsg);
			//writer.close();
			oos.close();
			clientSocket.close();
		}
		catch(IOException ex)
		{
			ex.printStackTrace();
		}		
	}	
	
	public void sendReadReply(FileDetails fd, boolean renderLock, int destination){
		try
		{
			String address = nodeMap.get(destination);
			String[] ips = address.split(":");
			//Create a client socket and connect to server at 127.0.0.1 port 5000
			Socket clientSocket = new Socket(ips[0],Integer.parseInt(ips[1]));
			
           // System.out.println("sent request to "+ destination+" from "+ nodeNo);
			ObjectOutputStream oos = new ObjectOutputStream(clientSocket.getOutputStream());
			Message repMsg = new Message("WGRANT",nodeNumber,fd,renderLock);
			oos.writeObject(repMsg);
			//writer.close();
			oos.close();
			clientSocket.close();
		}
		catch(IOException ex)
		{
			ex.printStackTrace();
		}		
	}	
	
	public void sendNoLockReply(int destination){
		try
		{
			String address = nodeMap.get(destination);
			String[] ips = address.split(":");
			//Create a client socket and connect to server at 127.0.0.1 port 5000
			Socket clientSocket = new Socket(ips[0],Integer.parseInt(ips[1]));
			
           // System.out.println("sent request to "+ destination+" from "+ nodeNo);
			ObjectOutputStream oos = new ObjectOutputStream(clientSocket.getOutputStream());
			Message repMsg = new Message("NOGRANT",nodeNumber,null,false);
			oos.writeObject(repMsg);
			//writer.close();
			oos.close();
			clientSocket.close();
		}
		catch(IOException ex)
		{
			ex.printStackTrace();
		}		
	}
	
	public void sendRequest(int destination){
		try
		{
			String address = nodeMap.get(destination);
			String[] ips = address.split(":");
			//Create a client socket and connect to server at 127.0.0.1 port 5000
			Socket clientSocket = new Socket(ips[0],Integer.parseInt(ips[1]));
			
           // System.out.println("sent request to "+ destination+" from "+ nodeNo);
			ObjectOutputStream oos = new ObjectOutputStream(clientSocket.getOutputStream());
			Message reqMsg = new Message("REQ",nodeNumber,null,false);
			oos.writeObject(reqMsg);
			//writer.close();
			oos.close();
			clientSocket.close();
		}
		catch(IOException ex)
		{
			ex.printStackTrace();
		}
	}
	
	public void sendWriteRequest(int destination){
		try
		{
			String address = nodeMap.get(destination);
			String[] ips = address.split(":");
			//Create a client socket and connect to server at 127.0.0.1 port 5000
			Socket clientSocket = new Socket(ips[0],Integer.parseInt(ips[1]));
			
           // System.out.println("sent request to "+ destination+" from "+ nodeNo);
			ObjectOutputStream oos = new ObjectOutputStream(clientSocket.getOutputStream());
			Message reqMsg = new Message("WREQ",nodeNumber,null,false);
			oos.writeObject(reqMsg);
			//writer.close();
			oos.close();
			clientSocket.close();
		}
		catch(IOException ex)
		{
			ex.printStackTrace();
		}
	}	
	
	public void sendReadRequest(int destination){
		try
		{
			String address = nodeMap.get(destination);
			String[] ips = address.split(":");
			//Create a client socket and connect to server at 127.0.0.1 port 5000
			Socket clientSocket = new Socket(ips[0],Integer.parseInt(ips[1]));
			
           // System.out.println("sent request to "+ destination+" from "+ nodeNo);
			ObjectOutputStream oos = new ObjectOutputStream(clientSocket.getOutputStream());
			Message reqMsg = new Message("RREQ",nodeNumber,null,false);
			oos.writeObject(reqMsg);
			//writer.close();
			oos.close();
			clientSocket.close();
		}
		catch(IOException ex)
		{
			ex.printStackTrace();
		}
	}	
	//sent when write is successful
	public void sendWriteLockRelease(FileDetails fd,int destination){
		try
		{
			String address = nodeMap.get(destination);
			String[] ips = address.split(":");
			//Create a client socket and connect to server at 127.0.0.1 port 5000
			Socket clientSocket = new Socket(ips[0],Integer.parseInt(ips[1]));
			
           // System.out.println("sent request to "+ destination+" from "+ nodeNo);
			ObjectOutputStream oos = new ObjectOutputStream(clientSocket.getOutputStream());
			Message reqMsg = new Message("WRELEASE",nodeNumber,fd,false,-1);
			oos.writeObject(reqMsg);
			//writer.close();
			oos.close();
			clientSocket.close();
		}
		catch(IOException ex)
		{
			ex.printStackTrace();
		}
	}	
	
	
	public void sendReadRelease(int destination){
		try
		{
			String address = nodeMap.get(destination);
			String[] ips = address.split(":");
			//Create a client socket and connect to server at 127.0.0.1 port 5000
			Socket clientSocket = new Socket(ips[0],Integer.parseInt(ips[1]));
			
           // System.out.println("sent request to "+ destination+" from "+ nodeNo);
			ObjectOutputStream oos = new ObjectOutputStream(clientSocket.getOutputStream());
			Message reqMsg = new Message("READRELEASE",nodeNumber,null,false,-1);
			oos.writeObject(reqMsg);
			//writer.close();
			oos.close();
			clientSocket.close();
		}
		catch(IOException ex)
		{
			ex.printStackTrace();
		}
	}	
	
	//sent when write is NOT successful and when read is successful
	public void sendReleaseLocks(int destination){
		try
		{
			String address = nodeMap.get(destination);
			String[] ips = address.split(":");
			//Create a client socket and connect to server at 127.0.0.1 port 5000
			Socket clientSocket = new Socket(ips[0],Integer.parseInt(ips[1]));
			
           // System.out.println("sent request to "+ destination+" from "+ nodeNo);
			ObjectOutputStream oos = new ObjectOutputStream(clientSocket.getOutputStream());
			Message reqMsg = new Message("RELEASE",nodeNumber,null,false,-1);
			oos.writeObject(reqMsg);
			//writer.close();
			oos.close();
			clientSocket.close();
		}
		catch(IOException ex)
		{
			ex.printStackTrace();
		}
	}	
	
	public void processWriteReply(Message reply){
		if(reply.lockAcquired){
			listOfFDs.add(reply.fileDetails);
			
		}
		
		count++;
		if(count == N-1)
			gotReplies = true;
	}
	
	public void processReadReply(Message reply){
		if(reply.lockAcquired){
			listOfFDs.add(reply.fileDetails);			
		}else
			//permissions[reply.sourceNode]=false;
		
		count++;
		if(count == N-1)
			gotReplies = true;
	}	
	
	public void processWriteRequest(Message msg) throws IOException{
		if(acquireWriteLock(msg.fileNo)){
			sendWriteReply(new FileDetails(VN,RU,DS,formFile(),nodeNumber),true,msg.sourceNode);
		}else{
			sendNoLockReply(msg.sourceNode);
		}
	}
	
	public void processReadRequest(Message msg) throws IOException{
		if(acquireReadLock(msg.fileNo)){
			sendReadReply(new FileDetails(VN,RU,DS,formFile(),nodeNumber),true,msg.sourceNode);
			listReadRequests.add(msg.sourceNode);
		}else{
			sendNoLockReply(msg.sourceNode);
		}
	}
	
	public void processWriteRelease(Message msg) throws IOException{
		byte[] file = msg.fileDetails.byteFile;
		updateFile(file);
		this.VN = msg.fileDetails.VN;
		this.RU = msg.fileDetails.RU;
		this.DS = msg.fileDetails.DS;
		synchronized(syncLock){
		    fileWriteLock[msg.fileNo]=false;
		}
	}
	
	public void processReadRelease(Message msg){
		listReadRequests.remove(msg.sourceNode);
		if(listReadRequests.isEmpty())
			fileReadLock[msg.fileNo] = false;
	}
	
	public void processReleaseLock(Message msg){
		synchronized(syncLock){
			fileWriteLock[msg.fileNo] = false;
		}
		listReadRequests.remove(msg.sourceNode);
		if(listReadRequests.isEmpty())
			fileReadLock[msg.fileNo] = false;
	}
	

	public synchronized boolean acquireReadLock(int fileToLock){
		if(!fileWriteLock[fileToLock]){
			fileReadLock[fileToLock] = true;
			return true;
		}
		return false;
	}
	
	public synchronized boolean acquireWriteLock(int fileToLock){
		if(!fileWriteLock[fileToLock] && !fileReadLock[fileToLock]){
			fileWriteLock[fileToLock] = true;
			return true;
		}
		return false;
		
	}
}
