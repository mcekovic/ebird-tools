package org.strangeforest.ebird.domain;

public enum Region {

   SERBIA("RS", "Serbia"),
   BELGRADE("RS-00", "Belgrade"),
   VOJVODINA("RS-VO", "Vojvodina"),
   MACVA("RS-08", "Mačva"),
   KOLUBARA("RS-09", "Kolubara"),
   PODUNAVLJE("RS-10", "Podunavlje"),
   BRANICEVO("RS-11", "Braničevo"),
   SUMADIJA("RS-12", "Šumadija");

   private final String code;
   private final String title;

   Region(String code, String title) {
      this.code = code;
      this.title = title;
   }

   public String code() {
      return code;
   }

   public String title() {
      return title;
   }
}
