package org.strangeforest.ebird;

import java.io.*;
import java.time.*;
import java.util.*;
import java.util.stream.*;

import org.jsoup.*;
import org.slf4j.*;

import static java.util.Comparator.*;

public class TopHotSpots {

   private static final String COUNTRY_CODE = "RS";
   private static final int HOT_SPOT_COUNT = 25;
   private static final String EBIRD_SESSION_ID = "5F03A2DAB9D0EB0E775180DB058928B7";

   private static final List<String> BLACK_LISTED_HOT_SPOTS = List.of(
      "Barje, Pirot",
      "Bovansko jezero (reservoir)--Mrestilište (ušće Moravice u akumulaciju)",
      "Kompenzacioni bazen, Pirot",
      "Krupačko jezero (lake), Krupac",
      "Liplje, selo",
      "Peštersko polje--Karajukića Bunari",
      "Raisnjevo",
      "Velika Grabovnica--šljunkara"
   );

   private static final Logger log = LoggerFactory.getLogger(TopHotSpots.class);

   public static void main(String[] args) throws IOException {
      System.out.println("Fetching hot spots for country " + COUNTRY_CODE);
      var hotSpots = getHotSpots(COUNTRY_CODE)
         .filter(hotSpot -> !BLACK_LISTED_HOT_SPOTS.contains(hotSpot.name))
         .limit(HOT_SPOT_COUNT);
      System.out.println("Enriching hot spot data");
      hotSpots = sortHotSpots(enrichHotSpots(hotSpots));
      System.out.printf("%n*** Top Hot Spots by Score ***%n%n");
      hotSpots.forEach(hotSpot -> {
         System.out.printf("%n%1$s - %2$.2f%n", hotSpot.name, hotSpot.score());
         hotSpot.targetSpecies.forEach(targetSpecies -> System.out.printf("  %1$s - %2$.2f%%%n", targetSpecies.name, targetSpecies.frequency * 100.0));
      });
   }

   private static Stream<HotSpot> enrichHotSpots(Stream<HotSpot> hotSpots) {
      var ticker = new Ticker(1, 100);
      return hotSpots.map(hotSpot -> {
         var targetSpecies = getTargetSpecies(hotSpot.id);
         ticker.tick();
         return new HotSpot(hotSpot.id, hotSpot.name, targetSpecies);
      });
   }

   private static Stream<HotSpot> sortHotSpots(Stream<HotSpot> hotSpots) {
      return hotSpots.toList().stream()
         .sorted(comparing(HotSpot::score).reversed());
   }

   private static Stream<HotSpot> getHotSpots(String countryCode) throws IOException {
      return Jsoup.connect("https://ebird.org/region/%1$s/hotspots".formatted(countryCode))
         .get()
         .select("#results tr.Table-row span.Heading > a")
         .stream().map(hotSpot -> {
            var href = hotSpot.attributes().get("href");
            var id = href.substring(9, href.indexOf('?'));
            var name = hotSpot.text();
            return new HotSpot(id, name, List.of());
         });
   }

   private static List<TargetSpecies> getTargetSpecies(String hotSpotId) {
      var today = LocalDate.now();
      var beginMonth = today.getMonth();
      if (today.getDayOfMonth() <= beginMonth.length(false) / 2)
         beginMonth = beginMonth.minus(1);
      var endMonth = beginMonth.plus(1);
      try {
         return Jsoup.connect("https://ebird.org/targets?r1=%1$s&bmo=%2$d&emo=%3$d&r2=world&t2=life&mediaType=".formatted(hotSpotId, beginMonth.getValue(), endMonth.getValue()))
            .cookie("EBIRD_SESSIONID", EBIRD_SESSION_ID)
            .get()
            .select("#targets-results div.ResultsStats")
            .stream().map(targetSpecies -> {
               var name = targetSpecies.select("div.SpecimenHeader a").textNodes().get(0).text().trim();
               var frequencyStr = targetSpecies.select("div.ResultsStats-stats div.StatsIcon").text();
               var frequency = Double.parseDouble(frequencyStr.substring(0, frequencyStr.indexOf('%'))) * 0.01;
               return new TargetSpecies(name, frequency);
            })
            .toList();
      }
      catch (IOException ex) {
         log.error("Error fetching target species for hotspot {}", hotSpotId, ex);
         return List.of();
      }
   }

   private record HotSpot(String id, String name, List<TargetSpecies> targetSpecies) {

      private double score() {
         return targetSpecies.stream().mapToDouble(TargetSpecies::frequency).sum();
      }
   }

   private record TargetSpecies(String name, double frequency) {}
}
