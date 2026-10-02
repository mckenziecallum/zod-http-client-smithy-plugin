import { serve } from "@hono/node-server";
import assert from "node:assert/strict";
import type { AddressInfo } from "node:net";
import { createFetchClient, MissingSequence } from "../build/generated/client/index.js";
import { createHonoRouter } from "../build/generated/hono/index.js";
import { handlers } from "./full-stack-handlers.js";

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
  const errorResponse = await fetch(`http://127.0.0.1:${address.port}/items/missing-sequence`);
  assert.equal(errorResponse.status, 409);
  assert.deepEqual(await errorResponse.json(), {
    _kind: "MissingSequence",
    __type: "MissingSequence",
    message: "Expected event 1 but received 3",
    expectedSequence: 1,
    receivedSequence: 3,
    details: { reason: "out of order" },
  });
  await assert.rejects(client.getItem({ itemId: "missing-sequence" }), (error: unknown) => {
    assert.ok(error instanceof MissingSequence);
    assert.equal(error.expectedSequence, 1);
    assert.equal(error.receivedSequence, 3);
    assert.deepEqual(error.details, { reason: "out of order" });
    return true;
  });
  const internalResponse = await fetch(`http://127.0.0.1:${address.port}/items/internal-error`);
  assert.equal(internalResponse.status, 500);
  assert.deepEqual(await internalResponse.json(), {
    message: "Internal failure",
    _kind: "InternalServerError",
  });

  const namedResponse = await fetch(`http://127.0.0.1:${address.port}/items/named-error`);
  assert.equal(namedResponse.status, 409);
  assert.deepEqual(await namedResponse.json(), {
    message: "Sequence mismatch",
    expectedSequence: 0,
    receivedSequence: 3,
    _kind: "MissingSequence",
    __type: "MissingSequence",
  });
  const invalidErrorResponse = await fetch(`http://127.0.0.1:${address.port}/items/invalid-modeled-error`);
  assert.equal(invalidErrorResponse.status, 500);
  assert.deepEqual(await invalidErrorResponse.json(), {
    message: "InternalServerError",
    _kind: "InternalServerError",
  });

  const malformedResponse = await fetch(`http://127.0.0.1:${address.port}/matches/invalid/events`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: "{",
  });
  assert.equal(malformedResponse.status, 400);
  assert.deepEqual(await malformedResponse.json(), {
    message: "Request body must be valid JSON.",
    issues: [{ path: "body", message: "Could not parse JSON request body." }],
  });

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
