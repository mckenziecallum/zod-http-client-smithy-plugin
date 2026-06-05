import { serve } from '@hono/node-server';
import { createHonoRouter, type HonoHandlers } from './generated';

const handlers: HonoHandlers = {
  async getItem(input) {
    return {
      itemId: input.path.itemId,
      name: 'Example item',
    };
  },
};

serve({
  fetch: createHonoRouter(handlers).fetch,
  port: 3000,
});
