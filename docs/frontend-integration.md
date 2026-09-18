# 前端接入指南

后端 API 根路径固定为 `/api/v1`，完整契约见根目录 `openapi.yaml`。两组登录字段均可使用。

## SPA（vite_react_init / vite_vue3_init）

`.env.local`：

```env
VITE_API_BASE=/api/v1
VITE_API_TARGET=http://localhost:8080
```

Vite dev proxy 把 `/api/*` 转发到后端（生产由网关转发）。

登录（直接 POST，不使用 Cookie）：

```js
// axios
const res = await axios.post('/api/v1/login', {
  username, password, remember,   // 旧模板字段 { name, password, checked } 同样被接受
});
const token = res.data.token;     // 响应顶层就是 token，没有 data 包装层
```

后续请求：

```js
axios.defaults.headers.common.Authorization = `Bearer ${token}`;
```

错误处理：读响应体的 `msg`（兼容现有前端），同时可读 `code`/`requestId` 用于埋点。

`GET /api/v1/me` 返回当前用户（顶层 `publicId/username/displayName/enabled/roles/permissions`）。

`POST /api/v1/logout` 撤销当前会话，重复调用仍返回 204。

## Next.js SSR（vite_react_ssr_init）

浏览器只与前端本地 BFF 交互，Token 永远不出现在浏览器 JS：

```ts
// app/api/session/route.ts
import { NextRequest, NextResponse } from 'next/server';

const BACKEND = process.env.BACKEND_API_BASE_URL!; // 例如 http://backend:8080/api/v1，仅服务端可见

export async function POST(req: NextRequest) {
  const { username, password, remember } = await req.json();
  const upstream = await fetch(`${BACKEND}/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password, remember }),
  });
  if (!upstream.ok) {
    const problem = await upstream.json();
    return NextResponse.json({ code: problem.code, msg: problem.msg }, { status: upstream.status });
  }
  const { token, expiresAt } = await upstream.json();
  const maxAge = Math.max(0, Math.floor((Date.parse(expiresAt) - Date.now()) / 1000)); // 不超过 expiresAt
  const res = NextResponse.json({ ok: true });
  res.cookies.set('cwa_token', token, {
    httpOnly: true,
    sameSite: 'lax',
    path: '/',
    maxAge,
    secure: process.env.NODE_ENV === 'production',
  });
  return res;
}
```

SSR 服务端请求转发（RSC / Route Handler）：

```ts
import { cookies } from 'next/headers';

async function backendFetch(path: string, init?: RequestInit) {
  const token = (await cookies()).get('cwa_token')?.value;
  return fetch(`${process.env.BACKEND_API_BASE_URL}${path}`, {
    ...init,
    headers: { ...init?.headers, Authorization: `Bearer ${token ?? ''}` },
  });
}
```

环境变量：

```env
BACKEND_API_BASE_URL=http://localhost:8080/api/v1   # 服务端私有地址
```

禁止使用 `NEXT_PUBLIC_*` 暴露该地址；生产必须启用 Cookie `Secure`。

## Nuxt SSR（vite_vue3_ssr_init）

```ts
// server/api/session.post.ts
export default defineEventHandler(async (event) => {
  const { username, password, remember } = await readBody(event);
  const config = useRuntimeConfig(event);

  const upstream = await $fetch.raw(`${config.backendApiBaseUrl}/login`, {
    method: 'POST',
    body: { username, password, remember },
  });
  if (!upstream.ok) {
    throw createError({ statusCode: upstream.status, data: upstream._data });
  }

  const { token, expiresAt } = upstream._data;
  const maxAge = Math.max(0, Math.floor((Date.parse(expiresAt) - Date.now()) / 1000));
  setCookie(event, 'cwa_token', token, {
    httpOnly: true,
    sameSite: 'lax',
    path: '/',
    maxAge,
    secure: process.env.NODE_ENV === 'production',
  });
  return { ok: true };
});
```

```ts
// 服务端转发
const token = getCookie(event, 'cwa_token');
await $fetch(`${config.backendApiBaseUrl}/me`, { headers: { Authorization: `Bearer ${token ?? ''}` } });
```

`nuxt.config.ts`：`runtimeConfig.backendApiBaseUrl`（不带 `public` 前缀，仅服务端可用）。

## Cookie 约束汇总

| 属性 | 值 |
| --- | --- |
| 名称 | `cwa_token` |
| HttpOnly | 必须 |
| SameSite | `Lax` |
| Path | `/` |
| Secure | 生产必须 |
| Max-Age | ≤ `expiresAt` 与后端会话 TTL |

## 不变契约

- 登录响应顶层 `token`；错误响应 Problem Details 顶层 `code/msg/requestId`。
- SPA 用 `Authorization: Bearer`；SSR Token 只存在于 HttpOnly Cookie 与服务端上下文。
- 后端不创建 HTTP Session，无 Cookie 鉴权路径。
