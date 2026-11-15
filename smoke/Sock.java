import java.io.*;
import java.net.*;
public class Sock {
    public static void main(String[] a) throws Exception {
        Socket s = new Socket();
        s.connect(new InetSocketAddress("127.0.0.1", 18080), 5000);
        System.out.println("connected: local=" + s.getLocalSocketAddress() + " remote=" + s.getRemoteSocketAddress());
        OutputStream out = s.getOutputStream();
        out.write("CONNECT example.com:443 HTTP/1.1\r\nHost: example.com:443\r\n\r\n".getBytes("US-ASCII"));
        out.flush();
        BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream(), "US-ASCII"));
        System.out.println("reply: " + r.readLine());
        s.close();
    }
}
