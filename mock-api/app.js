/*
 * Copyright 2020 Cyface GmbH
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
/**
 * Entry point for the mock API Express server.
 * <p>
 * You can start it with: `node app.js`
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 2.15.0
 */
const express = require('express')
const connectApiMocker = require('connect-api-mocker')

const port = 9113
const app = express()

app.use('/api/v2', connectApiMocker('api/v2'))

console.log(`Mock API is up and running at: http://localhost:${port}`)
app.listen(port)
