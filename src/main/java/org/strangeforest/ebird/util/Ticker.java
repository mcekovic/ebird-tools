package org.strangeforest.ebird.util;

import java.util.concurrent.atomic.*;

public final class Ticker {

   private final int printEvery;
   private final int newLineAfter;
   private final AtomicInteger current;

   public Ticker(int printEvery, int newLineAfter) {
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
