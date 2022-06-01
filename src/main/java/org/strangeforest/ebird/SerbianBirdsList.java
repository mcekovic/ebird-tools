package org.strangeforest.ebird;

import org.jsoup.*;

import java.io.*;

public class SerbianBirdsList {

   public static void main(String[] args) throws IOException {
      var doc = Jsoup.connect("https://ebird.org/region/RS?yr=all&m=&rank=lrec&hs_sortBy=date&hs_o=asc").get();
      var speciesList = doc.select("section.Observation--placeSpeciesObserved");
      for (var species : speciesList) {
         var numberStr = species.select("div.Observation-numberObserved > span:nth-child(2)").text();
         if (numberStr.endsWith("."))
            numberStr = numberStr.substring(0, numberStr.length() - 1);
         if (numberStr.isBlank())
            continue;
         var number = Integer.parseInt(numberStr);
         var nameElement = species.select("div.Observation-species");
         var name = nameElement.select("span.Heading-main").text();
//         var sciName = nameElement.select("span.Heading-sub.Heading-sub--inline.Heading-sub--sci").text();
         var url = nameElement.select("a").attr("href");
         int observations = number <= 1 ? getObservations(url) : 0;
         System.out.printf("%1$s: %2$s - %3$d%n", number, name, observations);
      }
   }

   private static int getObservations(String url) throws IOException {
      var e = Jsoup.connect("https://ebird.org" + url).get()
         .select("table.Table--speciesStats");
      System.out.println(e);
      var observations = e.select("tbody:nth-child(2) > tr:nth-child(2) > td:nth-child(2) > span:nth-child(1)").text();
      System.out.println(observations);
      return Integer.parseInt(observations);
   }
}
