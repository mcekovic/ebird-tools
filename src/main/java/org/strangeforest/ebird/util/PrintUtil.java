package org.strangeforest.ebird.util;

public interface PrintUtil {

   int BANNER_WIDTH = 3;

   static void printBanner(String banner) {
      var hLine = "*".repeat(banner.length() + (BANNER_WIDTH + 1) * 2);
      var vLine = "*".repeat(BANNER_WIDTH);
      System.out.println(hLine);
      System.out.println(vLine + ' ' + banner + ' ' + vLine);
      System.out.println(hLine);
   }
}
