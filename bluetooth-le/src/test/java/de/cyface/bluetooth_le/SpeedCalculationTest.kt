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
package de.cyface.bluetooth_le

import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * A parameterized test for different value combinations on the calculation carried out by the [ ].
 */
@RunWith(Parameterized::class)
class SpeedCalculationTest {

    /**
     * The test value for the previous 'Cumulative Wheel Revolution'.
     */
    @Parameterized.Parameter
    var prevCwr: Long = 0

    /**
     * The test value for the current 'Cumulative Wheel Revolution'.
     */
    @Parameterized.Parameter(1)
    var curCwr: Long = 0

    /**
     * The test value for the previous 'Last Wheel Event Time'.
     */
    @Parameterized.Parameter(2)
    var prevLwet = 0

    /**
     * The test value for the previous 'Last Wheel Event Time'.
     */
    @Parameterized.Parameter(3)
    var curLwet = 0

    /**
     * The test value for the vehicles wheel circumference.
     */
    @Parameterized.Parameter(4)
    var wheelCircumference = 0.0

    /**
     * An expected exception or `null` if no exception but a valid result is expected.
     */
    @Parameterized.Parameter(5)
    var expectedException: Class<out Exception?>? = null

    /**
     * The expected speed value or anything (usually 0.0) if an exception is expected.
     */
    @Parameterized.Parameter(6)
    var expectedSpeed = 0.0

    /**
     * A rule for an expected exception. The default value expects no exception. This is overwritten
     * if the 5th parameter is not set to `null`.
     */
    @get:Rule
    var thrown = ExpectedException.none()

    /**
     * Runs the actual test code.
     */
    @Test
    fun testSpeedCalculation() {
        if (expectedException != null) {
            thrown.expect(expectedException)
        }
        MatcherAssert.assertThat(
            CyclingCadenceSpeedMeasurementDevice.calcSpeed(
                prevCwr,
                curCwr,
                prevLwet,
                curLwet,
                wheelCircumference
            ),
            CoreMatchers.`is`(Matchers.closeTo(expectedSpeed, 0.001))
        )
    }

    companion object {
        /**
         * @return A collection containing one array per value combination of input and expected values
         * to create a test from.
         */
        @Parameterized.Parameters
        fun data(): Collection<Array<Any?>> {
            return listOf(
                arrayOf(
                    -1L,
                    -1L,
                    -1,
                    -1,
                    -1.0,
                    IllegalArgumentException::class.java,
                    0.0
                ),
                arrayOf(0L, 0L, 0, 0, 0.0, IllegalArgumentException::class.java, 0.0),
                arrayOf(10L, 20L, 0, 10, 1.0, null, 1024.0),
                arrayOf(10L, 20L, 10, 0, 1.0, null, 0.156)
            )
        }
    }
}