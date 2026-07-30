import type { APIRoute } from 'astro'

export const GET: APIRoute = () =>
  new Response(
    `User-agent: *
Allow: /

Sitemap: https://loomai.pro/sitemap.xml
`,
    {
      headers: {
        'Content-Type': 'text/plain; charset=utf-8',
      },
    },
  )
