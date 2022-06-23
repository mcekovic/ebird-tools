package org.strangeforest.ebird.domain;

public record Species(
   Integer number,
   String code,
   String name,
   Taxonomy taxonomy,
   ConservationStatus status,
   ObservationCounts obsCounts
) {

   public boolean isRecognized() {
      return !code.isBlank();
   }
}
