import java.util.*;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/*
 * @author Yusuf Ziya Dilek
 * Student ID: 21501462
 * Client Side
 * */

public class JavaSender {	
	public static long lastSegmentControlValue;	
	
	public static void main(String[] args) throws IOException {
		
		String host = "127.0.0.1";
		String locationOfTheImage= args[0];	
		int controlPort = Integer.parseInt(args[1]);
		int N = Integer.parseInt(args[2]);
		int timeout = Integer.parseInt(args[3]);
		int mss =1022;
		int[] tracker = new int[N];
		
		
		// Reading the image to be sent			
		byte[] image = null;
		Path filePath = Paths.get(locationOfTheImage);		
		try {
			image = Files.readAllBytes(filePath);
		} catch (IOException e) {			
			System.out.println(e);
		}
		
		int totalPackets = (int) Math.ceil((double) image.length / mss);
		lastSegmentControlValue = (totalPackets-1)*mss;
		
		// Datagram socket object in order to carry the data (UDP)	    
	    DatagramSocket cSocket = null;
		try {
			cSocket = new DatagramSocket();
		} catch (SocketException e) {
			System.out.println(e);
		}
		
		InetAddress ip = null;
		try {
			ip = InetAddress.getByName(host);
		} catch (UnknownHostException e) {
			System.out.println(e);
		}				
		
		// Variables to be used inside the sending loop
		int currentIndex = 1, seqAck = -1, m = 0;		
		long startTime = System.currentTimeMillis();
		 
		// Going in to the sending loop
		while(mss*(currentIndex-1) < image.length) {			
			for (m = 0; m < N; m++) {// sending, window size is N
				if (((currentIndex-1) * mss) > image.length)
					break;
				if (tracker[m] == 2) {
					currentIndex++;
					continue;
				}
								
				byte[] header = getHeader(currentIndex);
				
				byte[] dataBytes = new byte[1022];
				int dt = 0;
				for(int k = mss*(currentIndex-1); k < mss*(currentIndex); k++ ) {
					if(k < lastSegmentControlValue)
						dataBytes[dt]= image[k];
					dt++;
				}
				
				byte[] packetToSend = new byte[header.length + dataBytes.length];
				
				for (int i = 0, j = 0; i < packetToSend.length; i++) { 
					// Adding header then data into the packetToSend
					if (i < header.length)
						packetToSend[i] = header[i];
					else {
						packetToSend[i] = dataBytes[j];
						j++;
					}
				}
				
				DatagramPacket toReceiver = new DatagramPacket(packetToSend, packetToSend.length, ip, controlPort);
				try {
					cSocket.send(toReceiver);
					System.out.println("Packet sent : " + currentIndex);
					tracker[m] = 1;
					currentIndex++;
					
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			System.out.println("current index: " + currentIndex + " with m: " + m);
			// in receiving mode			
			byte[] receive = new byte[2];
			DatagramPacket fromReceiver = new DatagramPacket(receive, receive.length);
			boolean flag = true;

			currentIndex = currentIndex - m;
			try {
				cSocket.setSoTimeout(timeout);
				while (flag) {
					cSocket.receive(fromReceiver);
					seqAck = getAckValue(fromReceiver.getData());
					System.out.println("Ack received for : " + seqAck);
					if (seqAck != -1) { 
						int index = seqAck - currentIndex;
						tracker[index] = 2;
						if (index == 0) {
							while(tracker[index]==2){
							for (int i = 1; i < N; i++) {
								tracker[i - 1] = tracker[i];
							}
							tracker[N - 1] = -1;
							currentIndex++;
							}
						}
					}
				}
			} catch (SocketTimeoutException ste) {
				System.out.println("Timeout on sequence number = " + seqAck);
			}
			
		}		
		
		/// -- Ending Sequence -- ///		
		byte[] endSeqBytes = new byte[2];
		DatagramPacket pckt = new DatagramPacket(endSeqBytes, endSeqBytes.length, ip, controlPort);
		cSocket.send(pckt);					
		
		long endingTime = System.currentTimeMillis();
		System.out.println("Time of transfer: " + (endingTime - startTime));   
	}	
	
	public static byte[] getHeader(int sequence) {		
		byte[] header = new byte[2];
		
		int  i = sequence;		
		header[0] = (byte) (i >> 8);
		header[1] = (byte) (i >> 0);	
		
		return header;
	}	
	
	public static int getAckValue(byte[] data) {
		return ((data[0] & 0xff) << 8) | (data[1] & 0xff);
	}
}