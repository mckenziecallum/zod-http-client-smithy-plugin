import type { HonoHandlers } from "../build/generated/hono/index.js";

export const handlers: HonoHandlers = {
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
    if (input.path.itemId === "missing-sequence") {
      throw {
        _kind: "MissingSequence",
        message: "Expected event 1 but received 3",
        expectedSequence: 1,
        receivedSequence: 3,
        details: { reason: "out of order", secret: "nested secret" },
        secret: "must not leak",
      };
    }
    if (input.path.itemId === "named-error") {
      throw Object.assign(new Error("Sequence mismatch"), {
        name: "MissingSequence",
        expectedSequence: 0,
        receivedSequence: 3,
        secret: "must not leak",
      });
    }
    if (input.path.itemId === "invalid-modeled-error") {
      throw { _kind: "MissingSequence", message: "Invalid handler error", secret: "must not leak" };
    }
    if (input.path.itemId === "internal-error") {
      throw Object.assign(new Error("Internal failure"), { secret: "must not leak" });
    }
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
      source: input.source,
      events: input.body.events,
    };
  },
};

