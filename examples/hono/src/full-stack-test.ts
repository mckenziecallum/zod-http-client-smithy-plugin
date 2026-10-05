import axios from "axios";
import { z } from "zod";
import { serve } from "@hono/node-server";
import assert from "node:assert/strict";
import type { AddressInfo } from "node:net";
import { createAxiosClient, createFetchClient, CreateGenerationOutput, fromAxios, fromFetch, MissingSequence } from "../build/generated/client/index.js";
import { createHonoRouter } from "../build/generated/hono/index.js";
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
const server = serve({
  async fetch(request) {
    // Exercise client validation against a peer that omits a required wire header.
    if (new URL(request.url).pathname === "/generations") {
      const input = await request.clone().json() as { id: string };
      if (input.id === "missing-response-header") {
        return Response.json({ id: input.id }, { status: 202 });
      }
    }
    return app.fetch(request);
  },
  hostname: "127.0.0.1",
  port: 0,
});

try {
  await new Promise<void>((resolve) => {
    server.once("listening", resolve);
  });

  const address = server.address() as AddressInfo;
  const baseUrl = `http://127.0.0.1:${address.port}`;
  const generationResponse = await fetch(`${baseUrl}/generations`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ id: "gen_123" }),
  });
  assert.equal(generationResponse.status, 202);
  assert.equal(generationResponse.headers.get("Location"), "/generations/gen_123");
  assert.equal(generationResponse.headers.get("X-Trace-ID"), null);
  assert.deepEqual(await generationResponse.json(), { id: "gen_123" });

  const client = createFetchClient(`http://127.0.0.1:${address.port}`);
  const axiosClient = createAxiosClient(axios.create({ baseURL: baseUrl }));
  for (const api of [client, axiosClient]) {
    assert.deepEqual(await api.createGeneration({ id: "gen_123" }), {
      id: "gen_123",
      location: "/generations/gen_123",
      statusCode: 202,
    });
    assert.deepEqual(await api.createGeneration({ id: "with-optional" }), {
      id: "with-optional",
      location: "/generations/with-optional",
      traceId: "trace-123",
      statusCode: 202,
    });
    await assert.rejects(api.createGeneration({ id: "missing-response-header" }), z.ZodError);
  }
  const optionalResponse = await fetch(`${baseUrl}/generations`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ id: "with-optional" }),
  });
  assert.equal(optionalResponse.status, 202);
  assert.equal(optionalResponse.headers.get("x-trace-id"), "trace-123");
  assert.deepEqual(await optionalResponse.json(), { id: "with-optional" });

  const invalidOutputResponse = await fetch(`${baseUrl}/generations`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ id: "missing-required" }),
  });
  assert.equal(invalidOutputResponse.status, 500);
  assert.equal(invalidOutputResponse.headers.get("Location"), null);
  assert.deepEqual(await invalidOutputResponse.json(), { message: "InternalServerError", _kind: "InternalServerError" });

  // Axios adapters may preserve header casing, unlike native Fetch Headers.
  for (const name of ["Location", "location", "LoCaTiOn"]) {
    const response = fromAxios({
      data: { id: "mixed-case" },
      headers: { [name]: "/generations/mixed-case", "x-TrAcE-iD": "mixed-trace" },
      status: 202,
      statusText: "Accepted",
      config: { headers: new axios.AxiosHeaders() },
    });
    assert.deepEqual(CreateGenerationOutput.parse(response), {
      id: "mixed-case", location: "/generations/mixed-case", traceId: "mixed-trace", statusCode: 202,
    });
    const fetchResponse = await fromFetch(Response.json({ id: "mixed-case" }, {
      status: 202,
      headers: { [name]: "/generations/mixed-case", "x-TrAcE-iD": "mixed-trace" },
    }));
    assert.deepEqual(CreateGenerationOutput.parse(fetchResponse), CreateGenerationOutput.parse(response));
  }
  assert.equal(CreateGenerationOutput.safeParse({ body: { id: "missing" } }).success, false);

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
