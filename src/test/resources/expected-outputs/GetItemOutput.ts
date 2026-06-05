import { z } from 'zod';

export const GetItemOutput = z.object({
  body: z.object({
  itemId: z.string(),
  name: z.string(),
  description: z.string().optional()
}),
  statusCode: z.number().optional()
}).transform((v) => ({
  ...v.body,
  ...(v.statusCode !== undefined && { statusCode: v.statusCode }),
}));

export type GetItemOutput = z.output<typeof GetItemOutput>;
