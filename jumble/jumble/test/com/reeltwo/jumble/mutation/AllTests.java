package com.reeltwo.jumble.mutation;

import junit.framework.Test;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

/**
 * Runs all com.reeltwo.jumble tests.
 *
 * @author <a href="mailto:len@reeltwo.com">Len Trigg</a>
 * @version $Revision: 496 $
 */
public class AllTests extends TestSuite {
  public static Test suite() {
    TestSuite suite = new TestSuite();

    suite.addTest(MutaterTest.suite());
    suite.addTest(MutatingClassLoaderTest.suite());

    return suite;
  }
  
  public static void main(String[] args) {
    TestRunner.run(suite());
  }
}
