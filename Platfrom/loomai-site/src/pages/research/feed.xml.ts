import type { APIRoute } from 'astro'
import { research, site } from '../../data/content'

const escapeXml = (value: string) =>
  value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&apos;')

export const GET: APIRoute = () => {
  const items = [...research]
    .sort((left, right) => right.updatedAt.localeCompare(left.updatedAt))
    .map((item) => {
      const url = new URL(`/research/${item.slug}`, site.canonicalOrigin).toString()
      return `  <item>
    <title>${escapeXml(item.title)}</title>
    <link>${escapeXml(url)}</link>
    <guid isPermaLink="true">${escapeXml(url)}</guid>
    <description>${escapeXml(item.abstract)}</description>
    <pubDate>${new Date(`${item.updatedAt}T12:00:00Z`).toUTCString()}</pubDate>
  </item>`
    })
    .join('\n')

  return new Response(
    `<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0">
<channel>
  <title>Loom AI Labs Applied Research</title>
  <link>${site.canonicalOrigin}/research</link>
  <description>${escapeXml(site.description)}</description>
  <language>en-gb</language>
${items}
</channel>
</rss>
`,
    {
      headers: {
        'Content-Type': 'application/rss+xml; charset=utf-8',
      },
    },
  )
}
