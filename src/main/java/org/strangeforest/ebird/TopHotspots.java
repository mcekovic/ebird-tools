package org.strangeforest.ebird;

import java.io.*;
import java.time.*;
import java.util.function.*;
import java.util.stream.*;

import org.jsoup.*;
import org.slf4j.*;
import org.strangeforest.ebird.domain.Period;
import org.strangeforest.ebird.domain.*;
import org.strangeforest.ebird.util.*;

import static java.util.Comparator.*;
import static org.strangeforest.ebird.util.PrintUtil.*;
import static org.strangeforest.ebird.util.Util.*;

public class TopHotspots {

   private static final Region REGION = Region.SERBIA;
   private static final Period TARGET_PERIOD = Period.LIFE;
   private static final MediaType TARGET_ACTION = MediaType.NONE;
   private static final int MONTH_SPAN = 2;
   private static final int HOTSPOT_COUNT = 25;
   private static final int MIN_CHECKLISTS = 2;
   private static final Predicate<TargetSpecies> SPECIES_FILTER = TargetSpecies.filter();

   private static final String EBIRD_SESSIONID_PROPERTY = "ebird.sessionid";

   private static final Logger log = LoggerFactory.getLogger(TopHotspots.class);

   public static void main(String[] args) throws IOException {
      System.out.println("Finding hotspots for region: " + REGION.title());
      var hotspots = getHotspots(REGION.code())
         .limit(HOTSPOT_COUNT);
      hotspots = sortHotspots(enrichHotspots(hotspots, TARGET_PERIOD, TARGET_ACTION, MONTH_SPAN).filter(Hotspot::isScored));
      System.out.println();
      printBanner("Top Hotspots by Score");
      hotspots.forEach(hotspot -> {
         System.out.printf("%n%1$s - %2$.2f (%3$d)%n", hotspot.name(), hotspot.score(), hotspot.checklists());
         hotspot.targetSpecies().forEach(targetSpecies -> System.out.printf("  %1$s - %2$.2f%%%n", targetSpecies.name(), targetSpecies.frequency() * 100.0));
      });
   }

   private static Stream<Hotspot> getHotspots(String regionCode) throws IOException {
      return Jsoup.connect("https://ebird.org/region/%1$s/hotspots".formatted(regionCode))
         .get()
         .select("#results tr.Table-row span.Heading > a")
         .stream().map(hotspot -> {
            var href = hotspot.attributes().get("href");
            var id = href.substring(9, href.indexOf('?'));
            var name = hotspot.text();
            return new Hotspot(id, name);
         });
   }

   private static Stream<Hotspot> enrichHotspots(Stream<Hotspot> hotspots, Period targetPeriod, MediaType targetAction, int monthSpan) {
      var eBirdSessionId = System.getProperty(EBIRD_SESSIONID_PROPERTY);
      if (eBirdSessionId == null)
         throw new IllegalArgumentException("System property %1$s must be specified".formatted(EBIRD_SESSIONID_PROPERTY));

      var today = LocalDate.now();
      var currentMonth = today.getMonth();
      var beginMonth = currentMonth.minus((monthSpan - (monthSpan % 2 == 0 && today.getDayOfMonth() <= currentMonth.length(false) / 2 ? 0 : 1)) / 2);
      var endMonth = beginMonth.plus(monthSpan - 1);

      System.out.printf("Finding target species for hotspots: period %1$s, action %2$s, using period %3$s-%4$s%n", targetPeriod, targetAction, beginMonth, endMonth);

      var ticker = new Ticker(1, 100);
      return hotspots.map(hotspot -> {
         var enrichedHotspot = enrichHotspot(hotspot, beginMonth, endMonth, targetPeriod, targetAction, eBirdSessionId);
         ticker.tick();
         return enrichedHotspot;
      });
   }

   private static Hotspot enrichHotspot(Hotspot hotspot, Month beginMonth, Month endMonth, Period period, MediaType mediaType, String eBirdSessionId) {
      hotspot = doEnrichHotspot(hotspot, beginMonth, endMonth, period, MediaType.NONE, eBirdSessionId);
      if (mediaType != MediaType.NONE)
         hotspot = doEnrichHotspot(hotspot, beginMonth, endMonth, period, mediaType, eBirdSessionId);
      return hotspot;
   }

   private static Hotspot doEnrichHotspot(Hotspot hotspot, Month beginMonth, Month endMonth, Period period, MediaType mediaType, String eBirdSessionId) {
      var url = "https://ebird.org/targets?r1=%1$s&bmo=%2$d&emo=%3$d&r2=world&t2=%4$s&mediaType=%5$s".formatted(
         hotspot.id(),
         beginMonth.getValue(),
         endMonth.getValue(),
         period.code(),
         mediaType.code()
      );

      try {
         var doc = Jsoup.connect(url)
            .cookie("EBIRD_SESSIONID", eBirdSessionId)
            .get();
         var info = doc.select("p.u-text-3").text();
         var checklists = Integer.parseInt(info.substring(9, info.indexOf(' ', 9)));
         if (checklists < MIN_CHECKLISTS)
            return hotspot;
         var targetSpecies = doc.select("#targets-results div.ResultsStats")
            .stream().map(species -> {
               var name = species.select("div.SpecimenHeader a").textNodes().get(0).text().trim();
               var frequencyStr = species.select("div.ResultsStats-stats div.StatsIcon").text();
               var frequency = Double.parseDouble(frequencyStr.substring(0, frequencyStr.indexOf('%'))) * 0.01;
               return new TargetSpecies(name, frequency);
            })
            .filter(SPECIES_FILTER)
            .toList();
         if (!hotspot.targetSpecies().isEmpty())
            targetSpecies = mergeAndSort(hotspot.targetSpecies(), targetSpecies, comparing(TargetSpecies::frequency).reversed());
         return new Hotspot(hotspot.id(), hotspot.name(), checklists, targetSpecies);
      }
      catch (IOException ex) {
         log.error("Error fetching target species for hotspot {}", hotspot.id(), ex);
         return hotspot;
      }
   }

   private static Stream<Hotspot> sortHotspots(Stream<Hotspot> hotspots) {
      return hotspots.toList().stream()
         .sorted(comparing(Hotspot::score).reversed());
   }
}
