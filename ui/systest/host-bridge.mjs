/**
 * Forwards localhost inside the container to the Polarion running on the host, on the port
 * BRIDGE_PORT names. The wrapper takes that port from POLARION_URL, so it is 80 by default, 443
 * under https, and whatever the URL names when it names one.
 *
 * Polarion answers only requests whose Host header matches its base.url, which is
 * http://localhost; anything else is a 400. A browser writes the Host header from the URL it opens,
 * so the container has to reach Polarion as "localhost" rather than as host.docker.internal. This
 * plain TCP forward does exactly that and changes nothing else about the request.
 */
import net from 'node:net';

const target = process.env.BRIDGE_TARGET || 'host.docker.internal';
const port = Number(process.env.BRIDGE_PORT || 80);

const server = net.createServer((client) => {
  const upstream = net.connect(port, target);
  client.on('error', () => upstream.destroy());
  upstream.on('error', () => client.destroy());
  client.pipe(upstream).pipe(client);
});

server.listen(port, '127.0.0.1', () => console.log(`bridge: localhost:${port} -> ${target}:${port}`));
