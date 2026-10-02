import assert from 'node:assert/strict';
import { z } from 'zod';
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
      source: input.query.source,
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

const invalidJsonResponse = await app.request('/matches/example/events', { method: 'POST', body: '{' });
assert.equal(invalidJsonResponse.status, 400);
assert.deepEqual(await invalidJsonResponse.json(), {
  message: 'Request body must be valid JSON.',
  issues: [{ path: 'body', message: 'Could not parse JSON request body.' }],
});
const invalidInputResponse = await app.request('/matches/example/events', { method: 'POST', body: '{}' });
assert.equal(invalidInputResponse.status, 400);
const invalidInputBody = await invalidInputResponse.json() as { message: string; issues: unknown[] };
assert.equal(invalidInputBody.message, 'Request body failed validation.');
assert.ok(invalidInputBody.issues.length > 0);

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

const invalidOutputApp = createHonoRouter({
  ...handlers,
  getItem() { return { itemId: 'example', name: undefined! }; },
});
const invalidOutputResponse = await invalidOutputApp.request('/items/example-item');
assert.equal(invalidOutputResponse.status, 500);
assert.deepEqual(await invalidOutputResponse.json(), { message: 'InternalServerError', _kind: 'InternalServerError' });
