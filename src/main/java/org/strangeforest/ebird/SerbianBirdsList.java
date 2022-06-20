package org.strangeforest.ebird;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.stream.*;

import com.fasterxml.jackson.core.type.*;
import com.fasterxml.jackson.databind.*;
import com.jayway.jsonpath.*;
import net.minidev.json.*;
import okhttp3.*;
import org.jsoup.*;
import org.slf4j.*;

import static java.util.Comparator.*;

public class SerbianBirdsList {

   private static final boolean PERSONALIZED = false;
   private static final String EBIRD_API_USERNAME = "MjUxODE0Nw==";
   private static final String EBIRD_API_TOKEN = "jfekjedvescr";

   private static final OkHttpClient CLIENT = new OkHttpClient();
   private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

   private static final Logger log = LoggerFactory.getLogger(SerbianBirdsList.class);

   public static void main(String[] args) throws IOException {
      System.out.println("Fetching Serbian bird species");
      var species = recognizedSpecies("RS");
      System.out.println("Enriching species data");
      species = enrichSpecies(species);
      species = sortSpecies(species);
      System.out.println("\nSerbian Birds:");
      System.out.println("No,Code,Name,EnglishName,SciName,Status,ObsCount,FamilyName,FamilySciName,Order");
      species.forEach(s ->
         System.out.printf("%1$s,%2$s,%3$s,%4$s,%5$s,%6$s,%7$d,%8$s,%9$s,%10$s%n",
            csvCell(s.number), s.code,
            s.name, csvCell(s.taxonomy.comName), csvCell(s.taxonomy.sciName),
            csvCell(s.status), s.obsCounts.obsCount,
            csvCell(s.taxonomy.familyComName), csvCell(s.taxonomy.familySciName), csvCell(s.taxonomy.order)
         )
      );
   }

   private static Stream<Species> recognizedSpecies(String countryCode) throws IOException {
      return Jsoup.connect("https://ebird.org/region/%1$s?yr=all&m=&rank=lrec&hs_sortBy=date&hs_o=asc".formatted(countryCode))
         .cookie("I18N_LANGUAGE", "sr")
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
            return new Species(number, code, name, Taxonomy.EMPTY, null, ObservationCounts.EMPTY);
         })
         .filter(Species::isRecognized);
   }

   private static Stream<Species> enrichSpecies(Stream<Species> species) {
      var ticker = new Ticker(1, 100);
      return species.parallel()
         .map(s -> {
            var taxonomy = getTaxonomy(s.code);
            var consStatus = getConservationStatus(s.code);
            var obsCounts = getObservationCount(s.code);
            ticker.tick();
            return new Species(s.number, s.code, s.name, taxonomy, consStatus, obsCounts);
         });
   }

   private static Stream<Species> sortSpecies(Stream<Species> species) {
      return species.toList().stream()
         .sorted(comparing(Species::number, nullsLast(naturalOrder())));
   }

   private static Taxonomy getTaxonomy(String speciesCode) {
      return fetchData(
         speciesCode, new Request.Builder()
            .url("https://api.ebird.org/v2/ref/taxonomy/ebird?fmt=json&species=%1$s".formatted(speciesCode))
            .addHeader("X-eBirdApiToken", EBIRD_API_TOKEN)
            .build(),
         body -> {
            var taxonomies = OBJECT_MAPPER.readValue(body.byteStream(), Taxonomy.TYPE_REF);
            return !taxonomies.isEmpty() ? taxonomies.get(0) : Taxonomy.EMPTY;
         },
         Taxonomy.EMPTY, "taxonomy"
      );

   }

   private static ConservationStatus getConservationStatus(String speciesCode) {
      return fetchData(
         speciesCode, new Request.Builder()
            .url("https://species.birds.cornell.edu/bow/api/v1/auxspecies/%1$s?category=conservation_status".formatted(speciesCode))
            .build(),
         body -> {
            var statusArray = (JSONArray)JsonPath.parse(body.string()).read("$[?(@.fieldName == 'IUCN_status')].value");
            if (statusArray.isEmpty())
               return null;
            var status = statusArray.get(0).toString();
            if (status.startsWith("IUCN_"))
               status = status.substring(5);
            return ConservationStatus.valueOf(status);
         },
         null, "conservation status"
      );
   }

   private static ObservationCounts getObservationCount(String speciesCode) {
      return fetchData(
         speciesCode, new Request.Builder()
            .url("https://api.ebird.org/v2/product/obsstats/%1$s/RS?username=%2$s".formatted(speciesCode, PERSONALIZED ? EBIRD_API_USERNAME : ""))
            .addHeader("X-eBirdApiToken", EBIRD_API_TOKEN)
            .build(),
         body -> OBJECT_MAPPER.readValue(body.byteStream(), ObservationCounts.class),
         ObservationCounts.EMPTY, "observation count"
      );
   }

   private static <R> R fetchData(String speciesCode, Request request, ThrowingFunction<ResponseBody, R> mapper, R defValue, String message) {
      try (var response = CLIENT.newCall(request).execute()) {
         var code = response.code();
         if (code != 200) {
            log.error("HTTP Status {} fetching {} for {}", code, message, speciesCode);
            return defValue;
         }
         var body = response.body();
         if (body == null) {
            log.error("Empty body fetching {} for {}", message, speciesCode);
            return defValue;
         }
         return mapper.apply(body);
      }
      catch (Exception ex) {
         log.error("Error fetching {} for {}", message, speciesCode, ex);
         return defValue;
      }
   }

   @FunctionalInterface
   private interface ThrowingFunction<T, R> {
      R apply(T t) throws Exception;
   }


   private static String csvCell(String s) {
      return s != null ? (s.contains(",") ? "\"" + s + "\"" : s) : "";
   }

   private static String csvCell(Object obj) {
      return obj != null ? obj.toString() : "";
   }

   private record Species(Integer number, String code, String name, Taxonomy taxonomy, ConservationStatus status, ObservationCounts obsCounts) {

      public boolean isRecognized() {
         return !code.isBlank();
      }
   }

   private record Taxonomy(String comName, String sciName, String familyComName, String familySciName, String order) {
      private static final TypeReference<List<Taxonomy>> TYPE_REF = new TypeReference<>() {};
      private static final Taxonomy EMPTY = new Taxonomy(null, null, null, null, null);
   }

   private enum ConservationStatus { LC, NT, VU, EN, CR }

   private record ObservationCounts(long obsCount, long userObsCount, long userYearObsCount) {
      private static final ObservationCounts EMPTY = new ObservationCounts(-1L, -1L, -1L);
   }

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
