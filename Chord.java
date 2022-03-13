package dht;

import java.util.Scanner;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;

// Create node as a chord's first node or join a existing one
// Use command-line to interact with key/value in node's dht
public class Chord {
	
	private KeyValueTable<String> new_node;
	
	// Create a node as a chord's first node
	public Chord(String node_name) throws NotBoundException {
		try {
			// for the first time, create three new nodes and join
			new_node = new NodeClass<>(node_name);
			System.out.println("Chord Create Success!");
			
			// Success. Listen to input to operate table
			handle_msg();
		} catch (RemoteException e) {
			System.err.println("Fail to create the Chord.");
		}
	}
	
	// create node [new_node_name] to existing chord[ip:port] from entry node [existed_node]
	public Chord(String node_name, String existed_node, String ip, int port) {
		try {
			new_node = new NodeClass<>(node_name, existed_node, ip, port);

			// Success. Listen to input to operate table
			handle_msg();
		} catch (RemoteException e) {
			System.err.println("Error: Remote Exception.");
		} catch (NotBoundException e) {
			System.err.println("Error: RMI Registry error.");
		}
	}
	
	
	// Keeping listening inputs to handle with data, basic get/put/delete/show functions
	// "leave" input will let the node leave the chord.
	private void handle_msg() {
		System.out.println("Please enter command line (add/search/del/listAll/leave): ");
		Scanner scan = new Scanner(System.in);
		String input = scan.nextLine();
		while(!input.equals("leave")) {
			// get actions
			String[] cmd = input.split(" ");
			String action = cmd[0];
			try {
				switch(action) {
					case "add":
						String key = cmd[1];
						String value = cmd[2];
						final Lock write = new ReentrantLock();
						new Thread(){
							public void run() {
								new_node.create(key, value);
								write.lock();
								write.unlock();
							}
						}.start();
						break;
					case "search":
						key = cmd[1];
						new Thread() {
							public void run() {					
								new_node.search(key);
							}
						}.start();
						break;
					case "del":
						key = cmd[1];
						new Thread() {
							public void run() {					
								new_node.delete(key);
							}
						}.start();
						break;
					case "listAll":
						new_node.listAllKeyValue();
						break;
					default:
						System.out.println("Please enter command line (add/search/del/listAll/leave): ");
				}
				System.out.println("=============================");
			} catch (Exception e) {
				System.out.println("Can't read command line, please try again.");
			}
			input = scan.nextLine();
		}
		// leave node
		new_node.leave();
		scan.close();
	}
	
	public static void main(String[] args) throws NotBoundException {
		// get command-line "Java Chord [node_name]"
		if(args.length == 1) {
			String dht_name = args[0];
			new Chord(dht_name);
		}
		// get command-line "Java Chord [new_node_name, existed_node_name, chord_ip, port]"
		else if(args.length == 4) {
			String node_name = args[0];
			String existed_node = args[1];
			String chord_ip = args[2];
			int chord_port = Integer.parseInt(args[3]);
			new Chord(node_name, existed_node, chord_ip, chord_port);
		}
		else
			System.out.println("Incorrect input args.");
	}
}
