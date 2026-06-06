import { z } from 'zod';
import { ServiceError } from './utils.js';

export const BadRequestExceptionSchema = z.object({
  message: z.string().optional()
});

export class BadRequestException extends ServiceError {
  readonly _kind = 'BadRequestException' as const;
  constructor(body: z.infer<typeof BadRequestExceptionSchema>, statusCode: number, operationName: string) {
    super({
      message: (body as any).message ?? 'BadRequestException',
      statusCode,
      body,
      operationName,
      _kind: 'BadRequestException',
    });
  }
}

export const NotFoundExceptionSchema = z.object({
  message: z.string().optional()
});

export class NotFoundException extends ServiceError {
  readonly _kind = 'NotFoundException' as const;
  constructor(body: z.infer<typeof NotFoundExceptionSchema>, statusCode: number, operationName: string) {
    super({
      message: (body as any).message ?? 'NotFoundException',
      statusCode,
      body,
      operationName,
      _kind: 'NotFoundException',
    });
  }
}

export const InternalServiceExceptionSchema = z.object({
  message: z.string().optional()
});

export class InternalServiceException extends ServiceError {
  readonly _kind = 'InternalServiceException' as const;
  constructor(body: z.infer<typeof InternalServiceExceptionSchema>, statusCode: number, operationName: string) {
    super({
      message: (body as any).message ?? 'InternalServiceException',
      statusCode,
      body,
      operationName,
      _kind: 'InternalServiceException',
    });
  }
}

export function parseServiceError(operationName: string, statusCode: number, body: unknown): ServiceError {
  {
    const parsed = BadRequestExceptionSchema.safeParse(body);
    if (parsed.success && statusCode === 400) {
      return new BadRequestException(parsed.data, statusCode, operationName);
    }
  }
  {
    const parsed = NotFoundExceptionSchema.safeParse(body);
    if (parsed.success && statusCode === 404) {
      return new NotFoundException(parsed.data, statusCode, operationName);
    }
  }
  {
    const parsed = InternalServiceExceptionSchema.safeParse(body);
    if (parsed.success && statusCode === 500) {
      return new InternalServiceException(parsed.data, statusCode, operationName);
    }
  }
  return new ServiceError({
    message: typeof body === 'object' && body !== null && 'message' in body ? String((body as any).message) : 'Unknown error',
    statusCode,
    body,
    operationName,
    _kind: 'UnknownError',
  });
}
