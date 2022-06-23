package org.strangeforest.ebird.domain;

public enum MediaType {

   NONE(""),
   PHOTO("P"),
   AUDIO("A");

   private final String code;

   MediaType(String code) {
      this.code = code;
   }

   public String code() {
      return code;
   }
}
