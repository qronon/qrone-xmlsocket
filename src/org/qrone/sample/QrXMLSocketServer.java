package org.qrone.sample;

import java.util.LinkedList;

import org.qrone.xmlsocket.XMLSocket;
import org.qrone.xmlsocket.XMLSocketServer;
import org.qrone.xmlsocket.event.XMLSocketListener;
import org.qrone.xmlsocket.event.XMLSocketServerListener;
import org.w3c.dom.Document;

/**
 * 送られてきた XML を接続中の全員にそのまま送るサーバー（port:9601）のサンプル。
 * 
 * @author J.Tabuchi
 * @since 2005/8/6
 * @version 1.0
 * @link QrONE Technology : http://www.qrone.org/
 */
public class QrXMLSocketServer {
	// サーバーの待ちうけポート番号
	public static final int SERVER_PORT = 9601;
	
	public static void main(String[] args){
		// 接続中のクライアントのリスト
		final LinkedList clientList = new LinkedList();
		
		// XMLSocketServer の作成
		final XMLSocketServer socketServer = new XMLSocketServer();
		socketServer.setEncoding("UTF-8");
		
		// サーバーのイベントハンドラの登録
		socketServer.addXMLSocketServerListener(new XMLSocketServerListener(){

			// サーバー開始時
			public void onOpen(boolean success) {
				System.out.println("open:" + success);
			}

			// サーバー終了
			public void onClose() {
				System.out.println("close:");
			}

			// サーバーエラー終了
			public void onClose(Exception e) {
				e.printStackTrace();
			}

			// 新しいクライアントの接続
			public void onNewClient(final XMLSocket socket) {
				// クライアントをリストに登録
				clientList.add(socket);
				// クライアントの通し番号を作る
				final int clientnumber = clientList.size();
				
				System.out.println("newclient:" + clientnumber);
				
				// クライアントのイベントハンドラの登録
				socket.addXMLSocketListener(new XMLSocketListener(){
					//　接続開始時
					public void onConnect(boolean success) {
						System.out.println("flash:"+clientnumber+":connect:");
					}
					
					// 接続終了時
					public void onClose() {
						System.out.println("flash:"+clientnumber+":close:");
					}

					// エラー
					public void onError(Exception e) {
						e.printStackTrace();
					}

					//　タイムアウト
					public void onTimeout() {
						System.out.println("flash:"+clientnumber+":timeout");
					}

					// Flash からのデータ受信
					public void onData(String data) {
						System.out.println("flash:"+clientnumber+":data:"+data);
						socket.send("<?xml version=\"1.0\" encoding=\"Shift_JIS\"?>"+
									"<Message date=\"テスト\"/>");
					}

					// Flash から受信したデータの XML DOM
					public void onXML(Document doc) {
					}
				});
			}
		});
		
		// サーバーを開始する
		socketServer.open(SERVER_PORT);
	}

	private static String toLiteral(String str){
		StringBuffer buf = new StringBuffer();
		char[] ch = str.toCharArray();
		for (int i = 0; i < ch.length; i++) {
			switch(ch[i]){
			case '\b':
				buf.append("\\b");
				break;
			case '\f':
				buf.append("\\f");
				break;
			case '\n':
				buf.append("\\n");
				break;
			case '\r':
				buf.append("\\r");
				break;
			case '\t':
				buf.append("\\t");
				break;
			case '\'':
				buf.append("\\\'");
				break;
			case '\"':
				buf.append("\\\"");
				break;
			case '\\':
				buf.append("\\\\");
				break;
			default:
				buf.append(ch[i]);
				break;
			}
		}
		return buf.toString();
	}
}
