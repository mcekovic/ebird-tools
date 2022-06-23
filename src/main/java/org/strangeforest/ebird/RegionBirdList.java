package org.strangeforest.ebird;

import java.io.*;
import java.util.stream.*;

import com.fasterxml.jackson.databind.*;
import com.jayway.jsonpath.*;
import net.minidev.json.*;
import okhttp3.*;
import org.jsoup.*;
import org.slf4j.*;
import org.strangeforest.ebird.domain.*;
import org.strangeforest.ebird.util.*;

import static java.util.Comparator.*;
import static org.strangeforest.ebird.util.PrintUtil.*;
import static org.strangeforest.ebird.util.Util.*;

public class RegionBirdList {

   private static final Region REGION = Region.SERBIA;
   private static final boolean PERSONALIZED = false;

   private static final OkHttpClient CLIENT = new OkHttpClient();
   private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

   private static final Logger log = LoggerFactory.getLogger(RegionBirdList.class);

   public static void main(String[] args) throws IOException {
      System.out.println("Fetching bird species for region: " + REGION.title());
      var species = recognizedSpecies(REGION.code());
      System.out.println("Enriching species data");
      species = sortSpecies(enrichSpecies(REGION.code(), species));
      System.out.println();
      printBanner("Birds of " + REGION.title());
      System.out.println("No,Code,Name,EnglishName,SciName,Status,ObsCount,UserObsCount,FamilyName,FamilySciName,Order");
      species.forEach(s ->
         System.out.printf("%1$s,%2$s,%3$s,%4$s,%5$s,%6$s,%7$d,%8$d,%9$s,%10$s,%11$s%n",
            csvCell(s.number()), s.code(),
            s.name(), csvCell(s.taxonomy().comName()), csvCell(s.taxonomy().sciName()),
            csvCell(s.status()), s.obsCounts().obsCount(), s.obsCounts().userObsCount(),
            csvCell(s.taxonomy().familyComName()), csvCell(s.taxonomy().familySciName()), csvCell(s.taxonomy().order())
         )
      );
   }

   private static Stream<Species> recognizedSpecies(String regionCode) throws IOException {
      return Jsoup.connect("https://ebird.org/region/%1$s?yr=all&m=&rank=lrec&hs_sortBy=date&hs_o=asc".formatted(regionCode))
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

   private static Stream<Species> enrichSpecies(String regionCode, Stream<Species> species) {
      var ticker = new Ticker(1, 100);
      return species.parallel()
         .map(s -> {
            var taxonomy = getTaxonomy(s.code());
            var consStatus = getConservationStatus(s.code());
            var obsCounts = getObservationCount(regionCode, s.code());
            ticker.tick();
            return new Species(s.number(), s.code(), s.name(), taxonomy, consStatus, obsCounts);
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

   private static ObservationCounts getObservationCount(String regionCode, String speciesCode) {
      var username = PERSONALIZED ? padUsername(EBIRD_API_USERNAME) : "";
      return fetchData(
         speciesCode, new Request.Builder()
            .url("https://api.ebird.org/v2/product/obsstats/%1$s/%2$s?username=%3$s".formatted(speciesCode, regionCode, username))
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
}
