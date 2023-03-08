package de.cyface.app.r4r

import de.cyface.datacapturing.CyfaceDataCapturingService

/**
 * Interface which defines the dependencies implemented by the [MainActivity] to be accessible from
 * the `Fragments`.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 7.5.0
 */
interface ServiceProvider {

    val capturingService: CyfaceDataCapturingService
}