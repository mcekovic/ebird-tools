package org.strangeforest.ebird.util;

import java.util.*;
import java.util.stream.*;

public interface Util {

   String EBIRD_API_USERNAME = "MjUxODE0Nw";
   String EBIRD_API_TOKEN = "jfekjedvescr";

   static String padUsername(String username) {
      var mod4 = username.length() % 4;
      return mod4 != 0 ? username + "=".repeat(4 - mod4) : username;
   }

   static String csvCell(String s) {
      return s != null ? (s.contains(",") ? "\"" + s + "\"" : s) : "";
   }

   static String csvCell(Object obj) {
      return obj != null ? obj.toString() : "";
   }

   static<T> List<T> mergeAndSort(List<T> list1, List<T> list2, Comparator<T> comparator) {
      return Stream.concat(list1.stream(), list2.stream()).sorted(comparator).toList();
   }
}
