import { z } from 'zod';

export const SearchItemsInput = z.object({
  maxResults: z.number().int().min(1).max(10).default(10),
  enabled: z.boolean().default(true),
  pageSize: z.number().int().default(25)
}).transform((v) => ({
  ...v,
  path: {},
  headers: {
    ...(v.pageSize !== undefined && { 'X-Page-Size': v.pageSize })
  },
  query: {
    ...(v.maxResults !== undefined && { 'maxResults': v.maxResults }),
    ...(v.enabled !== undefined && { 'enabled': v.enabled })
  },
  body: {},
  url: `/search`,
  method: 'GET' as const,
}));

export type SearchItemsInput = z.output<typeof SearchItemsInput>;
