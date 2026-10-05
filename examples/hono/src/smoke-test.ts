import assert from 'node:assert/strict';
import { z } from 'zod';

import test from 'node:test';
import { handlers as fullStackHandlers } from './full-stack-handlers.js';
import { createHonoRouter, type HonoHandlers } from '../build/generated/hono/index.js';

const handlers: HonoHandlers = {
  getContent: fullStackHandlers.getContent,
  createGeneration: fullStackHandlers.createGeneration,
  async inspectBindings(input) {
    return {
      limit: input.limit,
      includeArchived: input.includeArchived,
      retryCount: input.retryCount,
      offset: input.offset,
      ratio: input.ratio,
      enabled: input.enabled,
      pageSize: input.pageSize,
      useCache: input.useCache,
    };
  },
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

const sensitiveMessage = 'Upstream request failed; internal-sensitive-diagnostic';
const unexpectedErrors: unknown[] = [
  new Error(sensitiveMessage),
  { get _kind() { throw new Error(sensitiveMessage); } },
  { message: sensitiveMessage, _kind: 'PrivateDatabaseError', stack: sensitiveMessage, databaseKey: sensitiveMessage },
  { _kind: 'ValidationError', message: sensitiveMessage, issues: [{ message: sensitiveMessage }] },
  Object.assign(new Error(sensitiveMessage), { name: 'ValidationError' }),
  new z.ZodError([{ code: 'custom', path: [], message: sensitiveMessage }]),
  sensitiveMessage,
  null,
];
for (const internalError of unexpectedErrors) {
  let loggedError: unknown;
  let loggedCorrelationId: string | undefined;
  let loggedPath: string | undefined;
  const failingApp = createHonoRouter({
    ...handlers,
    getItem() { throw internalError; },
  }, {
    async onUnexpectedError(error, { correlationId, context }) {
      loggedError = error;
      loggedCorrelationId = correlationId;
      loggedPath = context.req.path;
    },
  });
  const internalResponse = await failingApp.request('/items/example-item');
  assert.equal(internalResponse.status, 500);
  const internalBody = await internalResponse.text();
  assert.ok(!internalBody.includes('internal-sensitive-diagnostic'), internalBody);
  assert.deepEqual(JSON.parse(internalBody), { message: 'InternalServerError', _kind: 'InternalServerError' });
  assert.equal(loggedError, internalError);
  assert.equal(loggedPath, '/items/example-item');
  assert.match(loggedCorrelationId!, /^[0-9a-f-]{36}$/);
  assert.equal(internalResponse.headers.get('X-Correlation-ID'), loggedCorrelationId);
}

const failingLoggerApp = createHonoRouter({
  ...handlers,
  getItem() { throw new Error(sensitiveMessage); },
}, {
  async onUnexpectedError() { throw new Error(sensitiveMessage); },
});
const failingLoggerResponse = await failingLoggerApp.request('/items/example-item');
assert.equal(failingLoggerResponse.status, 500);
assert.deepEqual(await failingLoggerResponse.json(), { message: 'InternalServerError', _kind: 'InternalServerError' });

for (const { description, body, message, issuePath } of [
  { description: 'malformed JSON', body: '{', message: 'Request body must be valid JSON.', issuePath: 'body' },
  { description: 'missing body member', body: '{}', message: 'Request body failed validation.', issuePath: 'events' },
  { description: 'wrong body member type', body: '{"events":[123]}', message: 'Request body failed validation.', issuePath: 'events.0' },
]) {
  test(`${description} returns request validation details without calling the handler or server logging hook`, async () => {
    let handlerCalled = false;
    let logged = false;
    const validationApp = createHonoRouter({
      ...handlers,
      upload(input, context) {
        handlerCalled = true;
        return handlers.upload(input, context);
      },
    }, {
      onUnexpectedError() { logged = true; },
    });
    const response = await validationApp.request('/matches/example/events', {
      method: 'POST',
      headers: { 'content-type': 'application/json', 'X-Request-ID': 'request', 'X-Tenant-ID': 'tenant' },
      body,
    });
    assert.equal(response.status, 400);
    const responseBody = await response.json();
    assert.equal(responseBody.message, message);
    assert.deepEqual(responseBody.issues.map((issue: { path: string }) => issue.path), [issuePath]);
    if (issuePath === 'body') {
      assert.deepEqual(responseBody.issues, [{ path: 'body', message: 'Could not parse JSON request body.' }]);
    }
    assert.equal(handlerCalled, false);
    assert.equal(logged, false);
  });
}

let modeledErrorLogged = false;
const modeledErrorApp = createHonoRouter({
  ...handlers,
  getItem() { throw { _kind: 'ItemNotFound', message: 'Item does not exist.' }; },
}, {
  onUnexpectedError() { modeledErrorLogged = true; },
});
const modeledErrorResponse = await modeledErrorApp.request('/items/example-item');
assert.equal(modeledErrorResponse.status, 404);
const modeledErrorBody = await modeledErrorResponse.json() as { message: string; _kind: string };
assert.equal(modeledErrorBody.message, 'Item does not exist.');
assert.equal(modeledErrorBody._kind, 'ItemNotFound');
assert.equal(modeledErrorLogged, false);

for (const { description, name } of [
  { description: 'numeric name', name: 123 },
  { description: 'missing name', name: undefined },
  { description: 'sensitive invalid value', name: { secret: sensitiveMessage } },
]) {
  test(`${description} in handler output returns a sanitized 500 and logs validation diagnostics`, async () => {
    let handlerCalled = false;
    let loggedError: unknown;
    let loggedCorrelationId: string | undefined;
    let loggedPath: string | undefined;
    const invalidOutputApp = createHonoRouter({
      ...handlers,
      getItem(input) {
        handlerCalled = true;
        assert.equal(input.path.itemId, 'example-item');
        // Simulate a runtime defect despite the generated handler's static return type.
        return { itemId: input.path.itemId, name } as unknown as ReturnType<HonoHandlers['getItem']>;
      },
    }, {
      async onUnexpectedError(error, { correlationId, context }) {
        loggedError = error;
        loggedCorrelationId = correlationId;
        loggedPath = context.req.path;
      },
    });
    const response = await invalidOutputApp.request('/items/example-item');
    assert.equal(handlerCalled, true);
    assert.equal(response.status, 500);
    assert.deepEqual(await response.json(), { message: 'InternalServerError', _kind: 'InternalServerError' });
    assert.ok(loggedError instanceof z.ZodError);
    assert.deepEqual(loggedError.issues.map(issue => issue.path), [['name']]);
    assert.equal(loggedPath, '/items/example-item');
    assert.match(loggedCorrelationId!, /^[0-9a-f-]{36}$/);
    assert.equal(response.headers.get('X-Correlation-ID'), loggedCorrelationId);
  });
}

test('output validation stays sanitized when the logging hook fails', async () => {
  let loggedError: unknown;
  const invalidOutputApp = createHonoRouter({
    ...handlers,
    getItem() { return { itemId: 'example', name: 123 } as unknown as ReturnType<HonoHandlers['getItem']>; },
  }, {
    async onUnexpectedError(error) {
      loggedError = error;
      throw new Error(sensitiveMessage);
    },
  });
  const response = await invalidOutputApp.request('/items/example-item');
  assert.ok(loggedError instanceof z.ZodError);
  assert.equal(response.status, 500);
  assert.deepEqual(await response.json(), { message: 'InternalServerError', _kind: 'InternalServerError' });
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
