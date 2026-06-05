import { z } from 'zod';

export const CreateItemOutput = z.object({
  body: z.object({
  itemId: z.string(),
  status: z.enum(['Active', 'Inactive', 'Pending']),
  createdAt: z.string()
}),
  headers: z.object({
  'X-Request-ID': z.string().optional()
}).optional(),
  statusCode: z.number().optional()
}).transform((v) => ({
  ...v.body,
  ...(v.headers?.['X-Request-ID'] !== undefined && { requestId: v.headers?.['X-Request-ID'] }),
  ...(v.statusCode !== undefined && { statusCode: v.statusCode }),
}));

export type CreateItemOutput = z.output<typeof CreateItemOutput>;
