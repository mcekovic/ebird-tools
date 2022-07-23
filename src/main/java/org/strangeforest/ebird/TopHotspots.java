package org.strangeforest.ebird;

import java.io.*;
import java.util.function.*;

import org.apache.commons.io.output.*;
import org.strangeforest.ebird.domain.*;

public class TopHotspots {

   private static final Region REGION = Region.SERBIA;
   private static final Period TARGET_PERIOD = Period.LIFE;
   private static final MediaType TARGET_ACTION = MediaType.NONE;
   private static final int MONTH_SPAN = 2;
   private static final int HOTSPOT_COUNT = 50;
   private static final int MIN_CHECKLISTS = 2;
   private static final Predicate<TargetSpecies> SPECIES_FILTER = TargetSpecies.filter();

   public static void main(String[] args) throws IOException {
      var analyser = new HotspotsAnalyser(REGION, HOTSPOT_COUNT, TARGET_PERIOD, TARGET_ACTION, MONTH_SPAN, MIN_CHECKLISTS, SPECIES_FILTER);
      analyser.topHotspots(System.out);
//      topHotspotsForRegions();
   }

   private static void topHotspotsForRegions() throws IOException {
      for (var region : Region.values()) {
         var analyser = new HotspotsAnalyser(region, HOTSPOT_COUNT, TARGET_PERIOD, TARGET_ACTION, MONTH_SPAN, MIN_CHECKLISTS, SPECIES_FILTER);
         try (var out = new FileOutputStream("top-hotspots/%1$s.txt".formatted(region.title()))) {
            analyser.topHotspots(new PrintStream(new TeeOutputStream(System.out, out)));
            System.out.println("\n");
         }
      }
   }
}
