import java.nio.ByteBuffer;

import com4j.COM4J;
import com4j.Holder;
import com4j_idl.ClassFactory;
import junit.framework.TestCase;

/**
 * @author Kohsuke Kawaguchi
 */
public class OutByteBufTest extends TestCase {
    public void test1() throws Exception {
        final ITestObject2 t = ClassFactory.createTestObject().queryInterface(ITestObject2.class);
        final Holder<Integer> sz = new Holder<>();
        final int ptr = t.outByteBuf("Test", sz);
        final ByteBuffer buf = COM4J.createBuffer(ptr, sz.value);

        final byte[] tmp = new byte[13];
        buf.get(tmp);

        final String msg = new String(tmp);
        assertEquals("Hello, World!", msg);
        System.out.println(msg);
    }
}
