import { serve } from "@hono/node-server";
import assert from "node:assert/strict";
import type { AddressInfo } from "node:net";
import { createFetchClient, GetContentInput, MissingSequence } from "../build/generated/client/index.js";
import { createHonoRouter, type HonoHandlers } from "../build/generated/hono/index.js";
import { handlers } from "./full-stack-handlers.js";

const sensitiveError = new Error("Upstream request failed; internal-sensitive-diagnostic");
let loggedError: unknown;
let loggedCorrelationId: string | undefined;

const app = createHonoRouter({
  ...handlers,
  getItem(input, context) {
    if (input.path.itemId === "unexpected-error") throw sensitiveError;
    return handlers.getItem(input, context);
  },
}, {
  onUnexpectedError(error, { correlationId }) {
    loggedError = error;
    loggedCorrelationId = correlationId;
  },
});
const svg = '<svg xmlns="http://www.w3.org/2000/svg"><text>content</text></svg>';
app.get('/downloads/:contentId', (c) => c.body(svg, 200, { 'Content-Type': 'image/svg+xml' }));
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
  const baseUrl = `http://127.0.0.1:${address.port}`;
  const client = createFetchClient(baseUrl);
  assert.ok(!('getContent' in client));
  // @ts-expect-error The redirect handler must return the modeled Location member.
  const missingLocationHandler: HonoHandlers['getContent'] = () => ({});
  const invalidOutputApp = createHonoRouter({ ...handlers, getContent: missingLocationHandler });
  const missingLocation = await invalidOutputApp.request('/content/example', {
    headers: { 'X-Content-Access': 'allowed' },
  });
  assert.equal(missingLocation.status, 500);
  assert.equal(missingLocation.headers.get('location'), null);
  // This compile-time check ensures content cannot accidentally enter the JSON client.
  if (false) {
    // @ts-expect-error Redirect endpoints have no generated JSON client method.
    await client.getContent({ contentId: 'example', accessToken: 'allowed' });
  }
  const request = GetContentInput.parse({ contentId: 'example', accessToken: 'allowed' });
  const contentUrl = new URL(request.url, baseUrl);
  // Node's manual redirect mode exposes headers; browsers return opaqueredirect instead.
  const redirect = await fetch(contentUrl, { headers: request.headers, redirect: 'manual' });
  assert.equal(redirect.status, 303);
  assert.equal(redirect.headers.get('location'), '/downloads/example?token=example-short-lived-token');
  assert.equal(redirect.headers.get('cache-control'), 'no-store');
  assert.ok(!redirect.headers.get('content-type')?.includes('application/json'));
  assert.equal(await redirect.text(), '');
  const content = await fetch(contentUrl, { headers: request.headers, redirect: 'follow' });
  assert.equal(content.status, 200);
  assert.equal(content.redirected, true);
  assert.equal(content.headers.get('content-type'), 'image/svg+xml');
  assert.equal(await content.text(), svg);
  const unauthenticated = await fetch(contentUrl, { redirect: 'manual' });
  assert.equal(unauthenticated.status, 400);
  assert.equal(unauthenticated.headers.get('location'), null);
  const denied = await fetch(contentUrl, { headers: { 'X-Content-Access': 'denied' }, redirect: 'manual' });
  assert.equal(denied.status, 404);
  assert.equal(denied.headers.get('location'), null);
  const invalidRedirect = await fetch(new URL('/content/invalid-output', baseUrl), {
    headers: request.headers, redirect: 'manual',
  });
  assert.equal(invalidRedirect.status, 500);
  assert.equal(invalidRedirect.headers.get('location'), null);

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
    message: "InternalServerError",
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
    `http://127.0.0.1:${address.port}/matches/match-raw/events?source-channel=raw-http`,
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

  const unexpectedResponse = await fetch(`http://127.0.0.1:${address.port}/items/unexpected-error`);
  assert.equal(unexpectedResponse.status, 500);
  const internalBody = await unexpectedResponse.text();
  assert.ok(!internalBody.includes("internal-sensitive-diagnostic"), internalBody);
  assert.deepEqual(JSON.parse(internalBody), { message: "InternalServerError", _kind: "InternalServerError" });
  assert.equal(loggedError, sensitiveError);
  assert.match(loggedCorrelationId!, /^[0-9a-f-]{36}$/);
  assert.equal(unexpectedResponse.headers.get("X-Correlation-ID"), loggedCorrelationId);
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
