package dht;

// Interface for key-value table in each node
public interface KeyValueTable<V> {
	
	// CRUD functions for k-v table
	public void search(String key);
	public void create(String key, V value);
	public void delete(String key);
	public void listAllKeyValue();
	
	// join and leave of node
	public void join(NodeStruct<V> existed_node);
	public void leave();
	
	// update all finger table
	public void updateRouting();
	
}
