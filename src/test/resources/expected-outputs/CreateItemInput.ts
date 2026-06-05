import { z } from 'zod';

export const CreateItemInput = z.object({
  itemType: z.string(),
  itemId: z.string().min(1).max(64).regex(/^[a-zA-Z0-9_-]+$/, { message: 'Must match the required format: ^[a-zA-Z0-9_-]+$' }),
  version: z.string().optional(),
  requestId: z.string().optional(),
  name: z.string().min(1).max(128),
  description: z.string().optional(),
  metadata: z.record(z.string(), z.unknown()).optional(),
  status: z.enum(['Active', 'Inactive', 'Pending']).optional(),
  tags: z.union([z.object({
  simple: z.string()
}), z.object({
  complex: z.object({
  key: z.string().optional(),
  value: z.string().optional(),
  metadata: z.record(z.string(), z.unknown()).optional()
})
})]).optional(),
  priority: z.number().int().min(1).max(10).optional(),
  enabled: z.boolean().default(true),
  stage: z.string().default("draft"),
  retryCount: z.number().int().default(0)
}).transform((v) => ({
  ...v,
  path: { itemType: v.itemType, itemId: v.itemId },
  headers: {
    ...(v.requestId !== undefined && { 'X-Request-ID': v.requestId })
  },
  query: {
    ...(v.version !== undefined && { 'version': v.version })
  },
  body: { name: v.name, description: v.description, metadata: v.metadata, status: v.status, tags: v.tags, priority: v.priority, enabled: v.enabled, stage: v.stage, retryCount: v.retryCount },
  url: `/items/${encodeURIComponent(String(v.itemType))}/${encodeURIComponent(String(v.itemId))}`,
  method: 'POST' as const,
}));

export type CreateItemInput = z.output<typeof CreateItemInput>;
