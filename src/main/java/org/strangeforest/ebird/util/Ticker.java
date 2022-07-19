package org.strangeforest.ebird.util;

import java.util.concurrent.atomic.*;

public final class Ticker {

   private final int printEvery;
   private final int newLineAfter;
   private final AtomicInteger ticks;

   public Ticker(int printEvery, int newLineAfter) {
      this.printEvery = printEvery;
      this.newLineAfter = printEvery * newLineAfter;
      ticks = new AtomicInteger();
   }

   public void tick() {
      var curr = ticks.incrementAndGet();
      if (curr % printEvery == 0) {
         if (curr > 1 && curr % newLineAfter == 1)
            System.out.println();
         System.out.print('.');
      }
   }

   public int ticks() {
      return ticks.get();
   }
}
