function escapeHtml(value) {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function applyInlineFormatting(value) {
  const codeTokens = []
  let html = escapeHtml(value).replace(/`([^`]+?)`/g, (_, code) => {
    const token = `__CODE_TOKEN_${codeTokens.length}__`
    codeTokens.push(`<code>${code}</code>`)
    return token
  })

  html = html
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/__(.+?)__/g, '<strong>$1</strong>')
    .replace(/\*(.+?)\*/g, '<em>$1</em>')
    .replace(/_(.+?)_/g, '<em>$1</em>')
    .replace(/~~(.+?)~~/g, '<del>$1</del>')

  codeTokens.forEach((snippet, index) => {
    html = html.replace(`__CODE_TOKEN_${index}__`, snippet)
  })

  return html
}

function renderParagraph(lines) {
  return `<p>${lines.map(applyInlineFormatting).join('<br>')}</p>`
}

function renderQuote(lines) {
  const content = lines
    .map((line) => line.replace(/^\s*>\s?/, ''))
    .filter(Boolean)
  if (content.length === 0) return ''
  return `<blockquote>${content.map(applyInlineFormatting).join('<br>')}</blockquote>`
}

function renderList(lines, ordered) {
  const tag = ordered ? 'ol' : 'ul'
  const items = lines
    .map((line) => {
      const cleaned = ordered
        ? line.replace(/^\s*\d+[\.\uff0e]\s+/, '')
        : line.replace(/^\s*[-*+•·]\s+/, '')
      return `<li>${applyInlineFormatting(cleaned)}</li>`
    })
    .join('')
  return `<${tag}>${items}</${tag}>`
}

function isUnorderedList(line) {
  return /^\s*[-*+•·]\s+/.test(line)
}

function isOrderedList(line) {
  return /^\s*\d+[\.\uff0e]\s+/.test(line)
}

function isHeading(line) {
  return /^\s*#{1,3}\s+/.test(line)
}

export function splitRichTextBlocks(text) {
  if (!text) return []
  return String(text)
    .replace(/\r\n/g, '\n')
    .split(/\n{2,}/)
    .map((block) => block.trim())
    .filter(Boolean)
}

export function renderRichText(text, options = {}) {
  const emptyText = options.emptyText || '暂无内容'
  if (!text || !String(text).trim()) {
    return `<p class="rich-text-block__empty">${escapeHtml(emptyText)}</p>`
  }

  const lines = String(text).replace(/\r\n/g, '\n').split('\n')
  const html = []

  for (let index = 0; index < lines.length; ) {
    const line = lines[index]

    if (!line.trim()) {
      index += 1
      continue
    }

    if (isHeading(line)) {
      const [, hashes = '', content = ''] = line.match(/^(\s*#{1,3})\s+(.*)$/) || []
      const level = Math.min(3, hashes.trim().length || 1)
      html.push(`<h${level}>${applyInlineFormatting(content)}</h${level}>`)
      index += 1
      continue
    }

    if (/^\s*>\s?/.test(line)) {
      const quoteLines = []
      while (index < lines.length && /^\s*>\s?/.test(lines[index])) {
        quoteLines.push(lines[index])
        index += 1
      }
      html.push(renderQuote(quoteLines))
      continue
    }

    if (isUnorderedList(line)) {
      const listLines = []
      while (index < lines.length && isUnorderedList(lines[index])) {
        listLines.push(lines[index])
        index += 1
      }
      html.push(renderList(listLines, false))
      continue
    }

    if (isOrderedList(line)) {
      const listLines = []
      while (index < lines.length && isOrderedList(lines[index])) {
        listLines.push(lines[index])
        index += 1
      }
      html.push(renderList(listLines, true))
      continue
    }

    const paragraphLines = []
    while (
      index < lines.length &&
      lines[index].trim() &&
      !isHeading(lines[index]) &&
      !/^\s*>\s?/.test(lines[index]) &&
      !isUnorderedList(lines[index]) &&
      !isOrderedList(lines[index])
    ) {
      paragraphLines.push(lines[index].trim())
      index += 1
    }
    html.push(renderParagraph(paragraphLines))
  }

  return html.join('')
}
