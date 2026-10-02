import { serve } from "@hono/node-server";
import { createHonoRouter } from "../build/generated/hono/index.js";
import { handlers } from "./full-stack-handlers.js";

serve({ fetch: createHonoRouter(handlers).fetch, hostname: "127.0.0.1", port: 0 },
  ({ port }) => console.log(`http://127.0.0.1:${port}`));
