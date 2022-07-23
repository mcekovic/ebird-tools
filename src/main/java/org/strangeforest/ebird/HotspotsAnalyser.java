package org.strangeforest.ebird;

import java.io.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

import org.jsoup.*;
import org.slf4j.*;
import org.strangeforest.ebird.domain.Period;
import org.strangeforest.ebird.domain.*;
import org.strangeforest.ebird.util.*;

import static java.util.Comparator.*;
import static org.strangeforest.ebird.util.PrintUtil.*;
import static org.strangeforest.ebird.util.Util.*;

public class HotspotsAnalyser {

   private final Region region;
   private final int hotspotCount;
   private final Period targetPeriod;
   private final MediaType targetAction;
   private final int minChecklists;
   private final Predicate<TargetSpecies> speciesFilter;
   private final Month beginMonth;
   private final Month endMonth;
   private final String eBirdSessionId;
   private final Ticker ticker;

   private static final String EBIRD_SESSIONID_PROPERTY = "ebird.sessionid";
   private static final long PAUSE = 3L;

   private static final Logger log = LoggerFactory.getLogger(HotspotsAnalyser.class);

   public HotspotsAnalyser(
      Region region, int hotspotCount,
      Period targetPeriod, MediaType targetAction, int monthSpan, int minChecklists,
      Predicate<TargetSpecies> speciesFilter
   ) {
      this.region = region;
      this.hotspotCount = hotspotCount;
      this.targetPeriod = targetPeriod;
      this.targetAction = targetAction;
      this.minChecklists = minChecklists;
      this.speciesFilter = speciesFilter;
      var today = LocalDate.now();
      var currentMonth = today.getMonth();
      beginMonth = currentMonth.minus((monthSpan - (monthSpan % 2 == 0 && today.getDayOfMonth() <= currentMonth.length(false) / 2 ? 0 : 1)) / 2);
      endMonth = beginMonth.plus(monthSpan - 1);
      eBirdSessionId = System.getProperty(EBIRD_SESSIONID_PROPERTY);
      if (eBirdSessionId == null)
         throw new IllegalArgumentException("System property %1$s must be specified".formatted(EBIRD_SESSIONID_PROPERTY));
      ticker = new Ticker(1, 100);
   }

   public void topHotspots(PrintStream out) throws IOException {
      printBanner(System.out, "Top Hotspots by Score");
      out.printf("%nFinding top hotspots for region: %1$s%n", region.title());
      var hotspots = getHotspots();
      out.printf("Finding target species for %1$d hotspots: period %2$s, action %3$s, using period %4$s-%5$s%n", hotspots.size(), targetPeriod, targetAction, beginMonth, endMonth);
      hotspots = sortHotspots(enrichHotspots(hotspots));
      out.printf("%n%1$d hotspots processed%n", ticker.ticks());
      for (var hotspot : hotspots) {
         out.printf("%n%1$s - %2$.2f (%3$d)%n", hotspot.name(), hotspot.score(), hotspot.checklists());
         for (var targetSpecies : hotspot.targetSpecies())
            out.printf("  %1$s - %2$.2f%%%n", targetSpecies.name(), targetSpecies.frequency() * 100.0);
      }
   }

   private List<Hotspot> getHotspots() throws IOException {
      return Jsoup.connect("https://ebird.org/region/%1$s/hotspots".formatted(region.code()))
         .get()
         .select("#results tr.Table-row span.Heading > a")
         .stream()
         .limit(hotspotCount)
         .map(hotspot -> {
            var href = hotspot.attributes().get("href");
            var id = href.substring(9, href.indexOf('?'));
            var name = hotspot.text();
            return new Hotspot(id, name);
         })
         .toList();
   }

   private List<Hotspot> enrichHotspots(List<Hotspot> hotspots) {
      return hotspots.stream()
         .map(hotspot -> {
            var enrichedHotspot = enrichHotspot(hotspot);
            ticker.tick();
            return enrichedHotspot;
         })
         .filter(Hotspot::isScored)
         .toList();
   }

   private Hotspot enrichHotspot(Hotspot hotspot) {
      hotspot = doEnrichHotspot(hotspot, MediaType.NONE);
      if (targetAction != MediaType.NONE)
         hotspot = doEnrichHotspot(hotspot, targetAction);
      return hotspot;
   }

   private Hotspot doEnrichHotspot(Hotspot hotspot, MediaType mediaType) {
      var url = "https://ebird.org/targets?r1=%1$s&bmo=%2$d&emo=%3$d&r2=world&t2=%4$s&mediaType=%5$s".formatted(
         hotspot.id(),
         beginMonth.getValue(),
         endMonth.getValue(),
         targetPeriod.code(),
         mediaType.code()
      );

      try {
         TimeUnit.SECONDS.sleep(PAUSE);
         var doc = Jsoup.connect(url)
            .cookie("EBIRD_SESSIONID", eBirdSessionId)
            .get();
         var info = doc.select("p.u-text-3").text();
         var checklists = Integer.parseInt(info.substring(9, info.indexOf(' ', 9)));
         if (checklists < minChecklists)
            return hotspot;
         var targetSpecies = doc.select("#targets-results div.ResultsStats")
            .stream().map(species -> {
               var name = species.select("div.SpecimenHeader a").textNodes().get(0).text().trim();
               var frequencyStr = species.select("div.ResultsStats-stats div.StatsIcon").text();
               var frequency = Double.parseDouble(frequencyStr.substring(0, frequencyStr.indexOf('%'))) * 0.01;
               return new TargetSpecies(name, frequency);
            })
            .filter(speciesFilter)
            .toList();
         if (!hotspot.targetSpecies().isEmpty())
            targetSpecies = mergeAndSort(hotspot.targetSpecies(), targetSpecies, comparing(TargetSpecies::frequency).reversed());
         return new Hotspot(hotspot.id(), hotspot.name(), checklists, targetSpecies);
      }
      catch (Exception ex) {
         log.error("Error fetching target species for hotspot {}", hotspot.id(), ex);
         return hotspot;
      }
   }

   private static List<Hotspot> sortHotspots(List<Hotspot> hotspots) {
      var sortedHotspots = new ArrayList<>(hotspots);
      sortedHotspots.sort(comparing(Hotspot::score).reversed());
      return sortedHotspots;
   }
}
