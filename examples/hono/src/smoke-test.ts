import assert from 'node:assert/strict';
import test from 'node:test';
import { createHonoRouter, type HonoHandlers } from '../build/generated/hono/index.js';

const handlers: HonoHandlers = {
  async getItem(input) {
    return {
      itemId: input.path.itemId,
      name: 'Example item',
    };
  },
  async upload(input) {
    return {
      matchId: input.path.matchId,
      requestId: input.requestId,
      tenantId: input.tenantId,
      traceId: input.traceId,
      source: input.source,
      events: input.body.events,
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

const validHeaders = {
  'content-type': 'application/json',
  'X-Request-ID': 'actual-request',
  'X-Tenant-ID': 'actual-tenant',
};
const validOutput = {
  matchId: 'actual-match',
  requestId: 'actual-request',
  tenantId: 'actual-tenant',
  source: 'actual-source',
  events: ['actual-event'],
};

test('body properties cannot override path, query, or header bindings', async () => {
  const response = await app.request('/matches/actual-match/events?source-channel=actual-source', {
    method: 'POST',
    headers: validHeaders,
    body: JSON.stringify({
      events: ['actual-event'],
      matchId: 'body-match',
      requestId: 'body-request',
      tenantId: 'body-tenant',
      traceId: 'body-trace',
      source: 'body-source',
    }),
  });
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), validOutput);
});

test('unmodeled query fields cannot override path or supply header and body members', async () => {
  const query = new URLSearchParams({
    'source-channel': 'actual-source',
    source: 'query-source',
    matchId: 'query-match',
    requestId: 'query-request',
    tenantId: 'query-tenant',
    traceId: 'query-trace',
    events: 'query-event',
  });
  const response = await app.request(`/matches/actual-match/events?${query}`, {
    method: 'POST',
    headers: validHeaders,
    body: JSON.stringify({ events: ['actual-event'] }),
  });
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), validOutput);
});

for (const location of ['body', 'query'] as const) {
  test(`${location} cannot supply a missing required header`, async () => {
    const response = await app.request(
      `/matches/actual-match/events${location === 'query' ? '?requestId=injected-request' : ''}`,
      {
        method: 'POST',
        headers: { 'content-type': 'application/json', 'X-Tenant-ID': 'actual-tenant' },
        body: JSON.stringify({
          events: ['actual-event'],
          ...(location === 'body' ? { requestId: 'injected-request' } : {}),
        }),
      },
    );
    assert.equal(response.status, 400);
    const body = await response.json();
    assert.deepEqual(body.issues.map((issue: { path: string }) => issue.path), ['requestId']);
  });
}

test('JSON cannot supply an absent bound query member', async () => {
  const response = await app.request('/matches/actual-match/events', {
    method: 'POST',
    headers: validHeaders,
    body: JSON.stringify({ events: ['actual-event'], source: 'body-source', 'source-channel': 'body-wire-source' }),
  });
  assert.equal(response.status, 200);
  const { source, ...expected } = validOutput;
  assert.deepEqual(await response.json(), expected);
});

test('query member name cannot replace the declared query wire name', async () => {
  const response = await app.request('/matches/actual-match/events?source=injected-source', {
    method: 'POST',
    headers: validHeaders,
    body: JSON.stringify({ events: ['actual-event'] }),
  });
  assert.equal(response.status, 200);
  const { source, ...expected } = validOutput;
  assert.deepEqual(await response.json(), expected);
});

test('query cannot supply an absent required body member', async () => {
  const response = await app.request('/matches/actual-match/events?events=injected-event', {
    method: 'POST',
    headers: validHeaders,
    body: '{}',
  });
  assert.equal(response.status, 400);
  const body = await response.json();
  assert.deepEqual(body.issues.map((issue: { path: string }) => issue.path), ['events']);
});
