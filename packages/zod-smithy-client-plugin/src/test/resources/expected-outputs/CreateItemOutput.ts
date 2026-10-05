import { z } from 'zod';

export const CreateItemOutput = z.object({
  body: z.object({
  itemId: z.string(),
  status: z.enum(['Active', 'Inactive', 'Pending']),
  createdAt: z.string()
}),
  headers: z.object({
  'x-request-id': z.string().optional()
}).optional(),
  statusCode: z.number().optional()
}).transform((v) => ({
  ...v.body,
  ...(v.headers?.['x-request-id'] !== undefined && { requestId: v.headers?.['x-request-id'] }),
  ...(v.statusCode !== undefined && { statusCode: v.statusCode }),
}));

export type CreateItemOutput = z.output<typeof CreateItemOutput>;
