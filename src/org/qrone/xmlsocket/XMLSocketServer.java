package org.qrone.xmlsocket;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.LinkedList;

import org.qrone.xmlsocket.event.XMLSocketServerListener;

/**
 * XMLSocket 通信用のサーバークラス。<BR>
 * <BR>
 * Macromedia Flash 5 以降に実装されている .swf ファイルとの通信を行うサーバークラス
 * です。接続してきた複数のクライアントと接続を確立し、XMLSocket オブジェクトという形で各クラ
 * イアントとの通信手段を提供します。<BR>
 * <code><pre>
 * XMLSocketServer socketServer = new XMLSocketServer();
 * 
 * socketServer.addXMLSocketServerListener(new XMLSocketServerAdapter(){
 *     public void onNewClient(final XMLSocket socket) {
 *         System.out.println("newclient:");
 *         
 *         socket.addXMLSocketListener(new XMLSocketAdapter(){
 *             public void onXML(Document doc) {
 *                 Element e = doc.getDocumentElement();
 *                 ...
 *             }
 *         });
 *     }
 * });
 * socketServer.open(9601);
 * </pre></code>
 * addXMLSocketServerListener(XMLServerSoketLister) でイベントハンドラを登録することで
 * イベントを取得して利用することもできますし、このクラスを継承して protected 指定されているいくつかの
 * メソッドをオーバーライドすることでも利用できます。
 * 
 * @author J.Tabuchi
 * @since 2005/8/6
 * @version 1.0
 * @link QrONE Technology : http://www.qrone.org/
 */
public class XMLSocketServer {
	private static final int SERVER_TIMEOUT = 30000;
	private ServerSocket serversocket;
	
	private Thread startThread;
	private Thread acceptThread;
	
	private LinkedList socketlist = new LinkedList();
	private boolean opened = false;
	
	private LinkedList serverlistener = new LinkedList();

	private Charset inputcs  = null;
	private Charset outputcs = null;
	
	/**
	 * XMLSocketServer インスタンスを生成します
	 */
	public XMLSocketServer(){}

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
	
	/**
	 * 指定した port を開いてサーバーを開始します。
	 * @param port 接続受け入れポート（例:9601）
	 */
	public void open(int port){
		try {
			open(new ServerSocket(port));
		} catch (IOException e) {
			onOpen(false);
		}
	}
	
	/**
	 * 指定した serversocket を用いてサーバーを開始します。
	 * @param serversocket　受け入れ側サーバーソケット
	 */
	public void open(final ServerSocket serversocket){
		this.serversocket = serversocket;
		try {
			serversocket.setSoTimeout(SERVER_TIMEOUT);
		} catch (SocketException e) {}
		startThread = new Thread(new Runnable(){
			public void run() {
				acceptThread = new Thread(new Runnable(){
					public void run() {
						while(opened){
							try {
								Socket socket = serversocket.accept();
								XMLSocket xmlsocket = new XMLSocket();
								xmlsocket.setEncoding(inputcs,outputcs);
								
								onNewClient(xmlsocket);
								xmlsocket.connect(socket);
								socketlist.add(xmlsocket);
								
							} catch (SocketTimeoutException e){
							} catch (SocketException e){
							} catch (IOException e) {
								close(e);
							}
						}
					}
				});
				opened = true;
				onOpen(true);
				acceptThread.start();
			}
		});
		startThread.start();
	}
	
	/**
	 * XMLSocketServer サーバーを終了します。
	 */
	public void close(){
		close(null);
	}
	
	/**
	 * XMLSocketServer　サーバーに関連づけられている ServerSocket インスタンスを返します。
	 * @return 利用中のサーバーソケット
	 */
	public ServerSocket getServerSocket(){
		return serversocket;
	}
	
	private void close(Exception e){
		opened = false;
		for (Iterator iter = socketlist.iterator(); iter.hasNext();) {
			XMLSocket xmlsocket = (XMLSocket) iter.next();
			xmlsocket.close();
		}
		try {
			serversocket.close();
		} catch (IOException e1) {
			e = e1;
		}
		onClose();
		if(e!=null) onClose(e);
	}
	
	/**
	 * サーバー開始直後に呼び出されます。 success == false の時には<b>サーバーが開始されていません。</b><BR>
	 * 継承したクラスでこのメソッドをオーバーライドするとイベントハンドラのイベントが呼ばれなくなります。<BR>
	 * <BR>
	 * 通常は addXMLSocketServerListener(XMLSocketServerListener) でイベントハンドラを利用してください。
	 * @see #addXMLSocketServerListener(XMLSocketServerListener)
	 * @param success サーバー開始正否
	 */
	protected void onOpen(boolean success){
 		for (Iterator iter = serverlistener.iterator(); iter.hasNext();) {
			((XMLSocketServerListener)iter.next()).onOpen(success);
		}
	}

	/**
	 * サーバーがエラーで終了した直後に呼び出されます。<BR>
	 * 継承したクラスでこのメソッドをオーバーライドするとイベントハンドラのイベントが呼ばれなくなります。<BR>
	 * <BR>
	 * 通常は addXMLSocketServerListener(XMLSocketServerListener) でイベントハンドラを利用してください。
	 * @see #addXMLSocketServerListener(XMLSocketServerListener)
	 * @param e サーバー終了理由エラー
	 */
	protected void onClose(Exception e){
 		for (Iterator iter = serverlistener.iterator(); iter.hasNext();) {
			((XMLSocketServerListener)iter.next()).onClose(e);
		}
	}

	/**
	 * サーバー終了直後に呼び出されます。<BR>
	 * 継承したクラスでこのメソッドをオーバーライドするとイベントハンドラのイベントが呼ばれなくなります。<BR>
	 * <BR>
	 * 通常は addXMLSocketServerListener(XMLSocketServerListener) でイベントハンドラを利用してください。
	 * @see #addXMLSocketServerListener(XMLSocketServerListener)
	 */
	protected void onClose(){
 		for (Iterator iter = serverlistener.iterator(); iter.hasNext();) {
			((XMLSocketServerListener)iter.next()).onClose();
		}
	}

	/**
	 * 新たに Macromedia Flash の .swf ファイルから XMLSocket 通信を要求された時に
	 * 呼び出され、swf ファイルと通信を確立する直前の XMLSocket オブジェクトが渡されます。<BR>
	 * 継承したクラスでこのメソッドをオーバーライドするとイベントハンドラのイベントが呼ばれなくなります。<BR>
	 * <BR>
	 * 通常は addXMLSocketServerListener(XMLSocketServerListener) でイベントハンドラを利用してください。
	 * @see #addXMLSocketServerListener(XMLSocketServerListener)
	 */
	protected void onNewClient(XMLSocket socket){
 		for (Iterator iter = serverlistener.iterator(); iter.hasNext();) {
			((XMLSocketServerListener)iter.next()).onNewClient(socket);
		}
	}
	
	/**
	 * イベントハンドラを登録して各種イベントを取得します。
	 * @param listener　イベントハンドラ
	 */
	public void addXMLSocketServerListener(XMLSocketServerListener listener) {
		serverlistener.add(listener);
	}

	/**
	 * 登録されているイベントハンドラを削除します。
	 * @param listener　イベントハンドラ
	 */
	public void removeXMLSocketServerListener(XMLSocketServerListener listener) {
		serverlistener.remove(listener);
	}
	
}
