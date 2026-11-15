import java.net.*;
import java.util.List;
public class Psel {
    public static void main(String[] a) throws Exception {
        SmokeMain.installSandboxProxy();
        System.out.println("https.proxyHost=" + System.getProperty("https.proxyHost")
                + " port=" + System.getProperty("https.proxyPort"));
        List<Proxy> ps = ProxySelector.getDefault().select(new URI("https://www.jiosaavn.com/api.php"));
        for (Proxy p : ps) System.out.println("selected: " + p);
    }
}
