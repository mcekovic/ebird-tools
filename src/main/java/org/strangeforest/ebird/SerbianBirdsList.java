package org.strangeforest.ebird;

import com.fasterxml.jackson.databind.*;
import com.jayway.jsonpath.*;
import net.minidev.json.*;
import okhttp3.*;
import org.jsoup.*;
import org.slf4j.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.stream.*;

import static java.util.Comparator.*;

public class SerbianBirdsList {

   private static final String EBIRD_API_USERNAME = "MjUxODE0Nw==";
   private static final String EBIRD_API_TOKEN = "jfekjedvescr";

   private static final OkHttpClient CLIENT = new OkHttpClient();
   private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

   private static final Logger log = LoggerFactory.getLogger(SerbianBirdsList.class);

   public static void main(String[] args) throws IOException {
      System.out.println("Fetching Serbian bird species");
      var species = recognizedSpecies("RS");
      System.out.println("Enriching species data");
      species = enrichObservationCounts(species);
      species = sortSpecies(species);
      System.out.println("\nSerbian Birds:");
      System.out.println("No,Code,Name,Status,ObsCount");
      species.forEach(s ->
         System.out.printf("%1$s,%2$s,%3$s,%4$s,%5$s%n", s.number != null ? s.number : "", s.code, s.name, s.status != null ? s.status : "", s.obsCount != null ? s.obsCount : "")
      );
   }

   private static Stream<Species> recognizedSpecies(String countryCode) throws IOException {
      return Jsoup.connect("https://ebird.org/region/%1$s?yr=all&m=&rank=lrec&hs_sortBy=date&hs_o=asc".formatted(countryCode))
         .get()
         .select("section.Observation--placeSpeciesObserved")
         .stream().map(species -> {
            var numberStr = species.select("div.Observation-numberObserved > span:nth-child(2)").text();
            if (numberStr.endsWith("."))
               numberStr = numberStr.substring(0, numberStr.length() - 1);
            var number = !numberStr.isBlank() ? Integer.parseInt(numberStr) : null;
            var nameElement = species.select("div.Observation-species");
            var code = nameElement.select("a").attr("data-species-code");
            var name = nameElement.select("span.Heading-main").text();
            return new Species(number, code, name, null, null);
         })
         .filter(Species::isRecognized);
   }

   private static Stream<Species> enrichObservationCounts(Stream<Species> species) {
      var ticker = new Ticker(1, 100);
      return species.parallel()
         .map(s -> {
            var obsCount = Optional.ofNullable(getObservationCount(s.code)).map(ObservationCounts::obsCount).orElse(null);
            var status = getConservationStatus(s.code);
            ticker.tick();
            return new Species(s.number, s.code, s.name, status, obsCount);
         });
   }

   private static Stream<Species> sortSpecies(Stream<Species> species) {
      return species.toList().stream()
         .sorted(comparing(Species::number, nullsLast(naturalOrder())));
   }

   private static ConservationStatus getConservationStatus(String speciesCode) {
      var request = new Request.Builder()
         .url("https://species.birds.cornell.edu/bow/api/v1/auxspecies/%1$s?category=conservation_status".formatted(speciesCode))
         .build();

      try (var response = CLIENT.newCall(request).execute()) {
         var code = response.code();
         if (code != 200) {
            log.error("HTTP Status {} fetching conservation status for {}", code, speciesCode);
            return null;
         }
         var body = response.body();
         if (body == null) {
            log.error("Empty body fetching conservation status for {}", speciesCode);
            return null;
         }
         var statusArray = (JSONArray)JsonPath.parse(body.string()).read("$[?(@.fieldName == 'IUCN_status')].value");
         if (statusArray.isEmpty())
            return null;
         var status = statusArray.get(0).toString();
         if (status.startsWith("IUCN_"))
            status = status.substring(5);
         return ConservationStatus.valueOf(status);
      }
      catch (Exception ex) {
         log.error("Error fetching conservation status for {}", speciesCode, ex);
         return null;
      }
   }

   private static ObservationCounts getObservationCount(String speciesCode) {
      var request = new Request.Builder()
         .url("https://api.ebird.org/v2/product/obsstats/%1$s/RS?username=%2$s".formatted(speciesCode, EBIRD_API_USERNAME))
         .addHeader("X-eBirdApiToken", EBIRD_API_TOKEN)
         .build();

      try (var response = CLIENT.newCall(request).execute()) {
         var code = response.code();
         if (code != 200) {
            log.error("HTTP Status {} fetching observation count for {}", code, speciesCode);
            return null;
         }
         var body = response.body();
         if (body == null) {
            log.error("Empty body fetching observation count for {}", speciesCode);
            return null;
         }
         return OBJECT_MAPPER.readValue(body.byteStream(), ObservationCounts.class);
      }
      catch (Exception ex) {
         log.error("Error fetching observation count for {}", speciesCode, ex);
         return null;
      }
   }

   private enum ConservationStatus { LC, NT, VU, EN, CR }

   private record Species(Integer number, String code, String name, ConservationStatus status, Long obsCount) {
      public boolean isRecognized() {
         return !code.isBlank();
      }
   }

   private record ObservationCounts(long obsCount, long userObsCount, long userYearObsCount) {}

   private static final class Ticker {
      private final int printEvery;
      private final int newLineAfter;
      private final AtomicInteger current;

      private Ticker(int printEvery, int newLineAfter) {
         this.printEvery = printEvery;
         this.newLineAfter = printEvery * newLineAfter;
         current = new AtomicInteger();
      }

      public void tick() {
         var curr = current.incrementAndGet();
         if (curr % printEvery == 0) {
            if (curr > 1 && curr % newLineAfter == 1)
               System.out.println();
            System.out.print('.');
         }
      }
   }
}
