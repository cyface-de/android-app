/**
 * Mocked Authentication API which returns success and the expected header (Auth Token).
 */
module.exports = (request, response) => {
  response
    .writeHead(200, {
      Authorization: 'eyMockAuthToken',
      'Content-Length': 0
    })
    .end()
}
