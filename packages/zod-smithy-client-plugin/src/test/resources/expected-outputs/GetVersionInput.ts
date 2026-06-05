import { z } from 'zod';

export const GetVersionInput = z.object({
  versionId: z.string().regex(/^([a-zA-Z0-9_-]+|\$latest)$/, { message: 'Must match the required format: ^([a-zA-Z0-9_-]+|\\$latest)$' })
}).transform((v) => ({
  ...v,
  path: { versionId: v.versionId },
  headers: {},
  query: {},
  body: {},
  url: `/version/${encodeURIComponent(String(v.versionId))}`,
  method: 'GET' as const,
}));

export type GetVersionInput = z.output<typeof GetVersionInput>;
