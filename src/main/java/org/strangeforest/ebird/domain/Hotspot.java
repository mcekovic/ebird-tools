package org.strangeforest.ebird.domain;

import java.util.*;

public record Hotspot(String id, String name, double score, int checklists, List<TargetSpecies> targetSpecies) {

   public Hotspot(String id, String name) {
      this(id, name, 0, List.of());
   }

   public Hotspot(String id, String name, int checklists, List<TargetSpecies> targetSpecies) {
      this(id, name, score(targetSpecies), checklists, targetSpecies);
   }

   public boolean isScored() {
      return score > 0.0;
   }

   private static double score(Collection<TargetSpecies> targetSpecies) {
      return targetSpecies.stream().mapToDouble(TargetSpecies::frequency).sum();
   }
}
