package com.bc.calvalus.binning;

import org.junit.Test;

import java.util.Arrays;

import static java.lang.Math.*;
import static org.junit.Assert.*;


public class TemporalBinnerTest {
    @Test
    public void testTemporalBinning() {
        MyBinStore store = new MyBinStore();
        TemporalBinner<MyBin> temporalBinner = new TemporalBinner<MyBin>(store);

        MyBin b1 = new MyBin(1);
        b1.addObservation(new MyObservation(0, 0, 1.1));
        b1.addObservation(new MyObservation(0, 0, 1.2));
        b1.addObservation(new MyObservation(0, 0, 1.3));
        b1.finish();
        MyBin b2 = new MyBin(2);
        b2.addObservation(new MyObservation(0, 0, 2.1));
        b2.addObservation(new MyObservation(0, 0, 2.2));
        b2.addObservation(new MyObservation(0, 0, 2.3));
        b2.finish();
        MyBin b3 = new MyBin(3);
        b3.addObservation(new MyObservation(0, 0, 3.1));
        b3.addObservation(new MyObservation(0, 0, 3.2));
        b3.addObservation(new MyObservation(0, 0, 3.3));
        b3.finish();

        temporalBinner.consumeSlice(0, Arrays.asList(b1, b2, b3));
        assertEquals(3, store.binMap.size());
        MyBin actualB2 = store.binMap.get(2);
        assertNotNull(actualB2);
        assertEquals(3, actualB2.numObservations);
        assertEquals(sqrt(3), actualB2.weight, 1e-5);
        assertEquals((log(2.1) + log(2.2) + log(2.3)) / sqrt(3), actualB2.sumX, 1e-5);

        b2 = new MyBin(2);
        b2.addObservation(new MyObservation(0, 0, 1.1));
        b2.addObservation(new MyObservation(0, 0, 1.2));
        b2.finish();
        b3 = new MyBin(3);
        b3.addObservation(new MyObservation(0, 0, 2.1));
        b3.addObservation(new MyObservation(0, 0, 2.2));
        b3.finish();
        MyBin b4 = new MyBin(4);
        b4.addObservation(new MyObservation(0, 0, 3.1));
        b4.addObservation(new MyObservation(0, 0, 3.2));
        b4.finish();

        temporalBinner.consumeSlice(0, Arrays.asList(b2, b3, b4));
        assertEquals(4, store.binMap.size());
        actualB2 = store.binMap.get(2);
        assertNotNull(actualB2);
        assertEquals(3 + 2, actualB2.numObservations);
        assertEquals(sqrt(3) + sqrt(2), actualB2.weight, 1e-5);
        assertEquals((log(2.1) + log(2.2) + log(2.3)) / sqrt(3)
                + (log(1.1) + log(1.2)) / sqrt(2), actualB2.sumX, 1e-5);
    }

}
