package com.reeltwo.jumble.util;
/**
 * Class for testing java runner.
 * @author Tin Pavlinic
 * @version $Revision: 503 $
 */
public class CheckAssertions2 {
  // Private c'tor
  private CheckAssertions2() { }

  public static void main(String[] args) {
    assert print("Assertions on");
  }

  private static boolean print(String str) {
    System.out.println(str);
    return true;
  }
}
