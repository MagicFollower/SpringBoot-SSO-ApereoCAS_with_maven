package org.apereo.cas.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 浏览器 HTTP 访问守卫：禁止通过 HTTP 向浏览器提供 CAS 页面。
 *
 * <p>规则：明文 HTTP 请求打到浏览器端点（/login、/logout）时，一律 302 升级到
 * HTTPS 入口（{@code https://<原主机名>:<httpsPort>/...}），查询串原样保留
 * （service / channelCode / ticket 等 SSO 参数不丢失），并在响应体中给出中文提示，
 * 供不支持自动跳转的客户端查看。</p>
 *
 * <p>不受影响的端点：{@code /p3/**}、{@code /admin/control/**} 等服务端到服务端调用
 * （走回环 HTTP，不注册本过滤器）；HTTPS 请求直接放行。</p>
 *
 * <p>三层限制中的第三层（应用层）；前两层为 HTTPS 8083 对外、HTTP 8080 仅回环绑定。
 * 设计见 CAS-HTTPS启用方案.md。</p>
 */
public class CasHttpBrowserGuardFilter extends OncePerRequestFilter {

    private final int httpsPort;

    public CasHttpBrowserGuardFilter(final int httpsPort) {
        this.httpsPort = httpsPort;
    }

    @Override
    protected void doFilterInternal(final HttpServletRequest request,
                                    final HttpServletResponse response,
                                    final FilterChain filterChain)
            throws ServletException, IOException {
        if (request.isSecure()) {
            filterChain.doFilter(request, response);
            return;
        }
        final String target = httpsTarget(request);
        response.setStatus(HttpServletResponse.SC_FOUND);
        response.setHeader("Location", target);
        response.setContentType("text/html;charset=UTF-8");
        response.getWriter().write(hintBody(target));
    }

    /**
     * 构造 HTTPS 目标：主机名取自请求（getServerName 已剥离端口），端口替换为 HTTPS 入口，
     * URI 与查询串原样保留。例：
     * http://localhost:8080/cas/login?service=X → https://localhost:8083/cas/login?service=X
     */
    private String httpsTarget(final HttpServletRequest request) {
        final StringBuilder sb = new StringBuilder(128)
                .append("https://").append(request.getServerName()).append(':').append(httpsPort)
                .append(request.getRequestURI());
        final String query = request.getQueryString();
        if (query != null && !query.isBlank()) {
            sb.append('?').append(query);
        }
        return sb.toString();
    }

    private static String hintBody(final String target) {
        final String safe = target.replace("&", "&amp;")
                .replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
        return "<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">"
                + "<meta http-equiv=\"refresh\" content=\"0;url=" + safe + "\">"
                + "<title>必须通过 HTTPS 访问 CAS</title></head><body>"
                + "<h3>禁止通过 HTTP 访问 CAS</h3>"
                + "<p>出于安全要求，浏览器端必须通过 HTTPS 访问 CAS 单点登录。请使用：</p>"
                + "<p><a href=\"" + safe + "\">" + safe + "</a>（正在自动跳转…）</p>"
                + "<p>本 HTTP 端口仅供同机服务间调用（票据校验、控制面），不对浏览器提供页面。</p>"
                + "</body></html>";
    }
}
