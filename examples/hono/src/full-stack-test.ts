import { serve } from "@hono/node-server";
import assert from "node:assert/strict";
import type { AddressInfo } from "node:net";
import { createFetchClient } from "../build/generated/client/index.js";
import {
  createHonoRouter,
  type HonoHandlers,
} from "../build/generated/hono/index.js";

const handlers: HonoHandlers = {
  async getItem(input) {
    return {
      itemId: input.path.itemId,
      name: `Item ${input.path.itemId}`,
    };
  },
  async upload(input) {
    return {
      matchId: input.path.matchId,
      requestId: input.requestId,
      tenantId: input.tenantId,
      traceId: input.traceId,
      source: input.query.source,
      events: input.body.events,
    };
  },
};

const app = createHonoRouter(handlers);
const server = serve({
  fetch: app.fetch,
  hostname: "127.0.0.1",
  port: 0,
});

try {
  await new Promise<void>((resolve) => {
    server.once("listening", resolve);
  });

  const address = server.address() as AddressInfo;
  const client = createFetchClient(`http://127.0.0.1:${address.port}`);
  const item = await client.getItem({ itemId: "full-stack" });

  assert.deepEqual(item, {
    itemId: "full-stack",
    name: "Item full-stack",
    statusCode: 200,
  });

  const upload = await client.upload({
    matchId: "match-client",
    requestId: "request-from-client",
    tenantId: "tenant-client",
    source: "generated-client",
    events: ["player.ready"],
  });

  assert.deepEqual(upload, {
    matchId: "match-client",
    requestId: "request-from-client",
    tenantId: "tenant-client",
    source: "generated-client",
    events: ["player.ready"],
    statusCode: 200,
  });

  const rawResponse = await fetch(
    `http://127.0.0.1:${address.port}/matches/match-raw/events?source=raw-http`,
    {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-request-id": "request-from-raw-http",
        "X-TENANT-ID": "tenant-raw",
        "x-TrAcE-iD": "trace-raw",
      },
      body: JSON.stringify({ events: ["match.started"] }),
    },
  );

  assert.equal(rawResponse.status, 200);
  assert.deepEqual(await rawResponse.json(), {
    matchId: "match-raw",
    requestId: "request-from-raw-http",
    tenantId: "tenant-raw",
    traceId: "trace-raw",
    source: "raw-http",
    events: ["match.started"],
  });

  const missingHeaderResponse = await fetch(
    `http://127.0.0.1:${address.port}/matches/match-invalid/events`,
    {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "X-Tenant-ID": "tenant-invalid",
      },
      body: JSON.stringify({ events: ["match.started"] }),
    },
  );

  assert.equal(missingHeaderResponse.status, 400);
  assert.deepEqual(await missingHeaderResponse.json(), {
    message: "Request body failed validation.",
    issues: [
      {
        path: "requestId",
        message: "Invalid input: expected string, received undefined",
      },
    ],
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
