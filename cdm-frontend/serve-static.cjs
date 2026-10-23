const http = require('http')
const fs = require('fs')
const net = require('net')
const path = require('path')

const root = path.join(__dirname, 'dist')
const port = Number(process.env.PORT || 80)
const backendHost = process.env.BACKEND_HOST || 'cdm-backend'
const backendPort = Number(process.env.BACKEND_PORT || 8080)

const contentTypes = {
  '.css': 'text/css; charset=utf-8',
  '.html': 'text/html; charset=utf-8',
  '.ico': 'image/x-icon',
  '.js': 'text/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.svg': 'image/svg+xml',
  '.txt': 'text/plain; charset=utf-8',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2'
}

function sendFile(filePath, res) {
  const ext = path.extname(filePath).toLowerCase()
  res.writeHead(200, {
    'Content-Type': contentTypes[ext] || 'application/octet-stream',
    'Cache-Control': 'no-store, no-cache, must-revalidate'
  })
  fs.createReadStream(filePath).pipe(res)
}

function proxyHttp(req, res) {
  const proxyReq = http.request({
    hostname: backendHost,
    port: backendPort,
    path: req.url,
    method: req.method,
    headers: {
      ...req.headers,
      host: req.headers.host || `${backendHost}:${backendPort}`,
      connection: req.headers.connection || 'keep-alive'
    }
  }, proxyRes => {
    res.writeHead(proxyRes.statusCode || 502, proxyRes.headers)
    proxyRes.pipe(res)
  })

  proxyReq.on('error', error => {
    res.writeHead(502, { 'Content-Type': 'application/json; charset=utf-8' })
    res.end(JSON.stringify({ error: 'Bad Gateway', message: error.message }))
  })

  req.pipe(proxyReq)
}

const server = http.createServer((req, res) => {
  if (req.url?.startsWith('/api/') || req.url?.startsWith('/uploads/')) {
    proxyHttp(req, res)
    return
  }

  const requestPath = decodeURIComponent((req.url || '/').split('?')[0])
  const safePath = path.normalize(requestPath).replace(/^(\.\.[\\/])+/, '')
  const filePath = path.join(root, safePath === '/' ? 'index.html' : safePath)

  fs.stat(filePath, (error, stats) => {
    if (!error && stats.isFile()) {
      sendFile(filePath, res)
      return
    }

    const indexPath = path.join(root, 'index.html')
    fs.stat(indexPath, (indexError, indexStats) => {
      if (indexError || !indexStats.isFile()) {
        res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' })
        res.end('Not Found')
        return
      }
      sendFile(indexPath, res)
    })
  })
})

server.on('upgrade', (req, socket, head) => {
  if (!req.url?.startsWith('/ws/')) {
    socket.destroy()
    return
  }

  const upstream = net.connect(backendPort, backendHost, () => {
    const headers = Object.entries(req.headers)
      .map(([key, value]) => `${key}: ${value}`)
      .join('\r\n')

    upstream.write(
      `${req.method} ${req.url} HTTP/${req.httpVersion}\r\n${headers}\r\n\r\n`
    )

    if (head?.length) {
      upstream.write(head)
    }

    socket.pipe(upstream).pipe(socket)
  })

  upstream.on('error', () => {
    socket.destroy()
  })

  socket.on('error', () => {
    upstream.destroy()
  })
})

server.listen(port, '0.0.0.0', () => {
  console.log(`Static server listening on port ${port}`)
})
