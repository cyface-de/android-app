package de.cyface.app.utils.trips.incentives

import net.openid.appauth.AuthorizationException

interface AuthExceptionListener {
    fun onException(e: AuthorizationException)
}