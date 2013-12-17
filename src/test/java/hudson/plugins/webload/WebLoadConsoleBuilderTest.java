// (c) Copyright 2013 RadView Software Inc. 
// Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
// The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
package hudson.plugins.webload;

import static hudson.plugins.webload.WebLoadConsoleBuilder.extractValue;
import static hudson.plugins.webload.WebLoadConsoleBuilder.replaceExtension;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author yams
 */
public class WebLoadConsoleBuilderTest {
    
    public WebLoadConsoleBuilderTest() {
    }
    
    @BeforeClass
    public static void setUpClass() {
    }
    
    @AfterClass
    public static void tearDownClass() {
    }
    
    @Before
    public void setUp() {
    }
    
    @After
    public void tearDown() {
    }

    @Test
    public void testExtractValue() {
        System.out.println("testExtractValue");
        String s = "chello123therec";
        assertEquals("123", extractValue("hello", "there", s));
    }
    
    @Test
    public void testResult() {
        String str = 
                "\" <?xml version=\"1.0\"?>\n\n<root>\n\n" +
 	"<el SessionReturnCode=\"Passed\"/>\n\n" +
 	"<el Description=\"...\"/> \n\n" +
 	"<el StartDateTime=\"Sun Sep 29 15:02:24 2013\"/> \n\n" +
 	"<el EndDateTime=\"Sun Sep 29 15:02:56 2013\"/> \n\n" +
 	"<el Path=\"c:\\temp\\demols\"/> \n\n" +
 	"<el ErrorDescription=\"Test passed\"/> \n\n" +
        "</root>";
        
        assertEquals("Passed", extractValue("SessionReturnCode=\"","\"", str ) );
    }
    
    @Test
    public void testMakeLsFileName() {
        assertEquals("my.ls", replaceExtension("my.ls", "ls"));
        assertEquals("my.ls", replaceExtension("my", "ls"));
        assertEquals("my.ls", replaceExtension("my.tpl", "ls"));
    }
}