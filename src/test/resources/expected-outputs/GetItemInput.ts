import { z } from 'zod';

export const GetItemInput = z.object({
  itemId: z.string(),
  includeMetadata: z.boolean().optional()
}).transform((v) => ({
  ...v,
  path: { itemId: v.itemId },
  headers: {},
  query: {
    ...(v.includeMetadata !== undefined && { 'includeMetadata': v.includeMetadata })
  },
  body: {},
  url: `/items/${encodeURIComponent(String(v.itemId))}`,
  method: 'GET' as const,
}));

export type GetItemInput = z.output<typeof GetItemInput>;
