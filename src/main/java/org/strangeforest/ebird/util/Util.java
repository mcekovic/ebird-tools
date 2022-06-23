package org.strangeforest.ebird.util;

public interface Util {

   String EBIRD_API_USERNAME = "MjUxODE0Nw";
   String EBIRD_API_TOKEN = "jfekjedvescr";

   static String csvCell(String s) {
      return s != null ? (s.contains(",") ? "\"" + s + "\"" : s) : "";
   }

   static String csvCell(Object obj) {
      return obj != null ? obj.toString() : "";
   }

   static String padUsername(String username) {
      var mod4 = username.length() % 4;
      return mod4 != 0 ? username + "=".repeat(4 - mod4) : username;
   }
}
