import assert from 'node:assert/strict';
import { createHonoRouter, type HonoHandlers } from '../build/generated/hono/index.js';

const handlers: HonoHandlers = {
  async getItem(input) {
    return {
      itemId: input.path.itemId,
      name: 'Example item',
    };
  },
};

const app = createHonoRouter(handlers);
const response = await app.request('/items/example-item');

assert.equal(response.status, 200);
assert.deepEqual(await response.json(), {
  itemId: 'example-item',
  name: 'Example item',
});
