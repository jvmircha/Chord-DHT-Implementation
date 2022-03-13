package dht;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;


public interface NodeStruct<V> extends Remote {
	
	// get the id and node_name of the node
	public String getID() throws RemoteException;
	public String getName() throws RemoteException;

	// get the succ/pred of node in NodeStruct type
	public NodeStruct<V> getSuccessor() throws RemoteException;
	public NodeStruct<V> getPredecessor() throws RemoteException;
	
	// set a node as it's succ/pred
	public void setAsSuccessor(NodeStruct<V> succ) throws RemoteException;
	public void setAsPredecessor(NodeStruct<V> pred) throws RemoteException;
	
	// functions for node join/leave: reallocate the leaving node's data,
	// and updating finger table
	public Map<String, V> reallocate(String oldPredKey, String newPredKey) throws RemoteException;	
	public void updateFingerTable(List<NodeStruct<V>> nodes) throws RemoteException;
	
	// functions for nodes' implementation: search which node stores the key,
	// CRUD of key/value
	public NodeStruct<V> searchNodeFromKey(String key) throws RemoteException;
	public V getValueByKey(String key) throws RemoteException;
	public void addKeyValue(String key, V value) throws RemoteException;
	public int removeKeyValue(String key) throws RemoteException;
	public List<V> getAllValues() throws RemoteException;
	
	
}