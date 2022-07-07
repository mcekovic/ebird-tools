package org.strangeforest.ebird.domain;

import java.util.*;
import java.util.function.*;

public record TargetSpecies(String name, double frequency) {

   public static Predicate<TargetSpecies> ALL = s -> true;

   public static Predicate<TargetSpecies> filter(String name) {
      return s -> Objects.equals(s.name, name);
   }
}
