package org.qrone.xmlsocket;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.LinkedList;

import javax.xml.transform.TransformerException;

import org.qrone.XMLTools;
import org.qrone.xmlsocket.event.XMLSocketListener;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * XMLSocket 通信クラス。<BR>
 * <BR>
 * Macromedia Flash 5 以降に実装されている .swf ファイルとの XMLSocket 通信を行うクラス。
 * ActionScript の XMLSocket オブジェクトとほぼ同様に設計されており、.swf ファイルとの通信
 * を行います。全てのイベントは、addXMLSocketListener(XMLSocketListener) でイベントハン
 * ドラの形で登録して取得することが出来ます。
 * <code><pre>
 * XMLSocket socket = ...;
 * socket.addXMLSocketListener(new XMLSocketListener(){
 *     public void onConnect(boolean success){}
 *     public void onClose(){}
 *     public void onClose(Exception e){}
 *     public void onTimeout(){}
 *     public void onData(String data){}
 *     public void onXML(Document doc){
 *         Element e = doc.getDocumentElement();
 *         ...
 *     }
 * });
 * 
 * </pre></code>
 * XMLSocketServer を起動する場合には、XMLSocketServer.addXMLSocketServerListener(XMLSocketServerListener)
 * でイベントハンドラを登録し、XMLSocketServerListener.onNewClient(XMLSocket)
 * メソッドからこのクラスのインスタンスを取得してください。またこのクラスを継承することであたかも Flash クライ
 * アント側のように　XMLSocket サーバーと通信することも可能です。
 * 
 * @author J.Tabuchi
 * @since 2005/8/6
 * @version 1.0
 * @link QrONE Technology : http://www.qrone.org/
 */
public class XMLSocket {
	private static final int CLIENT_TIMEOUT = 30000;
	private static final char eof = '\0';
	private static final int BUF_SIZE = 1024;
	
	private Socket socket;
	private InputStream in;
	private BufferedReader reader;

	private OutputStream out;
	private BufferedWriter writer;
	
	private Thread connectThread;
	private Thread readThread;
	private Thread writeThread;
	private boolean connected;
	
	private boolean parsexml = true;
	private LinkedList queue = new LinkedList();
	private LinkedList xmllistener = new LinkedList();
	
	private Charset inputcs  = null;
	private Charset outputcs = null;
	/**
	 * 接続されていない XMLSocket オブジェクトを生成します。
	 */
	public XMLSocket(){}
	
	public void setEncoding(Charset cs){
		setEncoding(cs,cs);
	}
	
	public void setEncoding(String charset){
		setEncoding(Charset.forName(charset));
	}
	
	public void setEncoding(Charset input, Charset output){
		inputcs = input;
		outputcs = output;
	}
	
	public void dynamicChangeOutputEncode(Charset cs){
		synchronized(queue){
			writer = new BufferedWriter(new OutputStreamWriter(out,cs));
		}
	}
	
	/**
	 * すでに接続されている socket を利用して通信を開始します。
	 * @param socket　接続済ソケット
	 */
	public void connect(final Socket socket){
		this.socket = socket;
		try {
			socket.setSoTimeout(CLIENT_TIMEOUT);
		} catch (SocketException e) {}
		connectThread = new Thread(new Runnable(){
			public void run() {
				try{
					in=socket.getInputStream();
					out=socket.getOutputStream();
					
					if(inputcs == null){
						reader = new BufferedReader(new InputStreamReader(in));
					}else{
						reader = new BufferedReader(new InputStreamReader(in,inputcs));
					}
					if(outputcs == null){
						writer = new BufferedWriter(new OutputStreamWriter(out));
					}else{
						writer = new BufferedWriter(new OutputStreamWriter(out,outputcs));
					}
					connected = true;

					readThread = new Thread(new Runnable(){
				 		public void run(){
				 			char[] buf = new char[BUF_SIZE];
				 			StringBuffer strBuf = new StringBuffer();
				 			while(connected){
				 				try {
				 					int c = reader.read(buf);
				 					//blocking
				 					
				 					if(c < 0) close();
				 					for(int i=0;i<c;i++){
				 						if(buf[i] == 0){
				 							onData(strBuf.toString());
				 							if(parsexml){
				 						 		try {
				 									onXML(XMLTools.read(strBuf.toString()));
				 								} catch (SAXException e) {}
				 							}
				 							strBuf = new StringBuffer();
				 						}else{
				 							strBuf.append(buf[i]);
				 						}
				 					}
				 				}catch(SocketTimeoutException e) {
				 					onTimeout();
				 				}catch(SocketException e) {
				 					break;
				 				}catch (IOException e) {
				 					close(e);
				 					break;
				 				}
				 				
				 			}
				 		}
				 	});

					writeThread = new Thread(new Runnable(){
				 		public void run(){
				 			MAINROOP:while(connected){
				 				String data;
				 				synchronized (queue) {
				 					while(queue.size() == 0) {
				 						try{
					 						if(!connected){
					 							try {
													socket.close();
												} catch (IOException e2) {}
												break MAINROOP;
					 						}
				 							queue.wait();
				 							//blocking
				 							
				 						}catch(InterruptedException e){
				 							if(!connected) break MAINROOP;
				 						}
				 					}
				 					data = (String)queue.remove(0);
				 				}
				 				
				 				try {
				 					writer.write(data);
				 					writer.write("\0");
				 					writer.flush();
				 				}catch(SocketException e) {
				 					break;
				 				}catch (IOException e) {
				 					close(e);
				 					break;
				 				}
				 			}
				 		}
				 	});
					readThread.start();
					writeThread.start();
					onConnect(true);
				}catch(IOException ecp){
					connected = false;
					onConnect(false);
				}
			}
			
		});
		connectThread.start();
	}
	
	/**
	 * 指定された address:port に接続します。
	 * @param address　接続先ホストアドレス(例:www.qrone.org)
	 * @param port　接続先ポート(例:9601)
	 */
	public void connect(String address, int port) throws UnknownHostException, IOException{
		socket = new Socket(address,port);
		connect(socket);
	}
	
	/**
	 * 接続を切断します。切断完了時には onClose() が呼び出されます。
	 */
	public void close(){
		onClose();
		synchronized(queue){
			connected = false;
			queue.notifyAll();
		}
	}
	
	private void close(Exception e){
		try {
			connected = false;
			writeThread.interrupt();
			socket.close();
		} catch (IOException e2){
			onError(e2);
			onClose();
			return;
		}
		onError(e);
		onClose();
	}
	
	/**
	 * 文字列 str を相手側に送ります。 XMLSocket 通信では str は通常 well-formed XML である
	 * 必要があります。
	 * @param str　送信する文字列 （XML であるべきです）
	 */
	public void send(String str){
		if(connected){
			synchronized (queue) {
				queue.add(str);
				queue.notifyAll();
			}
		}
	}

	/**
	 * XML ドキュメントを相手側に送ります。 
	 * @param doc 送信する XML ドキュメント
	 */
	public void send(Document doc) throws TransformerException{
		if(connected){
			synchronized (queue) {
				queue.add(XMLTools.write(doc));
				queue.notifyAll();
			}
		}
	}
	/**
	 * XML 解析を行うかどうかの設定をします。true にした場合には常に XML 解析が行われ
	 * ますが、false にすると XML 解析が行われなくなり、onXML(Document) が呼び出さ
	 * れることがなくなります。
	 * @param bool XML 解析の行う/行わない
	 */
	public void setXMLParsing(boolean bool){
		parsexml = bool;
	}
	
	/**
	 * 接続を行っている Socket クラスのインスタンスを返します。
	 * @return　接続中ソケット
	 */
	public Socket getSocket(){
		return socket;
	}
	
	/**
	 * 接続開始時に呼ばれ、XMLSocketListener に通知します。success == false 
	 * の時は<b>通信が確立されていません。</b><BR>
	 * <BR>
	 * このクラスを継承したクラスを作る場合にはこのメソッドを継承することで onClose(Exception) イベントを
	 * 取得できます。
	 * @param success 接続の正否
	 */
	
	protected void onConnect(boolean success){
 		for (Iterator iter = xmllistener.iterator(); iter.hasNext();) {
			((XMLSocketListener)iter.next()).onConnect(success);
		}
	}

	/**
	 * エラーが出た時に呼ばれ、XMLSocketListener に通知します。<BR>
	 * <BR>
	 * このクラスを継承したクラスを作る場合にはこのメソッドを継承することで onError(Exception) イベントを
	 * 取得できます。
	 * @param e　エラー
	 */
	protected void onError(Exception e){
		onClose();
		if(e!=null){
			for (Iterator iter = xmllistener.iterator(); iter.hasNext();) {
				((XMLSocketListener)iter.next()).onError(e);
			}
		}
	}

	/**
	 * 切断完了時に呼ばれ、XMLSocketListener に通知します。<BR>
	 * <BR>
	 * このクラスを継承したクラスを作る場合にはこのメソッドを継承することで onClose() イベントを
	 * 取得できます
	 */
	protected void onClose(){
 		for (Iterator iter = xmllistener.iterator(); iter.hasNext();) {
			((XMLSocketListener)iter.next()).onClose();
		}
	}

	/**
	 * 通信タイムアウト時に呼ばれ、XMLSocketListener に通知します。タイムアウトは通常３０秒程度に
	 * 設定されています。<BR>
	 * <BR>
	 * このクラスを継承したクラスを作る場合にはこのメソッドを継承することで onTimeout() イベントを
	 * 取得できます
	 * @see java.net.Socket#setSoTimeout(int)
	 */
	protected void onTimeout(){
 		for (Iterator iter = xmllistener.iterator(); iter.hasNext();) {
			((XMLSocketListener)iter.next()).onTimeout();
		}
	}
	
	/**
	 * データ受信時に呼ばれ、XMLSocketListener に通知します。<BR>
	 * <BR>
	 * このクラスを継承したクラスを作る場合にはこのメソッドを継承することで onData(String) イベントを
	 * 取得できます。
	 */
	protected void onData(String data){
 		for (Iterator iter = xmllistener.iterator(); iter.hasNext();) {
			((XMLSocketListener)iter.next()).onData(data);
		}
 	}

	/**
	 * データ受信後のさらに XML 解析後、に呼ばれ、XMLSocketListener に通知します。<BR>
	 * <BR>
	 * このクラスを継承したクラスを作る場合にはこのメソッドを継承することで onXML(Document) イベントを
	 * 取得できます。このイベントを取得するには setXMLParseing(boolean)　に true (default) 
	 * が設定されている必要があります。
	 */
	protected void onXML(Document doc){
 		for (Iterator iter = xmllistener.iterator(); iter.hasNext();) {
			((XMLSocketListener)iter.next()).onXML(doc);
		}
 	}
	
	/**
	 * イベントハンドラを登録します。このメソッドを利用して XMLSocketListener　を実装したクラスを
	 * 登録してイベントを取得、適宜処理を行ってください。
	 * @param listener　イベントハンドラ
	 */
	public void addXMLSocketListener(XMLSocketListener listener) {
		xmllistener.add(listener);
	}
	
	/**
	 * 登録したイベントハンドラを削除します。
	 * @param listener　イベントハンドラ
	 */
	public void removeXMLSocketListener(XMLSocketListener listener) {
		xmllistener.remove(listener);
	}
}
