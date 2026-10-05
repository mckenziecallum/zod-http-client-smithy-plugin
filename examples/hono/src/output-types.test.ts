import type {
  CreateGenerationResultType,
  HonoHandlers,
} from "../build/generated/hono/index.js";
import type {
  CreateGenerationOutputType,
  createAxiosClient,
  createFetchClient,
} from "../build/generated/client/index.js";

// Compile-time regressions: required modeled headers stay required in every API.
type Assert<T extends true> = T;
type RequiredMember<T, K extends keyof T> = {} extends Pick<T, K> ? false : true;
type HandlerResult = Awaited<ReturnType<HonoHandlers["createGeneration"]>>;
type FetchResult = Awaited<ReturnType<ReturnType<typeof createFetchClient>["createGeneration"]>>;
type AxiosResult = Awaited<ReturnType<ReturnType<typeof createAxiosClient>["createGeneration"]>>;

type HandlerRequiresLocation = Assert<RequiredMember<HandlerResult, "location">>;
type ResultRequiresLocation = Assert<RequiredMember<CreateGenerationResultType, "location">>;
type OutputRequiresLocation = Assert<RequiredMember<CreateGenerationOutputType, "location">>;
type FetchRequiresLocation = Assert<RequiredMember<FetchResult, "location">>;
type AxiosRequiresLocation = Assert<RequiredMember<AxiosResult, "location">>;
type ResultAllowsMissingTrace = Assert<RequiredMember<CreateGenerationResultType, "traceId"> extends false ? true : false>;
type OutputAllowsMissingTrace = Assert<RequiredMember<CreateGenerationOutputType, "traceId"> extends false ? true : false>;
type ResultHasOnlyModeledMembers = Assert<"statusCode" extends keyof HandlerResult ? false : true>;
