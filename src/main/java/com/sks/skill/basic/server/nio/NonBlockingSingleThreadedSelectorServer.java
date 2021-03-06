package com.sks.skill.basic.server.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.sks.skill.basic.server.oio.Util;

/**
 * @author Sabith_ks
 * This is too good
 *
 */
public class NonBlockingSingleThreadedSelectorServer {


	private static Map<SocketChannel, Queue<ByteBuffer>> pendingSocketWrite = new HashMap<SocketChannel, Queue<ByteBuffer>>();


	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) {
		//Byte Buffer allocation
		try{
			ServerSocketChannel serverSocketChannel;
			serverSocketChannel = ServerSocketChannel.open();
			//Bind the server socket to a interface.
			serverSocketChannel.bind(new InetSocketAddress("localhost", 8080));
			serverSocketChannel.configureBlocking(false);
			//Create a selector
			Selector selector	= Selector.open();
			//Register the selector onto the server socket channel;
			serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
			while(true){
				//Once there is a connect, this would react.
				selector.select();
				//Key select
				for(Iterator<SelectionKey> iterator = selector.selectedKeys().iterator(); iterator.hasNext();){
					SelectionKey key	= iterator.next();
					iterator.remove();
					//check if the socket is closed or not
					if(key.isValid()){
						if(key.isAcceptable()) {
							//Someone connected to our server socket channel
							accept(key);
						}else  if(key.isReadable()) { 
							read(key);
						}else if(key.isWritable()) {
							write(key);
						}
					}
				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	/**
	 * @param key
	 * @throws IOException 
	 */
	private static void write(SelectionKey key) throws IOException {
		SocketChannel sChannel	= (SocketChannel) key.channel();
		Queue<ByteBuffer> byteBufferQueue	= pendingSocketWrite.get(sChannel);
		//Read the data from the queue
		ByteBuffer byteBuffer;
		while((byteBuffer = byteBufferQueue.peek()) != null){
			//We are using peek and not poll, bcos, 
			//We are sure the data stays even if there is a data loss in first write

			sChannel.write(byteBuffer);
			if(! byteBuffer.hasRemaining()){
				//we wrote the everything.
				byteBufferQueue.poll();
			}else{
				//there is something remaining, so that we have reading enabled back
				return;
			}
		}
		//ONce the queue is empty, change the channel back to read
		sChannel.register(key.selector(), SelectionKey.OP_READ);
	}

	/**
	 * @param key
	 */
	private static void read(SelectionKey key) {
		SocketChannel sChannel	= (SocketChannel) key.channel();
		try{
			ByteBuffer byteBuffer	= ByteBuffer.allocate(1024);
			int read	= sChannel.read(byteBuffer);
			//Remove the socket channel from set if the connection is closed
			if(read == -1){
				//if the socket is closed, remove it from the set
				pendingSocketWrite.remove(sChannel);
				//System.out.println("Connection  Closed from "+sChannel+" on thread "+Thread.currentThread().getId()+". Total concurrent connection "+pendingSocketWrite.size());;
				return;
			}
			byteBuffer.flip();
			//Transmogify  
			for(int index=0; index< byteBuffer.limit(); index++){
				byteBuffer.put(index, (byte) Util.transmogify(byteBuffer.get(index)));
			}
			//dont write it yet, bcos we are just reading
			//So we add this to the queue 
			pendingSocketWrite.get(sChannel).add(byteBuffer);
			//Now we need  make the socket react once the socket is ready to write.
			sChannel.register(key.selector(), SelectionKey.OP_WRITE);
			//We just registed a selector for write
		} catch (IOException e) {
			//in case of error, remove the socket 
			System.err.println("Error while connecting : "+e.getMessage());
			e.printStackTrace();
		}

	}

	/**
	 * @param key
	 * @throws IOException 
	 */
	private static void accept(SelectionKey key) throws IOException {
		ServerSocketChannel serverSocketChannel	= (ServerSocketChannel) key.channel();
		//Get the socket channel out of the server socket channel
		SocketChannel	socketChannel	= serverSocketChannel.accept(); //non-blocking , never null
		socketChannel.configureBlocking(false);
		//We are saying that we are gonna make the read when someone puts somethign in socket.
		socketChannel.register(key.selector(), SelectionKey.OP_READ);
		pendingSocketWrite.put(socketChannel, new ConcurrentLinkedQueue<ByteBuffer>());
	}
}
