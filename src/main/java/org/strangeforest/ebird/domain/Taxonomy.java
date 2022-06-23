package org.strangeforest.ebird.domain;

import java.util.*;

import com.fasterxml.jackson.core.type.*;

public record Taxonomy(
   String comName,
   String sciName,
   String familyComName,
   String familySciName,
   String order
) {

   public static final TypeReference<List<Taxonomy>> TYPE_REF = new TypeReference<>() {};
   public static final Taxonomy EMPTY = new Taxonomy(null, null, null, null, null);
}
