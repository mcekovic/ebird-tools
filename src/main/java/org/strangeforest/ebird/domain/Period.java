package org.strangeforest.ebird.domain;

public enum Period {

   LIFE("life"),
   YEAR("year");

   private final String code;

   Period(String code) {
      this.code = code;
   }

   public String code() {
      return code;
   }
}
