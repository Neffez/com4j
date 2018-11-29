
import com4j.COM4J;

/**
 * Test program.
 *
 * @author Kohsuke Kawaguchi (kk@kohsuke.org)
 */
public class Main {
    public static void main(final String[] args) {
        final IWshShell wsh = COM4J.createInstance(IWshShell.class, "WScript.Shell");
        final String s = wsh.ExpandEnvironmentStrings("%WinDir%");
        // Holder<String> h = new Holder<String>();
        // wsh.ExpandEnvironmentStrings("%WinDir%",h);
        // String s = h.value;
        System.out.println(s);
        wsh.dispose();
    }
}
