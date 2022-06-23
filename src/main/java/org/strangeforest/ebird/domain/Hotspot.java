package org.strangeforest.ebird.domain;

import java.util.*;

public record Hotspot(String id, String name, double score, int checklists, List<TargetSpecies> targetSpecies) {

   public boolean isScored() {
      return score > 0.0;
   }
}
