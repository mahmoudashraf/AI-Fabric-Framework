import type { APIRoute } from 'astro'
import { experiments, products, research, site } from '../data/content'

const staticPaths = ['/', '/products', '/experiments', '/research', '/about', '/connect']
const paths = [
  ...staticPaths,
  ...products.map((item) => `/products/${item.slug}`),
  ...experiments.map((item) => `/experiments/${item.slug}`),
  ...research.map((item) => `/research/${item.slug}`),
]

const escapeXml = (value: string) =>
  value.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;')

export const GET: APIRoute = () => {
  const urls = paths
    .map((path) => `  <url><loc>${escapeXml(new URL(path, site.canonicalOrigin).toString())}</loc></url>`)
    .join('\n')

  return new Response(
    `<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
${urls}
</urlset>
`,
    {
      headers: {
        'Content-Type': 'application/xml; charset=utf-8',
      },
    },
  )
}
