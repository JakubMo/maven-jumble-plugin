package com.reeltwo.jumble.util;

import java.util.Properties;

/**
 * Class used for testing purposes
 *
 * @author Tin
 * @version $Revision: 503 $
 */
public class DisplayEnvironment {

  // Private c'tor
  private DisplayEnvironment() { }

  public static void main(String[] args) {
    Properties props = System.getProperties();
        
    System.out.println("java.home " + props.getProperty("java.home"));
    System.out.println("java.class.path " + props.getProperty("java.class.path"));
  }
}
