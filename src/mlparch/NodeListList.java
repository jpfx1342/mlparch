/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package mlparch;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 *
 * @author John Petska
 */
public class NodeListList implements List<Node> {
	private final NodeList nodes;
	private final int start;
	private final int end;

	public NodeListList(NodeList nodes) {
		this(nodes, -1, -1);
	}
	public NodeListList(NodeList nodes, int start, int end) {
		this.nodes = nodes;
		this.start = start;
		this.end = end;
		if ((this.start<0)^(this.end<0))
			throw new IllegalArgumentException("Both start and end index must be positive or negative (signs must match)");
	}

	@Override public int size() { return start>=0 ? end-start : nodes.getLength(); }
	public int start() { return start>=0 ? start : 0; }
	@Override public boolean isEmpty() { return nodes.getLength() != 0;}

	@Override public Object[] toArray() {
		Node[] na = new Node[size()];
		for (int i = 0; i < size(); i++)
			na[i] = nodes.item(i+start());
		return na;
	}

	@Override public <Node> Node[] toArray(Node[] a) {
		if (a.length < size())
			return (Node[]) toArray();
		for (int i = 0; i < size(); i++)
			a[i] = (Node) nodes.item(i+start());
		if (a.length > size())
			a[size()] = null;
		return a;
	}

	@Override public int indexOf(Object o) {
		if (!(o instanceof Node)) return -1;

		for (int i = 0; i < size(); i++) {
			Node n = nodes.item(i+start());

			if ((o==null && n == null) || o.equals(n))
				return i;
		}
		return -1;
	}
	@Override public int lastIndexOf(Object o) {
		if (!(o instanceof Node)) return -1;

		for (int i = size()-1; i >= 0; i--) {
			Node n = nodes.item(i+start());

			if ((o==null && n == null) || o.equals(n))
				return i;
		}
		return -1;
	}
	@Override public boolean contains(Object o) { return indexOf(o) >= 0; }
	@Override public boolean containsAll(Collection<?> c) {
		for (Iterator<?> it = c.iterator(); it.hasNext();) {
			Object o = it.next();
			if (!(o instanceof Node) || !contains((Node)o))
				return false;
		}
		return true;
	}

	private RuntimeException unmodifiableError() { return new UnsupportedOperationException("NodeListCollection is unmodifiable."); }

	@Override public boolean add(Node e) { throw unmodifiableError(); }
	@Override public boolean remove(Object o) { throw unmodifiableError(); }
	@Override public boolean addAll(Collection<? extends Node> c) { throw unmodifiableError(); }
	@Override public boolean addAll(int index, Collection<? extends Node> c) { throw unmodifiableError(); }
	@Override public boolean removeAll(Collection<?> c) { throw unmodifiableError(); }
	@Override public boolean retainAll(Collection<?> c) { throw unmodifiableError(); }
	@Override public void clear() { throw unmodifiableError(); }

	@Override public Node get(int index) { return nodes.item(index+start()); }
	@Override public Node set(int index, Node element) { throw unmodifiableError(); }
	@Override public void add(int index, Node element) { throw unmodifiableError(); }
	@Override public Node remove(int index) { throw unmodifiableError(); }

	/* sorry, no iterators, because I'm lazy **/
	@Override public Iterator<Node> iterator() {
		throw new UnsupportedOperationException("Not supported yet.");
	}
	@Override public ListIterator<Node> listIterator() {
		throw new UnsupportedOperationException("Not supported yet.");
	}
	@Override public ListIterator<Node> listIterator(int index) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override public List<Node> subList(int fromIndex, int toIndex) {
		return new NodeListList(nodes, fromIndex, toIndex);
	}
}
	