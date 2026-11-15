import com.jackbhai.omnitools.nativ.Net;
import com.jackbhai.omnitools.nativ.Sec;
public class Probe {
    public static void main(String[] a) throws Exception {
        SmokeMain.installSandboxProxy();
        System.out.println("decode test: " + Sec.decode("aHR0cHM6Ly93d3cuamlvc2Fhdm4uY29tL2FwaS5waHA="));
        try {
            String r = Net.get("https://www.jiosaavn.com/api.php?__call=search.getResults&_format=json&_marker=0&api_version=4&ctx=web6dot0&n=3&p=1&q=khaab%20akhil", 15000);
            System.out.println("official raw len=" + r.length() + " head=" + r.substring(0, Math.min(200, r.length())));
        } catch (Exception e) { e.printStackTrace(); }
    }
}
