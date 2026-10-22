import http from 'node:http';
import crypto from 'node:crypto';

const PORT = Number(process.env.PORT ?? 9001);
const CHANNEL_CODE = process.env.CHANNEL_CODE ?? 'RETAIL';
const CAS_BASE = (process.env.CAS_BASE ?? 'http://localhost:8080/cas').replace(/\/$/, '');
const SERVICE_BASE = (process.env.SERVICE_BASE ?? `http://localhost:${PORT}`).replace(/\/$/, '');
const SERVICE_URL = `${SERVICE_BASE}/callback`;
const SESSION_COOKIE = `demo_sid_${CHANNEL_CODE}`;
const OTHER_PORT = PORT === 9001 ? 9002 : 9001;
const OTHER_CHANNEL = PORT === 9001 ? 'CREDIT' : 'RETAIL';
const sessions = new Map();

function parseCookies(req) {
  return Object.fromEntries(
    (req.headers.cookie ?? '')
      .split(';')
      .map((item) => item.trim())
      .filter(Boolean)
      .map((item) => {
        const index = item.indexOf('=');
        return [item.slice(0, index), decodeURIComponent(item.slice(index + 1))];
      })
  );
}

async function readBody(req, limit = 64 * 1024) {
  const chunks = [];
  let size = 0;
  for await (const chunk of req) {
    size += chunk.length;
    if (size > limit) {
      throw new Error('request body too large');
    }
    chunks.push(chunk);
  }
  return Buffer.concat(chunks).toString('utf8');
}

function sessionId(req) {
  return parseCookies(req)[SESSION_COOKIE];
}

function sessionCookie(value, maxAge) {
  return `${SESSION_COOKIE}=${encodeURIComponent(value)}; Path=/; Max-Age=${maxAge}; HttpOnly; SameSite=Lax`;
}

function invalidateBySlo(payload) {
  const sessionIndex = payload.match(/<(?:\w+:)?SessionIndex>([^<]+)</i)?.[1];
  const nameId = payload.match(/<(?:\w+:)?NameID[^>]*>([^<]*)</i)?.[1];
  let removed = 0;
  for (const [id, session] of sessions) {
    const matchesTicket = sessionIndex && session.ticket === sessionIndex;
    const matchesUser = !sessionIndex && nameId && nameId !== '@NOT_USED@' && session.user === nameId;
    if (matchesTicket || matchesUser) {
      sessions.delete(id);
      removed += 1;
    }
  }
  return { sessionIndex: sessionIndex ?? null, nameId: nameId ?? null, removed };
}

async function handleSlo(req, res, url) {
  try {
    const body = await readBody(req);
    const form = new URLSearchParams(body);
    const payload = form.get('logoutRequest')
      ?? url.searchParams.get('logoutRequest')
      ?? (body.trim().startsWith('<') ? body : '');
    const result = invalidateBySlo(payload);
    console.log(`SLO received: sessionIndex=${result.sessionIndex} nameId=${result.nameId} removed=${result.removed}`);
    res.writeHead(200, { 'Content-Type': 'text/plain; charset=utf-8' });
    res.end('logout accepted');
  } catch (error) {
    console.error(`SLO failed: ${error.message}`);
    res.writeHead(400, { 'Content-Type': 'text/plain; charset=utf-8' });
    res.end('invalid logout request');
  }
}

function escapeHtml(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;');
}

async function validateTicket(ticket) {
  const url = new URL(`${CAS_BASE}/p3/serviceValidate`);
  url.searchParams.set('service', SERVICE_URL);
  url.searchParams.set('ticket', ticket);
  const response = await fetch(url, { redirect: 'manual' });
  const xml = await response.text();
  const user = xml.match(/<cas:user>([^<]*)<\/cas:user>/)?.[1];
  return { ok: response.ok && Boolean(user), user, xml };
}

function page(title, body) {
  return `<!doctype html>
<html lang="zh-CN">
<head><meta charset="UTF-8"><title>${escapeHtml(title)}</title></head>
<body>
<h2>${escapeHtml(title)}</h2>
${body}
<hr>
<p>channelCode: <code>${escapeHtml(CHANNEL_CODE)}</code>，端口: ${PORT}</p>
</body>
</html>`;
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url ?? '/', `http://${req.headers.host ?? `localhost:${PORT}`}`);

  if (url.pathname === '/slo' || (url.pathname === '/logout' && req.method === 'POST')) {
    await handleSlo(req, res, url);
    return;
  }

  const currentId = sessionId(req);
  const session = currentId ? sessions.get(currentId) : undefined;
  const user = session?.user;

  if (url.pathname === '/') {
    if (!user) {
      const loginUrl = `${CAS_BASE}/login?service=${encodeURIComponent(SERVICE_URL)}&channelCode=${encodeURIComponent(CHANNEL_CODE)}`;
      res.writeHead(302, {
        'Set-Cookie': sessionCookie('', 0),
        Location: loginUrl
      });
      res.end();
      return;
    }
    const otherUrl = `http://localhost:${OTHER_PORT}/`;
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
    res.end(page(`${CHANNEL_CODE} 演示系统`, `
      <p>已登录用户：<strong>${escapeHtml(user)}</strong></p>
      <p>本地会话：<code>${escapeHtml(currentId)}</code></p>
      <p><a href="/logout">单点登出</a></p>
      <p><a href="${otherUrl}">访问另一个条线（${escapeHtml(OTHER_CHANNEL)}，端口 ${OTHER_PORT}）</a></p>
      <p><a href="/bad-channel">用错误的 channelCode 触发登录入口校验</a></p>
    `));
    return;
  }

  if (url.pathname === '/bad-channel') {
    const loginUrl = `${CAS_BASE}/login?service=${encodeURIComponent(SERVICE_URL)}&channelCode=WRONG`;
    res.writeHead(302, { Location: loginUrl });
    res.end();
    return;
  }

  if (url.pathname === '/callback') {
    const ticket = url.searchParams.get('ticket');
    if (!ticket) {
      res.writeHead(400, { 'Content-Type': 'text/plain; charset=utf-8' });
      res.end('缺少 ticket 参数');
      return;
    }
    try {
      const result = await validateTicket(ticket);
      if (!result.ok) {
        res.writeHead(403, { 'Content-Type': 'text/html; charset=utf-8' });
        res.end(page('CAS 票据校验失败', `<pre>${escapeHtml(result.xml)}</pre>`));
        return;
      }
      const oldSessionId = sessionId(req);
      if (oldSessionId) {
        sessions.delete(oldSessionId);
      }
      const sid = crypto.randomUUID();
      sessions.set(sid, { user: result.user, ticket, createdAt: Date.now() });
      res.writeHead(302, {
        'Set-Cookie': sessionCookie(sid, 3600),
        Location: '/'
      });
      res.end();
    } catch (error) {
      res.writeHead(502, { 'Content-Type': 'text/plain; charset=utf-8' });
      res.end(`CAS 校验请求失败：${error.message}`);
    }
    return;
  }

  if (url.pathname === '/logout') {
    if (currentId) {
      sessions.delete(currentId);
    }
    res.writeHead(302, {
      'Set-Cookie': sessionCookie('', 0),
      Location: `${CAS_BASE}/logout?service=${encodeURIComponent(SERVICE_BASE + '/')}`
    });
    res.end();
    return;
  }

  res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
  res.end('not found');
});

server.listen(PORT, () => {
  console.log(`demo client listening on ${SERVICE_BASE}, channelCode=${CHANNEL_CODE}`);
  console.log(`CAS base: ${CAS_BASE}`);
});
