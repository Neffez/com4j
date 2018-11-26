import com4j.ComException;
import com4j_idl.ClassFactory;
import com4j_idl.ITestObject;
import junit.framework.TestCase;

/**
 * @author Kohsuke Kawaguchi
 */
public class LongTest extends TestCase {
    public void test1() {
        final ITestObject t = ClassFactory.createTestObject();
        final long magic = 0x100000002L;
        assertEquals(magic, t.testInt64(magic));
        try {
            t.testInt64(1);
            fail();
        } catch (final ComException e) {
            assertEquals(0x80004005, e.getHRESULT());
        }
    }
}
