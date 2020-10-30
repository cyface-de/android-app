/*
 * Copyright 2017 Cyface GmbH
 *
 * This file is part of the Cyface App for Android.
 *
 * The Cyface App for Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The Cyface App for Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the Cyface App for Android. If not, see <http://www.gnu.org/licenses/>.
 */
package de.cyface.bluetooth_le;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.closeTo;
import static org.junit.Assert.assertThat;

import java.util.Arrays;
import java.util.Collection;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * A parameterized test for different value combinations on the calculation carried out by the {@link
 * CyclingCadenceSpeedMeasurementDevice}.
 */
@RunWith(Parameterized.class)
public final class SpeedCalculationTest {
    /**
     * @return A collection containing one array per value combination of input and expected values
     *         to create a test from.
     */
    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                {-1L, -1L, -1, -1, -1.0, IllegalArgumentException.class, 0.0},
                {0L, 0L, 0, 0, 0.0, IllegalArgumentException.class, 0.0},
                {10L, 20L, 0, 10, 1.0, null, 1024.0},
                {10L, 20L, 10, 0, 1.0, null, 0.156}
        });
    }

    /**
     * The test value for the previous 'Cumulative Wheel Revolution'.
     */
    @Parameterized.Parameter
    public long prevCwr;
    /**
     * The test value for the current 'Cumulative Wheel Revolution'.
     */
    @Parameterized.Parameter(1)
    public long curCwr;
    /**
     * The test value for the previous 'Last Wheel Event Time'.
     */
    @Parameterized.Parameter(2)
    public int prevLwet;
    /**
     * The test value for the previous 'Last Wheel Event Time'.
     */
    @Parameterized.Parameter(3)
    public int curLwet;
    /**
     * The test value for the vehicles wheel circumference.
     */
    @Parameterized.Parameter(4)
    public double wheelCircumference;
    /**
     * An expected exception or {@code null} if no exception but a valid result is expected.
     */
    @Parameterized.Parameter(5)
    public Class<? extends Exception> expectedException;
    /**
     * The expected speed value or anything (usually 0.0) if an exception is expected.
     */
    @Parameterized.Parameter(6)
    public double expectedSpeed;

    /**
     * A rule for an expected exception. The default value expects no exception. This is overwritten
     * if the 5th parameter is not set to {@code null}.
     */
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    /**
     * Runs the actual test code.
     */
    @Test
    public void testSpeedCalculation() {
        if (expectedException != null) {
            thrown.expect(expectedException);
        }
        assertThat(
                CyclingCadenceSpeedMeasurementDevice.calcSpeed(prevCwr, curCwr, prevLwet, curLwet, wheelCircumference),
                is(closeTo(expectedSpeed, 0.001)));
    }
}
