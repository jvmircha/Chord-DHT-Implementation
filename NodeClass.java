package dht;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public class NodeClass<E> extends UnicastRemoteObject implements NodeStruct<E>, KeyValueTable<E> {
	
	// auto-generated serialVersionUID to remember versions of a Serializable class to verify that a loaded class and the serialized object are compatible. 
	private static final long serialVersionUID = 741902889341823535L;
	private String node_name;
	private String node_id;
	
	private NodeStruct<E> predecessor;
	private NodeStruct<E> successor;
	
	// finger table and key-value table of the node
	private HashMap<String, E> key_value_table = new HashMap<>();
	private Map<String, NodeStruct<E>> finger_table = new LinkedHashMap<>();
	
	private static final int N = 1048576; //1MB
	public static final int DEFAULT_PORT = 1099;
	Registry registry;
	
	public NodeClass(String node_name) throws RemoteException {
		
		// set the first node
		this.node_name = node_name;
		node_id = generateID(node_name, N);
		successor = this;
		predecessor = this;
		Registry registry;
		System.out.println("Curr Node: " + node_name + " / Pred Node: " + predecessor.getName() +  " / Succ Node: " + successor.getName());
		System.out.println("Current Node List: " + getAllNodesName());
		
		try {
			// set the property to the ip of first node, instead of getting 127.0.0.1
			System.setProperty("java.rmi.server.hostname", "172.31.159.230");
			registry = LocateRegistry.createRegistry(DEFAULT_PORT);
		} catch (Exception e) {
			registry = LocateRegistry.getRegistry(DEFAULT_PORT);
		}
		registry.rebind(node_name, this);
	}
		
	@SuppressWarnings("unchecked")
	public NodeClass(String name, String existed_name, String ip, int port) throws RemoteException, NotBoundException {
		this(name);
		registry = LocateRegistry.getRegistry(ip, port);
		
		// add new node from the existing node
		// search for the given existing node
		NodeStruct<E> existed_node = (NodeStruct<E>) registry.lookup(existed_name);
		join(existed_node);
	}
	
	@Override
	public void join(NodeStruct<E> existed_node) {
		try {
			boolean joined = false;
			// find the right position by id, and insert the node to that position
			while(!joined) {
				NodeStruct<E> pred = existed_node.getPredecessor();
				String curr_key = existed_node.getID();
				String pred_key = pred.getID();
				
				if(isBetween(node_id, pred_key, curr_key)) {
					pred.setAsSuccessor(this);
					existed_node.setAsPredecessor(this);
					setAsSuccessor(existed_node);
					setAsPredecessor(pred);
					
					// reallocate the data, get responsible for data which id is between pred_id and node_id
					Map<String, E> get_from_pred = successor.reallocate(pred_key, node_id);
					for(String k : get_from_pred.keySet())
						key_value_table.put(k, get_from_pred.get(k));
					
					System.out.println("Node Join Success!");
					System.out.println("Curr Node: " + node_name + " / Pred Node: " + pred.getName() +" / Succ Node: " + existed_node.getName());
					joined = true;
					
				} else
					// go to the next successor if curr isn't the right position
					existed_node = existed_node.getSuccessor();
			}
			
			// update all existing nodes' finger table
			updateRouting();
			System.out.println("Current Node List: " + getAllNodesName());
			System.out.println("Routing Table Updated.");
			
		} catch(RemoteException e) {
			System.err.println("Joining Error on Node " + existed_node);
			e.printStackTrace();
		}
	}
	
	
	public void leave() {
		try {
			// transfer curr node's data to it's succ to take care
			for(String k : key_value_table.keySet())
				if(successor.getValueByKey(k) == null) {
					successor.addKeyValue(k, key_value_table.get(k));
				}
			System.out.println("Leaving Node " + node_name + ". Success Passing its data to it's successor.");
			
			//update succ/pred between neighbours
			successor.setAsPredecessor(predecessor);
			predecessor.setAsSuccessor(successor);
			successor = this;
			predecessor = this;
			updateRouting();
			System.out.println("Node left successfully.");
			
			
		} catch(RemoteException e) {
			System.err.println("Error leaving " + node_name);
			e.printStackTrace();
		}
	}

	@Override
	public Map<String, E> reallocate(String pred_key, String new_pred_key) throws RemoteException {
		// generate a list of data to be transfer to succ
		Map<String, E> trans_map = new LinkedHashMap<>();
		List<String> key_list = new ArrayList<String>(key_value_table.keySet());
		for(String k : key_list)
			if(isBetween(k, pred_key, new_pred_key))
				trans_map.put(k, key_value_table.remove(k));
		// successor store data from pred_id to its id, now transfer data id from pred_id to new_pred_id for new_pred to take care
		return trans_map;
	}
	
	// use SHA-1 to generate ID
	public static String generateID(String name, int space) {
		String sha1 = null;
		try {
			MessageDigest md = MessageDigest.getInstance("SHA1");
			byte[] result = md.digest(name.getBytes());
			StringBuffer sb = new StringBuffer();
			for (int i = 0; i < result.length; i++) {
				sb.append(Integer.toString(result[i], 2).substring(1));
			}
			sha1 = sb.toString();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		int characters = (int) (Math.log(space)/Math.log(2));
		characters = Math.min(characters, sha1.length());
		return sha1.substring(sha1.length()-characters-1, sha1.length());
	}
	
	@Override
	public String getName() {
		return node_name;
	}
	
	@Override
	public String getID() throws RemoteException {
		return node_id;
	}

	@Override
	public NodeStruct<E> getSuccessor() throws RemoteException {
		return successor;
	}

	@Override
	public NodeStruct<E> getPredecessor() throws RemoteException {
		return predecessor;
	}

	@Override
	public void setAsSuccessor(NodeStruct<E> new_succ) throws RemoteException {
		successor = new_succ;
	}

	@Override
	public void setAsPredecessor(NodeStruct<E> new_pred) throws RemoteException {
		predecessor = new_pred;
	}
	
	// check if it's the same node
	@Override
	public boolean equals(Object compare_node) {	
		if(compare_node instanceof NodeStruct<?>)
			try {
				return node_id.equals(((NodeStruct<?>) compare_node).getID());
			} catch (RemoteException e) {
				e.printStackTrace();
				return false;
			}
		else
			return false;
	}
	
	// update all finger tables
	@Override
	public void updateRouting() {
		try {
			List<NodeStruct<E>> node_list = getAllNodes();
			for(NodeStruct<E> node : node_list)
				node.updateFingerTable(node_list);
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}

	// use key's id to seach which node stores this key-value pair
	@Override
	public NodeStruct<E> searchNodeFromKey(String key) throws RemoteException {
		// generate key id and find the position
		String pred_key = predecessor.getID();
		if(isBetween(key, pred_key, getID())) {
			return this;
		}
		else {
			// find from nodes in curr node's finger table
			String[] key_list = {};
			key_list = finger_table.keySet().toArray(key_list);
			for(int i=0; i<(key_list.length-1); i++) {
				String curr_key = key_list[i];
				String next_key = key_list[i+1];
				if(isBetween(key, curr_key, next_key)) {
					NodeStruct<E> curr_node = finger_table.get(curr_key);
					NodeStruct<E> target_node = curr_node.getSuccessor();
					return target_node.searchNodeFromKey(key);
				}
			}
			// return the last node in the table
			return finger_table.get(key_list[key_list.length-1]).getSuccessor().searchNodeFromKey(key);
		}
	}
	
	@Override
	public E getValueByKey(String key) throws RemoteException {
		return key_value_table.get(key);
	}
	
	@Override
	public void addKeyValue(String key, E value) throws RemoteException {
		key_value_table.put(key, value);
	}
	
	@Override
	public int removeKeyValue(String key) throws RemoteException {
			E res;
			res = key_value_table.remove(key);
			// if null then key is not found in the node, means run through all the replications
			if(res == null) {
				return 1;
			}else {
				return 0;
			}
			 
	}

	// search where the value store by key id and return the value
	@Override
	public void search(String key) {
		try {
			String query_id = generateID(key, N);
			NodeStruct<E> node = searchNodeFromKey(query_id);
			System.out.println("Search value for " + key + ": " + node.getValueByKey(query_id));
		} catch (RemoteException e) {
			System.out.println("key search failed.");
			e.printStackTrace();
		}
	}
	
	// search which node to store by key id and put key_id/value into the table of that node
	// replicate the data to next two succ 
	@Override
	public void create(String key, E value) {
		try {
			String key_id = generateID(key, N);
			NodeStruct<E> target_node = searchNodeFromKey(key_id);
			target_node.addKeyValue(key_id, value);
			NodeStruct<E> rep_node = target_node.getSuccessor();
			int rep_level = 2;
			while (!rep_node.equals(target_node) && rep_level > 0) {
				rep_node.addKeyValue(key_id, value);
				rep_node = rep_node.getSuccessor();
				rep_level --;
			}
			System.out.println("key/value: " + key + " / " + value + " has been added.");
			
		} catch (RemoteException e) {
			System.out.println("key/value pair added failed.");
			e.printStackTrace();
		}
	}
	
	// remove key_id/value pair
	@Override
	public void delete(String key) {
		try {
			int chack_repli = 0;
			String key_id = generateID(key, N);
			NodeStruct<E> node = searchNodeFromKey(key_id);
			
			if(node.removeKeyValue(key_id) == 1) {
				System.out.println("No existing key: " + key);
			} else {
				// if != 1 means might still have succ store the replication, continue to delete all replications
				while(chack_repli != 1) {
					node = node.getSuccessor();
					chack_repli = node.removeKeyValue(key_id);
				}
				System.out.println("Run through all replications.");
				System.out.println(key + " has been removed.");
			}
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}
	
	// run through all nodes and print all values
	@Override
	public void listAllKeyValue() {
		NodeStruct<E> curr_node = this;
		System.out.println("Stored Values:");
		try {
			do {
				System.out.print(curr_node.getName() + ": ");
				for(E values : curr_node.getAllValues())
					System.out.print(values.toString() + ", ");
				System.out.println();
				curr_node = curr_node.getSuccessor();
			} while(!this.equals(curr_node));
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}

	@Override
	public List<E> getAllValues() throws RemoteException {
		ArrayList<E> value_lists = new ArrayList<>(key_value_table.values());
		return value_lists;
	} 
	
	// get a list of all node's name
	private List<String> getAllNodesName() {
		ArrayList<String> name_list = new ArrayList<>();
		try {
			NodeStruct<E> curr_node = this;
			do {
				name_list.add(curr_node.getName());
				curr_node = curr_node.getSuccessor();
			} while(!this.equals(curr_node));
		} catch(RemoteException e){
			System.err.println("Error finding all nodes.");
		}
		return name_list;
	}
	
	
	// get a sorted NodeStruct list of all nodes with their id
	private List<NodeStruct<E>> getAllNodes() {
		ArrayList<NodeStruct<E>> node_list = new ArrayList<>();
		try {
			NodeStruct<E> curr_node = this;
			do {
				node_list.add(curr_node);
				curr_node = curr_node.getSuccessor();
			} while(!this.equals(curr_node));
		} catch(RemoteException e){
			System.err.println("Error finding all nodes.");
		}
		// sort nodes with their id
		Collections.sort(node_list, new Comparator<NodeStruct<E>>() {
			@Override
			public int compare(NodeStruct<E> node_1, NodeStruct<E> node_2) {
				try {
					int key_1 = Integer.parseInt(node_1.getID(), 2);
					int key_2 = Integer.parseInt(node_2.getID(), 2);
					
					if(key_1 > key_2) {
						return 1;
					}
					else if(key_1 < key_2) {
						return -1;
					}
					else {
						return 0;
					}
				}catch(RemoteException e) {
					return 0;
				}
			}
		});
		return node_list;
	}
				
	// update finger table
	public void updateFingerTable(List<NodeStruct<E>> nodes) {
		Map<String, NodeStruct<E>> temp_ft = new LinkedHashMap<>();
		temp_ft.put(node_id, this);
		try {
			int my_index = nodes.indexOf(this);
			// (n +2^(k−1)) mod 2^m, 1 ≤ k ≤ m
			for(int k=1; Math.pow(2, k)<=nodes.size(); k = k+1) {
				int node_index = (my_index + 2^(k-1)) % nodes.size();
				NodeStruct<E> n = nodes.get(node_index);
				temp_ft.put(n.getID(), n);
			}
		} catch(RemoteException e) {
			e.printStackTrace();
		}
		this.finger_table = temp_ft;
	}
	
	// compare id to check key_id is between two given key
	public static boolean isBetween(String key_id, String upbound, String lowbound) {
		float key = Float.parseFloat(key_id);
		float upper = Float.parseFloat(upbound);
		float lower = Float.parseFloat(lowbound);
		
		if(upper > lower) {
			return key > upper || key <= lower;
		} else if(upper < lower) {
			return key > upper && key <= lower;
		}
		else {
			return true;
		}
	}
}
	

