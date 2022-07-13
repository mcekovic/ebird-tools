package org.strangeforest.ebird.domain;

import java.util.*;
import java.util.function.*;

public record TargetSpecies(String name, double frequency) {

   public static Predicate<TargetSpecies> filter(String... names) {
      return s -> names.length == 0 || Set.of(names).contains(s.name);
   }
}
