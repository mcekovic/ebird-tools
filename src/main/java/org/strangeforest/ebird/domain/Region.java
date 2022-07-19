package org.strangeforest.ebird.domain;

public enum Region {

   SERBIA("RS", "Serbia"),
   BEOGRAD("RS-00", "Beograd"),
   VOJVODINA("RS-VO", "Vojvodina"),
   MACVA("RS-08", "Mačva"),
   KOLUBARA("RS-09", "Kolubara"),
   PODUNAVLJE("RS-10", "Podunavlje"),
   BRANICEVO("RS-11", "Braničevo"),
   SUMADIJA("RS-12", "Šumadija"),
   POMORAVLJE("RS-13", "Pomoravlje"),
   BOR("RS-14", "Bor"),
   ZAJECAR("RS-15", "Zaječar"),
   ZLATIBOR("RS-16", "Zlatibor"),
   MORAVICA("RS-17", "Moravica"),
   RASKA("RS-18", "Raška"),
   RASINA("RS-19", "Rasina"),
   NISAVA("RS-20", "Nišava"),
   TOPLICA("RS-21", "Toplica"),
   PIROT("RS-22", "Pirot"),
   JABLANICA("RS-23", "Jablanica"),
   PCINJA("RS-24", "Pčinja");

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
