import assert from 'node:assert/strict';
import type { AddressInfo } from 'node:net';
import { serve } from '@hono/node-server';
import { createFetchClient } from '../build/generated/client/index.js';
import { createHonoRouter, type HonoHandlers } from '../build/generated/hono/index.js';

const handlers: HonoHandlers = {
  async getItem(input) {
    return {
      itemId: input.path.itemId,
      name: `Item ${input.path.itemId}`,
    };
  },
};

const app = createHonoRouter(handlers);
const server = serve({
  fetch: app.fetch,
  hostname: '127.0.0.1',
  port: 0,
});

try {
  await new Promise<void>((resolve) => {
    server.once('listening', resolve);
  });

  const address = server.address() as AddressInfo;
  const client = createFetchClient(`http://127.0.0.1:${address.port}`);
  const item = await client.getItem({ itemId: 'full-stack' });

  assert.deepEqual(item, {
    itemId: 'full-stack',
    name: 'Item full-stack',
    statusCode: 200,
  });
} finally {
  await new Promise<void>((resolve, reject) => {
    server.close((error) => {
      if (error) {
        reject(error);
        return;
      }
      resolve();
    });
  });
}
