import { fromMarkdown } from 'mdast-util-from-markdown'

function inline(nodes, formatting = {}) {
  return nodes.flatMap(node => {
    if (node.type === 'text' || node.type === 'inlineCode') return [{ text: node.value, ...formatting }]
    if (node.type === 'break') return [{ text: '\n', ...formatting }]
    if (node.type === 'strong' || node.type === 'emphasis') return inline(node.children, { ...formatting, ...(node.type === 'strong' ? { bold: true } : {}) })
    if (node.type === 'link') {
      const url = new URL(node.url)
      if (!['https:','tel:'].includes(url.protocol) || url.username || url.password) throw new Error('UNSAFE_LINK')
      return inline(node.children, { ...formatting, href: url.href })
    }
    throw new Error(`UNSUPPORTED_INLINE_${node.type}`)
  })
}

export function proposalToContent(markdown, { recipient, date, version = 1 }) {
  const tree = fromMarkdown(markdown)
  const content = { title: '', subtitle: '', recipient, date, version, sections: [] }
  let section
  for (const node of tree.children) {
    if (node.type === 'html' && node.value.trim() === '<!-- pagebreak -->') continue
    if (node.type === 'heading') {
      const heading = inline(node.children).map(run => run.text).join('')
      if (node.depth === 1 && !content.title) { content.title = heading; continue }
      if (node.depth !== 2) throw new Error('UNSUPPORTED_HEADING')
      section = { heading, blocks: [] }
      content.sections.push(section)
    } else if (node.type === 'paragraph') {
      const runs = inline(node.children)
      if (!section && !content.subtitle) { content.subtitle = runs.map(run => run.text).join(''); continue }
      if (!section) throw new Error('MISSING_SECTION')
      section.blocks.push({ type: 'paragraph', runs })
    } else if (node.type === 'list' && section) {
      section.blocks.push({ type: 'list', ordered: node.ordered, items: node.children.map(item => {
        if (item.children.length !== 1 || item.children[0].type !== 'paragraph') throw new Error('UNSUPPORTED_LIST_ITEM')
        return inline(item.children[0].children)
      }) })
    } else throw new Error(`UNSUPPORTED_BLOCK_${node.type}`)
  }
  if (!content.title || !content.sections.length || !recipient || !date || !Number.isSafeInteger(version)) throw new Error('INCOMPLETE_PROPOSAL')
  return content
}
