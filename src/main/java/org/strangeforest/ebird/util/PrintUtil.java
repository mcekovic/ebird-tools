package org.strangeforest.ebird.util;

import java.io.*;

public interface PrintUtil {

   int BANNER_WIDTH = 3;

   static void printBanner(PrintStream out, String banner) {
      var hLine = "*".repeat(banner.length() + (BANNER_WIDTH + 1) * 2);
      var vLine = "*".repeat(BANNER_WIDTH);
      out.println(hLine);
      out.println(vLine + ' ' + banner + ' ' + vLine);
      out.println(hLine);
   }
}
