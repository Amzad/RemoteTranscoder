import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.regex.Pattern;

public class Client extends Thread {
	String serverIP;
	Socket s = null;
	boolean connectionValid = true;
	FileObject object;
	FileObject file;
	static String sendDir;
	String recDir;
	boolean useServer = false;
	static Queue<FileObject> fileQueue = new LinkedList<>();
	static Queue<FileObject> returnQueue = new LinkedList<>();
	ObjectOutputStream out;

	public Client(String ip, String send, String rec, boolean server) {
		serverIP = ip;
		sendDir = send;
		recDir = rec;
		useServer = server;
		
	}

	public void run() {
		createConnection(); // Connect to server
		if (s != null || useServer == false) {
			monitorSending(); // Monitor folder for new files
			while (connectionValid) {
				if(fileQueue.size() > 0) {
					object = fileQueue.remove(); // Remove file from queue to an object
					AnimeFile temp = new AnimeFile(object.thisFile);
					
					if (temp.extension.equals("mp4")) { // If it's an MP4
						if (Database.aList.contains(temp.animeTitle)) {
							// Existing Title
							temp.directory = Database.getAnimeDirectory(temp.animeTitle);
							boolean renamed = FileOperations.renameAndMove(temp);
							if (renamed) System.out.println("Done!");
							Database.insertNewEpisode(temp);
							
						} else {
							// New Title
							
							
							
						}
					// If it's not an MP4, send to server if available
					} else { 
						if (useServer) {
							sendRequest(); // Send file
						} else { // If Local
							// Move to pending folder
							print(temp.animeTitle + " added to pending folder");

						}
						
					}
					
					
					
				}
				else {
					try {
						Thread.sleep(5000);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}

		}
	}

	private void monitorSending() {
		if (useServer) {
			new Thread(new FolderWatcher(sendDir, 1)).start();
		} else {
			new Thread(new FolderWatcher(sendDir, 0)).start();
		}
		
	}
	
	
	private void createConnection() {
		if (useServer == true) {
			try {
				s = new Socket(serverIP, 1337);
				print("Connected to " + serverIP);
			} catch (UnknownHostException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (ConnectException e) {
				print("Connection refused by server");
				GUI.stopButton();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		} else {
			print("Local Mode Only");
		}

	}
	
	
	private void sendRequest() {
		try {
			PrintWriter out = new PrintWriter(s.getOutputStream());
			BufferedReader bReader = new BufferedReader(new InputStreamReader(s.getInputStream()));
			String reply;
			out.println("PermissionToSendObject");
			out.flush();
			boolean waiting = true;
			while (waiting) {
				// Wait for valid response
				while ((reply = bReader.readLine()) == null) {
				}
				
				if (reply.equals("PermissionGranted")) {
					print("Sending metadata");
					sendObject();
				} else

				if (reply.equals("SendFile")) {
					if (sendFile(object)) {
						print(object.name + " sent. Waiting for response.");
					}
				} else
					
				if (reply.equals("FileReady")) {
					if (isValidObject()) {
						receiveFile();
					}
				} else	

				if (reply.equals("InvalidObject")) {
					print("Checksum mismatch. Sending object again");
					sendObject();
				}

			}
			

		} catch (SocketException e) {
			print("Server closed the connection");
			connectionValid = false;
			e.printStackTrace();
		}
		catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

	public void sendObject() {
		try {	
			out = new ObjectOutputStream(s.getOutputStream());
			out.writeObject(object);
			out.flush();
			print("Client: " + object.name);
			print("Object sent!");
			
		} catch (SocketException e) {
			print("Socket Remotely Closed");
		}
		catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
	
	private boolean isValidObject() {
		try {
			ObjectInputStream in = new ObjectInputStream(s.getInputStream());
			FileObject object = (FileObject) in.readObject();

			String checksum = object.name + object.size;
			if (checksum.hashCode() == object.checkSum) {
				print("Valid object. Requesting file");
				return true;

			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			print("Connection abbruptedly closed");
			try {
				s.close();
				return false;
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		} catch (ClassNotFoundException e) {
			print("Invalid Class Received.");
			try {
				s.close();
				return false;
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}	
		return false;
	}
	
	private boolean sendFile(FileObject file) {
		try {

			if (file.thisFile.exists() && !file.thisFile.isDirectory()) {
				Thread t = new FileTransfer(file, s, serverIP);
				t.start();
				t.join();
				return true;
			} else {
				print("File not found");
			}

		} catch (InterruptedException e) {
			print("Thread interrupted");
			e.printStackTrace();
		}
		return false;

	}
	
	private boolean receiveFile() {
		try {
				Thread t = new FileTransfer(recDir, s, serverIP);
				t.start();
				t.join();
				return true;
		} catch (InterruptedException e) {
			print("Thread interrupted");
			e.printStackTrace();
		}
		return false;
	}

	private void print(String input) {
		GUI.print(input);
	}
	
	public static boolean validateIP(String IP) {
		Pattern PATTERN = Pattern.compile("^(([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}([01]?\\d\\d?|2[0-4]\\d|25[0-5])$");
		return PATTERN.matcher(IP).matches();
	}

}
