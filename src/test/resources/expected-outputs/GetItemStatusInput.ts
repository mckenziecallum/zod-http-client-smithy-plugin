import { z } from 'zod';

export const GetItemStatusInput = z.object({
  itemId: z.string(),
  enabled: z.boolean(),
  limit: z.number().int().optional(),
  resourceId: z.string().min(1).max(64).regex(/^[a-zA-Z0-9_-]+$/, { message: 'Must match the required format: ^[a-zA-Z0-9_-]+$' }).optional(),
  priority: z.number().int().min(1).max(10).optional(),
  retryCount: z.number().int().optional()
}).transform((v) => ({
  ...v,
  path: { itemId: v.itemId, enabled: v.enabled },
  headers: {
    ...(v.retryCount !== undefined && { 'X-Retry-Count': v.retryCount })
  },
  query: {
    ...(v.limit !== undefined && { 'limit': v.limit }),
    ...(v.resourceId !== undefined && { 'resourceId': v.resourceId }),
    ...(v.priority !== undefined && { 'priority': v.priority })
  },
  body: {},
  url: `/items/${encodeURIComponent(String(v.itemId))}/status/${encodeURIComponent(String(v.enabled))}`,
  method: 'GET' as const,
}));

export type GetItemStatusInput = z.output<typeof GetItemStatusInput>;
